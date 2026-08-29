package com.projectflow.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.h2.tools.Restore;
import org.springframework.stereotype.Component;

/**
 * Creates and validates transactional H2 backup zips.  Restore deliberately
 * stops at an isolated target; the caller must perform the final offline
 * switch after protecting the current database.
 */
@Component
public final class EmbeddedH2BackupService {
    private static final String CONTRACT_VERSION = "v1";
    private static final String CREATION_METHOD = "H2_BACKUP_TO";
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final int MAX_BACKUP_ARTIFACTS = 5;
    private static final Pattern BACKUP_ID = Pattern.compile("h2-[0-9a-fA-F-]{36}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final ObjectMapper objectMapper;

    public EmbeddedH2BackupService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public BackupArtifact createBackup(
        DataSource dataSource,
        Path backupDirectory,
        BackupMetadata metadata
    ) {
        String backupId = "h2-" + UUID.randomUUID();
        Path tempPayload = backupDirectory.resolve(backupId + ".zip.tmp");
        Path payload = backupDirectory.resolve(backupId + ".zip");
        Path tempManifest = backupDirectory.resolve(backupId + ".manifest.json.tmp");
        Path manifestPath = backupDirectory.resolve(backupId + ".manifest.json");
        try {
            Files.createDirectories(backupDirectory);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("BACKUP TO '" + sqlPath(tempPayload) + "'");
            }
            if (!Files.isRegularFile(tempPayload) || Files.size(tempPayload) <= 0) {
                throw new BackupException("BACKUP_FAILED", "H2 backup payload was not created");
            }
            forceFile(tempPayload);
            move(tempPayload, payload);
            long payloadBytes = Files.size(payload);
            String sha256 = sha256(payload);
            BackupManifest manifest = new BackupManifest(
                backupId,
                safe(metadata.productVersion()),
                safe(metadata.sourceVersion()),
                "h2",
                Instant.now(),
                safe(metadata.schemaClassification()),
                safe(metadata.flywayCurrentVersion()),
                safe(metadata.flywayTargetVersion()),
                payload.getFileName().toString(),
                payloadBytes,
                sha256,
                true,
                CREATION_METHOD,
                CONTRACT_VERSION
            );
            Files.writeString(
                tempManifest,
                objectMapper.writeValueAsString(manifest),
                StandardCharsets.UTF_8
            );
            forceFile(tempManifest);
            move(tempManifest, manifestPath);
            return new BackupArtifact(payload, manifestPath, manifest);
        } catch (BackupException exception) {
            cleanup(tempPayload, payload, tempManifest, manifestPath);
            throw exception;
        } catch (Exception exception) {
            cleanup(tempPayload, payload, tempManifest, manifestPath);
            throw new BackupException("BACKUP_FAILED", "H2 backup failed", exception);
        }
    }

    public BackupValidation validate(BackupArtifact artifact) {
        return validate(artifact.payload(), artifact.manifestPath());
    }

