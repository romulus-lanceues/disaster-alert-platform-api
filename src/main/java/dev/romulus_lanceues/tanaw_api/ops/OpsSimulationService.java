package dev.romulus_lanceues.tanaw_api.ops;

import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleNotFoundException;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleRepository;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventNotFoundException;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulateEarthquakeRequest;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulatedEarthquakeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(OpsSimulationProperties.class)
@Transactional(readOnly = true)
public class OpsSimulationService {

    private final OpsSimulationRepository opsSimulationRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final Clock clock;

    @Transactional
    public SimulatedEarthquakeResponse simulateEarthquake(SimulateEarthquakeRequest request) {
        AlertRule rule = alertRuleRepository.findById(request.alertRuleId())
                .orElseThrow(() -> new AlertRuleNotFoundException("Alert rule not found: " + request.alertRuleId()));

        if (!rule.isEnabled()) {
            throw new IllegalStateException("Alert rule is disabled: " + request.alertRuleId());
        }

        double baseLat = rule.getLocation().getLatitude();
        double baseLon = rule.getLocation().getLongitude();

        double offsetKm = request.offsetKm() != null ? request.offsetKm() : 0.0;
        double simLat = baseLat + (offsetKm / 111.0);
        double simLon = baseLon;

        double magnitude;
        if (request.magnitude() != null) {
            magnitude = request.magnitude();
        } else if (rule.getMinimumMagnitude() != null) {
            magnitude = rule.getMinimumMagnitude() + 0.5;
        } else {
            magnitude = 5.5;
        }

        String severity = rule.getMinimumSeverity() != null ? rule.getMinimumSeverity() : "MODERATE";

        UUID disasterEventId = UUID.randomUUID();
        String externalId = "sim-eq-" + disasterEventId;
        String place = "[TEST] Simulated earthquake near " + rule.getLocation().getName();

        Instant now = clock.instant();

        opsSimulationRepository.insertSimulatedDisasterEvent(disasterEventId, externalId, now, place, severity, "{}");
        opsSimulationRepository.insertEarthquake(disasterEventId, simLat, simLon, magnitude, 10.0);
        Optional<UUID> alertIdOpt = opsSimulationRepository.createTargetedAlert(disasterEventId, rule.getId(), now);

        boolean matched = alertIdOpt.isPresent();
        UUID alertId = alertIdOpt.orElse(null);
        String message = matched
                ? "Simulated earthquake created and alert triggered for rule " + rule.getId()
                : "Simulated earthquake created but no alert matched for rule " + rule.getId();

        log.info("Simulated earthquake {} created for rule {}. Matched alert: {}", disasterEventId, rule.getId(), matched);

        return new SimulatedEarthquakeResponse(
                disasterEventId,
                externalId,
                simLat,
                simLon,
                magnitude,
                offsetKm,
                matched,
                alertId,
                rule.getLocation().getName(),
                message
        );
    }

    @Transactional
    public void cleanup(UUID disasterEventId) {
        int rows = opsSimulationRepository.deleteSimulation(disasterEventId);
        if (rows == 0) {
            throw new DisasterEventNotFoundException("Simulated disaster event not found: " + disasterEventId);
        }
        log.info("Simulated disaster event {} cleaned up successfully", disasterEventId);
    }
}
