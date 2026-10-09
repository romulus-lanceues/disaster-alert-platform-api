package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("usgs")
record UsgsProperties(
        @NotBlank String baseUrl,
        @NotNull @Positive Double minMagnitude,
        @NotNull Duration pollInterval,
        @NotNull @Valid BoundingBox boundingBox
) {

    record BoundingBox(
            @NotNull Double minLatitude,
            @NotNull Double maxLatitude,
            @NotNull Double minLongitude,
            @NotNull Double maxLongitude
    ) {}
}
