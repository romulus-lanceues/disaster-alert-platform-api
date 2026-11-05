package dev.romulus_lanceues.tanaw_api.ops.dto;

import java.util.UUID;

public record SimulatedEarthquakeResponse(
        UUID disasterEventId,
        String externalId,
        double latitude,
        double longitude,
        double magnitude,
        Double distanceKm,
        boolean matched,
        UUID alertId,
        String locationName,
        String message
) {}
