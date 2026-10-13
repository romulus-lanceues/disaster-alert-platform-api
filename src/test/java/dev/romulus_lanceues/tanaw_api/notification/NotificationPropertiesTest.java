package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NotificationProperties Validation and Binding Tests")
class NotificationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(NotificationProperties.class)
    static class TestConfig {}

    private ApplicationContextRunner contextWithValidProperties() {
        return contextRunner.withPropertyValues(
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
    }

    private NotificationProperties createDefaultProperties() {
        return new NotificationProperties(
                true,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="),
                null
        );
    }

    @Nested
    @DisplayName("Constructor Invariant Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("valid parameters create NotificationProperties successfully")
        void validParametersSucceed() {
            NotificationProperties properties = createDefaultProperties();

            assertThat(properties.dryRun()).isTrue();
            assertThat(properties.dispatcher().enabled()).isTrue();
            assertThat(properties.dispatcher().interval()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.dispatcher().batchSize()).isEqualTo(50);
            assertThat(properties.dispatcher().lease()).isEqualTo(Duration.ofMinutes(2));

            assertThat(properties.retry().maxAttempts()).isEqualTo(5);
            assertThat(properties.retry().initialBackoff()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.retry().maxBackoff()).isEqualTo(Duration.ofMinutes(10));

            assertThat(properties.fanout().maxEventAge()).isEqualTo(Duration.ofHours(6));

            assertThat(properties.discord().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.discord().readTimeout()).isEqualTo(Duration.ofSeconds(10));

            assertThat(properties.crypto().key()).isEqualTo("dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng==");
            assertThat(properties.telegram()).isNull();
        }

        @Test
        @DisplayName("valid parameters including telegram succeed")
        void validParametersWithTelegramSucceed() {
            NotificationProperties properties = new NotificationProperties(
                    false,
                    new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                    new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                    new NotificationProperties.Fanout(Duration.ofHours(6)),
                    new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                    new NotificationProperties.Crypto("dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="),
                    new NotificationProperties.Telegram("bot-token-123", "tanaw_bot")
            );

            assertThat(properties.telegram()).isNotNull();
            assertThat(properties.telegram().botToken()).isEqualTo("bot-token-123");
            assertThat(properties.telegram().botUsername()).isEqualTo("tanaw_bot");
        }

        @Test
        @DisplayName("null top-level components throw NullPointerException")
        void nullTopLevelComponentsThrow() {
            NotificationProperties.Dispatcher dispatcher = new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2));
            NotificationProperties.Retry retry = new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10));
            NotificationProperties.Fanout fanout = new NotificationProperties.Fanout(Duration.ofHours(6));
            NotificationProperties.Discord discord = new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10));
            NotificationProperties.Crypto crypto = new NotificationProperties.Crypto("key");

            assertThatThrownBy(() -> new NotificationProperties(null, dispatcher, retry, fanout, discord, crypto, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dry run must not be null");

            assertThatThrownBy(() -> new NotificationProperties(true, null, retry, fanout, discord, crypto, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dispatcher configuration must not be null");

            assertThatThrownBy(() -> new NotificationProperties(true, dispatcher, null, fanout, discord, crypto, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("retry configuration must not be null");

            assertThatThrownBy(() -> new NotificationProperties(true, dispatcher, retry, null, discord, crypto, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fanout configuration must not be null");

            assertThatThrownBy(() -> new NotificationProperties(true, dispatcher, retry, fanout, null, crypto, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("discord configuration must not be null");

            assertThatThrownBy(() -> new NotificationProperties(true, dispatcher, retry, fanout, discord, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("crypto configuration must not be null");
        }

        @Test
        @DisplayName("Dispatcher null and non-positive invariants throw")
        void dispatcherInvariantsThrow() {
            assertThatThrownBy(() -> new NotificationProperties.Dispatcher(null, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("enabled must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Dispatcher(true, null, 50, Duration.ofMinutes(2)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("interval must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Dispatcher(true, Duration.ZERO, 50, Duration.ofMinutes(2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("interval must be positive");

            assertThatThrownBy(() -> new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), null, Duration.ofMinutes(2)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("batch size must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 0, Duration.ofMinutes(2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("batch size must be positive");

            assertThatThrownBy(() -> new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("lease must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lease must be positive");
        }

        @Test
        @DisplayName("Retry invariants throw on null, non-positive, or maxBackoff < initialBackoff")
        void retryInvariantsThrow() {
            assertThatThrownBy(() -> new NotificationProperties.Retry(null, Duration.ofSeconds(10), Duration.ofMinutes(10)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("max attempts must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Retry(0, Duration.ofSeconds(10), Duration.ofMinutes(10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max attempts must be positive");

            assertThatThrownBy(() -> new NotificationProperties.Retry(5, null, Duration.ofMinutes(10)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("initial backoff must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Retry(5, Duration.ZERO, Duration.ofMinutes(10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("initial backoff must be positive");

            assertThatThrownBy(() -> new NotificationProperties.Retry(5, Duration.ofSeconds(10), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("max backoff must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max backoff must be positive");

            assertThatThrownBy(() -> new NotificationProperties.Retry(5, Duration.ofMinutes(5), Duration.ofSeconds(30)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max backoff must be greater than or equal to initial backoff");
        }

        @Test
        @DisplayName("Fanout invariants throw on null or non-positive maxEventAge")
        void fanoutInvariantsThrow() {
            assertThatThrownBy(() -> new NotificationProperties.Fanout(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("max event age must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Fanout(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max event age must be positive");
        }

        @Test
        @DisplayName("Discord invariants throw on null or non-positive timeouts")
        void discordInvariantsThrow() {
            assertThatThrownBy(() -> new NotificationProperties.Discord(null, Duration.ofSeconds(10)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("connect timeout must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Discord(Duration.ZERO, Duration.ofSeconds(10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("connect timeout must be positive");

            assertThatThrownBy(() -> new NotificationProperties.Discord(Duration.ofSeconds(3), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("read timeout must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(-5)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("read timeout must be positive");
        }

        @Test
        @DisplayName("Crypto invariants throw on null or blank key")
        void cryptoInvariantsThrow() {
            assertThatThrownBy(() -> new NotificationProperties.Crypto(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("crypto key must not be null");

            assertThatThrownBy(() -> new NotificationProperties.Crypto("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("crypto key must not be blank");
        }
    }

    @Nested
    @DisplayName("Spring Boot Configuration Binding and Startup Validation")
    class SpringBindingTests {

        @Test
        @DisplayName("valid properties bind and populate NotificationProperties bean")
        void validPropertiesBindSuccessfully() {
            contextWithValidProperties()
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(NotificationProperties.class);

                        NotificationProperties properties = context.getBean(NotificationProperties.class);
                        assertThat(properties.dryRun()).isTrue();
                        assertThat(properties.dispatcher().enabled()).isTrue();
                        assertThat(properties.dispatcher().interval()).isEqualTo(Duration.ofSeconds(5));
                        assertThat(properties.dispatcher().batchSize()).isEqualTo(50);
                        assertThat(properties.dispatcher().lease()).isEqualTo(Duration.ofMinutes(2));

                        assertThat(properties.retry().maxAttempts()).isEqualTo(5);
                        assertThat(properties.retry().initialBackoff()).isEqualTo(Duration.ofSeconds(10));
                        assertThat(properties.retry().maxBackoff()).isEqualTo(Duration.ofMinutes(10));

                        assertThat(properties.fanout().maxEventAge()).isEqualTo(Duration.ofHours(6));

                        assertThat(properties.discord().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
                        assertThat(properties.discord().readTimeout()).isEqualTo(Duration.ofSeconds(10));

                        assertThat(properties.crypto().key()).isEqualTo("dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng==");
                        assertThat(properties.telegram()).isNull();
                    });
        }

        @Test
        @DisplayName("binding with telegram properties sets telegram bean fields")
        void telegramPropertiesBindSuccessfully() {
            contextWithValidProperties()
                    .withPropertyValues(
                            "notification.telegram.bot-token=telegram-token-xyz",
                            "notification.telegram.bot-username=tanaw_alert_bot"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        NotificationProperties properties = context.getBean(NotificationProperties.class);
                        assertThat(properties.telegram()).isNotNull();
                        assertThat(properties.telegram().botToken()).isEqualTo("telegram-token-xyz");
                        assertThat(properties.telegram().botUsername()).isEqualTo("tanaw_alert_bot");
                    });
        }

        @Test
        @DisplayName("missing required crypto key fails context startup")
        void missingCryptoKeyFailsStartup() {
            contextRunner
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
                            "notification.crypto.key="
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .isInstanceOf(ConfigurationPropertiesBindException.class);
                    });
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -10})
        @DisplayName("non-positive dispatcher batch size fails context startup")
        void nonPositiveDispatcherBatchSizeFailsStartup(int invalidBatchSize) {
            contextWithValidProperties()
                    .withPropertyValues("notification.dispatcher.batch-size=" + invalidBatchSize)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .isInstanceOf(ConfigurationPropertiesBindException.class);
                    });
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("non-positive retry max attempts fails context startup")
        void nonPositiveRetryMaxAttemptsFailsStartup(int invalidMaxAttempts) {
            contextWithValidProperties()
                    .withPropertyValues("notification.retry.max-attempts=" + invalidMaxAttempts)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .isInstanceOf(ConfigurationPropertiesBindException.class);
                    });
        }
    }
}
