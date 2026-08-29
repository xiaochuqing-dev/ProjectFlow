package com.projectflow.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

class DatabaseSchemaSignatureTest {
    private final DatabaseSchemaSignature signature = new DatabaseSchemaSignature();

    @Test
    void recognizesFrozenV39ColumnsKeysUniquesAndIndexes() throws Exception {
        String url = "jdbc:h2:mem:schema-signature-v39;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            runV39Baseline(connection);
            DatabaseSchemaSignature.Inspection inspection = signature.inspect(connection);

            assertThat(inspection.classification()).isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_V39);
            assertThat(inspection.missingEntries()).isEmpty();
            assertThat(inspection.extraEntries()).isEmpty();
        }
    }

    @Test
    void recognizesCurrentSchemaAfterSecretRefMigration() throws Exception {
        String url = "jdbc:h2:mem:schema-signature-current;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            runV39Baseline(connection);
            connection.createStatement().execute(
                "ALTER TABLE ai_providers ADD COLUMN secret_ref VARCHAR(200)"
            );

            assertThat(signature.inspect(connection).classification())
                .isEqualTo(DatabaseSchemaSignature.SchemaClassification.KNOWN_CURRENT);
        }
    }

    @Test
    void rejectsIndexDriftAndReportsTheMissingIdentity() throws Exception {
        String url = "jdbc:h2:mem:schema-signature-index-drift;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            runV39Baseline(connection);
            connection.createStatement().execute("DROP INDEX IF EXISTS idx_project_fact_batch");

            DatabaseSchemaSignature.Inspection inspection = signature.inspect(connection);
            assertThat(inspection.classification()).isEqualTo(DatabaseSchemaSignature.SchemaClassification.UNKNOWN);
            assertThat(inspection.missingEntries())
                .contains("index.project_facts.idx_project_fact_batch.batch_id");
        }
    }

    @Test
    void rejectsPrimaryKeyDrift() throws Exception {
        String url = "jdbc:h2:mem:schema-signature-primary-drift;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            runV39Baseline(connection);
            connection.createStatement().execute("ALTER TABLE users DROP PRIMARY KEY");

            DatabaseSchemaSignature.Inspection inspection = signature.inspect(connection);
            assertThat(inspection.classification()).isEqualTo(DatabaseSchemaSignature.SchemaClassification.UNKNOWN);
            assertThat(inspection.missingEntries()).contains("primary.users.id");
        }
    }

    @Test
    void treatsPublicViewsAndSequencesAsUnknownSchemaObjects() throws Exception {
        String url = "jdbc:h2:mem:schema-signature-object-drift;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("CREATE VIEW stray_view AS SELECT 1 AS marker");
            connection.createStatement().execute("CREATE SEQUENCE stray_sequence");

            DatabaseSchemaSignature.Inspection inspection = signature.inspect(connection);

            assertThat(inspection.classification()).isEqualTo(DatabaseSchemaSignature.SchemaClassification.UNKNOWN);
            assertThat(inspection.extraEntries())
                .contains("object.view.stray_view", "object.sequence.stray_sequence");
        }
    }

    private void runV39Baseline(Connection connection) throws Exception {
        try (Reader reader = new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("db/migration/V1__v39_schema_baseline.sql"),
            StandardCharsets.UTF_8
        )) {
            RunScript.execute(connection, reader);
        }
    }
}
