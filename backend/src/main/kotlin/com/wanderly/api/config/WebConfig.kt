package com.wanderly.api.config

import com.wanderly.api.security.JwtAuthenticationFilter
import com.wanderly.api.service.FileStorageService
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, jwtFilter: JwtAuthenticationFilter): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            // Public: auth, catalogue browsing, tools, docs, uploaded images
            it.requestMatchers("/api/auth/**").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/destinations/**", "/api/hotels/**").permitAll()
            it.requestMatchers("/api/currency/**", "/api/ai/**", "/api/packing/**").permitAll()
            it.requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
            // WebSocket: authenticated by JwtHandshakeInterceptor (token in header or ?token=)
            it.requestMatchers("/ws", "/ws/**").permitAll()
            // Payment gateway callbacks come from the user's browser / SSLCommerz, not the app.
            // They are trusted only after server-side validation with SSLCommerz (PaymentService).
            it.requestMatchers("/api/payments/sslcommerz/**", "/payments/result").permitAll()
            it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            it.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
            // Everything else (trips, bookings, payments, wishlist, budget, journal…) needs a JWT
            it.anyRequest().authenticated()
        }
        .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(props: WanderlyProperties): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = props.cors.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Location")
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}

@Configuration
class WebConfig(private val files: FileStorageService) : WebMvcConfigurer {

    /** Serves uploaded journal photos at /uploads/{file}. */
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(files.root.toUri().toString())
    }

    /** Swagger UI with an "Authorize" button for the JWT. */
    @Bean
    fun wanderlyOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info().title("Wanderly API").version("2.0.0")
                .description("Kotlin + Spring Boot backend for the Wanderly – Explore Beyond travel app. " +
                    "Real-time events: WebSocket at /ws?token=<JWT>.")
        )
        .components(
            Components().addSecuritySchemes(
                "bearer", SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("bearer"))
}
