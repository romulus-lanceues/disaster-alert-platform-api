package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("ChannelSupportRegistry Unit Tests")
class ChannelSupportRegistryTest {

    private ChannelVerifier discordVerifier;
    private NotificationSender discordSender;
    private ChannelSupportRegistry registry;

    @BeforeEach
    void setUp() {
        discordVerifier = mock(ChannelVerifier.class);
        given(discordVerifier.channel()).willReturn(NotificationChannel.DISCORD);

        discordSender = mock(NotificationSender.class);
        given(discordSender.channel()).willReturn(NotificationChannel.DISCORD);

        registry = new ChannelSupportRegistry(List.of(discordVerifier), List.of(discordSender));
    }

    @Nested
    @DisplayName("ChannelVerifier operations")
    class VerifierOperations {

        @Test
        @DisplayName("isSupported returns true for registered channel and false for unregistered channels")
        void isSupportedChecks() {
            assertThat(registry.isSupported(NotificationChannel.DISCORD)).isTrue();
            assertThat(registry.isSupported(NotificationChannel.EMAIL)).isFalse();
            assertThat(registry.isSupported(NotificationChannel.TELEGRAM)).isFalse();
        }

        @Test
        @DisplayName("assertSupported succeeds for registered channel and throws UnsupportedNotificationChannelException for unregistered")
        void assertSupportedEnforcement() {
            registry.assertSupported(NotificationChannel.DISCORD);

            assertThatThrownBy(() -> registry.assertSupported(NotificationChannel.EMAIL))
                    .isInstanceOf(UnsupportedNotificationChannelException.class)
                    .hasMessageContaining("EMAIL")
                    .hasMessageContaining("not supported");

            assertThatThrownBy(() -> registry.assertSupported(NotificationChannel.TELEGRAM))
                    .isInstanceOf(UnsupportedNotificationChannelException.class)
                    .hasMessageContaining("TELEGRAM");
        }

        @Test
        @DisplayName("getVerifier returns verifier for registered channel and throws for unregistered")
        void getVerifier() {
            ChannelVerifier verifier = registry.getVerifier(NotificationChannel.DISCORD);
            assertThat(verifier).isSameAs(discordVerifier);

            assertThatThrownBy(() -> registry.getVerifier(NotificationChannel.EMAIL))
                    .isInstanceOf(UnsupportedNotificationChannelException.class);
        }

