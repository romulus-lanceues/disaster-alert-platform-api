package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scheduled background worker that orchestrates the alert fan-out, expired lease reaping,
 * and high-throughput batch draining of pending notifications.
 * <p>
 * Key architectural guarantees:
 * <ul>
 *     <li><b>Guarded Execution:</b> Scheduled at {@code notification.dispatcher.interval} and
 *         guarded by {@code notification.dispatcher.enabled = true}.</li>
 *     <li><b>Sequential Pipeline:</b> Each tick executes (1) fan-out, (2) lease reaping, and (3)
 *         claim-and-dispatch loop.</li>
 *     <li><b>Immediate Batch Draining:</b> Continues claiming subsequent batches immediately
 *         without waiting for the next timer interval if the current batch is full ({@code size == batchSize}).</li>
 *     <li><b>Exception Containment:</b> Stage-level and item-level exception handling prevents
 *         abnormal termination of the scheduler loop or batch abandonment.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "notification.dispatcher.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationFanoutService fanoutService;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDeliveryExecutor deliveryExecutor;
    private final NotificationProperties properties;
    private final Clock clock;

    /**
     * Scheduled entry point executed periodically by the Spring task scheduler.
     */
    @Scheduled(fixedDelayString = "${notification.dispatcher.interval}")
    public void tick() {
        try {
            dispatchTick();
        } catch (Exception ex) {
            log.error("Unhandled exception occurred during notification dispatcher tick", ex);
        }
    }

    /**
     * Executes a complete dispatcher tick: fan-out, lease reaping, and batch draining loop.
     *
     * @return summary record of actions taken during this tick
     */
    public DispatchTickResult dispatchTick() {
        // 1. Fan-out stage
        FanoutResult fanoutResult = executeFanoutStage();

        // 2. Lease reaping stage
        int reapedLeases = executeReapingStage();

        // 3. Claim and dispatch draining loop
        DrainMetrics drainMetrics = executeDrainingLoop();

        log.debug("Notification dispatcher tick completed: reaped={}, batches={}, dispatched={}",
                reapedLeases, drainMetrics.batchesClaimed(), drainMetrics.notificationsDispatched());

        return new DispatchTickResult(
                fanoutResult,
                reapedLeases,
                drainMetrics.batchesClaimed(),
                drainMetrics.notificationsDispatched()
        );
    }

    private FanoutResult executeFanoutStage() {
        try {
            FanoutResult result = fanoutService.fanout();
            return result != null ? result : FanoutResult.empty();
        } catch (Exception ex) {
            log.error("Notification fan-out failed during dispatcher tick", ex);
            return FanoutResult.empty();
        }
    }

    private int executeReapingStage() {
        try {
            Instant now = clock.instant();
            int maxAttempts = properties.retry().maxAttempts();
            int reaped = deliveryRepository.reapExpiredLeases(now, maxAttempts);
            if (reaped > 0) {
                log.info("Reaped {} expired notification lease(s)", reaped);
            }
            return reaped;
        } catch (Exception ex) {
            log.error("Notification lease reaping failed during dispatcher tick", ex);
            return 0;
        }
    }

    private DrainMetrics executeDrainingLoop() {
        int batchSize = properties.dispatcher().batchSize();
        Duration lease = properties.dispatcher().lease();
        int batchesClaimed = 0;
        int notificationsDispatched = 0;

        while (true) {
            Instant now = clock.instant();
            List<ClaimedNotification> claimedBatch;
            try {
                claimedBatch = deliveryRepository.claimPendingNotifications(batchSize, now, lease);
            } catch (Exception ex) {
                log.error("Failed to claim pending notifications batch during dispatcher tick", ex);
                break;
            }

            if (claimedBatch == null || claimedBatch.isEmpty()) {
                break;
            }

            batchesClaimed++;
            List<UUID> claimedIds = claimedBatch.stream().map(ClaimedNotification::id).toList();

            Map<UUID, NotificationDeliveryContext> contextMap = loadContextMap(claimedIds, batchesClaimed);
            if (contextMap == null) {
                break;
            }

            for (ClaimedNotification claim : claimedBatch) {
                NotificationDeliveryContext context = contextMap.get(claim.id());
                if (context == null) {
                    log.warn("Missing delivery context for claimed notification {}; recording pre-send cancellation", claim.id());
                    try {
                        deliveryRepository.recordPreSendCancellation(
                                claim.id(),
                                claim.attemptCount(),
                                "Missing delivery context",
                                now
                        );
                    } catch (Exception ex) {
                        log.error("Failed to record pre-send cancellation for orphaned notification {}", claim.id(), ex);
                    }
                    continue;
                }

                try {
                    deliveryExecutor.executeDelivery(claim, context);
                    notificationsDispatched++;
                } catch (Exception ex) {
                    log.error("Unexpected error dispatching claimed notification {} (alertId={}, channel={})",
                            claim.id(), context.alertId(), context.channel(), ex);
                }
            }

            // Drains immediately if claimed batch was full (size == batchSize)
            if (claimedBatch.size() < batchSize) {
                break;
            }
        }

        return new DrainMetrics(batchesClaimed, notificationsDispatched);
    }

    private Map<UUID, NotificationDeliveryContext> loadContextMap(List<UUID> claimedIds, int batchIndex) {
        try {
            List<NotificationDeliveryContext> contexts = deliveryRepository.findDeliveryContexts(claimedIds);
            if (contexts == null || contexts.isEmpty()) {
                return Map.of();
            }
            return contexts.stream().collect(
                    Collectors.toMap(NotificationDeliveryContext::notificationId, Function.identity(), (c1, c2) -> c1)
            );
        } catch (Exception ex) {
            log.error("Failed to load delivery contexts for batch {} ({} notifications)",
                    batchIndex, claimedIds.size(), ex);
            return null;
        }
    }

    private record DrainMetrics(int batchesClaimed, int notificationsDispatched) {}
}
