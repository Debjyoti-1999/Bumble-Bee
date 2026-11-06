package com.datingapp.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret base64-encoded HMAC-SHA256 key of at least 32 bytes
 *               (generate with {@code openssl rand -base64 32})
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration ttl,
        @NotBlank String issuer
) {
}
