package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ActuatorConfigTest.TestConfig.class)
@EnableConfigurationProperties({WebEndpointProperties.class, HealthEndpointProperties.class})
@DisplayName("Actuator Configuration Tests")
class ActuatorConfigTest {

    @Configuration
    static class TestConfig {
    }

    private final WebEndpointProperties webEndpointProperties;
    private final HealthEndpointProperties healthEndpointProperties;
    private final Environment environment;

    @Autowired
    ActuatorConfigTest(
            WebEndpointProperties webEndpointProperties,
            HealthEndpointProperties healthEndpointProperties,
            Environment environment) {
        this.webEndpointProperties = webEndpointProperties;
        this.healthEndpointProperties = healthEndpointProperties;
        this.environment = environment;
    }

    @Test
    @DisplayName("Web endpoint exposure includes health and info, and excludes sensitive endpoints")
    void webEndpoints_exposureConfiguredCorrectly() {
        assertThat(webEndpointProperties.getExposure().getInclude())
                .containsExactlyInAnyOrder("health", "info");

        assertThat(webEndpointProperties.getExposure().getExclude())
                .isEmpty();
    }

    @Test
    @DisplayName("Health endpoint hides details from unauthenticated users")
    void healthEndpoint_detailsHidden() {
        assertThat(healthEndpointProperties.getShowDetails())
                .isEqualTo(Show.NEVER);
    }

    @Test
    @DisplayName("Health probes (liveness and readiness) are explicitly enabled")
    void healthProbes_enabled() {
        Boolean probesEnabled = environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class);
        assertThat(probesEnabled).isTrue();
    }
}
