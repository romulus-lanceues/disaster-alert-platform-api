package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GitHub Actions Workflow Security & Hardening Tests")
class WorkflowHardeningTest {

    private static final Path ROOT = Path.of("");
    private static final Path WORKFLOW_FILE = ROOT.resolve(".github/workflows/deploy.yml");

    @Test
    @DisplayName("deploy.yml exists and defines push/dispatch triggers with sequential deployment concurrency")
    void workflow_definesTriggersAndConcurrency() throws IOException {
        assertThat(WORKFLOW_FILE)
                .as(".github/workflows/deploy.yml must exist")
                .exists();

        String content = Files.readString(WORKFLOW_FILE);

        assertThat(content)
                .as("Workflow must trigger on pushes to main")
                .contains("branches: [\"main\"]");

        assertThat(content)
                .as("Workflow must support manual workflow_dispatch execution")
                .contains("workflow_dispatch:");

        assertThat(content)
                .as("Concurrency group must serialize production deploys without cancelling in-flight runs")
                .contains("group: deploy-production")
                .contains("cancel-in-progress: false");
    }

    @Test
    @DisplayName("deploy.yml enforces least-privilege OIDC permissions and prohibits static AWS credentials")
    void workflow_enforcesLeastPrivilegeOidcAndNoStaticKeys() throws IOException {
        assertThat(WORKFLOW_FILE).exists();
        String content = Files.readString(WORKFLOW_FILE);

        assertThat(content)
                .as("Top-level permissions must be restricted to id-token write and contents read")
                .contains("id-token: write")
                .contains("contents: read");

        assertThat(content)
                .as("Workflow must never reference static AWS secret keys")
                .doesNotContain("AWS_ACCESS_KEY_ID")
                .doesNotContain("AWS_SECRET_ACCESS_KEY");

        assertThat(content)
                .as("Deploy job must bind to the production environment")
                .contains("environment: production");

        assertThat(content)
                .as("AWS credentials action must assume the OIDC role from repository secrets")
                .contains("role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE_ARN }}");
    }

    @Test
    @DisplayName("deploy.yml configures linux/amd64 buildx with GitHub Actions layer caching and provenance disabled")
    void workflow_configuresBuildxAndLayerCaching() throws IOException {
        assertThat(WORKFLOW_FILE).exists();
        String content = Files.readString(WORKFLOW_FILE);

        assertThat(content)
                .as("Docker build must target linux/amd64 matching EC2 t3.small host architecture")
                .contains("platforms: linux/amd64");

        assertThat(content)
                .as("Provenance must be disabled to prevent untagged manifest clutter in ECR")
                .contains("provenance: false");

        assertThat(content)
                .as("Docker build must utilize GitHub Actions layer caching")
                .contains("cache-from: type=gha")
                .contains("cache-to: type=gha,mode=max");

        assertThat(content)
                .as("Docker image must be tagged with the commit SHA")
                .contains("tags: ${{ steps.ecr.outputs.registry }}/${{ env.ECR_REPOSITORY }}:${{ github.sha }}");
    }

    @Test
    @DisplayName("deploy.yml synchronizes deploy bundle and executes SSM Run Command with status polling")
    void workflow_configuresS3SyncAndSsmExecution() throws IOException {
        assertThat(WORKFLOW_FILE).exists();
        String content = Files.readString(WORKFLOW_FILE);

        assertThat(content)
                .as("Deploy job must sync deploy/ bundle to S3 OPS_BUCKET with --delete")
                .contains("aws s3 sync deploy/ \"s3://${{ vars.OPS_BUCKET }}/deploy/\" --delete");

        assertThat(content)
                .as("SSM command must invoke AWS-RunShellScript document targeting EC2_INSTANCE_ID")
                .contains("--document-name AWS-RunShellScript")
                .contains("INSTANCE_ID: ${{ vars.EC2_INSTANCE_ID }}")
                .contains("BUCKET: ${{ vars.OPS_BUCKET }}");

        assertThat(content)
                .as("SSM polling loop must check status and stream stdout/stderr")
                .contains("aws ssm get-command-invocation")
                .contains("[StandardOutputContent, StandardErrorContent]")
                .contains("[ \"$STATUS\" = \"Success\" ]");
    }
}
