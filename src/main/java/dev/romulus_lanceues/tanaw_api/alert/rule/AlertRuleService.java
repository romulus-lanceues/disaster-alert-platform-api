package dev.romulus_lanceues.tanaw_api.alert.rule;

import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.location.LocationNotFoundException;
import dev.romulus_lanceues.tanaw_api.location.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public AlertRuleResponse createAlertRule(AlertRuleRequest request) {
        log.info("Creating alert rule for location {} with disaster type {}",
                request.locationId(), request.disasterType());

        Location location = locationRepository.findByIdAndUserId(request.locationId(), request.userId())
                .orElseThrow(() -> new LocationNotFoundException(
                        "Location not found: " + request.locationId()));

        AlertRule alertRule = AlertRule.builder()
                .location(location)
                .disasterType(request.disasterType())
                .enabled(true)
                .minimumMagnitude(request.minimumMagnitude())
                .radiusKm(request.radiusKm())
                .minimumSeverity(request.minimumSeverity())
                .build();

        return AlertRuleResponse.from(alertRuleRepository.save(alertRule));
    }

    public AlertRuleResponse getAlertRule(UUID alertRuleId, UUID userId) {
        log.info("Fetching alert rule {} for user {}", alertRuleId, userId);

        return alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId)
                .map(AlertRuleResponse::from)
                .orElseThrow(() -> new AlertRuleNotFoundException(
                        "Alert rule not found: " + alertRuleId));
    }

    public List<AlertRuleResponse> getAlertRulesByUser(UUID userId) {
        log.info("Fetching alert rules for user {}", userId);

        return alertRuleRepository.findByLocationUserId(userId)
                .stream()
                .map(AlertRuleResponse::from)
                .toList();
    }

    public List<AlertRuleResponse> getAlertRulesByLocation(UUID locationId, UUID userId) {
        log.info("Fetching alert rules for location {} and user {}", locationId, userId);

        return alertRuleRepository.findByLocationIdAndLocationUserId(locationId, userId)
                .stream()
                .map(AlertRuleResponse::from)
                .toList();
    }

    @Transactional
    public AlertRuleResponse updateThresholds(UUID alertRuleId, UUID userId,
                                              Double minimumMagnitude, Double radiusKm, String minimumSeverity) {
        log.info("Updating thresholds for alert rule {}", alertRuleId);

        AlertRule alertRule = alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId)
                .orElseThrow(() -> new AlertRuleNotFoundException(
                        "Alert rule not found: " + alertRuleId));

        alertRule.updateThresholds(minimumMagnitude, radiusKm, minimumSeverity);

        return AlertRuleResponse.from(alertRuleRepository.save(alertRule));
    }

    @Transactional
    public AlertRuleResponse enableAlertRule(UUID alertRuleId, UUID userId) {
        log.info("Enabling alert rule {}", alertRuleId);

        AlertRule alertRule = alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId)
                .orElseThrow(() -> new AlertRuleNotFoundException(
                        "Alert rule not found: " + alertRuleId));

        alertRule.enable();

        return AlertRuleResponse.from(alertRuleRepository.save(alertRule));
    }

    @Transactional
    public AlertRuleResponse disableAlertRule(UUID alertRuleId, UUID userId) {
        log.info("Disabling alert rule {}", alertRuleId);

        AlertRule alertRule = alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId)
                .orElseThrow(() -> new AlertRuleNotFoundException(
                        "Alert rule not found: " + alertRuleId));

        alertRule.disable();

        return AlertRuleResponse.from(alertRuleRepository.save(alertRule));
    }

    @Transactional
    public void deleteAlertRule(UUID alertRuleId, UUID userId) {
        log.info("Deleting alert rule {}", alertRuleId);

        AlertRule alertRule = alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId)
                .orElseThrow(() -> new AlertRuleNotFoundException(
                        "Alert rule not found: " + alertRuleId));

        alertRuleRepository.delete(alertRule);
    }
}

