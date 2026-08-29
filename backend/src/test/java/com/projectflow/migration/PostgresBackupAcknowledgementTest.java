package com.projectflow.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostgresBackupAcknowledgementTest {
    private final PostgresBackupAcknowledgement acknowledgement = new PostgresBackupAcknowledgement();

    @Test
    void blocksNonEmptyPendingPostgresWithoutExplicitConfirmation() {
        assertThatThrownBy(() -> acknowledgement.requireIfNeeded("postgresql", true, true, false))
            .isInstanceOf(SchemaMigrationException.class)
            .extracting("code")
            .isEqualTo("BACKUP_REQUIRED");
    }

    @Test
    void allowsEmptyOrExplicitlyConfirmedPostgres() {
        acknowledgement.requireIfNeeded("postgresql", false, true, false);
        acknowledgement.requireIfNeeded("postgresql", true, true, true);
        acknowledgement.requireIfNeeded("h2", true, true, false);
    }
}
