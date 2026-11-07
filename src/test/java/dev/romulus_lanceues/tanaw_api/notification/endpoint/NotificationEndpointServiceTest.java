package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import dev.romulus_lanceues.tanaw_api.notification.NotificationRepository;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelSupportRegistry;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelVerifier;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.UnsupportedNotificationChannelException;
import dev.romulus_lanceues.tanaw_api.notification.channel.discord.DiscordDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.email.EmailDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.telegram.TelegramDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEndpointService Unit Tests")
class NotificationEndpointServiceTest {

    @Mock
    private NotificationEndpointRepository notificationEndpointRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChannelSupportRegistry channelSupportRegistry;

    @Mock
    private DiscordDestinationValidator discordDestinationValidator;

    @Mock
    private TelegramDestinationValidator telegramDestinationValidator;

    @Mock
    private EmailDestinationValidator emailDestinationValidator;

    @Mock
    private DestinationMasker destinationMasker;

    @Mock
    private EndpointTestRateLimiter endpointTestRateLimiter;

    @Mock
    private ChannelVerifier discordChannelVerifier;

    @Mock
    private ChannelVerifier telegramChannelVerifier;

    private final Instant now = Instant.parse("2026-10-16T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private NotificationEndpointService service;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID endpointId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new NotificationEndpointService(
                notificationEndpointRepository,
                notificationRepository,
                userRepository,
                channelSupportRegistry,
                discordDestinationValidator,
                telegramDestinationValidator,
                emailDestinationValidator,
                destinationMasker,
                endpointTestRateLimiter,
                clock
        );

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("listEndpoints")
    class ListEndpoints {

