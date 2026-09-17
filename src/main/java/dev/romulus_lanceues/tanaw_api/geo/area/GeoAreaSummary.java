package dev.romulus_lanceues.tanaw_api.geo.area;

import java.util.UUID;

public record GeoAreaSummary(
        UUID id,
        String psgcCode,
        String name,
        GeographicAreaType type
) {
}
