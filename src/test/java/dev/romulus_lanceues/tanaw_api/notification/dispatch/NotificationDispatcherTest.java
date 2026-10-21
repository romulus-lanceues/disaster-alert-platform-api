package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatcher Unit Tests")
class NotificationDispatcherTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-20T15:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private NotificationFanoutService fanoutService;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private NotificationDeliveryExecutor deliveryExecutor;

    private NotificationProperties properties;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties(
                false,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 10, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("dGVzdC1zZWNyZXQta2V5LTMyLWJ5dGVzLWxvbmctISEhIQ=="),
                null
        );

        dispatcher = new NotificationDispatcher(
                fanoutService,
                deliveryRepository,
                deliveryExecutor,
                properties,
                clock
        );
    }

    private NotificationDeliveryContext createMockContext(UUID notificationId) {
        return new NotificationDeliveryContext(
                notificationId,
                UUID.randomUUID(),
                NotificationChannel.DISCORD,
                "https://discord.com/api/webhooks/123/token",
                "Discord: ...token",
                UUID.randomUUID(),
                true,
                FIXED_NOW.minusSeconds(3600),
                UUID.randomUUID(),
                UserStatus.ACTIVE,
                "Home",
                "Manila",
                FIXED_NOW.minusSeconds(120),
                "reviewed",
                "CRITICAL",
                null,
                6.0,
                10.0,
                15.0
        );
    }

    @Test
    @DisplayName("executes fan-out, lease reaping, and single batch dispatch when queue has fewer items than batchSize")
    void executesFanoutReapingAndSingleBatch() {
        FanoutResult fanoutResult = new FanoutResult(2, 0, 2, 2);
        given(fanoutService.fanout()).willReturn(fanoutResult);
        given(deliveryRepository.reapExpiredLeases(FIXED_NOW, 5)).willReturn(1);

        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        List<ClaimedNotification> claimed = List.of(
                new ClaimedNotification(n1, 1),
                new ClaimedNotification(n2, 1)
        );
        given(deliveryRepository.claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2)))
                .willReturn(claimed);

        NotificationDeliveryContext ctx1 = createMockContext(n1);
        NotificationDeliveryContext ctx2 = createMockContext(n2);
        given(deliveryRepository.findDeliveryContexts(List.of(n1, n2)))
                .willReturn(List.of(ctx1, ctx2));

        given(deliveryExecutor.executeDelivery(eq(claimed.get(0)), eq(ctx1))).willReturn(DeliveryExecutionOutcome.SENT);
        given(deliveryExecutor.executeDelivery(eq(claimed.get(1)), eq(ctx2))).willReturn(DeliveryExecutionOutcome.SENT);

        DispatchTickResult result = dispatcher.dispatchTick();

        assertThat(result.fanoutResult()).isEqualTo(fanoutResult);
        assertThat(result.reapedLeases()).isEqualTo(1);
        assertThat(result.batchesClaimed()).isEqualTo(1);
        assertThat(result.notificationsDispatched()).isEqualTo(2);

        then(fanoutService).should().fanout();
        then(deliveryRepository).should().reapExpiredLeases(FIXED_NOW, 5);
        then(deliveryRepository).should().claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2));
        then(deliveryExecutor).should().executeDelivery(claimed.get(0), ctx1);
        then(deliveryExecutor).should().executeDelivery(claimed.get(1), ctx2);
    }

    @Test
    @DisplayName("drains multiple batches immediately when each batch claims full batchSize")
    void drainsMultipleBatchesWhenFull() {
        given(fanoutService.fanout()).willReturn(FanoutResult.empty());
        given(deliveryRepository.reapExpiredLeases(FIXED_NOW, 5)).willReturn(0);

        // Batch 1: exactly 10 items (batchSize = 10)
        List<ClaimedNotification> batch1 = List.of(
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1),
                new ClaimedNotification(UUID.randomUUID(), 1)
        );

        // Batch 2: 3 items (< batchSize)
        UUID b2Id1 = UUID.randomUUID();
        UUID b2Id2 = UUID.randomUUID();
        UUID b2Id3 = UUID.randomUUID();
        List<ClaimedNotification> batch2 = List.of(
                new ClaimedNotification(b2Id1, 1),
                new ClaimedNotification(b2Id2, 1),
                new ClaimedNotification(b2Id3, 1)
        );

        given(deliveryRepository.claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2)))
                .willReturn(batch1)
                .willReturn(batch2);

        given(deliveryRepository.findDeliveryContexts(any()))
                .willAnswer(invocation -> {
                    List<UUID> ids = invocation.getArgument(0);
                    return ids.stream().map(this::createMockContext).toList();
                });

        given(deliveryExecutor.executeDelivery(any(ClaimedNotification.class), any(NotificationDeliveryContext.class)))
                .willReturn(DeliveryExecutionOutcome.SENT);

        DispatchTickResult result = dispatcher.dispatchTick();

        assertThat(result.batchesClaimed()).isEqualTo(2);
        assertThat(result.notificationsDispatched()).isEqualTo(13);

        then(deliveryRepository).should(times(2)).claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2));
        then(deliveryExecutor).should(times(13)).executeDelivery(any(ClaimedNotification.class), any(NotificationDeliveryContext.class));
    }

    @Test
    @DisplayName("terminates claim loop immediately when pending queue is empty")
    void terminatesWhenQueueIsEmpty() {
        given(fanoutService.fanout()).willReturn(FanoutResult.empty());
        given(deliveryRepository.reapExpiredLeases(FIXED_NOW, 5)).willReturn(0);
        given(deliveryRepository.claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2)))
                .willReturn(List.of());

        DispatchTickResult result = dispatcher.dispatchTick();

        assertThat(result.batchesClaimed()).isEqualTo(0);
        assertThat(result.notificationsDispatched()).isEqualTo(0);

        then(deliveryRepository).should(never()).findDeliveryContexts(any());
        then(deliveryExecutor).should(never()).executeDelivery(any(ClaimedNotification.class), any(NotificationDeliveryContext.class));
    }

    @Test
    @DisplayName("contains fan-out exception and safely proceeds to reap and dispatch")
    void containsFanoutException() {
        given(fanoutService.fanout()).willThrow(new RuntimeException("Simulated fan-out error"));
        given(deliveryRepository.reapExpiredLeases(FIXED_NOW, 5)).willReturn(0);

        UUID n1 = UUID.randomUUID();
        List<ClaimedNotification> claimed = List.of(new ClaimedNotification(n1, 1));
        given(deliveryRepository.claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2)))
                .willReturn(claimed);

        NotificationDeliveryContext ctx = createMockContext(n1);
        given(deliveryRepository.findDeliveryContexts(List.of(n1))).willReturn(List.of(ctx));
        given(deliveryExecutor.executeDelivery(claimed.get(0), ctx)).willReturn(DeliveryExecutionOutcome.SENT);

        DispatchTickResult result = dispatcher.dispatchTick();

        assertThat(result.fanoutResult()).isEqualTo(FanoutResult.empty());
        assertThat(result.batchesClaimed()).isEqualTo(1);
        assertThat(result.notificationsDispatched()).isEqualTo(1);

        then(deliveryRepository).should().reapExpiredLeases(FIXED_NOW, 5);
        then(deliveryExecutor).should().executeDelivery(claimed.get(0), ctx);
    }

    @Test
    @DisplayName("contains reaper exception and safely proceeds to claim and dispatch")
    void containsReaperException() {
        given(fanoutService.fanout()).willReturn(FanoutResult.empty());
        given(deliveryRepository.reapExpiredLeases(FIXED_NOW, 5)).willThrow(new RuntimeException("Simulated reaper error"));

        UUID n1 = UUID.randomUUID();
        List<ClaimedNotification> claimed = List.of(new ClaimedNotification(n1, 1));
        given(deliveryRepository.claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2)))
                .willReturn(claimed);

        NotificationDeliveryContext ctx = createMockContext(n1);
        given(deliveryRepository.findDeliveryContexts(List.of(n1))).willReturn(List.of(ctx));
        given(deliveryExecutor.executeDelivery(claimed.get(0), ctx)).willReturn(DeliveryExecutionOutcome.SENT);

        DispatchTickResult result = dispatcher.dispatchTick();

        assertThat(result.reapedLeases()).isEqualTo(0);
        assertThat(result.batchesClaimed()).isEqualTo(1);
        assertThat(result.notificationsDispatched()).isEqualTo(1);

        then(deliveryExecutor).should().executeDelivery(claimed.get(0), ctx);
    }

    @Test
    @DisplayName("cancels orphaned notification when delivery context is missing and continues batch")
    void handlesMissingDeliveryContext() {
        given(fanoutService.fanout()).willReturn(FanoutResult.empty());
        given(deliveryRepository.reapExpiredLeases(FIXED_NOW, 5)).willReturn(0);

        UUID n1 = UUID.randomUUID();
        UUID nOrphaned = UUID.randomUUID();
        List<ClaimedNotification> claimed = List.of(
                new ClaimedNotification(n1, 1),
                new ClaimedNotification(nOrphaned, 1)
        );
        given(deliveryRepository.claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2)))
                .willReturn(claimed);

        // Return context for n1 only, missing context for nOrphaned
        NotificationDeliveryContext ctx1 = createMockContext(n1);
        given(deliveryRepository.findDeliveryContexts(List.of(n1, nOrphaned))).willReturn(List.of(ctx1));
        given(deliveryExecutor.executeDelivery(claimed.get(0), ctx1)).willReturn(DeliveryExecutionOutcome.SENT);

        DispatchTickResult result = dispatcher.dispatchTick();

        assertThat(result.notificationsDispatched()).isEqualTo(1);
        then(deliveryExecutor).should().executeDelivery(claimed.get(0), ctx1);
        then(deliveryRepository).should().recordPreSendCancellation(
                eq(nOrphaned), eq(1), eq("Missing delivery context"), eq(FIXED_NOW));
    }

    @Test
    @DisplayName("contains exception from deliveryExecutor and continues processing remaining batch items")
    void containsExecutorExceptionPerNotification() {
        given(fanoutService.fanout()).willReturn(FanoutResult.empty());
        given(deliveryRepository.reapExpiredLeases(FIXED_NOW, 5)).willReturn(0);

        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        List<ClaimedNotification> claimed = List.of(
                new ClaimedNotification(n1, 1),
                new ClaimedNotification(n2, 1)
        );
        given(deliveryRepository.claimPendingNotifications(10, FIXED_NOW, Duration.ofMinutes(2)))
                .willReturn(claimed);

        NotificationDeliveryContext ctx1 = createMockContext(n1);
        NotificationDeliveryContext ctx2 = createMockContext(n2);
        given(deliveryRepository.findDeliveryContexts(List.of(n1, n2))).willReturn(List.of(ctx1, ctx2));

        given(deliveryExecutor.executeDelivery(claimed.get(0), ctx1))
                .willThrow(new RuntimeException("Unexpected dispatch error"));
        given(deliveryExecutor.executeDelivery(claimed.get(1), ctx2))
                .willReturn(DeliveryExecutionOutcome.SENT);

        DispatchTickResult result = dispatcher.dispatchTick();

        assertThat(result.notificationsDispatched()).isEqualTo(1);
        then(deliveryExecutor).should().executeDelivery(claimed.get(0), ctx1);
        then(deliveryExecutor).should().executeDelivery(claimed.get(1), ctx2);
    }
}
