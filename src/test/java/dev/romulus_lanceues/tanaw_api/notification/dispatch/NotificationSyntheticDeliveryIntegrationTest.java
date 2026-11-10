package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.disaster.Earthquake;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.notification.Notification;
import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.NotificationRepository;
import dev.romulus_lanceues.tanaw_api.notification.NotificationStatus;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelSupportRegistry;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.discord.DiscordDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.discord.DiscordNotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.discord.DiscordTestSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.email.EmailDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.email.EmailNotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.email.EmailTestSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.telegram.TelegramDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.telegram.TelegramNotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.telegram.TelegramRateLimiter;
import dev.romulus_lanceues.tanaw_api.notification.channel.telegram.TelegramTestSender;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.EndpointTestRateLimiter;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpoint;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointNotFoundException;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointRepository;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointResponse;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointService;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.RateLimitExceededException;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        NotificationEndpointService.class,
        EndpointTestRateLimiter.class,
        DestinationMasker.class,
        DiscordDestinationValidator.class,
        TelegramDestinationValidator.class,
        EmailDestinationValidator.class,
        DiscordNotificationSender.class,
        DiscordTestSender.class,
        TelegramNotificationSender.class,
        TelegramTestSender.class,
        TelegramRateLimiter.class,
        EmailNotificationSender.class,
        EmailTestSender.class,
        ChannelSupportRegistry.class,
        NotificationSyntheticDeliveryIntegrationTest.TestConfig.class
})
@DisplayName("Synthetic Alert and End-to-End Delivery Integration Tests")
class NotificationSyntheticDeliveryIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-23T08:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @TestConfiguration
    static class TestConfig {

        private final RestClient.Builder restClientBuilder = RestClient.builder();
        private final MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build();

        @Bean
        public Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }

        @Bean
        public NotificationProperties notificationProperties() {
            return new NotificationProperties(
                    false, // dry-run false for live HTTP client dispatch to MockRestServiceServer
                    new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 10, Duration.ofMinutes(2)),
                    new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                    new NotificationProperties.Fanout(Duration.ofHours(6)),
                    new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                    new NotificationProperties.Crypto("dGVzdC1zZWNyZXQta2V5LTMyLWJ5dGVzLWxvbmctISEhIQ=="),
                    new NotificationProperties.Telegram("123456:test-bot-token", "test_bot", null, null)
            );
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public MockRestServiceServer mockRestServiceServer() {
            return mockServer;
        }

        @Bean
        @Qualifier("telegramRestClient")
        public RestClient telegramRestClient() {
            return restClientBuilder.build();
        }

        @Bean
        @Qualifier("discordRestClient")
        public RestClient discordRestClient() {
            return restClientBuilder.build();
        }
    }

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationEndpointService endpointService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEndpointRepository endpointRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MockRestServiceServer mockServer;

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private ChannelSupportRegistry registry;

    @Autowired
    private NotificationFanoutService fanoutService;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();
    private static final AtomicLong PSGC_COUNTER = new AtomicLong(4000000000L);

    private User primaryUser;
    private GeographicArea geographicArea;
    private Location primaryLocation;
    private AlertRule primaryRule;

    @BeforeEach
    void setUp() {
        mockServer.reset();
        primaryUser = persistUser("synthetic-user-" + UUID.randomUUID() + "@example.com", UserStatus.ACTIVE);
        geographicArea = persistGeographicArea(String.valueOf(PSGC_COUNTER.incrementAndGet()), "City of Manila");
        primaryLocation = persistLocation(primaryUser, "Home", 14.5995, 120.9842);
        primaryRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
    }

    @Test
    @DisplayName("dispatcher processes synthetic alert labelled [TEST] through fan-out, claiming, delivery executor, and Discord sender, verifying payload and mention suppression")
    void dispatcherProcessesSyntheticAlertEndToEnd() {
        String rawWebhook = "https://discord.com/api/webhooks/123456789/synthetic-test-token";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-synth", true, FIXED_NOW.minusSeconds(3600));

        // Create synthetic disaster event labelled with [TEST] in place
        DisasterEvent syntheticEvent = persistDisasterEvent(
                "USGS",
                "usgs-synthetic-" + UUID.randomUUID(),
                "[TEST] Synthetic Drill — 10 km SW of Manila",
                FIXED_NOW.minus(10, ChronoUnit.MINUTES)
        );
        persistEarthquake(syntheticEvent, 14.6000, 120.9850, 6.2, 10.0);

        Alert alert = persistAlert(syntheticEvent, primaryRule, AlertStatus.PENDING, FIXED_NOW.minus(5, ChronoUnit.MINUTES));

        // Mock Discord webhook response for synthetic alert delivery
        mockServer.expect(requestTo(rawWebhook + "?wait=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.username").value("Tanaw Alerts"))
                .andExpect(jsonPath("$.embeds[0].title").value("\\[TEST\\] M6.2 earthquake — 0 km from Home"))
                .andExpect(jsonPath("$.embeds[0].fields[?(@.name == 'Location')].value").value("\\[TEST\\] Synthetic Drill — 10 km SW of Manila"))
                .andExpect(jsonPath("$.allowed_mentions.parse").isArray())
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andRespond(withSuccess("{\"id\": \"discord-synthetic-msg-9999\"}", MediaType.APPLICATION_JSON));

        // Execute dispatcher tick: fan-out -> lease reap -> batch claim & dispatch
        DispatchTickResult tickResult = dispatcher.dispatchTick();

        // Verify dispatcher metrics
        assertThat(tickResult.fanoutResult().notificationsCreated()).isEqualTo(1);
        assertThat(tickResult.batchesClaimed()).isEqualTo(1);
        assertThat(tickResult.notificationsDispatched()).isEqualTo(1);

        // Verify HTTP mock server received and satisfied the expected payload
        mockServer.verify();

        entityManager.clear();

        // Verify alert transitioned to PROCESSED
        Alert updatedAlert = entityManager.find(Alert.class, alert.getId());
        assertThat(updatedAlert.getStatus()).isEqualTo(AlertStatus.PROCESSED);

        // Verify notification transitioned to SENT with provider message ID and cleared lock
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification sentNotification = notifications.get(0);
        assertThat(sentNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sentNotification.getProviderMessageId()).isEqualTo("discord-synthetic-msg-9999");
        assertThat(sentNotification.getSentAt()).isNotNull();
        assertThat(sentNotification.getLockedUntil()).isNull();
        assertThat(sentNotification.getAttemptCount()).isEqualTo(1);
        assertThat(sentNotification.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("endpoint test verification message strictly contains [TEST] in title, description, and suppresses mentions")
    void endpointTestVerificationMessageStrictlyContainsTestLabelAndSuppressesMentions() {
        String rawWebhook = "https://discord.com/api/webhooks/987654321/verification-token";
        NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, "hint-verify", true, null);

        // Mock Discord webhook response for endpoint verification test message
        mockServer.expect(requestTo(rawWebhook))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.username").value("Tanaw Alerts"))
                .andExpect(jsonPath("$.embeds[0].title").value("[TEST] Tanaw Alert Notification — Endpoint Verification"))
                .andExpect(jsonPath("$.embeds[0].description").value("This is a test notification confirming that your Discord webhook is active and configured correctly."))
                .andExpect(jsonPath("$.allowed_mentions.parse").isArray())
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andRespond(withNoContent());

        NotificationEndpointResponse response = endpointService.testEndpoint(primaryUser.getId(), endpoint.getId());

        mockServer.verify();

        assertThat(response.verifiedAt()).isEqualTo(FIXED_NOW);

        entityManager.flush();
        entityManager.clear();
        NotificationEndpoint verifiedEndpoint = endpointRepository.findById(endpoint.getId()).orElseThrow();
        assertThat(verifiedEndpoint.getVerifiedAt()).isEqualTo(FIXED_NOW);
        assertThat(verifiedEndpoint.isEnabled()).isTrue();
    }

    @Test
    void registersAllThreeChannelImplementations() {
        assertThat(registry.getSupportedChannels()).containsExactlyInAnyOrder(NotificationChannel.values());
        assertThat(registry.getSupportedSenderChannels()).containsExactlyInAnyOrder(NotificationChannel.values());
        for (NotificationChannel channel : NotificationChannel.values()) {
            assertThat(registry.getSender(channel).channel()).isEqualTo(channel);
            assertThat(registry.getVerifier(channel).channel()).isEqualTo(channel);
        }
    }

    @Test
    void fansOutOneAlertToAllChannelsIdempotently() {
        createThreeVerifiedEndpoints();
        Alert alert = createMultiChannelAlert();

        assertThat(fanoutService.fanout().notificationsCreated()).isEqualTo(3);
        assertThat(fanoutService.fanout().notificationsCreated()).isZero();
        entityManager.clear();
        assertThat(entityManager.find(Alert.class, alert.getId()).getStatus()).isEqualTo(AlertStatus.PROCESSED);
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(3);
        assertThat(notifications).extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.values());
        assertThat(notifications).allSatisfy(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getAttemptCount()).isZero();
            assertThat(notification.getEndpoint().getChannel()).isEqualTo(notification.getChannel());
            assertThat(notification.getDestination()).isEqualTo(notification.getEndpoint().getDestinationHint());
        });
    }

    @Test
    void dispatchesOneAlertThroughAllThreeRealSenders() throws Exception {
        createThreeVerifiedEndpoints();
        Alert alert = createMultiChannelAlert();
        given(mailSender.createMimeMessage()).willAnswer(invocation -> new MimeMessage((Session) null));
        expectDiscordDelivery();
        mockServer.expect(requestTo("https://api.telegram.org/bot123456:test-bot-token/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("123456789"))
                .andExpect(jsonPath("$.text").value(containsString("[TEST]")))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":42}}", MediaType.APPLICATION_JSON));

        DispatchTickResult result = dispatcher.dispatchTick();
        assertThat(result.fanoutResult().notificationsCreated()).isEqualTo(3);
        assertThat(result.notificationsDispatched()).isEqualTo(3);
        mockServer.verify();
        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        then(mailSender).should().send(mail.capture());
        assertThat(mail.getValue().getAllRecipients()[0].toString()).isEqualTo(destination(NotificationChannel.EMAIL));
        assertThat(mail.getValue().getSubject()).contains("[TEST]");
        entityManager.clear();
        assertThat(entityManager.find(Alert.class, alert.getId()).getStatus()).isEqualTo(AlertStatus.PROCESSED);
        assertThat(notificationRepository.findAll()).hasSize(3).allSatisfy(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getAttemptCount()).isEqualTo(1);
            assertThat(notification.getLockedUntil()).isNull();
            assertThat(notification.getSentAt()).isEqualTo(FIXED_NOW);
            assertThat(notification.getProviderMessageId()).isNotBlank();
        });
        assertThat(dispatcher.dispatchTick().notificationsDispatched()).isZero();
    }

    @Test
    void upsertsAndVerifiesAllChannelsUsingRegisteredVerifiers() {
        given(mailSender.createMimeMessage()).willAnswer(invocation -> new MimeMessage((Session) null));
        mockServer.expect(requestTo("https://discord.com/api/webhooks/123456789/multichannel-token"))
                .andExpect(jsonPath("$.embeds[0].title").value(containsString("[TEST]")))
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andRespond(withNoContent());
        mockServer.expect(requestTo("https://api.telegram.org/bot123456:test-bot-token/sendMessage"))
                .andExpect(jsonPath("$.chat_id").value("123456789"))
                .andExpect(jsonPath("$.text").value(containsString("[TEST]")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        for (NotificationChannel channel : NotificationChannel.values()) {
            NotificationEndpointResponse created = endpointService.upsertEndpoint(primaryUser.getId(), channel, destination(channel));
            assertThat(created.verifiedAt()).isNull();
            NotificationEndpointResponse verified = endpointService.testEndpoint(primaryUser.getId(), created.id());
            assertThat(verified.channel()).isEqualTo(channel);
            assertThat(verified.verifiedAt()).isEqualTo(FIXED_NOW);
            entityManager.flush();
            entityManager.clear();
            assertThat(endpointRepository.findById(created.id()).orElseThrow().getVerifiedAt()).isEqualTo(FIXED_NOW);
            NotificationEndpointResponse updated = endpointService.upsertEndpoint(primaryUser.getId(), channel, destination(channel));
            assertThat(updated.id()).isEqualTo(created.id());
            assertThat(updated.verifiedAt()).isNull();
        }
        mockServer.verify();
        then(mailSender).should().send(any(MimeMessage.class));
        assertThat(endpointRepository.findByUserIdOrderByCreatedAtAsc(primaryUser.getId())).hasSize(3);
        NotificationEndpoint endpoint = endpointRepository.findByUserIdAndChannel(primaryUser.getId(), NotificationChannel.EMAIL).orElseThrow();
        assertThatThrownBy(() -> endpointService.testEndpoint(primaryUser.getId(), endpoint.getId()))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void rejectsCrossUserVerificationForEveryChannelBeforeSending() {
        createThreeVerifiedEndpoints();
        UUID otherUser = UUID.randomUUID();
        for (NotificationEndpoint endpoint : endpointRepository.findByUserIdOrderByCreatedAtAsc(primaryUser.getId())) {
            assertThatThrownBy(() -> endpointService.testEndpoint(otherUser, endpoint.getId()))
                    .isInstanceOf(NotificationEndpointNotFoundException.class);
        }
        then(mailSender).shouldHaveNoInteractions();
        mockServer.verify();
    }

    @Test
    void telegramFailureDoesNotPreventDiscordAndEmailDelivery() {
        persistEndpoint(primaryUser, NotificationChannel.DISCORD, destination(NotificationChannel.DISCORD), "masked-discord", true, FIXED_NOW.minusSeconds(60));
        persistEndpoint(primaryUser, NotificationChannel.EMAIL, destination(NotificationChannel.EMAIL), "masked-email", true, FIXED_NOW.minusSeconds(60));
        persistEndpoint(primaryUser, NotificationChannel.TELEGRAM, "987654321", "masked-telegram", true, FIXED_NOW.minusSeconds(60));
        Alert alert = createMultiChannelAlert();
        given(mailSender.createMimeMessage()).willAnswer(invocation -> new MimeMessage((Session) null));
        expectDiscordDelivery();
        mockServer.expect(requestTo("https://api.telegram.org/bot123456:test-bot-token/sendMessage"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":30}}"));
        assertThat(dispatcher.dispatchTick().notificationsDispatched()).isEqualTo(3);
        mockServer.verify();
        entityManager.clear();
        assertThat(entityManager.find(Alert.class, alert.getId()).getStatus()).isEqualTo(AlertStatus.PROCESSED);
        assertThat(notificationRepository.findAll()).hasSize(3).allSatisfy(notification -> {
            if (notification.getChannel() == NotificationChannel.TELEGRAM) {
                assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
                assertThat(notification.getNextAttemptAt()).isAfterOrEqualTo(FIXED_NOW.plusSeconds(30));
                assertThat(notification.getFailureReason()).doesNotContain("test-bot-token");
            } else {
                assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            }
            assertThat(notification.getAttemptCount()).isEqualTo(1);
            assertThat(notification.getLockedUntil()).isNull();
        });
    }

    private String destination(NotificationChannel channel) {
        return switch (channel) {
            case DISCORD -> "https://discord.com/api/webhooks/123456789/multichannel-token";
            case TELEGRAM -> "123456789";
            case EMAIL -> "recipient@example.com";
        };
    }

    private void createThreeVerifiedEndpoints() {
        for (NotificationChannel channel : NotificationChannel.values()) {
            persistEndpoint(primaryUser, channel, destination(channel), "masked-" + channel, true, FIXED_NOW.minusSeconds(60));
        }
    }

    private Alert createMultiChannelAlert() {
        DisasterEvent event = persistDisasterEvent("USGS", "multi-" + UUID.randomUUID(), "[TEST] Multi-channel drill", FIXED_NOW.minusSeconds(60));
        persistEarthquake(event, 14.6000, 120.9850, 6.2, 10.0);
        return persistAlert(event, primaryRule, AlertStatus.PENDING, FIXED_NOW.minusSeconds(30));
    }

    private void expectDiscordDelivery() {
        mockServer.expect(requestTo(destination(NotificationChannel.DISCORD) + "?wait=true"))
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andRespond(withSuccess("{\"id\":\"multi-discord-id\"}", MediaType.APPLICATION_JSON));
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

    private DisasterEvent persistDisasterEvent(String source, String externalId, String place, Instant occurredAt) {
        DisasterEvent event = DisasterEvent.builder()
                .source(source)
                .externalId(externalId)
                .disasterType(DisasterType.EARTHQUAKE)
                .occurredAt(occurredAt)
                .sourceUpdatedAt(occurredAt)
                .status("reviewed")
                .place(place)
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
}
