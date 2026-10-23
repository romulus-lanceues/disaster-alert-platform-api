package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelSupportRegistry;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationFanoutService Unit Tests")
class NotificationFanoutServiceTest {

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private ChannelSupportRegistry channelSupportRegistry;

    @Mock
    private NotificationProperties properties;

    @Mock
    private NotificationProperties.Dispatcher dispatcherProps;

    @Mock
    private NotificationProperties.Fanout fanoutProps;

    private Clock clock;
    private Instant now;
    private NotificationFanoutService fanoutService;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-16T12:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);

        given(properties.dispatcher()).willReturn(dispatcherProps);
        given(dispatcherProps.batchSize()).willReturn(50);
        org.mockito.Mockito.lenient().when(properties.fanout()).thenReturn(fanoutProps);
        org.mockito.Mockito.lenient().when(fanoutProps.maxEventAge()).thenReturn(Duration.ofHours(6));

        fanoutService = new NotificationFanoutService(
                deliveryRepository,
                channelSupportRegistry,
                properties,
                clock
        );
    }

    @Test
    @DisplayName("returns empty FanoutResult when no pending alerts are claimed")
    void returnsEmptyWhenNoAlertsClaimed() {
        given(deliveryRepository.claimPendingAlerts(50)).willReturn(List.of());

        FanoutResult result = fanoutService.fanout();

        assertThat(result).isEqualTo(FanoutResult.empty());
        then(deliveryRepository).should(never()).cancelAlerts(anyList());
        then(deliveryRepository).should(never()).createNotificationsForAlerts(anyList(), any(), any());
        then(deliveryRepository).should(never()).markAlertsProcessed(anyList());
    }

    @Test
    @DisplayName("cancels stale alerts whose event occurred before the max-event-age cutoff")
    void cancelsStaleAlerts() {
        UUID staleAlertId = UUID.randomUUID();
        UUID activeAlertId = UUID.randomUUID();

        // 7 hours ago (older than 6h cutoff)
        ClaimedAlert staleAlert = new ClaimedAlert(staleAlertId, now.minus(Duration.ofHours(7)));
        // 1 hour ago (within 6h cutoff)
        ClaimedAlert activeAlert = new ClaimedAlert(activeAlertId, now.minus(Duration.ofHours(1)));

        given(deliveryRepository.claimPendingAlerts(50)).willReturn(List.of(staleAlert, activeAlert));
        given(channelSupportRegistry.getSupportedChannels()).willReturn(Set.of(NotificationChannel.DISCORD));
        given(deliveryRepository.cancelAlerts(List.of(staleAlertId))).willReturn(1);
        given(deliveryRepository.createNotificationsForAlerts(List.of(activeAlertId), Set.of(NotificationChannel.DISCORD), now)).willReturn(1);
        given(deliveryRepository.markAlertsProcessed(List.of(activeAlertId))).willReturn(1);

        FanoutResult result = fanoutService.fanout();

        assertThat(result.alertsClaimed()).isEqualTo(2);
        assertThat(result.alertsCancelledStale()).isEqualTo(1);
        assertThat(result.alertsProcessed()).isEqualTo(1);
        assertThat(result.notificationsCreated()).isEqualTo(1);

        then(deliveryRepository).should().cancelAlerts(List.of(staleAlertId));
        then(deliveryRepository).should().createNotificationsForAlerts(List.of(activeAlertId), Set.of(NotificationChannel.DISCORD), now);
        then(deliveryRepository).should().markAlertsProcessed(List.of(activeAlertId));
    }

    @Test
    @DisplayName("cancels all alerts and skips notifications when all claimed alerts are stale")
    void cancelsAllAlertsWhenAllStale() {
        UUID staleId1 = UUID.randomUUID();
        UUID staleId2 = UUID.randomUUID();

        ClaimedAlert alert1 = new ClaimedAlert(staleId1, now.minus(Duration.ofHours(8)));
        ClaimedAlert alert2 = new ClaimedAlert(staleId2, now.minus(Duration.ofHours(12)));

        given(deliveryRepository.claimPendingAlerts(50)).willReturn(List.of(alert1, alert2));
        given(deliveryRepository.cancelAlerts(List.of(staleId1, staleId2))).willReturn(2);

        FanoutResult result = fanoutService.fanout();

        assertThat(result.alertsClaimed()).isEqualTo(2);
        assertThat(result.alertsCancelledStale()).isEqualTo(2);
        assertThat(result.alertsProcessed()).isZero();
        assertThat(result.notificationsCreated()).isZero();

        then(deliveryRepository).should().cancelAlerts(List.of(staleId1, staleId2));
        then(deliveryRepository).should(never()).createNotificationsForAlerts(anyList(), any(), any());
        then(deliveryRepository).should(never()).markAlertsProcessed(anyList());
    }

    @Test
    @DisplayName("marks alerts processed without creating notifications when no channels are supported")
    void processesAlertsWhenNoChannelsSupported() {
        UUID activeId = UUID.randomUUID();
        ClaimedAlert activeAlert = new ClaimedAlert(activeId, now.minus(Duration.ofMinutes(30)));

        given(deliveryRepository.claimPendingAlerts(50)).willReturn(List.of(activeAlert));
        given(channelSupportRegistry.getSupportedChannels()).willReturn(Set.of());
        given(deliveryRepository.markAlertsProcessed(List.of(activeId))).willReturn(1);

        FanoutResult result = fanoutService.fanout();

        assertThat(result.alertsClaimed()).isEqualTo(1);
        assertThat(result.alertsCancelledStale()).isZero();
        assertThat(result.alertsProcessed()).isEqualTo(1);
        assertThat(result.notificationsCreated()).isZero();

        then(deliveryRepository).should(never()).cancelAlerts(anyList());
        then(deliveryRepository).should(never()).createNotificationsForAlerts(anyList(), any(), any());
        then(deliveryRepository).should().markAlertsProcessed(List.of(activeId));
    }

    @Test
    @DisplayName("successfully fans out all active alerts across supported channels")
    void happyPathFanout() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ClaimedAlert a1 = new ClaimedAlert(id1, now.minus(Duration.ofMinutes(10)));
        ClaimedAlert a2 = new ClaimedAlert(id2, now.minus(Duration.ofMinutes(20)));

        given(deliveryRepository.claimPendingAlerts(50)).willReturn(List.of(a1, a2));
        given(channelSupportRegistry.getSupportedChannels()).willReturn(Set.of(NotificationChannel.DISCORD, NotificationChannel.EMAIL));
        given(deliveryRepository.createNotificationsForAlerts(List.of(id1, id2), Set.of(NotificationChannel.DISCORD, NotificationChannel.EMAIL), now))
                .willReturn(4);
        given(deliveryRepository.markAlertsProcessed(List.of(id1, id2))).willReturn(2);

        FanoutResult result = fanoutService.fanout();

        assertThat(result.alertsClaimed()).isEqualTo(2);
        assertThat(result.alertsCancelledStale()).isZero();
        assertThat(result.alertsProcessed()).isEqualTo(2);
        assertThat(result.notificationsCreated()).isEqualTo(4);

        then(deliveryRepository).should(never()).cancelAlerts(anyList());
        then(deliveryRepository).should().createNotificationsForAlerts(List.of(id1, id2), Set.of(NotificationChannel.DISCORD, NotificationChannel.EMAIL), now);
        then(deliveryRepository).should().markAlertsProcessed(List.of(id1, id2));
    }
}
