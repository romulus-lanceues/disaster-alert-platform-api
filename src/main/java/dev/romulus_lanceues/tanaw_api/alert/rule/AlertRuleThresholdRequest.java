package dev.romulus_lanceues.tanaw_api.alert.rule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Payload used to replace an alert rule's optional threshold filters")
public record AlertRuleThresholdRequest(
        @Schema(
                description = "Minimum earthquake magnitude; null clears this filter",
                example = "5.0",
                minimum = "0",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @PositiveOrZero(message = "Minimum magnitude must be zero or positive")
        Double minimumMagnitude,

        @Schema(
                description = "Radius around the location in kilometers; null clears this filter",
                example = "50.0",
                minimum = "0",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @PositiveOrZero(message = "Radius in km must be zero or positive")
        Double radiusKm,

        @Schema(
                description = "Minimum alert severity; null clears this filter",
                example = "MODERATE",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String minimumSeverity
) {
}
