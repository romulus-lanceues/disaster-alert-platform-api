package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.enums.AlertStatus;
import dev.romulus_lanceues.tanaw_api.enums.DisasterType;
import dev.romulus_lanceues.tanaw_api.enums.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.enums.UserStatus;
import dev.romulus_lanceues.tanaw_api.jts.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.location.GeographicArea;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
@DisplayName("AlertRepository Tests")
class AlertRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();

    private User primaryUser;
    private GeographicArea geographicArea;
    private Location primaryLocation;
    private AlertRule primaryRule;
    private DisasterEvent primaryDisasterEvent;

    @BeforeEach
    void setUp() {
        primaryUser = persistUser("user1@example.com");
        geographicArea = persistGeographicArea("137600000", "City of Manila");
        primaryLocation = persistLocation(primaryUser, "Home", 14.5995, 120.9842);
        primaryRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
        primaryDisasterEvent = persistDisasterEvent("USGS", "usgs-event-001", DisasterType.EARTHQUAKE, 6.2, 14.6000, 120.9850);
    }

    @Nested
    @DisplayName("Entity Persistence and Constraints")
    class PersistenceAndConstraints {

        @Test
        @DisplayName("should persist alert successfully with required relationships")
        void shouldPersistAlertSuccessfully() {
            Instant triggeredAt = Instant.parse("2026-09-10T10:00:00Z");
            Alert alert = Alert.builder()
                    .disasterEvent(primaryDisasterEvent)
                    .alertRule(primaryRule)
                    .status(AlertStatus.PENDING)
                    .triggeredAt(triggeredAt)
                    .build();

            Alert saved = alertRepository.saveAndFlush(alert);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getStatus()).isEqualTo(AlertStatus.PENDING);
            assertThat(saved.getTriggeredAt()).isEqualTo(triggeredAt);
            assertThat(saved.getDisasterEvent().getId()).isEqualTo(primaryDisasterEvent.getId());
            assertThat(saved.getAlertRule().getId()).isEqualTo(primaryRule.getId());
        }

        @Test
        @DisplayName("should update alert status successfully")
        void shouldUpdateAlertStatus() {
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);

            alert.updateStatus(AlertStatus.PROCESSED);
            alertRepository.saveAndFlush(alert);

            entityManager.clear();

            Optional<Alert> updated = alertRepository.findById(alert.getId());
            assertThat(updated).isPresent();
            assertThat(updated.get().getStatus()).isEqualTo(AlertStatus.PROCESSED);
        }

        @Test
        @DisplayName("should fail when violating unique constraint on disasterEvent and alertRule")
        void shouldFailWhenViolatingUniqueConstraintOnEventAndRule() {
            persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);

            Alert duplicateAlert = Alert.builder()
                    .disasterEvent(primaryDisasterEvent)
                    .alertRule(primaryRule)
                    .status(AlertStatus.PENDING)
                    .triggeredAt(Instant.now())
                    .build();

            assertThatThrownBy(() -> {
                alertRepository.saveAndFlush(duplicateAlert);
            }).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_alert_event_rule");
        }
    }

    @Nested
    @DisplayName("findByDisasterEventIdAndAlertRuleId")
    class FindByDisasterEventIdAndAlertRuleId {

        @Test
        @DisplayName("should find alert when both disaster event ID and alert rule ID match")
        void shouldFindAlertWhenBothEventIdAndRuleIdMatch() {
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);

            Optional<Alert> found = alertRepository.findByDisasterEventIdAndAlertRuleId(
                    primaryDisasterEvent.getId(),
                    primaryRule.getId()
            );

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> {
                        assertThat(persisted.getId()).isEqualTo(alert.getId());
                        assertThat(persisted.getDisasterEvent().getId()).isEqualTo(primaryDisasterEvent.getId());
                        assertThat(persisted.getAlertRule().getId()).isEqualTo(primaryRule.getId());
                    });
        }

        @Test
        @DisplayName("should return empty when disaster event ID does not match")
        void shouldReturnEmptyWhenDisasterEventIdMismatched() {
            persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
            DisasterEvent otherEvent = persistDisasterEvent("PHIVOLCS", "phi-event-002", DisasterType.EARTHQUAKE, 5.5, 14.5000, 121.0000);

            Optional<Alert> found = alertRepository.findByDisasterEventIdAndAlertRuleId(
                    otherEvent.getId(),
                    primaryRule.getId()
            );

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return empty when alert rule ID does not match")
        void shouldReturnEmptyWhenAlertRuleIdMismatched() {
            persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
            AlertRule otherRule = persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 100.0);

            Optional<Alert> found = alertRepository.findByDisasterEventIdAndAlertRuleId(
                    primaryDisasterEvent.getId(),
                    otherRule.getId()
            );

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return empty when IDs do not exist")
        void shouldReturnEmptyWhenIdsDoNotExist() {
            Optional<Alert> found = alertRepository.findByDisasterEventIdAndAlertRuleId(UUID.randomUUID(), UUID.randomUUID());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByDisasterEventIdAndAlertRuleId")
    class ExistsByDisasterEventIdAndAlertRuleId {

        @Test
        @DisplayName("should return true when alert exists for disaster event and alert rule")
        void shouldReturnTrueWhenAlertExists() {
            persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);

            boolean exists = alertRepository.existsByDisasterEventIdAndAlertRuleId(
                    primaryDisasterEvent.getId(),
                    primaryRule.getId()
            );

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when alert does not exist for disaster event and alert rule")
        void shouldReturnFalseWhenAlertDoesNotExist() {
            boolean exists = alertRepository.existsByDisasterEventIdAndAlertRuleId(
                    primaryDisasterEvent.getId(),
                    primaryRule.getId()
            );

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByDisasterEventId")
    class FindByDisasterEventId {

        @Test
        @DisplayName("should return all alerts associated with specified disaster event ID")
        void shouldFindAllAlertsForDisasterEvent() {
            AlertRule secondRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 4.0, 30.0);
            DisasterEvent anotherEvent = persistDisasterEvent("PHIVOLCS", "phi-event-003", DisasterType.EARTHQUAKE, 4.8, 14.1, 121.1);

            Alert alert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
            Alert alert2 = persistAlert(primaryDisasterEvent, secondRule, AlertStatus.PROCESSED);
            persistAlert(anotherEvent, primaryRule, AlertStatus.PENDING);

            List<Alert> alerts = alertRepository.findByDisasterEventId(primaryDisasterEvent.getId());

            assertThat(alerts)
                    .hasSize(2)
                    .extracting(Alert::getId)
                    .containsExactlyInAnyOrder(alert1.getId(), alert2.getId());
        }

        @Test
        @DisplayName("should return empty list when no alerts exist for disaster event")
        void shouldReturnEmptyListWhenNoAlertsExistForDisasterEvent() {
            List<Alert> alerts = alertRepository.findByDisasterEventId(UUID.randomUUID());

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAlertRuleId")
    class FindByAlertRuleId {

        @Test
        @DisplayName("should return all alerts associated with specified alert rule ID")
        void shouldFindAllAlertsForAlertRule() {
            DisasterEvent secondEvent = persistDisasterEvent("PHIVOLCS", "phi-event-004", DisasterType.EARTHQUAKE, 5.1, 14.2, 121.2);
            AlertRule secondRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 4.0, 30.0);

            Alert alert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
            Alert alert2 = persistAlert(secondEvent, primaryRule, AlertStatus.PROCESSED);
            persistAlert(primaryDisasterEvent, secondRule, AlertStatus.PENDING);

            List<Alert> alerts = alertRepository.findByAlertRuleId(primaryRule.getId());

            assertThat(alerts)
                    .hasSize(2)
                    .extracting(Alert::getId)
                    .containsExactlyInAnyOrder(alert1.getId(), alert2.getId());
        }

        @Test
        @DisplayName("should return empty list when no alerts exist for alert rule")
        void shouldReturnEmptyListWhenNoAlertsExistForAlertRule() {
            List<Alert> alerts = alertRepository.findByAlertRuleId(UUID.randomUUID());

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should return all alerts matching specified status")
        void shouldFindAlertsByStatus() {
            AlertRule rule2 = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 4.0, 20.0);
            DisasterEvent event2 = persistDisasterEvent("PHIVOLCS", "phi-event-005", DisasterType.EARTHQUAKE, 4.9, 14.3, 121.3);

            Alert pending1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
            Alert pending2 = persistAlert(primaryDisasterEvent, rule2, AlertStatus.PENDING);
            Alert processed = persistAlert(event2, primaryRule, AlertStatus.PROCESSED);

            List<Alert> pendingAlerts = alertRepository.findByStatus(AlertStatus.PENDING);
            List<Alert> processedAlerts = alertRepository.findByStatus(AlertStatus.PROCESSED);

            assertThat(pendingAlerts)
                    .hasSize(2)
                    .extracting(Alert::getId)
                    .containsExactlyInAnyOrder(pending1.getId(), pending2.getId());

            assertThat(processedAlerts)
                    .hasSize(1)
                    .extracting(Alert::getId)
                    .containsExactly(processed.getId());
        }

        @Test
        @DisplayName("should return empty list when no alerts match given status")
        void shouldReturnEmptyListWhenNoAlertsMatchStatus() {
            persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);

            List<Alert> cancelledAlerts = alertRepository.findByStatus(AlertStatus.CANCELLED);

            assertThat(cancelledAlerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAlertRuleLocationUserId")
    class FindByAlertRuleLocationUserId {

        @Test
        @DisplayName("should return paginated alerts belonging to specified user and exclude others")
        void shouldFindPagedAlertsForUser() {
            User otherUser = persistUser("other@example.com");
            Location otherLocation = persistLocation(otherUser, "Other Office", 14.5500, 121.0300);
            AlertRule otherRule = persistAlertRule(otherLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);

            DisasterEvent event2 = persistDisasterEvent("USGS", "usgs-event-002", DisasterType.EARTHQUAKE, 5.8, 14.4, 121.0);
            DisasterEvent event3 = persistDisasterEvent("USGS", "usgs-event-003", DisasterType.EARTHQUAKE, 6.0, 14.5, 121.1);

            Alert userAlert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
            Alert userAlert2 = persistAlert(event2, primaryRule, AlertStatus.PROCESSED);
            Alert userAlert3 = persistAlert(event3, primaryRule, AlertStatus.PENDING);
            persistAlert(primaryDisasterEvent, otherRule, AlertStatus.PENDING);

            Page<Alert> page0 = alertRepository.findByAlertRuleLocationUserId(
                    primaryUser.getId(),
                    PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "triggeredAt"))
            );

            assertThat(page0.getTotalElements()).isEqualTo(3);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getContent())
                    .extracting(Alert::getId)
                    .containsExactly(userAlert1.getId(), userAlert2.getId());

            Page<Alert> page1 = alertRepository.findByAlertRuleLocationUserId(
                    primaryUser.getId(),
                    PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "triggeredAt"))
            );

            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getContent())
                    .extracting(Alert::getId)
                    .containsExactly(userAlert3.getId());
        }

        @Test
        @DisplayName("should return empty page when user has no alerts")
        void shouldReturnEmptyPageWhenUserHasNoAlerts() {
            User emptyUser = persistUser("empty@example.com");

            Page<Alert> page = alertRepository.findByAlertRuleLocationUserId(
                    emptyUser.getId(),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAlertRuleLocationUserIdAndStatus")
    class FindByAlertRuleLocationUserIdAndStatus {

        @Test
        @DisplayName("should return paginated alerts matching both user ID and status")
        void shouldFindPagedAlertsForUserFilteredByStatus() {
            DisasterEvent event2 = persistDisasterEvent("USGS", "usgs-event-002", DisasterType.EARTHQUAKE, 5.8, 14.4, 121.0);
            DisasterEvent event3 = persistDisasterEvent("USGS", "usgs-event-003", DisasterType.EARTHQUAKE, 6.0, 14.5, 121.1);

            User otherUser = persistUser("other@example.com");
            Location otherLocation = persistLocation(otherUser, "Other Office", 14.5500, 121.0300);
            AlertRule otherRule = persistAlertRule(otherLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);

            Alert userPending1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
            Alert userPending2 = persistAlert(event2, primaryRule, AlertStatus.PENDING);
            Alert userProcessed = persistAlert(event3, primaryRule, AlertStatus.PROCESSED);
            persistAlert(primaryDisasterEvent, otherRule, AlertStatus.PENDING);

            Page<Alert> pendingPage = alertRepository.findByAlertRuleLocationUserIdAndStatus(
                    primaryUser.getId(),
                    AlertStatus.PENDING,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "triggeredAt"))
            );

            assertThat(pendingPage.getTotalElements()).isEqualTo(2);
            assertThat(pendingPage.getContent())
                    .extracting(Alert::getId)
                    .containsExactly(userPending1.getId(), userPending2.getId());

            Page<Alert> processedPage = alertRepository.findByAlertRuleLocationUserIdAndStatus(
                    primaryUser.getId(),
                    AlertStatus.PROCESSED,
                    PageRequest.of(0, 10)
            );

            assertThat(processedPage.getTotalElements()).isEqualTo(1);
            assertThat(processedPage.getContent())
                    .extracting(Alert::getId)
                    .containsExactly(userProcessed.getId());
        }

        @Test
        @DisplayName("should return empty page when user has no alerts with specified status")
        void shouldReturnEmptyPageWhenNoAlertsMatchStatusForUser() {
            persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);

            Page<Alert> cancelledPage = alertRepository.findByAlertRuleLocationUserIdAndStatus(
                    primaryUser.getId(),
                    AlertStatus.CANCELLED,
                    PageRequest.of(0, 10)
            );

            assertThat(cancelledPage.getTotalElements()).isZero();
            assertThat(cancelledPage.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdWithDetails")
    class FindByIdWithDetails {

        @Test
        @DisplayName("should eagerly fetch alert with its disaster event, alert rule, location, and user")
        void shouldEagerlyFetchAlertWithAllAssociatedDetails() {
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);

            entityManager.flush();
            entityManager.clear();

            Optional<Alert> result = alertRepository.findByIdWithDetails(alert.getId());

            assertThat(result).isPresent();
            Alert fetchedAlert = result.get();

            assertThat(fetchedAlert.getId()).isEqualTo(alert.getId());
            assertThat(fetchedAlert.getStatus()).isEqualTo(AlertStatus.PENDING);

            // Verify disaster event is fetched and fully accessible without lazy loading issuess
            DisasterEvent fetchedEvent = fetchedAlert.getDisasterEvent();
            assertThat(fetchedEvent).isNotNull();
            assertThat(fetchedEvent.getId()).isEqualTo(primaryDisasterEvent.getId());
            assertThat(fetchedEvent.getSource()).isEqualTo("USGS");
            assertThat(fetchedEvent.getExternalId()).isEqualTo("usgs-event-001");
            assertThat(fetchedEvent.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(fetchedEvent.getMagnitude()).isEqualTo(6.2);

            // Verify alert rule is fetched
            AlertRule fetchedRule = fetchedAlert.getAlertRule();
            assertThat(fetchedRule).isNotNull();
            assertThat(fetchedRule.getId()).isEqualTo(primaryRule.getId());
            assertThat(fetchedRule.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(fetchedRule.getRadiusKm()).isEqualTo(50.0);

            // Verify location is fetched via alert rule
            Location fetchedLocation = fetchedRule.getLocation();
            assertThat(fetchedLocation).isNotNull();
            assertThat(fetchedLocation.getId()).isEqualTo(primaryLocation.getId());
            assertThat(fetchedLocation.getName()).isEqualTo("Home");
            assertThat(fetchedLocation.getLatitude()).isEqualTo(14.5995);
            assertThat(fetchedLocation.getLongitude()).isEqualTo(120.9842);

            // Verify user is fetched via location
            User fetchedUser = fetchedLocation.getUser();
            assertThat(fetchedUser).isNotNull();
            assertThat(fetchedUser.getId()).isEqualTo(primaryUser.getId());
            assertThat(fetchedUser.getEmail()).isEqualTo("user1@example.com");
            assertThat(fetchedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should return empty optional when alert ID does not exist")
        void shouldReturnEmptyWhenAlertIdDoesNotExist() {
            Optional<Alert> result = alertRepository.findByIdWithDetails(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }

    // --- Helper methods to set up test entities with proper constraints ---

    private User persistUser(String email) {
        User user = User.builder()
                .email(email)
                .passwordHash("hashed_password_123")
                .status(UserStatus.ACTIVE)
                .build();
        return entityManager.persistAndFlush(user);
    }

    private GeographicArea persistGeographicArea(String psgcCode, String name) {
        GeographicArea area = GeographicArea.builder()
                .psgcCode(psgcCode)
                .name(name)
                .type(GeographicAreaType.MUNICIPALITY)
                .active(true)
                .build();
        return entityManager.persistAndFlush(area);
    }

    private Location persistLocation(User user, String name, double latitude, double longitude) {
        Location location = Location.create(
                user,
                name,
                "Sample Address",
                geographicArea,
                latitude,
                longitude,
                geoPointFactory
        );
        return entityManager.persistAndFlush(location);
    }

    private AlertRule persistAlertRule(Location location, DisasterType disasterType, Double minMagnitude, Double radiusKm) {
        AlertRule rule = AlertRule.builder()
                .location(location)
                .disasterType(disasterType)
                .enabled(true)
                .minimumMagnitude(minMagnitude)
                .radiusKm(radiusKm)
                .minimumSeverity("MODERATE")
                .build();
        return entityManager.persistAndFlush(rule);
    }

    private DisasterEvent persistDisasterEvent(String source, String externalId, DisasterType type, Double magnitude, double lat, double lon) {
        DisasterEvent event = DisasterEvent.builder()
                .source(source)
                .externalId(externalId)
                .disasterType(type)
                .occurredAt(Instant.now())
                .latitude(lat)
                .longitude(lon)
                .location(geoPointFactory.create(lat, lon))
                .magnitude(magnitude)
                .depthKm(10.0)
                .severity("HIGH")
                .build();
        return entityManager.persistAndFlush(event);
    }

    private Alert persistAlert(DisasterEvent disasterEvent, AlertRule alertRule, AlertStatus status) {
        Alert alert = Alert.builder()
                .disasterEvent(disasterEvent)
                .alertRule(alertRule)
                .status(status)
                .triggeredAt(Instant.now())
                .build();
        return entityManager.persistAndFlush(alert);
    }
}
