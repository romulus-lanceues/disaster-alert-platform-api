package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.geo.area.GeoAreaSummary;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Location response data")
public record LocationResponse(
        @Schema(
                description = "Unique identifier of the location",
                example = "123e4567-e89b-12d3-a456-426614174000"
        )
        UUID id,

        @Schema(
                description = "Unique identifier of the user who owns this location",
                example = "123e4567-e89b-12d3-a456-426614174000"
        )
        UUID userId,

        @Schema(
                description = "User-defined name or label for the location",
                example = "Home"
        )
        String name,

        @Schema(
                description = "Physical street address of the location",
                example = "123 Rizal St"
        )
        String address,

        @Schema(
                description = "Latitude coordinate in degrees",
                example = "14.60"
        )
        double latitude,

        @Schema(
                description = "Longitude coordinate in degrees",
                example = "120.98"
        )
        double longitude,

        @Schema(
                description = "Summary of the associated geographic area"
        )
        GeoAreaSummary geographicArea,

        @Schema(
                description = "Timestamp when the location was created",
                example = "2026-09-18T10:00:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "Timestamp when the location was last updated",
                example = "2026-09-18T10:00:00Z"
        )
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
