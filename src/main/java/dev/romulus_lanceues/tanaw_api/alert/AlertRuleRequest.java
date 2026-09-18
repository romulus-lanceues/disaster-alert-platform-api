package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record AlertRuleRequest(
        @NotNull(message = "User ID is required")
        UUID userId,

        @NotNull(message = "Location ID is required")
        UUID locationId,

        @NotNull(message = "Disaster type is required")
        DisasterType disasterType,

        @PositiveOrZero(message = "Minimum magnitude must be zero or positive")
        Double minimumMagnitude,

        @PositiveOrZero(message = "Radius in km must be zero or positive")
        Double radiusKm,

        String minimumSeverity
) {
}
