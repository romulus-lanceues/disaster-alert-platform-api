package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.BDDMockito.given;

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
                "HIGH"
        );
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
            assertThat(actualResponse.severity()).isEqualTo("HIGH");
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
            assertThat(item.severity()).isEqualTo("HIGH");
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
}
