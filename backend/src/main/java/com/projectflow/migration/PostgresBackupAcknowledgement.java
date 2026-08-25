package com.projectflow.migration;

import org.springframework.stereotype.Component;

/** Enforces an explicit operator acknowledgement for non-empty PostgreSQL upgrades. */
@Component
public final class PostgresBackupAcknowledgement {
    public void requireIfNeeded(
        String dialect,
        boolean nonEmpty,
        boolean pendingMigration,
        boolean confirmed
    ) {
        if ("postgresql".equalsIgnoreCase(dialect) && nonEmpty && pendingMigration && !confirmed) {
            throw new SchemaMigrationException(
                "BACKUP_REQUIRED",
                "External PostgreSQL has pending schema changes; complete and acknowledge an independent pg_dump backup first."
            );
        }
    }
}
