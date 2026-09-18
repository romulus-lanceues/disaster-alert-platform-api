package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.Alert;
import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
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

    private NotificationResponse buildNotificationResponse(UUID id) {
        return new NotificationResponse(
                id,
                UUID.randomUUID(),
                NotificationChannel.EMAIL,
                "user@example.com",
                NotificationStatus.PENDING,
                0,
                null,
                null,
                Instant.now(),
                Instant.now(),
                AlertStatus.PENDING,
                UUID.randomUUID(),
                "Home",
                DisasterType.EARTHQUAKE,
                UUID.randomUUID(),
                Instant.now(),
                6.2,
                "HIGH",
                10.0,
                14.5995,
                120.9842
        );
    }

    private Notification buildNotification(UUID id, Alert alert, NotificationStatus status, int attemptCount) {
        return Notification.builder()
                .id(id)
                .alert(alert)
                .channel(NotificationChannel.EMAIL)
                .destination("user@example.com")
                .status(status)
                .attemptCount(attemptCount)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("createNotification")
    class CreateNotification {

        @Test
        @DisplayName("should save notification when alert and channel combination does not exist")
        void shouldSaveNotification_whenAlertAndChannelDoNotExist() {

            UUID alertId = UUID.randomUUID();
            Alert alert = Alert.builder().id(alertId).build();
            given(notificationRepository.existsByAlertIdAndChannel(alertId, NotificationChannel.EMAIL))
                    .willReturn(false);

            notificationService.createNotification(alert, NotificationChannel.EMAIL, "user@example.com");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getAlert()).isEqualTo(alert);
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(saved.getDestination()).isEqualTo("user@example.com");
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(saved.getAttemptCount()).isZero();
        }

        @Test
        @DisplayName("should throw NotificationAlreadyExistsException when alert and channel already exist")
        void shouldThrowNotificationAlreadyExistsException_whenAlertAndChannelAlreadyExist() {

            UUID alertId = UUID.randomUUID();
            Alert alert = Alert.builder().id(alertId).build();
            given(notificationRepository.existsByAlertIdAndChannel(alertId, NotificationChannel.EMAIL))
                    .willReturn(true);


            assertThatThrownBy(() -> notificationService.createNotification(alert, NotificationChannel.EMAIL, "user@example.com"))
                    .isInstanceOf(NotificationAlreadyExistsException.class)
                    .hasMessageContaining(alertId.toString())
                    .hasMessageContaining(NotificationChannel.EMAIL.name());

            then(notificationRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("getNotificationForUser")
    class GetNotificationForUser {

        @Test
        @DisplayName("should return projected NotificationResponse when owned by requesting user")
        void shouldReturnNotificationResponse_whenFoundByNotificationIdAndUserId() {
            UUID notifId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            NotificationResponse expectedResponse = buildNotificationResponse(notifId);
            given(notificationRepository.findByIdAndUserId(notifId, userId))
                    .willReturn(Optional.of(expectedResponse));

            NotificationResponse actualResponse = notificationService.getNotificationForUser(notifId, userId);

            assertThat(actualResponse).isNotNull().isEqualTo(expectedResponse);
            assertThat(actualResponse.id()).isEqualTo(notifId);
            assertThat(actualResponse.alertId()).isEqualTo(expectedResponse.alertId());
            assertThat(actualResponse.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(actualResponse.destination()).isEqualTo("user@example.com");
            assertThat(actualResponse.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(actualResponse.attemptCount()).isZero();
            assertThat(actualResponse.locationId()).isEqualTo(expectedResponse.locationId());
            assertThat(actualResponse.locationName()).isEqualTo("Home");
            assertThat(actualResponse.disasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(actualResponse.disasterEventId()).isEqualTo(expectedResponse.disasterEventId());
            assertThat(actualResponse.magnitude()).isEqualTo(6.2);
            assertThat(actualResponse.severity()).isEqualTo("HIGH");
            assertThat(actualResponse.depthKm()).isEqualTo(10.0);
            assertThat(actualResponse.latitude()).isEqualTo(14.5995);
            assertThat(actualResponse.longitude()).isEqualTo(120.9842);
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when notification does not exist or is not owned by user")
        void shouldThrowNotificationNotFoundException_whenNotFoundOrNotOwnedByUser() {
            UUID notifId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            given(notificationRepository.findByIdAndUserId(notifId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getNotificationForUser(notifId, userId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());
        }
    }

    @Nested
    @DisplayName("getNotificationsForUser")
    class GetNotificationsForUser {

        @Test
        @DisplayName("should return paginated notification responses when status filter is provided")
        void shouldReturnPagedNotificationResponses_whenStatusProvided() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            NotificationResponse response = buildNotificationResponse(UUID.randomUUID());
            Page<NotificationResponse> expectedPage = new PageImpl<>(List.of(response), pageable, 1);
            given(notificationRepository.findByUserId(userId, NotificationStatus.PENDING, pageable))
                    .willReturn(expectedPage);

            Page<NotificationResponse> actualPage = notificationService.getNotificationsForUser(userId, NotificationStatus.PENDING, pageable);

            assertThat(actualPage).isNotNull();
            assertThat(actualPage.getContent()).containsExactly(response);
            assertThat(actualPage.getTotalElements()).isEqualTo(1);
            NotificationResponse item = actualPage.getContent().get(0);
            assertThat(item.id()).isEqualTo(response.id());
            assertThat(item.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(item.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(item.destination()).isEqualTo("user@example.com");
            assertThat(item.locationName()).isEqualTo("Home");
            assertThat(item.disasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(item.magnitude()).isEqualTo(6.2);
        }

        @Test
        @DisplayName("should return paginated notification responses across all statuses when status is null")
        void shouldReturnPagedNotificationResponses_whenStatusIsNull() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            NotificationResponse response = buildNotificationResponse(UUID.randomUUID());
            Page<NotificationResponse> expectedPage = new PageImpl<>(List.of(response), pageable, 1);
            given(notificationRepository.findByUserId(userId, null, pageable))
                    .willReturn(expectedPage);

            Page<NotificationResponse> actualPage = notificationService.getNotificationsForUser(userId, null, pageable);

            assertThat(actualPage).isNotNull();
            assertThat(actualPage.getContent()).containsExactly(response);
            assertThat(actualPage.getTotalElements()).isEqualTo(1);
            NotificationResponse item = actualPage.getContent().get(0);
            assertThat(item.id()).isEqualTo(response.id());
            assertThat(item.status()).isEqualTo(NotificationStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("markAsProcessing")
    class MarkAsProcessing {

        @Test
        @DisplayName("should increment attempt count, set status to PROCESSING, and return NotificationStatusResponse")
        void shouldIncrementAttemptAndSetStatusProcessing_whenFound() {
            UUID notifId = UUID.randomUUID();
            UUID alertId = UUID.randomUUID();
            Alert alert = Alert.builder().id(alertId).build();
            Notification notification = buildNotification(notifId, alert, NotificationStatus.PENDING, 0);

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            NotificationStatusResponse result = notificationService.markAsProcessing(notifId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(notifId);
            assertThat(result.alertId()).isEqualTo(alertId);
            assertThat(result.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(result.destination()).isEqualTo("user@example.com");
            assertThat(result.status()).isEqualTo(NotificationStatus.PROCESSING);
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(result.sentAt()).isNull();
            assertThat(result.failureReason()).isNull();
            assertThat(result.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when notification not found")
        void shouldThrowNotificationNotFoundException_whenNotFound() {
            UUID notifId = UUID.randomUUID();
            given(notificationRepository.findById(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsProcessing(notifId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());

            then(notificationRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("recordSuccess")
    class RecordSuccess {

        @Test
        @DisplayName("should set status to SENT, record sentAt timestamp, and return NotificationStatusResponse")
        void shouldSetStatusSentAndPopulateSentAt_whenFound() {
            UUID notifId = UUID.randomUUID();
            UUID alertId = UUID.randomUUID();
            Alert alert = Alert.builder().id(alertId).build();
            Notification notification = buildNotification(notifId, alert, NotificationStatus.PROCESSING, 1);

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            NotificationStatusResponse result = notificationService.recordSuccess(notifId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(notifId);
            assertThat(result.alertId()).isEqualTo(alertId);
            assertThat(result.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(result.destination()).isEqualTo("user@example.com");
            assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(result.sentAt()).isNotNull();
            assertThat(result.failureReason()).isNull();
            assertThat(result.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when notification not found")
        void shouldThrowNotificationNotFoundException_whenNotFound() {
            UUID notifId = UUID.randomUUID();
            given(notificationRepository.findById(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.recordSuccess(notifId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());

            then(notificationRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailure {

        @Test
        @DisplayName("should set status to FAILED, record failureReason, and return NotificationStatusResponse")
        void shouldSetStatusFailedAndPopulateFailureReason_whenFound() {
            UUID notifId = UUID.randomUUID();
            UUID alertId = UUID.randomUUID();
            Alert alert = Alert.builder().id(alertId).build();
            Notification notification = buildNotification(notifId, alert, NotificationStatus.PROCESSING, 1);

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            NotificationStatusResponse result = notificationService.recordFailure(notifId, "Connection timeout");

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(notifId);
            assertThat(result.alertId()).isEqualTo(alertId);
            assertThat(result.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(result.destination()).isEqualTo("user@example.com");
            assertThat(result.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(result.failureReason()).isEqualTo("Connection timeout");
            assertThat(result.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when notification not found")
        void shouldThrowNotificationNotFoundException_whenNotFound() {
            UUID notifId = UUID.randomUUID();
            given(notificationRepository.findById(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.recordFailure(notifId, "Connection timeout"))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());

            then(notificationRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("markAsCancelled")
    class MarkAsCancelled {

        @Test
        @DisplayName("should set status to CANCELLED and return NotificationStatusResponse")
        void shouldSetStatusCancelled_whenFound() {
            UUID notifId = UUID.randomUUID();
            UUID alertId = UUID.randomUUID();
            Alert alert = Alert.builder().id(alertId).build();
            Notification notification = buildNotification(notifId, alert, NotificationStatus.PENDING, 0);

            given(notificationRepository.findById(notifId)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            NotificationStatusResponse result = notificationService.markAsCancelled(notifId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(notifId);
            assertThat(result.alertId()).isEqualTo(alertId);
            assertThat(result.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(result.destination()).isEqualTo("user@example.com");
            assertThat(result.status()).isEqualTo(NotificationStatus.CANCELLED);
            assertThat(result.attemptCount()).isZero();
            assertThat(result.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw NotificationNotFoundException when notification not found")
        void shouldThrowNotificationNotFoundException_whenNotFound() {
            UUID notifId = UUID.randomUUID();
            given(notificationRepository.findById(notifId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsCancelled(notifId))
                    .isInstanceOf(NotificationNotFoundException.class)
                    .hasMessageContaining(notifId.toString());

            then(notificationRepository).should(never()).save(any());
        }
    }
}
