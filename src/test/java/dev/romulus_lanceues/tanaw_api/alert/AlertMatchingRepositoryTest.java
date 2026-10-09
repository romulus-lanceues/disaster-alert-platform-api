package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleRepository;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaRepository;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.DisasterEventProcessor;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeatureProperties;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsGeometry;
import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.location.LocationRepository;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "security.jwt.secret=" + TestJwtFactory.DEFAULT_SECRET)
@Testcontainers
@DisplayName("AlertUpserter Integration Tests")
class AlertMatchingRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private AlertMatchingRepository alertMatchingRepository;

    @Autowired
    private DisasterEventProcessor disasterEventProcessor;

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

    private Location baseLocation;
    private AlertRule baseRule;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        alertRuleRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();
        geographicAreaRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .email("test-user-" + UUID.randomUUID() + "@example.com")
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
                user,
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

    @Test
    @DisplayName("should create alert when simulated earthquake is within radius and above minimum magnitude")
    void shouldCreateAlertWhenEarthquakeMatchesRule() {
        // Earthquake ~1 km away from Manila Base (14.6000, 120.9850), magnitude 5.5
        UsgsFeature feature = createFeature("eq-match-1", 14.6000, 120.9850, 5.5);
        UUID eventId = disasterEventProcessor.persistDisasterEvent(feature);
        assertThat(eventId).isNotNull();

        List<CreatedAlerts> createdAlerts = alertMatchingRepository.persistAlert(List.of(eventId));

        assertThat(createdAlerts).hasSize(1);
        CreatedAlerts alert = createdAlerts.getFirst();
        assertThat(alert.disasterEventId()).isEqualTo(eventId);
        assertThat(alert.alertRuleId()).isEqualTo(baseRule.getId());
        assertThat(alert.id()).isNotNull();

        // Verify alert actually saved in database
        assertThat(alertRepository.findById(alert.id())).isPresent();
    }

    @Test
    @DisplayName("should be idempotent and not create duplicate alerts on subsequent runs (ON CONFLICT DO NOTHING)")
    void shouldNotCreateDuplicateAlertOnRepeat() {
        UsgsFeature feature = createFeature("eq-repeat-1", 14.6000, 120.9850, 5.5);
        UUID eventId = disasterEventProcessor.persistDisasterEvent(feature);

        // First run creates 1 alert
        List<CreatedAlerts> firstRun = alertMatchingRepository.persistAlert(List.of(eventId));
        assertThat(firstRun).hasSize(1);

        // Second run with same event ID should return 0 (DO NOTHING)
        List<CreatedAlerts> secondRun = alertMatchingRepository.persistAlert(List.of(eventId));
        assertThat(secondRun).isEmpty();

        // Database should still contain exactly 1 alert
        assertThat(alertRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should not create alert when earthquake is outside radius")
    void shouldNotCreateAlertWhenOutsideRadius() {
        // Far away coordinates: Surigao (approx 700 km away)
        UsgsFeature feature = createFeature("eq-far-1", 9.8000, 125.6000, 6.5);
        UUID eventId = disasterEventProcessor.persistDisasterEvent(feature);

        List<CreatedAlerts> createdAlerts = alertMatchingRepository.persistAlert(List.of(eventId));

        assertThat(createdAlerts).isEmpty();
        assertThat(alertRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("should not create alert when magnitude is below rule threshold")
    void shouldNotCreateAlertWhenBelowMagnitude() {
        // Close by (~1 km away), but magnitude 3.5 is below the 5.0 threshold
        UsgsFeature feature = createFeature("eq-low-mag-1", 14.6000, 120.9850, 3.5);
        UUID eventId = disasterEventProcessor.persistDisasterEvent(feature);

        List<CreatedAlerts> createdAlerts = alertMatchingRepository.persistAlert(List.of(eventId));

        assertThat(createdAlerts).isEmpty();
        assertThat(alertRepository.count()).isEqualTo(0);
    }

    private UsgsFeature createFeature(String id, double lat, double lon, double mag) {
        long now = System.currentTimeMillis();
        UsgsFeatureProperties props = new UsgsFeatureProperties(
                mag,
                "Test Place",
                now,
                now,
                "reviewed",
                0,
                "yellow",
                "https://earthquake.usgs.gov/" + id,
                "M " + mag + " - Test",
                "earthquake"
        );
        UsgsGeometry geom = new UsgsGeometry("Point", List.of(lon, lat, 10.0));
        return new UsgsFeature(id, props, geom, "{}");
    }
}
