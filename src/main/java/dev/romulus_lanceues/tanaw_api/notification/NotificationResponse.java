package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID alertId,
        NotificationChannel channel,
        String destination,
        NotificationStatus status,
        int attemptCount,
        Instant sentAt,
        String failureReason,
        Instant createdAt,
        Instant triggeredAt,
        AlertStatus alertStatus,
        UUID locationId,
        String locationName,
        DisasterType disasterType,
        UUID disasterEventId,
        Instant occurredAt,
        Double magnitude,
        String severity,
        Double depthKm,
        Double latitude,
        Double longitude
) {
}
