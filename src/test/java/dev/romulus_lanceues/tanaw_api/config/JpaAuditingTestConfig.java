package dev.romulus_lanceues.tanaw_api.config;

import dev.romulus_lanceues.tanaw_api.notification.destination.NotificationTestCryptoConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@TestConfiguration
@Import(NotificationTestCryptoConfig.class)
@EnableJpaAuditing
public class JpaAuditingTestConfig {
}
