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
    public void sync(){
        List<UUID> persistedEventsId = ingestionService.ingest();

        if(persistedEventsId.isEmpty()){
            return;
        }

        try{
            alertMatchingService.createAlertForEvents(persistedEventsId);
        } catch (Exception e) {

            log.error("Alert matching failed for events {}", persistedEventsId, e);
        }

    }

}