        @Test
        @DisplayName("returns mapped endpoints when user exists")
        void returnsMappedEndpointsWhenUserExists() {
            given(userRepository.existsById(userId)).willReturn(true);

            NotificationEndpoint endpoint1 = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/123/token1")
                    .destinationHint("Discord webhook ...ken1")
                    .enabled(true)
                    .verifiedAt(now)
                    .createdAt(now.minusSeconds(60))
                    .updatedAt(now.minusSeconds(30))
                    .build();

            given(notificationEndpointRepository.findByUserIdOrderByCreatedAtAsc(userId))
                    .willReturn(List.of(endpoint1));

            List<NotificationEndpointResponse> results = service.listEndpoints(userId);

            assertThat(results).hasSize(1);
            NotificationEndpointResponse resp = results.get(0);
            assertThat(resp.id()).isEqualTo(endpointId);
            assertThat(resp.userId()).isEqualTo(userId);
            assertThat(resp.channel()).isEqualTo(NotificationChannel.DISCORD);
            assertThat(resp.destinationHint()).isEqualTo("Discord webhook ...ken1");
            assertThat(resp.enabled()).isTrue();
            assertThat(resp.verifiedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("throws UserNotFoundException when user does not exist")
        void throwsUserNotFoundExceptionWhenUserNotFound() {
            given(userRepository.existsById(userId)).willReturn(false);

            assertThatThrownBy(() -> service.listEndpoints(userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(userId.toString());

            then(notificationEndpointRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("getEndpoint")
    class GetEndpoint {

        @Test
        @DisplayName("returns mapped endpoint when owned by user")
        void returnsEndpointWhenFoundAndOwned() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/123/token")
                    .destinationHint("Discord webhook ...oken")
                    .enabled(true)
                    .verifiedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));

            NotificationEndpointResponse response = service.getEndpoint(userId, endpointId);

            assertThat(response.id()).isEqualTo(endpointId);
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.channel()).isEqualTo(NotificationChannel.DISCORD);
        }

        @Test
        @DisplayName("throws NotificationEndpointNotFoundException when not found or user mismatch (IDOR safe)")
        void throwsNotFoundWhenIdOrUserMismatch() {
            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEndpoint(userId, endpointId))
                    .isInstanceOf(NotificationEndpointNotFoundException.class)
                    .hasMessageContaining(endpointId.toString());
        }
    }

    @Nested
    @DisplayName("upsertEndpoint")
    class UpsertEndpoint {

        private final String validDiscordUrl = "https://discord.com/api/webhooks/123456789/abcdef-1234";
        private final String maskedDiscordHint = "Discord webhook ...1234";

        @Test
        @DisplayName("creates new endpoint with verifiedAt=null when not existing")
        void createsNewEndpointWhenNotExisting() {
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.DISCORD);
            willDoNothing().given(discordDestinationValidator).validate(validDiscordUrl);
            given(destinationMasker.mask(NotificationChannel.DISCORD, validDiscordUrl))
                    .willReturn(maskedDiscordHint);
            given(notificationEndpointRepository.findByUserIdAndChannel(userId, NotificationChannel.DISCORD))
                    .willReturn(Optional.empty());

            ArgumentCaptor<NotificationEndpoint> captor = ArgumentCaptor.forClass(NotificationEndpoint.class);
            given(notificationEndpointRepository.save(captor.capture()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            NotificationEndpointResponse response = service.upsertEndpoint(userId, NotificationChannel.DISCORD, validDiscordUrl);

            assertThat(response.channel()).isEqualTo(NotificationChannel.DISCORD);
            assertThat(response.destinationHint()).isEqualTo(maskedDiscordHint);
            assertThat(response.enabled()).isTrue();
            assertThat(response.verifiedAt()).isNull();

            NotificationEndpoint saved = captor.getValue();
            assertThat(saved.getUser()).isEqualTo(testUser);
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.DISCORD);
            assertThat(saved.getDestination()).isEqualTo(validDiscordUrl);
            assertThat(saved.getDestinationHint()).isEqualTo(maskedDiscordHint);
            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.getVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("updates existing endpoint destination and resets verifiedAt to null")
        void updatesExistingEndpointAndResetsVerifiedAt() {
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.DISCORD);
            willDoNothing().given(discordDestinationValidator).validate(validDiscordUrl);
            given(destinationMasker.mask(NotificationChannel.DISCORD, validDiscordUrl))
                    .willReturn(maskedDiscordHint);

            NotificationEndpoint existingEndpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/old/token")
                    .destinationHint("Discord webhook ...old")
                    .enabled(true)
                    .verifiedAt(now.minusSeconds(3600))
                    .build();

            given(notificationEndpointRepository.findByUserIdAndChannel(userId, NotificationChannel.DISCORD))
                    .willReturn(Optional.of(existingEndpoint));
            given(notificationEndpointRepository.save(existingEndpoint)).willReturn(existingEndpoint);

            NotificationEndpointResponse response = service.upsertEndpoint(userId, NotificationChannel.DISCORD, validDiscordUrl);

            assertThat(existingEndpoint.getDestination()).isEqualTo(validDiscordUrl);
            assertThat(existingEndpoint.getDestinationHint()).isEqualTo(maskedDiscordHint);
            assertThat(existingEndpoint.getVerifiedAt()).isNull();
            assertThat(response.verifiedAt()).isNull();
            then(notificationEndpointRepository).should().save(existingEndpoint);
        }

        @Test
        @DisplayName("throws UserNotFoundException when user does not exist")
        void throwsUserNotFoundException() {
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.upsertEndpoint(userId, NotificationChannel.DISCORD, validDiscordUrl))
                    .isInstanceOf(UserNotFoundException.class);

            then(notificationEndpointRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("throws UnsupportedNotificationChannelException when channel is unsupported")
        void throwsUnsupportedNotificationChannelException() {
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willThrow(new UnsupportedNotificationChannelException(NotificationChannel.TELEGRAM))
                    .given(channelSupportRegistry).assertSupported(NotificationChannel.TELEGRAM);

            assertThatThrownBy(() -> service.upsertEndpoint(userId, NotificationChannel.TELEGRAM, "12345"))
                    .isInstanceOf(UnsupportedNotificationChannelException.class);

            then(notificationEndpointRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidDestinationException when destination format is invalid")
        void throwsInvalidDestinationException() {
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.DISCORD);
            willThrow(new InvalidDestinationException("Invalid Discord webhook URI"))
                    .given(discordDestinationValidator).validate("http://bad-url");

            assertThatThrownBy(() -> service.upsertEndpoint(userId, NotificationChannel.DISCORD, "http://bad-url"))
                    .isInstanceOf(InvalidDestinationException.class);

            then(notificationEndpointRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("creates new Telegram endpoint with verifiedAt=null when not existing")
        void createsNewTelegramEndpointWhenNotExisting() {
            String validTelegramChatId = "987654321";
            String maskedTelegramHint = "Telegram chat ...4321";

            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.TELEGRAM);
            willDoNothing().given(telegramDestinationValidator).validate(validTelegramChatId);
            given(destinationMasker.mask(NotificationChannel.TELEGRAM, validTelegramChatId))
                    .willReturn(maskedTelegramHint);
            given(notificationEndpointRepository.findByUserIdAndChannel(userId, NotificationChannel.TELEGRAM))
                    .willReturn(Optional.empty());

            ArgumentCaptor<NotificationEndpoint> captor = ArgumentCaptor.forClass(NotificationEndpoint.class);
            given(notificationEndpointRepository.save(captor.capture()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            NotificationEndpointResponse response = service.upsertEndpoint(userId, NotificationChannel.TELEGRAM, validTelegramChatId);

            assertThat(response.channel()).isEqualTo(NotificationChannel.TELEGRAM);
            assertThat(response.destinationHint()).isEqualTo(maskedTelegramHint);
            assertThat(response.enabled()).isTrue();
            assertThat(response.verifiedAt()).isNull();

            NotificationEndpoint saved = captor.getValue();
            assertThat(saved.getUser()).isEqualTo(testUser);
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.TELEGRAM);
            assertThat(saved.getDestination()).isEqualTo(validTelegramChatId);
            assertThat(saved.getDestinationHint()).isEqualTo(maskedTelegramHint);
            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.getVerifiedAt()).isNull();

            then(discordDestinationValidator).shouldHaveNoInteractions();
            then(telegramDestinationValidator).should().validate(validTelegramChatId);
        }

        @Test
        @DisplayName("updates existing Telegram endpoint destination and resets verifiedAt to null")
        void updatesExistingTelegramEndpointAndResetsVerifiedAt() {
            String validTelegramChatId = "987654321";
            String maskedTelegramHint = "Telegram chat ...4321";

            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.TELEGRAM);
            willDoNothing().given(telegramDestinationValidator).validate(validTelegramChatId);
            given(destinationMasker.mask(NotificationChannel.TELEGRAM, validTelegramChatId))
                    .willReturn(maskedTelegramHint);

            NotificationEndpoint existingEndpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.TELEGRAM)
                    .destination("123456789")
                    .destinationHint("Telegram chat ...6789")
                    .enabled(true)
                    .verifiedAt(now.minusSeconds(3600))
                    .build();

            given(notificationEndpointRepository.findByUserIdAndChannel(userId, NotificationChannel.TELEGRAM))
                    .willReturn(Optional.of(existingEndpoint));
            given(notificationEndpointRepository.save(existingEndpoint)).willReturn(existingEndpoint);

            NotificationEndpointResponse response = service.upsertEndpoint(userId, NotificationChannel.TELEGRAM, validTelegramChatId);

            assertThat(existingEndpoint.getDestination()).isEqualTo(validTelegramChatId);
            assertThat(existingEndpoint.getDestinationHint()).isEqualTo(maskedTelegramHint);
            assertThat(existingEndpoint.getVerifiedAt()).isNull();
            assertThat(response.verifiedAt()).isNull();
            then(notificationEndpointRepository).should().save(existingEndpoint);
            then(discordDestinationValidator).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("throws InvalidDestinationException when Telegram destination format is invalid")
        void throwsInvalidDestinationExceptionForInvalidTelegramDestination() {
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.TELEGRAM);
            willThrow(new InvalidDestinationException("Telegram destination must be a valid numeric chat ID or @username"))
                    .given(telegramDestinationValidator).validate("invalid-chat-id");

            assertThatThrownBy(() -> service.upsertEndpoint(userId, NotificationChannel.TELEGRAM, "invalid-chat-id"))
                    .isInstanceOf(InvalidDestinationException.class);

            then(notificationEndpointRepository).should(never()).save(any());
            then(discordDestinationValidator).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("creates new Email endpoint with verifiedAt=null when not existing")
        void createsNewEmailEndpointWhenNotExisting() {
            String validEmail = "alerts@tanaw.dev";
            String maskedEmailHint = "a***@tanaw.dev";

            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.EMAIL);
            willDoNothing().given(emailDestinationValidator).validate(validEmail);
            given(destinationMasker.mask(NotificationChannel.EMAIL, validEmail))
                    .willReturn(maskedEmailHint);
            given(notificationEndpointRepository.findByUserIdAndChannel(userId, NotificationChannel.EMAIL))
                    .willReturn(Optional.empty());

            ArgumentCaptor<NotificationEndpoint> captor = ArgumentCaptor.forClass(NotificationEndpoint.class);
            given(notificationEndpointRepository.save(captor.capture()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            NotificationEndpointResponse response = service.upsertEndpoint(userId, NotificationChannel.EMAIL, validEmail);

            assertThat(response.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(response.destinationHint()).isEqualTo(maskedEmailHint);
            assertThat(response.enabled()).isTrue();
            assertThat(response.verifiedAt()).isNull();

            NotificationEndpoint saved = captor.getValue();
            assertThat(saved.getUser()).isEqualTo(testUser);
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(saved.getDestination()).isEqualTo(validEmail);
            assertThat(saved.getDestinationHint()).isEqualTo(maskedEmailHint);
            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.getVerifiedAt()).isNull();

            then(emailDestinationValidator).should().validate(validEmail);
            then(discordDestinationValidator).shouldHaveNoInteractions();
            then(telegramDestinationValidator).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("updates existing Email endpoint destination and resets verifiedAt to null")
        void updatesExistingEmailEndpointAndResetsVerifiedAt() {
            String validEmail = "new-email@tanaw.dev";
            String maskedEmailHint = "n***@tanaw.dev";

            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.EMAIL);
            willDoNothing().given(emailDestinationValidator).validate(validEmail);
            given(destinationMasker.mask(NotificationChannel.EMAIL, validEmail))
                    .willReturn(maskedEmailHint);

            NotificationEndpoint existingEndpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.EMAIL)
                    .destination("old-email@tanaw.dev")
                    .destinationHint("o***@tanaw.dev")
                    .enabled(true)
                    .verifiedAt(now.minusSeconds(3600))
                    .build();

            given(notificationEndpointRepository.findByUserIdAndChannel(userId, NotificationChannel.EMAIL))
                    .willReturn(Optional.of(existingEndpoint));
            given(notificationEndpointRepository.save(existingEndpoint)).willReturn(existingEndpoint);

            NotificationEndpointResponse response = service.upsertEndpoint(userId, NotificationChannel.EMAIL, validEmail);

            assertThat(existingEndpoint.getDestination()).isEqualTo(validEmail);
            assertThat(existingEndpoint.getDestinationHint()).isEqualTo(maskedEmailHint);
            assertThat(existingEndpoint.getVerifiedAt()).isNull();
            assertThat(response.verifiedAt()).isNull();
            then(notificationEndpointRepository).should().save(existingEndpoint);
            then(emailDestinationValidator).should().validate(validEmail);
            then(discordDestinationValidator).shouldHaveNoInteractions();
            then(telegramDestinationValidator).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("throws InvalidDestinationException when Email destination format is invalid")
        void throwsInvalidDestinationExceptionForInvalidEmailDestination() {
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.EMAIL);
            willThrow(new InvalidDestinationException("Email destination must not contain whitespace"))
                    .given(emailDestinationValidator).validate("invalid email@test.com");

            assertThatThrownBy(() -> service.upsertEndpoint(userId, NotificationChannel.EMAIL, "invalid email@test.com"))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("whitespace");

            then(notificationEndpointRepository).should(never()).save(any());
            then(discordDestinationValidator).shouldHaveNoInteractions();
            then(telegramDestinationValidator).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("toggleEndpoint")
    class ToggleEndpoint {

        @Test
        @DisplayName("disabling endpoint sets enabled=false, records reason, and atomically cancels pending notifications in same transaction")
        void disablingEndpointCancelsPendingNotifications() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/tok")
                    .destinationHint("Discord webhook ...tok")
                    .enabled(true)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            given(notificationRepository.cancelPendingByEndpointId(endpointId, "Endpoint disabled"))
                    .willReturn(3);
            given(notificationEndpointRepository.save(endpoint)).willReturn(endpoint);

            NotificationEndpointResponse response = service.toggleEndpoint(userId, endpointId, false);

            assertThat(response.enabled()).isFalse();
            assertThat(endpoint.isEnabled()).isFalse();
            assertThat(endpoint.getDisabledReason()).isEqualTo("User disabled endpoint");
            then(notificationRepository).should().cancelPendingByEndpointId(endpointId, "Endpoint disabled");
            then(notificationEndpointRepository).should().save(endpoint);
        }

        @Test
        @DisplayName("enabling endpoint sets enabled=true, clears reason, and does not cancel notifications")
        void enablingEndpointClearsReasonWithoutCancelling() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/tok")
                    .destinationHint("Discord webhook ...tok")
                    .enabled(false)
                    .disabledReason("User disabled endpoint")
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            given(notificationEndpointRepository.save(endpoint)).willReturn(endpoint);

            NotificationEndpointResponse response = service.toggleEndpoint(userId, endpointId, true);

            assertThat(response.enabled()).isTrue();
            assertThat(endpoint.isEnabled()).isTrue();
            assertThat(endpoint.getDisabledReason()).isNull();
            then(notificationRepository).shouldHaveNoInteractions();
            then(notificationEndpointRepository).should().save(endpoint);
        }

        @Test
        @DisplayName("throws NotificationEndpointNotFoundException when endpoint not found or owned by different user (IDOR safe)")
        void throwsNotFoundOnUserMismatch() {
            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleEndpoint(userId, endpointId, false))
                    .isInstanceOf(NotificationEndpointNotFoundException.class);

            then(notificationRepository).shouldHaveNoInteractions();
            then(notificationEndpointRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteEndpoint")
    class DeleteEndpoint {

        @Test
        @DisplayName("atomically cancels pending notifications and deletes endpoint in same transaction")
        void deletesEndpointAndCancelsPendingNotifications() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/tok")
                    .destinationHint("Discord webhook ...tok")
                    .enabled(true)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            given(notificationRepository.cancelPendingByEndpointId(endpointId, "Endpoint deleted"))
                    .willReturn(5);

            service.deleteEndpoint(userId, endpointId);

            then(notificationRepository).should().cancelPendingByEndpointId(endpointId, "Endpoint deleted");
            then(notificationEndpointRepository).should().delete(endpoint);
        }

        @Test
        @DisplayName("throws NotificationEndpointNotFoundException when endpoint not found or owned by different user (IDOR safe)")
        void throwsNotFoundOnUserMismatch() {
            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteEndpoint(userId, endpointId))
                    .isInstanceOf(NotificationEndpointNotFoundException.class);

            then(notificationRepository).shouldHaveNoInteractions();
            then(notificationEndpointRepository).should(never()).delete(any(NotificationEndpoint.class));
        }
    }

    @Nested
    @DisplayName("testEndpoint")
    class TestEndpoint {

        @Test
        @DisplayName("acquires rate limiter, verifies via channel verifier, and marks endpoint verified on success")
        void verifiesAndMarksVerifiedOnSuccess() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/tok")
                    .destinationHint("Discord webhook ...tok")
                    .enabled(true)
                    .verifiedAt(null)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.DISCORD);
            willDoNothing().given(endpointTestRateLimiter).acquire(userId);
            given(channelSupportRegistry.getVerifier(NotificationChannel.DISCORD))
                    .willReturn(discordChannelVerifier);
            willDoNothing().given(discordChannelVerifier).verify(endpoint.getDestination());
            given(notificationEndpointRepository.save(endpoint)).willReturn(endpoint);

            NotificationEndpointResponse response = service.testEndpoint(userId, endpointId);

            assertThat(endpoint.getVerifiedAt()).isEqualTo(now);
            assertThat(response.verifiedAt()).isEqualTo(now);
            then(endpointTestRateLimiter).should().acquire(userId);
            then(discordChannelVerifier).should().verify(endpoint.getDestination());
            then(notificationEndpointRepository).should().save(endpoint);
        }

        @Test
        @DisplayName("throws RateLimitExceededException and does not invoke verifier when rate limit exceeded")
        void throwsRateLimitExceededException() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/tok")
                    .destinationHint("Discord webhook ...tok")
                    .enabled(true)
                    .verifiedAt(null)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.DISCORD);
            willThrow(new RateLimitExceededException("Rate limit exceeded", Duration.ofSeconds(30)))
                    .given(endpointTestRateLimiter).acquire(userId);

            assertThatThrownBy(() -> service.testEndpoint(userId, endpointId))
                    .isInstanceOf(RateLimitExceededException.class);

            assertThat(endpoint.getVerifiedAt()).isNull();
            then(discordChannelVerifier).shouldHaveNoInteractions();
            then(notificationEndpointRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("throws EndpointVerificationException and does not mark verified when verifier fails")
        void doesNotMarkVerifiedWhenVerifierFails() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/tok")
                    .destinationHint("Discord webhook ...tok")
                    .enabled(true)
                    .verifiedAt(null)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.DISCORD);
            willDoNothing().given(endpointTestRateLimiter).acquire(userId);
            given(channelSupportRegistry.getVerifier(NotificationChannel.DISCORD))
                    .willReturn(discordChannelVerifier);
            willThrow(new EndpointVerificationException("Discord webhook verification failed: HTTP 404"))
                    .given(discordChannelVerifier).verify(endpoint.getDestination());

            assertThatThrownBy(() -> service.testEndpoint(userId, endpointId))
                    .isInstanceOf(EndpointVerificationException.class)
                    .hasMessageContaining("HTTP 404");

            assertThat(endpoint.getVerifiedAt()).isNull();
            then(notificationEndpointRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("acquires rate limiter, verifies Telegram via channel verifier, and marks endpoint verified on success")
        void verifiesAndMarksTelegramEndpointVerifiedOnSuccess() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.TELEGRAM)
                    .destination("987654321")
                    .destinationHint("Telegram chat ...4321")
                    .enabled(true)
                    .verifiedAt(null)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.TELEGRAM);
            willDoNothing().given(endpointTestRateLimiter).acquire(userId);
            given(channelSupportRegistry.getVerifier(NotificationChannel.TELEGRAM))
                    .willReturn(telegramChannelVerifier);
            willDoNothing().given(telegramChannelVerifier).verify(endpoint.getDestination());
            given(notificationEndpointRepository.save(endpoint)).willReturn(endpoint);

