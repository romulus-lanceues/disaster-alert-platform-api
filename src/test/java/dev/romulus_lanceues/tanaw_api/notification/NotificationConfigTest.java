package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification Spring Context Wiring Tests")
class NotificationConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    NotificationConfig.class,
                    DiscordTestSender.class,
                    DiscordDestinationValidator.class,
                    DestinationMasker.class,
                    EndpointTestRateLimiter.class,
                    ChannelSupportRegistry.class
            )
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues(
                    "notification.dry-run=true",
                    "notification.dispatcher.enabled=true",
                    "notification.dispatcher.interval=PT5S",
                    "notification.dispatcher.batch-size=50",
                    "notification.dispatcher.lease=PT2M",
                    "notification.retry.max-attempts=5",
                    "notification.retry.initial-backoff=PT10S",
                    "notification.retry.max-backoff=PT10M",
                    "notification.fanout.max-event-age=PT6H",
                    "notification.discord.connect-timeout=PT3S",
                    "notification.discord.read-timeout=PT10S",
                    "notification.crypto.key=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
            );

    @Test
    @DisplayName("Successfully creates all notification beans with single constructors without DI errors")
    void contextLoadsBeansSuccessfully() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("discordRestClient");
            assertThat(context).getBean("discordRestClient").isInstanceOf(RestClient.class);
            assertThat(context).hasSingleBean(DiscordTestSender.class);
            assertThat(context).hasSingleBean(EndpointTestRateLimiter.class);
            assertThat(context).hasSingleBean(ChannelSupportRegistry.class);

            ChannelSupportRegistry registry = context.getBean(ChannelSupportRegistry.class);
            assertThat(registry.isSupported(NotificationChannel.DISCORD)).isTrue();
            assertThat(registry.isSupported(NotificationChannel.EMAIL)).isFalse();
        });
    }
}
