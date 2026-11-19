package com.wanderly.api.payment

import com.wanderly.api.config.WanderlyProperties
import com.wanderly.api.domain.*
import com.wanderly.api.dto.BookingDto
import com.wanderly.api.dto.PaymentSessionDto
import com.wanderly.api.exception.ApiException
import com.wanderly.api.realtime.RealtimePublisher
import com.wanderly.api.repository.BookingRepository
import com.wanderly.api.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.security.SecureRandom

/** A hosted-checkout payment provider. [SslCommerzGateway] is the real one; tests plug in a fake. */
interface PaymentGateway {

    data class SessionRequest(
        val tranId: String, val amount: Int, val currency: String, val productName: String,
        val customerName: String, val customerEmail: String,
        val successUrl: String, val failUrl: String, val cancelUrl: String, val ipnUrl: String, val reference: String,
    )

    /** [gatewayUrl] is the hosted payment page the app opens. */
    data class Session(val gatewayUrl: String, val sessionKey: String?)

    /** What the provider says really happened. */
    data class Validation(
        val valid: Boolean, val tranId: String?, val amount: BigDecimal?, val currency: String?,
        val method: String?, val rawStatus: String?,
    )

    fun createSession(request: SessionRequest): Session
    fun validate(validationId: String): Validation
}

/**
 * SSLCommerz (Bangladesh) hosted checkout — cards, bKash, Nagad, Rocket and internet banking.
 * Docs: https://developer.sslcommerz.com/doc/v4/
 */
@Component
class SslCommerzGateway(props: WanderlyProperties, private val json: JsonMapper) : PaymentGateway {

    companion object {
        private val log = LoggerFactory.getLogger(SslCommerzGateway::class.java)
    }

    private val config = props.payments.sslcommerz
    private val http = RestClient.builder().baseUrl(config.baseUrl).build()

    init {
        log.info("SSLCommerz {} mode, store '{}'", if (config.sandbox) "SANDBOX" else "LIVE", config.storeId)
    }

    /** SSLCommerz sometimes labels its JSON as text/html, so parse the raw body ourselves. */
    @Suppress("UNCHECKED_CAST")
    private fun parse(body: String?): Map<String, Any?>? {
        if (body.isNullOrBlank()) return null
        return try {
            json.readValue(body, Map::class.java) as Map<String, Any?>
        } catch (e: RuntimeException) {
            throw ApiException(HttpStatus.BAD_GATEWAY, "Unexpected response from payment gateway")
        }
    }

    override fun createSession(request: PaymentGateway.SessionRequest): PaymentGateway.Session {
        val r = request
        val form = LinkedMultiValueMap<String, String>().apply {
            add("store_id", config.storeId)
            add("store_passwd", config.storePassword)
            add("total_amount", r.amount.toString())
            add("currency", r.currency)
            add("tran_id", r.tranId)
            add("success_url", r.successUrl)
            add("fail_url", r.failUrl)
            add("cancel_url", r.cancelUrl)
            add("ipn_url", r.ipnUrl)
            add("cus_name", r.customerName)
            add("cus_email", r.customerEmail)
            // Address/phone are required by the API; the app doesn't collect them for hotel stays.
            add("cus_add1", "Dhaka")
            add("cus_city", "Dhaka")
            add("cus_country", "Bangladesh")
            add("cus_phone", "01700000000")
            add("shipping_method", "NO")
            add("num_of_item", "1")
            add("product_name", r.productName)
            add("product_category", "Travel")
            add("product_profile", "travel-vertical")
            add("hotel_name", r.productName)
            add("length_of_stay", "1")
            add("check_in_time", "14:00")
            add("hotel_city", "Bangladesh")
            add("value_a", r.reference)
        }
        val res = try {
            parse(
                http.post().uri("/gwprocess/v4/api.php")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String::class.java)
            )
        } catch (e: RestClientException) {
            throw ApiException(HttpStatus.BAD_GATEWAY, "Payment gateway unreachable: ${e.message}")
        }
        val url = res?.get("GatewayPageURL")?.toString()
        if (res == null || !"SUCCESS".equals(res["status"]?.toString(), true) || url.isNullOrBlank()) {
            throw ApiException(HttpStatus.BAD_GATEWAY, "Payment gateway rejected the session: ${res?.get("failedreason") ?: "empty response"}")
        }
        return PaymentGateway.Session(url, res["sessionkey"]?.toString())
    }

    override fun validate(validationId: String): PaymentGateway.Validation {
        val res = try {
            parse(
                http.get().uri {
                    it.path("/validator/api/validationserverAPI.php")
                        .queryParam("val_id", validationId)
                        .queryParam("store_id", config.storeId)
                        .queryParam("store_passwd", config.storePassword)
                        .queryParam("format", "json")
                        .build()
                }.retrieve().body(String::class.java)
            )
        } catch (e: RestClientException) {
            throw ApiException(HttpStatus.BAD_GATEWAY, "Could not validate payment: ${e.message}")
        } ?: return PaymentGateway.Validation(false, null, null, null, null, "EMPTY")

        val status = res["status"]?.toString()
        val amount = res["amount"]?.toString()?.toBigDecimalOrNull()
        val valid = (status.equals("VALID", true) || status.equals("VALIDATED", true)) && amount != null
        return PaymentGateway.Validation(valid, res["tran_id"]?.toString(), amount, res["currency"]?.toString(), res["card_type"]?.toString(), status)
    }
}

