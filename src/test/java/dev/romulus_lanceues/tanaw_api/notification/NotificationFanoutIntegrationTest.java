package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingTestConfig.class,
        NotificationDeliveryRepository.class,
        NotificationFanoutService.class,
        NotificationFanoutIntegrationTest.TestFanoutConfig.class
})
@DisplayName("NotificationFanoutService Integration Tests")
class NotificationFanoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private NotificationFanoutService fanoutService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final AtomicLong PSGC_COUNTER = new AtomicLong(1377000000L);
    private final GeoPointFactory geoPointFactory = new GeoPointFactory();

    private User primaryUser;
    private GeographicArea geographicArea;
    private Location primaryLocation;
    private AlertRule primaryRule;
    private static final Instant FIXED_NOW = Instant.parse("2026-10-16T12:00:00Z");

    @TestConfiguration
    static class TestFanoutConfig {

        @Bean
        public Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }

        @Bean
        public ChannelSupportRegistry channelSupportRegistry() {
            ChannelVerifier discordVerifier = new ChannelVerifier() {
                @Override
                public NotificationChannel channel() {
                    return NotificationChannel.DISCORD;
                }

                @Override
                public void verify(String destination) {
                }
            };
            return new ChannelSupportRegistry(List.of(discordVerifier), List.of());
        }

        @Bean
        public NotificationProperties notificationProperties() {
            return new NotificationProperties(
                    true,
                    new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                    new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                    new NotificationProperties.Fanout(Duration.ofHours(6)),
                    new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                    new NotificationProperties.Crypto("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="),
                    null
            );
        }
    }

    @BeforeEach
    void setUp() {
        primaryUser = persistUser("fanout-user-" + UUID.randomUUID() + "@example.com", UserStatus.ACTIVE);
        geographicArea = persistGeographicArea(String.valueOf(PSGC_COUNTER.incrementAndGet()), "City of Manila");
        primaryLocation = persistLocation(primaryUser, "Home", 14.5995, 120.9842);
        primaryRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
    }

    @Test
    @DisplayName("fans out pending active alert into processed alert and creates pending notification")
    void fansOutPendingAlertSuccessfully() {
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "Discord ...1234", true, FIXED_NOW);

        DisasterEvent recentEvent = persistDisasterEvent(
                "USGS",
                "eq-recent-" + UUID.randomUUID(),
                FIXED_NOW.minus(30, ChronoUnit.MINUTES)
        );
        Alert alert = persistAlert(recentEvent, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(20, ChronoUnit.MINUTES));

        FanoutResult result = fanoutService.fanout();

        assertThat(result.alertsClaimed()).isEqualTo(1);
        assertThat(result.alertsCancelledStale()).isZero();
        assertThat(result.alertsProcessed()).isEqualTo(1);
        assertThat(result.notificationsCreated()).isEqualTo(1);

        entityManager.clear();

        Alert processedAlert = entityManager.find(Alert.class, alert.getId());
        assertThat(processedAlert.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getAlert().getId()).isEqualTo(alert.getId());
        assertThat(notification.getEndpoint().getId()).isEqualTo(endpoint.getId());
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.DISCORD);
        assertThat(notification.getDestination()).isEqualTo("Discord ...1234");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("marks stale alert as CANCELLED and creates no notifications")
    void cancelsStaleAlertDuringFanout() {
        persistEndpoint(primaryUser, NotificationChannel.DISCORD, "Discord ...1234", true, FIXED_NOW);

        DisasterEvent staleEvent = persistDisasterEvent(
                "USGS",
                "eq-stale-" + UUID.randomUUID(),
                FIXED_NOW.minus(10, ChronoUnit.HOURS)
        );
        Alert alert = persistAlert(staleEvent, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(1, ChronoUnit.HOURS));

        FanoutResult result = fanoutService.fanout();

        assertThat(result.alertsClaimed()).isEqualTo(1);
        assertThat(result.alertsCancelledStale()).isEqualTo(1);
        assertThat(result.alertsProcessed()).isZero();
        assertThat(result.notificationsCreated()).isZero();

        entityManager.clear();

        Alert cancelledAlert = entityManager.find(Alert.class, alert.getId());
        assertThat(cancelledAlert.getStatus()).isEqualTo(AlertStatus.CANCELLED);
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    private User persistUser(String email, UserStatus status) {
        User user = User.builder()
                .email(email)
                .passwordHash("hashed_password_123")
                .status(status)
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

    private DisasterEvent persistDisasterEvent(String source, String externalId, Instant occurredAt) {
        DisasterEvent event = DisasterEvent.builder()
                .source(source)
                .externalId(externalId)
                .disasterType(DisasterType.EARTHQUAKE)
                .occurredAt(occurredAt)
                .sourceUpdatedAt(occurredAt)
                .status("reviewed")
                .place("Manila")
                .severity("HIGH")
                .build();
        return entityManager.persistAndFlush(event);
    }

    private Alert persistAlert(DisasterEvent disasterEvent, AlertRule alertRule, AlertStatus status, Instant triggeredAt) {
        Alert alert = Alert.builder()
                .disasterEvent(disasterEvent)
                .alertRule(alertRule)
                .status(status)
                .triggeredAt(triggeredAt)
                .build();
        return entityManager.persistAndFlush(alert);
    }

    private NotificationEndpoint persistEndpoint(User user, NotificationChannel channel, String destinationHint, boolean enabled, Instant verifiedAt) {
        NotificationEndpoint endpoint = NotificationEndpoint.builder()
                .user(user)
                .channel(channel)
                .destination("enc:" + destinationHint)
                .destinationHint(destinationHint)
                .enabled(enabled)
                .verifiedAt(verifiedAt)
                .build();
        return entityManager.persistAndFlush(endpoint);
    }
}
