package dev.romulus_lanceues.tanaw_api.shared.config;

import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Forwarded Headers Configuration Tests")
class ForwardedHeadersConfigTest {

    @Configuration
    static class TestConfig {
    }

    @Nested
    @SpringBootTest(classes = TestConfig.class)
    @EnableConfigurationProperties({ServerProperties.class, TomcatServerProperties.class, WebProperties.class})
    @ActiveProfiles("prod")
    @DisplayName("Prod Profile Forwarded Headers Tests")
    class ProdProfileTests {

        private final ServerProperties serverProperties;
        private final TomcatServerProperties tomcatServerProperties;
        private final WebProperties webProperties;
        private final Environment environment;

        @Autowired
        ProdProfileTests(
                ServerProperties serverProperties,
                TomcatServerProperties tomcatServerProperties,
                WebProperties webProperties,
                Environment environment) {
            this.serverProperties = serverProperties;
            this.tomcatServerProperties = tomcatServerProperties;
            this.webProperties = webProperties;
            this.environment = environment;
        }

        @Test
        @DisplayName("Prod profile configures server.forward-headers-strategy to native")
        void prodProfile_configuresNativeForwardHeadersStrategy() {
            assertThat(serverProperties.getForwardHeadersStrategy())
                    .isEqualTo(ServerProperties.ForwardHeadersStrategy.NATIVE);
        }

        @Test
        @DisplayName("TomcatWebServerFactoryCustomizer registers RemoteIpValve when native strategy is enabled")
        void tomcatCustomizer_registersRemoteIpValveWithTrustedInternalProxies() {
            TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
            TomcatWebServerFactoryCustomizer customizer = new TomcatWebServerFactoryCustomizer(
                    environment,
                    serverProperties,
                    tomcatServerProperties,
                    webProperties
            );
            customizer.customize(factory);

            RemoteIpValve remoteIpValve = factory.getEngineValves().stream()
                    .filter(RemoteIpValve.class::isInstance)
                    .map(RemoteIpValve.class::cast)
                    .findFirst()
                    .orElse(null);

            assertThat(remoteIpValve)
                    .withFailMessage("RemoteIpValve should be registered when forward-headers-strategy is native")
                    .isNotNull();

            assertThat(remoteIpValve.getProtocolHeader()).isEqualToIgnoringCase("x-forwarded-proto");
            assertThat(remoteIpValve.getRemoteIpHeader()).isEqualToIgnoringCase("x-forwarded-for");
            assertThat(remoteIpValve.getHostHeader()).isEqualToIgnoringCase("x-forwarded-host");
            assertThat(remoteIpValve.getPortHeader()).isEqualToIgnoringCase("x-forwarded-port");
            assertThat(remoteIpValve.getProtocolHeaderHttpsValue()).isEqualToIgnoringCase("https");

            // Verify trusted internal proxies includes loopback and Docker/private network CIDRs
            String internalProxies = remoteIpValve.getInternalProxies();
            assertThat(internalProxies)
                    .contains("127.0.0.0/8")
                    .contains("172.16.0.0/12")
                    .contains("10.0.0.0/8")
                    .contains("192.168.0.0/16")
                    .contains("::1/128");

            // Untrusted wide-open proxies (e.g. 0.0.0.0/0 or arbitrary hosts) must NOT be trusted
            assertThat(internalProxies).doesNotContain("0.0.0.0/0");
            assertThat(remoteIpValve.getTrustedProxies()).isNull();
        }
    }

    @Nested
    @SpringBootTest(classes = TestConfig.class)
    @EnableConfigurationProperties({ServerProperties.class, TomcatServerProperties.class, WebProperties.class})
    @DisplayName("Default Profile Forwarded Headers Tests")
    class DefaultProfileTests {

        private final ServerProperties serverProperties;

        @Autowired
        DefaultProfileTests(ServerProperties serverProperties) {
            this.serverProperties = serverProperties;
        }

        @Test
        @DisplayName("Default profile does not enable native forward-headers-strategy to prevent header spoofing")
        void defaultProfile_doesNotEnableNativeForwardHeadersStrategy() {
            assertThat(serverProperties.getForwardHeadersStrategy())
                    .isNotEqualTo(ServerProperties.ForwardHeadersStrategy.NATIVE);
        }
    }
}
