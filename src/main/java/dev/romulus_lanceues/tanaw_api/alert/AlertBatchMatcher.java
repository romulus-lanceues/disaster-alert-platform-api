package dev.romulus_lanceues.tanaw_api.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertBatchMatcher {

    private final AlertMatchingRepository repository;
    private final Clock clock;

    @Transactional
    public BatchMatchResult matchBatch(int batchSize) {
        List<UUID> claimedIds = repository.claimUnmatchedEvents(batchSize);
        if (claimedIds.isEmpty()) {
            return new BatchMatchResult(0, 0, List.of());
        }

        List<CreatedAlert> createdAlerts = repository.persistAlert(claimedIds);
        Instant now = Instant.now(clock);
        repository.markMatched(claimedIds, now);

        long matchedEventsCount = createdAlerts.stream()
                .map(CreatedAlert::disasterEventId)
                .distinct()
                .count();

        log.debug("Batch matched: claimed {} event(s), {} event(s) matched rules, created {} alert(s)",
                claimedIds.size(), matchedEventsCount, createdAlerts.size());

        return new BatchMatchResult(claimedIds.size(), createdAlerts.size(), createdAlerts);
    }
}
