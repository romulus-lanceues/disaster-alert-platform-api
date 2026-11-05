package dev.romulus_lanceues.tanaw_api.ops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OpsSimulationProperties Tests")
class OpsSimulationPropertiesTest {

    @Nested
    @DisplayName("Constructor Invariant Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Creation succeeds when disabled and simulation-key is null")
        void succeedsWhenDisabledAndKeyIsNull() {
            assertThatCode(() -> new OpsSimulationProperties(false, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Creation succeeds when disabled and simulation-key is empty")
        void succeedsWhenDisabledAndKeyIsEmpty() {
            assertThatCode(() -> new OpsSimulationProperties(false, ""))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Creation fails when enabled and simulation-key is null")
        void failsWhenEnabledAndKeyIsNull() {
            assertThatThrownBy(() -> new OpsSimulationProperties(true, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 16 characters");
        }

        @Test
        @DisplayName("Creation fails when enabled and simulation-key is blank")
        void failsWhenEnabledAndKeyIsBlank() {
            assertThatThrownBy(() -> new OpsSimulationProperties(true, "   "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 16 characters");
        }

        @Test
        @DisplayName("Creation fails when enabled and simulation-key is shorter than 16 characters")
        void failsWhenEnabledAndKeyTooShort() {
            assertThatThrownBy(() -> new OpsSimulationProperties(true, "short-key-123"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 16 characters");
        }

        @Test
        @DisplayName("Creation succeeds when enabled and simulation-key has exactly 16 characters")
        void succeedsWhenEnabledAndKeyIs16Chars() {
            assertThatCode(() -> new OpsSimulationProperties(true, "1234567890123456"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Creation succeeds when enabled and simulation-key has more than 16 characters")
        void succeedsWhenEnabledAndKeyIsMoreThan16Chars() {
            assertThatCode(() -> new OpsSimulationProperties(true, "super-secure-production-ops-key-value"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Spring Boot Configuration Binding and Startup Validation")
    class SpringBootBinding {

        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class);

        @Configuration
        @EnableConfigurationProperties(OpsSimulationProperties.class)
        static class TestConfig {}

        @Test
        @DisplayName("Context startup succeeds when disabled and key is omitted")
        void startupSucceedsWhenDisabled() {
            contextRunner
                    .withPropertyValues("tanaw.ops.enabled=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(OpsSimulationProperties.class);
                        OpsSimulationProperties props = context.getBean(OpsSimulationProperties.class);
                        assertThat(props.enabled()).isFalse();
                    });
        }

        @Test
        @DisplayName("Context startup succeeds when enabled with valid key")
        void startupSucceedsWhenEnabledWithValidKey() {
            contextRunner
                    .withPropertyValues(
                            "tanaw.ops.enabled=true",
                            "tanaw.ops.simulation-key=valid-ops-key-at-least-16-chars"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(OpsSimulationProperties.class);
                        OpsSimulationProperties props = context.getBean(OpsSimulationProperties.class);
                        assertThat(props.enabled()).isTrue();
                        assertThat(props.simulationKey()).isEqualTo("valid-ops-key-at-least-16-chars");
                    });
        }

        @Test
        @DisplayName("Context startup fails when enabled with key shorter than 16 characters")
        void startupFailsWhenEnabledWithShortKey() {
            contextRunner
                    .withPropertyValues(
                            "tanaw.ops.enabled=true",
                            "tanaw.ops.simulation-key=too-short"
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasRootCauseInstanceOf(IllegalStateException.class)
                                .hasRootCauseMessage("tanaw.ops.simulation-key must be configured with at least 16 characters when tanaw.ops.enabled is true.");
                    });
        }
    }
}

