package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import java.util.UUID;

/**
 * Represents a claimed notification with its fencing token (attempt count).
 */
public record ClaimedNotification(
        UUID id,
        int attemptCount
) {
}
