package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.alert.AlertMatchingRepository;
import dev.romulus_lanceues.tanaw_api.alert.CreatedAlerts;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class UsgsEarthquakeIngestionService {

    private final UsgsClient usgsClient;
    private final Clock clock;
    private final DisasterEventProcessor disasterEventProcessor;
    private final AlertMatchingRepository alertMatchingRepository;

    private Instant lastSync;

    @Scheduled(fixedDelayString = "${usgs.poll-interval}")
    void ingest() {
        if (lastSync == null) {
            lastSync = Instant.now(clock).minus(Duration.ofHours(1));
        }

        Instant started = Instant.now(clock);

        try {

            List<UUID> persistedEventsId = new ArrayList<>();

            UsgsResponse response = usgsClient.fetchUpdatedAfter(lastSync.minus(Duration.ofMinutes(2)));

            List<UsgsFeature> features = response != null && response.features() != null
                    ? response.features()
                    : List.of();

            log.info("USGS poll completed: {} earthquake(s) updated since {}", features.size(), lastSync);

            features.forEach(feat -> {
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

                        if(persistedEventId != null){
                            persistedEventsId.add(persistedEventId);
                        }
                    });


            if(!persistedEventsId.isEmpty()){
                List<CreatedAlerts> alerts = alertMatchingRepository.persistAlert(persistedEventsId);

                for(CreatedAlerts a : alerts){
                    log.info("id:{}, disaster_id:{}, alert_rule_id:{}",
                            a.id(), a.disasterEventId(), a.alertRuleId());
                }
            }

            lastSync = started;
        } catch (Exception ex) {
            log.error("USGS ingestion failed — will retry on next poll (lastSync unchanged: {})",
                    lastSync, ex);
        }

    }

}
