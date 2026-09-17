package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public NotificationResponse createNotification(Alert alert, NotificationChannel channel, String destination) {
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

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    public NotificationResponse getNotificationForUser(UUID id, UUID userId) {
        log.info("Fetching notification {} for user {}", id, userId);

        return notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + id));
    }

    public Page<NotificationResponse> getNotificationsForUser(UUID userId, NotificationStatus status, Pageable pageable) {
        log.info("Fetching paged notifications for user {}, status: {}, page: {}, size: {}",
                userId, status, pageable.getPageNumber(), pageable.getPageSize());

        return notificationRepository.findByUserId(userId, status, pageable);
    }

    @Transactional
    public NotificationResponse markAsProcessing(UUID id) {
        log.info("Marking notification {} as PROCESSING", id);

        Notification notification = findNotification(id);
        notification.incrementAttempt();
        notification.updateStatus(NotificationStatus.PROCESSING);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse recordSuccess(UUID id) {
        log.info("Recording delivery success for notification {}", id);

        Notification notification = findNotification(id);
        notification.recordSuccess(Instant.now(), NotificationStatus.SENT);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse recordFailure(UUID id, String failureReason) {
        log.info("Recording delivery failure for notification {}: {}", id, failureReason);

        Notification notification = findNotification(id);
        notification.recordFailure(NotificationStatus.FAILED, failureReason);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse markAsCancelled(UUID id) {
        log.info("Marking notification {} as CANCELLED", id);

        Notification notification = findNotification(id);
        notification.updateStatus(NotificationStatus.CANCELLED);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    private Notification findNotification(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + id));
    }
}
