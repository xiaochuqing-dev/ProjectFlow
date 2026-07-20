package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.projectflow.security.JwtService;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.LocalProjectPathGuard;
import com.projectflow.service.ProjectZipUploadGuard;
import com.projectflow.support.AppException;
import com.projectflow.entity.ModelProtocol;

class SecurityGuardrailTest {
    @Test
    void jwtServiceRejectsExplicitWeakSecret() {
        assertThatThrownBy(() -> new JwtService("short-secret", 60))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void aiProviderUrlGuardRejectsMetadataAndPrivateNetworkTargets() {
        AiProviderUrlGuard guard = new AiProviderUrlGuard();

        assertThatThrownBy(() -> guard.validateBaseUrl("http://169.254.169.254/latest/meta-data"))
            .isInstanceOf(AppException.class)
            .extracting("code")
            .isEqualTo("AI_PROVIDER_URL_BLOCKED");
        assertThatThrownBy(() -> guard.validateBaseUrl("http://10.0.0.5/v1"))
            .isInstanceOf(AppException.class)
            .extracting("code")
            .isEqualTo("AI_PROVIDER_URL_BLOCKED");
    }

    @Test
    void aiProviderUrlGuardAllowsHttpsAndLocalDevelopmentHttp() {
        AiProviderUrlGuard guard = new AiProviderUrlGuard();

        assertThat(guard.validateBaseUrl("https://api.deepseek.com/v1")).isEqualTo("https://api.deepseek.com/v1");
        assertThat(guard.validateBaseUrl("http://127.0.0.1:18080/v1")).isEqualTo("http://127.0.0.1:18080/v1");
        assertThat(guard.validateBaseUrl("http://localhost:18080/v1")).isEqualTo("http://localhost:18080/v1");
        assertThat(guard.endpointUri("https://api.openai.com/v1", ModelProtocol.OPENAI_RESPONSES, null).toString())
            .isEqualTo("https://api.openai.com/v1/responses");
        assertThat(guard.sdkBaseUrl(
            "https://relay.example/v1", ModelProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://relay.example/custom/chat/completions"
        )).isEqualTo("https://relay.example/custom");
        assertThatThrownBy(() -> guard.sdkBaseUrl(
            "https://relay.example/v1", ModelProtocol.OPENAI_RESPONSES,
            "https://relay.example/custom/chat/completions"
        )).isInstanceOf(AppException.class).extracting("code").isEqualTo("AI_PROVIDER_URL_BLOCKED");
        assertThatThrownBy(() -> guard.validateBaseUrl("https://user:secret@api.example/v1"))
            .isInstanceOf(AppException.class).extracting("code").isEqualTo("AI_PROVIDER_URL_BLOCKED");
    }

    @Test
    void aiProviderUrlGuardPermitsQueryOnlyOnAProtocolMatchingFullEndpointOverride() {
        AiProviderUrlGuard guard = new AiProviderUrlGuard();

        assertThat(guard.endpointUri(
            "https://relay.example/v1", ModelProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://relay.example/custom/chat/completions?region=test"
        ).toString()).endsWith("/custom/chat/completions?region=test");
        assertThatThrownBy(() -> guard.sdkBaseUrl(
            "https://relay.example/v1", ModelProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://relay.example/custom/chat/completions?region=test"
        )).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> guard.endpointUri(
            "https://relay.example/v1?region=test", ModelProtocol.OPENAI_CHAT_COMPLETIONS, null
        )).isInstanceOf(AppException.class);
    }

    @Test
    void localProjectPathGuardRejectsBroadDirectoriesAndAcceptsRealProjectFolder() throws Exception {
        LocalProjectPathGuard guard = new LocalProjectPathGuard();
        Path project = Files.createTempDirectory("projectflow-path-guard");

        assertThat(guard.requireProjectDirectory(project.toString()).path()).isEqualTo(project.toAbsolutePath().normalize());
        assertThatThrownBy(() -> guard.requireProjectDirectory(project.getRoot().toString()))
            .isInstanceOf(AppException.class)
            .extracting("code")
            .isEqualTo("PROJECT_PATH_TOO_BROAD");
        assertThatThrownBy(() -> guard.requireProjectDirectory(System.getProperty("user.home")))
            .isInstanceOf(AppException.class)
            .extracting("code")
            .isEqualTo("PROJECT_PATH_TOO_BROAD");
    }

    @Test
    void projectZipUploadGuardRejectsOversizedZipBeforeScanning() {
        ProjectZipUploadGuard guard = new ProjectZipUploadGuard(1024, 512);

        assertThatThrownBy(() -> guard.assertUploadBudget(1025))
            .isInstanceOf(AppException.class)
            .extracting("status")
            .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        guard.assertUploadBudget(1024);
        guard.assertReadBudget(256, 256);
        assertThatThrownBy(() -> guard.assertReadBudget(512, 1))
            .isInstanceOf(AppException.class)
            .extracting("status")
            .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void projectZipUploadGuardAllowsLargeLocalProjectZipWithinFileBudget() {
        ProjectZipUploadGuard guard = new ProjectZipUploadGuard(512L * 1024 * 1024, 64L * 1024 * 1024);

        guard.assertUploadBudget(250L * 1024 * 1024);
    }
}
