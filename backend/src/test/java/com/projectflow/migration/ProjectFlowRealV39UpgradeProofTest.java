package com.projectflow.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.ProjectFlowApplication;
import com.projectflow.service.ProviderCredentialStore;

/**
 * Release gate that obtains the legacy schema from the exact final V3.9
 * application, never from the current V1 migration resource.
 */
class ProjectFlowRealV39UpgradeProofTest {
    private static final String V39_FINAL_SHA = "dd5ee41b6afcbd7703fa0883dc115c11f4821447";
    private static final Duration START_TIMEOUT = Duration.ofMinutes(2);
    private static final String REDIS_EXCLUSIONS = String.join(",",
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    );

    @Test
    void upgradesRealFinalV39H2WithBackupCredentialMigrationAndStableRestart() throws Exception {
        ProofInput input = proofInput();
        Path root = Files.createTempDirectory("projectflow-real-v39-h2-");
        try {
            Path dataRoot = Files.createDirectories(root.resolve("legacy-data"));
            Path databaseBase = dataRoot.resolve("projectflow");
            DatabaseTarget target = new DatabaseTarget(
                "jdbc:h2:file:" + databaseBase.toAbsolutePath().normalize()
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                "org.h2.Driver"
            );
            createSchemaWithExactV39(input, target, Map.of("PROJECTFLOW_DATA_DIR", dataRoot.toString()), true);

            DatabaseSchemaSignature.Inspection actualV39 = inspect(target);
            assertThat(actualV39.classification())
                .as("missing=%s extra=%s", actualV39.missingEntries(), actualV39.extraEntries())
                .isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_V39);
            assertThat(actualV39.hasFlywayHistory()).isFalse();

            V39RepresentativeDataFixture fixture = new V39RepresentativeDataFixture();
            withConnection(target, fixture::seed);
            withConnection(target, fixture::assertLegacyRecords);

            Path backups = Files.createDirectories(root.resolve("backups"));
            Path runtime = Files.createDirectories(root.resolve("current-runtime"));
            try (ConfigurableApplicationContext context = startCurrent(target, backups, runtime, false)) {
                String secretRef = withConnectionResult(target, fixture::assertMigratedRecords);
                assertThat(context.getBean(ProviderCredentialStore.class).status(secretRef))
                    .isEqualTo(ProviderCredentialStore.Status.CONFIGURED);
                assertCurrentSchema(target);
            }

            long protectedFingerprint = protectedFingerprint(target);
            long backupCount = validH2BackupCount(backups);
            assertThat(backupCount).isEqualTo(1);

            try (ConfigurableApplicationContext ignored = startCurrent(target, backups, runtime, false)) {
                withConnection(target, fixture::assertMigratedRecords);
                assertCurrentSchema(target);
            }

            assertThat(protectedFingerprint(target)).isEqualTo(protectedFingerprint);
            assertThat(validH2BackupCount(backups)).isEqualTo(backupCount);
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void upgradesRealFinalV39PostgresAfterDumpRestoreAndExplicitBackupAcknowledgement() throws Exception {
        ProofInput input = proofInput();
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("projectflow")
            .withUsername("projectflow")
            .withPassword("projectflow-fixture")) {
            postgres.start();
            DatabaseTarget target = new DatabaseTarget(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), "org.postgresql.Driver"
            );
            createSchemaWithExactV39(input, target, Map.of(
                "DATABASE_URL", target.url(),
                "DATABASE_USERNAME", target.user(),
                "DATABASE_PASSWORD", target.password()
            ), false);

            DatabaseSchemaSignature.Inspection actualV39 = inspect(target);
            assertThat(actualV39.classification())
                .as("missing=%s extra=%s", actualV39.missingEntries(), actualV39.extraEntries())
                .isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_V39);
            assertThat(actualV39.hasFlywayHistory()).isFalse();

            V39RepresentativeDataFixture fixture = new V39RepresentativeDataFixture();
            withConnection(target, fixture::seed);
            withConnection(target, fixture::assertLegacyRecords);
            provePostgresDumpRestore(postgres, target, fixture);