        @Test
        @DisplayName("getSupportedChannels returns unmodifiable set of verifier channels")
        void getSupportedChannels() {
            Set<NotificationChannel> supported = registry.getSupportedChannels();
            assertThat(supported).containsExactly(NotificationChannel.DISCORD);

            assertThatThrownBy(() -> supported.add(NotificationChannel.EMAIL))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("NotificationSender operations")
    class SenderOperations {

        @Test
        @DisplayName("hasSender returns true for registered sender and false for unregistered channels")
        void hasSenderChecks() {
            assertThat(registry.hasSender(NotificationChannel.DISCORD)).isTrue();
            assertThat(registry.hasSender(NotificationChannel.EMAIL)).isFalse();
            assertThat(registry.hasSender(NotificationChannel.TELEGRAM)).isFalse();
        }

        @Test
        @DisplayName("assertSenderSupported succeeds for registered sender and throws UnsupportedNotificationChannelException for unregistered")
        void assertSenderSupportedEnforcement() {
            registry.assertSenderSupported(NotificationChannel.DISCORD);

            assertThatThrownBy(() -> registry.assertSenderSupported(NotificationChannel.EMAIL))
                    .isInstanceOf(UnsupportedNotificationChannelException.class)
                    .hasMessageContaining("EMAIL")
                    .hasMessageContaining("not supported");

            assertThatThrownBy(() -> registry.assertSenderSupported(NotificationChannel.TELEGRAM))
                    .isInstanceOf(UnsupportedNotificationChannelException.class)
                    .hasMessageContaining("TELEGRAM");
        }

        @Test
        @DisplayName("getSender returns sender for registered channel and throws for unregistered")
        void getSender() {
            NotificationSender sender = registry.getSender(NotificationChannel.DISCORD);
            assertThat(sender).isSameAs(discordSender);

            assertThatThrownBy(() -> registry.getSender(NotificationChannel.EMAIL))
                    .isInstanceOf(UnsupportedNotificationChannelException.class)
                    .hasMessageContaining("EMAIL");
        }

        @Test
        @DisplayName("getSupportedSenderChannels returns unmodifiable set of sender channels")
        void getSupportedSenderChannels() {
            Set<NotificationChannel> senderChannels = registry.getSupportedSenderChannels();
            assertThat(senderChannels).containsExactly(NotificationChannel.DISCORD);

            assertThatThrownBy(() -> senderChannels.add(NotificationChannel.EMAIL))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getSupportedSenders returns unmodifiable map of registered senders")
        void getSupportedSenders() {
            Map<NotificationChannel, NotificationSender> senders = registry.getSupportedSenders();
            assertThat(senders).containsEntry(NotificationChannel.DISCORD, discordSender);

            NotificationSender mockEmailSender = mock(NotificationSender.class);
            assertThatThrownBy(() -> senders.put(NotificationChannel.EMAIL, mockEmailSender))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Decoupled capabilities and constructor edge cases")
    class DecoupledCapabilitiesAndEdgeCases {

        @Test
        @DisplayName("Supports channel having verifier but no sender, or sender but no verifier")
        void decoupledCapabilities() {
            ChannelVerifier emailVerifier = mock(ChannelVerifier.class);
            given(emailVerifier.channel()).willReturn(NotificationChannel.EMAIL);

            NotificationSender telegramSender = mock(NotificationSender.class);
            given(telegramSender.channel()).willReturn(NotificationChannel.TELEGRAM);

            ChannelSupportRegistry customRegistry = new ChannelSupportRegistry(
                    List.of(emailVerifier),
                    List.of(telegramSender)
            );

            // EMAIL has verifier only
            assertThat(customRegistry.isSupported(NotificationChannel.EMAIL)).isTrue();
            assertThat(customRegistry.hasSender(NotificationChannel.EMAIL)).isFalse();

            // TELEGRAM has sender only
            assertThat(customRegistry.isSupported(NotificationChannel.TELEGRAM)).isFalse();
            assertThat(customRegistry.hasSender(NotificationChannel.TELEGRAM)).isTrue();

            // DISCORD has neither
            assertThat(customRegistry.isSupported(NotificationChannel.DISCORD)).isFalse();
            assertThat(customRegistry.hasSender(NotificationChannel.DISCORD)).isFalse();
        }

        @Test
        @DisplayName("Null lists passed to constructor are safely treated as empty collections")
        void nullListsHandledSafely() {
            ChannelSupportRegistry emptyRegistry = new ChannelSupportRegistry(null, null);

            assertThat(emptyRegistry.getSupportedChannels()).isEmpty();
            assertThat(emptyRegistry.getSupportedSenderChannels()).isEmpty();
            assertThat(emptyRegistry.getSupportedSenders()).isEmpty();
            assertThat(emptyRegistry.isSupported(NotificationChannel.DISCORD)).isFalse();
            assertThat(emptyRegistry.hasSender(NotificationChannel.DISCORD)).isFalse();
        }

        @Test
        @DisplayName("Duplicate senders for same channel throws IllegalStateException")
        void duplicateSendersThrowsException() {
            NotificationSender sender1 = mock(NotificationSender.class);
            given(sender1.channel()).willReturn(NotificationChannel.DISCORD);

            NotificationSender sender2 = mock(NotificationSender.class);
            given(sender2.channel()).willReturn(NotificationChannel.DISCORD);

            assertThatThrownBy(() -> new ChannelSupportRegistry(List.of(), List.of(sender1, sender2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Duplicate key");
        }

        @Test
        @DisplayName("Duplicate verifiers for same channel throws IllegalStateException")
        void duplicateVerifiersThrowsException() {
            ChannelVerifier verifier1 = mock(ChannelVerifier.class);
            given(verifier1.channel()).willReturn(NotificationChannel.DISCORD);

            ChannelVerifier verifier2 = mock(ChannelVerifier.class);
            given(verifier2.channel()).willReturn(NotificationChannel.DISCORD);

            assertThatThrownBy(() -> new ChannelSupportRegistry(List.of(verifier1, verifier2), List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Duplicate key");
        }
    }
}
