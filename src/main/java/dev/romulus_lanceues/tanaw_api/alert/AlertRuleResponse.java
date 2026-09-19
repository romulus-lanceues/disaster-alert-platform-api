package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Alert rule response data")
public record AlertRuleResponse(
        @Schema(
                description = "Unique identifier of the alert rule",
                example = "123e4567-e89b-12d3-a456-426614174002"
        )
        UUID id,

        @Schema(
                description = "Unique identifier of the monitored location",
                example = "123e4567-e89b-12d3-a456-426614174001"
        )
        UUID locationId,

        @Schema(
                description = "User-defined name of the monitored location",
                example = "Home"
        )
        String locationName,

        @Schema(
                description = "Type of disaster monitored by the rule",
                example = "EARTHQUAKE"
        )
        DisasterType disasterType,

        @Schema(description = "Whether the alert rule is currently active", example = "true")
        boolean enabled,

        @Schema(
                description = "Minimum earthquake magnitude that triggers the rule; null means no magnitude filter",
                example = "5.0",
                nullable = true
        )
        Double minimumMagnitude,

        @Schema(
                description = "Radius around the location in kilometers; null means no radius filter",
                example = "50.0",
                nullable = true
        )
        Double radiusKm,

        @Schema(
                description = "Minimum alert severity that triggers the rule; null means no severity filter",
                example = "MODERATE",
                nullable = true
        )
        String minimumSeverity,

        @Schema(
                description = "Timestamp when the alert rule was created",
                example = "2026-09-18T10:00:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "Timestamp when the alert rule was last updated",
                example = "2026-09-18T10:00:00Z"
        )
        Instant updatedAt
) {

    public static AlertRuleResponse from(AlertRule alertRule) {
        return new AlertRuleResponse(
                alertRule.getId(),
                alertRule.getLocation().getId(),
                alertRule.getLocation().getName(),
                alertRule.getDisasterType(),
                alertRule.isEnabled(),
                alertRule.getMinimumMagnitude(),
                alertRule.getRadiusKm(),
                alertRule.getMinimumSeverity(),
                alertRule.getCreatedAt(),
                alertRule.getUpdatedAt()
        );
    }
}
