package dev.romulus_lanceues.tanaw_api.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AlertMatchingProperties Validation and Binding Tests")
class AlertMatchingPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(AlertMatchingProperties.class)
    static class TestConfig {}

    private ApplicationContextRunner contextWithValidProperties() {
        return contextRunner.withPropertyValues(
                "alert.matching.batch-size=200",
                "alert.matching.backlog-warn-age=PT10M",
                "alert.matching.max-batches-per-drain=10"
        );
    }

    @Nested
    @DisplayName("Constructor Invariant Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("valid parameters create AlertMatchingProperties successfully")
        void validParametersSucceed() {
            AlertMatchingProperties properties = new AlertMatchingProperties(
                    200,
                    Duration.ofMinutes(10),
                    10
            );

            assertThat(properties.batchSize()).isEqualTo(200);
            assertThat(properties.backlogWarnAge()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.maxBatchesPerDrain()).isEqualTo(10);
        }

        @Test
        @DisplayName("null batchSize throws NullPointerException")
        void nullBatchSizeThrows() {
            assertThatThrownBy(() -> new AlertMatchingProperties(null, Duration.ofMinutes(10), 10))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("batch size must not be null");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -50})
        @DisplayName("zero or negative batchSize throws IllegalArgumentException")
        void nonPositiveBatchSizeThrows(int invalidBatchSize) {
            assertThatThrownBy(() -> new AlertMatchingProperties(invalidBatchSize, Duration.ofMinutes(10), 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("batch size must be positive");
        }

        @Test
        @DisplayName("null backlogWarnAge throws NullPointerException")
        void nullBacklogWarnAgeThrows() {
            assertThatThrownBy(() -> new AlertMatchingProperties(200, null, 10))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("backlog warn age must not be null");
        }

        @Test
        @DisplayName("zero or negative backlogWarnAge throws IllegalArgumentException")
        void nonPositiveBacklogWarnAgeThrows() {
            assertThatThrownBy(() -> new AlertMatchingProperties(200, Duration.ZERO, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("backlog warn age must be positive");

            assertThatThrownBy(() -> new AlertMatchingProperties(200, Duration.ofMinutes(-5), 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("backlog warn age must be positive");
        }

        @Test
        @DisplayName("null maxBatchesPerDrain throws NullPointerException")
        void nullMaxBatchesPerDrainThrows() {
            assertThatThrownBy(() -> new AlertMatchingProperties(200, Duration.ofMinutes(10), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("max batches per drain must not be null");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10})
        @DisplayName("zero or negative maxBatchesPerDrain throws IllegalArgumentException")
        void nonPositiveMaxBatchesPerDrainThrows(int invalidMaxBatches) {
            assertThatThrownBy(() -> new AlertMatchingProperties(200, Duration.ofMinutes(10), invalidMaxBatches))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max batches per drain must be positive");
        }
    }

    @Nested
    @DisplayName("Spring Boot Configuration Binding and Startup Validation")
    class SpringBootBindingTests {

        @Test
        @DisplayName("startup succeeds when all valid properties are configured")
        void startupSucceedsWhenValidPropertiesConfigured() {
            contextWithValidProperties()
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(AlertMatchingProperties.class);

                        AlertMatchingProperties props = context.getBean(AlertMatchingProperties.class);
                        assertThat(props.batchSize()).isEqualTo(200);
                        assertThat(props.backlogWarnAge()).isEqualTo(Duration.ofMinutes(10));
                        assertThat(props.maxBatchesPerDrain()).isEqualTo(10);
                    });
        }

        @Test
        @DisplayName("startup fails when alert.matching configuration is missing")
        void startupFailsWhenPropertiesAreMissing() {
            contextRunner.run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("startup fails when batch-size property is missing")
        void startupFailsWhenBatchSizeIsMissing() {
            contextRunner
                    .withPropertyValues(
                            "alert.matching.backlog-warn-age=PT10M",
                            "alert.matching.max-batches-per-drain=10"
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("batch size must not be null");
                    });
        }

        @Test
        @DisplayName("startup fails when backlog-warn-age property is missing")
        void startupFailsWhenBacklogWarnAgeIsMissing() {
            contextRunner
                    .withPropertyValues(
                            "alert.matching.batch-size=200",
                            "alert.matching.max-batches-per-drain=10"
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("backlog warn age must not be null");
                    });
        }

        @Test
        @DisplayName("startup fails when max-batches-per-drain property is missing")
        void startupFailsWhenMaxBatchesPerDrainIsMissing() {
            contextRunner
                    .withPropertyValues(
                            "alert.matching.batch-size=200",
                            "alert.matching.backlog-warn-age=PT10M"
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("max batches per drain must not be null");
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "-100"})
        @DisplayName("startup fails when alert.matching.batch-size is zero or negative")
        void startupFailsOnInvalidBatchSize(String invalidBatchSize) {
            contextWithValidProperties()
                    .withPropertyValues("alert.matching.batch-size=" + invalidBatchSize)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("batch size must be positive");
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {"PT0S", "-PT10M", "PT-1M"})
        @DisplayName("startup fails when alert.matching.backlog-warn-age is zero or negative")
        void startupFailsOnInvalidBacklogWarnAge(String invalidAge) {
            contextWithValidProperties()
                    .withPropertyValues("alert.matching.backlog-warn-age=" + invalidAge)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("backlog warn age must be positive");
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "-10"})
        @DisplayName("startup fails when alert.matching.max-batches-per-drain is zero or negative")
        void startupFailsOnInvalidMaxBatchesPerDrain(String invalidMaxBatches) {
            contextWithValidProperties()
                    .withPropertyValues("alert.matching.max-batches-per-drain=" + invalidMaxBatches)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("max batches per drain must be positive");
                    });
        }
    }
}
