package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelSupportRegistry;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.discord.DiscordDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.discord.DiscordNotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.discord.DiscordTestSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.email.EmailDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.email.EmailNotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.email.EmailTestSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.telegram.TelegramDestinationValidator;
import dev.romulus_lanceues.tanaw_api.notification.channel.telegram.TelegramTestSender;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.EndpointTestRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification Spring Context Wiring Tests")
class NotificationConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(
                    NotificationConfig.class,
                    DiscordTestSender.class,
                    DiscordNotificationSender.class,
                    DiscordDestinationValidator.class,
                    TelegramTestSender.class,
                    TelegramDestinationValidator.class,
                    EmailTestSender.class,
                    EmailNotificationSender.class,
                    EmailDestinationValidator.class,
                    DestinationMasker.class,
                    EndpointTestRateLimiter.class,
                    ChannelSupportRegistry.class
            )
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues(
                    "spring.mail.host=localhost",
                    "spring.mail.port=587",
                    "spring.mail.properties.mail.smtp.auth=true",
                    "spring.mail.properties.mail.smtp.starttls.enable=true",
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
            assertThat(context).hasBean("telegramRestClient");
            assertThat(context).getBean("telegramRestClient").isInstanceOf(RestClient.class);
            assertThat(context).hasSingleBean(JavaMailSender.class);
            assertThat(context).getBean(JavaMailSender.class).isInstanceOf(JavaMailSenderImpl.class);

            JavaMailSenderImpl mailSender = (JavaMailSenderImpl) context.getBean(JavaMailSender.class);
            Properties mailProperties = mailSender.getJavaMailProperties();
            assertThat(mailProperties.getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(mailProperties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
            assertThat(mailProperties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
            assertThat(mailProperties.getProperty("mail.smtp.timeout")).isEqualTo("10000");
            assertThat(mailProperties.getProperty("mail.smtp.writetimeout")).isEqualTo("10000");

            assertThat(context).hasSingleBean(DiscordTestSender.class);
            assertThat(context).hasSingleBean(DiscordNotificationSender.class);
            assertThat(context).hasSingleBean(TelegramTestSender.class);
            assertThat(context).hasSingleBean(TelegramDestinationValidator.class);
            assertThat(context).hasSingleBean(EmailTestSender.class);
            assertThat(context).hasSingleBean(EmailNotificationSender.class);
            assertThat(context).hasSingleBean(EmailDestinationValidator.class);
            assertThat(context).hasSingleBean(EndpointTestRateLimiter.class);
            assertThat(context).hasSingleBean(ChannelSupportRegistry.class);

            ChannelSupportRegistry registry = context.getBean(ChannelSupportRegistry.class);
            assertThat(registry.isSupported(NotificationChannel.DISCORD)).isTrue();
            assertThat(registry.isSupported(NotificationChannel.TELEGRAM)).isTrue();
            assertThat(registry.isSupported(NotificationChannel.EMAIL)).isTrue();
            assertThat(registry.hasSender(NotificationChannel.DISCORD)).isTrue();
            assertThat(registry.getSender(NotificationChannel.DISCORD)).isInstanceOf(DiscordNotificationSender.class);
            assertThat(registry.hasSender(NotificationChannel.TELEGRAM)).isFalse();
            assertThat(registry.hasSender(NotificationChannel.EMAIL)).isTrue();
            assertThat(registry.getSender(NotificationChannel.EMAIL)).isInstanceOf(EmailNotificationSender.class);
        });
    }

    @Test
    @DisplayName("Preserves existing spring.mail.properties timeout configurations without overriding")
    void preservesExistingSpringMailPropertiesTimeouts() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.properties.mail.smtp.connectiontimeout=2500",
                        "spring.mail.properties.mail.smtp.timeout=7500",
                        "spring.mail.properties.mail.smtp.writetimeout=7500"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JavaMailSenderImpl mailSender = (JavaMailSenderImpl) context.getBean(JavaMailSender.class);
                    Properties mailProperties = mailSender.getJavaMailProperties();
                    assertThat(mailProperties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("2500");
                    assertThat(mailProperties.getProperty("mail.smtp.timeout")).isEqualTo("7500");
                    assertThat(mailProperties.getProperty("mail.smtp.writetimeout")).isEqualTo("7500");
                });
    }

    @Test
    @DisplayName("Applies notification.email timeout fallbacks to JavaMailSenderImpl when spring.mail timeout properties are absent")
    void appliesNotificationEmailTimeoutFallbacks() {
        contextRunner
                .withPropertyValues(
                        "notification.email.connect-timeout=PT7S",
                        "notification.email.read-timeout=PT15S"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JavaMailSenderImpl mailSender = (JavaMailSenderImpl) context.getBean(JavaMailSender.class);
                    Properties mailProperties = mailSender.getJavaMailProperties();
                    assertThat(mailProperties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("7000");
                    assertThat(mailProperties.getProperty("mail.smtp.timeout")).isEqualTo("15000");
                    assertThat(mailProperties.getProperty("mail.smtp.writetimeout")).isEqualTo("15000");
                    assertThat(mailProperties.getProperty("mail.smtps.connectiontimeout")).isEqualTo("7000");
                    assertThat(mailProperties.getProperty("mail.smtps.timeout")).isEqualTo("15000");
                    assertThat(mailProperties.getProperty("mail.smtps.writetimeout")).isEqualTo("15000");
                });
    }
}
