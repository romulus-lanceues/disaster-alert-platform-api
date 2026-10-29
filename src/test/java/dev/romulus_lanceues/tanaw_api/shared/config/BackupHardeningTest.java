package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Backup Script Security & Reliability Hardening Tests")
class BackupHardeningTest {

    private static final Path ROOT = Path.of("");
    private static final Path BACKUP_SH = ROOT.resolve("deploy/backup.sh");
    private static final Path EC2_DEPLOY_PLAN = ROOT.resolve(".agents/plans/aws-ec2-deployment-plan.md");

    @Test
    @DisplayName("deploy/backup.sh guards variable extraction against silent pipefail exits")
    void backupScript_guardsVariableExtractionAgainstSilentExit() throws IOException {
        assertThat(BACKUP_SH).exists();
        String content = Files.readString(BACKUP_SH);

        assertThat(content)
                .as("grep commands must be guarded with || true to avoid silent pipefail exits on missing variables")
                .matches("(?s).*POSTGRES_USER=.*grep.*\\|\\|\\s*true.*")
                .matches("(?s).*POSTGRES_DB=.*grep.*\\|\\|\\s*true.*")
                .matches("(?s).*OPS_BUCKET=.*grep.*\\|\\|\\s*true.*");
    }

    @Test
    @DisplayName("deploy/backup.sh buffers pg_dump to an EXIT-trapped temporary file and validates size before upload")
    void backupScript_buffersDumpAndValidatesBeforeUpload() throws IOException {
        assertThat(BACKUP_SH).exists();
        String content = Files.readString(BACKUP_SH);

        assertThat(content)
                .as("backup.sh must set umask 077 so temporary dump file is private (0600)")
                .contains("umask 077");

        assertThat(content)
                .as("backup.sh must register an EXIT trap to clean up the temporary dump file")
                .contains("trap 'rm -f \"$TMP_DUMP\"' EXIT");

        assertThat(content)
                .as("backup.sh must verify the dump is non-empty before uploading to S3")
                .contains("-s \"$TMP_DUMP\"");

        assertThat(content)
                .as("backup.sh must pass --region to aws s3 cp")
                .contains("--region");

        assertThat(content)
                .as("backup.sh must not stream directly via unbuffered pipe | aws s3 cp -")
                .doesNotContain("| aws s3 cp -");
    }

    @Test
    @DisplayName("aws-ec2-deployment-plan.md Section 2.7 is synchronized with hardened backup.sh")
    void ec2DeploymentPlan_isSynchronizedWithHardenedBackupScript() throws IOException {
        assertThat(EC2_DEPLOY_PLAN).exists();
        String content = Files.readString(EC2_DEPLOY_PLAN);

        assertThat(content)
                .as("Section 2.7 in deployment plan must include umask 077")
                .contains("umask 077");

        assertThat(content)
                .as("Section 2.7 in deployment plan must include EXIT trap for TMP_DUMP")
                .contains("trap 'rm -f \"$TMP_DUMP\"' EXIT");

        assertThat(content)
                .as("Section 2.7 in deployment plan must pass --region to aws s3 cp")
                .contains("--region");
    }
}

