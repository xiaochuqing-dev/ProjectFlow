package com.projectflow.migration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

/**
 * Performs the release-only schema preflight before allowing Flyway to run.
 * No baseline is inferred for an unknown or partial database.
 */
@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class ProjectFlowFlywayMigrationStrategy implements FlywayMigrationStrategy {
    private static final String SCHEMA_STATE_FILE = "projectflow-schema-state.json";
    private static final String SCHEMA_STATE_VERSION = "projectflow-schema-state-v1";
    private final DatabaseSchemaSignature signature;
    private final EmbeddedH2BackupService h2BackupService;
    private final PostgresBackupAcknowledgement postgresBackupAcknowledgement;
    private final Path backupDirectory;
    private final boolean postgresBackupConfirmed;

    public ProjectFlowFlywayMigrationStrategy(
        DatabaseSchemaSignature signature,
        EmbeddedH2BackupService h2BackupService,
        PostgresBackupAcknowledgement postgresBackupAcknowledgement,
        @Value("${projectflow.migration.backup-directory:./.projectflow/local-data/backups}") String backupDirectory,
        @Value("${projectflow.migration.postgres-backup-confirmed:false}") boolean postgresBackupConfirmed
    ) {
        this.signature = signature;
        this.h2BackupService = h2BackupService;
        this.postgresBackupAcknowledgement = postgresBackupAcknowledgement;
        this.backupDirectory = Path.of(backupDirectory).toAbsolutePath().normalize();
        this.postgresBackupConfirmed = postgresBackupConfirmed;
    }

    @Override
    public void migrate(Flyway flyway) {
        DatabaseSchemaSignature.Inspection inspection;
        try {
            assertMigrationConfiguration(flyway);
            DataSource dataSource = flyway.getConfiguration().getDataSource();
            inspection = signature.inspect(dataSource);
            if (!"h2".equalsIgnoreCase(inspection.dialect())
                && !"postgresql".equalsIgnoreCase(inspection.dialect())) {
                throw new SchemaMigrationException(
                    "UNSUPPORTED_DATABASE_DIALECT",
                    "Only H2 embedded and PostgreSQL external databases are supported for schema migration."
                );
            }
            MigrationInfo[] pending = flyway.info().pending();
            boolean hasPending = pending != null && pending.length > 0;
            if (inspection.hasFlywayHistory()) {
                validateHistory(flyway);
                validateManagedSchemaState(flyway, inspection);
                EmbeddedH2BackupService.BackupArtifact backup = hasPending
                    ? requirePreMigrationProtection(dataSource, inspection, flyway)
                    : null;
                runMigrate(flyway);
                completeH2Retention(backup);
                recordSchemaState(dataSource, flyway);
                return;
            }
            switch (inspection.classification()) {
                case EMPTY -> {
                    runMigrate(flyway);
                    recordSchemaState(dataSource, flyway);
                }
                case KNOWN_V39 -> {
                    EmbeddedH2BackupService.BackupArtifact backup =
                        requirePreMigrationProtection(dataSource, inspection, flyway);
                    baselineKnownV39(flyway);
                    runMigrate(flyway);
                    completeH2Retention(backup);
                    recordSchemaState(dataSource, flyway);
                }
                case KNOWN_CURRENT -> throw new SchemaMigrationException(
                    "UNSUPPORTED_LEGACY_SCHEMA",
                    "A current-looking schema without Flyway history cannot be safely adopted; migration is blocked."
                );
                case UNKNOWN -> throw new SchemaMigrationException(
                    "UNSUPPORTED_LEGACY_SCHEMA",
                    "Non-empty database does not match the frozen V3.9 schema signature; migration is blocked."
                );
            }
        } catch (SchemaMigrationException exception) {
            throw exception;
        } catch (EmbeddedH2BackupService.BackupException exception) {
            throw new SchemaMigrationException(exception.code(), exception.getMessage(), exception);
        } catch (FlywayException exception) {
            throw new SchemaMigrationException("SCHEMA_MIGRATION_BLOCKED", "Flyway schema migration failed", exception);
        } catch (RuntimeException exception) {
            throw new SchemaMigrationException("SCHEMA_MIGRATION_BLOCKED", "Schema migration preflight failed", exception);
        }
    }

    private void assertMigrationConfiguration(Flyway flyway) {
        if (flyway.getConfiguration().isBaselineOnMigrate()
            || flyway.getConfiguration().getBaselineVersion() == null
            || !MigrationVersion.fromVersion("1").equals(flyway.getConfiguration().getBaselineVersion())
            || !flyway.getConfiguration().isCleanDisabled()
            || flyway.getConfiguration().isOutOfOrder()) {
            throw new SchemaMigrationException(
                "SCHEMA_MIGRATION_CONFIGURATION_INVALID",
                "Flyway requires baselineOnMigrate=false, explicit baseline version V1, cleanDisabled=true, and ordered migrations."
            );
        }
    }

    private void validateHistory(Flyway flyway) {
        try {
            // A release preflight must validate applied history without treating the
            // next bounded migration as an error before the protected migrate call.
            Flyway validationFlyway = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .ignoreMigrationPatterns("*:pending")
                .load();
            validationFlyway.validate();
        } catch (FlywayException exception) {
            throw new SchemaMigrationException("SCHEMA_MIGRATION_BLOCKED", "Flyway history validation failed", exception);
        }
    }

    private void validateManagedSchemaState(
        Flyway flyway,
        DatabaseSchemaSignature.Inspection inspection
    ) {
        MigrationInfo current = flyway.info().current();
        String version = current == null || current.getVersion() == null
            ? "" : current.getVersion().getVersion();
        boolean expected = ("1".equals(version)
            && inspection.classification() == DatabaseSchemaSignature.SchemaClassification.KNOWN_V39)
            || ("2".equals(version)
                && inspection.classification() == DatabaseSchemaSignature.SchemaClassification.KNOWN_CURRENT);
        if (!expected) {
            throw new SchemaMigrationException(
                "SCHEMA_MIGRATION_BLOCKED",
                "Flyway history and the exact managed schema signature do not match; migration is blocked."
            );
        }
    }

    private void baselineKnownV39(Flyway flyway) {
        try {
            flyway.baseline();
        } catch (FlywayException exception) {
            throw new SchemaMigrationException("SCHEMA_MIGRATION_BLOCKED", "Known V3.9 baseline failed", exception);
        }
    }

    private void runMigrate(Flyway flyway) {
        try {
            flyway.migrate();
        } catch (FlywayException exception) {
            throw new SchemaMigrationException("SCHEMA_MIGRATION_BLOCKED", "Flyway migration failed", exception);
        }
    }

    private EmbeddedH2BackupService.BackupArtifact requirePreMigrationProtection(
        DataSource dataSource,
        DatabaseSchemaSignature.Inspection inspection,
        Flyway flyway
    ) {
        boolean pending = flyway.info().pending() != null && flyway.info().pending().length > 0;
        if (!pending) return null;
        if ("h2".equalsIgnoreCase(inspection.dialect())) {
            return h2BackupService.createBackup(
                dataSource,
                backupDirectory,
                new EmbeddedH2BackupService.BackupMetadata(
                    "3.10",
                    "v3.9",
                    inspection.classification().name(),
                    currentVersion(flyway),
                    targetVersion(flyway)
                )
            );
        }
        if (!"postgresql".equalsIgnoreCase(inspection.dialect())) {
            throw new SchemaMigrationException(
                "UNSUPPORTED_DATABASE_DIALECT",
                "Only H2 embedded and PostgreSQL external databases are supported for schema migration."
            );
        }
        postgresBackupAcknowledgement.requireIfNeeded(
            inspection.dialect(),
            inspection.classification() != DatabaseSchemaSignature.SchemaClassification.EMPTY,
            true,
            postgresBackupConfirmed
        );
        return null;
    }

    private void completeH2Retention(EmbeddedH2BackupService.BackupArtifact backup) {
        if (backup != null) h2BackupService.pruneCompletedBackups(backupDirectory);
    }

    private void recordSchemaState(DataSource dataSource, Flyway flyway) {
        try {
            DatabaseSchemaSignature.Inspection inspection = signature.inspect(dataSource);
            if (!inspection.matchesRestorableSchema()) {
                throw new SchemaMigrationException(
                    "SCHEMA_MIGRATION_BLOCKED",
                    "The migrated database did not match a known restorable schema."
                );
            }
            String content = "{\n"
                + "  \"schemaVersion\":\"" + SCHEMA_STATE_VERSION + "\",\n"
                + "  \"databaseType\":\"" + escape(inspection.dialect()) + "\",\n"
                + "  \"schemaClassification\":\"" + inspection.classification().name() + "\",\n"
                + "  \"schemaFingerprint\":\"" + escape(inspection.actualFingerprint()) + "\",\n"
                + "  \"flywayCurrentVersion\":\"" + escape(currentVersion(flyway)) + "\",\n"
                + "  \"updatedAt\":\"" + Instant.now() + "\"\n"
                + "}\n";
            Files.createDirectories(backupDirectory);
            Path target = backupDirectory.resolve(SCHEMA_STATE_FILE);
            Path temporary = backupDirectory.resolve(SCHEMA_STATE_FILE + ".tmp");
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IllegalStateException("Atomic schema state move is required", exception);
            }
        } catch (SchemaMigrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SchemaMigrationException(
                "SCHEMA_MIGRATION_BLOCKED",
                "Trusted schema state could not be recorded.",
                exception
            );
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", " ").replace("\n", " ");
    }

    private String currentVersion(Flyway flyway) {
        MigrationInfo current = flyway.info().current();
        return current == null || current.getVersion() == null ? "" : current.getVersion().getVersion();
    }

    private String targetVersion(Flyway flyway) {
        MigrationInfo[] pending = flyway.info().pending();
        if (pending == null || pending.length == 0 || pending[pending.length - 1].getVersion() == null) return "";
        return pending[pending.length - 1].getVersion().getVersion();
    }
}
