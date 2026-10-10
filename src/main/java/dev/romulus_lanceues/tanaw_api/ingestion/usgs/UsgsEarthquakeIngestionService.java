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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class UsgsEarthquakeIngestionService {

    private final UsgsClient usgsClient;
    private final Clock clock;
    private final DisasterEventProcessor disasterEventProcessor;

    private Instant lastSync;

    public List<UUID> ingest() {
        if (lastSync == null) {
            lastSync = Instant.now(clock).minus(Duration.ofHours(1));
        }

        List<UUID> persistedEventsId = new ArrayList<>();

        Instant started = Instant.now(clock);

        try {
            UsgsResponse response = usgsClient.fetchUpdatedAfter(lastSync.minus(Duration.ofMinutes(2)));

            List<UsgsFeature> features = response != null && response.features() != null
                    ? response.features()
                    : List.of();

            log.info("USGS poll completed: {} earthquake(s) updated since {}", features.size(), lastSync);

            features.forEach(feat -> {

                try {

                    if(feat.properties() == null ||
                            !feat.properties().type().equalsIgnoreCase("earthquake")) {
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

                    if(persistedEventId != null){
                        persistedEventsId.add(persistedEventId);
                    }
                } catch (Exception e) {
                    log.error("An error occurred while processing event [{}]: {}", feat.id(), e.getMessage(), e);
                }

                    });

            lastSync = started;
        } catch (Exception ex) {
            log.error("USGS ingestion failed — will retry on next poll (lastSync unchanged: {})",
                    lastSync, ex);
        }

        return persistedEventsId;

    }

}
