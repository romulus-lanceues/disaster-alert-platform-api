package dev.romulus_lanceues.tanaw_api.location;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LocationRequest(
        @NotNull(message = "User ID is required")
        UUID userId,

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address,

        @NotBlank(message = "Geographic area code is required")
        String geographicAreaCode,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be greater than or equal to -90")
        @DecimalMax(value = "90.0", message = "Latitude must be less than or equal to 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be greater than or equal to -180")
        @DecimalMax(value = "180.0", message = "Longitude must be less than or equal to 180")
        Double longitude
) {
}
