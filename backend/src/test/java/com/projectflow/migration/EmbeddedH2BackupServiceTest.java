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
import java.util.UUID;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class EmbeddedH2BackupServiceTest {
    @Test
    void createsChecksRetainsAndRestoresAnIsolatedH2Archive() throws Exception {
        Path root = Files.createTempDirectory("projectflow-h2-backup-");
        try {
            Path databaseBase = root.resolve("projectflow");
            String url = "jdbc:h2:file:" + databaseBase.toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                runV39Baseline(connection);
                connection.createStatement().executeUpdate(
                    "INSERT INTO users(created_at, updated_at, id, username, email, password_hash) VALUES "
                        + "(CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '" + UUID.randomUUID()
                        + "', 'backup-user', 'backup@example.invalid', 'hash')"
                );
            }

            EmbeddedH2BackupService service = new EmbeddedH2BackupService(
                new ObjectMapper().registerModule(new JavaTimeModule())
            );
            EmbeddedH2BackupService.BackupMetadata metadata = new EmbeddedH2BackupService.BackupMetadata(
                "3.10", "v3.9", "KNOWN_V39", "", "2"
            );
            EmbeddedH2BackupService.BackupArtifact latest = null;
            for (int i = 0; i < 6; i++) {
                latest = service.createBackup(
                    newDataSource(url),
                    root.resolve("backups"),
                    metadata
                );
            }

            assertThat(latest).isNotNull();
            EmbeddedH2BackupService.BackupArtifact finalArtifact = latest;
            assertThat(finalArtifact.manifest().creationMethod()).isEqualTo("H2_BACKUP_TO");
            try (var files = Files.list(root.resolve("backups"))) {
                var backupFiles = files.toList();
                assertThat(backupFiles.stream().filter(path -> path.getFileName().toString().endsWith(".zip")).count())
                    .isEqualTo(6);
            }
            service.pruneCompletedBackups(root.resolve("backups"));
            assertThat(service.validate(finalArtifact).manifest().complete()).isTrue();
            try (var files = Files.list(root.resolve("backups"))) {
                var backupFiles = files.toList();
                assertThat(backupFiles.stream().filter(path -> path.getFileName().toString().endsWith(".zip")).count())
                    .isLessThanOrEqualTo(5);
                assertThat(backupFiles.stream().noneMatch(path -> path.getFileName().toString().endsWith(".tmp"))).isTrue();
            }

            EmbeddedH2BackupService.IsolatedRestore restore = service.restoreIntoIsolated(
                finalArtifact,
                root.resolve("isolated"),
                "restored-projectflow",
                new DatabaseSchemaSignature()
            );
            String restoredUrl = "jdbc:h2:file:" + restore.restoredDatabaseBase().toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r";
            try (Connection restored = DriverManager.getConnection(restoredUrl, "sa", "")) {
                try (var result = restored.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM users WHERE username = 'backup-user'"
                )) {
                    result.next();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
            }

            Files.write(finalArtifact.payload(), new byte[] {1, 2, 3});
            assertThatThrownBy(() -> service.validate(finalArtifact))
                .isInstanceOf(EmbeddedH2BackupService.BackupException.class)
                .extracting("code")
                .isEqualTo("BACKUP_INVALID");
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void rejectsAUnknownBackupBeforeRestore() throws Exception {
        Path root = Files.createTempDirectory("projectflow-h2-backup-unknown-");
        try {
            EmbeddedH2BackupService service = new EmbeddedH2BackupService(
                new ObjectMapper().registerModule(new JavaTimeModule())
            );
            String url = "jdbc:h2:file:" + root.resolve("unknown").toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                connection.createStatement().execute("CREATE TABLE users(id UUID)");
            }
            EmbeddedH2BackupService.BackupArtifact artifact = service.createBackup(
                newDataSource(url),
                root.resolve("backups"),
                new EmbeddedH2BackupService.BackupMetadata("3.10", "v3.9", "UNKNOWN", "", "2")
            );
            assertThatThrownBy(() -> service.restoreIntoIsolated(
                artifact, root.resolve("isolated"), "unknown", new DatabaseSchemaSignature()
            )).isInstanceOf(EmbeddedH2BackupService.BackupException.class)
                .extracting("code")
                .isEqualTo("RESTORE_BLOCKED");
        } finally {
            deleteTree(root);
        }
    }

    private org.springframework.jdbc.datasource.DriverManagerDataSource newDataSource(String url) {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void runV39Baseline(Connection connection) throws Exception {
        try (Reader reader = new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("db/migration/V1__v39_schema_baseline.sql"),
            StandardCharsets.UTF_8
        )) {
            RunScript.execute(connection, reader);
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
