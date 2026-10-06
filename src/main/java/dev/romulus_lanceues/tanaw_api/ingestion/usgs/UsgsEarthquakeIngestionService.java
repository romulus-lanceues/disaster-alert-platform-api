package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventRepository;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsResponse;
import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class UsgsEarthquakeIngestionService {

    private final UsgsClient usgsClient;
    private final Clock clock;
    private final GeoPointFactory geoPointFactory;
    private final DisasterEventRepository  disasterEventRepository;

    private Instant lastSync;

    @Scheduled(fixedDelayString = "${usgs.poll-interval}")
    @Transactional
    void ingest() {
        if (lastSync == null) {
            lastSync = Instant.now(clock).minus(Duration.ofHours(1));
        }

        Instant started = Instant.now(clock);

        try {
            UsgsResponse response = usgsClient.fetchUpdatedAfter(lastSync);

            List<UsgsFeature> features = response != null && response.features() != null
                    ? response.features()
                    : List.of();

            log.info("USGS poll completed: {} earthquake(s) updated since {}", features.size(), lastSync);

            features.forEach(feat ->
                    log.info("Earthquake [{}] mag={} depth={}km place=\"{}\" coords=[{}, {}] alert={} url={}",
                            feat.id(),
                            feat.properties().mag(),
                            feat.geometry().depthKm(),
                            feat.properties().place(),
                            feat.geometry().latitude(),
                            feat.geometry().longitude(),
                            feat.properties().alert(),
                            feat.properties().url()));

            lastSync = started;
        } catch (Exception ex) {
            log.error("USGS ingestion failed — will retry on next poll (lastSync unchanged: {})",
                    lastSync, ex);
        }
    }

}
