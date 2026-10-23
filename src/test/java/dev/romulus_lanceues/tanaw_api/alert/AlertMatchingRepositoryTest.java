package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleRepository;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.UsgsSyncOrchestrator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import dev.romulus_lanceues.tanaw_api.notification.destination.NotificationTestCryptoConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = "security.jwt.secret=" + TestJwtFactory.DEFAULT_SECRET)
@Import(NotificationTestCryptoConfig.class)
@Testcontainers
@DisplayName("AlertMatching Integration Tests")
class AlertMatchingRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @MockitoSpyBean
    private AlertMatchingRepository alertMatchingRepository;

    @MockitoBean
    private UsgsSyncOrchestrator usgsSyncOrchestrator;

    @Autowired
    private AlertBatchMatcher alertBatchMatcher;

    @Autowired
    private DisasterEventProcessor disasterEventProcessor;

    @Autowired
    private DisasterEventRepository disasterEventRepository;

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

    @Nested
    @DisplayName("persistAlert")
    class PersistAlertTests {

        @Test
        @DisplayName("should create alert when simulated earthquake is within radius and above minimum magnitude")
        void shouldCreateAlertWhenEarthquakeMatchesRule() {
            // Earthquake ~1 km away from Manila Base (14.6000, 120.9850), magnitude 5.5
            UsgsFeature feature = createFeature("eq-match-1", 14.6000, 120.9850, 5.5);
            UUID eventId = disasterEventProcessor.persistDisasterEvent(feature);
            assertThat(eventId).isNotNull();

            List<CreatedAlert> createdAlerts = alertMatchingRepository.persistAlert(List.of(eventId));

            assertThat(createdAlerts).hasSize(1);
            CreatedAlert alert = createdAlerts.getFirst();
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
            List<CreatedAlert> firstRun = alertMatchingRepository.persistAlert(List.of(eventId));
            assertThat(firstRun).hasSize(1);

            // Second run with same event ID should return 0 (DO NOTHING)
            List<CreatedAlert> secondRun = alertMatchingRepository.persistAlert(List.of(eventId));
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

            List<CreatedAlert> createdAlerts = alertMatchingRepository.persistAlert(List.of(eventId));

            assertThat(createdAlerts).isEmpty();
            assertThat(alertRepository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not create alert when magnitude is below rule threshold")
        void shouldNotCreateAlertWhenBelowMagnitude() {
            // Close by (~1 km away), but magnitude 3.5 is below the 5.0 threshold
            UsgsFeature feature = createFeature("eq-low-mag-1", 14.6000, 120.9850, 3.5);
            UUID eventId = disasterEventProcessor.persistDisasterEvent(feature);

            List<CreatedAlert> createdAlerts = alertMatchingRepository.persistAlert(List.of(eventId));

            assertThat(createdAlerts).isEmpty();
            assertThat(alertRepository.count()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("claimUnmatchedEvents & markMatched")
    class ClaimAndMarkTests {

        @Test
        @DisplayName("should claim unmatched events ordered by source_updated_at ASC and skip already matched events")
        void shouldClaimUnmatchedEventsInOrder() {
            // Persist 3 events with different source_updated_at timestamps
            UUID id1 = disasterEventProcessor.persistDisasterEvent(
                    createFeatureWithTime("eq-claim-1", 14.6000, 120.9850, 5.5, 1000L, 1000L));
            UUID id2 = disasterEventProcessor.persistDisasterEvent(
                    createFeatureWithTime("eq-claim-2", 14.6000, 120.9850, 5.5, 3000L, 3000L));
            UUID id3 = disasterEventProcessor.persistDisasterEvent(
                    createFeatureWithTime("eq-claim-3", 14.6000, 120.9850, 5.5, 2000L, 2000L));

            // Mark id3 as already matched
            alertMatchingRepository.markMatched(List.of(id3), Instant.now());

            // Claim with limit 10: only id1 and id2 should be returned, in ascending order of updated_at (id1 then id2)
            List<UUID> claimed = alertMatchingRepository.claimUnmatchedEvents(10);
            assertThat(claimed).containsExactly(id1, id2);

            // Claim with limit 1: only id1 is returned
            List<UUID> claimedLimit1 = alertMatchingRepository.claimUnmatchedEvents(1);
            assertThat(claimedLimit1).containsExactly(id1);

            // Invalid limit returns empty
            assertThat(alertMatchingRepository.claimUnmatchedEvents(0)).isEmpty();
            assertThat(alertMatchingRepository.claimUnmatchedEvents(-5)).isEmpty();
        }

        @Test
        @DisplayName("should mark claimed events as matched with timestamp and remove them from future claims")
        void shouldMarkEventsAsMatched() {
            UUID id = disasterEventProcessor.persistDisasterEvent(
                    createFeature("eq-mark-1", 14.6000, 120.9850, 5.5));

            assertThat(alertMatchingRepository.claimUnmatchedEvents(10)).containsExactly(id);

            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            int updatedCount = alertMatchingRepository.markMatched(List.of(id), now);
            assertThat(updatedCount).isEqualTo(1);

            // Once marked, event is no longer claimed
            assertThat(alertMatchingRepository.claimUnmatchedEvents(10)).isEmpty();

            // Verify timestamp in database
            DisasterEvent saved = disasterEventRepository.findById(id).orElseThrow();
            assertThat(saved.getAlertMatchedAt()).isEqualTo(now);

            // Calling markMatched with empty list returns 0
            assertThat(alertMatchingRepository.markMatched(List.of(), now)).isEqualTo(0);
            assertThat(alertMatchingRepository.markMatched(null, now)).isEqualTo(0);
        }

        @Test
        @DisplayName("should find oldest unmatched event time or return empty when none exist")
        void shouldFindOldestUnmatchedEventTime() {
            // When table is empty
            assertThat(alertMatchingRepository.findOldestUnmatchedEventTime()).isEmpty();

            // Insert 2 events: t1 (5000ms) and t2 (10000ms)
            UUID id1 = disasterEventProcessor.persistDisasterEvent(
                    createFeatureWithTime("eq-old-1", 14.6000, 120.9850, 5.5, 5000L, 5000L));
            UUID id2 = disasterEventProcessor.persistDisasterEvent(
                    createFeatureWithTime("eq-old-2", 14.6000, 120.9850, 5.5, 10000L, 10000L));

            Optional<Instant> oldest = alertMatchingRepository.findOldestUnmatchedEventTime();
            assertThat(oldest).contains(Instant.ofEpochMilli(5000L));

            // Mark id1 as matched -> oldest is now id2 (10000ms)
            alertMatchingRepository.markMatched(List.of(id1), Instant.now());
            assertThat(alertMatchingRepository.findOldestUnmatchedEventTime())
                    .contains(Instant.ofEpochMilli(10000L));

            // Mark id2 as matched -> oldest is now empty
            alertMatchingRepository.markMatched(List.of(id2), Instant.now());
            assertThat(alertMatchingRepository.findOldestUnmatchedEventTime()).isEmpty();
        }
    }

    @Nested
    @DisplayName("AlertBatchMatcher integration")
    class AlertBatchMatcherTests {

        @Test
        @DisplayName("should claim, match rules, persist alerts, and mark events in an isolated transaction")
        void shouldExecuteBatchMatchingEndToEnd() {
            // Persist unmatched matching earthquake event
            UUID eventId = disasterEventProcessor.persistDisasterEvent(
                    createFeature("eq-batch-1", 14.6000, 120.9850, 5.5));

            BatchMatchResult result = alertBatchMatcher.matchBatch(10);

            assertThat(result.claimedCount()).isEqualTo(1);
            assertThat(result.createdAlertsCount()).isEqualTo(1);
            assertThat(result.createdAlerts()).hasSize(1);
            assertThat(result.createdAlerts().getFirst().disasterEventId()).isEqualTo(eventId);

            // Event in DB must now be marked as matched
            DisasterEvent eventInDb = disasterEventRepository.findById(eventId).orElseThrow();
            assertThat(eventInDb.getAlertMatchedAt()).isNotNull();

            // Next batch invocation finds 0 pending events
            BatchMatchResult secondResult = alertBatchMatcher.matchBatch(10);
            assertThat(secondResult.claimedCount()).isEqualTo(0);
            assertThat(secondResult.createdAlertsCount()).isEqualTo(0);
            assertThat(secondResult.createdAlerts()).isEmpty();
        }

        @Test
        @DisplayName("should leave markers NULL when batch matching fails mid-transaction and recover on next drain")
        void shouldRollbackMarkersOnFailureAndRecoverOnNextDrain() {
            // Persist unmatched matching earthquake event
            UUID eventId = disasterEventProcessor.persistDisasterEvent(
                    createFeature("eq-fail-recover-1", 14.6000, 120.9850, 5.5));

            // Force persistAlert to fail after the claim, then call real method on subsequent calls
            doThrow(new RuntimeException("Simulated matching failure after claim"))
                    .doCallRealMethod()
                    .when(alertMatchingRepository).persistAlert(anyList());

            // Attempt batch matching — must fail and roll back transaction
            assertThatThrownBy(() -> alertBatchMatcher.matchBatch(10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Simulated matching failure after claim");

            // Assert that markers stay NULL and no alerts exist
            DisasterEvent eventAfterFailure = disasterEventRepository.findById(eventId).orElseThrow();
            assertThat(eventAfterFailure.getAlertMatchedAt()).isNull();
            assertThat(alertRepository.count()).isEqualTo(0);

            // Run next drain — must successfully claim the event and create the alert
            BatchMatchResult recoveryResult = alertBatchMatcher.matchBatch(10);
            assertThat(recoveryResult.claimedCount()).isEqualTo(1);
            assertThat(recoveryResult.createdAlertsCount()).isEqualTo(1);
            assertThat(alertRepository.count()).isEqualTo(1);

            DisasterEvent eventAfterRecovery = disasterEventRepository.findById(eventId).orElseThrow();
            assertThat(eventAfterRecovery.getAlertMatchedAt()).isNotNull();
        }

        @Test
        @DisplayName("should not overlap claimed events or create duplicate alerts during concurrent batch matching")
        void shouldNotOverlapOrDuplicateMatchesDuringConcurrentDrains() throws Exception {
            // Persist 10 distinct unmatched earthquake events
            List<UUID> createdEventIds = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UUID id = disasterEventProcessor.persistDisasterEvent(
                        createFeature("eq-concurrent-" + i, 14.6000, 120.9850, 5.5));
                createdEventIds.add(id);
            }
            assertThat(createdEventIds).hasSize(10);

            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            Callable<BatchMatchResult> task = () -> {
                readyLatch.countDown();
                startLatch.await(); // Hold both threads until release
                return alertBatchMatcher.matchBatch(5); // Each worker claims a batch of 5
            };

            Future<BatchMatchResult> future1 = executor.submit(task);
            Future<BatchMatchResult> future2 = executor.submit(task);

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown(); // Release both threads at the exact same instant

            BatchMatchResult result1 = future1.get(10, TimeUnit.SECONDS);
            BatchMatchResult result2 = future2.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            List<UUID> claimed1 = result1.createdAlerts().stream()
                    .map(CreatedAlert::disasterEventId)
                    .toList();
            List<UUID> claimed2 = result2.createdAlerts().stream()
                    .map(CreatedAlert::disasterEventId)
                    .toList();

            // Total claimed events across both concurrent workers must be 10
            assertThat(result1.claimedCount() + result2.claimedCount()).isEqualTo(10);

            // Assert the claimed ID sets don't overlap (FOR UPDATE SKIP LOCKED guarantees mutually exclusive sets)
            assertThat(claimed1).doesNotContainAnyElementsOf(claimed2);

            // Assert exactly 10 alerts were created with no duplicates
            assertThat(alertRepository.count()).isEqualTo(10);

            // Assert all 10 events now have alert_matched_at populated
            List<DisasterEvent> allEvents = disasterEventRepository.findAllById(createdEventIds);
            assertThat(allEvents).allMatch(event -> event.getAlertMatchedAt() != null);
        }

        @Test
        @DisplayName("should not match historical backfilled events against newly created rules")
        void shouldNotMatchBackfilledHistoricalEvents() {
            // Persist an event and simulate Flyway V15 backfill: alert_matched_at = created_at
            UUID eventId = disasterEventProcessor.persistDisasterEvent(
                    createFeature("eq-historical-1", 14.6000, 120.9850, 5.5));

            jdbc.getJdbcOperations().execute(
                    "UPDATE disaster_events SET alert_matched_at = created_at WHERE id = '" + eventId + "'"
            );

            // Run batch matching — backfilled event must NOT be claimed
            BatchMatchResult result = alertBatchMatcher.matchBatch(10);

            assertThat(result.claimedCount()).isEqualTo(0);
            assertThat(result.createdAlertsCount()).isEqualTo(0);
            assertThat(alertRepository.count()).isEqualTo(0);
        }
    }

    private UsgsFeature createFeature(String id, double lat, double lon, double mag) {
        long now = System.currentTimeMillis();
        return createFeatureWithTime(id, lat, lon, mag, now, now);
    }

    private UsgsFeature createFeatureWithTime(String id, double lat, double lon, double mag, long time, long updated) {
        UsgsFeatureProperties props = new UsgsFeatureProperties(
                mag,
                "Test Place",
                time,
                updated,
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
