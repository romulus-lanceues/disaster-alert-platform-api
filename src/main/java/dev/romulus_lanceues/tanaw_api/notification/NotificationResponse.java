package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Notification delivery history and its associated alert and disaster details")
public record NotificationResponse(
        @Schema(description = "Unique identifier of the notification", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID id,
        @Schema(description = "Unique identifier of the alert that triggered the notification", example = "123e4567-e89b-12d3-a456-426614174002")
        UUID alertId,
        @Schema(description = "Delivery channel used for the notification", example = "EMAIL")
        NotificationChannel channel,
        @Schema(description = "Delivery destination, such as an email address or messaging handle", example = "user@example.com")
        String destination,
        @Schema(description = "Current delivery status", example = "SENT")
        NotificationStatus status,
        @Schema(description = "Number of delivery attempts made", example = "1")
        int attemptCount,
        @Schema(description = "Timestamp when the notification was successfully sent", example = "2026-09-18T10:00:00Z", nullable = true)
        Instant sentAt,
        @Schema(description = "Reason the delivery failed, if applicable", example = "Connection timeout", nullable = true)
        String failureReason,
        @Schema(description = "Timestamp when the notification was created", example = "2026-09-18T10:00:00Z")
        Instant createdAt,
        @Schema(description = "Timestamp when the associated alert was triggered", example = "2026-09-18T09:59:00Z")
        Instant triggeredAt,
        @Schema(description = "Status of the associated alert", example = "PENDING")
        AlertStatus alertStatus,
        @Schema(description = "Unique identifier of the location monitored by the alert rule", example = "123e4567-e89b-12d3-a456-426614174003")
        UUID locationId,
        @Schema(description = "User-defined name of the monitored location", example = "Home")
        String locationName,
        @Schema(description = "Type of the associated disaster", example = "EARTHQUAKE")
        DisasterType disasterType,
        @Schema(description = "Unique identifier of the associated disaster event", example = "123e4567-e89b-12d3-a456-426614174004")
        UUID disasterEventId,
        @Schema(description = "Timestamp when the disaster occurred", example = "2026-09-18T09:55:00Z")
        Instant occurredAt,
        @Schema(description = "Reported disaster magnitude, when applicable", example = "5.2", nullable = true)
        Double magnitude,
        @Schema(description = "Reported disaster severity", example = "MODERATE", nullable = true)
        String severity,
        @Schema(description = "Reported disaster depth in kilometers, when applicable", example = "10.0", nullable = true)
        Double depthKm,
        @Schema(description = "Latitude of the disaster event in degrees", example = "14.60")
        Double latitude,
        @Schema(description = "Longitude of the disaster event in degrees", example = "120.98")
        Double longitude
) {
}
