package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventRepository;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UpsertResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "security.jwt.secret=" + TestJwtFactory.DEFAULT_SECRET)
@Testcontainers
@DisplayName("DisasterEventUpserter Integration Tests")
class DisasterEventUpserterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private DisasterEventUpserter disasterEventUpserter;

    @Autowired
    private DisasterEventRepository disasterEventRepository;

    @MockitoBean
    private UsgsSyncOrchestrator usgsSyncOrchestrator;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.getJdbcOperations().execute("DELETE FROM alerts");
        jdbc.getJdbcOperations().execute("DELETE FROM earthquake");
        jdbc.getJdbcOperations().execute("DELETE FROM disaster_events");
    }

    @Nested
    @DisplayName("upsert")
    class UpsertTests {

        @Test
        @DisplayName("inserts new disaster event with alert_matched_at = NULL and returns inserted = true")
        void insertsNewEventWithNullAlertMatchedAt() {
            UUID newId = UUID.randomUUID();
            Instant occurredAt = Instant.parse("2026-10-10T10:00:00Z");
            Instant sourceUpdatedAt = Instant.parse("2026-10-10T10:05:00Z");

            Optional<UpsertResult> result = disasterEventUpserter.upsert(
                    newId, "USGS", "us7000test01", "EARTHQUAKE", "reviewed",
                    occurredAt, sourceUpdatedAt, "HIGH", "Manila, Philippines", "{\"raw\": 1}"
            );

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(newId);
            assertThat(result.get().inserted()).isTrue();

            DisasterEvent saved = disasterEventRepository.findById(newId).orElseThrow();
            assertThat(saved.getSource()).isEqualTo("USGS");
            assertThat(saved.getExternalId()).isEqualTo("us7000test01");
            assertThat(saved.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(saved.getStatus()).isEqualTo("reviewed");
            assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
            assertThat(saved.getSourceUpdatedAt()).isEqualTo(sourceUpdatedAt);
            assertThat(saved.getSeverity()).isEqualTo("HIGH");
            assertThat(saved.getPlace()).isEqualTo("Manila, Philippines");
            assertThat(saved.getAlertMatchedAt()).isNull();
        }

        @Test
        @DisplayName("updates existing disaster event and resets alert_matched_at to NULL on newer revision")
        void updatesEventAndResetsAlertMatchedAtToNullOnRevision() {
            UUID initialId = UUID.randomUUID();
            Instant t1Occurred = Instant.parse("2026-10-10T10:00:00Z");
            Instant t1Updated = Instant.parse("2026-10-10T10:05:00Z");

            Optional<UpsertResult> initialResult = disasterEventUpserter.upsert(
                    initialId, "USGS", "us7000test02", "EARTHQUAKE", "automatic",
                    t1Occurred, t1Updated, "MODERATE", "Initial Place", "{\"rev\": 1}"
            );
            assertThat(initialResult).isPresent();

            // Simulate alert matching: set alert_matched_at to a known timestamp
            Instant matchedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            jdbc.update(
                    "UPDATE disaster_events SET alert_matched_at = :matchedAt WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("matchedAt", Timestamp.from(matchedAt))
                            .addValue("id", initialId)
            );

            DisasterEvent beforeRevision = disasterEventRepository.findById(initialId).orElseThrow();
            assertThat(beforeRevision.getAlertMatchedAt()).isNotNull();

            // USGS sends revision with newer source_updated_at (t2 > t1)
            Instant t2Updated = Instant.parse("2026-10-10T10:15:00Z");
            Optional<UpsertResult> revisionResult = disasterEventUpserter.upsert(
                    UUID.randomUUID(), "USGS", "us7000test02", "EARTHQUAKE", "reviewed",
                    t1Occurred, t2Updated, "HIGH", "Revised Place", "{\"rev\": 2}"
            );

            assertThat(revisionResult).isPresent();
            assertThat(revisionResult.get().id()).isEqualTo(initialId);
            assertThat(revisionResult.get().inserted()).isFalse();

            DisasterEvent revisedEvent = disasterEventRepository.findById(initialId).orElseThrow();
            assertThat(revisedEvent.getAlertMatchedAt()).isNull();
            assertThat(revisedEvent.getStatus()).isEqualTo("reviewed");
            assertThat(revisedEvent.getSourceUpdatedAt()).isEqualTo(t2Updated);
            assertThat(revisedEvent.getSeverity()).isEqualTo("HIGH");
            assertThat(revisedEvent.getPlace()).isEqualTo("Revised Place");
        }

        @Test
        @DisplayName("skips stale update and preserves alert_matched_at when source_updated_at is not newer")
        void skipsStaleUpdateAndPreservesAlertMatchedAt() {
            UUID initialId = UUID.randomUUID();
            Instant t2Occurred = Instant.parse("2026-10-10T10:00:00Z");
            Instant t2Updated = Instant.parse("2026-10-10T10:15:00Z");

            disasterEventUpserter.upsert(
                    initialId, "USGS", "us7000test03", "EARTHQUAKE", "reviewed",
                    t2Occurred, t2Updated, "HIGH", "Current Place", "{\"rev\": 2}"
            );

            Instant matchedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            jdbc.update(
                    "UPDATE disaster_events SET alert_matched_at = :matchedAt WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("matchedAt", Timestamp.from(matchedAt))
                            .addValue("id", initialId)
            );

            // Stale payload with older source_updated_at (t1 < t2)
            Instant t1Updated = Instant.parse("2026-10-10T10:05:00Z");
            Optional<UpsertResult> staleResult = disasterEventUpserter.upsert(
                    UUID.randomUUID(), "USGS", "us7000test03", "EARTHQUAKE", "automatic",
                    t2Occurred, t1Updated, "LOW", "Stale Place", "{\"rev\": 1}"
            );

            assertThat(staleResult).isEmpty();

            DisasterEvent afterStale = disasterEventRepository.findById(initialId).orElseThrow();
            assertThat(afterStale.getPlace()).isEqualTo("Current Place");
            assertThat(afterStale.getSeverity()).isEqualTo("HIGH");
            assertThat(afterStale.getSourceUpdatedAt()).isEqualTo(t2Updated);
            assertThat(afterStale.getAlertMatchedAt()).isEqualTo(matchedAt);

            // Duplicate payload with identical source_updated_at (t2 == t2)
            Optional<UpsertResult> duplicateResult = disasterEventUpserter.upsert(
                    UUID.randomUUID(), "USGS", "us7000test03", "EARTHQUAKE", "reviewed",
                    t2Occurred, t2Updated, "HIGH", "Current Place", "{\"rev\": 2}"
            );

            assertThat(duplicateResult).isEmpty();

            DisasterEvent afterDuplicate = disasterEventRepository.findById(initialId).orElseThrow();
            assertThat(afterDuplicate.getAlertMatchedAt()).isEqualTo(matchedAt);
        }
    }

    @Nested
    @DisplayName("upsertEarthquake")
    class UpsertEarthquakeTests {

        @Test
        @DisplayName("inserts and updates earthquake details with PostGIS geography point")
        void insertsAndUpdatesEarthquakeDetails() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.parse("2026-10-10T10:00:00Z");

            disasterEventUpserter.upsert(
                    eventId, "USGS", "us7000test04", "EARTHQUAKE", "reviewed",
                    now, now, "MODERATE", "Davao, Philippines", "{}"
            );

            disasterEventUpserter.upsertEarthquake(eventId, 7.1907, 125.4553, 5.8, 15.0);

            String querySql = """
                    SELECT latitude, longitude, magnitude, depth_km, ST_AsText(location) AS geom
                    FROM earthquake
                    WHERE disaster_event_id = :id
                    """;
            Map<String, Object> row = jdbc.queryForMap(querySql, new MapSqlParameterSource("id", eventId));

            assertThat(row.get("latitude")).isEqualTo(7.1907);
            assertThat(row.get("longitude")).isEqualTo(125.4553);
            assertThat(row.get("magnitude")).isEqualTo(5.8);
            assertThat(row.get("depth_km")).isEqualTo(15.0);
            assertThat(row.get("geom")).isEqualTo("POINT(125.4553 7.1907)");

            // Update earthquake on revision
            disasterEventUpserter.upsertEarthquake(eventId, 7.1920, 125.4560, 6.1, 12.5);

            Map<String, Object> updatedRow = jdbc.queryForMap(querySql, new MapSqlParameterSource("id", eventId));
            assertThat(updatedRow.get("latitude")).isEqualTo(7.1920);
            assertThat(updatedRow.get("longitude")).isEqualTo(125.4560);
            assertThat(updatedRow.get("magnitude")).isEqualTo(6.1);
            assertThat(updatedRow.get("depth_km")).isEqualTo(12.5);
            assertThat(updatedRow.get("geom")).isEqualTo("POINT(125.456 7.192)");
        }
    }
}
