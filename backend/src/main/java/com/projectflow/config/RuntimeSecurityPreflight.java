package com.projectflow.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeSecurityPreflight {
    private static final String JWT_PLACEHOLDER = "replace-with-at-least-32-random-bytes";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "::1", "[::1]");
    private static final Set<String> MODES = Set.of("developer", "test", "local-release", "external");

    public RuntimeSecurityPreflight(
        @Value("${projectflow.runtime.mode:developer}") String runtimeMode,
        @Value("${server.address:}") String serverAddress,
        @Value("${projectflow.auth.required:false}") boolean authenticationRequired,
        @Value("${projectflow.jwt.secret:${JWT_SECRET:replace-with-at-least-32-random-bytes}}") String jwtSecret,
        @Value("${projectflow.auth.password-reset-code:}") String passwordResetCode,
        @Value("${FRONTEND_ORIGIN:http://localhost:3000,http://127.0.0.1:3000}") String frontendOrigins,
        @Value("${spring.datasource.password:}") String databasePassword,
        @Value("${spring.data.redis.password:}") String redisPassword
    ) {
        validate(
            runtimeMode, serverAddress, authenticationRequired, jwtSecret, passwordResetCode,
            frontendOrigins, databasePassword, redisPassword
        );
    }

    public static void validate(
        String runtimeMode,
        String serverAddress,
        boolean authenticationRequired,
        String jwtSecret,
        String passwordResetCode,
        String frontendOrigins,
        String databasePassword,
        String redisPassword
    ) {
        String mode = normalized(runtimeMode).toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw blocked("RUNTIME_MODE_INVALID", "Unknown ProjectFlow runtime mode");
        }

        String bind = normalized(serverAddress).toLowerCase(Locale.ROOT);
        if ("local-release".equals(mode) && !LOOPBACK_HOSTS.contains(bind)) {
            throw blocked("LOCAL_BIND_UNSAFE", "Local release must bind to an explicit loopback address");
        }
        if ("external".equals(mode) && bind.isBlank()) {
            throw blocked("EXTERNAL_BIND_REQUIRED", "External mode requires an explicit server address");
        }
        if (!authenticationRequired && !"developer".equals(mode) && !"test".equals(mode)
            && !LOOPBACK_HOSTS.contains(bind)) {
            throw blocked("AUTH_OFF_NETWORK_EXPOSURE", "Authentication-off mode is restricted to loopback");
        }

        if (authenticationRequired) {
            String jwt = normalized(jwtSecret);
            if (jwt.isBlank() || JWT_PLACEHOLDER.equals(jwt) || jwt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw blocked("JWT_SECRET_REQUIRED", "Authentication requires a non-placeholder JWT secret of at least 32 bytes");
            }
            if (normalized(passwordResetCode).isBlank()) {
                throw blocked("PASSWORD_RESET_SECRET_REQUIRED", "Authentication requires an explicitly supplied password reset secret");
            }
        }

        String[] origins = Arrays.stream(normalized(frontendOrigins).split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toArray(String[]::new);
        if (origins.length == 0) {
            throw blocked("CORS_ORIGIN_REQUIRED", "At least one explicit frontend origin is required");
        }
        for (String origin : origins) {
            validateOrigin(origin, mode);
        }

        if ("external".equals(mode)) {
            String dbPassword = normalized(databasePassword);
            if (dbPassword.isBlank() || "change-me-local".equals(dbPassword)) {
                throw blocked("DATABASE_CREDENTIAL_REQUIRED", "External mode rejects the local default database password");
            }
            if (normalized(redisPassword).isBlank()) {
                throw blocked("REDIS_CREDENTIAL_REQUIRED", "External mode requires an explicit Redis password");
            }
        }
    }

    private static void validateOrigin(String origin, String mode) {
        if ("*".equals(origin) || origin.contains("*")) {
            throw blocked("CORS_ORIGIN_UNBOUNDED", "Wildcard CORS origins are not allowed");
        }
        try {
            URI uri = URI.create(origin);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw blocked("CORS_ORIGIN_INVALID", "Frontend origins must be explicit HTTP(S) origins");
            }
            if ("local-release".equals(mode) && !LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                throw blocked("LOCAL_CORS_UNSAFE", "Local release only permits loopback frontend origins");
            }
        } catch (IllegalArgumentException exception) {
            throw blocked("CORS_ORIGIN_INVALID", "Frontend origins must be explicit HTTP(S) origins");
        }
    }

    private static IllegalStateException blocked(String code, String message) {
        return new IllegalStateException(code + ": " + message);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
