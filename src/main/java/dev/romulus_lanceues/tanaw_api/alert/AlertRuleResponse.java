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
) {}