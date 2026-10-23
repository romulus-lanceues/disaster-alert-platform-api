package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.Notification;
import dev.romulus_lanceues.tanaw_api.notification.NotificationRepository;
import dev.romulus_lanceues.tanaw_api.notification.NotificationStatus;
import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
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

    @Nested
    @DisplayName("claimPendingNotifications")
    class ClaimPendingNotifications {

        @Test
        @DisplayName("claims pending notifications ordered by next_attempt_at ascending and sets status to PROCESSING with lease")
        void claimsPendingNotificationsOrderedByNextAttemptAtAsc() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Instant t1 = now.minus(10, ChronoUnit.MINUTES);
            Instant t2 = now.minus(5, ChronoUnit.MINUTES);
            Instant tFuture = now.plus(5, ChronoUnit.MINUTES);

            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, t1);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n1 = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PENDING, 0, t2, null);
            Notification n2 = persistNotification(alert, endpoint, NotificationChannel.EMAIL, "hint-2", NotificationStatus.PENDING, 0, t1, null);
            Notification nFuture = persistNotification(alert, endpoint, NotificationChannel.TELEGRAM, "hint-3", NotificationStatus.PENDING, 0, tFuture, null);

            List<ClaimedNotification> claimed = new TransactionTemplate(transactionManager).execute(status ->
                    deliveryRepository.claimPendingNotifications(2, now, java.time.Duration.ofMinutes(2))
            );

            assertThat(claimed).hasSize(2);
            assertThat(claimed.get(0).id()).isEqualTo(n2.getId());
            assertThat(claimed.get(0).attemptCount()).isEqualTo(1);
            assertThat(claimed.get(1).id()).isEqualTo(n1.getId());
            assertThat(claimed.get(1).attemptCount()).isEqualTo(1);

            entityManager.clear();
            Notification reloadedN2 = entityManager.find(Notification.class, n2.getId());
            assertThat(reloadedN2.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
            assertThat(reloadedN2.getAttemptCount()).isEqualTo(1);
            assertThat(reloadedN2.getLockedUntil()).isAfterOrEqualTo(now.plus(119, ChronoUnit.SECONDS));

            Notification reloadedFuture = entityManager.find(Notification.class, nFuture.getId());
            assertThat(reloadedFuture.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloadedFuture.getAttemptCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("concurrent claimers never claim the same notifications due to SKIP LOCKED")
        void concurrentClaimsNeverClaimSameNotifications() throws Exception {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n1 = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PENDING, 0, now.minus(5, ChronoUnit.MINUTES), null);
            Notification n2 = persistNotification(alert, endpoint, NotificationChannel.EMAIL, "hint-2", NotificationStatus.PENDING, 0, now.minus(4, ChronoUnit.MINUTES), null);

            // Commit the setup so other database sessions can see the rows
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch t1ClaimedLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(1);

            try {
                Future<List<ClaimedNotification>> t1Future = executor.submit(() -> txTemplate.execute(status -> {
                    List<ClaimedNotification> claims = deliveryRepository.claimPendingNotifications(1, now, java.time.Duration.ofMinutes(2));
                    t1ClaimedLatch.countDown();
                    try {
                        finishLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return claims;
                }));

                t1ClaimedLatch.await(5, TimeUnit.SECONDS);

                Future<List<ClaimedNotification>> t2Future = executor.submit(() -> txTemplate.execute(status ->
                        deliveryRepository.claimPendingNotifications(1, now, java.time.Duration.ofMinutes(2))
                ));

                List<ClaimedNotification> t2Claims = t2Future.get();
                finishLatch.countDown();
                List<ClaimedNotification> t1Claims = t1Future.get();

                assertThat(t1Claims).hasSize(1);
                assertThat(t2Claims).hasSize(1);
                assertThat(t1Claims.get(0).id()).isNotEqualTo(t2Claims.get(0).id());
                assertThat(List.of(t1Claims.get(0).id(), t2Claims.get(0).id())).containsExactlyInAnyOrder(n1.getId(), n2.getId());
            } finally {
                executor.shutdown();
                org.springframework.test.context.transaction.TestTransaction.start();
                notificationRepository.deleteAll();
                org.springframework.test.context.transaction.TestTransaction.flagForCommit();
                org.springframework.test.context.transaction.TestTransaction.end();
                org.springframework.test.context.transaction.TestTransaction.start();
            }
        }

        @Test
        @DisplayName("returns empty list when limit <= 0 or no ready notifications")
        void returnsEmptyListWhenNoMatches() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            assertThat(deliveryRepository.claimPendingNotifications(0, now, java.time.Duration.ofMinutes(2))).isEmpty();
            assertThat(deliveryRepository.claimPendingNotifications(-1, now, java.time.Duration.ofMinutes(2))).isEmpty();
            assertThat(deliveryRepository.claimPendingNotifications(10, now, java.time.Duration.ofMinutes(2))).isEmpty();
        }
    }

    @Nested
    @DisplayName("reapExpiredLeases")
    class ReapExpiredLeases {

        @Test
        @DisplayName("reaps expired PROCESSING notifications back to PENDING when attempt_count < maxAttempts")
        void reapsExpiredProcessingNotificationsBelowMaxAttempts() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification expired = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now.minus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES)
            );

            int reaped = deliveryRepository.reapExpiredLeases(now, 3);
            assertThat(reaped).isEqualTo(1);

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, expired.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloaded.getLockedUntil()).isNull();
            assertThat(reloaded.getNextAttemptAt()).isEqualTo(now);
            assertThat(reloaded.getFailureReason()).isEqualTo("Lease expired; returned to queue");
        }

        @Test
        @DisplayName("marks expired PROCESSING notifications as FAILED when attempt_count >= maxAttempts")
        void marksExpiredProcessingNotificationsAtOrAboveMaxAttemptsAsFailed() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification expiredExhausted = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 3, now.minus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES)
            );

            int reaped = deliveryRepository.reapExpiredLeases(now, 3);
            assertThat(reaped).isEqualTo(1);

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, expiredExhausted.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(reloaded.getLockedUntil()).isNull();
            assertThat(reloaded.getFailureReason()).isEqualTo("Lease expired; max attempts exceeded");
        }

        @Test
        @DisplayName("ignores unexpired PROCESSING notifications and notifications in other statuses")
        void ignoresUnexpiredNotifications() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification unexpired = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now.minus(5, ChronoUnit.MINUTES), now.plus(2, ChronoUnit.MINUTES)
            );
            persistNotification(
                    alert, endpoint, NotificationChannel.EMAIL, "hint-2",
                    NotificationStatus.PENDING, 0, now.minus(5, ChronoUnit.MINUTES), now.minus(2, ChronoUnit.MINUTES)
            );

            int reaped = deliveryRepository.reapExpiredLeases(now, 3);
            assertThat(reaped).isZero();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, unexpired.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
            assertThat(reloaded.getLockedUntil()).isNotNull();
        }
    }

    @Nested
    @DisplayName("fencedOutcomeRecording")
    class FencedOutcomeRecording {

        @Test
        @DisplayName("recordSuccess updates status to SENT and sets providerMessageId when fencing token matches")
        void recordSuccessWhenFencingTokenMatches() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Instant sentAt = now.minus(1, ChronoUnit.SECONDS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 2, now, now.plus(2, ChronoUnit.MINUTES)
            );

            boolean updated = deliveryRepository.recordSuccess(n.getId(), 2, "msg-discord-999", sentAt, now);
            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(reloaded.getProviderMessageId()).isEqualTo("msg-discord-999");
            assertThat(reloaded.getSentAt()).isEqualTo(sentAt);
            assertThat(reloaded.getFailureReason()).isNull();
            assertThat(reloaded.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("recordSuccess returns false and does not mutate when attempt_count does not match fencing token")
        void recordSuccessFailsWhenFencingTokenMismatches() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 3, now, now.plus(2, ChronoUnit.MINUTES)
            );

            boolean updated = deliveryRepository.recordSuccess(n.getId(), 2, "msg-discord-999", now, now);
            assertThat(updated).isFalse();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
            assertThat(reloaded.getAttemptCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("recordPreSendCancellation transitions PROCESSING to CANCELLED with reason")
        void recordPreSendCancellation() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now, now.plus(2, ChronoUnit.MINUTES)
            );

            boolean updated = deliveryRepository.recordPreSendCancellation(n.getId(), 1, "ENDPOINT_DISABLED", now);
            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
            assertThat(reloaded.getFailureReason()).isEqualTo("ENDPOINT_DISABLED");
            assertThat(reloaded.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("recordRetryableFailure requeues to PENDING when claimedAttempt < maxAttempts")
        void recordRetryableFailureRequeues() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Instant nextAttemptAt = now.plus(30, ChronoUnit.SECONDS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now, now.plus(2, ChronoUnit.MINUTES)
            );

            boolean updated = deliveryRepository.recordRetryableFailure(n.getId(), 1, "HTTP 429", nextAttemptAt, 3, now);
            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloaded.getNextAttemptAt()).isEqualTo(nextAttemptAt);
            assertThat(reloaded.getFailureReason()).isEqualTo("HTTP 429");
            assertThat(reloaded.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("recordRetryableFailure marks as FAILED when claimedAttempt >= maxAttempts")
        void recordRetryableFailureExhausts() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Instant nextAttemptAt = now.plus(30, ChronoUnit.SECONDS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 3, now, now.plus(2, ChronoUnit.MINUTES)
            );

            boolean updated = deliveryRepository.recordRetryableFailure(n.getId(), 3, "HTTP 500", nextAttemptAt, 3, now);
            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(reloaded.getFailureReason()).isEqualTo("HTTP 500");
            assertThat(reloaded.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("recordPermanentFailure marks notification as FAILED")
        void recordPermanentFailureMarksAsFailed() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now, now.plus(2, ChronoUnit.MINUTES)
            );

            boolean updated = deliveryRepository.recordPermanentFailure(n.getId(), 1, "HTTP 404 WEBHOOK_NOT_FOUND", now);
            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(reloaded.getFailureReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");
            assertThat(reloaded.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("recordSuccess rejects stale worker when lease was expired and reaped")
        void recordSuccessRejectsStaleWorkerAfterReap() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now.minus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES)
            );

            // Reaper runs and moves the expired row back to PENDING
            int reaped = deliveryRepository.reapExpiredLeases(now, 3);
            assertThat(reaped).isEqualTo(1);

            // Stale worker finishes its send and tries to record success with claimedAttempt = 1
            boolean updated = deliveryRepository.recordSuccess(n.getId(), 1, "msg-late-123", now, now);
            assertThat(updated).isFalse();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloaded.getProviderMessageId()).isNull();
            assertThat(reloaded.getFailureReason()).isEqualTo("Lease expired; returned to queue");
        }

        @Test
        @DisplayName("recordRetryableFailure rejects stale worker when lease was expired and reaped")
        void recordRetryableFailureRejectsStaleWorkerAfterReap() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now.minus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES)
            );

            int reaped = deliveryRepository.reapExpiredLeases(now, 3);
            assertThat(reaped).isEqualTo(1);

            boolean updated = deliveryRepository.recordRetryableFailure(n.getId(), 1, "HTTP 500", now.plusSeconds(30), 3, now);
            assertThat(updated).isFalse();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloaded.getFailureReason()).isEqualTo("Lease expired; returned to queue");
        }

        @Test
        @DisplayName("recordPermanentFailure rejects stale worker when lease was expired and reaped")
        void recordPermanentFailureRejectsStaleWorkerAfterReap() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification n = persistNotification(
                    alert, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now.minus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES)
            );

            int reaped = deliveryRepository.reapExpiredLeases(now, 3);
            assertThat(reaped).isEqualTo(1);

            boolean updated = deliveryRepository.recordPermanentFailure(n.getId(), 1, "HTTP 404 WEBHOOK_NOT_FOUND", now);
            assertThat(updated).isFalse();

            entityManager.clear();
            Notification reloaded = entityManager.find(Notification.class, n.getId());
            assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloaded.getFailureReason()).isEqualTo("Lease expired; returned to queue");
        }

        @Test
        @DisplayName("recordPermanentFailureAndInvalidateEndpoint rejects stale worker when lease was expired and reaped")
        void recordPermanentFailureAndInvalidateEndpointRejectsStaleWorkerAfterReap() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            DisasterEvent event2 = persistDisasterEvent("USGS", "event-" + UUID.randomUUID(), now);
            Alert alert2 = persistAlert(event2, primaryRule, AlertStatus.PROCESSED, now.plus(1, ChronoUnit.SECONDS));

            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);
            Notification nExpired = persistNotification(
                    alert1, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PROCESSING, 1, now.minus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES)
            );
            Notification nPending = persistNotification(
                    alert2, endpoint, NotificationChannel.DISCORD, "hint-1",
                    NotificationStatus.PENDING, 0, now, null
            );

            int reaped = deliveryRepository.reapExpiredLeases(now, 3);
            assertThat(reaped).isEqualTo(1);

            boolean updated = deliveryRepository.recordPermanentFailureAndInvalidateEndpoint(
                    nExpired.getId(), 1, endpoint.getId(), "HTTP 404 WEBHOOK_NOT_FOUND", now
            );
            assertThat(updated).isFalse();

            entityManager.clear();
            Notification reloadedExpired = entityManager.find(Notification.class, nExpired.getId());
            assertThat(reloadedExpired.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloadedExpired.getFailureReason()).isEqualTo("Lease expired; returned to queue");

            NotificationEndpoint reloadedEndpoint = entityManager.find(NotificationEndpoint.class, endpoint.getId());
            assertThat(reloadedEndpoint.isEnabled()).isTrue();
            assertThat(reloadedEndpoint.getDisabledReason()).isNull();

            Notification reloadedPending = entityManager.find(Notification.class, nPending.getId());
            assertThat(reloadedPending.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reloadedPending.getFailureReason()).isNull();
        }
    }

    @Nested
    @DisplayName("invalidateEndpointAndCancelPending")
    class InvalidateEndpointAndCancelPending {

        @Test
        @DisplayName("disables endpoint with reason and cancels all other pending notifications for that endpoint")
        void disablesEndpointAndCancelsPendingNotifications() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            DisasterEvent event2 = persistDisasterEvent("USGS", "event-" + UUID.randomUUID(), now);
            Alert alert2 = persistAlert(event2, primaryRule, AlertStatus.PROCESSED, now.plus(1, ChronoUnit.SECONDS));
            DisasterEvent event3 = persistDisasterEvent("USGS", "event-" + UUID.randomUUID(), now);
            Alert alert3 = persistAlert(event3, primaryRule, AlertStatus.PROCESSED, now.plus(2, ChronoUnit.SECONDS));

            NotificationEndpoint endpoint1 = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);
            NotificationEndpoint endpoint2 = persistEndpoint(primaryUser, NotificationChannel.EMAIL, "hint-2", true, now);

            Notification n1Pending = persistNotification(alert1, endpoint1, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PENDING, 0, now, null);
            Notification n2Pending = persistNotification(alert2, endpoint1, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PENDING, 1, now, null);
            Notification n1Sent = persistNotification(alert1, endpoint1, NotificationChannel.EMAIL, "hint-1", NotificationStatus.SENT, 1, now, null);
            Notification nOtherEndpoint = persistNotification(alert3, endpoint2, NotificationChannel.EMAIL, "hint-2", NotificationStatus.PENDING, 0, now, null);

            int cancelled = deliveryRepository.invalidateEndpointAndCancelPending(endpoint1.getId(), "HTTP 404 WEBHOOK_NOT_FOUND", now);
            assertThat(cancelled).isEqualTo(2);

            entityManager.clear();
            NotificationEndpoint reloadedEndpoint1 = entityManager.find(NotificationEndpoint.class, endpoint1.getId());
            assertThat(reloadedEndpoint1.isEnabled()).isFalse();
            assertThat(reloadedEndpoint1.getDisabledReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");

            Notification reloadedN1 = entityManager.find(Notification.class, n1Pending.getId());
            assertThat(reloadedN1.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
            assertThat(reloadedN1.getFailureReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");

            Notification reloadedN2 = entityManager.find(Notification.class, n2Pending.getId());
            assertThat(reloadedN2.getStatus()).isEqualTo(NotificationStatus.CANCELLED);

            Notification reloadedN1Sent = entityManager.find(Notification.class, n1Sent.getId());
            assertThat(reloadedN1Sent.getStatus()).isEqualTo(NotificationStatus.SENT);

            Notification reloadedOther = entityManager.find(Notification.class, nOtherEndpoint.getId());
            assertThat(reloadedOther.getStatus()).isEqualTo(NotificationStatus.PENDING);
        }

        @Test
        @DisplayName("handles reason longer than 100 characters by safely truncating for endpoint and keeping full reason for notifications")
        void handlesLongReasonSafely() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-long", true, now);
            Notification nPending = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "hint-long", NotificationStatus.PENDING, 0, now, null);

            String longReason = "HTTP 404 Discord webhook deleted permanently: https://discord.com/api/webhooks/1234567890/very-long-token-that-exceeds-one-hundred-characters-and-would-normally-cause-a-database-overflow-error";
            assertThat(longReason.length()).isGreaterThan(100);

            int cancelled = deliveryRepository.invalidateEndpointAndCancelPending(endpoint.getId(), longReason, now);
            assertThat(cancelled).isEqualTo(1);

            entityManager.clear();
            NotificationEndpoint reloadedEndpoint = entityManager.find(NotificationEndpoint.class, endpoint.getId());
            assertThat(reloadedEndpoint.isEnabled()).isFalse();
            assertThat(reloadedEndpoint.getDisabledReason()).isEqualTo(longReason.substring(0, 100));

            Notification reloadedNotification = entityManager.find(Notification.class, nPending.getId());
            assertThat(reloadedNotification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
            assertThat(reloadedNotification.getFailureReason()).isEqualTo(longReason);
        }
    }

    @Nested
    @DisplayName("recordPermanentFailureAndInvalidateEndpoint")
    class RecordPermanentFailureAndInvalidateEndpoint {

        @Test
        @DisplayName("atomically records permanent failure, disables endpoint with reason, and cancels other pending notifications")
        void atomicallyRecordsPermanentFailureAndInvalidatesEndpoint() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            DisasterEvent event2 = persistDisasterEvent("USGS", "event-" + UUID.randomUUID(), now);
            Alert alert2 = persistAlert(event2, primaryRule, AlertStatus.PROCESSED, now.plus(1, ChronoUnit.SECONDS));
            DisasterEvent event3 = persistDisasterEvent("USGS", "event-" + UUID.randomUUID(), now);
            Alert alert3 = persistAlert(event3, primaryRule, AlertStatus.PROCESSED, now.plus(2, ChronoUnit.SECONDS));

            NotificationEndpoint endpoint1 = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);
            NotificationEndpoint endpoint2 = persistEndpoint(primaryUser, NotificationChannel.EMAIL, "hint-2", true, now);

            Notification nProcessing = persistNotification(alert1, endpoint1, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PROCESSING, 1, now, now.plus(2, ChronoUnit.MINUTES));
            Notification nPending = persistNotification(alert2, endpoint1, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PENDING, 0, now, null);
            Notification nSent = persistNotification(alert1, endpoint1, NotificationChannel.EMAIL, "hint-1", NotificationStatus.SENT, 1, now, null);
            Notification nOther = persistNotification(alert3, endpoint2, NotificationChannel.EMAIL, "hint-2", NotificationStatus.PENDING, 0, now, null);

            boolean updated = deliveryRepository.recordPermanentFailureAndInvalidateEndpoint(
                    nProcessing.getId(), 1, endpoint1.getId(), "HTTP 404 WEBHOOK_NOT_FOUND", now
            );

            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloadedProcessing = entityManager.find(Notification.class, nProcessing.getId());
            assertThat(reloadedProcessing.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(reloadedProcessing.getFailureReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");
            assertThat(reloadedProcessing.getLockedUntil()).isNull();

            NotificationEndpoint reloadedEndpoint1 = entityManager.find(NotificationEndpoint.class, endpoint1.getId());
            assertThat(reloadedEndpoint1.isEnabled()).isFalse();
            assertThat(reloadedEndpoint1.getDisabledReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");

            Notification reloadedPending = entityManager.find(Notification.class, nPending.getId());
            assertThat(reloadedPending.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
            assertThat(reloadedPending.getFailureReason()).isEqualTo("HTTP 404 WEBHOOK_NOT_FOUND");

            Notification reloadedSent = entityManager.find(Notification.class, nSent.getId());
            assertThat(reloadedSent.getStatus()).isEqualTo(NotificationStatus.SENT);

            Notification reloadedOther = entityManager.find(Notification.class, nOther.getId());
            assertThat(reloadedOther.getStatus()).isEqualTo(NotificationStatus.PENDING);

            NotificationEndpoint reloadedEndpoint2 = entityManager.find(NotificationEndpoint.class, endpoint2.getId());
            assertThat(reloadedEndpoint2.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("fencing mismatch returns false and leaves notification, endpoint, and pending notifications untouched")
        void fencingMismatchReturnsFalseAndLeavesAllRowsUntouched() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            DisasterEvent event2 = persistDisasterEvent("USGS", "event-" + UUID.randomUUID(), now);
            Alert alert2 = persistAlert(event2, primaryRule, AlertStatus.PROCESSED, now.plus(1, ChronoUnit.SECONDS));

            NotificationEndpoint endpoint1 = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);

            Notification nProcessing = persistNotification(alert1, endpoint1, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PROCESSING, 1, now, now.plus(2, ChronoUnit.MINUTES));
            Notification nPending = persistNotification(alert2, endpoint1, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PENDING, 0, now, null);

            // Worker provides stale attemptCount = 2 when current is 1
            boolean updated = deliveryRepository.recordPermanentFailureAndInvalidateEndpoint(
                    nProcessing.getId(), 2, endpoint1.getId(), "HTTP 404 WEBHOOK_NOT_FOUND", now
            );

            assertThat(updated).isFalse();

            entityManager.clear();
            Notification reloadedProcessing = entityManager.find(Notification.class, nProcessing.getId());
            assertThat(reloadedProcessing.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
            assertThat(reloadedProcessing.getAttemptCount()).isEqualTo(1);

            NotificationEndpoint reloadedEndpoint1 = entityManager.find(NotificationEndpoint.class, endpoint1.getId());
            assertThat(reloadedEndpoint1.isEnabled()).isTrue();
            assertThat(reloadedEndpoint1.getDisabledReason()).isNull();

            Notification reloadedPending = entityManager.find(Notification.class, nPending.getId());
            assertThat(reloadedPending.getStatus()).isEqualTo(NotificationStatus.PENDING);
        }

        @Test
        @DisplayName("safely truncates oversized reasons to 500 characters for notifications and 100 for endpoint")
        void safelyTruncatesOversizedReasons() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert1 = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            DisasterEvent event2 = persistDisasterEvent("USGS", "event-" + UUID.randomUUID(), now);
            Alert alert2 = persistAlert(event2, primaryRule, AlertStatus.PROCESSED, now.plus(1, ChronoUnit.SECONDS));

            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);
            Notification nProcessing = persistNotification(alert1, endpoint, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PROCESSING, 1, now, now.plus(2, ChronoUnit.MINUTES));
            Notification nPending = persistNotification(alert2, endpoint, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PENDING, 0, now, null);

            String oversizedReason = "HTTP 404 ERROR: " + "X".repeat(600);

            boolean updated = deliveryRepository.recordPermanentFailureAndInvalidateEndpoint(
                    nProcessing.getId(), 1, endpoint.getId(), oversizedReason, now
            );

            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloadedProcessing = entityManager.find(Notification.class, nProcessing.getId());
            assertThat(reloadedProcessing.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(reloadedProcessing.getFailureReason()).hasSize(500);
            assertThat(reloadedProcessing.getFailureReason()).isEqualTo(oversizedReason.substring(0, 500));

            NotificationEndpoint reloadedEndpoint = entityManager.find(NotificationEndpoint.class, endpoint.getId());
            assertThat(reloadedEndpoint.getDisabledReason()).hasSize(100);
            assertThat(reloadedEndpoint.getDisabledReason()).isEqualTo(oversizedReason.substring(0, 100));

            Notification reloadedPending = entityManager.find(Notification.class, nPending.getId());
            assertThat(reloadedPending.getFailureReason()).hasSize(500);
            assertThat(reloadedPending.getFailureReason()).isEqualTo(oversizedReason.substring(0, 500));
        }

        @Test
        @DisplayName("handles null endpointId gracefully by marking notification as FAILED without touching endpoints")
        void handlesNullEndpointIdGracefully() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Alert alert = persistAlert(primaryDisasterEvent, primaryRule, AlertStatus.PROCESSED, now);
            NotificationEndpoint endpoint = persistEndpoint(primaryUser, NotificationChannel.DISCORD, "hint-1", true, now);
            Notification nProcessing = persistNotification(alert, endpoint, NotificationChannel.DISCORD, "hint-1", NotificationStatus.PROCESSING, 1, now, now.plus(2, ChronoUnit.MINUTES));

            boolean updated = deliveryRepository.recordPermanentFailureAndInvalidateEndpoint(
                    nProcessing.getId(), 1, null, "HTTP 404 WEBHOOK_NOT_FOUND", now
            );

            assertThat(updated).isTrue();

            entityManager.clear();
            Notification reloadedProcessing = entityManager.find(Notification.class, nProcessing.getId());
            assertThat(reloadedProcessing.getStatus()).isEqualTo(NotificationStatus.FAILED);

            NotificationEndpoint reloadedEndpoint = entityManager.find(NotificationEndpoint.class, endpoint.getId());
            assertThat(reloadedEndpoint.isEnabled()).isTrue();
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
        return persistNotification(alert, endpoint, channel, destinationHint, NotificationStatus.PENDING, 0, Instant.now(), null);
    }

    private Notification persistNotification(
            Alert alert,
            NotificationEndpoint endpoint,
            NotificationChannel channel,
            String destinationHint,
            NotificationStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant lockedUntil
    ) {
        Notification notification = Notification.builder()
                .alert(alert)
                .endpoint(endpoint)
                .channel(channel)
                .destination(destinationHint)
                .status(status)
                .attemptCount(attemptCount)
                .nextAttemptAt(nextAttemptAt)
                .lockedUntil(lockedUntil)
                .build();
        return entityManager.persistAndFlush(notification);
    }
}
