package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.geo.area.GeoAreaSummary;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        UUID userId,
        String name,
        String address,
        double latitude,
        double longitude,
        GeoAreaSummary geographicArea,
        Instant createdAt,
        Instant updatedAt
) {
}
