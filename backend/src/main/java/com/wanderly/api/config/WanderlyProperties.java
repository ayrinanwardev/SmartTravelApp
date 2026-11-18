package com.wanderly.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Typed view of the {@code wanderly.*} settings in application.yml. */
@ConfigurationProperties(prefix = "wanderly")
public record WanderlyProperties(Jwt jwt, Cors cors, Uploads uploads, Seed seed) {

    public record Jwt(String secret, long expirationHours) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Uploads(String dir) {
    }

    public record Seed(boolean enabled) {
    }
}
