package dev.romulus_lanceues.tanaw_api.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationFanoutService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final ChannelSupportRegistry channelSupportRegistry;
    private final NotificationProperties properties;
    private final Clock clock;

    @Transactional
    public FanoutResult fanout() {
        int batchSize = properties.dispatcher().batchSize();
        List<ClaimedAlert> claimedAlerts = deliveryRepository.claimPendingAlerts(batchSize);

        if (claimedAlerts.isEmpty()) {
            return FanoutResult.empty();
        }

        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.fanout().maxEventAge());

        List<UUID> staleAlertIds = new ArrayList<>();
        List<UUID> activeAlertIds = new ArrayList<>();

        for (ClaimedAlert alert : claimedAlerts) {
            if (alert.occurredAt().isBefore(cutoff)) {
                staleAlertIds.add(alert.id());
            } else {
                activeAlertIds.add(alert.id());
            }
        }

        int alertsCancelledStale = 0;
        if (!staleAlertIds.isEmpty()) {
            alertsCancelledStale = deliveryRepository.cancelAlerts(staleAlertIds);
            log.info("Cancelled {} stale pending alerts older than cutoff ({})", alertsCancelledStale, cutoff);
        }

        int notificationsCreated = 0;
        int alertsProcessed = 0;

        if (!activeAlertIds.isEmpty()) {
            Set<NotificationChannel> supportedChannels = channelSupportRegistry.getSupportedChannels();
            if (supportedChannels != null && !supportedChannels.isEmpty()) {
                notificationsCreated = deliveryRepository.createNotificationsForAlerts(activeAlertIds, supportedChannels, now);
            } else {
                log.warn("No notification channels currently supported; skipping notification creation for {} active alerts", activeAlertIds.size());
            }

            alertsProcessed = deliveryRepository.markAlertsProcessed(activeAlertIds);
        }

        log.info("Fan-out batch completed: claimed={}, cancelledStale={}, processed={}, notificationsCreated={}",
                claimedAlerts.size(), alertsCancelledStale, alertsProcessed, notificationsCreated);

        return new FanoutResult(
                claimedAlerts.size(),
                alertsCancelledStale,
                alertsProcessed,
                notificationsCreated
        );
    }
}