/**
 * Online payment for hotel bookings.
 *  1. App → `POST /api/payments/bookings/{id}/session` → hosted checkout URL.
 *  2. User pays on SSLCommerz (card / bKash / Nagad …).
 *  3. SSLCommerz → success / fail / cancel callback (browser) and IPN (server-to-server).
 *  4. We **validate with SSLCommerz** — amount, currency and transaction id must match — before
 *     marking the booking paid. Callbacks alone are never trusted.
 *  5. The app learns instantly over the WebSocket (`booking.updated`) and via a notification.
 * Completion is idempotent: the browser callback and the IPN may both arrive.
 */
@Service
class PaymentService(
    private val bookings: BookingRepository,
    private val gateway: PaymentGateway,
    private val notifications: NotificationService,
    private val realtime: RealtimePublisher,
    props: WanderlyProperties,
) {
    companion object {
        const val CURRENCY = "BDT"
        private val log = LoggerFactory.getLogger(PaymentService::class.java)
        private val RANDOM = SecureRandom()
    }

    private val baseUrl = props.payments.publicBaseUrl.trimEnd('/')

    @Transactional
    fun startSession(user: User, bookingId: String): PaymentSessionDto {
        val b = bookings.findByIdAndUserId(bookingId, user.uid).orElseThrow { ApiException.notFound("Booking", bookingId) }
        if (b.status == BookingStatus.CANCELLED) throw ApiException.conflict("Booking is cancelled")
        if (b.paymentStatus == PaymentStatus.PAID) throw ApiException.conflict("Booking is already paid")

        // Fresh transaction id per attempt, so retrying after a failed/cancelled payment works.
        val tranId = "${b.id}-${System.currentTimeMillis().toString(36).uppercase()}${RANDOM.nextInt(1296).toString(36).uppercase()}"
        b.startPayment(tranId)

        val cb = "$baseUrl/api/payments/sslcommerz/"
        val session = gateway.createSession(
            PaymentGateway.SessionRequest(
                tranId, b.total, CURRENCY, b.hotel.name, user.name, user.email,
                cb + "success", cb + "fail", cb + "cancel", cb + "ipn", b.id,
            )
        )
        return PaymentSessionDto(b.id, tranId, session.gatewayUrl, b.total, CURRENCY)
    }

    /** Success callback or IPN: verify with the gateway, then mark paid. Returns the booking. */
    @Transactional
    fun complete(tranId: String?, valId: String?): Booking {
        val b = byTran(tranId)
        if (b.paymentStatus == PaymentStatus.PAID) return b // already handled (IPN + redirect)
        if (valId.isNullOrBlank()) return b.also { fail(it, "missing val_id") }

        val v = gateway.validate(valId)
        val matches = v.valid && v.tranId == tranId && CURRENCY.equals(v.currency, true) &&
            v.amount?.compareTo(BigDecimal.valueOf(b.total.toLong())) == 0
        if (!matches) {
            log.warn("Payment validation mismatch for {}: status={} tran={} amount={} {}", tranId, v.rawStatus, v.tranId, v.amount, v.currency)
            return b.also { fail(it, "validation failed") }
        }
        b.markPaid(valId, v.method)
        notifications.notify(
            b.user, NotificationType.BOOKING, "Payment received — ${b.hotel.name} is confirmed",
            "৳${b.total} paid${v.method?.let { " via $it" } ?: ""} · confirmation ${b.id}",
        )
        realtime.toUser(b.user.uid, "booking.updated", BookingDto.from(b))
        return b
    }

    @Transactional
    fun fail(tranId: String?): Booking = byTran(tranId).also { if (it.paymentStatus != PaymentStatus.PAID) fail(it, "gateway reported failure") }

    /** User closed/cancelled checkout: the booking stays unpaid and can be paid later. */
    @Transactional
    fun cancel(tranId: String?): Booking = byTran(tranId).also {
        if (it.paymentStatus != PaymentStatus.PAID) {
            it.startPayment(it.paymentTranId!!)
            realtime.toUser(it.user.uid, "booking.updated", BookingDto.from(it))
        }
    }

    private fun fail(b: Booking, reason: String) {
        log.info("Payment for booking {} failed: {}", b.id, reason)
        b.markPaymentFailed()
        realtime.toUser(b.user.uid, "booking.updated", BookingDto.from(b))
    }

    private fun byTran(tranId: String?): Booking {
        if (tranId.isNullOrBlank()) throw ApiException.badRequest("tran_id is required")
        return bookings.findByPaymentTranId(tranId).orElseThrow { ApiException.notFound("Payment", tranId) }
    }
}
