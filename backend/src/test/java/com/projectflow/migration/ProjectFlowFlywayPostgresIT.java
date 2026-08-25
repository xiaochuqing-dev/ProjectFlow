package com.projectflow.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL legacy fixture; opt in only where Docker is an explicit test dependency. */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "PROJECTFLOW_RUN_POSTGRES_MIGRATION_TESTS", matches = "true")
class ProjectFlowFlywayPostgresIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesEmptyAndRequiresBackupAcknowledgementForKnownLegacySchema() throws Exception {
        String emptyUrl = POSTGRES.getJdbcUrl();
        ProjectFlowFlywayMigrationStrategy strategy = strategy(Path.of("target", "postgres-migration-backups"), false);
        strategy.migrate(flyway(emptyUrl));
        try (var connection = DriverManager.getConnection(emptyUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(connection.getMetaData().getColumns(null, "public", "ai_providers", "secret_ref").next())
                .isTrue();
        }

        String database = "projectflow_legacy";
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        try (var admin = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        )) {
            admin.createStatement().execute("CREATE DATABASE " + database);
        }
        String legacyUrl = POSTGRES.getJdbcUrl().substring(0, POSTGRES.getJdbcUrl().lastIndexOf('/') + 1) + database;
        // The baseline fixture is executed by the same bounded JDBC script path used in H2 tests.
        try (var legacy = DriverManager.getConnection(legacyUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("db/migration/V1__v39_schema_baseline.sql"),
                java.nio.charset.StandardCharsets.UTF_8
            ))) {
                StringBuilder sql = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sql.append(line).append('\n');
                for (String statement : sql.toString().split(";\\s*(?:\\r?\\n|$)")) {
                    if (!statement.isBlank()) legacy.createStatement().execute(statement);
                }
                String now = "TIMESTAMPTZ '2026-08-25 00:00:00+00'";
                legacy.createStatement().executeUpdate("""
                    INSERT INTO users(created_at, updated_at, id, username, email, password_hash)
                    VALUES (%s, %s, '%s', 'pg-fixture-user', 'pg-fixture@example.invalid', 'fixture-hash')
                    """.formatted(now, now, userId));
                legacy.createStatement().executeUpdate("""
                    INSERT INTO projects(created_at, updated_at, id, user_id, status, name)
                    VALUES (%s, %s, '%s', '%s', 'BUILDING', 'PostgreSQL V3.9 fixture')
                    """.formatted(now, now, projectId, userId));
                legacy.createStatement().executeUpdate("""
                    INSERT INTO project_facts(created_at, updated_at, id, project_id, confidence, origin,
                        record_status, fact_fingerprint, revision, title)
                    VALUES (%s, %s, '%s', '%s', 'HIGH', 'INCREMENTAL_SCAN', 'RECORDED',
                        'pg-fact-fingerprint-v39', 'pg-fact-revision-v39', 'Synthetic PostgreSQL fact')
                    """.formatted(now, now, factId, projectId));
                legacy.createStatement().executeUpdate("""
                    INSERT INTO ai_providers(default_enabled, max_tokens, temperature, created_at, updated_at,
                        id, user_id, type, name, model_name, base_url, api_key)
                    VALUES (TRUE, 2048, 0.1, %s, %s, '%s', '%s', 'OPENAI', 'PG fixture provider',
                        'legacy-pg-model', 'https://example.invalid/v1', 'legacy-pg-sentinel')
                    """.formatted(now, now, providerId, userId));
                DatabaseSchemaSignature.Inspection legacySignature = new DatabaseSchemaSignature().inspect(legacy);
                assertThat(legacySignature.classification())
                    .as("missing=%s extra=%s", legacySignature.missingEntries(), legacySignature.extraEntries())
                    .isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_V39);
            }
        }

        assertThatThrownBy(() -> strategy(Path.of("target", "postgres-migration-backups"), false)
            .migrate(flyway(legacyUrl)))
            .isInstanceOf(SchemaMigrationException.class)
            .extracting("code")
            .isEqualTo("BACKUP_REQUIRED");
        strategy(Path.of("target", "postgres-migration-backups"), true).migrate(flyway(legacyUrl));

        try (var legacy = DriverManager.getConnection(legacyUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(legacy.getMetaData().getColumns(null, "public", "ai_providers", "secret_ref").next())
                .isTrue();
            try (var facts = legacy.createStatement().executeQuery(
                "SELECT fact_fingerprint, revision FROM project_facts"
            )) {
                assertThat(facts.next()).isTrue();
                assertThat(facts.getString("fact_fingerprint")).isEqualTo("pg-fact-fingerprint-v39");
                assertThat(facts.getString("revision")).isEqualTo("pg-fact-revision-v39");
            }
            try (var providers = legacy.createStatement().executeQuery(
                "SELECT model_name, api_key, secret_ref FROM ai_providers"
            )) {
                assertThat(providers.next()).isTrue();
                assertThat(providers.getString("model_name")).isEqualTo("legacy-pg-model");
                assertThat(providers.getString("api_key")).isEqualTo("legacy-pg-sentinel");
                assertThat(providers.getString("secret_ref")).isNull();
            }
        }
        try (var admin = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        )) {
            admin.createStatement().execute("DROP DATABASE " + database);
        }
    }

    private ProjectFlowFlywayMigrationStrategy strategy(Path backupDirectory, boolean confirmed) {
        return new ProjectFlowFlywayMigrationStrategy(
            new DatabaseSchemaSignature(),
            new EmbeddedH2BackupService(new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())),
            new PostgresBackupAcknowledgement(),
            backupDirectory.toString(),
            confirmed
        );
    }

    private Flyway flyway(String url) {
        return Flyway.configure()
            .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .baselineVersion("1")
            .cleanDisabled(true)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .load();
    }
}
