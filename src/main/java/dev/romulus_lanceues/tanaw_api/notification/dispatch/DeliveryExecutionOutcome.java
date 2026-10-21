package dev.romulus_lanceues.tanaw_api.notification.dispatch;

/**
 * Result outcome of executing an individual notification delivery attempt.
 */
public enum DeliveryExecutionOutcome {
    SENT,
    RETRYABLE,
    PERMANENT_FAILURE,
    CANCELLED,
    FENCE_CONFLICT
}
