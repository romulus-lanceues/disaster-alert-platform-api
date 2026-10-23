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
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.EndpointTestRateLimiter;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpoint;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointRepository;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointResponse;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointService;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
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
        DiscordNotificationSender.class,
        DiscordTestSender.class,
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
        private final MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

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
                    null
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
