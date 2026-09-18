package dev.romulus_lanceues.tanaw_api.alert;

import jakarta.validation.constraints.PositiveOrZero;

public record AlertRuleThresholdRequest(
        @PositiveOrZero(message = "Minimum magnitude must be zero or positive")
        Double minimumMagnitude,

        @PositiveOrZero(message = "Radius in km must be zero or positive")
        Double radiusKm,

        String minimumSeverity
) {
}
