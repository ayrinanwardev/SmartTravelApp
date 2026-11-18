package com.wanderly.api.security;

import com.wanderly.api.config.WanderlyProperties;
import com.wanderly.api.domain.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and validates HS256 JWT access tokens. The subject is the user's email. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(WanderlyProperties props) {
        String secret = props.jwt().secret();
        byte[] bytes;
        try {
            bytes = secret == null ? new byte[0] : Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException e) {
            bytes = new byte[0];
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be a Base64 string of at least 32 bytes (256 bits). "
                            + "Remove it from .env to use the dev key, or generate one as shown in .env.example.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofHours(props.jwt().expirationHours());
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("name", user.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** Returns the email in the token, or empty if the token is invalid or expired. */
    public Optional<String> validate(String token) {
        try {
            return Optional.ofNullable(
                    Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
