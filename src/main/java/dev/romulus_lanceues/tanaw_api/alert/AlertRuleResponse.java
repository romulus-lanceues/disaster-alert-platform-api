package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;

import java.time.Instant;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        UUID locationId,
        String locationName,
        DisasterType disasterType,
        boolean enabled,
        Double minimumMagnitude,
        Double radiusKm,
        String minimumSeverity,
        Instant createdAt,
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