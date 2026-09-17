package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    private final AlertRepository alertRepository;


    @Transactional
    public Alert createAlert(DisasterEvent disasterEvent, AlertRule alertRule) {
        log.info("Creating alert for disaster event {} and alert rule {}",
                disasterEvent.getId(), alertRule.getId());

        if (alertRepository.existsByDisasterEventIdAndAlertRuleId(disasterEvent.getId(), alertRule.getId())) {
            throw new AlertAlreadyExistsException(
                    "Alert already exists for disaster event %s and alert rule %s"
                            .formatted(disasterEvent.getId(), alertRule.getId())
            );
        }

        Alert alert = Alert.builder()
                .disasterEvent(disasterEvent)
                .alertRule(alertRule)
                .status(AlertStatus.PENDING)
                .triggeredAt(Instant.now())
                .build();

        return alertRepository.save(alert);
    }

    public Alert getAlert(UUID id) {
        log.info("Fetching alert with ID {}", id);

        return alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException("Alert not found: " + id));
    }

    public Alert getAlertWithDetails(UUID id) {
        log.info("Fetching alert with details for ID {}", id);

        return alertRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AlertNotFoundException("Alert not found: " + id));
    }

    public Alert getAlertForUser(UUID id, UUID userId) {
        log.info("Fetching alert {} for user {}", id, userId);

        Alert alert = alertRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AlertNotFoundException("Alert not found: " + id));

        UUID ownerId = alert.getAlertRule().getLocation().getUser().getId();
        if (!ownerId.equals(userId)) {
            throw new AlertNotFoundException("Alert not found: " + id);
        }

        return alert;
    }

    public Page<Alert> getAlertsByUser(UUID userId, Pageable pageable) {
        log.info("Fetching paged alerts for user {}, page: {}, size: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return alertRepository.findByAlertRuleLocationUserId(userId, pageable);
    }

    public Page<Alert> getAlertsByUserAndStatus(UUID userId, AlertStatus status, Pageable pageable) {
        log.info("Fetching paged alerts for user {} with status {}, page: {}, size: {}",
                userId, status, pageable.getPageNumber(), pageable.getPageSize());

        return alertRepository.findByAlertRuleLocationUserIdAndStatus(userId, status, pageable);
    }


    public List<Alert> getAlertsByDisasterEvent(UUID disasterEventId) {
        log.info("Fetching alerts for disaster event {}", disasterEventId);

        return alertRepository.findByDisasterEventId(disasterEventId);
    }


    public List<Alert> getAlertsByAlertRule(UUID alertRuleId) {
        log.info("Fetching alerts for alert rule {}", alertRuleId);

        return alertRepository.findByAlertRuleId(alertRuleId);
    }


    public List<Alert> getAlertsByStatus(AlertStatus status) {
        log.info("Fetching alerts with status {}", status);

        return alertRepository.findByStatus(status);
    }


    @Transactional
    public Alert updateAlertStatus(UUID alertId, AlertStatus status) {
        log.info("Updating status of alert {} to {}", alertId, status);
        return changeAlertStatus(alertId, status);
    }


    @Transactional
    public Alert markAsProcessed(UUID alertId) {
        log.info("Marking alert {} as PROCESSED", alertId);
        return changeAlertStatus(alertId, AlertStatus.PROCESSED);
    }


    @Transactional
    public Alert markAsCancelled(UUID alertId) {
        log.info("Marking alert {} as CANCELLED", alertId);
        return changeAlertStatus(alertId, AlertStatus.CANCELLED);
    }


    private Alert changeAlertStatus(UUID alertId, AlertStatus status) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException("Alert not found: " + alertId));

        alert.updateStatus(status);
        return alertRepository.save(alert);
    }
}
