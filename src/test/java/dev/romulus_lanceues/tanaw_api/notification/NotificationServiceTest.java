package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private GeographicArea buildArea() {
        return GeographicArea.builder()
                .id(UUID.randomUUID())
                .psgcCode("137600000")
                .name("Manila")
                .type(GeographicAreaType.MUNICIPALITY)
                .active(true)
                .build();
    }

    private Location buildLocation(UUID id, User user) {
        return Location.builder()
                .id(id)
                .user(user)
                .name("Home")
                .address("123 Rizal St")
                .geographicArea(buildArea())
                .latitude(14.5995)
                .longitude(120.9842)
                .build();
    }

    private AlertRule buildAlertRule(UUID id, Location location) {
        return AlertRule.builder()
                .id(id)
                .location(location)
                .disasterType(DisasterType.EARTHQUAKE)
                .enabled(true)
                .minimumMagnitude(5.0)
                .radiusKm(50.0)
                .minimumSeverity("MODERATE")
                .build();
    }

    private DisasterEvent buildDisasterEvent(UUID id) {
        return DisasterEvent.builder()
                .id(id)
                .source("USGS")
                .externalId("usgs-001")
                .disasterType(DisasterType.EARTHQUAKE)
                .occurredAt(Instant.now())
                .latitude(14.5995)
                .longitude(120.9842)
                .magnitude(6.2)
                .depthKm(10.0)
                .severity("HIGH")
                .build();
    }

    private Alert buildAlert(UUID id, DisasterEvent event, AlertRule rule) {
        return Alert.builder()
                .id(id)
                .disasterEvent(event)
                .alertRule(rule)
                .status(AlertStatus.PENDING)
                .triggeredAt(Instant.now())
                .build();
    }

    private Notification buildNotification(UUID id, Alert alert, NotificationChannel channel, String destination) {
        return Notification.builder()
                .id(id)
                .alert(alert)
                .channel(channel)
                .destination(destination)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .build();
    }

    @Nested
    @DisplayName("createNotification")
    class CreateNotification {

        @Test
        @DisplayName("should create notification successfully when not duplicate")
        void shouldCreateNotificationSuccessfully() {
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule rule = buildAlertRule(UUID.randomUUID(), location);
            DisasterEvent event = buildDisasterEvent(UUID.randomUUID());
            Alert alert = buildAlert(UUID.randomUUID(), event, rule);

            given(notificationRepository.existsByAlertIdAndChannel(alert.getId(), NotificationChannel.EMAIL))
                    .willReturn(false);
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.createNotification(alert, NotificationChannel.EMAIL, "user@example.com");

            assertThat(result).isNotNull();
            assertThat(result.getAlert()).isEqualTo(alert);
            assertThat(result.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(result.getDestination()).isEqualTo("user@example.com");
            assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(result.getAttemptCount()).isZero();

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getAlert()).isEqualTo(alert);
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(saved.getDestination()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("should throw NotificationAlreadyExistsException when alert and channel already exist")
        void shouldThrowWhenNotificationAlreadyExists() {
            UUID alertId = UUID.randomUUID();
            Alert alert = Alert.builder().id(alertId).build();

            given(notificationRepository.existsByAlertIdAndChannel(alertId, NotificationChannel.EMAIL))
                    .willReturn(true);

            assertThatThrownBy(() -> notificationService.createNotification(alert, NotificationChannel.EMAIL, "user@example.com"))
                    .isInstanceOf(NotificationAlreadyExistsException.class)
                    .hasMessageContaining(alertId.toString());

            then(notificationRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("getNotification")
    class GetNotification {

        @Test
        @DisplayName("should return notification when found by id")
        void shouldReturnNotificationWhenFound() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder().id(notifId).build();

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));

            Notification result = notificationService.getNotification(notifId);

            assertThat(result).isEqualTo(notification);
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when id not found")
        void shouldThrowWhenNotFound() {
            UUID notifId = UUID.randomUUID();

            given(notificationRepository.findById(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getNotification(notifId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());
        }
    }

    @Nested
    @DisplayName("getNotificationWithDetails")
    class GetNotificationWithDetails {

        @Test
        @DisplayName("should return notification with details when found")
        void shouldReturnNotificationWithDetailsWhenFound() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder().id(notifId).build();

            given(notificationRepository.findByIdWithDetails(notifId)).willReturn(Optional.of(notification));

            Notification result = notificationService.getNotificationWithDetails(notifId);

            assertThat(result).isEqualTo(notification);
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when details not found")
        void shouldThrowWhenDetailsNotFound() {
            UUID notifId = UUID.randomUUID();

            given(notificationRepository.findByIdWithDetails(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getNotificationWithDetails(notifId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());
        }
    }

    @Nested
    @DisplayName("getNotificationForUser")
    class GetNotificationForUser {

        @Test
        @DisplayName("should return notification when owned by user")
        void shouldReturnNotificationWhenOwnedByUser() {
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule rule = buildAlertRule(UUID.randomUUID(), location);
            DisasterEvent event = buildDisasterEvent(UUID.randomUUID());
            Alert alert = buildAlert(UUID.randomUUID(), event, rule);
            UUID notifId = UUID.randomUUID();
            Notification notification = buildNotification(notifId, alert, NotificationChannel.EMAIL, "dest");

            given(notificationRepository.findByIdWithDetails(notifId)).willReturn(Optional.of(notification));

            Notification result = notificationService.getNotificationForUser(notifId, userId);

            assertThat(result).isEqualTo(notification);
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when notification does not exist")
        void shouldThrowWhenNotificationNotFound() {
            UUID notifId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(notificationRepository.findByIdWithDetails(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getNotificationForUser(notifId, userId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when notification belongs to another user")
        void shouldThrowWhenBelongsToAnotherUser() {
            UUID ownerId = UUID.randomUUID();
            UUID requestingUserId = UUID.randomUUID();
            User user = buildUser(ownerId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule rule = buildAlertRule(UUID.randomUUID(), location);
            DisasterEvent event = buildDisasterEvent(UUID.randomUUID());
            Alert alert = buildAlert(UUID.randomUUID(), event, rule);
            UUID notifId = UUID.randomUUID();
            Notification notification = buildNotification(notifId, alert, NotificationChannel.EMAIL, "dest");

            given(notificationRepository.findByIdWithDetails(notifId)).willReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationService.getNotificationForUser(notifId, requestingUserId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());
        }
    }

    @Nested
    @DisplayName("getNotificationsByUser")
    class GetNotificationsByUser {

        @Test
        @DisplayName("should return paginated notifications for user")
        void shouldReturnPaginatedNotificationsForUser() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            List<Notification> content = List.of(Notification.builder().id(UUID.randomUUID()).build());
            Page<Notification> page = new PageImpl<>(content, pageable, 1);

            given(notificationRepository.findByAlertAlertRuleLocationUserId(userId, pageable)).willReturn(page);

            Page<Notification> result = notificationService.getNotificationsByUser(userId, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getNotificationsByUserAndStatus")
    class GetNotificationsByUserAndStatus {

        @Test
        @DisplayName("should return paginated notifications for user and status")
        void shouldReturnPaginatedNotificationsForUserAndStatus() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            List<Notification> content = List.of(Notification.builder().id(UUID.randomUUID()).status(NotificationStatus.SENT).build());
            Page<Notification> page = new PageImpl<>(content, pageable, 1);

            given(notificationRepository.findByAlertAlertRuleLocationUserIdAndStatus(userId, NotificationStatus.SENT, pageable))
                    .willReturn(page);

            Page<Notification> result = notificationService.getNotificationsByUserAndStatus(userId, NotificationStatus.SENT, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        }
    }

    @Nested
    @DisplayName("getNotificationsByAlert")
    class GetNotificationsByAlert {

        @Test
        @DisplayName("should return notifications for given alert")
        void shouldReturnNotificationsForAlert() {
            UUID alertId = UUID.randomUUID();
            List<Notification> notifications = List.of(
                    Notification.builder().id(UUID.randomUUID()).channel(NotificationChannel.EMAIL).build(),
                    Notification.builder().id(UUID.randomUUID()).channel(NotificationChannel.DISCORD).build()
            );

            given(notificationRepository.findByAlertId(alertId)).willReturn(notifications);

            List<Notification> result = notificationService.getNotificationsByAlert(alertId);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getNotificationsByStatus")
    class GetNotificationsByStatus {

        @Test
        @DisplayName("should return paginated notifications by status")
        void shouldReturnPaginatedNotificationsByStatus() {
            Pageable pageable = PageRequest.of(0, 5);
            List<Notification> notifications = List.of(
                    Notification.builder().id(UUID.randomUUID()).status(NotificationStatus.PENDING).build()
            );
            Page<Notification> page = new PageImpl<>(notifications, pageable, 1);

            given(notificationRepository.findByStatus(NotificationStatus.PENDING, pageable)).willReturn(page);

            Page<Notification> result = notificationService.getNotificationsByStatus(NotificationStatus.PENDING, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getPendingNotificationsForDispatch")
    class GetPendingNotificationsForDispatch {

        @Test
        @DisplayName("should return pending notifications for dispatch under max attempts")
        void shouldReturnPendingNotificationsForDispatch() {
            List<Notification> pending = List.of(
                    Notification.builder().id(UUID.randomUUID()).attemptCount(0).status(NotificationStatus.PENDING).build(),
                    Notification.builder().id(UUID.randomUUID()).attemptCount(1).status(NotificationStatus.PENDING).build()
            );

            given(notificationRepository.findPendingForDispatch(NotificationStatus.PENDING, 3)).willReturn(pending);

            List<Notification> result = notificationService.getPendingNotificationsForDispatch(3);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("markAsProcessing")
    class MarkAsProcessing {

        @Test
        @DisplayName("should increment attempt count and set status to PROCESSING")
        void shouldIncrementAttemptAndSetStatusProcessing() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder()
                    .id(notifId)
                    .status(NotificationStatus.PENDING)
                    .attemptCount(0)
                    .build();

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.markAsProcessing(notifId);

            assertThat(result.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
            assertThat(result.getAttemptCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("recordSuccess")
    class RecordSuccess {

        @Test
        @DisplayName("should set status to SENT and populate sentAt")
        void shouldSetStatusSentAndPopulateSentAt() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder()
                    .id(notifId)
                    .status(NotificationStatus.PROCESSING)
                    .build();

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.recordSuccess(notifId);

            assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(result.getSentAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailure {

        @Test
        @DisplayName("should set status to FAILED and populate failureReason")
        void shouldSetStatusFailedAndPopulateFailureReason() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder()
                    .id(notifId)
                    .status(NotificationStatus.PROCESSING)
                    .build();

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.recordFailure(notifId, "Connection timeout");

            assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(result.getFailureReason()).isEqualTo("Connection timeout");
        }
    }

    @Nested
    @DisplayName("markAsCancelled")
    class MarkAsCancelled {

        @Test
        @DisplayName("should set status to CANCELLED")
        void shouldSetStatusCancelled() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder()
                    .id(notifId)
                    .status(NotificationStatus.PENDING)
                    .build();

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.markAsCancelled(notifId);

            assertThat(result.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("updateNotificationStatus")
    class UpdateNotificationStatus {

        @Test
        @DisplayName("should update status to requested status")
        void shouldUpdateStatusToRequestedStatus() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder()
                    .id(notifId)
                    .status(NotificationStatus.PENDING)
                    .build();

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.updateNotificationStatus(notifId, NotificationStatus.CANCELLED);

            assertThat(result.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("deleteNotification")
    class DeleteNotification {

        @Test
        @DisplayName("should delete notification when found")
        void shouldDeleteNotificationWhenFound() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder().id(notifId).build();

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));

            notificationService.deleteNotification(notifId);

            then(notificationRepository).should().delete(notification);
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when deleting non-existent notification")
        void shouldThrowWhenDeletingNonExistent() {
            UUID notifId = UUID.randomUUID();

            given(notificationRepository.findById(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.deleteNotification(notifId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());

            then(notificationRepository).should(never()).delete(any(Notification.class));
        }
    }
}
