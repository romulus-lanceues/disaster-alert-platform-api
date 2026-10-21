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
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointRepository;
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
        NotificationRetryPolicy.class,
        AlertMessageFactory.class,
        NotificationDeliveryExecutor.class,
        NotificationDeliveryExecutorIntegrationTest.TestConfig.class
})
@DisplayName("NotificationDeliveryExecutor Integration Tests")
class NotificationDeliveryExecutorIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-20T14:00:00Z");

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
                    new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                    new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                    new NotificationProperties.Fanout(Duration.ofHours(6)),
                    new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                    new NotificationProperties.Crypto("dGVzdC1zZWNyZXQta2V5LTMyLWJ5dGVzLWxvbmctISEhIQ=="),
                    null
            );
        }
    }

    @Autowired
    private NotificationDeliveryExecutor executor;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEndpointRepository endpointRepository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private ChannelSupportRegistry channelSupportRegistry;

    @MockitoBean
    private NotificationSender discordSender;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();
    private static final AtomicLong PSGC_COUNTER = new AtomicLong(2000000000L);

    private User primaryUser;
    private GeographicArea geographicArea;
    private Location primaryLocation;
    private AlertRule primaryRule;
    private DisasterEvent primaryDisasterEvent;

    @BeforeEach
    void setUp() {
        primaryUser = persistUser("user-" + UUID.randomUUID() + "@example.com", UserStatus.ACTIVE);
        geographicArea = persistGeographicArea(String.valueOf(PSGC_COUNTER.incrementAndGet()), "City of Manila");
        primaryLocation = persistLocation(primaryUser, "Home", 14.5995, 120.9842);
        primaryRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
        primaryDisasterEvent = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), FIXED_NOW.minus(1, ChronoUnit.HOURS));

        given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
        given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
    }

    @Test
    @DisplayName("executes successful delivery, updates status to SENT with provider message ID and clears lock")
    void executesSuccessfulDelivery() {
        String rawWebhook = "https://discord.com/api/webhooks/123/valid-token";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "Discord ...token", true, FIXED_NOW.minusSeconds(3600));
        Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, FIXED_NOW);
        Notification notification = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "Discord ...token");

        // 1. Claim
        List<ClaimedNotification> claimed = deliveryRepository.claimPendingNotifications(1, FIXED_NOW, Duration.ofMinutes(2));
        assertThat(claimed).hasSize(1);
        ClaimedNotification claim = claimed.get(0);

        // 2. Load context
        List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(claim.id()));
        assertThat(contexts).hasSize(1);

        // 3. Sender mock
        given(discordSender.send(any(), eq(rawWebhook))).willReturn(new DeliveryResult.Sent("discord-msg-888"));

        // 4. Execute
        DeliveryExecutionOutcome outcome = executor.executeDelivery(claim, contexts.get(0));
        assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.SENT);

        // 5. Verify database state
        entityManager.clear();
        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(updated.getProviderMessageId()).isEqualTo("discord-msg-888");
        assertThat(updated.getSentAt()).isNotNull();
        assertThat(updated.getLockedUntil()).isNull();
        assertThat(updated.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancels notification when pre-send guard detects disabled endpoint")
    void cancelsWhenPreSendGuardFails() {
        String rawWebhook = "https://discord.com/api/webhooks/123/valid-token";
        // Disabled endpoint
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "Discord ...token", false, FIXED_NOW.minusSeconds(3600));
        Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, FIXED_NOW);
        Notification notification = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "Discord ...token");

        List<ClaimedNotification> claimed = deliveryRepository.claimPendingNotifications(1, FIXED_NOW, Duration.ofMinutes(2));
        assertThat(claimed).hasSize(1);
        ClaimedNotification claim = claimed.get(0);

        List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(claim.id()));
        assertThat(contexts).hasSize(1);

        DeliveryExecutionOutcome outcome = executor.executeDelivery(claim, contexts.get(0));
        assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.CANCELLED);

        entityManager.clear();
        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        assertThat(updated.getFailureReason()).isEqualTo("Endpoint is disabled");
        assertThat(updated.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("records retryable failure and schedules next attempt when sender returns Retryable")
    void recordsRetryableFailureAndSchedulesNextAttempt() {
        String rawWebhook = "https://discord.com/api/webhooks/123/valid-token";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "Discord ...token", true, FIXED_NOW.minusSeconds(3600));
        Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, FIXED_NOW);
        Notification notification = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "Discord ...token");

        List<ClaimedNotification> claimed = deliveryRepository.claimPendingNotifications(1, FIXED_NOW, Duration.ofMinutes(2));
        ClaimedNotification claim = claimed.get(0);

        List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(claim.id()));

        // Return retryable with provider retry_after of 45 seconds
        given(discordSender.send(any(), eq(rawWebhook)))
                .willReturn(new DeliveryResult.Retryable("HTTP 429 Rate limited", Duration.ofSeconds(45)));

        DeliveryExecutionOutcome outcome = executor.executeDelivery(claim, contexts.get(0));
        assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.RETRYABLE);

        entityManager.clear();
        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(updated.getFailureReason()).isEqualTo("HTTP 429 Rate limited");
        assertThat(updated.getNextAttemptAt()).isEqualTo(FIXED_NOW.plusSeconds(45));
        assertThat(updated.getLockedUntil()).isNull();
        assertThat(updated.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("records permanent failure and invalidates endpoint when sender returns Permanent with invalidateEndpoint = true")
    void recordsPermanentFailureAndInvalidatesEndpoint() {
        String rawWebhook = "https://discord.com/api/webhooks/123/valid-token";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-1", true, FIXED_NOW.minusSeconds(3600));
        Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, FIXED_NOW);
        Notification notification = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "hint-1", FIXED_NOW.minusSeconds(10));

        // Another pending notification for same endpoint (different alert)
        DisasterEvent event2 = persistDisasterEvent("USGS", "usgs-event-2", FIXED_NOW.minus(1, ChronoUnit.HOURS));
        Alert alert2 = persistAlert(event2, primaryRule, AlertStatus.PROCESSED, FIXED_NOW);
        Notification pendingOther = persistNotification(alert2, endpoint, NotificationChannel.DISCORD, "hint-1", FIXED_NOW);

        List<ClaimedNotification> claimed = deliveryRepository.claimPendingNotifications(1, FIXED_NOW, Duration.ofMinutes(2));
        ClaimedNotification claim = claimed.get(0);

        List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(claim.id()));

        given(discordSender.send(any(), eq(rawWebhook)))
                .willReturn(new DeliveryResult.Permanent("HTTP 404 Webhook Not Found", true));

        DeliveryExecutionOutcome outcome = executor.executeDelivery(claim, contexts.get(0));
        assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.PERMANENT_FAILURE);

        entityManager.clear();
        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(updated.getFailureReason()).isEqualTo("HTTP 404 Webhook Not Found");

        // Verify endpoint disabled
        NotificationEndpoint updatedEndpoint = endpointRepository.findById(endpoint.getId()).orElseThrow();
        assertThat(updatedEndpoint.isEnabled()).isFalse();
        assertThat(updatedEndpoint.getDisabledReason()).isEqualTo("HTTP 404 Webhook Not Found");

        // Verify other pending notification cancelled
        Notification updatedOther = notificationRepository.findById(pendingOther.getId()).orElseThrow();
        assertThat(updatedOther.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test
    @DisplayName("rejects stale worker outcome update when fencing token does not match")
    void rejectsStaleWorkerWhenFencingTokenDoesNotMatch() {
        String rawWebhook = "https://discord.com/api/webhooks/123/valid-token";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-1", true, FIXED_NOW.minusSeconds(3600));
        Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, FIXED_NOW);
        Notification notification = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "hint-1");

        List<ClaimedNotification> claimed = deliveryRepository.claimPendingNotifications(1, FIXED_NOW, Duration.ofMinutes(2));
        ClaimedNotification claim = claimed.get(0);

        List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(claim.id()));

        // Simulate stale worker attemptCount (e.g. attemptCount 99 instead of 1)
        given(discordSender.send(any(), eq(rawWebhook))).willReturn(new DeliveryResult.Sent("msg-stale"));

        DeliveryExecutionOutcome outcome = executor.executeDelivery(contexts.get(0), 99);
        assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.FENCE_CONFLICT);

        // Verify database row still in PROCESSING from attempt 1
        entityManager.clear();
        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
    }

    // Helper persistence methods matching NotificationDeliveryRepositoryTest conventions

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
