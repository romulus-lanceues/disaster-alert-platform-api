package dev.romulus_lanceues.tanaw_api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

@Validated
@ConfigurationProperties("security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenDuration,
        @NotNull Duration refreshTokenDuration,
        @NotBlank String secret) {


    public JwtProperties {
        Objects.requireNonNull(accessTokenDuration,
                "JWT access token duration must not be null.");

        Objects.requireNonNull(refreshTokenDuration,
                "JWT refresh token duration must not be null.");

        if (accessTokenDuration.isZero() || accessTokenDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "JWT access token duration must be positive."
            );
        }

        if (refreshTokenDuration.isZero() || refreshTokenDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "JWT refresh token duration must be positive."
            );
        }

        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT secret must not be blank."
            );
        }

        byte[] decodedSecret;

        try {
            decodedSecret = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "JWT secret must be a valid Base64-encoded string.", e
            );
        }

        if (decodedSecret.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least 32 bytes "
                            + "after Base64 decoding."
            );
        }
    }

}
