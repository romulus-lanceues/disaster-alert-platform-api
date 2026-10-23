package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.alert.AlertMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsgsSyncOrchestrator {
    private final UsgsEarthquakeIngestionService ingestionService;
    private final AlertMatchingService alertMatchingService;

    @Scheduled(fixedDelayString = "${usgs.poll-interval}")
    public void sync() {
        try {
            List<UUID> persistedEventsId = ingestionService.ingest();
            if (persistedEventsId != null && !persistedEventsId.isEmpty()) {
                log.info("USGS ingestion persisted {} event(s)", persistedEventsId.size());
            }
        } catch (Exception e) {
            log.error("USGS ingestion failed unexpectedly during sync", e);
        }

        try {
            alertMatchingService.drainPendingEvents();
        } catch (Exception e) {
            log.error("Alert matching drain failed — will retry on next poll", e);
        }
    }

}
