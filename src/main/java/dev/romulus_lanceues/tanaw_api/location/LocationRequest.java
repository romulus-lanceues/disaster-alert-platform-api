package dev.romulus_lanceues.tanaw_api.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Payload required to create a new location")
public record LocationRequest(
        @Schema(
                description = "Unique UUID identifier of the user who owns this location",
                example = "123e4567-e89b-12d3-a456-426614174000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "User ID is required")
        UUID userId,

        @Schema(
                description = "User-defined name or label for the location (maximum 100 characters)",
                example = "Home",
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(
                description = "Physical street address of the location (maximum 255 characters)",
                example = "123 Rizal St",
                maxLength = 255,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address,

        @Schema(
                description = "PSGC code corresponding to the geographic area",
                example = "137600000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Geographic area code is required")
        String geographicAreaCode,

        @Schema(
                description = "Latitude coordinate in degrees between -90 and 90",
                example = "14.60",
                minimum = "-90.0",
                maximum = "90.0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be greater than or equal to -90")
        @DecimalMax(value = "90.0", message = "Latitude must be less than or equal to 90")
        Double latitude,

        @Schema(
                description = "Longitude coordinate in degrees between -180 and 180",
                example = "120.98",
                minimum = "-180.0",
                maximum = "180.0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be greater than or equal to -180")
        @DecimalMax(value = "180.0", message = "Longitude must be less than or equal to 180")
        Double longitude
) {
}