            Path root = Files.createTempDirectory("projectflow-real-v39-postgres-");
            try {
                Path backups = Files.createDirectories(root.resolve("backup-acknowledgement"));
                Path runtime = Files.createDirectories(root.resolve("current-runtime"));
                assertBackupRequired(target, backups, runtime);
                assertThat(inspect(target).classification())
                    .isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_V39);
                assertThat(tableExists(target, "flyway_schema_history")).isFalse();
                withConnection(target, fixture::assertLegacyRecords);

                try (ConfigurableApplicationContext context = startCurrent(target, backups, runtime, true)) {
                    String secretRef = withConnectionResult(target, fixture::assertMigratedRecords);
                    assertThat(context.getBean(ProviderCredentialStore.class).status(secretRef))
                        .isEqualTo(ProviderCredentialStore.Status.CONFIGURED);
                    assertCurrentSchema(target);
                }

                long protectedFingerprint = protectedFingerprint(target);
                try (ConfigurableApplicationContext ignored = startCurrent(target, backups, runtime, true)) {
                    withConnection(target, fixture::assertMigratedRecords);
                    assertCurrentSchema(target);
                }
                assertThat(protectedFingerprint(target)).isEqualTo(protectedFingerprint);
            } finally {
                deleteTree(root);
            }
        }
    }

    private ProofInput proofInput() throws Exception {
        boolean required = Boolean.getBoolean("projectflow.v39.real-proof.required");
        if (!required) {
            Assumptions.assumeTrue(false, "Exact V3.9 release proof runs only in its required CI gate");
        }
        String sourceValue = System.getenv("PROJECTFLOW_V39_SOURCE_ROOT");
        String declaredSha = System.getenv("PROJECTFLOW_V39_SOURCE_SHA");
        if (sourceValue == null || sourceValue.isBlank() || !V39_FINAL_SHA.equals(declaredSha)) {
            throw new AssertionError("Exact V3.9 source proof input is missing or has the wrong revision");
        }
        Path sourceRoot = Path.of(sourceValue).toAbsolutePath().normalize();
        Path oldJar = sourceRoot.resolve("backend/target/projectflow-3.9.0.jar");
        if (!Files.isDirectory(sourceRoot) || !Files.isRegularFile(oldJar)) {
            throw new AssertionError("Exact V3.9 source or built application is unavailable");
        }
        String actualSha = runAndRead(List.of("git", "-C", sourceRoot.toString(), "rev-parse", "HEAD"));
        if (!V39_FINAL_SHA.equals(actualSha)) {
            throw new AssertionError("V3.9 proof worktree does not match the frozen final revision");
        }
        return new ProofInput(sourceRoot, oldJar);
    }

    private void createSchemaWithExactV39(
        ProofInput input,
        DatabaseTarget target,
        Map<String, String> environment,
        boolean embedded
    ) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(input.oldJar().toString());
        if (embedded) command.add("--spring.profiles.active=embedded");
        command.add("--server.address=127.0.0.1");
        command.add("--server.port=0");
        command.add("--spring.main.banner-mode=off");
        command.add("--spring.autoconfigure.exclude=" + REDIS_EXCLUSIONS);
        command.add("--management.health.redis.enabled=false");

        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(input.sourceRoot().resolve("backend").toFile())
            .redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        AtomicBoolean started = new AtomicBoolean();
        Thread outputDrainer = drainOutput(process, started);
        try {
            waitForV39Schema(target, process, started, embedded);
        } finally {
            stopProcess(process);
            outputDrainer.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private Thread drainOutput(Process process, AtomicBoolean started) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Started ProjectFlowApplication")) started.set(true);
                }
            } catch (IOException ignored) {
                // The bounded child process may close its stream during shutdown.
            }
        }, "v39-proof-output-drainer");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void waitForV39Schema(
        DatabaseTarget target,
        Process process,
        AtomicBoolean started,
        boolean embedded
    ) throws Exception {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new AssertionError("Exact V3.9 application exited before creating its schema");
            }
            if (embedded) {
                if (started.get()) return;
                Thread.sleep(250);
                continue;
            }
            try {
                DatabaseSchemaSignature.Inspection inspection = inspect(target);
                if (started.get()
                    && inspection.classification() == DatabaseSchemaSignature.SchemaClassification.KNOWN_V39
                    && !inspection.hasFlywayHistory()) {
                    return;
                }
            } catch (Exception ignored) {
                // The database can be unavailable or structurally partial while Hibernate is creating it.
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Exact V3.9 application did not create the frozen schema before the deadline");
    }

    private void assertBackupRequired(DatabaseTarget target, Path backups, Path runtime) {
        try (ConfigurableApplicationContext ignored = startCurrent(target, backups, runtime, false)) {
            throw new AssertionError("PostgreSQL migration started without backup acknowledgement");
        } catch (RuntimeException failure) {
            SchemaMigrationException migration = findMigrationFailure(failure);
            assertThat(migration).isNotNull();
            assertThat(migration.code()).isEqualTo("BACKUP_REQUIRED");
        }
    }

    private SchemaMigrationException findMigrationFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SchemaMigrationException migration) return migration;
            current = current.getCause();
        }
        return null;
    }

    private ConfigurableApplicationContext startCurrent(
        DatabaseTarget target,
        Path backupDirectory,
        Path runtimeRoot,
        boolean postgresBackupConfirmed
    ) {
        String[] args = {
            "--spring.main.banner-mode=off",
            "--spring.main.web-application-type=none",
            "--spring.main.log-startup-info=false",
            "--spring.autoconfigure.exclude=" + REDIS_EXCLUSIONS,
            "--spring.datasource.url=" + target.url(),
            "--spring.datasource.username=" + target.user(),
            "--spring.datasource.password=" + target.password(),
            "--spring.datasource.driver-class-name=" + target.driver(),
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.flyway.enabled=true",
            "--spring.flyway.baseline-on-migrate=false",
            "--spring.flyway.baseline-version=1",
            "--spring.flyway.clean-disabled=true",
            "--spring.flyway.out-of-order=false",
            "--projectflow.credentials.store=in-memory",
            "--projectflow.migration.backup-directory=" + backupDirectory.toAbsolutePath().normalize(),
            "--projectflow.migration.postgres-backup-confirmed=" + postgresBackupConfirmed,
            "--projectflow.runtime.mode=local-release",
            "--projectflow.auth.required=false",
            "--projectflow.storage.data-dir=" + runtimeRoot.toAbsolutePath().normalize(),
            "--projectflow.storage.config-dir=" + runtimeRoot.resolve("config").toAbsolutePath().normalize(),
            "--PROJECTFLOW_DATA_DIR=" + runtimeRoot.toAbsolutePath().normalize(),
            "--server.address=127.0.0.1",
            "--logging.file.name=",
            "--logging.level.root=OFF"
        };
        return new SpringApplicationBuilder(ProjectFlowApplication.class)
            .profiles("ci", "release")
            .web(WebApplicationType.NONE)
            .registerShutdownHook(false)
            .logStartupInfo(false)
            .run(args);
    }

    private void provePostgresDumpRestore(
        PostgreSQLContainer<?> postgres,
        DatabaseTarget source,
        V39RepresentativeDataFixture fixture
    ) throws Exception {
        assertExec(postgres.execInContainer(
            "pg_dump", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
            "-Fc", "-f", "/tmp/projectflow-v39-final.dump"
        ), "PostgreSQL V3.9 dump failed");
        Container.ExecResult checksum = postgres.execInContainer(
            "sha256sum", "/tmp/projectflow-v39-final.dump"
        );
        assertExec(checksum, "PostgreSQL V3.9 dump checksum failed");
        assertThat(checksum.getStdout().trim()).matches("[0-9a-f]{64}\\s+.+");
        assertExec(postgres.execInContainer(
            "psql", "-U", postgres.getUsername(), "-d", "postgres", "-v", "ON_ERROR_STOP=1",
            "-c", "CREATE DATABASE projectflow_v39_restore"
        ), "PostgreSQL restore database creation failed");
        assertExec(postgres.execInContainer(
            "pg_restore", "-U", postgres.getUsername(), "-d", "projectflow_v39_restore",
            "--exit-on-error", "--no-owner", "--no-privileges", "/tmp/projectflow-v39-final.dump"
        ), "PostgreSQL V3.9 restore failed");

        String restoredUrl = source.url().replace("/" + postgres.getDatabaseName(), "/projectflow_v39_restore");
        DatabaseTarget restored = new DatabaseTarget(restoredUrl, source.user(), source.password(), source.driver());
        assertThat(inspect(restored).classification())
            .isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_V39);
        withConnection(restored, fixture::assertLegacyRecords);
    }

    private void assertExec(Container.ExecResult result, String message) {
        if (result.getExitCode() != 0) throw new AssertionError(message);
    }

    private void assertCurrentSchema(DatabaseTarget target) throws Exception {
        DatabaseSchemaSignature.Inspection current = inspect(target);
        assertThat(current.classification())
            .as("missing=%s extra=%s", current.missingEntries(), current.extraEntries())
            .isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_CURRENT);
        assertThat(current.hasFlywayHistory()).isTrue();
        withConnection(target, connection -> assertThat(V39RepresentativeDataFixture.queryLong(
            connection,
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL"
        )).isEqualTo(2));
    }

    private long validH2BackupCount(Path backupDirectory) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<Path> manifests;
        try (var files = Files.list(backupDirectory)) {
            manifests = files.filter(path -> path.getFileName().toString().endsWith(".manifest.json")).toList();
        }
        for (Path manifest : manifests) {
            JsonNode json = mapper.readTree(Files.readString(manifest));
            assertThat(json.path("complete").asBoolean()).isTrue();
            assertThat(json.path("schemaClassification").asText()).isEqualTo("KNOWN_V39");
            assertThat(json.path("sha256").asText()).matches("[0-9a-f]{64}");
            assertThat(Path.of(json.path("payloadFile").asText()).isAbsolute()).isFalse();
        }
        return manifests.size();
    }

    private long protectedFingerprint(DatabaseTarget target) throws Exception {
        return withConnectionResult(target, connection -> V39RepresentativeDataFixture.queryString(
            connection,
            "SELECT CONCAT((SELECT fact_fingerprint FROM project_facts), '|',"
                + " (SELECT payload_hash FROM project_history_events), '|',"
                + " (SELECT source_event_fingerprint FROM project_history_snapshots), '|',"
                + " (SELECT source_fingerprint FROM project_history_corrections), '|',"
                + " (SELECT source_revision FROM project_agent_candidates), '|',"
                + " (SELECT model_name FROM ai_providers))"
        ).hashCode());
    }

    private DatabaseSchemaSignature.Inspection inspect(DatabaseTarget target) throws Exception {
        return withConnectionResult(target, connection -> new DatabaseSchemaSignature().inspect(connection));
    }

    private boolean tableExists(DatabaseTarget target, String table) throws Exception {
        return withConnectionResult(target, connection -> {
            try (var result = connection.getMetaData().getTables(null, "public", table, new String[] {"TABLE"})) {
                return result.next();
            }
        });
    }

    private void stopProcess(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(20, TimeUnit.SECONDS);
        }
    }

    private String runAndRead(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new AssertionError("V3.9 proof revision check failed");
        }
        return output;
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
            ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private <T> T withConnectionResult(DatabaseTarget target, SqlFunction<T> operation) throws Exception {
        try (Connection connection = DriverManager.getConnection(target.url(), target.user(), target.password())) {
            return operation.apply(connection);
        }
    }

    private void withConnection(DatabaseTarget target, SqlConsumer operation) throws Exception {
        try (Connection connection = DriverManager.getConnection(target.url(), target.user(), target.password())) {
            operation.accept(connection);
        }
    }

    private void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private record ProofInput(Path sourceRoot, Path oldJar) {}

    private record DatabaseTarget(String url, String user, String password, String driver) {}

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }
}
