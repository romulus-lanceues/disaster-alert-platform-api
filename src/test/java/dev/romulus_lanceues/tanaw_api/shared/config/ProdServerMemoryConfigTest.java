package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production Server Thread & Memory Guardrails Config Tests")
class ProdServerMemoryConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "DB_URL=jdbc:postgresql://localhost:5432/tanaw",
                    "POSTGRES_USER=tanaw",
                    "POSTGRES_PASSWORD=secret",
                    "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng==",
                    "NOTIFICATION_CRYPTO_KEY=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
            );

    @Configuration
    @EnableConfigurationProperties(TomcatServerProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("prod profile caps Tomcat max worker threads at 50 by default to bound native stack and Argon2 memory")
    void prodProfile_capsTomcatMaxThreadsAt50ByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            TomcatServerProperties tomcat = context.getBean(TomcatServerProperties.class);
            assertThat(tomcat.getThreads().getMax()).isEqualTo(50);
        });
    }

    @Test
    @DisplayName("prod profile allows overriding Tomcat max worker threads via TOMCAT_MAX_THREADS")
    void prodProfile_allowsOverridingTomcatMaxThreads() {
        contextRunner
                .withPropertyValues("TOMCAT_MAX_THREADS=32")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TomcatServerProperties tomcat = context.getBean(TomcatServerProperties.class);
                    assertThat(tomcat.getThreads().getMax()).isEqualTo(32);
                });
    }
}

