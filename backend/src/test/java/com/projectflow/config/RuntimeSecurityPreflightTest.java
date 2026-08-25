package com.projectflow.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimeSecurityPreflightTest {
    private static final String STRONG_JWT = "test-secret-must-be-at-least-32-bytes-long";

    @Test
    void localReleaseAllowsAuthOffOnlyOnLoopback() {
        assertThatCode(() -> validate("local-release", "127.0.0.1", false, "replace-with-at-least-32-random-bytes",
            "", "http://127.0.0.1:3000", "", "")).doesNotThrowAnyException();

        assertThatThrownBy(() -> validate("local-release", "0.0.0.0", false,
            "replace-with-at-least-32-random-bytes", "", "http://127.0.0.1:3000", "", ""))
            .hasMessageContaining("LOCAL_BIND_UNSAFE");
    }

    @Test
    void localReleaseRejectsNonLoopbackCorsOrigin() {
        assertThatThrownBy(() -> validate("local-release", "127.0.0.1", false,
            "replace-with-at-least-32-random-bytes", "", "http://192.168.1.20:3000", "", ""))
            .hasMessageContaining("LOCAL_CORS_UNSAFE");
    }

    @Test
    void authenticationRejectsPlaceholderAndMissingResetSecret() {
        assertThatThrownBy(() -> validate("external", "0.0.0.0", true,
            "replace-with-at-least-32-random-bytes", "reset", "https://projectflow.example", "db-secret", "redis-secret"))
            .hasMessageContaining("JWT_SECRET_REQUIRED");

        assertThatThrownBy(() -> validate("external", "0.0.0.0", true,
            STRONG_JWT, "", "https://projectflow.example", "db-secret", "redis-secret"))
            .hasMessageContaining("PASSWORD_RESET_SECRET_REQUIRED");
    }

    @Test
    void externalModeRejectsLocalInfrastructureDefaultsAndWildcardCors() {
        assertThatThrownBy(() -> validate("external", "0.0.0.0", true,
            STRONG_JWT, "reset", "*", "db-secret", "redis-secret"))
            .hasMessageContaining("CORS_ORIGIN_UNBOUNDED");

        assertThatThrownBy(() -> validate("external", "0.0.0.0", true,
            STRONG_JWT, "reset", "https://projectflow.example", "change-me-local", "redis-secret"))
            .hasMessageContaining("DATABASE_CREDENTIAL_REQUIRED");

        assertThatThrownBy(() -> validate("external", "0.0.0.0", true,
            STRONG_JWT, "reset", "https://projectflow.example", "db-secret", ""))
            .hasMessageContaining("REDIS_CREDENTIAL_REQUIRED");
    }

    private static void validate(
        String mode,
        String bind,
        boolean auth,
        String jwt,
        String reset,
        String origins,
        String databasePassword,
        String redisPassword
    ) {
        RuntimeSecurityPreflight.validate(mode, bind, auth, jwt, reset, origins, databasePassword, redisPassword);
    }
}
