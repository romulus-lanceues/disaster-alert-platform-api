package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

@Schema(description = "Payload required to create an alert rule")
public record AlertRuleRequest(
        @Schema(
                description = "Unique UUID identifier of the user who owns the location",
                example = "123e4567-e89b-12d3-a456-426614174000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "User ID is required")
        UUID userId,

        @Schema(
                description = "Unique UUID identifier of the location to monitor",
                example = "123e4567-e89b-12d3-a456-426614174001",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Location ID is required")
        UUID locationId,

        @Schema(
                description = "Type of disaster that should trigger this rule",
                example = "EARTHQUAKE",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Disaster type is required")
        DisasterType disasterType,

        @Schema(
                description = "Minimum earthquake magnitude that triggers the rule; omit to disable this filter",
                example = "5.0",
                minimum = "0",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @PositiveOrZero(message = "Minimum magnitude must be zero or positive")
        Double minimumMagnitude,

        @Schema(
                description = "Radius around the location in kilometers; omit to disable this filter",
                example = "50.0",
                minimum = "0",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @PositiveOrZero(message = "Radius in km must be zero or positive")
        Double radiusKm,

        @Schema(
                description = "Minimum alert severity that triggers the rule",
                example = "MODERATE",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String minimumSeverity
) {
}