    public BackupValidation validate(Path payload, Path manifestPath) {
        try {
            if (!Files.isRegularFile(manifestPath) || Files.size(manifestPath) > MAX_MANIFEST_BYTES) {
                throw invalid("Backup manifest is missing or too large");
            }
            BackupManifest manifest = objectMapper.readValue(Files.readString(manifestPath), BackupManifest.class);
            if (!manifest.complete()
                || !"h2".equalsIgnoreCase(manifest.databaseType())
                || !CONTRACT_VERSION.equals(manifest.dataDirectoryContractVersion())
                || !CREATION_METHOD.equals(manifest.creationMethod())
                || manifest.createdAt() == null
                || manifest.backupId() == null
                || !BACKUP_ID.matcher(manifest.backupId()).matches()
                || manifest.payloadFile() == null
                || !manifest.payloadFile().equals(manifest.backupId() + ".zip")
                || Path.of(manifest.payloadFile()).isAbsolute()
                || manifest.payloadFile().contains("/")
                || manifest.payloadFile().contains("\\")
                || !sameDirectory(payload, manifestPath)
                || !manifestPath.getFileName().toString().equals(manifest.backupId() + ".manifest.json")
                || !payload.getFileName().toString().equals(manifest.payloadFile())) {
                throw invalid("Backup manifest is incomplete or escapes its directory");
            }
            if (manifest.payloadBytes() <= 0
                || manifest.sha256() == null
                || !SHA256.matcher(manifest.sha256()).matches()) {
                throw invalid("Backup manifest integrity metadata is invalid");
            }
            if (!Files.isRegularFile(payload) || Files.size(payload) != manifest.payloadBytes()) {
                throw invalid("Backup payload size is invalid");
            }
            String actual = sha256(payload);
            if (!MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                manifest.sha256().getBytes(StandardCharsets.US_ASCII)
            )) {
                throw invalid("Backup payload checksum is invalid");
            }
            return new BackupValidation(manifest, payload, manifestPath);
        } catch (BackupException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BackupException("BACKUP_INVALID", "Backup validation failed", exception);
        }
    }

    /**
     * Restores a validated archive into a new directory and verifies that the
     * result has a known V3.9 pre-upgrade or current schema.  No current database file is moved
     * or deleted by this method.
     */
    public IsolatedRestore restoreIntoIsolated(
        BackupArtifact artifact,
        Path targetDirectory,
        String databaseName,
        DatabaseSchemaSignature signature
    ) {
        BackupValidation validation = validate(artifact);
        if (databaseName == null || !databaseName.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new BackupException("RESTORE_BLOCKED", "Database name is invalid");
        }
        String schemaClassification = validation.manifest().schemaClassification();
        if (!DatabaseSchemaSignature.SchemaClassification.KNOWN_V39.name().equals(schemaClassification)
            && !DatabaseSchemaSignature.SchemaClassification.KNOWN_CURRENT.name().equals(schemaClassification)) {
            throw new BackupException("RESTORE_BLOCKED", "Backup schema classification is not restorable");
        }
        try {
            if (Files.exists(targetDirectory)) {
                try (var children = Files.list(targetDirectory)) {
                    if (children.findAny().isPresent()) {
                        throw new BackupException("RESTORE_BLOCKED", "Isolated restore target must be empty");
                    }
                }
            }
            Files.createDirectories(targetDirectory);
            Restore.execute(
                validation.payload().toString(),
                targetDirectory.toString(),
                databaseName
            );
            Path restoredBase = targetDirectory.resolve(databaseName);
            String url = "jdbc:h2:file:" + restoredBase.toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r";
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                DatabaseSchemaSignature.Inspection inspection = signature.inspect(connection);
                if (!inspection.matchesRestorableSchema()) {
                    throw new BackupException("RESTORE_VERIFICATION_FAILED", "Restored H2 schema is neither a frozen V3.9 nor a known current schema");
                }
            }
            return new IsolatedRestore(restoredBase, validation.manifest());
        } catch (BackupException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BackupException("RESTORE_VERIFICATION_FAILED", "H2 restore verification failed", exception);
        }
    }

    private String sqlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("'", "''");
    }

    private boolean sameDirectory(Path payload, Path manifestPath) {
        Path payloadParent = payload.toAbsolutePath().normalize().getParent();
        Path manifestParent = manifestPath.toAbsolutePath().normalize().getParent();
        return payloadParent != null && payloadParent.equals(manifestParent);
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic move is required for migration backup artifacts", exception);
        }
    }

    private void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void pruneOldBackups(Path backupDirectory, Path newestPayload) throws IOException {
        List<Path> payloads;
        try (var stream = Files.list(backupDirectory)) {
            payloads = stream
                .filter(path -> path.getFileName().toString().startsWith("h2-")
                    && path.getFileName().toString().endsWith(".zip"))
                .sorted(Comparator.comparingLong(this::lastModified).reversed())
                .toList();
        }
        for (Path payload : payloads.subList(Math.min(MAX_BACKUP_ARTIFACTS, payloads.size()), payloads.size())) {
            if (payload.equals(newestPayload)) continue;
            Files.deleteIfExists(payload);
            String name = payload.getFileName().toString();
            String backupId = name.substring(0, name.length() - ".zip".length());
            Files.deleteIfExists(backupDirectory.resolve(backupId + ".manifest.json"));
        }
    }

    /**
     * Retains only the bounded set of completed upgrade backups.  Callers must
     * invoke this only after the associated migration has succeeded; failed
     * migrations intentionally retain every usable pre-upgrade archive.
     */
    public void pruneCompletedBackups(Path backupDirectory) {
        try {
            if (!Files.isDirectory(backupDirectory)) return;
            pruneOldBackups(backupDirectory, null);
        } catch (IOException exception) {
            throw new BackupException("BACKUP_RETENTION_FAILED", "Completed backup retention failed", exception);
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private void cleanup(Path... paths) {
        for (Path path : paths) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", " ").trim();
    }

    private BackupException invalid(String message) {
        return new BackupException("BACKUP_INVALID", message);
    }

    public record BackupMetadata(
        String productVersion,
        String sourceVersion,
        String schemaClassification,
        String flywayCurrentVersion,
        String flywayTargetVersion
    ) {}

    public record BackupManifest(
        String backupId,
        String productVersion,
        String sourceVersion,
        String databaseType,
        Instant createdAt,
        String schemaClassification,
        String flywayCurrentVersion,
        String flywayTargetVersion,
        String payloadFile,
        long payloadBytes,
        String sha256,
        boolean complete,
        String creationMethod,
        String dataDirectoryContractVersion
    ) {}

    public record BackupArtifact(Path payload, Path manifestPath, BackupManifest manifest) {}

    public record BackupValidation(BackupManifest manifest, Path payload, Path manifestPath) {}

    public record IsolatedRestore(Path restoredDatabaseBase, BackupManifest manifest) {}

    public static final class BackupException extends RuntimeException {
        private final String code;

        public BackupException(String code, String message) {
            super(message);
            this.code = code;
        }

        public BackupException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
