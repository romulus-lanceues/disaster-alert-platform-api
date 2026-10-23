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

    @Test
    @DisplayName("isolates transient provider failure: notification scheduled for retry while alert remains PROCESSED and subsequent ingestion is unblocked")
    void isolatesTransientProviderFailureWithoutAffectingAlertOrIngestion() {
        String rawWebhook = "https://discord.com/api/webhooks/999/retryable-webhook";
        persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-retry", true, FIXED_NOW.minusSeconds(3600));

        DisasterEvent event1 = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(10, ChronoUnit.MINUTES));
        persistEarthquake(event1, 14.6000, 120.9850, 6.0, 10.0);
        Alert alert1 = persistAlert(event1, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(5, ChronoUnit.MINUTES));

        // Provider returns retryable 429
        given(discordSender.send(any(), eq(rawWebhook)))
                .willReturn(new DeliveryResult.Retryable("HTTP 429 RATE_LIMITED", Duration.ofSeconds(30)));

        DispatchTickResult tickResult1 = dispatcher.dispatchTick();

        assertThat(tickResult1.fanoutResult().notificationsCreated()).isEqualTo(1);
        assertThat(tickResult1.notificationsDispatched()).isEqualTo(1);

        entityManager.clear();

        // 1. Verify alert remains in PROCESSED status
        Alert reloadedAlert1 = entityManager.find(Alert.class, alert1.getId());
        assertThat(reloadedAlert1.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        // 2. Verify notification is in PENDING with retry delay
        List<Notification> notifications1 = notificationRepository.findAll();
        assertThat(notifications1).hasSize(1);
        Notification notif1 = notifications1.get(0);
        assertThat(notif1.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notif1.getAttemptCount()).isEqualTo(1);
        assertThat(notif1.getFailureReason()).isEqualTo("HTTP 429 RATE_LIMITED");
        assertThat(notif1.getNextAttemptAt()).isEqualTo(FIXED_NOW.plusSeconds(30));
        assertThat(notif1.getLockedUntil()).isNull();

        // 3. Verify ingestion and subsequent dispatcher ticks are completely unblocked
        DisasterEvent event2 = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(2, ChronoUnit.MINUTES));
        persistEarthquake(event2, 14.6000, 120.9850, 5.8, 12.0);
        Alert alert2 = persistAlert(event2, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(1, ChronoUnit.MINUTES));

        // Now provider succeeds for new alerts
        given(discordSender.send(any(), eq(rawWebhook)))
                .willReturn(new DeliveryResult.Sent("discord-msg-event2"));

        DispatchTickResult tickResult2 = dispatcher.dispatchTick();

        assertThat(tickResult2.fanoutResult().notificationsCreated()).isEqualTo(1);
        // Note: notif1 is scheduled for +30s in the future so only notif2 is claimed and dispatched
        assertThat(tickResult2.notificationsDispatched()).isEqualTo(1);

        entityManager.clear();
        Alert reloadedAlert2 = entityManager.find(Alert.class, alert2.getId());
        assertThat(reloadedAlert2.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        Alert recheckedAlert1 = entityManager.find(Alert.class, alert1.getId());
        assertThat(recheckedAlert1.getStatus()).isEqualTo(AlertStatus.PROCESSED);
    }

    @Test
    @DisplayName("isolates permanent provider failure: marks notification FAILED, disables endpoint, cancels pending, and preserves alert PROCESSED")
    void isolatesPermanentProviderFailureAndInvalidatesEndpointWithoutAffectingAlert() {
        String rawWebhook = "https://discord.com/api/webhooks/999/deleted-webhook";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-deleted", true, FIXED_NOW.minusSeconds(3600));

        // Older alert with another pending notification scheduled for future retry on this endpoint
        DisasterEvent olderEvent = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(30, ChronoUnit.MINUTES));
        persistEarthquake(olderEvent, 14.6000, 120.9850, 5.0, 10.0);
        Alert olderAlert = persistAlert(olderEvent, primaryRule, AlertStatus.PROCESSED, FIXED_NOW.minus(20, ChronoUnit.MINUTES));
        Notification olderPendingNotif = persistNotification(
                olderAlert, endpoint, NotificationChannel.DISCORD, "hint-deleted", FIXED_NOW.plus(10, ChronoUnit.MINUTES)
        );

        // Fresh alert
        DisasterEvent event = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(10, ChronoUnit.MINUTES));
        persistEarthquake(event, 14.6000, 120.9850, 6.5, 8.0);
        Alert alert = persistAlert(event, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(5, ChronoUnit.MINUTES));

        // Provider returns permanent failure with endpoint invalidation
        given(discordSender.send(any(), eq(rawWebhook)))
                .willReturn(new DeliveryResult.Permanent("HTTP 404 WEBHOOK_NOT_FOUND", true));

        DispatchTickResult tickResult = dispatcher.dispatchTick();

        assertThat(tickResult.fanoutResult().notificationsCreated()).isEqualTo(1);

        entityManager.clear();

        // 1. Parent alerts remain PROCESSED
        Alert reloadedAlert = entityManager.find(Alert.class, alert.getId());
        assertThat(reloadedAlert.getStatus()).isEqualTo(AlertStatus.PROCESSED);
        Alert reloadedOlderAlert = entityManager.find(Alert.class, olderAlert.getId());
        assertThat(reloadedOlderAlert.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        // 2. Notification receiving the 404 is FAILED
        List<Notification> allNotifications = notificationRepository.findAll();
        assertThat(allNotifications).hasSize(2);

        Notification failedNotif = allNotifications.stream()
                .filter(n -> n.getAlert().getId().equals(alert.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(failedNotif.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failedNotif.getFailureReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");
        assertThat(failedNotif.getLockedUntil()).isNull();

        // 3. Endpoint is disabled with reason
        NotificationEndpoint reloadedEndpoint = entityManager.find(NotificationEndpoint.class, endpoint.getId());
        assertThat(reloadedEndpoint.isEnabled()).isFalse();
        assertThat(reloadedEndpoint.getDisabledReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");

        // 4. Other pending notification for that endpoint was cancelled
        Notification cancelledNotif = allNotifications.stream()
                .filter(n -> n.getId().equals(olderPendingNotif.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(cancelledNotif.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        assertThat(cancelledNotif.getFailureReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");
    }

    @Test
    @DisplayName("isolates partial provider failure across multiple endpoints: healthy endpoint succeeds while failing endpoint fails, alert remains PROCESSED")
    void isolatesPartialProviderFailureAcrossMultipleEndpointsForSameAlert() {
        String webhookSuccess = "https://discord.com/api/webhooks/111/healthy";
        String webhookFailure = "https://discord.com/api/webhooks/222/broken";

        User secondaryUser = persistUser("dispatcher-user-2-" + UUID.randomUUID() + "@example.com", UserStatus.ACTIVE);
        Location secondaryLoc = persistLocation(secondaryUser, "Work", 14.5995, 120.9842);
        AlertRule secondaryRule = persistAlertRule(secondaryLoc, DisasterType.EARTHQUAKE, 5.0, 50.0);

        NotificationEndpoint healthyEndpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, webhookSuccess, "hint-healthy", true, FIXED_NOW.minusSeconds(3600));
        NotificationEndpoint brokenEndpoint = persistEndpoint(secondaryUser, NotificationChannel.DISCORD, webhookFailure, "hint-broken", true, FIXED_NOW.minusSeconds(3600));

        DisasterEvent event = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(10, ChronoUnit.MINUTES));
        persistEarthquake(event, 14.6000, 120.9850, 6.1, 15.0);
        Alert alert1 = persistAlert(event, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(5, ChronoUnit.MINUTES));
        Alert alert2 = persistAlert(event, secondaryRule, AlertStatus.PENDING, FIXED_NOW.minus(5, ChronoUnit.MINUTES));

        // Mock senders differently per webhook
        given(discordSender.send(any(), eq(webhookSuccess))).willReturn(new DeliveryResult.Sent("msg-healthy-ok"));
        given(discordSender.send(any(), eq(webhookFailure))).willReturn(new DeliveryResult.Permanent("HTTP 404 WEBHOOK_NOT_FOUND", true));

        DispatchTickResult tickResult = dispatcher.dispatchTick();

        assertThat(tickResult.fanoutResult().notificationsCreated()).isEqualTo(2);
        assertThat(tickResult.notificationsDispatched()).isEqualTo(2);

        entityManager.clear();

        // 1. Both alerts are safely PROCESSED
        Alert reloadedAlert1 = entityManager.find(Alert.class, alert1.getId());
        assertThat(reloadedAlert1.getStatus()).isEqualTo(AlertStatus.PROCESSED);
        Alert reloadedAlert2 = entityManager.find(Alert.class, alert2.getId());
        assertThat(reloadedAlert2.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        // 2. Healthy endpoint delivered and remains enabled
        NotificationEndpoint reloadedHealthy = entityManager.find(NotificationEndpoint.class, healthyEndpoint.getId());
        assertThat(reloadedHealthy.isEnabled()).isTrue();

        // 3. Broken endpoint disabled
        NotificationEndpoint reloadedBroken = entityManager.find(NotificationEndpoint.class, brokenEndpoint.getId());
        assertThat(reloadedBroken.isEnabled()).isFalse();
        assertThat(reloadedBroken.getDisabledReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");

        // 4. Notifications reflect individual delivery outcomes
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(2);

        Notification healthyNotification = notifications.stream()
                .filter(n -> n.getEndpoint().getId().equals(healthyEndpoint.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(healthyNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(healthyNotification.getProviderMessageId()).isEqualTo("msg-healthy-ok");

        Notification brokenNotification = notifications.stream()
                .filter(n -> n.getEndpoint().getId().equals(brokenEndpoint.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(brokenNotification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(brokenNotification.getFailureReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");
    }

    @Test
    @DisplayName("isolates unexpected sender runtime exception: catches error, sanitizes reason, and preserves alert PROCESSED")
    void isolatesUnexpectedSenderExceptionGracefullyWithoutAffectingAlert() {
        String rawWebhook = "https://discord.com/api/webhooks/999/crashing-webhook";
        persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-crash", true, FIXED_NOW.minusSeconds(3600));

        DisasterEvent event = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(10, ChronoUnit.MINUTES));
        persistEarthquake(event, 14.6000, 120.9850, 5.9, 10.0);
        Alert alert = persistAlert(event, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(5, ChronoUnit.MINUTES));

        // Sender throws uncaught RuntimeException
        given(discordSender.send(any(), eq(rawWebhook))).willThrow(new RuntimeException("Simulated socket connection crash"));

        // Dispatch tick must complete cleanly without throwing
        DispatchTickResult tickResult = dispatcher.dispatchTick();

        assertThat(tickResult.fanoutResult().notificationsCreated()).isEqualTo(1);
        assertThat(tickResult.notificationsDispatched()).isEqualTo(1);

        entityManager.clear();

        // 1. Alert remains PROCESSED
        Alert reloadedAlert = entityManager.find(Alert.class, alert.getId());
        assertThat(reloadedAlert.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        // 2. Notification is sanitized and scheduled for retry
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification notif = notifications.get(0);
        assertThat(notif.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notif.getAttemptCount()).isEqualTo(1);
        assertThat(notif.getFailureReason()).isEqualTo("UNEXPECTED_ERROR: RuntimeException");
        assertThat(notif.getFailureReason()).doesNotContain("crashing-webhook");
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
        return persistNotification(alert, endpoint, channel, destinationHint, FIXED_NOW);
    }

    private Notification persistNotification(Alert alert, NotificationEndpoint endpoint, NotificationChannel channel, String destinationHint, Instant nextAttemptAt) {
        Notification notification = Notification.builder()
                .alert(alert)
                .endpoint(endpoint)
                .channel(channel)
                .destination(destinationHint)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(nextAttemptAt)
                .build();
        return entityManager.persistAndFlush(notification);
    }
}
