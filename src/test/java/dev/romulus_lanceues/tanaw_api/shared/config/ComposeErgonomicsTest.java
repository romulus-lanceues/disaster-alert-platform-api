package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Docker Compose Ergonomics & Safe SSM .env Rendering Tests")
class ComposeErgonomicsTest {

    private static final Path ROOT = Path.of("");
    private static final Path DEPLOY_SH = ROOT.resolve("deploy/deploy.sh");
    private static final Path COMPOSE_PROD = ROOT.resolve("deploy/docker-compose.prod.yml");

    @Test
    @DisplayName("deploy.sh uses python3 to parse SSM JSON output and avoids fragile awk splitting")
    void deployScript_usesPythonToRenderEnvFromJsonWithoutAwk() throws IOException {
        assertThat(DEPLOY_SH).exists();
        String content = Files.readString(DEPLOY_SH);

        assertThat(content)
                .as("deploy.sh must request JSON output from SSM get-parameters-by-path")
                .contains("--output json");

        assertThat(content)
                .as("deploy.sh must not use fragile tab-delimited awk splitting")
                .doesNotContain("awk -F'\\t'");

        assertThat(content)
                .as("deploy.sh must use python3 to safely parse SSM JSON parameters")
                .contains("python3");
    }

    @Test
    @DisplayName("deploy.sh python rendering validates parameter names, rejects newlines, and losslessly double-quotes values")
    void deployScript_validatesParametersAndSafelyQuotesValues() throws IOException {
        assertThat(DEPLOY_SH).exists();
        String content = Files.readString(DEPLOY_SH);

        assertThat(content)
                .as("deploy.sh python parser must guard against newline injection in parameter values")
                .containsAnyOf("\\n", "\\r");

        assertThat(content)
                .as("deploy.sh python parser must escape backslashes, double quotes, and dollar signs for Compose dotenv")
                .contains("val.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\").replace(\"$\", \"\\\\$\")");
    }

    @Test
    @DisplayName("deploy.sh keeps IMAGE_TAG at previous known-good tag until readiness passes and maintains bundle symlink")
    void deployScript_persistsEcrRegistryAndPromotesImageTagOnlyAfterReadiness() throws IOException {
        assertThat(DEPLOY_SH).exists();
        String content = Files.readString(DEPLOY_SH);

        assertThat(content)
                .as("deploy.sh must seed .env IMAGE_TAG with ACTIVE_TAG (${PREV:-$TAG}) before pull/readiness")
                .contains("ACTIVE_TAG=\"${PREV:-$TAG}\"")
                .contains("printf 'ECR_REGISTRY=\"%s\"\\nIMAGE_TAG=\"%s\"\\n' \"$ECR_REGISTRY\" \"$ACTIVE_TAG\"");

        assertThat(content)
                .as("deploy.sh must promote IMAGE_TAG to $TAG in .env after readiness succeeds")
                .contains("sed -i \"s/^IMAGE_TAG=.*/IMAGE_TAG=\\\"$TAG\\\"/\" \"$ROOT/.env\"");

        assertThat(content)
                .as("deploy.sh must maintain bundle/.env symlink for seamless operator CLI usage")
                .contains("ln -sf \"$ROOT/.env\" \"$BUNDLE/.env\"");

        assertThat(content)
                .as("deploy.sh must ensure IMAGE_TAG reflects $PREV on rollback")
                .contains("sed -i \"s/^IMAGE_TAG=.*/IMAGE_TAG=\\\"$PREV\\\"/\" \"$ROOT/.env\"");
    }

    @Test
    @DisplayName("docker-compose.prod.yml references env_file ../.env and valid service definitions")
    void dockerComposeProd_referencesEnvFile() throws IOException {
        assertThat(COMPOSE_PROD).exists();
        String content = Files.readString(COMPOSE_PROD);

        assertThat(content)
                .as("docker-compose.prod.yml must reference env_file: ../.env")
                .contains("env_file: ../.env");

        assertThat(content)
                .as("docker-compose.prod.yml must reference ECR_REGISTRY and IMAGE_TAG")
                .contains("${ECR_REGISTRY}")
                .contains("${IMAGE_TAG}");
    }
}
