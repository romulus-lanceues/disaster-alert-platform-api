package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.location.Location;

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

    public static NotificationResponse from(Notification notification) {
        if (notification == null) {
            return null;
        }

        Alert alert = notification.getAlert();
        DisasterEvent disasterEvent = alert != null ? alert.getDisasterEvent() : null;
        AlertRule alertRule = alert != null ? alert.getAlertRule() : null;
        Location location = alertRule != null ? alertRule.getLocation() : null;

        return new NotificationResponse(
                notification.getId(),
                alert != null ? alert.getId() : null,
                notification.getChannel(),
                notification.getDestination(),
                notification.getStatus(),
                notification.getAttemptCount(),
                notification.getSentAt(),
                notification.getFailureReason(),
                notification.getCreatedAt(),
                alert != null ? alert.getTriggeredAt() : null,
                alert != null ? alert.getStatus() : null,
                location != null ? location.getId() : null,
                location != null ? location.getName() : null,
                disasterEvent != null ? disasterEvent.getDisasterType() : (alertRule != null ? alertRule.getDisasterType() : null),
                disasterEvent != null ? disasterEvent.getId() : null,
                disasterEvent != null ? disasterEvent.getOccurredAt() : null,
                disasterEvent != null ? disasterEvent.getMagnitude() : null,
                disasterEvent != null ? disasterEvent.getSeverity() : null,
                disasterEvent != null ? disasterEvent.getDepthKm() : null,
                disasterEvent != null ? disasterEvent.getLatitude() : null,
                disasterEvent != null ? disasterEvent.getLongitude() : null
        );
    }
}

