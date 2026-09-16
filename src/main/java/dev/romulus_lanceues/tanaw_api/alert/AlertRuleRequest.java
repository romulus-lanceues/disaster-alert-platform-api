package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;

import java.util.UUID;

public record AlertRuleRequest(
        UUID userId,
        UUID locationId,
        DisasterType disasterType,
        Double minimumMagnitude,
        Double radiusKm,
        String minimumSeverity
) {
}
