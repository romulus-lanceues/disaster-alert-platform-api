package dev.romulus_lanceues.tanaw_api.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tanaw.ops")
public record OpsSimulationProperties(
        boolean enabled,
        String simulationKey
) {
    public OpsSimulationProperties {
        if (enabled && (simulationKey == null || simulationKey.isBlank() || simulationKey.length() < 16)) {
            throw new IllegalStateException("tanaw.ops.simulation-key must be configured with at least 16 characters when tanaw.ops.enabled is true.");
        }
    }
}
