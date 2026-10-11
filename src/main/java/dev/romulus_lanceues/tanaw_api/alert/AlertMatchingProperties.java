package dev.romulus_lanceues.tanaw_api.alert;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "alert.matching")
public record AlertMatchingProperties(
        @Positive Integer batchSize,
        @NotNull Duration backlogWarnAge
) {
    public AlertMatchingProperties(
            @DefaultValue("200") Integer batchSize,
            @DefaultValue("PT10M") Duration backlogWarnAge
    ) {
        this.batchSize = (batchSize != null && batchSize > 0) ? batchSize : 200;
        this.backlogWarnAge = (backlogWarnAge != null) ? backlogWarnAge : Duration.ofMinutes(10);
    }
}
