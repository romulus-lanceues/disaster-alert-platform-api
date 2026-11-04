package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EC2 Bootstrap User-Data Script Security & Idempotency Hardening Tests")
class UserDataHardeningTest {

    private static final Path ROOT = Path.of("");
    private static final Path USER_DATA_SH = ROOT.resolve("deploy/user-data.sh");

    @Test
    @DisplayName("deploy/user-data.sh configures dpkg lock timeouts, needrestart mode, and non-interactive flags")
    void userDataScript_configuresAptLockAndNonInteractiveFlags() throws IOException {
        assertThat(USER_DATA_SH).exists();
        String content = Files.readString(USER_DATA_SH);

        assertThat(content)
                .as("user-data.sh must export NEEDRESTART_MODE=a to avoid interactive prompt freezes")
                .contains("NEEDRESTART_MODE=a");

        assertThat(content)
                .as("user-data.sh must configure DPkg::Lock::Timeout=120 to tolerate background unattended-upgrades")
                .contains("DPkg::Lock::Timeout=120");

        assertThat(content)
                .as("user-data.sh must configure force-confdef and force-confold to handle config prompts non-interactively")
                .contains("force-confdef")
                .contains("force-confold");

        assertThat(content)
                .as("user-data.sh must explicitly install python3 for deploy.sh SSM JSON rendering")
                .matches("(?s).*apt-get install.*\\bpython3\\b.*");
    }

    @Test
    @DisplayName("deploy/user-data.sh isolates AWS CLI installation in an EXIT-trapped temporary directory with --update flag")
    void userDataScript_isolatesAwsCliInstallationAndEnforcesIdempotency() throws IOException {
        assertThat(USER_DATA_SH).exists();
        String content = Files.readString(USER_DATA_SH);

        assertThat(content)
                .as("user-data.sh must use mktemp -d for AWS CLI installer extraction instead of static /tmp/aws")
                .contains("mktemp -d");

        assertThat(content)
                .as("user-data.sh must clean up the AWS CLI temporary directory on EXIT")
                .matches("(?s).*trap.*rm.*EXIT.*");

        assertThat(content)
                .as("user-data.sh must pass --update to aws/install for re-run idempotency")
                .contains("/install\" --update");
    }

    @Test
    @DisplayName("deploy/user-data.sh manages 2GB swap idempotently and safely")
    void userDataScript_managesSwapIdempotently() throws IOException {
        assertThat(USER_DATA_SH).exists();
        String content = Files.readString(USER_DATA_SH);

        assertThat(content)
                .as("user-data.sh must guard swapfile creation with an existence check")
                .contains("if [[ ! -f /swapfile ]]");

        assertThat(content)
                .as("user-data.sh must guard swapon against activating an already active swapfile")
                .contains("swapon --show");

        assertThat(content)
                .as("user-data.sh must check /etc/fstab before appending to prevent duplicate entries")
                .contains("grep -qxF '/swapfile none swap sw 0 0' /etc/fstab");
    }
}
