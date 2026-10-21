package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.channel.AlertMessage;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelSupportRegistry;
import dev.romulus_lanceues.tanaw_api.notification.channel.DeliveryResult;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Service responsible for executing individual notification delivery attempts.
 * <p>
 * Key architectural guarantees:
 * <ul>
 *     <li><b>Pre-send guards:</b> Validates that the endpoint is present, enabled, verified,
 *         the user is active, destination is valid, and the channel sender is registered.
 *         Cancels notification if guards fail.</li>
 *     <li><b>Non-transactional dispatch:</b> Outbound HTTP delivery is performed outside of
 *         any database transaction to protect the HikariCP connection pool.</li>
 *     <li><b>Optimistic fencing:</b> Coordinates outcome updates enforcing attempt-count
 *         fencing tokens ({@code WHERE status = 'PROCESSING' AND attempt_count = :claimedAttempt})
 *         to reject stale workers superseded by reapers or concurrent threads.</li>
 *     <li><b>Secret redaction:</b> Unexpected runtime exceptions during dispatch are sanitized
 *         to prevent webhook URLs or tokens from leaking into database columns or logs.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryExecutor {

    private final NotificationDeliveryRepository deliveryRepository;
    private final ChannelSupportRegistry channelSupportRegistry;
    private final AlertMessageFactory alertMessageFactory;
    private final NotificationRetryPolicy retryPolicy;
    private final Clock clock;

    /**
     * Executes delivery for a claimed notification.
     *
     * @param claimed claimed notification with id and attemptCount
     * @param context delivery context containing alert and endpoint information
     * @return outcome of the execution
     */
    public DeliveryExecutionOutcome executeDelivery(ClaimedNotification claimed, NotificationDeliveryContext context) {
        if (claimed == null) {
            throw new IllegalArgumentException("ClaimedNotification must not be null.");
        }
        return executeDelivery(context, claimed.attemptCount());
    }

    /**
     * Executes delivery for a notification context at the given attempt count.
     *
     * @param context      delivery context containing alert and endpoint information
     * @param attemptCount 1-based attempt number for optimistic fencing
     * @return outcome of the execution
     */
    public DeliveryExecutionOutcome executeDelivery(NotificationDeliveryContext context, int attemptCount) {
        validateInputs(context, attemptCount);

        // 1. Pre-send guards
        String cancellationReason = evaluatePreSendGuards(context);
        if (cancellationReason != null) {
            return handlePreSendCancellation(context, attemptCount, cancellationReason);
        }

        // 2. Non-transactional dispatch outside database transactions
        DeliveryResult result = dispatchNonTransactional(context);

        // 3. Coordinate delivery outcome with fenced persistence updates
        return coordinateOutcome(context, attemptCount, result);
    }

    private void validateInputs(NotificationDeliveryContext context, int attemptCount) {
        if (context == null) {
            throw new IllegalArgumentException("NotificationDeliveryContext must not be null.");
        }
        if (context.notificationId() == null) {
            throw new IllegalArgumentException("Notification ID must not be null.");
        }
        if (attemptCount <= 0) {
            throw new IllegalArgumentException("attemptCount must be positive.");
        }
    }

    private String evaluatePreSendGuards(NotificationDeliveryContext context) {
        if (!context.isEndpointPresent()) {
            return "Endpoint not found or deleted";
        }
        if (!context.isEndpointEnabled()) {
            return "Endpoint is disabled";
        }
        if (!context.isEndpointVerified()) {
            return "Endpoint is not verified";
        }
        if (!context.isUserActive()) {
            return "User account is not active";
        }
        if (context.destination() == null || context.destination().isBlank()) {
            return "Notification destination is missing or empty";
        }
        if (!channelSupportRegistry.hasSender(context.channel())) {
            return "Unsupported notification channel: " + context.channel();
        }
        return null;
    }

    private DeliveryExecutionOutcome handlePreSendCancellation(
            NotificationDeliveryContext context,
            int attemptCount,
            String reason
    ) {
        Instant now = clock.instant();
        boolean updated = deliveryRepository.recordPreSendCancellation(
                context.notificationId(),
                attemptCount,
                reason,
                now
        );

        if (!updated) {
            log.warn("Fenced pre-send cancellation failed for notification {} at attempt {}: already superseded or processed",
                    context.notificationId(), attemptCount);
            return DeliveryExecutionOutcome.FENCE_CONFLICT;
        }

        log.info("Pre-send guard cancelled notification {} at attempt {}: {}",
                context.notificationId(), attemptCount, reason);
        return DeliveryExecutionOutcome.CANCELLED;
    }

    private DeliveryResult dispatchNonTransactional(NotificationDeliveryContext context) {
        try {
            AlertMessage message = alertMessageFactory.create(context);
            NotificationSender sender = channelSupportRegistry.getSender(context.channel());
            DeliveryResult result = sender.send(message, context.destination());
            return result != null ? result : new DeliveryResult.Retryable("Sender returned null result");
        } catch (Exception ex) {
            log.warn("Unexpected exception during dispatch of notification {} (alertId={}, channel={}): {}",
                    context.notificationId(), context.alertId(), context.channel(), ex.getClass().getSimpleName());
            String safeReason = sanitizeExceptionReason(ex);
            return new DeliveryResult.Retryable(safeReason);
        }
    }

    private String sanitizeExceptionReason(Throwable ex) {
        if (ex instanceof RestClientResponseException restEx) {
            return "HTTP " + restEx.getStatusCode().value() + " error during dispatch";
        }
        if (ex instanceof ResourceAccessException) {
            return "Network timeout or I/O error during dispatch";
        }
        return "Unexpected dispatch error: " + ex.getClass().getSimpleName();
    }

    private DeliveryExecutionOutcome coordinateOutcome(
            NotificationDeliveryContext context,
            int attemptCount,
            DeliveryResult result
    ) {
        Instant now = clock.instant();

        return switch (result) {
            case DeliveryResult.Sent sent -> handleSent(context, attemptCount, sent, now);
            case DeliveryResult.Retryable retryable -> handleRetryable(context, attemptCount, retryable, now);
            case DeliveryResult.Permanent permanent -> handlePermanent(context, attemptCount, permanent, now);
        };
    }

    private DeliveryExecutionOutcome handleSent(
            NotificationDeliveryContext context,
            int attemptCount,
            DeliveryResult.Sent sent,
            Instant now
    ) {
        boolean updated = deliveryRepository.recordSuccess(
                context.notificationId(),
                attemptCount,
                sent.providerMessageId(),
                now,
                now
        );

        if (!updated) {
            log.warn("Fenced success update failed for notification {} at attempt {}: already superseded or processed",
                    context.notificationId(), attemptCount);
            return DeliveryExecutionOutcome.FENCE_CONFLICT;
        }

        log.info("Successfully delivered notification {} (alertId={}, channel={})",
                context.notificationId(), context.alertId(), context.channel());
        return DeliveryExecutionOutcome.SENT;
    }

    private DeliveryExecutionOutcome handleRetryable(
            NotificationDeliveryContext context,
            int attemptCount,
            DeliveryResult.Retryable retryable,
            Instant now
    ) {
        Instant nextAttemptAt = retryPolicy.calculateNextAttempt(attemptCount, retryable.retryAfter(), now);
        int maxAttempts = retryPolicy.maxAttempts();

        boolean updated = deliveryRepository.recordRetryableFailure(
                context.notificationId(),
                attemptCount,
                retryable.reason(),
                nextAttemptAt,
                maxAttempts,
                now
        );

        if (!updated) {
            log.warn("Fenced retryable update failed for notification {} at attempt {}: already superseded or processed",
                    context.notificationId(), attemptCount);
            return DeliveryExecutionOutcome.FENCE_CONFLICT;
        }

        if (retryPolicy.isExhausted(attemptCount)) {
            log.warn("Notification {} retry attempts exhausted ({}/{}) for alert {}: {}",
                    context.notificationId(), attemptCount, maxAttempts, context.alertId(), retryable.reason());
        } else {
            log.info("Notification {} retryable failure recorded ({}/{}); next attempt at {}: {}",
                    context.notificationId(), attemptCount, maxAttempts, nextAttemptAt, retryable.reason());
        }

        return DeliveryExecutionOutcome.RETRYABLE;
    }

    private DeliveryExecutionOutcome handlePermanent(
            NotificationDeliveryContext context,
            int attemptCount,
            DeliveryResult.Permanent permanent,
            Instant now
    ) {
        boolean updated = deliveryRepository.recordPermanentFailure(
                context.notificationId(),
                attemptCount,
                permanent.reason(),
                now
        );

        if (!updated) {
            log.warn("Fenced permanent failure update failed for notification {} at attempt {}: already superseded or processed",
                    context.notificationId(), attemptCount);
            return DeliveryExecutionOutcome.FENCE_CONFLICT;
        }

        log.warn("Permanent failure recorded for notification {} (alertId={}, channel={}): {}",
                context.notificationId(), context.alertId(), context.channel(), permanent.reason());

        if (permanent.invalidateEndpoint() && context.isEndpointPresent()) {
            int cancelledCount = deliveryRepository.invalidateEndpointAndCancelPending(
                    context.endpointId(),
                    permanent.reason(),
                    now
            );
            log.warn("Disabled endpoint {} and cancelled {} pending notification(s) due to permanent failure: {}",
                    context.endpointId(), cancelledCount, permanent.reason());
        }

        return DeliveryExecutionOutcome.PERMANENT_FAILURE;
    }
}
