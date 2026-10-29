package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Deployment Script Security & Permission Hardening Tests")
class DeployHardeningTest {

    private static final Path ROOT = Path.of("");
    private static final Path DEPLOY_SH = ROOT.resolve("deploy/deploy.sh");
    private static final Path USER_DATA_SH = ROOT.resolve("deploy/user-data.sh");

    @Test
    @DisplayName("deploy.sh enforces umask 077, installs directories with mode 700, and registers EXIT trap for .env.tmp")
    void deployScript_enforcesUmaskAndTrapCleanup() throws IOException {
        assertThat(DEPLOY_SH).exists();
        String content = Files.readString(DEPLOY_SH);

        assertThat(content)
                .as("deploy.sh must enforce umask 077 to prevent world-readable temporary files")
                .contains("umask 077");

        assertThat(content)
                .as("deploy.sh must register an EXIT trap to guarantee .env.tmp is deleted on failure")
                .contains("trap 'rm -f \"$ROOT/.env.tmp\"' EXIT");

        assertThat(content)
                .as("deploy.sh must secure root, bundle, and state directories with mode 700")
                .contains("install -d -m 700 \"$ROOT\" \"$BUNDLE\" \"$STATE\"");

        assertThat(content)
                .as("deploy.sh must validate TAG against strict OCI tag character allowlist before interpolation")
                .contains("^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$");
    }

    @Test
    @DisplayName("user-data.sh locks down /opt/tanaw directory permissions to mode 700")
    void userDataScript_locksTanawDirectoryPermissions() throws IOException {
        assertThat(USER_DATA_SH).exists();
        String content = Files.readString(USER_DATA_SH);

        assertThat(content)
                .as("user-data.sh must restrict /opt/tanaw permissions to 700")
                .contains("chmod 700 /opt/tanaw");
    }

    @Test
    @DisplayName("deploy.sh gracefully reloads Caddy configuration after readiness check passes")
    void deployScript_reloadsCaddyOnDeployment() throws IOException {
        assertThat(DEPLOY_SH).exists();
        String content = Files.readString(DEPLOY_SH);

        assertThat(content)
                .as("deploy.sh must gracefully reload Caddy configuration on deploy so bind-mounted Caddyfile changes take effect")
                .contains("compose \"$TAG\" exec -T caddy caddy reload --config /etc/caddy/Caddyfile");
    }
}
