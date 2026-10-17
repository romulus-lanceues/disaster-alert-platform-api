package dev.romulus_lanceues.tanaw_api.notification;

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
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingTestConfig.class, NotificationDeliveryRepository.class})
@DisplayName("NotificationDeliveryRepository Integration Tests")
class NotificationDeliveryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();

    private User primaryUser;
    private GeographicArea geographicArea;
    private Location primaryLocation;
    private AlertRule primaryRule;
    private DisasterEvent primaryDisasterEvent;
    private static final java.util.concurrent.atomic.AtomicLong PSGC_COUNTER = new java.util.concurrent.atomic.AtomicLong(1376000000L);

    @BeforeEach
    void setUp() {
        primaryUser = persistUser("user-" + UUID.randomUUID() + "@example.com", UserStatus.ACTIVE);
        geographicArea = persistGeographicArea(String.valueOf(PSGC_COUNTER.incrementAndGet()), "City of Manila");
        primaryLocation = persistLocation(primaryUser, "Home", 14.5995, 120.9842);
        primaryRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
        primaryDisasterEvent = persistDisasterEvent("USGS", "usgs-event-" + UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.HOURS));
    }

    @Nested
    @DisplayName("claimPendingAlerts")
    class ClaimPendingAlerts {

        @Test
        @DisplayName("claims pending alerts ordered by triggered_at ascending up to limit")
        void claimsPendingAlertsOrderedByTriggeredAtAsc() {
            Instant t1 = Instant.now().minus(10, ChronoUnit.MINUTES);
            Instant t2 = Instant.now().minus(5, ChronoUnit.MINUTES);
            Instant t3 = Instant.now().minus(1, ChronoUnit.MINUTES);

            DisasterEvent e1 = persistDisasterEvent("USGS", "event-t1", Instant.now().minus(1, ChronoUnit.HOURS));
            DisasterEvent e2 = persistDisasterEvent("USGS", "event-t2", Instant.now().minus(1, ChronoUnit.HOURS));
            DisasterEvent e3 = persistDisasterEvent("USGS", "event-t3", Instant.now().minus(1, ChronoUnit.HOURS));

            Alert a1 = persistAlert(e2, primaryRule, AlertStatus.PENDING, t2);
            Alert a2 = persistAlert(e1, primaryRule, AlertStatus.PENDING, t1);
            Alert a3 = persistAlert(e3, primaryRule, AlertStatus.PENDING, t3);

            List<ClaimedAlert> claimed = deliveryRepository.claimPendingAlerts(2);

            assertThat(claimed).hasSize(2);
            assertThat(claimed.get(0).id()).isEqualTo(a2.getId());
            assertThat(claimed.get(0).occurredAt()).isCloseTo(e1.getOccurredAt(), org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));
            assertThat(claimed.get(1).id()).isEqualTo(a1.getId());
        }

        @Test
        @DisplayName("ignores non-pending alerts")
        void ignoresNonPendingAlerts() {
            DisasterEvent e1 = persistDisasterEvent("USGS", "event-proc", Instant.now());
            DisasterEvent e2 = persistDisasterEvent("USGS", "event-canc", Instant.now());

            persistAlert(e1, primaryRule, AlertStatus.PROCESSED, Instant.now());
            persistAlert(e2, primaryRule, AlertStatus.CANCELLED, Instant.now());

            List<ClaimedAlert> claimed = deliveryRepository.claimPendingAlerts(10);

            assertThat(claimed).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when limit is zero or negative")
        void returnsEmptyWhenLimitZeroOrNegative() {
            persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING, Instant.now());

            assertThat(deliveryRepository.claimPendingAlerts(0)).isEmpty();
            assertThat(deliveryRepository.claimPendingAlerts(-5)).isEmpty();
        }

        @Test
        @DisplayName("skips locked rows when two transactions claim concurrently")
        void skipsLockedRowsConcurrently() throws InterruptedException, ExecutionException {
            DisasterEvent e1 = persistDisasterEvent("USGS", "event-c1", Instant.now().minus(1, ChronoUnit.HOURS));
            DisasterEvent e2 = persistDisasterEvent("USGS", "event-c2", Instant.now().minus(1, ChronoUnit.HOURS));

            Alert a1 = persistAlert(e1, primaryRule, AlertStatus.PENDING, Instant.now().minus(5, ChronoUnit.MINUTES));
            Alert a2 = persistAlert(e2, primaryRule, AlertStatus.PENDING, Instant.now().minus(1, ChronoUnit.MINUTES));

            // Commit the setup so other database sessions can see the rows
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch t1ClaimedLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(1);

            try {
                Future<List<ClaimedAlert>> t1Future = executor.submit(() -> txTemplate.execute(status -> {
                    List<ClaimedAlert> claims = deliveryRepository.claimPendingAlerts(1);
                    t1ClaimedLatch.countDown();
                    try {
                        finishLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return claims;
                }));

                t1ClaimedLatch.await(5, TimeUnit.SECONDS);

                Future<List<ClaimedAlert>> t2Future = executor.submit(() -> txTemplate.execute(status ->
                        deliveryRepository.claimPendingAlerts(1)
                ));

                List<ClaimedAlert> t2Claims = t2Future.get();
                finishLatch.countDown();
                List<ClaimedAlert> t1Claims = t1Future.get();

                assertThat(t1Claims).hasSize(1);
                assertThat(t2Claims).hasSize(1);
                assertThat(t1Claims.get(0).id()).isEqualTo(a1.getId());
                assertThat(t2Claims.get(0).id()).isEqualTo(a2.getId());
            } finally {
                executor.shutdown();
                // Restart transaction for tearDown/cleanup
                org.springframework.test.context.transaction.TestTransaction.start();
            }
        }
    }

    @Nested
    @DisplayName("cancelAlerts")
    class CancelAlerts {

        @Test
        @DisplayName("marks specified alerts as CANCELLED")
        void marksSpecifiedAlertsAsCancelled() {
            DisasterEvent e1 = persistDisasterEvent("USGS", "event-canc-1", Instant.now());
            DisasterEvent e2 = persistDisasterEvent("USGS", "event-canc-2", Instant.now());

            Alert a1 = persistAlert(e1, primaryRule, AlertStatus.PENDING, Instant.now());
            Alert a2 = persistAlert(e2, primaryRule, AlertStatus.PENDING, Instant.now());

            int updated = deliveryRepository.cancelAlerts(List.of(a1.getId(), a2.getId()));

            assertThat(updated).isEqualTo(2);
            entityManager.clear();
            Alert reloaded1 = entityManager.find(Alert.class, a1.getId());
            Alert reloaded2 = entityManager.find(Alert.class, a2.getId());
            assertThat(reloaded1.getStatus()).isEqualTo(AlertStatus.CANCELLED);
            assertThat(reloaded2.getStatus()).isEqualTo(AlertStatus.CANCELLED);
        }

        @Test
        @DisplayName("returns zero for empty or null list")
        void returnsZeroForEmptyOrNull() {
            assertThat(deliveryRepository.cancelAlerts(List.of())).isZero();
            assertThat(deliveryRepository.cancelAlerts(null)).isZero();
        }
    }

    @Nested
    @DisplayName("markAlertsProcessed")
    class MarkAlertsProcessed {

        @Test
        @DisplayName("marks specified alerts as PROCESSED")
        void marksSpecifiedAlertsAsProcessed() {
            DisasterEvent e1 = persistDisasterEvent("USGS", "event-proc-1", Instant.now());
            DisasterEvent e2 = persistDisasterEvent("USGS", "event-proc-2", Instant.now());

            Alert a1 = persistAlert(e1, primaryRule, AlertStatus.PENDING, Instant.now());
            Alert a2 = persistAlert(e2, primaryRule, AlertStatus.PENDING, Instant.now());

            int updated = deliveryRepository.markAlertsProcessed(List.of(a1.getId(), a2.getId()));

            assertThat(updated).isEqualTo(2);
            entityManager.clear();
            Alert reloaded1 = entityManager.find(Alert.class, a1.getId());
            Alert reloaded2 = entityManager.find(Alert.class, a2.getId());
            assertThat(reloaded1.getStatus()).isEqualTo(AlertStatus.PROCESSED);
            assertThat(reloaded2.getStatus()).isEqualTo(AlertStatus.PROCESSED);
        }

        @Test
        @DisplayName("returns zero for empty or null list")
        void returnsZeroForEmptyOrNull() {
            assertThat(deliveryRepository.markAlertsProcessed(List.of())).isZero();
            assertThat(deliveryRepository.markAlertsProcessed(null)).isZero();
        }
    }

    @Nested
    @DisplayName("createNotificationsForAlerts")
    class CreateNotificationsForAlerts {

        @Test
        @DisplayName("creates one notification per eligible endpoint for the alert owner")
        void createsNotificationPerEligibleEndpoint() {
            NotificationEndpoint discordEp = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "Discord ...1234", true, Instant.now());
            NotificationEndpoint emailEp = persistEndpoint(primaryUser, NotificationChannel.EMAIL, "u***@example.com", true, Instant.now());

            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING, Instant.now());
            Instant now = Instant.parse("2026-10-16T12:00:00Z");

            int inserted = deliveryRepository.createNotificationsForAlerts(
                    List.of(alert.getId()),
                    Set.of(NotificationChannel.DISCORD, NotificationChannel.EMAIL),
                    now
            );

            assertThat(inserted).isEqualTo(2);
            entityManager.clear();

            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(2);

            assertThat(notifications).allSatisfy(n -> {
                assertThat(n.getAlert().getId()).isEqualTo(alert.getId());
                assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
                assertThat(n.getAttemptCount()).isZero();
            });

            Notification discordNotif = notifications.stream()
                    .filter(n -> n.getChannel() == NotificationChannel.DISCORD)
                    .findFirst()
                    .orElseThrow();
            assertThat(discordNotif.getDestination()).isEqualTo("Discord ...1234");
            assertThat(discordNotif.getEndpoint().getId()).isEqualTo(discordEp.getId());

            Notification emailNotif = notifications.stream()
                    .filter(n -> n.getChannel() == NotificationChannel.EMAIL)
                    .findFirst()
                    .orElseThrow();
            assertThat(emailNotif.getDestination()).isEqualTo("u***@example.com");
            assertThat(emailNotif.getEndpoint().getId()).isEqualTo(emailEp.getId());
        }

        @Test
        @DisplayName("skips disabled endpoints, unverified endpoints, and unsupported channels")
        void skipsIneligibleEndpoints() {
            // Disabled endpoint
            persistEndpoint(primaryUser, NotificationChannel.DISCORD, "Discord disabled", false, Instant.now());
            // Unverified endpoint
            persistEndpoint(primaryUser, NotificationChannel.EMAIL, "Email unverified", true, null);
            // Unsupported channel
            persistEndpoint(primaryUser, NotificationChannel.TELEGRAM, "Telegram verified", true, Instant.now());

            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING, Instant.now());

            // Supported channels only includes DISCORD and EMAIL
            int inserted = deliveryRepository.createNotificationsForAlerts(
                    List.of(alert.getId()),
                    Set.of(NotificationChannel.DISCORD, NotificationChannel.EMAIL),
                    Instant.now()
            );

            assertThat(inserted).isZero();
            assertThat(notificationRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("skips endpoints belonging to inactive users")
        void skipsInactiveUsers() {
            User disabledUser = persistUser("disabled@example.com", UserStatus.DISABLED);
            Location disabledLoc = persistLocation(disabledUser, "Disabled Loc", 14.5, 121.0);
            AlertRule disabledRule = persistAlertRule(disabledLoc, DisasterType.EARTHQUAKE, 5.0, 50.0);
            persistEndpoint(disabledUser, NotificationChannel.DISCORD, "Discord hint", true, Instant.now());

            Alert alert = persistAlert(primaryDisasterEvent, disabledRule, AlertStatus.PENDING, Instant.now());

            int inserted = deliveryRepository.createNotificationsForAlerts(
                    List.of(alert.getId()),
                    Set.of(NotificationChannel.DISCORD),
                    Instant.now()
            );

            assertThat(inserted).isZero();
            assertThat(notificationRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("is idempotent and produces zero new notifications on conflict")
        void isIdempotentOnConflict() {
            persistEndpoint(primaryUser, NotificationChannel.DISCORD, "Discord ...1234", true, Instant.now());
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PENDING, Instant.now());
            Instant now = Instant.now();

            int firstRun = deliveryRepository.createNotificationsForAlerts(
                    List.of(alert.getId()),
                    Set.of(NotificationChannel.DISCORD),
                    now
            );
            assertThat(firstRun).isEqualTo(1);

            int secondRun = deliveryRepository.createNotificationsForAlerts(
                    List.of(alert.getId()),
                    Set.of(NotificationChannel.DISCORD),
                    now
            );
            assertThat(secondRun).isZero();

            assertThat(notificationRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("returns zero for empty or null inputs")
        void returnsZeroForEmptyOrNullInputs() {
            Instant now = Instant.now();
            assertThat(deliveryRepository.createNotificationsForAlerts(List.of(), Set.of(NotificationChannel.DISCORD), now)).isZero();
            assertThat(deliveryRepository.createNotificationsForAlerts(null, Set.of(NotificationChannel.DISCORD), now)).isZero();
            assertThat(deliveryRepository.createNotificationsForAlerts(List.of(UUID.randomUUID()), Set.of(), now)).isZero();
            assertThat(deliveryRepository.createNotificationsForAlerts(List.of(UUID.randomUUID()), null, now)).isZero();
        }
    }

    @Nested
    @DisplayName("findDeliveryContexts")
    class FindDeliveryContexts {

        @Test
        @DisplayName("loads complete delivery context including decrypted destination, PostGIS distance in km, and USGS url")
        void loadsCompleteDeliveryContext() throws Exception {
            String rawWebhook = "https://discord.com/api/webhooks/123456789/token-abc-123";
            String hint = "Discord webhook ...c-123";
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, rawWebhook, hint, true, Instant.now());

            String json = """
                    {
                      "properties": {
                        "url": "https://earthquake.usgs.gov/earthquakes/eventpage/us7000test"
                      }
                    }
                    """;
            JsonNode rawPayload = new ObjectMapper().readTree(json);
            DisasterEvent eventWithPayload = DisasterEvent.builder()
                    .source("USGS")
                    .externalId("usgs-detailed-" + UUID.randomUUID())
                    .disasterType(DisasterType.EARTHQUAKE)
                    .occurredAt(Instant.parse("2026-10-17T06:00:00Z"))
                    .sourceUpdatedAt(Instant.parse("2026-10-17T06:05:00Z"))
                    .status("reviewed")
                    .place("10 km SW of Manila")
                    .severity("HIGH")
                    .rawPayload(rawPayload)
                    .build();
            entityManager.persistAndFlush(eventWithPayload);

            // Epicenter at 14.7000, 121.0000 (roughly 11.3 km from Manila home 14.5995, 120.9842)
            persistEarthquake(eventWithPayload, 14.7000, 121.0000, 5.4, 10.5);

            Alert alert = persistAlert(eventWithPayload, primaryRule, AlertStatus.PROCESSED, Instant.now());
            Notification notification = persistNotification(alert, endpoint, NotificationChannel.DISCORD, hint);

            entityManager.clear();

            List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(notification.getId()));

            assertThat(contexts).hasSize(1);
            NotificationDeliveryContext ctx = contexts.get(0);

            assertThat(ctx.notificationId()).isEqualTo(notification.getId());
            assertThat(ctx.alertId()).isEqualTo(alert.getId());
            assertThat(ctx.channel()).isEqualTo(NotificationChannel.DISCORD);
            assertThat(ctx.destination()).isEqualTo(rawWebhook);
            assertThat(ctx.destinationHint()).isEqualTo(hint);
            assertThat(ctx.endpointId()).isEqualTo(endpoint.getId());
            assertThat(ctx.endpointEnabled()).isTrue();
            assertThat(ctx.endpointVerifiedAt()).isNotNull();
            assertThat(ctx.isEndpointPresent()).isTrue();
            assertThat(ctx.isEndpointEnabled()).isTrue();
            assertThat(ctx.isEndpointVerified()).isTrue();
            assertThat(ctx.userId()).isEqualTo(primaryUser.getId());
            assertThat(ctx.userStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(ctx.isUserActive()).isTrue();
            assertThat(ctx.locationName()).isEqualTo("Home");
            assertThat(ctx.place()).isEqualTo("10 km SW of Manila");
            assertThat(ctx.occurredAt()).isEqualTo(Instant.parse("2026-10-17T06:00:00Z"));
            assertThat(ctx.eventStatus()).isEqualTo("reviewed");
            assertThat(ctx.severity()).isEqualTo("HIGH");
            assertThat(ctx.usgsUrl()).isEqualTo("https://earthquake.usgs.gov/earthquakes/eventpage/us7000test");
            assertThat(ctx.magnitude()).isEqualTo(5.4);
            assertThat(ctx.depthKm()).isEqualTo(10.5);
            assertThat(ctx.distanceKm()).isNotNull();
            assertThat(ctx.distanceKm()).isCloseTo(11.3, Offset.offset(0.5));
        }

        @Test
        @DisplayName("handles deleted endpoint gracefully with null endpoint fields")
        void handlesDeletedEndpointGracefully() {
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, Instant.now());
            Notification notification = persistNotification(alert, null, NotificationChannel.DISCORD, "Discord ...snapshot");

            entityManager.clear();

            List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(notification.getId()));

            assertThat(contexts).hasSize(1);
            NotificationDeliveryContext ctx = contexts.get(0);

            assertThat(ctx.notificationId()).isEqualTo(notification.getId());
            assertThat(ctx.endpointId()).isNull();
            assertThat(ctx.destination()).isNull();
            assertThat(ctx.destinationHint()).isEqualTo("Discord ...snapshot");
            assertThat(ctx.endpointEnabled()).isNull();
            assertThat(ctx.endpointVerifiedAt()).isNull();
            assertThat(ctx.isEndpointPresent()).isFalse();
            assertThat(ctx.isEndpointEnabled()).isFalse();
            assertThat(ctx.isEndpointVerified()).isFalse();
            assertThat(ctx.userId()).isEqualTo(primaryUser.getId());
            assertThat(ctx.userStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(ctx.locationName()).isEqualTo("Home");
        }

        @Test
        @DisplayName("reflects inactive user status correctly")
        void reflectsInactiveUserStatus() {
            User disabledUser = persistUser("inactive@example.com", UserStatus.DISABLED);
            Location disabledLoc = persistLocation(disabledUser, "Office", 14.5, 121.0);
            AlertRule disabledRule = persistAlertRule(disabledLoc, DisasterType.EARTHQUAKE, 4.0, 30.0);
            NotificationEndpoint ep = persistEndpoint(disabledUser, NotificationChannel.DISCORD, "https://discord.com/api/webhooks/1/tok", "Discord ...tok", true, Instant.now());

            Alert alert = persistAlert(primaryDisasterEvent, disabledRule, AlertStatus.PROCESSED, Instant.now());
            Notification notification = persistNotification(alert, ep, NotificationChannel.DISCORD, "Discord ...tok");

            entityManager.clear();

            List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(List.of(notification.getId()));

            assertThat(contexts).hasSize(1);
            NotificationDeliveryContext ctx = contexts.get(0);
            assertThat(ctx.userId()).isEqualTo(disabledUser.getId());
            assertThat(ctx.userStatus()).isEqualTo(UserStatus.DISABLED);
            assertThat(ctx.isUserActive()).isFalse();
        }

        @Test
        @DisplayName("returns empty list for null, empty, or non-existent IDs")
        void returnsEmptyListForEmptyOrNull() {
            assertThat(deliveryRepository.findDeliveryContexts(List.of())).isEmpty();
            assertThat(deliveryRepository.findDeliveryContexts(null)).isEmpty();
            assertThat(deliveryRepository.findDeliveryContexts(List.of(UUID.randomUUID()))).isEmpty();
        }
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
        return persistEndpoint(user, channel, "https://discord.com/api/webhooks/default/" + destinationHint, destinationHint, enabled, verifiedAt);
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

    private Notification persistNotification(Alert alert, NotificationEndpoint endpoint, NotificationChannel channel, String destinationHint) {
        Notification notification = Notification.builder()
                .alert(alert)
                .endpoint(endpoint)
                .channel(channel)
                .destination(destinationHint)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .build();
        return entityManager.persistAndFlush(notification);
    }
}
