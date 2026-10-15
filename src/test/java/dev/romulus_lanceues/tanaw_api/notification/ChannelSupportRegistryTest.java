package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("ChannelSupportRegistry Unit Tests")
class ChannelSupportRegistryTest {

    private ChannelVerifier discordVerifier;
    private ChannelSupportRegistry registry;

    @BeforeEach
    void setUp() {
        discordVerifier = mock(ChannelVerifier.class);
        given(discordVerifier.channel()).willReturn(NotificationChannel.DISCORD);

        registry = new ChannelSupportRegistry(List.of(discordVerifier));
    }

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
    @DisplayName("getSupportedChannels returns only registered channels")
    void getSupportedChannels() {
        Set<NotificationChannel> supported = registry.getSupportedChannels();
        assertThat(supported).containsExactly(NotificationChannel.DISCORD);
    }
}
