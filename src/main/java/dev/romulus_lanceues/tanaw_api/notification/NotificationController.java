package dev.romulus_lanceues.tanaw_api.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> getNotifications(
            @RequestParam UUID userId,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(DEFAULT_PAGE_SIZE) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationService.getNotificationsForUser(userId, status, pageable);
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotification(
            @PathVariable UUID id,
            @RequestParam UUID userId) {
        return notificationService.getNotificationForUser(id, userId);
    }
}
