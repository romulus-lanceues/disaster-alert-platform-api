package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.geo.area.GeoAreaSummary;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;

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

    public static LocationResponse from(Location location) {
        if (location == null) {
            return null;
        }

        GeographicArea area = location.getGeographicArea();
        GeoAreaSummary geoAreaSummary = area != null
                ? new GeoAreaSummary(
                        area.getId(),
                        area.getPsgcCode(),
                        area.getName(),
                        area.getType()
                )
                : null;

        return new LocationResponse(
                location.getId(),
                location.getUser() != null ? location.getUser().getId() : null,
                location.getName(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude(),
                geoAreaSummary,
                location.getCreatedAt(),
                location.getUpdatedAt()
        );
    }
}
