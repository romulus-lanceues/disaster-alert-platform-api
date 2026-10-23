package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Pure domain policy calculating exponential backoff with jitter and retry exhaustion
 * for notification delivery attempts.
 */
@Component
public class NotificationRetryPolicy {

    private final NotificationProperties properties;
    private final DoubleSupplier jitterSupplier;

    @Autowired
    public NotificationRetryPolicy(NotificationProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    NotificationRetryPolicy(NotificationProperties properties, DoubleSupplier jitterSupplier) {
        this.properties = Objects.requireNonNull(properties, "NotificationProperties must not be null");
        this.jitterSupplier = Objects.requireNonNull(jitterSupplier, "jitterSupplier must not be null");
    }

    /**
     * Checks if the delivery attempts for a notification are exhausted.
     *
     * @param attemptCount number of attempts performed so far
     * @return true if attemptCount is greater than or equal to configured max-attempts
     */
    public boolean isExhausted(int attemptCount) {
        return attemptCount >= properties.retry().maxAttempts();
    }

    /**
     * Returns the configured maximum attempts.
     */
    public int maxAttempts() {
        return properties.retry().maxAttempts();
    }

    /**
     * Calculates the backoff duration for the next retry attempt using exponential backoff
     * with bounded jitter, honoring provider retry_after recommendations when larger.
     * Formula: min(initial * 2^(attempt - 1), max) ± jitter (capped at maxBackoff).
     *
     * @param attemptCount       current attempt number (1-based; <= 1 uses initialBackoff)
     * @param providerRetryAfter optional provider-suggested delay (e.g. from HTTP 429)
     * @return calculated delay Duration
     */
    public Duration calculateDelay(int attemptCount, Duration providerRetryAfter) {
        long initialMillis = properties.retry().initialBackoff().toMillis();
        long maxMillis = properties.retry().maxBackoff().toMillis();

        long baseMillis;
        if (attemptCount <= 1) {
            baseMillis = initialMillis;
        } else {
            int exponent = Math.min(attemptCount - 1, 30);
            long multiplier = 1L << exponent;
            if (multiplier > maxMillis / initialMillis) {
                baseMillis = maxMillis;
            } else {
                baseMillis = Math.min(initialMillis * multiplier, maxMillis);
            }
        }

        // Bounded jitter: ±20% (multiplier in [0.8, 1.2])
        double jitterFraction = 0.8 + (0.4 * jitterSupplier.getAsDouble());
        long jitteredMillis = Math.round(baseMillis * jitterFraction);
        long effectiveMillis = Math.min(Math.max(1L, jitteredMillis), maxMillis);

        if (providerRetryAfter != null && !providerRetryAfter.isNegative() && !providerRetryAfter.isZero()) {
            long providerMillis = providerRetryAfter.toMillis();
            if (providerMillis > effectiveMillis) {
                effectiveMillis = providerMillis;
            }
        }

        return Duration.ofMillis(effectiveMillis);
    }

    /**
     * Calculates the next attempt Instant by adding calculated delay to the current time.
     *
     * @param attemptCount       current attempt number (1-based)
     * @param providerRetryAfter optional provider-suggested delay
     * @param now                current time
     * @return next attempt Instant
     */
    public Instant calculateNextAttempt(int attemptCount, Duration providerRetryAfter, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        Duration delay = calculateDelay(attemptCount, providerRetryAfter);
        return now.plus(delay);
    }
}
