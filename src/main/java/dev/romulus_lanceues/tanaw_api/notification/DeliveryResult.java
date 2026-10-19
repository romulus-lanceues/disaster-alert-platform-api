package dev.romulus_lanceues.tanaw_api.notification;

import java.time.Duration;

/**
 * Sealed hierarchy representing the outcome of dispatching an alert notification
 * via a provider-specific sender.
 */
public sealed interface DeliveryResult permits
        DeliveryResult.Sent,
        DeliveryResult.Retryable,
        DeliveryResult.Permanent {

    /**
     * Successful delivery.
     *
     * @param providerMessageId provider-assigned message or interaction ID, or null/fallback
     *                          if the provider returned HTTP 204 No Content or during dry-run.
     */
    record Sent(String providerMessageId) implements DeliveryResult {
        public Sent() {
            this(null);
        }
    }

    /**
     * Transient failure eligible for retry with backoff.
     *
     * @param reason     sanitized, non-sensitive reason description (e.g. HTTP 429, timeout, 5xx)
     * @param retryAfter optional provider-recommended delay before retry, or null to use
     *                   exponential backoff
     */
    record Retryable(String reason, Duration retryAfter) implements DeliveryResult {
        public Retryable {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be null or blank");
            }
        }

        public Retryable(String reason) {
            this(reason, null);
        }
    }

    /**
     * Permanent failure where further retries of this notification will not succeed.
     *
     * @param reason             sanitized, non-sensitive failure reason description
     * @param invalidateEndpoint true if the destination is dead/revoked (e.g. 401/404) and
     *                           the endpoint should be disabled
     */
    record Permanent(String reason, boolean invalidateEndpoint) implements DeliveryResult {
        public Permanent {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be null or blank");
            }
        }

        public Permanent(String reason) {
            this(reason, false);
        }
    }

    default boolean isSent() {
        return this instanceof Sent;
    }

    default boolean isRetryable() {
        return this instanceof Retryable;
    }

    default boolean isPermanent() {
        return this instanceof Permanent;
    }
}
