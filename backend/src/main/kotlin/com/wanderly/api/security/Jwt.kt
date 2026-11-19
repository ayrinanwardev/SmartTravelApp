package com.wanderly.api.security

import com.wanderly.api.config.WanderlyProperties
import com.wanderly.api.domain.User
import com.wanderly.api.repository.UserRepository
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/** Issues and validates HS256 JWT access tokens. The subject is the user's email. */
@Service
class JwtService(props: WanderlyProperties) {

    private val key: SecretKey
    private val ttl: Duration = Duration.ofHours(props.jwt.expirationHours)

    init {
        val bytes = runCatching { Decoders.BASE64.decode(props.jwt.secret.trim()) }.getOrDefault(ByteArray(0))
        check(bytes.size >= 32) {
            "JWT_SECRET must be a Base64 string of at least 32 bytes (256 bits). " +
                "Remove it from .env to use the dev key, or generate one as shown in .env.example."
        }
        key = Keys.hmacShaKeyFor(bytes)
    }

    val ttlSeconds: Long get() = ttl.seconds

    fun issue(user: User): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.email)
            .claim("uid", user.id)
            .claim("name", user.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact()
    }

    /** The email in the token, or null if the token is invalid or expired. */
    fun validate(token: String): String? = try {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload.subject
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Reads `Authorization: Bearer <jwt>`, validates it and puts the [User] entity into the
 * security context, so controllers can take `@AuthenticationPrincipal user: User`.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val users: UserRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().authentication == null) {
            jwtService.validate(header.substring(7))
                ?.let { users.findByEmailIgnoreCase(it).orElse(null) }
                ?.let { user ->
                    val auth = UsernamePasswordAuthenticationToken(user, null, user.authorities)
                    auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = auth
                }
        }
        chain.doFilter(request, response)
    }
}
