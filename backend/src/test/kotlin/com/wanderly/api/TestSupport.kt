package com.wanderly.api

import com.wanderly.api.payment.PaymentGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Stands in for SSLCommerz in tests: sessions are recorded, and `validate("val-<tranId>")`
 * confirms the recorded amount — so tests can simulate genuine and forged callbacks offline.
 */
class FakePaymentGateway : PaymentGateway {
    val sessions = ConcurrentHashMap<String, PaymentGateway.SessionRequest>()

    override fun createSession(request: PaymentGateway.SessionRequest): PaymentGateway.Session {
        sessions[request.tranId] = request
        return PaymentGateway.Session("https://sandbox.example/checkout/${request.tranId}", "session-${request.tranId}")
    }

    override fun validate(validationId: String): PaymentGateway.Validation {
        val tranId = validationId.removePrefix("val-")
        val s = sessions[tranId] ?: return PaymentGateway.Validation(false, null, null, null, null, "INVALID_TRANSACTION")
        return PaymentGateway.Validation(true, tranId, BigDecimal("${s.amount}.00"), s.currency, "BKASH-BKash", "VALID")
    }
}

@TestConfiguration
class TestPaymentConfig {
    @Bean
    @Primary
    fun fakePaymentGateway() = FakePaymentGateway()
}
