package dev.romulus_lanceues.tanaw_api.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Slim DTO returned by command methods (status transitions).
 * Contains only notification-level columns — no alert, disaster, or location
 * fields — so it never triggers lazy-loading of the entity graph.
 *
 * <p>{@code notification.getAlert().getId()} reads the FK column already
 * present on the managed entity and does not initialise the Alert proxy.</p>
 */
public record NotificationStatusResponse(
        UUID id,
        UUID alertId,
        NotificationChannel channel,
        String destination,
        NotificationStatus status,
        int attemptCount,
        Instant sentAt,
        String failureReason,
        Instant createdAt
) {

    public static NotificationStatusResponse from(Notification notification) {
        return new NotificationStatusResponse(
                notification.getId(),
                notification.getAlert().getId(),
                notification.getChannel(),
                notification.getDestination(),
                notification.getStatus(),
                notification.getAttemptCount(),
                notification.getSentAt(),
                notification.getFailureReason(),
                notification.getCreatedAt()
        );
    }
}
