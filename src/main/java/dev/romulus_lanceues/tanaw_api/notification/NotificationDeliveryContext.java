package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection containing all context required to render and dispatch
 * a notification.
 */
public record NotificationDeliveryContext(
        UUID notificationId,
        UUID alertId,
        NotificationChannel channel,
        String destination,
        String destinationHint,
        UUID endpointId,
        Boolean endpointEnabled,
        Instant endpointVerifiedAt,
        UUID userId,
        UserStatus userStatus,
        String locationName,
        String place,
        Instant occurredAt,
        String eventStatus,
        String severity,
        String usgsUrl,
        Double magnitude,
        Double depthKm,
        Double distanceKm
) {
    public boolean isEndpointPresent() {
        return endpointId != null;
    }

    public boolean isEndpointEnabled() {
        return Boolean.TRUE.equals(endpointEnabled);
    }

    public boolean isEndpointVerified() {
        return endpointVerifiedAt != null;
    }

    public boolean isUserActive() {
        return userStatus == UserStatus.ACTIVE;
    }
}
