package dev.romulus_lanceues.tanaw_api.alert;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "alert.matching")
public record AlertMatchingProperties(
        @NotNull @Positive Integer batchSize,
        @NotNull Duration backlogWarnAge,
        @NotNull @Positive Integer maxBatchesPerDrain
) {

    public AlertMatchingProperties {
        Objects.requireNonNull(batchSize,
                "Alert matching batch size must not be null.");

        Objects.requireNonNull(backlogWarnAge,
                "Alert matching backlog warn age must not be null.");

        Objects.requireNonNull(maxBatchesPerDrain,
                "Alert matching max batches per drain must not be null.");

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Alert matching batch size must be positive."
            );
        }

        if (backlogWarnAge.isZero() || backlogWarnAge.isNegative()) {
            throw new IllegalArgumentException(
                    "Alert matching backlog warn age must be positive."
            );
        }

        if (maxBatchesPerDrain <= 0) {
            throw new IllegalArgumentException(
                    "Alert matching max batches per drain must be positive."
            );
        }
    }
}
