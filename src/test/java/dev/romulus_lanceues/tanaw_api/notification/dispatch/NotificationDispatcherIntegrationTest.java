package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.Notification;
import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.NotificationRepository;
import dev.romulus_lanceues.tanaw_api.notification.NotificationStatus;
import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelSupportRegistry;
import dev.romulus_lanceues.tanaw_api.notification.channel.DeliveryResult;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpoint;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.disaster.Earthquake;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingTestConfig.class,
        NotificationDeliveryRepository.class,
        NotificationFanoutService.class,
        NotificationRetryPolicy.class,
        AlertMessageFactory.class,
        NotificationDeliveryExecutor.class,
        NotificationDispatcher.class,
        NotificationDispatcherIntegrationTest.TestConfig.class
})
@DisplayName("NotificationDispatcher Integration Tests")
class NotificationDispatcherIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-20T16:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @TestConfiguration
    static class TestConfig {
        @Bean
        public Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }

        @Bean
        public NotificationProperties notificationProperties() {
            return new NotificationProperties(
                    false,
                    new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 10, Duration.ofMinutes(2)),
                    new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                    new NotificationProperties.Fanout(Duration.ofHours(6)),
                    new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                    new NotificationProperties.Crypto("dGVzdC1zZWNyZXQta2V5LTMyLWJ5dGVzLWxvbmctISEhIQ=="),
                    null
            );
        }
    }

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private ChannelSupportRegistry channelSupportRegistry;

    @MockitoBean
    private NotificationSender discordSender;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();
    private static final AtomicLong PSGC_COUNTER = new AtomicLong(3000000000L);

    private User primaryUser;
    private GeographicArea geographicArea;
    private Location primaryLocation;
    private AlertRule primaryRule;

    @BeforeEach
    void setUp() {
        primaryUser = persistUser("dispatcher-user-" + UUID.randomUUID() + "@example.com", UserStatus.ACTIVE);
        geographicArea = persistGeographicArea(String.valueOf(PSGC_COUNTER.incrementAndGet()), "City of Manila");
        primaryLocation = persistLocation(primaryUser, "Home", 14.5995, 120.9842);
        primaryRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);

        given(channelSupportRegistry.getSupportedChannels()).willReturn(Set.of(NotificationChannel.DISCORD));
        given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
        given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
    }

    @Test
    @DisplayName("executes complete lifecycle: fan-out pending alerts, claims notifications, dispatches, and updates database to SENT")
    void executesCompletePipelineEndToEnd() {
        String rawWebhook = "https://discord.com/api/webhooks/999/e2e-token";
        persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-e2e", true, FIXED_NOW.minusSeconds(3600));

        DisasterEvent event = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(10, ChronoUnit.MINUTES));
        persistEarthquake(event, 14.6000, 120.9850, 6.2, 10.0);

        Alert alert = persistAlert(event, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(5, ChronoUnit.MINUTES));

        given(discordSender.send(any(), eq(rawWebhook))).willReturn(new DeliveryResult.Sent("discord-msg-e2e-success"));

        // Execute tick
        DispatchTickResult tickResult = dispatcher.dispatchTick();

        assertThat(tickResult.fanoutResult().notificationsCreated()).isEqualTo(1);
        assertThat(tickResult.batchesClaimed()).isEqualTo(1);
        assertThat(tickResult.notificationsDispatched()).isEqualTo(1);

        entityManager.clear();

        // Verify alert marked PROCESSED by fan-out
        Alert updatedAlert = entityManager.find(Alert.class, alert.getId());
        assertThat(updatedAlert.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        // Verify notification marked SENT by delivery executor
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification sentNotification = notifications.get(0);
        assertThat(sentNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sentNotification.getProviderMessageId()).isEqualTo("discord-msg-e2e-success");
        assertThat(sentNotification.getSentAt()).isNotNull();
        assertThat(sentNotification.getLockedUntil()).isNull();
        assertThat(sentNotification.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("drains multiple notification batches across pipeline until queue is exhausted")
    void drainsMultipleNotificationBatchesEndToEnd() {
        String rawWebhook = "https://discord.com/api/webhooks/999/e2e-multi";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-multi", true, FIXED_NOW.minusSeconds(3600));

        given(discordSender.send(any(), eq(rawWebhook))).willReturn(new DeliveryResult.Sent("msg-batch"));

        // Create 12 pending notifications (batchSize = 10, so 2 batches will be drained)
        for (int i = 0; i < 12; i++) {
            DisasterEvent ev = persistDisasterEvent("USGS", "usgs-multi-" + UUID.randomUUID(), FIXED_NOW.minus(10, ChronoUnit.MINUTES));
            persistEarthquake(ev, 14.6000, 120.9850, 5.5, 10.0);
            Alert alert = persistAlert(ev, primaryRule, AlertStatus.PROCESSED, FIXED_NOW.minus(5, ChronoUnit.MINUTES));
            persistNotification(alert, endpoint, NotificationChannel.DISCORD, "hint-multi");
        }

        DispatchTickResult tickResult = dispatcher.dispatchTick();

        assertThat(tickResult.batchesClaimed()).isEqualTo(2);
        assertThat(tickResult.notificationsDispatched()).isEqualTo(12);

        entityManager.clear();
        List<Notification> allNotifications = notificationRepository.findAll();
        assertThat(allNotifications).hasSize(12);
        assertThat(allNotifications).allMatch(n -> n.getStatus() == NotificationStatus.SENT);
    }

    // Helper persistence methods

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

    private Earthquake persistEarthquake(DisasterEvent event, double latitude, double longitude, Double magnitude, Double depthKm) {
        Earthquake earthquake = Earthquake.builder()
                .event(event)
                .disasterType(DisasterType.EARTHQUAKE)
                .latitude(latitude)
                .longitude(longitude)
                .location(geoPointFactory.create(latitude, longitude))
                .magnitude(magnitude)
                .depthKm(depthKm)
                .build();
        return entityManager.persistAndFlush(earthquake);
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

    private NotificationEndpoint persistEndpoint(User user, NotificationChannel channel, String rawDestination, String destinationHint, boolean enabled, Instant verifiedAt) {
        NotificationEndpoint endpoint = NotificationEndpoint.builder()
                .user(user)
                .channel(channel)
                .destination(rawDestination)
                .destinationHint(destinationHint)
                .enabled(enabled)
                .verifiedAt(verifiedAt)
                .build();
        return entityManager.persistAndFlush(endpoint);
    }

    private Notification persistNotification(Alert alert, NotificationEndpoint endpoint, NotificationChannel channel, String destinationHint) {
        Notification notification = Notification.builder()
                .alert(alert)
                .endpoint(endpoint)
                .channel(channel)
                .destination(destinationHint)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(FIXED_NOW)
                .build();
        return entityManager.persistAndFlush(notification);
    }
}
