package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class UsgsEarthquakeIngestionService {

    private static final Duration MAX_CURSOR_LAG = Duration.ofMinutes(30);
    private static final Duration OVERLAP_BUFFER = Duration.ofMinutes(2);
    private static final Duration DEFAULT_LOOKBACK = Duration.ofHours(1);
    private static final Duration MAX_LOOKBACK = Duration.ofHours(24);

    private final UsgsClient usgsClient;
    private final Clock clock;
    private final DisasterEventProcessor disasterEventProcessor;

    private Instant lastSync;
    private Instant cursorHeldSince;

    public List<UUID> ingest() {
        if (lastSync == null) {
            lastSync = initializeLastSync();
        }

        List<UUID> persistedEventsId = new ArrayList<>();
        List<String> failedFeatureIds = new ArrayList<>();

        Instant started = Instant.now(clock);

        try {
            UsgsResponse response = usgsClient.fetchUpdatedAfter(lastSync.minus(OVERLAP_BUFFER));

            List<UsgsFeature> features = response != null && response.features() != null
                    ? response.features()
                    : List.of();

            log.info("USGS poll completed: {} earthquake(s) updated since {}", features.size(), lastSync);

            features.forEach(feat -> {
                try {
                    if (feat.properties() == null ||
                            !"earthquake".equalsIgnoreCase(feat.properties().type())) {
                        log.debug("Skipping non-earthquake event [{}] (type: {})",
                                feat.id(), feat.properties() != null ? feat.properties().type() : "null");
                        return;
                    }

                    if (feat.geometry() == null || feat.geometry().latitude() == null || feat.geometry().longitude() == null) {
                        log.warn("Skipping earthquake [{}] due to missing coordinates", feat.id());
                        return;
                    }

                    log.info("Earthquake [{}] mag={} depth={}km place=\"{}\" coords=[{}, {}] alert={} url={}, body={}",
                            feat.id(),
                            feat.properties().mag(),
                            feat.geometry().depthKm(),
                            feat.properties().place(),
                            feat.geometry().latitude(),
                            feat.geometry().longitude(),
                            feat.properties().alert(),
                            feat.properties().url(),
                            feat.rawPayload());

                    UUID persistedEventId = disasterEventProcessor.persistDisasterEvent(feat);

                    if (persistedEventId != null) {
                        persistedEventsId.add(persistedEventId);
                    }
                } catch (Exception e) {
                    failedFeatureIds.add(feat.id() != null ? feat.id() : "unknown");
                    log.error("An error occurred while processing event [{}]: {}", feat.id(), e.getMessage(), e);
                }
            });

            if (failedFeatureIds.isEmpty()) {
                lastSync = started;
                cursorHeldSince = null;
            } else {
                handleIngestionFailures(started, failedFeatureIds);
            }
        } catch (Exception ex) {
            log.error("USGS ingestion failed — will retry on next poll (lastSync unchanged: {})",
                    lastSync, ex);
        }

        return persistedEventsId;
    }

    private void handleIngestionFailures(Instant started, List<String> failedFeatureIds) {
        if (cursorHeldSince == null) {
            cursorHeldSince = started;
            log.warn("USGS ingestion encountered {} failed feature(s) {}. Holding cursor at {} to retry on next poll",
                    failedFeatureIds.size(), failedFeatureIds, lastSync);
            return;
        }

        Duration heldDuration = Duration.between(cursorHeldSince, started);
        if (heldDuration.compareTo(MAX_CURSOR_LAG) >= 0) {
            log.error("Poison guard triggered: cursor held at {} for {} (exceeds limit {}). Forcing cursor advance to {} and abandoning failed USGS feature(s): {}",
                    lastSync, heldDuration, MAX_CURSOR_LAG, started, failedFeatureIds);
            lastSync = started;
            cursorHeldSince = null;
        } else {
            log.warn("USGS ingestion encountered {} failed feature(s) {}. Cursor held at {} (held for {} / limit {}). Will retry on next poll",
                    failedFeatureIds.size(), failedFeatureIds, lastSync, heldDuration, MAX_CURSOR_LAG);
        }
    }

    private Instant initializeLastSync() {
        Instant now = Instant.now(clock);
        Instant earliestAllowed = now.minus(MAX_LOOKBACK);

        try {
            Optional<Instant> latestDbUpdate = disasterEventProcessor.findLatestSourceUpdatedAt("USGS");
            if (latestDbUpdate.isPresent()) {
                Instant maxUpdatedAt = latestDbUpdate.get();
                if (maxUpdatedAt.isBefore(earliestAllowed)) {
                    log.warn("Latest USGS event in database ({}) is older than max lookback ({}). Capping lastSync to {}",
                            maxUpdatedAt, MAX_LOOKBACK, earliestAllowed);
                    return earliestAllowed;
                }
                if (maxUpdatedAt.isAfter(now)) {
                    log.warn("Latest USGS event in database ({}) is in the future. Clamping lastSync to now ({})",
                            maxUpdatedAt, now);
                    return now;
                }
                log.info("Initialized lastSync from database to {} (latest USGS event)", maxUpdatedAt);
                return maxUpdatedAt;
            }
        } catch (Exception ex) {
            log.error("Failed to query latest USGS event from database — falling back to default lookback", ex);
        }

        Instant fallback = now.minus(DEFAULT_LOOKBACK);
        log.info("No prior USGS events found in database; initializing lastSync to default lookback {}", fallback);
        return fallback;
    }
}
