package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Datasource Pool and Environment Flags Configuration Tests")
class DatasourcePoolAndEnvironmentConfigTest {

    @Configuration
    static class TestConfig {
    }

    @Nested
    @SpringBootTest(classes = TestConfig.class)
    @DisplayName("Default Profile Environment Flags Tests")
    class DefaultProfileTests {

        private final Environment environment;

        @Autowired
        DefaultProfileTests(Environment environment) {
            this.environment = environment;
        }

        @Test
        @DisplayName("notification.dry-run defaults to true when NOTIFICATION_DRY_RUN is not specified")
        void notificationDryRun_defaultsToTrue() {
            Boolean dryRun = environment.getProperty("notification.dry-run", Boolean.class);
            assertThat(dryRun)
                    .withFailMessage("notification.dry-run should default to true")
                    .isTrue();
        }

        @Test
        @DisplayName("spring.datasource.hikari pool size is not configured in default profile")
        void hikariPool_notExplicitlySetInDefaultProfile() {
            Integer maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
            assertThat(maxPoolSize).isNull();
        }
    }

    @Nested
    @SpringBootTest(classes = TestConfig.class)
    @TestPropertySource(properties = "NOTIFICATION_DRY_RUN=false")
    @DisplayName("Notification Dry-Run Override Tests")
    class NotificationDryRunOverrideTests {

        private final Environment environment;

        @Autowired
        NotificationDryRunOverrideTests(Environment environment) {
            this.environment = environment;
        }

        @Test
        @DisplayName("notification.dry-run resolves to false when NOTIFICATION_DRY_RUN environment variable is set to false")
        void notificationDryRun_canBeOverriddenToFalse() {
            Boolean dryRun = environment.getProperty("notification.dry-run", Boolean.class);
            assertThat(dryRun)
                    .withFailMessage("notification.dry-run should resolve to false when NOTIFICATION_DRY_RUN=false")
                    .isFalse();
        }
    }

    @Nested
    @SpringBootTest(classes = TestConfig.class)
    @ActiveProfiles("prod")
    @DisplayName("Prod Profile Default Hikari Pool Tests")
    class ProdProfileDefaultPoolTests {

        private final Environment environment;

        @Autowired
        ProdProfileDefaultPoolTests(Environment environment) {
            this.environment = environment;
        }

        @Test
        @DisplayName("Prod profile configures Hikari maximum-pool-size to default of 10")
        void prodProfile_configuresHikariMaximumPoolSizeDefault() {
            Integer maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
            assertThat(maxPoolSize)
                    .withFailMessage("Hikari maximum-pool-size should default to 10 in prod profile")
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("Prod profile configures Hikari minimum-idle to default of 2")
        void prodProfile_configuresHikariMinimumIdleDefault() {
            Integer minIdle = environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class);
            assertThat(minIdle)
                    .withFailMessage("Hikari minimum-idle should default to 2 in prod profile")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Prod profile configures Hikari connection-timeout to 30000 ms")
        void prodProfile_configuresHikariConnectionTimeout() {
            Long connectionTimeout = environment.getProperty("spring.datasource.hikari.connection-timeout", Long.class);
            assertThat(connectionTimeout)
                    .withFailMessage("Hikari connection-timeout should be 30000 ms in prod profile")
                    .isEqualTo(30000L);
        }

        @Test
        @DisplayName("Prod profile configures Hikari max-lifetime to 1800000 ms (30 minutes)")
        void prodProfile_configuresHikariMaxLifetime() {
            Long maxLifetime = environment.getProperty("spring.datasource.hikari.max-lifetime", Long.class);
            assertThat(maxLifetime)
                    .withFailMessage("Hikari max-lifetime should be 1800000 ms in prod profile")
                    .isEqualTo(1800000L);
        }
    }

    @Nested
    @SpringBootTest(classes = TestConfig.class)
    @ActiveProfiles("prod")
    @TestPropertySource(properties = {
            "DB_POOL_MAX_SIZE=15",
            "DB_POOL_MIN_IDLE=5"
    })
    @DisplayName("Prod Profile Overridden Hikari Pool Tests")
    class ProdProfileOverriddenPoolTests {

        private final Environment environment;

        @Autowired
        ProdProfileOverriddenPoolTests(Environment environment) {
            this.environment = environment;
        }

        @Test
        @DisplayName("Prod profile allows DB_POOL_MAX_SIZE override")
        void prodProfile_overridesMaximumPoolSize() {
            Integer maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
            assertThat(maxPoolSize)
                    .withFailMessage("Hikari maximum-pool-size should be overridden to 15")
                    .isEqualTo(15);
        }

        @Test
        @DisplayName("Prod profile allows DB_POOL_MIN_IDLE override")
        void prodProfile_overridesMinimumIdle() {
            Integer minIdle = environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class);
            assertThat(minIdle)
                    .withFailMessage("Hikari minimum-idle should be overridden to 5")
                    .isEqualTo(5);
        }
    }
}
