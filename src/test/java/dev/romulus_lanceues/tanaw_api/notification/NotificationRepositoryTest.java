package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.jts.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
public class NotificationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    TestEntityManager entityManager;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();

    private User primaryUser;
    private GeographicArea geographicArea;
    private Location primaryLocation;
    private AlertRule primaryRule;
    private DisasterEvent primaryDisasterEvent;
    private Alert primaryAlert;

    @BeforeEach
    void setUp() {
        primaryUser = persistUser("user1@example.com");
        geographicArea = persistGeographicArea("137600000", "City of Manila");
        primaryLocation = persistLocation(primaryUser, "Home", 14.5995, 120.9842);
        primaryRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
        primaryDisasterEvent = persistDisasterEvent("USGS", "usgs-event-001", DisasterType.EARTHQUAKE, 6.2, 14.6000, 120.9850);
        primaryAlert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING);
    }

    @Nested
    @DisplayName("entity persistence and relationship")
    class EntityPersistenceAndRelationship {

        @Test
        @DisplayName("should persist notification successfully with fields")
        void shouldPersistNotificationSuccessfullyWithFields() {

            Notification notification = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.DISCORD)
                    .destination("Discord Server")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(0)
                    .build();

            Notification persisted = notificationRepository.saveAndFlush(notification);

            entityManager.clear();

            Optional<Notification> found = notificationRepository.findById(persisted.getId());

            assertThat(found).isPresent().hasValueSatisfying( persistedNotif -> {
                assertThat(persistedNotif.getId()).isEqualTo(persisted.getId());
                assertThat(persistedNotif.getAlert().getId()).isEqualTo(primaryAlert.getId());
                assertThat(persistedNotif.getChannel()).isEqualTo(NotificationChannel.DISCORD);
                assertThat(persistedNotif.getDestination()).isEqualTo("Discord Server");
                assertThat(persistedNotif.getStatus()).isEqualTo(NotificationStatus.PENDING);
                assertThat(persistedNotif.getAttemptCount()).isEqualTo(0);
                assertThat(persistedNotif.getCreatedAt()).isNotNull();
            });
        }

        @Test
        @DisplayName("should persist when given a valid parent")
        void shouldPersistWithCorrectParent() {
            Notification notification = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.EMAIL)
                    .destination("Email")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            Notification persisted = notificationRepository.saveAndFlush(notification);

            entityManager.clear();
            Optional<Notification> found = notificationRepository.findById(persisted.getId());

            assertThat(found).isPresent().hasValueSatisfying( persistedNotif -> {
                assertThat(persistedNotif.getAlert().getId()).isEqualTo(primaryAlert.getId());
                assertThat(persistedNotif.getAlert().getAlertRule().getId()).isEqualTo(primaryRule.getId());
                assertThat(persistedNotif.getAlert().getDisasterEvent().getId()).isEqualTo(primaryDisasterEvent.getId());
            });
        }

        @Test
        @DisplayName("should not persist when not given a valid parent")
        void shouldNotPersistWithNoParent() {

            Notification notification = Notification.builder()
                    .channel(NotificationChannel.EMAIL)
                    .destination("Email")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            assertThatThrownBy(
                    () -> notificationRepository.saveAndFlush(notification))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should increase attempt when increment attempt")
        void shouldIncreaseAttemptWhenIncrementAttempt() {

            Notification notification = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.EMAIL)
                    .destination("Email")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            Notification persisted = notificationRepository.saveAndFlush(notification);

            entityManager.clear();

            Optional<Notification> found = notificationRepository.findById(persisted.getId());

            assertThat(found).isPresent().hasValueSatisfying( persistedNotif -> {
                assertThat(persistedNotif.getAttemptCount()).isEqualTo(1);
            });

            Notification persistedNotification = found.get();

            persistedNotification.incrementAttempt();

            Notification persistedUpdate = notificationRepository.saveAndFlush(persistedNotification);

            entityManager.clear();

            Optional<Notification> foundUpdated = notificationRepository.findById(persistedUpdate.getId());

            assertThat(foundUpdated).isPresent().hasValueSatisfying( persistedNotif ->
                assertThat(persistedNotif.getAttemptCount()).isEqualTo(2));

        }

        @Test
        @DisplayName("should updated notification status to success when record success")
        void shouldUpdatedNotificationStatusToSuccessWhenRecordSuccess(){
            Notification notification = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.EMAIL)
                    .destination("Email")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            Notification persisted = notificationRepository.saveAndFlush(notification);

            entityManager.clear();

            Optional<Notification> found = notificationRepository.findById(persisted.getId());

            assertThat(found).isPresent().hasValueSatisfying( persistedNotif ->{
                assertThat(persistedNotif.getStatus()).isEqualTo(NotificationStatus.PENDING);
                assertThat(persistedNotif.getSentAt()).isNull();
            });

            Notification persistedNotification = found.get();
            persistedNotification.recordSuccess(Instant.now(), NotificationStatus.SENT);

            Notification persistedUpdate = notificationRepository.saveAndFlush(persistedNotification);
            entityManager.clear();

            Optional<Notification> foundUpdated = notificationRepository.findById(persistedUpdate.getId());

            assertThat(foundUpdated).isPresent().hasValueSatisfying( persistedNotif ->{
                assertThat(persistedNotif.getStatus()).isEqualTo(NotificationStatus.SENT);
                assertThat(persistedNotif.getSentAt()).isNotNull();
            });
        }

        @Test
        @DisplayName("should record notification failure when record failure")
        void shouldRecordNotificationFailureWhenRecordFailure(){
            Notification notification = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.TELEGRAM)
                    .destination("Email")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            Notification persisted = notificationRepository.saveAndFlush(notification);

            entityManager.clear();

            Optional<Notification> found = notificationRepository.findById(persisted.getId());

            assertThat(found).isPresent().hasValueSatisfying( persistedNotif ->{
                assertThat(persistedNotif.getStatus()).isEqualTo(NotificationStatus.PENDING);
                assertThat(persistedNotif.getFailureReason()).isNull();
            });

            Notification persistedNotification = found.get();

            persistedNotification.recordFailure(NotificationStatus.FAILED, "Provided platform unreachable");

            Notification persistedUpdate = notificationRepository.saveAndFlush(persistedNotification);

            entityManager.clear();

            Optional<Notification> foundUpdated = notificationRepository.findById(persistedUpdate.getId());

            assertThat(foundUpdated).isPresent().hasValueSatisfying( persistedNotif ->{
                assertThat(persistedNotif.getStatus()).isEqualTo(NotificationStatus.FAILED);
                assertThat(persistedNotif.getFailureReason()).isEqualTo("Provided platform unreachable");
            });
        }

        @Test
        @DisplayName("should fail when violating unique constraint on alert and channel")
        void shouldFailWhenViolatingUniqueAlertAndChannelConstraint() {
            Notification notification1 = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.EMAIL)
                    .destination("user@example.com")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(0)
                    .build();
            notificationRepository.saveAndFlush(notification1);

            Notification notification2 = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.EMAIL)
                    .destination("user2@example.com")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(0)
                    .build();

            assertThatThrownBy(() -> notificationRepository.saveAndFlush(notification2))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_notification_alert_channel");
        }

    }

    @Nested
    @DisplayName("findByAlertIdAndChannel")
    class FindByAlertIdAndChannel {

        @Test
        @DisplayName("should find notification when alert id and channel match the notification")
        void shouldFindNotificationWhenAlertIdAndChannelMatchTheNotification(){
            Notification notification = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.DISCORD)
                    .destination("Email")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            Notification persisted = notificationRepository.saveAndFlush(notification);

            entityManager.clear();

            Optional<Notification> found = notificationRepository.findByAlertIdAndChannel(primaryAlert.getId(), NotificationChannel.DISCORD);

            assertThat(found).isPresent().hasValueSatisfying( persistedNotif ->{
                assertThat(persistedNotif.getId()).isEqualTo(persisted.getId());
                assertThat(persistedNotif.getAlert().getId()).isEqualTo(primaryAlert.getId());
            });
        }

        @Test
        @DisplayName("should return empty when alert id and channel did not match a notification")
        void shouldReturnEmptyWhenAlertIdAndChannelDidNotMatchANotification(){

            Location secondLocation = persistLocation(primaryUser, "Office", 14.5547, 121.0244);
            AlertRule secondRule = persistAlertRule(secondLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert secondAlert = persistAlert(primaryDisasterEvent, secondRule, AlertStatus.PENDING);

            Notification notification = Notification.builder()
                    .alert(secondAlert)
                    .channel(NotificationChannel.DISCORD)
                    .destination("Discord Server")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            Notification persisted = notificationRepository.saveAndFlush(notification);

            entityManager.clear();

            Optional<Notification> found = notificationRepository.findByAlertIdAndChannel(primaryAlert.getId(), NotificationChannel.DISCORD);

            assertThat(found).isEmpty();

        }

        @Test
        @DisplayName("should return empty when channel does not match notification")
        void shouldReturnEmptyWhenChannelMismatches() {
            Notification notification = Notification.builder()
                    .alert(primaryAlert)
                    .channel(NotificationChannel.EMAIL)
                    .destination("Email")
                    .status(NotificationStatus.PENDING)
                    .attemptCount(1)
                    .build();

            notificationRepository.saveAndFlush(notification);
            entityManager.clear();

            Optional<Notification> found = notificationRepository.findByAlertIdAndChannel(primaryAlert.getId(), NotificationChannel.TELEGRAM);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByAlertIdAndChannel")
    class ExistsByAlertIdAndChannel {

        @Test
        @DisplayName("should return true when notification exists for alert and channel")
        void shouldReturnTrueWhenNotificationExistsForAlertAndChannel() {
            persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);

            boolean exists = notificationRepository.existsByAlertIdAndChannel(primaryAlert.getId(), NotificationChannel.EMAIL);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when notification does not exist for alert and channel")
        void shouldReturnFalseWhenNotificationDoesNotExistForAlertAndChannel() {
            boolean exists = notificationRepository.existsByAlertIdAndChannel(primaryAlert.getId(), NotificationChannel.EMAIL);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false when channel mismatches existing notification")
        void shouldReturnFalseWhenChannelMismatches() {
            persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);

            boolean exists = notificationRepository.existsByAlertIdAndChannel(primaryAlert.getId(), NotificationChannel.TELEGRAM);

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByAlertId")
    class FindByAlertId {

        @Test
        @DisplayName("should return all notifications associated with specified alert ID")
        void shouldReturnAllNotificationsAssociatedWithSpecifiedAlertId() {
            Location secondLocation = persistLocation(primaryUser, "Office", 14.5547, 121.0244);
            AlertRule secondRule = persistAlertRule(secondLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert otherAlert = persistAlert(primaryDisasterEvent, secondRule, AlertStatus.PENDING);

            Notification notif1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);
            Notification notif2 = persistNotification(primaryAlert, NotificationChannel.TELEGRAM, NotificationStatus.SENT);
            persistNotification(otherAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);

            List<Notification> notifications = notificationRepository.findByAlertId(primaryAlert.getId());

            assertThat(notifications)
                    .hasSize(2)
                    .extracting(Notification::getId)
                    .containsExactlyInAnyOrder(notif1.getId(), notif2.getId());
        }

        @Test
        @DisplayName("should return empty list when no notifications exist for alert ID")
        void shouldReturnEmptyListWhenNoNotificationsExistForAlertId() {
            List<Notification> notifications = notificationRepository.findByAlertId(UUID.randomUUID());

            assertThat(notifications).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should return all notifications with specified status")
        void shouldReturnAllNotificationsWithSpecifiedStatus() {
            Location secondLocation = persistLocation(primaryUser, "Office", 14.5547, 121.0244);
            AlertRule secondRule = persistAlertRule(secondLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert secondAlert = persistAlert(primaryDisasterEvent, secondRule, AlertStatus.PENDING);

            Notification pending1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);
            Notification pending2 = persistNotification(secondAlert, NotificationChannel.DISCORD, NotificationStatus.PENDING);
            persistNotification(primaryAlert, NotificationChannel.TELEGRAM, NotificationStatus.SENT);

            List<Notification> result = notificationRepository.findByStatus(NotificationStatus.PENDING);

            assertThat(result)
                    .hasSize(2)
                    .extracting(Notification::getId)
                    .containsExactlyInAnyOrder(pending1.getId(), pending2.getId());
        }

        @Test
        @DisplayName("should return empty list when no notifications match status")
        void shouldReturnEmptyListWhenNoNotificationsMatchStatus() {
            persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);

            List<Notification> result = notificationRepository.findByStatus(NotificationStatus.FAILED);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus with Pageable")
    class FindByStatusWithPageable {

        @Test
        @DisplayName("should return paginated notifications matching specified status")
        void shouldReturnPaginatedNotificationsMatchingStatus() {
            Location loc2 = persistLocation(primaryUser, "Office", 14.5547, 121.0244);
            AlertRule rule2 = persistAlertRule(loc2, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert alert2 = persistAlert(primaryDisasterEvent, rule2, AlertStatus.PENDING);

            Location loc3 = persistLocation(primaryUser, "Warehouse", 14.5600, 121.0300);
            AlertRule rule3 = persistAlertRule(loc3, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert alert3 = persistAlert(primaryDisasterEvent, rule3, AlertStatus.PENDING);

            Notification notif1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);
            Notification notif2 = persistNotification(alert2, NotificationChannel.TELEGRAM, NotificationStatus.PENDING);
            Notification notif3 = persistNotification(alert3, NotificationChannel.DISCORD, NotificationStatus.PENDING);
            persistNotification(primaryAlert, NotificationChannel.DISCORD, NotificationStatus.SENT);

            Page<Notification> page0 = notificationRepository.findByStatus(
                    NotificationStatus.PENDING,
                    PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page0.getTotalElements()).isEqualTo(3);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getContent())
                    .extracting(Notification::getId)
                    .containsExactly(notif1.getId(), notif2.getId());

            Page<Notification> page1 = notificationRepository.findByStatus(
                    NotificationStatus.PENDING,
                    PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getContent())
                    .extracting(Notification::getId)
                    .containsExactly(notif3.getId());
        }

        @Test
        @DisplayName("should return empty page when no notifications match status")
        void shouldReturnEmptyPageWhenNoNotificationsMatchStatus() {
            persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);

            Page<Notification> page = notificationRepository.findByStatus(
                    NotificationStatus.CANCELLED,
                    PageRequest.of(0, 10)
            );

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAlertAlertRuleLocationUserId")
    class FindByAlertAlertRuleLocationUserId {

        @Test
        @DisplayName("should return paginated notifications belonging to specified user and exclude others")
        void shouldFindPagedNotificationsForUser() {
            User otherUser = persistUser("other@example.com");
            Location otherLocation = persistLocation(otherUser, "Other Office", 14.5500, 121.0300);
            AlertRule otherRule = persistAlertRule(otherLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert otherAlert = persistAlert(primaryDisasterEvent, otherRule, AlertStatus.PENDING);

            Location loc2 = persistLocation(primaryUser, "Office", 14.5547, 121.0244);
            AlertRule rule2 = persistAlertRule(loc2, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert alert2 = persistAlert(primaryDisasterEvent, rule2, AlertStatus.PENDING);

            Notification notif1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);
            Notification notif2 = persistNotification(primaryAlert, NotificationChannel.DISCORD, NotificationStatus.SENT);
            Notification notif3 = persistNotification(alert2, NotificationChannel.TELEGRAM, NotificationStatus.PENDING);
            persistNotification(otherAlert, NotificationChannel.EMAIL, NotificationStatus.PENDING);

            Page<Notification> page0 = notificationRepository.findByAlertAlertRuleLocationUserId(
                    primaryUser.getId(),
                    PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page0.getTotalElements()).isEqualTo(3);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getContent())
                    .extracting(Notification::getId)
                    .containsExactly(notif1.getId(), notif2.getId());

            Page<Notification> page1 = notificationRepository.findByAlertAlertRuleLocationUserId(
                    primaryUser.getId(),
                    PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getContent())
                    .extracting(Notification::getId)
                    .containsExactly(notif3.getId());
        }

        @Test
        @DisplayName("should return empty page when user has no notifications")
        void shouldReturnEmptyPageWhenUserHasNoNotifications() {
            User emptyUser = persistUser("empty@example.com");

            Page<Notification> page = notificationRepository.findByAlertAlertRuleLocationUserId(
                    emptyUser.getId(),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findPendingForDispatch")
    class FindPendingForDispatch {

        @Test
        @DisplayName("should return pending notifications with attempts below max and eager fetch details ordered by createdAt")
        void shouldReturnPendingNotificationsWithAttemptsBelowMaxOrderedByCreatedAt() {
            Location loc2 = persistLocation(primaryUser, "Office", 14.5547, 121.0244);
            AlertRule rule2 = persistAlertRule(loc2, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert alert2 = persistAlert(primaryDisasterEvent, rule2, AlertStatus.PENDING);

            // Valid: PENDING, attemptCount = 0 (< 3)
            Notification notif1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, "dest1", NotificationStatus.PENDING, 0);

            // Valid: PENDING, attemptCount = 2 (< 3)
            Notification notif2 = persistNotification(primaryAlert, NotificationChannel.DISCORD, "dest2", NotificationStatus.PENDING, 2);

            // Excluded: PENDING, attemptCount = 3 (not < 3)
            persistNotification(primaryAlert, NotificationChannel.TELEGRAM, "dest3", NotificationStatus.PENDING, 3);

            // Excluded: status is SENT (not PENDING)
            persistNotification(alert2, NotificationChannel.EMAIL, "dest4", NotificationStatus.SENT, 0);

            entityManager.flush();
            entityManager.clear();

            List<Notification> result = notificationRepository.findPendingForDispatch(NotificationStatus.PENDING, 3);

            assertThat(result)
                    .hasSize(2)
                    .extracting(Notification::getId)
                    .containsExactly(notif1.getId(), notif2.getId());

            // Verify eager fetches work without lazy initialization exception after clear
            Notification fetched1 = result.get(0);
            assertThat(fetched1.getAlert()).isNotNull();
            assertThat(fetched1.getAlert().getId()).isEqualTo(primaryAlert.getId());

            assertThat(fetched1.getAlert().getDisasterEvent()).isNotNull();
            assertThat(fetched1.getAlert().getDisasterEvent().getId()).isEqualTo(primaryDisasterEvent.getId());
            assertThat(fetched1.getAlert().getDisasterEvent().getSource()).isEqualTo("USGS");

            assertThat(fetched1.getAlert().getAlertRule()).isNotNull();
            assertThat(fetched1.getAlert().getAlertRule().getId()).isEqualTo(primaryRule.getId());
            assertThat(fetched1.getAlert().getAlertRule().getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
        }

        @Test
        @DisplayName("should return empty list when no notifications match pending criteria")
        void shouldReturnEmptyListWhenNoNotificationsMatchCriteria() {
            persistNotification(primaryAlert, NotificationChannel.EMAIL, "dest1", NotificationStatus.PENDING, 3);

            List<Notification> result = notificationRepository.findPendingForDispatch(NotificationStatus.PENDING, 3);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdWithDetails")
    class FindByIdWithDetails {

        @Test
        @DisplayName("should eagerly fetch notification with alert, disaster event, alert rule, and location")
        void shouldEagerlyFetchNotificationWithAllAssociatedDetails() {
            Notification notification = persistNotification(primaryAlert, NotificationChannel.DISCORD, "Discord Server", NotificationStatus.PENDING, 0);

            entityManager.flush();
            entityManager.clear();

            Optional<Notification> result = notificationRepository.findByIdWithDetails(notification.getId());

            assertThat(result).isPresent();
            Notification fetchedNotification = result.get();

            assertThat(fetchedNotification.getId()).isEqualTo(notification.getId());
            assertThat(fetchedNotification.getChannel()).isEqualTo(NotificationChannel.DISCORD);
            assertThat(fetchedNotification.getDestination()).isEqualTo("Discord Server");
            assertThat(fetchedNotification.getStatus()).isEqualTo(NotificationStatus.PENDING);

            // Verify alert
            Alert fetchedAlert = fetchedNotification.getAlert();
            assertThat(fetchedAlert).isNotNull();
            assertThat(fetchedAlert.getId()).isEqualTo(primaryAlert.getId());
            assertThat(fetchedAlert.getStatus()).isEqualTo(AlertStatus.PENDING);

            // Verify disaster event
            DisasterEvent fetchedEvent = fetchedAlert.getDisasterEvent();
            assertThat(fetchedEvent).isNotNull();
            assertThat(fetchedEvent.getId()).isEqualTo(primaryDisasterEvent.getId());
            assertThat(fetchedEvent.getSource()).isEqualTo("USGS");
            assertThat(fetchedEvent.getExternalId()).isEqualTo("usgs-event-001");
            assertThat(fetchedEvent.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(fetchedEvent.getMagnitude()).isEqualTo(6.2);

            // Verify alert rule
            AlertRule fetchedRule = fetchedAlert.getAlertRule();
            assertThat(fetchedRule).isNotNull();
            assertThat(fetchedRule.getId()).isEqualTo(primaryRule.getId());
            assertThat(fetchedRule.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(fetchedRule.getRadiusKm()).isEqualTo(50.0);

            // Verify location via alert rule
            Location fetchedLocation = fetchedRule.getLocation();
            assertThat(fetchedLocation).isNotNull();
            assertThat(fetchedLocation.getId()).isEqualTo(primaryLocation.getId());
            assertThat(fetchedLocation.getName()).isEqualTo("Home");
            assertThat(fetchedLocation.getLatitude()).isEqualTo(14.5995);
            assertThat(fetchedLocation.getLongitude()).isEqualTo(120.9842);
        }

        @Test
        @DisplayName("should return empty optional when notification ID does not exist")
        void shouldReturnEmptyWhenNotificationIdDoesNotExist() {
            Optional<Notification> result = notificationRepository.findByIdWithDetails(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }



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

    private Notification persistNotification(Alert alert, NotificationChannel channel, String destination, NotificationStatus status, int attemptCount) {
        Notification notification = Notification.builder()
                .alert(alert)
                .channel(channel)
                .destination(destination)
                .status(status)
                .attemptCount(attemptCount)
                .build();
        return entityManager.persistAndFlush(notification);
    }

    private Notification persistNotification(Alert alert, NotificationChannel channel, NotificationStatus status) {
        return persistNotification(alert, channel, "default-destination", status, 0);
    }
}
