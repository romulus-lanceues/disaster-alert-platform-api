package dev.romulus_lanceues.tanaw_api.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        @NotNull Boolean dryRun,
        @NotNull @Valid Dispatcher dispatcher,
        @NotNull @Valid Retry retry,
        @NotNull @Valid Fanout fanout,
        @NotNull @Valid Discord discord,
        @NotNull @Valid Crypto crypto,
        @Valid Telegram telegram
) {

    public NotificationProperties {
        Objects.requireNonNull(dryRun, "Notification dry run must not be null.");
        Objects.requireNonNull(dispatcher, "Notification dispatcher configuration must not be null.");
        Objects.requireNonNull(retry, "Notification retry configuration must not be null.");
        Objects.requireNonNull(fanout, "Notification fanout configuration must not be null.");
        Objects.requireNonNull(discord, "Notification discord configuration must not be null.");
        Objects.requireNonNull(crypto, "Notification crypto configuration must not be null.");
    }

    public record Dispatcher(
            @NotNull Boolean enabled,
            @NotNull Duration interval,
            @NotNull @Positive Integer batchSize,
            @NotNull Duration lease
    ) {
        public Dispatcher {
            Objects.requireNonNull(enabled, "Dispatcher enabled must not be null.");
            Objects.requireNonNull(interval, "Dispatcher interval must not be null.");
            Objects.requireNonNull(batchSize, "Dispatcher batch size must not be null.");
            Objects.requireNonNull(lease, "Dispatcher lease must not be null.");

            if (interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException("Dispatcher interval must be positive.");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("Dispatcher batch size must be positive.");
            }
            if (lease.isZero() || lease.isNegative()) {
                throw new IllegalArgumentException("Dispatcher lease must be positive.");
            }
        }
    }

    public record Retry(
            @NotNull @Positive Integer maxAttempts,
            @NotNull Duration initialBackoff,
            @NotNull Duration maxBackoff
    ) {
        public Retry {
            Objects.requireNonNull(maxAttempts, "Retry max attempts must not be null.");
            Objects.requireNonNull(initialBackoff, "Retry initial backoff must not be null.");
            Objects.requireNonNull(maxBackoff, "Retry max backoff must not be null.");

            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("Retry max attempts must be positive.");
            }
            if (initialBackoff.isZero() || initialBackoff.isNegative()) {
                throw new IllegalArgumentException("Retry initial backoff must be positive.");
            }
            if (maxBackoff.isZero() || maxBackoff.isNegative()) {
                throw new IllegalArgumentException("Retry max backoff must be positive.");
            }
            if (maxBackoff.compareTo(initialBackoff) < 0) {
                throw new IllegalArgumentException("Retry max backoff must be greater than or equal to initial backoff.");
            }
        }
    }

    public record Fanout(
            @NotNull Duration maxEventAge
    ) {
        public Fanout {
            Objects.requireNonNull(maxEventAge, "Fanout max event age must not be null.");
            if (maxEventAge.isZero() || maxEventAge.isNegative()) {
                throw new IllegalArgumentException("Fanout max event age must be positive.");
            }
        }
    }

    public record Discord(
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout
    ) {
        public Discord {
            Objects.requireNonNull(connectTimeout, "Discord connect timeout must not be null.");
            Objects.requireNonNull(readTimeout, "Discord read timeout must not be null.");

            if (connectTimeout.isZero() || connectTimeout.isNegative()) {
                throw new IllegalArgumentException("Discord connect timeout must be positive.");
            }
            if (readTimeout.isZero() || readTimeout.isNegative()) {
                throw new IllegalArgumentException("Discord read timeout must be positive.");
            }
        }
    }

    public record Crypto(
            @NotBlank String key
    ) {
        public Crypto {
            Objects.requireNonNull(key, "Notification crypto key must not be null.");
            if (key.isBlank()) {
                throw new IllegalArgumentException("Notification crypto key must not be blank.");
            }
        }
    }

    public record Telegram(
            String botToken,
            String botUsername
    ) {}
}
