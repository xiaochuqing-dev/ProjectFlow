package com.projectflow.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Comparator;

import org.flywaydb.core.Flyway;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

class ProjectFlowFlywayMigrationStrategyTest {
    @Test
    void migratesEmptyH2ThroughV1AndV2AndIsIdempotent() throws Exception {
        String url = "jdbc:h2:mem:flyway-empty;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = flyway(url);
        ProjectFlowFlywayMigrationStrategy strategy = strategy(Path.of("target", "migration-test-backups"), false);

        strategy.migrate(flyway);
        strategy.migrate(flyway);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnExists(connection, "ai_providers", "secret_ref")).isTrue();
            assertThat(historyCount(connection)).isEqualTo(2);
        }
        String schemaState = Files.readString(Path.of("target", "migration-test-backups", "projectflow-schema-state.json"));
        assertThat(schemaState)
            .contains("\"schemaVersion\":\"projectflow-schema-state-v1\"")
            .contains("\"schemaClassification\":\"KNOWN_CURRENT\"")
            .contains("\"flywayCurrentVersion\":\"2\"")
            .doesNotContain("C:\\");
    }

    @Test
    void baselinesKnownV39AfterBackupAndAppliesV2() throws Exception {
        Path root = Files.createTempDirectory("projectflow-flyway-known-");
        try {
            Path databaseBase = root.resolve("projectflow");
            String url = "jdbc:h2:file:" + databaseBase.toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                runV39Baseline(connection);
            }

            Path backups = root.resolve("backups");
            strategy(backups, false).migrate(flyway(url));

            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                assertThat(columnExists(connection, "ai_providers", "secret_ref")).isTrue();
                assertThat(historyCount(connection)).isEqualTo(2);
            }
            try (var files = Files.list(backups)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith(".manifest.json")).count())
                    .isEqualTo(1);
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void upgradesAnAlreadyBaselinedV39HistoryAfterBackup() throws Exception {
        Path root = Files.createTempDirectory("projectflow-flyway-managed-v1-");
        try {
            String url = "jdbc:h2:file:" + root.resolve("projectflow").toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                runV39Baseline(connection);
            }
            Flyway flyway = flyway(url);
            flyway.baseline();

            Path backups = root.resolve("backups");
            strategy(backups, false).migrate(flyway);

            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                assertThat(columnExists(connection, "ai_providers", "secret_ref")).isTrue();
                assertThat(historyCount(connection)).isEqualTo(2);
            }
            try (var files = Files.list(backups)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith(".manifest.json")).count())
                    .isEqualTo(1);
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void blocksUnknownPartialSchemaWithoutCreatingHistoryOrChangingIt() throws Exception {
        String url = "jdbc:h2:mem:flyway-unknown;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("CREATE TABLE users(id UUID)");
        }

        assertThatThrownBy(() -> strategy(Path.of("target", "migration-test-backups-unknown"), false)
            .migrate(flyway(url)))
            .isInstanceOf(SchemaMigrationException.class)
            .extracting("code")
            .isEqualTo("UNSUPPORTED_LEGACY_SCHEMA");

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnExists(connection, "users", "id")).isTrue();
            assertThat(tableExists(connection, "flyway_schema_history")).isFalse();
        }
    }

    @Test
    void failedH2BackupCanBeRetriedAfterTheOperatorFixesTheTarget() throws Exception {
        Path root = Files.createTempDirectory("projectflow-flyway-retry-");
        try {
            String url = "jdbc:h2:file:" + root.resolve("projectflow").toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                runV39Baseline(connection);
            }
            Path blockedBackupDirectory = root.resolve("backup-target");
            Files.writeString(blockedBackupDirectory, "not-a-directory", StandardCharsets.UTF_8);

            assertThatThrownBy(() -> strategy(blockedBackupDirectory, false).migrate(flyway(url)))
                .isInstanceOf(SchemaMigrationException.class)
                .extracting("code")
                .isEqualTo("BACKUP_FAILED");
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                assertThat(tableExists(connection, "flyway_schema_history")).isFalse();
            }

            Files.delete(blockedBackupDirectory);
            strategy(blockedBackupDirectory, false).migrate(flyway(url));
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                assertThat(historyCount(connection)).isEqualTo(2);
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void rejectsBaselineOnMigrateEvenWhenTheDatabaseIsEmpty() {
        String url = "jdbc:h2:mem:flyway-invalid-config;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway invalid = Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load();

        assertThatThrownBy(() -> strategy(Path.of("target", "migration-test-backups-invalid"), false).migrate(invalid))
            .isInstanceOf(SchemaMigrationException.class)
            .extracting("code")
            .isEqualTo("SCHEMA_MIGRATION_CONFIGURATION_INVALID");
    }

    @Test
    void blocksCurrentSchemaWithoutFlywayHistoryInsteadOfSilentlyReturning() throws Exception {
        String url = "jdbc:h2:mem:flyway-current-without-history;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            runV39Baseline(connection);
            connection.createStatement().execute("ALTER TABLE ai_providers ADD COLUMN secret_ref VARCHAR(200)");
        }

        assertThatThrownBy(() -> strategy(Path.of("target", "migration-test-backups-current"), false)
            .migrate(flyway(url)))
            .isInstanceOf(SchemaMigrationException.class)
            .extracting("code")
            .isEqualTo("UNSUPPORTED_LEGACY_SCHEMA");

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "flyway_schema_history")).isFalse();
            assertThat(columnExists(connection, "ai_providers", "secret_ref")).isTrue();
        }
    }

    @Test
    void blocksAHistoryEntryFromAnUnknownFutureVersion() throws Exception {
        String url = "jdbc:h2:mem:flyway-future;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = flyway(url);
        ProjectFlowFlywayMigrationStrategy strategy = strategy(Path.of("target", "migration-test-backups-future"), false);
        strategy.migrate(flyway);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().executeUpdate("""
                INSERT INTO flyway_schema_history
                    (installed_rank, version, description, type, script, checksum, installed_by,
                     installed_on, execution_time, success)
                VALUES (3, '3', 'future migration', 'SQL', 'V3__future.sql', 12345, 'sa',
                        CURRENT_TIMESTAMP, 0, TRUE)
                """);
        }

        assertThatThrownBy(() -> strategy.migrate(flyway))
            .isInstanceOf(SchemaMigrationException.class)
            .extracting("code")
            .isEqualTo("SCHEMA_MIGRATION_BLOCKED");
    }

    @Test
    void blocksManagedHistoryWhenThePhysicalSchemaHasDrifted() throws Exception {
        String url = "jdbc:h2:mem:flyway-managed-drift;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = flyway(url);
        ProjectFlowFlywayMigrationStrategy strategy = strategy(
            Path.of("target", "migration-test-backups-managed-drift"),
            false
        );
        strategy.migrate(flyway);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("DROP INDEX idx_project_fact_batch");
        }

        assertThatThrownBy(() -> strategy.migrate(flyway))
            .isInstanceOf(SchemaMigrationException.class)
            .extracting("code")
            .isEqualTo("SCHEMA_MIGRATION_BLOCKED");
    }

    private ProjectFlowFlywayMigrationStrategy strategy(Path backupDirectory, boolean postgresConfirmed) {
        return new ProjectFlowFlywayMigrationStrategy(
            new DatabaseSchemaSignature(),
            new EmbeddedH2BackupService(new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())),
            new PostgresBackupAcknowledgement(),
            backupDirectory.toString(),
            postgresConfirmed
        );
    }

    private Flyway flyway(String url) {
        return Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .baselineVersion("1")
            .cleanDisabled(true)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .load();
    }

    private void runV39Baseline(Connection connection) throws Exception {
        try (Reader reader = new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("db/migration/V1__v39_schema_baseline.sql"),
            StandardCharsets.UTF_8
        )) {
            RunScript.execute(connection, reader);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var result = connection.createStatement().executeQuery("SELECT 1 FROM " + table + " WHERE 1 = 0")) {
            return true;
        } catch (java.sql.SQLException missing) {
            return false;
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (var result = connection.createStatement().executeQuery(
            "SELECT " + column + " FROM " + table + " WHERE 1 = 0"
        )) {
            return true;
        } catch (java.sql.SQLException missing) {
            return false;
        }
    }

    private int historyCount(Connection connection) throws Exception {
        try (var result = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL"
        )) {
            result.next();
            return result.getInt(1);
        }
    }

    private void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }
}
