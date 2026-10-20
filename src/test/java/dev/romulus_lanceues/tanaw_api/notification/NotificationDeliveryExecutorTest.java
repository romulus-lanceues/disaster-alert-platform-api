package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDeliveryExecutor Unit Tests")
class NotificationDeliveryExecutorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private ChannelSupportRegistry channelSupportRegistry;

    @Mock
    private AlertMessageFactory alertMessageFactory;

    @Mock
    private NotificationRetryPolicy retryPolicy;

    @Mock
    private NotificationSender discordSender;

    private NotificationDeliveryExecutor executor;

    private UUID notificationId;
    private UUID alertId;
    private UUID endpointId;
    private UUID userId;
    private String destination;
    private AlertMessage mockAlertMessage;

    @BeforeEach
    void setUp() {
        executor = new NotificationDeliveryExecutor(
                deliveryRepository,
                channelSupportRegistry,
                alertMessageFactory,
                retryPolicy,
                clock
        );

        notificationId = UUID.randomUUID();
        alertId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        userId = UUID.randomUUID();
        destination = "https://discord.com/api/webhooks/123456789/secret-webhook-token";

        mockAlertMessage = new AlertMessage(
                "M6.5 earthquake — Manila",
                "Manila, Philippines",
                6.5,
                "M6.5",
                15.0,
                "15 km from Manila",
                "Home",
                10.0,
                "10.0 km",
                "CRITICAL",
                FIXED_NOW.minusSeconds(120),
                "2026-10-20 19:58:00 PHT",
                "2026-10-20 11:58:00 UTC",
                "reviewed",
                null,
                "https://earthquake.usgs.gov/earthquakes/eventpage/us123",
                AlertMessageFactory.DEFAULT_FOOTER
        );
    }

    private NotificationDeliveryContext createValidContext() {
        return new NotificationDeliveryContext(
                notificationId,
                alertId,
                NotificationChannel.DISCORD,
                destination,
                "Discord: ...token",
                endpointId,
                true,
                FIXED_NOW.minusSeconds(3600),
                userId,
                UserStatus.ACTIVE,
                "Home",
                "Manila, Philippines",
                FIXED_NOW.minusSeconds(120),
                "reviewed",
                "CRITICAL",
                "https://earthquake.usgs.gov/earthquakes/eventpage/us123",
                6.5,
                10.0,
                15.0
        );
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when context is null")
        void throwsWhenContextIsNull() {
            assertThatThrownBy(() -> executor.executeDelivery((NotificationDeliveryContext) null, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("NotificationDeliveryContext must not be null");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when claimed notification is null")
        void throwsWhenClaimedNotificationIsNull() {
            NotificationDeliveryContext context = createValidContext();
            assertThatThrownBy(() -> executor.executeDelivery((ClaimedNotification) null, context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ClaimedNotification must not be null");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when notificationId is null")
        void throwsWhenNotificationIdIsNull() {
            NotificationDeliveryContext invalidContext = new NotificationDeliveryContext(
                    null, alertId, NotificationChannel.DISCORD, destination, null,
                    endpointId, true, FIXED_NOW, userId, UserStatus.ACTIVE,
                    "Home", "Manila", FIXED_NOW, "reviewed", "CRITICAL",
                    null, 6.0, 10.0, 15.0
            );

            assertThatThrownBy(() -> executor.executeDelivery(invalidContext, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Notification ID must not be null");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when attempt count is zero or negative")
        void throwsWhenAttemptCountNotPositive() {
            NotificationDeliveryContext context = createValidContext();

            assertThatThrownBy(() -> executor.executeDelivery(context, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attemptCount must be positive");

            assertThatThrownBy(() -> executor.executeDelivery(context, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attemptCount must be positive");
        }
    }

    @Nested
    @DisplayName("Pre-Send Guards")
    class PreSendGuards {

        @Test
        @DisplayName("cancels delivery when endpoint is missing / null")
        void cancelsWhenEndpointMissing() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    notificationId, alertId, NotificationChannel.DISCORD, destination, null,
                    null, null, null, userId, UserStatus.ACTIVE,
                    "Home", "Manila", FIXED_NOW, "reviewed", "CRITICAL",
                    null, 6.0, 10.0, 15.0
            );
            given(deliveryRepository.recordPreSendCancellation(eq(notificationId), eq(1), anyString(), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.CANCELLED);
            then(deliveryRepository).should().recordPreSendCancellation(
                    notificationId, 1, "Endpoint not found or deleted", FIXED_NOW);
            then(channelSupportRegistry).should(never()).getSender(any());
        }

        @Test
        @DisplayName("cancels delivery when endpoint is disabled")
        void cancelsWhenEndpointDisabled() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    notificationId, alertId, NotificationChannel.DISCORD, destination, null,
                    endpointId, false, FIXED_NOW.minusSeconds(3600), userId, UserStatus.ACTIVE,
                    "Home", "Manila", FIXED_NOW, "reviewed", "CRITICAL",
                    null, 6.0, 10.0, 15.0
            );
            given(deliveryRepository.recordPreSendCancellation(eq(notificationId), eq(1), anyString(), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.CANCELLED);
            then(deliveryRepository).should().recordPreSendCancellation(
                    notificationId, 1, "Endpoint is disabled", FIXED_NOW);
            then(channelSupportRegistry).should(never()).getSender(any());
        }

        @Test
        @DisplayName("cancels delivery when endpoint is unverified")
        void cancelsWhenEndpointUnverified() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    notificationId, alertId, NotificationChannel.DISCORD, destination, null,
                    endpointId, true, null, userId, UserStatus.ACTIVE,
                    "Home", "Manila", FIXED_NOW, "reviewed", "CRITICAL",
                    null, 6.0, 10.0, 15.0
            );
            given(deliveryRepository.recordPreSendCancellation(eq(notificationId), eq(1), anyString(), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.CANCELLED);
            then(deliveryRepository).should().recordPreSendCancellation(
                    notificationId, 1, "Endpoint is not verified", FIXED_NOW);
            then(channelSupportRegistry).should(never()).getSender(any());
        }

        @Test
        @DisplayName("cancels delivery when user status is not ACTIVE")
        void cancelsWhenUserNotActive() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    notificationId, alertId, NotificationChannel.DISCORD, destination, null,
                    endpointId, true, FIXED_NOW.minusSeconds(3600), userId, UserStatus.DISABLED,
                    "Home", "Manila", FIXED_NOW, "reviewed", "CRITICAL",
                    null, 6.0, 10.0, 15.0
            );
            given(deliveryRepository.recordPreSendCancellation(eq(notificationId), eq(1), anyString(), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.CANCELLED);
            then(deliveryRepository).should().recordPreSendCancellation(
                    notificationId, 1, "User account is not active", FIXED_NOW);
            then(channelSupportRegistry).should(never()).getSender(any());
        }

        @Test
        @DisplayName("cancels delivery when destination is null or blank")
        void cancelsWhenDestinationMissing() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    notificationId, alertId, NotificationChannel.DISCORD, "   ", null,
                    endpointId, true, FIXED_NOW.minusSeconds(3600), userId, UserStatus.ACTIVE,
                    "Home", "Manila", FIXED_NOW, "reviewed", "CRITICAL",
                    null, 6.0, 10.0, 15.0
            );
            given(deliveryRepository.recordPreSendCancellation(eq(notificationId), eq(1), anyString(), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.CANCELLED);
            then(deliveryRepository).should().recordPreSendCancellation(
                    notificationId, 1, "Notification destination is missing or empty", FIXED_NOW);
            then(channelSupportRegistry).should(never()).getSender(any());
        }

        @Test
        @DisplayName("cancels delivery when channel sender is unsupported")
        void cancelsWhenChannelSenderUnsupported() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(false);
            given(deliveryRepository.recordPreSendCancellation(eq(notificationId), eq(1), anyString(), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.CANCELLED);
            then(deliveryRepository).should().recordPreSendCancellation(
                    notificationId, 1, "Unsupported notification channel: DISCORD", FIXED_NOW);
            then(channelSupportRegistry).should(never()).getSender(any());
        }

        @Test
        @DisplayName("returns FENCE_CONFLICT if recordPreSendCancellation returns false (stale worker)")
        void returnsFenceConflictWhenCancellationFails() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(false);
            given(deliveryRepository.recordPreSendCancellation(eq(notificationId), eq(1), anyString(), eq(FIXED_NOW)))
                    .willReturn(false);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.FENCE_CONFLICT);
        }
    }

    @Nested
    @DisplayName("Successful Dispatch")
    class SuccessfulDispatch {

        @Test
        @DisplayName("renders message, dispatches via sender, and records success")
        void deliversSuccessfully() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination)).willReturn(new DeliveryResult.Sent("provider-msg-999"));
            given(deliveryRepository.recordSuccess(notificationId, 2, "provider-msg-999", FIXED_NOW, FIXED_NOW))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 2);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.SENT);
            then(deliveryRepository).should().recordSuccess(notificationId, 2, "provider-msg-999", FIXED_NOW, FIXED_NOW);
        }

        @Test
        @DisplayName("supports ClaimedNotification overload")
        void supportsClaimedNotificationOverload() {
            NotificationDeliveryContext context = createValidContext();
            ClaimedNotification claimed = new ClaimedNotification(notificationId, 3);
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination)).willReturn(new DeliveryResult.Sent("msg-1"));
            given(deliveryRepository.recordSuccess(notificationId, 3, "msg-1", FIXED_NOW, FIXED_NOW))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(claimed, context);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.SENT);
            then(deliveryRepository).should().recordSuccess(notificationId, 3, "msg-1", FIXED_NOW, FIXED_NOW);
        }

        @Test
        @DisplayName("returns FENCE_CONFLICT when recordSuccess returns false (worker superseded)")
        void returnsFenceConflictWhenRecordSuccessFails() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination)).willReturn(new DeliveryResult.Sent("msg-1"));
            given(deliveryRepository.recordSuccess(notificationId, 1, "msg-1", FIXED_NOW, FIXED_NOW))
                    .willReturn(false);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.FENCE_CONFLICT);
        }
    }

    @Nested
    @DisplayName("Retryable Failures")
    class RetryableFailures {

        @Test
        @DisplayName("calculates backoff and records retryable failure")
        void handlesRetryableFailure() {
            NotificationDeliveryContext context = createValidContext();
            Duration retryAfter = Duration.ofSeconds(30);
            Instant nextAttemptAt = FIXED_NOW.plusSeconds(30);

            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination))
                    .willReturn(new DeliveryResult.Retryable("HTTP 429 Rate limited", retryAfter));
            given(retryPolicy.calculateNextAttempt(1, retryAfter, FIXED_NOW)).willReturn(nextAttemptAt);
            given(retryPolicy.maxAttempts()).willReturn(5);
            given(deliveryRepository.recordRetryableFailure(notificationId, 1, "HTTP 429 Rate limited", nextAttemptAt, 5, FIXED_NOW))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.RETRYABLE);
            then(deliveryRepository).should().recordRetryableFailure(
                    notificationId, 1, "HTTP 429 Rate limited", nextAttemptAt, 5, FIXED_NOW);
        }

        @Test
        @DisplayName("returns FENCE_CONFLICT when recordRetryableFailure returns false")
        void returnsFenceConflictWhenRetryableUpdateFails() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination))
                    .willReturn(new DeliveryResult.Retryable("HTTP 500 Discord Server Error"));
            given(retryPolicy.calculateNextAttempt(1, null, FIXED_NOW)).willReturn(FIXED_NOW.plusSeconds(10));
            given(retryPolicy.maxAttempts()).willReturn(5);
            given(deliveryRepository.recordRetryableFailure(eq(notificationId), eq(1), anyString(), any(), eq(5), eq(FIXED_NOW)))
                    .willReturn(false);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.FENCE_CONFLICT);
        }
    }

    @Nested
    @DisplayName("Permanent Failures")
    class PermanentFailures {

        @Test
        @DisplayName("records permanent failure without endpoint invalidation")
        void handlesPermanentFailureWithoutInvalidation() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination))
                    .willReturn(new DeliveryResult.Permanent("HTTP 400 Bad Request", false));
            given(deliveryRepository.recordPermanentFailure(notificationId, 2, "HTTP 400 Bad Request", FIXED_NOW))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 2);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.PERMANENT_FAILURE);
            then(deliveryRepository).should().recordPermanentFailure(notificationId, 2, "HTTP 400 Bad Request", FIXED_NOW);
            then(deliveryRepository).should(never()).invalidateEndpointAndCancelPending(any(), any(), any());
        }

        @Test
        @DisplayName("records permanent failure with endpoint invalidation and cancels pending notifications")
        void handlesPermanentFailureWithInvalidation() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination))
                    .willReturn(new DeliveryResult.Permanent("HTTP 404 Webhook Not Found", true));
            given(deliveryRepository.recordPermanentFailure(notificationId, 1, "HTTP 404 Webhook Not Found", FIXED_NOW))
                    .willReturn(true);
            given(deliveryRepository.invalidateEndpointAndCancelPending(endpointId, "HTTP 404 Webhook Not Found", FIXED_NOW))
                    .willReturn(3);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.PERMANENT_FAILURE);
            then(deliveryRepository).should().recordPermanentFailure(notificationId, 1, "HTTP 404 Webhook Not Found", FIXED_NOW);
            then(deliveryRepository).should().invalidateEndpointAndCancelPending(endpointId, "HTTP 404 Webhook Not Found", FIXED_NOW);
        }

        @Test
        @DisplayName("does not invalidate endpoint if recordPermanentFailure returns false (stale worker)")
        void doesNotInvalidateEndpointWhenPermanentFailureFencingFails() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);
            given(discordSender.send(mockAlertMessage, destination))
                    .willReturn(new DeliveryResult.Permanent("HTTP 404 Webhook Not Found", true));
            given(deliveryRepository.recordPermanentFailure(notificationId, 1, "HTTP 404 Webhook Not Found", FIXED_NOW))
                    .willReturn(false);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.FENCE_CONFLICT);
            then(deliveryRepository).should(never()).invalidateEndpointAndCancelPending(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Exception Containment & Secret Redaction")
    class ExceptionContainment {

        @Test
        @DisplayName("catches RestClientResponseException and safely records retryable failure with sanitized status code")
        void catchesRestClientResponseExceptionSafely() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);

            // RestClientResponseException typically embeds the request URI with webhook tokens in message
            RestClientResponseException restException = new RestClientResponseException(
                    "502 Bad Gateway: GET https://discord.com/api/webhooks/12345/SUPER_SECRET_TOKEN",
                    HttpStatusCode.valueOf(502),
                    "Bad Gateway",
                    new HttpHeaders(),
                    new byte[0],
                    null
            );
            given(discordSender.send(mockAlertMessage, destination)).willThrow(restException);

            given(retryPolicy.calculateNextAttempt(eq(1), any(), eq(FIXED_NOW))).willReturn(FIXED_NOW.plusSeconds(10));
            given(retryPolicy.maxAttempts()).willReturn(5);
            given(deliveryRepository.recordRetryableFailure(eq(notificationId), eq(1), anyString(), any(), eq(5), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.RETRYABLE);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            then(deliveryRepository).should().recordRetryableFailure(
                    eq(notificationId), eq(1), reasonCaptor.capture(), any(), eq(5), eq(FIXED_NOW));

            String capturedReason = reasonCaptor.getValue();
            assertThat(capturedReason)
                    .doesNotContain("SUPER_SECRET_TOKEN")
                    .doesNotContain("https://")
                    .contains("502");
        }

        @Test
        @DisplayName("catches ResourceAccessException (timeout) and safely records retryable failure")
        void catchesResourceAccessExceptionSafely() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);

            ResourceAccessException timeoutException = new ResourceAccessException("Read timed out to https://discord.com/api/webhooks/123/SECRET");
            given(discordSender.send(mockAlertMessage, destination)).willThrow(timeoutException);

            given(retryPolicy.calculateNextAttempt(eq(1), any(), eq(FIXED_NOW))).willReturn(FIXED_NOW.plusSeconds(10));
            given(retryPolicy.maxAttempts()).willReturn(5);
            given(deliveryRepository.recordRetryableFailure(eq(notificationId), eq(1), anyString(), any(), eq(5), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.RETRYABLE);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            then(deliveryRepository).should().recordRetryableFailure(
                    eq(notificationId), eq(1), reasonCaptor.capture(), any(), eq(5), eq(FIXED_NOW));

            String capturedReason = reasonCaptor.getValue();
            assertThat(capturedReason)
                    .doesNotContain("SECRET")
                    .doesNotContain("https://");
        }

        @Test
        @DisplayName("catches unexpected RuntimeException and safely records retryable failure")
        void catchesUnexpectedRuntimeExceptionSafely() {
            NotificationDeliveryContext context = createValidContext();
            given(channelSupportRegistry.hasSender(NotificationChannel.DISCORD)).willReturn(true);
            given(channelSupportRegistry.getSender(NotificationChannel.DISCORD)).willReturn(discordSender);
            given(alertMessageFactory.create(context)).willReturn(mockAlertMessage);

            given(discordSender.send(mockAlertMessage, destination)).willThrow(new IllegalStateException("Internal error with token secret-123"));

            given(retryPolicy.calculateNextAttempt(eq(1), any(), eq(FIXED_NOW))).willReturn(FIXED_NOW.plusSeconds(10));
            given(retryPolicy.maxAttempts()).willReturn(5);
            given(deliveryRepository.recordRetryableFailure(eq(notificationId), eq(1), anyString(), any(), eq(5), eq(FIXED_NOW)))
                    .willReturn(true);

            DeliveryExecutionOutcome outcome = executor.executeDelivery(context, 1);

            assertThat(outcome).isEqualTo(DeliveryExecutionOutcome.RETRYABLE);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            then(deliveryRepository).should().recordRetryableFailure(
                    eq(notificationId), eq(1), reasonCaptor.capture(), any(), eq(5), eq(FIXED_NOW));

            String capturedReason = reasonCaptor.getValue();
            assertThat(capturedReason)
                    .doesNotContain("secret-123")
                    .contains("IllegalStateException");
        }
    }
}
