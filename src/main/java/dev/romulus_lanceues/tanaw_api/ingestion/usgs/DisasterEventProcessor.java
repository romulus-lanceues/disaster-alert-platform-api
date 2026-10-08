package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.disaster.SeverityMapper;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UpsertResult;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeatureProperties;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsGeometry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisasterEventProcessor {

    private final DisasterEventUpserter disasterEventUpserter;
    private final SeverityMapper severityMapper;

    @Transactional
    public void persistDisasterEvent(UsgsFeature feature) {

        String id = feature.id();
        UsgsFeatureProperties disasterProperties = feature.properties();
        UsgsGeometry disasterGeometry = feature.geometry();
        String rawPayload = feature.rawPayload();

        Optional<UpsertResult> disasterPersistenceResult = disasterEventUpserter.upsert(
                UUID.randomUUID(), "USGS", id, disasterProperties.type().toUpperCase(), disasterProperties.status(),
                Instant.ofEpochMilli(disasterProperties.time()), Instant.ofEpochMilli(disasterProperties.updated()),
                severityMapper.fromUsgs(disasterProperties).toString(), disasterProperties.place(), rawPayload
        );

        if(disasterPersistenceResult.isEmpty()) return;

        UpsertResult upsertResult = disasterPersistenceResult.get();

        if (upsertResult.inserted()) {
            log.info("Inserted a new disaster to the database[{}] (externalId: {})", upsertResult.id(), id);
        } else {
            log.info("Updated an existing disaster event [{}] (externalId: {})", upsertResult.id(), id);
        }

        disasterEventUpserter.upsertEarthquake(disasterPersistenceResult.get().id(),
                disasterGeometry.latitude(), disasterGeometry.longitude(),
                disasterProperties.mag(), disasterGeometry.depthKm());
    }
}
