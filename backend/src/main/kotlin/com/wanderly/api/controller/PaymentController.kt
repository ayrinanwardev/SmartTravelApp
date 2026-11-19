package com.wanderly.api.controller

import com.wanderly.api.domain.PaymentStatus
import com.wanderly.api.domain.User
import com.wanderly.api.dto.PaymentSessionDto
import com.wanderly.api.payment.PaymentService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.HtmlUtils
import org.springframework.web.util.UriComponentsBuilder

/**
 * Payments.
 *  - `POST /api/payments/bookings/{id}/session` (JWT) → hosted checkout URL for the app.
 *  - `/api/payments/sslcommerz/{success|fail|cancel}` — browser redirects from SSLCommerz (form POST).
 *  - `/api/payments/sslcommerz/ipn` — server-to-server notification.
 *  - `/payments/result` — tiny page the app's in-app browser recognises to close checkout.
 */
@RestController
class PaymentController(private val payments: PaymentService) {

    @PostMapping("/api/payments/bookings/{bookingId}/session")
    fun session(@AuthenticationPrincipal user: User, @PathVariable bookingId: String): PaymentSessionDto =
        payments.startSession(user, bookingId)

    @RequestMapping("/api/payments/sslcommerz/success", method = [RequestMethod.POST, RequestMethod.GET])
    fun success(@RequestParam("tran_id") tranId: String?, @RequestParam("val_id", required = false) valId: String?): ResponseEntity<Void> {
        val b = payments.complete(tranId, valId)
        val status = if (b.paymentStatus == PaymentStatus.PAID) "success" else "failed"
        return redirect(status, b.id)
    }

    @RequestMapping("/api/payments/sslcommerz/fail", method = [RequestMethod.POST, RequestMethod.GET])
    fun fail(@RequestParam("tran_id") tranId: String?) = redirect("failed", payments.fail(tranId).id)

    @RequestMapping("/api/payments/sslcommerz/cancel", method = [RequestMethod.POST, RequestMethod.GET])
    fun cancel(@RequestParam("tran_id") tranId: String?) = redirect("cancelled", payments.cancel(tranId).id)

    /** IPN: SSLCommerz calls this directly; validation inside complete() makes it safe. */
    @PostMapping("/api/payments/sslcommerz/ipn")
    fun ipn(
        @RequestParam("tran_id", required = false) tranId: String?,
        @RequestParam("val_id", required = false) valId: String?,
        @RequestParam("status", required = false) status: String?,
    ): Map<String, String> {
        if (tranId != null && (status.equals("VALID", true) || status.equals("VALIDATED", true))) payments.complete(tranId, valId)
        return mapOf("received" to "true")
    }

    @GetMapping("/payments/result", produces = [MediaType.TEXT_HTML_VALUE])
    fun result(@RequestParam status: String, @RequestParam(required = false) bookingId: String?): String {
        val (icon, title, body) = when (status) {
            "success" -> Triple("✅", "Payment successful", "Your booking is confirmed.")
            "cancelled" -> Triple("↩️", "Payment cancelled", "Your booking is saved — you can pay later from Booking history.")
            else -> Triple("⚠️", "Payment failed", "No money was taken. You can try again from Booking history.")
        }
        val ref = bookingId?.let { "<p class=ref>Booking ${HtmlUtils.htmlEscape(it)}</p>" }.orEmpty()
        return """<!doctype html><html><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
            <title>$title</title><style>body{font-family:system-ui,sans-serif;background:#FAF7F2;color:#14213D;display:grid;
            place-items:center;min-height:100vh;margin:0;text-align:center;padding:24px}.i{font-size:56px}.ref{color:#6B7280}</style>
            </head><body><div><div class=i>$icon</div><h1>$title</h1><p>$body</p>$ref<p>Return to the Wanderly app.</p></div></body></html>"""
    }

    private fun redirect(status: String, bookingId: String): ResponseEntity<Void> {
        val location = UriComponentsBuilder.fromPath("/payments/result")
            .queryParam("status", status).queryParam("bookingId", bookingId).build().toUriString()
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, location).build()
    }
}
