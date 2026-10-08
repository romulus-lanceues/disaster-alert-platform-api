package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.disaster.Severity;
import dev.romulus_lanceues.tanaw_api.disaster.SeverityMapper;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UpsertResult;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeatureProperties;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisasterEventProcessor Tests")
class DisasterEventProcessorTest {

    @Mock
    private DisasterEventUpserter disasterEventUpserter;

    @Mock
    private SeverityMapper severityMapper;

    @InjectMocks
    private DisasterEventProcessor disasterEventProcessor;

    private UsgsFeature createSampleFeature(String id, String type, long time, long updated) {
        UsgsFeatureProperties properties = new UsgsFeatureProperties(
                6.5,
                "15 km ESE of Surigao, Philippines",
                time,
                updated,
                "reviewed",
                0,
                "yellow",
                "https://earthquake.usgs.gov/earthquakes/eventpage/" + id,
                "M 6.5 - Philippines",
                type
        );
        UsgsGeometry geometry = new UsgsGeometry("Point", List.of(125.6, 9.8, 20.5));
        String rawPayload = "{\"id\":\"" + id + "\"}";
        return new UsgsFeature(id, properties, geometry, rawPayload);
    }

    @Nested
    @DisplayName("persistDisasterEvent")
    class PersistDisasterEventTests {

        @Test
        @DisplayName("successfully upserts a new disaster event and its earthquake record")
        void persistsNewDisasterEventAndEarthquake() {
            UsgsFeature feature = createSampleFeature("us7000test01", "earthquake", 1712495000000L, 1712499000000L);
            UUID generatedId = UUID.randomUUID();

            given(severityMapper.fromUsgs(feature.properties())).willReturn(Severity.HIGH);
            given(disasterEventUpserter.upsert(
                    any(UUID.class),
                    eq("USGS"),
                    eq("us7000test01"),
                    eq("EARTHQUAKE"),
                    eq("reviewed"),
                    eq(Instant.ofEpochMilli(1712495000000L)),
                    eq(Instant.ofEpochMilli(1712499000000L)),
                    eq("HIGH"),
                    eq("15 km ESE of Surigao, Philippines"),
                    eq(feature.rawPayload())
            )).willReturn(Optional.of(new UpsertResult(generatedId, true)));

            disasterEventProcessor.persistDisasterEvent(feature);

            ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
            then(disasterEventUpserter).should().upsert(
                    idCaptor.capture(),
                    eq("USGS"),
                    eq("us7000test01"),
                    eq("EARTHQUAKE"),
                    eq("reviewed"),
                    eq(Instant.ofEpochMilli(1712495000000L)),
                    eq(Instant.ofEpochMilli(1712499000000L)),
                    eq("HIGH"),
                    eq("15 km ESE of Surigao, Philippines"),
                    eq(feature.rawPayload())
            );
            assertThat(idCaptor.getValue()).isNotNull();

            then(disasterEventUpserter).should().upsertEarthquake(
                    generatedId,
                    9.8,
                    125.6,
                    6.5,
                    20.5
            );
        }

        @Test
        @DisplayName("successfully updates an existing disaster event and upserts earthquake details")
        void updatesExistingDisasterEventAndEarthquake() {
            UsgsFeature feature = createSampleFeature("us7000test02", "earthquake", 1712495000000L, 1712500000000L);
            UUID existingId = UUID.randomUUID();

            given(severityMapper.fromUsgs(feature.properties())).willReturn(Severity.MODERATE);
            given(disasterEventUpserter.upsert(
                    any(UUID.class),
                    eq("USGS"),
                    eq("us7000test02"),
                    eq("EARTHQUAKE"),
                    eq("reviewed"),
                    eq(Instant.ofEpochMilli(1712495000000L)),
                    eq(Instant.ofEpochMilli(1712500000000L)),
                    eq("MODERATE"),
                    eq("15 km ESE of Surigao, Philippines"),
                    eq(feature.rawPayload())
            )).willReturn(Optional.of(new UpsertResult(existingId, false)));

            disasterEventProcessor.persistDisasterEvent(feature);

            then(disasterEventUpserter).should().upsertEarthquake(
                    existingId,
                    9.8,
                    125.6,
                    6.5,
                    20.5
            );
        }

        @Test
        @DisplayName("skips earthquake upsert when disaster upsert returns empty Optional")
        void skipsEarthquakeUpsertWhenDisasterUpsertEmpty() {
            UsgsFeature feature = createSampleFeature("us7000test03", "earthquake", 1712495000000L, 1712496000000L);

            given(severityMapper.fromUsgs(feature.properties())).willReturn(Severity.LOW);
            given(disasterEventUpserter.upsert(
                    any(UUID.class),
                    eq("USGS"),
                    eq("us7000test03"),
                    eq("EARTHQUAKE"),
                    eq("reviewed"),
                    any(Instant.class),
                    any(Instant.class),
                    eq("LOW"),
                    eq("15 km ESE of Surigao, Philippines"),
                    eq(feature.rawPayload())
            )).willReturn(Optional.empty());

            disasterEventProcessor.persistDisasterEvent(feature);

            then(disasterEventUpserter).should(never()).upsertEarthquake(
                    any(), any(), any(), any(), any()
            );
        }
    }
}
