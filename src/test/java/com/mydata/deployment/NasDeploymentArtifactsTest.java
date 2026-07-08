package com.mydata.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NasDeploymentArtifactsTest {
    @Test
    void dockerfileBuildsAdminUiAndSpringBootJarIntoRuntimeImage() throws Exception {
        String dockerfile = readRequired("Dockerfile");

        assertThat(dockerfile)
            .contains("FROM node:22-alpine AS admin-ui-builder")
            .contains("npm run build")
            .contains("FROM eclipse-temurin:21-jdk")
            .contains("bootJar -x test -x adminUiNpmInstall -x adminUiNpmBuild")
            .contains("FROM eclipse-temurin:21-jre-alpine")
            .contains("EXPOSE 50506")
            .contains("java", "-jar", "/app/app.jar");
    }

    @Test
    void dockerignoreExcludesLocalBuildSecretsAndGeneratedArtifacts() throws Exception {
        String dockerignore = readRequired(".dockerignore");

        assertThat(dockerignore)
            .contains(".git")
            .contains(".env")
            .contains(".env.*")
            .contains("build/")
            .contains("frontend/admin/node_modules/")
            .contains("frontend/admin/dist/")
            .contains(".playwright-mcp/");
    }

    @Test
    void synologyComposeRunsAppImageOnHostNetworkWithProdEnvAndHealthcheck() throws Exception {
        String compose = readRequired("deploy/compose.yml");

        assertThat(compose)
            .contains("my-data-slack-chat:")
            .contains("image: ${IMAGE:-ghcr.io/greatbooms/my-data-slack-chat:latest}")
            .contains("container_name: my-data-slack-chat")
            .contains("network_mode: host")
            .contains("restart: unless-stopped")
            .contains("env_file:")
            .contains("- .env.prod")
            .contains("SERVER_PORT: \"50506\"")
            .contains("/actuator/health")
            .contains("max-size: \"100m\"")
            .contains("max-file: \"5\"");
    }

    @Test
    void synologyDeployScriptPullsImageWaitsForHealthAndCleansOldImages() throws Exception {
        String script = readRequired("scripts/deploy-synology.sh");

        assertThat(script)
            .contains("APP_NAME=\"${APP_NAME:-my-data-slack-chat}\"")
            .contains("DEPLOY_PATH=\"${DEPLOY_PATH:?DEPLOY_PATH is required}\"")
            .contains("IMAGE=\"${IMAGE:?IMAGE is required}\"")
            .contains("Missing runtime env file")
            .contains("docker_cmd compose --env-file \"$DEPLOY_ENV_FILE\" -f \"$COMPOSE_FILE\" pull")
            .contains("docker_cmd compose --env-file \"$DEPLOY_ENV_FILE\" -f \"$COMPOSE_FILE\" up -d --remove-orphans")
            .contains("Waiting for container health")
            .contains("cleanup_unused_app_images")
            .contains("docker_cmd logs --tail 120 \"$APP_NAME\"");
    }

    @Test
    void githubWorkflowBuildsGhcrImageAndDeploysToSynologyOverTailscale() throws Exception {
        String workflow = readRequired(".github/workflows/deploy.yml");

        assertThat(workflow)
            .contains("branches:")
            .contains("- main")
            .contains("packages: write")
            .contains("docker/setup-buildx-action@v3")
            .contains("docker/login-action@v3")
            .contains("docker/build-push-action@v6")
            .contains("tailscale/github-action@v4")
            .contains("SYNOLOGY_HOST")
            .contains("SYNOLOGY_DEPLOY_PATH")
            .contains("deploy/compose.yml")
            .contains("scripts/deploy-synology.sh")
            .contains("APP_NAME=my-data-slack-chat");
    }

    @Test
    void productionEnvExampleUsesJdbcDatabaseUrlAndKeepsSecretsBlank() throws Exception {
        String envExample = readRequired(".env.prod.example");

        assertThat(envExample)
            .contains("SPRING_PROFILES_ACTIVE=prod")
            .contains("SERVER_PORT=50506")
            .contains("DATABASE_URL=jdbc:postgresql://127.0.0.1:15432/my_data?currentSchema=public")
            .contains("DATABASE_USERNAME=postgres")
            .contains("DATABASE_PASSWORD=")
            .contains("ADMIN_BOOTSTRAP_EMAIL=admin@example.com")
            .contains("ADMIN_BOOTSTRAP_PASSWORD=")
            .contains("SLACK_SOCKET_MODE_ENABLED=false")
            .contains("NOTION_API_TOKEN=")
            .contains("MY_DATA_LLM_PROVIDER=stub")
            .doesNotContain("xoxb-")
            .doesNotContain("sk-");
    }

    @Test
    void nasDeploymentGuideDocumentsUserOwnedSetupSteps() throws Exception {
        String guide = readRequired("docs/nas-deployment.md");

        assertThat(guide)
            .contains("Synology NAS 배포")
            .contains(".env.prod")
            .contains("DATABASE_URL=jdbc:postgresql://127.0.0.1:15432/my_data?currentSchema=public")
            .contains("postgresql://")
            .contains("jdbc:postgresql://")
            .contains("GitHub Secrets")
            .contains("SYNOLOGY_HOST")
            .contains("SYNOLOGY_DEPLOY_PATH")
            .contains("docker compose")
            .contains("/actuator/health");
    }

    private static String readRequired(String path) throws IOException {
        Path file = Path.of(path);

        assertThat(file).exists().isRegularFile();
        return Files.readString(file);
    }
}
