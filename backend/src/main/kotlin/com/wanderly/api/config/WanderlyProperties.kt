package com.wanderly.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Typed view of the `wanderly.*` settings in application.yml. */
@ConfigurationProperties(prefix = "wanderly")
data class WanderlyProperties(
    val jwt: Jwt,
    val cors: Cors,
    val uploads: Uploads,
    val seed: Seed,
    val payments: Payments,
    val push: Push,
) {
    data class Jwt(val secret: String, val expirationHours: Long)

    data class Cors(val allowedOrigins: List<String>)

    data class Uploads(val dir: String)

    data class Seed(val enabled: Boolean)

    /** [publicBaseUrl]: where SSLCommerz can reach this API for callbacks/IPN (e.g. the Render URL). */
    data class Payments(val publicBaseUrl: String, val sslcommerz: SslCommerz)

    /** Sandbox defaults are SSLCommerz's public test store; set real credentials via env for live mode. */
    data class SslCommerz(val storeId: String, val storePassword: String, val sandbox: Boolean) {
        val baseUrl: String
            get() = if (sandbox) "https://sandbox.sslcommerz.com" else "https://securepay.sslcommerz.com"
    }

    /** Base64 of a Firebase service-account JSON; empty = push disabled. */
    data class Push(val firebaseCredentialsBase64: String = "") {
        val enabled: Boolean get() = firebaseCredentialsBase64.isNotBlank()
    }
}
