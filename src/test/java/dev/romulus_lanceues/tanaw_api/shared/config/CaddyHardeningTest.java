package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Caddy Reverse Proxy Security Hardening Tests")
class CaddyHardeningTest {

    private static final Path ROOT = Path.of("");
    private static final Path CADDYFILE = ROOT.resolve("deploy/Caddyfile");

    @Test
    @DisplayName("deploy/Caddyfile sets header_up X-Forwarded-Port {server_port} to prevent Location header spoofing")
    void caddyfile_setsHeaderUpXForwardedPort() throws IOException {
        assertThat(CADDYFILE).exists();
        String content = Files.readString(CADDYFILE);

        assertThat(content)
                .as("Caddyfile must sanitize and set X-Forwarded-Port {server_port} in reverse_proxy")
                .contains("header_up X-Forwarded-Port {server_port}");

        assertThat(content)
                .as("header_up must be inside reverse_proxy app:8080 block")
                .matches("(?s).*reverse_proxy\\s+app:8080\\s*\\{[^}]*header_up\\s+X-Forwarded-Port\\s+\\{server_port\\}[^}]*\\}.*");
    }
}