            NotificationEndpointResponse response = service.testEndpoint(userId, endpointId);

            assertThat(endpoint.getVerifiedAt()).isEqualTo(now);
            assertThat(response.verifiedAt()).isEqualTo(now);
            then(endpointTestRateLimiter).should().acquire(userId);
            then(telegramChannelVerifier).should().verify(endpoint.getDestination());
            then(notificationEndpointRepository).should().save(endpoint);
        }

        @Test
        @DisplayName("throws EndpointVerificationException and does not mark verified when Telegram verifier fails")
        void doesNotMarkVerifiedWhenTelegramVerifierFails() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .id(endpointId)
                    .user(testUser)
                    .channel(NotificationChannel.TELEGRAM)
                    .destination("987654321")
                    .destinationHint("Telegram chat ...4321")
                    .enabled(true)
                    .verifiedAt(null)
                    .build();

            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.of(endpoint));
            willDoNothing().given(channelSupportRegistry).assertSupported(NotificationChannel.TELEGRAM);
            willDoNothing().given(endpointTestRateLimiter).acquire(userId);
            given(channelSupportRegistry.getVerifier(NotificationChannel.TELEGRAM))
                    .willReturn(telegramChannelVerifier);
            willThrow(new EndpointVerificationException("Telegram verification failed: HTTP 400"))
                    .given(telegramChannelVerifier).verify(endpoint.getDestination());

            assertThatThrownBy(() -> service.testEndpoint(userId, endpointId))
                    .isInstanceOf(EndpointVerificationException.class)
                    .hasMessageContaining("HTTP 400");

            assertThat(endpoint.getVerifiedAt()).isNull();
            then(notificationEndpointRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("throws NotificationEndpointNotFoundException when endpoint not found or owned by different user (IDOR safe)")
        void throwsNotFoundOnUserMismatch() {
            given(notificationEndpointRepository.findByIdAndUserId(endpointId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.testEndpoint(userId, endpointId))
                    .isInstanceOf(NotificationEndpointNotFoundException.class);

            then(channelSupportRegistry).shouldHaveNoInteractions();
            then(endpointTestRateLimiter).shouldHaveNoInteractions();
            then(notificationEndpointRepository).should(never()).save(any());
        }
    }
}
