package dev.romulus_lanceues.tanaw_api.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

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
}
