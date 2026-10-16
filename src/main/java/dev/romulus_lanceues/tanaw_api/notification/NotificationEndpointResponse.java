package dev.romulus_lanceues.tanaw_api.notification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Notification delivery endpoint configuration for a user")
public record NotificationEndpointResponse(
        @Schema(description = "Unique identifier of the notification endpoint", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID id,

        @Schema(description = "Unique identifier of the user who owns this endpoint", example = "123e4567-e89b-12d3-a456-426614174002")
        UUID userId,

        @Schema(description = "Notification delivery channel", example = "DISCORD")
        NotificationChannel channel,

        @Schema(description = "Masked destination hint safe for display", example = "Discord webhook ...a1b2")
        String destinationHint,

        @Schema(description = "Whether the endpoint is enabled for dispatching notifications", example = "true")
        boolean enabled,

        @Schema(description = "Timestamp when the endpoint was verified via a test message", example = "2026-09-18T10:00:00Z", nullable = true)
        Instant verifiedAt,

        @Schema(description = "Reason the endpoint was disabled, if applicable", example = "User disabled endpoint", nullable = true)
        String disabledReason,

        @Schema(description = "Timestamp when the endpoint was created", example = "2026-09-18T10:00:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp when the endpoint was last updated", example = "2026-09-18T10:00:00Z")
        Instant updatedAt
) {
    public static NotificationEndpointResponse from(NotificationEndpoint endpoint) {
        return new NotificationEndpointResponse(
                endpoint.getId(),
                endpoint.getUser().getId(),
                endpoint.getChannel(),
                endpoint.getDestinationHint(),
                endpoint.isEnabled(),
                endpoint.getVerifiedAt(),
                endpoint.getDisabledReason(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}
