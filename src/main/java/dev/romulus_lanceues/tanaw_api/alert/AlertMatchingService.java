package dev.romulus_lanceues.tanaw_api.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AlertMatchingProperties.class)
@Slf4j
public class AlertMatchingService {

    private final AlertBatchMatcher batchMatcher;
    private final AlertMatchingRepository repository;
    private final AlertMatchingProperties properties;
    private final Clock clock;

    /**
     * Drains pending unmatched disaster events in batches, executing each batch
     * in its own short transaction through AlertBatchMatcher.
     */
    public DrainResult drainPendingEvents() {
        int batchSize = properties != null ? properties.batchSize() : 200;
        int totalBatches = 0;
        int totalClaimed = 0;
        int totalAlertsCreated = 0;
        List<CreatedAlert> allCreatedAlerts = new ArrayList<>();

        while (true) {
            BatchMatchResult batchResult = batchMatcher.matchBatch(batchSize);
            if (batchResult.claimedCount() == 0) {
                break;
            }

            totalBatches++;
            totalClaimed += batchResult.claimedCount();
            totalAlertsCreated += batchResult.createdAlertsCount();
            allCreatedAlerts.addAll(batchResult.createdAlerts());

            if (batchResult.claimedCount() < batchSize) {
                break;
            }
        }

        if (totalClaimed > 0) {
            log.info("Alert matching drain completed: {} batch(es), claimed {} event(s), created {} alert(s)",
                    totalBatches, totalClaimed, totalAlertsCreated);
        }

        checkBacklogLag();

        return new DrainResult(totalBatches, totalClaimed, totalAlertsCreated, allCreatedAlerts);
    }

    private void checkBacklogLag() {
        try {
            Optional<Instant> oldest = repository.findOldestUnmatchedEventTime();
            if (oldest.isPresent()) {
                Instant now = Instant.now(clock);
                Duration age = Duration.between(oldest.get(), now);
                Duration warnThreshold = properties != null ? properties.backlogWarnAge() : Duration.ofMinutes(10);
                if (age.compareTo(warnThreshold) > 0) {
                    log.warn("Oldest unmatched disaster event is lagging by {} seconds (threshold: {}s, source_updated_at: {})",
                            age.toSeconds(), warnThreshold.toSeconds(), oldest.get());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check unmatched backlog age: {}", e.getMessage());
        }
    }
}
