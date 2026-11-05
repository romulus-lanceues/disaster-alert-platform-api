package dev.romulus_lanceues.tanaw_api.ops;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertMatchingRepository;
import dev.romulus_lanceues.tanaw_api.alert.AlertRepository;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleRepository;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaRepository;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.UsgsSyncOrchestrator;
import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.location.LocationRepository;
import dev.romulus_lanceues.tanaw_api.notification.destination.NotificationTestCryptoConfig;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "security.jwt.secret=" + TestJwtFactory.DEFAULT_SECRET)
@Import(NotificationTestCryptoConfig.class)
@Testcontainers
@DisplayName("OpsSimulationRepository Integration Tests")
class OpsSimulationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @MockitoBean
    private UsgsSyncOrchestrator usgsSyncOrchestrator;

    @Autowired
    private OpsSimulationRepository opsSimulationRepository;

    @Autowired
    private AlertMatchingRepository alertMatchingRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private GeographicAreaRepository geographicAreaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeoPointFactory geoPointFactory;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private User baseUser;
    private Location baseLocation;
    private AlertRule baseRule;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        alertRuleRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
        geographicAreaRepository.deleteAll();
        jdbc.getJdbcOperations().execute("DELETE FROM disaster_events");

        baseUser = userRepository.save(User.builder()
                .email("ops-test-user-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed_password")
                .status(UserStatus.ACTIVE)
                .build());

        GeographicArea area = geographicAreaRepository.save(GeographicArea.builder()
                .psgcCode("1376000000")
                .name("City of Manila")
                .type(GeographicAreaType.MUNICIPALITY)
                .active(true)
                .build());

        baseLocation = locationRepository.save(Location.create(
                baseUser,
                "Manila Base Location",
                "123 Manila St.",
                area,
                14.5995,
                120.9842,
                geoPointFactory
        ));

        baseRule = alertRuleRepository.save(AlertRule.builder()
                .location(baseLocation)
                .disasterType(DisasterType.EARTHQUAKE)
                .enabled(true)
                .minimumMagnitude(5.0)
                .radiusKm(50.0)
                .minimumSeverity("MODERATE")
                .build());
    }

    @Nested
    @DisplayName("insertSimulatedDisasterEvent")
    class InsertSimulatedDisasterEventTests {

        @Test
        @DisplayName("should insert simulated event with SIMULATION source and pre-matched timestamp for matcher isolation")
        void shouldInsertSimulatedEventWithMatcherIsolation() {
            UUID eventId = UUID.randomUUID();
            String externalId = "sim-eq-" + UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            String place = "[TEST] Simulated earthquake near Manila Base Location";
            String severity = "MODERATE";

            opsSimulationRepository.insertSimulatedDisasterEvent(
                    eventId,
                    externalId,
                    now,
                    place,
                    severity,
                    null
            );

            // 1. Verify row persisted correctly in disaster_events
            String sql = "SELECT * FROM disaster_events WHERE id = :id";
            Map<String, Object> row = jdbc.queryForMap(sql, new MapSqlParameterSource("id", eventId));

            assertThat(row.get("source")).isEqualTo("SIMULATION");
            assertThat(row.get("external_id")).isEqualTo(externalId);
            assertThat(row.get("disaster_type")).isEqualTo("EARTHQUAKE");
            assertThat(row.get("status")).isEqualTo("test");
            assertThat(row.get("place")).isEqualTo(place);
            assertThat(row.get("severity")).isEqualTo(severity);
            assertThat(row.get("raw_payload").toString()).isEqualTo("{}");

            Timestamp alertMatchedAt = (Timestamp) row.get("alert_matched_at");
            assertThat(alertMatchedAt).isNotNull();
            assertThat(alertMatchedAt.toInstant()).isEqualTo(now);

            // 2. Zero User Leak guarantee: event is NOT claimed by AlertMatchingRepository
            List<UUID> claimed = alertMatchingRepository.claimUnmatchedEvents(10);
            assertThat(claimed).doesNotContain(eventId);
        }
    }

    @Nested
    @DisplayName("insertEarthquake")
    class InsertEarthquakeTests {

        @Test
        @DisplayName("should insert earthquake with PostGIS geography point and default depth when depthKm is null")
        void shouldInsertEarthquakeWithGeometryAndDefaultDepth() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            opsSimulationRepository.insertSimulatedDisasterEvent(
                    eventId,
                    "sim-" + UUID.randomUUID(),
                    now,
                    "Test Place",
                    "HIGH",
                    "{\"test\":true}"
            );

            opsSimulationRepository.insertEarthquake(eventId, 14.6000, 120.9850, 6.2, null);

            String sql = """
                    SELECT disaster_event_id, latitude, longitude, magnitude, depth_km,
                           ST_X(location::geometry) AS lon, ST_Y(location::geometry) AS lat
                    FROM earthquake
                    WHERE disaster_event_id = :id
                    """;
            Map<String, Object> row = jdbc.queryForMap(sql, new MapSqlParameterSource("id", eventId));

            assertThat(row.get("disaster_event_id")).isEqualTo(eventId);
            assertThat(((Number) row.get("latitude")).doubleValue()).isEqualTo(14.6000);
            assertThat(((Number) row.get("longitude")).doubleValue()).isEqualTo(120.9850);
            assertThat(((Number) row.get("magnitude")).doubleValue()).isEqualTo(6.2);
            assertThat(((Number) row.get("depth_km")).doubleValue()).isEqualTo(10.0);
            assertThat(((Number) row.get("lat")).doubleValue()).isEqualTo(14.6000);
            assertThat(((Number) row.get("lon")).doubleValue()).isEqualTo(120.9850);
        }
    }

    @Nested
    @DisplayName("createTargetedAlert")
    class CreateTargetedAlertTests {

        @Test
        @DisplayName("should create targeted alert when earthquake is within rule radius and satisfies magnitude threshold")
        void shouldCreateTargetedAlertWhenMatching() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            opsSimulationRepository.insertSimulatedDisasterEvent(eventId, "sim-match", now, "Place", "HIGH", null);
            // ~1 km away from Manila Base (14.5995, 120.9842), mag 6.0 >= 5.0 threshold
            opsSimulationRepository.insertEarthquake(eventId, 14.6000, 120.9850, 6.0, 12.0);

            Optional<UUID> alertIdOpt = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);

            assertThat(alertIdOpt).isPresent();
            UUID alertId = alertIdOpt.get();

            Optional<Alert> alertInDb = alertRepository.findById(alertId);
            assertThat(alertInDb).isPresent();
            Alert alert = alertInDb.get();
            assertThat(alert.getDisasterEvent().getId()).isEqualTo(eventId);
            assertThat(alert.getAlertRule().getId()).isEqualTo(baseRule.getId());
            assertThat(alert.getStatus().name()).isEqualTo("PENDING");
            assertThat(alert.getTriggeredAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should not create alert when earthquake is outside radius")
        void shouldNotCreateAlertWhenOutsideRadius() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            opsSimulationRepository.insertSimulatedDisasterEvent(eventId, "sim-outside", now, "Far Away", "HIGH", null);
            // Surigao (~700 km away)
            opsSimulationRepository.insertEarthquake(eventId, 9.8000, 125.6000, 7.0, 15.0);

            Optional<UUID> alertIdOpt = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);

            assertThat(alertIdOpt).isEmpty();
            assertThat(alertRepository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not create alert when magnitude is below rule threshold")
        void shouldNotCreateAlertWhenBelowMagnitude() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            opsSimulationRepository.insertSimulatedDisasterEvent(eventId, "sim-low-mag", now, "Nearby", "LOW", null);
            // ~1 km away, but magnitude 4.0 is below 5.0 threshold
            opsSimulationRepository.insertEarthquake(eventId, 14.6000, 120.9850, 4.0, 10.0);

            Optional<UUID> alertIdOpt = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);

            assertThat(alertIdOpt).isEmpty();
            assertThat(alertRepository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not create alert when rule is disabled")
        void shouldNotCreateAlertWhenRuleDisabled() {
            baseRule.disable();
            alertRuleRepository.save(baseRule);

            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            opsSimulationRepository.insertSimulatedDisasterEvent(eventId, "sim-disabled-rule", now, "Nearby", "HIGH", null);
            opsSimulationRepository.insertEarthquake(eventId, 14.6000, 120.9850, 6.0, 10.0);

            Optional<UUID> alertIdOpt = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);

            assertThat(alertIdOpt).isEmpty();
            assertThat(alertRepository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not create alert when user status is DISABLED (not ACTIVE)")
        void shouldNotCreateAlertWhenUserInactive() {
            jdbc.getJdbcOperations().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", baseUser.getId());

            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            opsSimulationRepository.insertSimulatedDisasterEvent(eventId, "sim-inactive-user", now, "Nearby", "HIGH", null);
            opsSimulationRepository.insertEarthquake(eventId, 14.6000, 120.9850, 6.0, 10.0);

            Optional<UUID> alertIdOpt = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);

            assertThat(alertIdOpt).isEmpty();
            assertThat(alertRepository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should be idempotent on conflict and return empty on duplicate call")
        void shouldBeIdempotentOnConflict() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            opsSimulationRepository.insertSimulatedDisasterEvent(eventId, "sim-idempotent", now, "Nearby", "HIGH", null);
            opsSimulationRepository.insertEarthquake(eventId, 14.6000, 120.9850, 6.0, 10.0);

            Optional<UUID> first = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);
            assertThat(first).isPresent();

            Optional<UUID> second = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);
            assertThat(second).isEmpty();

            assertThat(alertRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("deleteSimulation")
    class DeleteSimulationTests {

        @Test
        @DisplayName("should delete simulation disaster event and cascade to earthquake and alert records")
        void shouldDeleteSimulationAndCascade() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            opsSimulationRepository.insertSimulatedDisasterEvent(eventId, "sim-delete", now, "Nearby", "HIGH", null);
            opsSimulationRepository.insertEarthquake(eventId, 14.6000, 120.9850, 6.0, 10.0);
            Optional<UUID> alertIdOpt = opsSimulationRepository.createTargetedAlert(eventId, baseRule.getId(), now);
            assertThat(alertIdOpt).isPresent();

            // Perform deletion
            int deleted = opsSimulationRepository.deleteSimulation(eventId);

            assertThat(deleted).isEqualTo(1);

            // Verify disaster_events row is gone
            Integer eventCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM disaster_events WHERE id = :id",
                    new MapSqlParameterSource("id", eventId),
                    Integer.class
            );
            assertThat(eventCount).isEqualTo(0);

            // Verify earthquake row is cascaded
            Integer eqCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM earthquake WHERE disaster_event_id = :id",
                    new MapSqlParameterSource("id", eventId),
                    Integer.class
            );
            assertThat(eqCount).isEqualTo(0);

            // Verify alerts row is cascaded
            Integer alertCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM alerts WHERE disaster_event_id = :id",
                    new MapSqlParameterSource("id", eventId),
                    Integer.class
            );
            assertThat(alertCount).isEqualTo(0);
        }

        @Test
        @DisplayName("should not delete non-simulation events (e.g. source = USGS) and return 0")
        void shouldNotDeleteNonSimulationEvents() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            // Insert USGS event directly
            String sql = """
                    INSERT INTO disaster_events (
                        id, source, external_id, disaster_type, status, occurred_at,
                        source_updated_at, place, severity, raw_payload, created_at
                    ) VALUES (
                        :id, 'USGS', 'usgs-12345', 'EARTHQUAKE', 'reviewed', :now,
                        :now, 'Real Place', 'HIGH', '{}'::jsonb, :now
                    )
                    """;
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", eventId)
                    .addValue("now", Timestamp.from(now)));

            int deleted = opsSimulationRepository.deleteSimulation(eventId);

            assertThat(deleted).isEqualTo(0);

            // Verify event still exists
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM disaster_events WHERE id = :id",
                    new MapSqlParameterSource("id", eventId),
                    Integer.class
            );
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should return 0 when deleting non-existent simulation")
        void shouldReturnZeroWhenNotFound() {
            int deleted = opsSimulationRepository.deleteSimulation(UUID.randomUUID());
            assertThat(deleted).isEqualTo(0);
        }
    }
}
