package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification createNotification(Alert alert, NotificationChannel channel, String destination) {
        log.info("Creating notification for alert {} and channel {}", alert.getId(), channel);

        if (notificationRepository.existsByAlertIdAndChannel(alert.getId(), channel)) {
            throw new NotificationAlreadyExistsException(
                    "Notification already exists for alert %s and channel %s"
                            .formatted(alert.getId(), channel)
            );
        }

        Notification notification = Notification.builder()
                .alert(alert)
                .channel(channel)
                .destination(destination)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .build();

        return notificationRepository.save(notification);
    }

    public Notification getNotification(UUID id) {
        log.info("Fetching notification with ID {}", id);

        return notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + id));
    }

    public Notification getNotificationWithDetails(UUID id) {
        log.info("Fetching notification with details for ID {}", id);

        return notificationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + id));
    }

    public Notification getNotificationForUser(UUID id, UUID userId) {
        log.info("Fetching notification {} for user {}", id, userId);

        Notification notification = notificationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + id));

        UUID ownerId = notification.getAlert().getAlertRule().getLocation().getUser().getId();
        if (!ownerId.equals(userId)) {
            throw new NotificationNotFoundException("Notification not found: " + id);
        }

        return notification;
    }

    public Page<Notification> getNotificationsByUser(UUID userId, Pageable pageable) {
        log.info("Fetching paged notifications for user {}, page: {}, size: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return notificationRepository.findByAlertAlertRuleLocationUserId(userId, pageable);
    }

    public Page<Notification> getNotificationsByUserAndStatus(UUID userId, NotificationStatus status, Pageable pageable) {
        log.info("Fetching paged notifications for user {} with status {}, page: {}, size: {}",
                userId, status, pageable.getPageNumber(), pageable.getPageSize());

        return notificationRepository.findByAlertAlertRuleLocationUserIdAndStatus(userId, status, pageable);
    }

    public List<Notification> getNotificationsByAlert(UUID alertId) {
        log.info("Fetching notifications for alert {}", alertId);

        return notificationRepository.findByAlertId(alertId);
    }

    public Page<Notification> getNotificationsByStatus(NotificationStatus status, Pageable pageable) {
        log.info("Fetching paged notifications with status {}, page: {}, size: {}",
                status, pageable.getPageNumber(), pageable.getPageSize());

        return notificationRepository.findByStatus(status, pageable);
    }

    public List<Notification> getPendingNotificationsForDispatch(int maxAttempts) {
        log.info("Fetching pending notifications for dispatch with max attempts {}", maxAttempts);

        return notificationRepository.findPendingForDispatch(NotificationStatus.PENDING, maxAttempts);
    }

    @Transactional
    public Notification markAsProcessing(UUID id) {
        log.info("Marking notification {} as PROCESSING", id);

        Notification notification = getNotification(id);
        notification.incrementAttempt();
        notification.updateStatus(NotificationStatus.PROCESSING);

        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification recordSuccess(UUID id) {
        log.info("Recording delivery success for notification {}", id);

        Notification notification = getNotification(id);
        notification.recordSuccess(Instant.now(), NotificationStatus.SENT);

        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification recordFailure(UUID id, String failureReason) {
        log.info("Recording delivery failure for notification {}: {}", id, failureReason);

        Notification notification = getNotification(id);
        notification.recordFailure(NotificationStatus.FAILED, failureReason);

        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification markAsCancelled(UUID id) {
        log.info("Marking notification {} as CANCELLED", id);

        Notification notification = getNotification(id);
        notification.updateStatus(NotificationStatus.CANCELLED);

        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification updateNotificationStatus(UUID id, NotificationStatus status) {
        log.info("Updating status of notification {} to {}", id, status);

        Notification notification = getNotification(id);
        notification.updateStatus(status);

        return notificationRepository.save(notification);
    }

    @Transactional
    public void deleteNotification(UUID id) {
        log.info("Deleting notification {}", id);

        Notification notification = getNotification(id);
        notificationRepository.delete(notification);
    }
}
