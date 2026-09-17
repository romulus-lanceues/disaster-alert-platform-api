package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
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
    @DisplayName("findByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("should return projected NotificationResponse with full event and location details when owned by user")
        void shouldReturnProjectedNotificationResponseWithDetails_whenNotificationBelongsToUser() {
            Notification notification = persistNotification(primaryAlert, NotificationChannel.EMAIL, "user1@example.com", NotificationStatus.PENDING, 0);

            entityManager.flush();
            entityManager.clear();

            Optional<NotificationResponse> result = notificationRepository.findByIdAndUserId(notification.getId(), primaryUser.getId());

            assertThat(result).isPresent();
            NotificationResponse response = result.get();

            assertThat(response.id()).isEqualTo(notification.getId());
            assertThat(response.alertId()).isEqualTo(primaryAlert.getId());
            assertThat(response.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(response.destination()).isEqualTo("user1@example.com");
            assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(response.attemptCount()).isZero();
            assertThat(response.alertStatus()).isEqualTo(AlertStatus.PENDING);

            // Location details
            assertThat(response.locationId()).isEqualTo(primaryLocation.getId());
            assertThat(response.locationName()).isEqualTo("Home");

            // Disaster event details
            assertThat(response.disasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(response.disasterEventId()).isEqualTo(primaryDisasterEvent.getId());
            assertThat(response.magnitude()).isEqualTo(6.2);
            assertThat(response.severity()).isEqualTo("HIGH");
            assertThat(response.depthKm()).isEqualTo(10.0);
            assertThat(response.latitude()).isEqualTo(14.6000);
            assertThat(response.longitude()).isEqualTo(120.9850);
        }

        @Test
        @DisplayName("should return empty optional when notification belongs to another user")
        void shouldReturnEmpty_whenNotificationBelongsToAnotherUser() {
            User otherUser = persistUser("other@example.com");
            Notification notification = persistNotification(primaryAlert, NotificationChannel.EMAIL, "user1@example.com", NotificationStatus.PENDING, 0);

            entityManager.flush();
            entityManager.clear();

            Optional<NotificationResponse> result = notificationRepository.findByIdAndUserId(notification.getId(), otherUser.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty optional when notification ID does not exist")
        void shouldReturnEmpty_whenNotificationDoesNotExist() {
            Optional<NotificationResponse> result = notificationRepository.findByIdAndUserId(UUID.randomUUID(), primaryUser.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("should return paginated notifications filtered by status for specified user")
        void shouldReturnPagedNotifications_whenFilteredByStatus() {
            User otherUser = persistUser("other@example.com");
            Location otherLocation = persistLocation(otherUser, "Other Office", 14.5500, 121.0300);
            AlertRule otherRule = persistAlertRule(otherLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert otherAlert = persistAlert(primaryDisasterEvent, otherRule, AlertStatus.PENDING);

            Notification notif1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, "email", NotificationStatus.PENDING, 0);
            persistNotification(primaryAlert, NotificationChannel.DISCORD, "discord", NotificationStatus.SENT, 0);
            // Notification belonging to other user should be excluded
            persistNotification(otherAlert, NotificationChannel.EMAIL, "other-email", NotificationStatus.PENDING, 0);

            entityManager.flush();
            entityManager.clear();

            Page<NotificationResponse> page = notificationRepository.findByUserId(
                    primaryUser.getId(),
                    NotificationStatus.PENDING,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).hasSize(1);
            NotificationResponse item = page.getContent().get(0);
            assertThat(item.id()).isEqualTo(notif1.getId());
            assertThat(item.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(item.locationName()).isEqualTo("Home");
        }

        @Test
        @DisplayName("should return all notifications across all statuses when status parameter is null")
        void shouldReturnAllNotificationsForUser_whenStatusIsNull() {
            Notification notif1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, "email", NotificationStatus.PENDING, 0);
            Notification notif2 = persistNotification(primaryAlert, NotificationChannel.DISCORD, "discord", NotificationStatus.SENT, 0);

            entityManager.flush();
            entityManager.clear();

            Page<NotificationResponse> page = notificationRepository.findByUserId(
                    primaryUser.getId(),
                    null,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting(NotificationResponse::id)
                    .containsExactly(notif1.getId(), notif2.getId());
        }

        @Test
        @DisplayName("should handle pagination correctly across multiple pages")
        void shouldHandlePaginationCorrectly() {
            Location loc2 = persistLocation(primaryUser, "Office", 14.5547, 121.0244);
            AlertRule rule2 = persistAlertRule(loc2, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert alert2 = persistAlert(primaryDisasterEvent, rule2, AlertStatus.PENDING);

            Location loc3 = persistLocation(primaryUser, "Warehouse", 14.5600, 121.0300);
            AlertRule rule3 = persistAlertRule(loc3, DisasterType.EARTHQUAKE, 5.0, 50.0);
            Alert alert3 = persistAlert(primaryDisasterEvent, rule3, AlertStatus.PENDING);

            Notification notif1 = persistNotification(primaryAlert, NotificationChannel.EMAIL, "dest1", NotificationStatus.PENDING, 0);
            Notification notif2 = persistNotification(alert2, NotificationChannel.TELEGRAM, "dest2", NotificationStatus.PENDING, 0);
            Notification notif3 = persistNotification(alert3, NotificationChannel.DISCORD, "dest3", NotificationStatus.PENDING, 0);

            entityManager.flush();
            entityManager.clear();

            Page<NotificationResponse> page0 = notificationRepository.findByUserId(
                    primaryUser.getId(),
                    NotificationStatus.PENDING,
                    PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page0.getTotalElements()).isEqualTo(3);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getContent())
                    .extracting(NotificationResponse::id)
                    .containsExactly(notif1.getId(), notif2.getId());

            Page<NotificationResponse> page1 = notificationRepository.findByUserId(
                    primaryUser.getId(),
                    NotificationStatus.PENDING,
                    PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "createdAt"))
            );

            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getContent())
                    .extracting(NotificationResponse::id)
                    .containsExactly(notif3.getId());
        }

        @Test
        @DisplayName("should return empty page when user has no notifications")
        void shouldReturnEmptyPageWhenUserHasNoNotifications() {
            User emptyUser = persistUser("empty@example.com");

            entityManager.flush();
            entityManager.clear();

            Page<NotificationResponse> page = notificationRepository.findByUserId(
                    emptyUser.getId(),
                    null,
                    PageRequest.of(0, 10)
            );

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
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
