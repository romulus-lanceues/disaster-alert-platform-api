package dev.romulus_lanceues.tanaw_api.notification.channel;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ChannelSupportRegistryIntegrationTest {

    @ParameterizedTest
    @EnumSource(NotificationChannel.class)
    void discoversOnlyTheChannelBeansPresentInTheContext(NotificationChannel omitted) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(ChannelSupportRegistry.class);
        for (NotificationChannel channel : NotificationChannel.values()) {
            if (channel == omitted) {
                continue;
            }
            ChannelVerifier verifier = mock(ChannelVerifier.class);
            NotificationSender sender = mock(NotificationSender.class);
            given(verifier.channel()).willReturn(channel);
            given(sender.channel()).willReturn(channel);
            runner = runner.withBean(channel.name() + "Verifier", ChannelVerifier.class, () -> verifier)
                    .withBean(channel.name() + "Sender", NotificationSender.class, () -> sender);
        }

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ChannelSupportRegistry registry = context.getBean(ChannelSupportRegistry.class);
            assertThat(registry.getSupportedChannels()).hasSize(2).doesNotContain(omitted);
            assertThat(registry.getSupportedSenderChannels()).hasSize(2).doesNotContain(omitted);
            for (NotificationChannel channel : NotificationChannel.values()) {
                if (channel != omitted) {
                    assertThat(registry.getVerifier(channel)).isSameAs(context.getBean(channel.name() + "Verifier"));
                    assertThat(registry.getSender(channel)).isSameAs(context.getBean(channel.name() + "Sender"));
                }
            }
            assertThatThrownBy(() -> registry.getVerifier(omitted))
                    .isInstanceOf(UnsupportedNotificationChannelException.class);
            assertThatThrownBy(() -> registry.getSender(omitted))
                    .isInstanceOf(UnsupportedNotificationChannelException.class);
        });
    }
}
