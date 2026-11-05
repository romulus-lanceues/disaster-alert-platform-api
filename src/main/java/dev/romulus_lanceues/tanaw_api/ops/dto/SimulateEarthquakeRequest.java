package dev.romulus_lanceues.tanaw_api.ops.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SimulateEarthquakeRequest(
        @NotNull UUID alertRuleId,
        @DecimalMin("0.0") @DecimalMax("10.0") Double magnitude,
        @DecimalMin("0.0") @DecimalMax("1000.0") Double offsetKm
) {}
