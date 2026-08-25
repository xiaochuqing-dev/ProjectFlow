package com.projectflow.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectFlowV39DataContinuityMigrationTest {
    @Test
    void preservesRepresentativeV39FactsHistoryCorrectionsAndProviderMetadata() throws Exception {
        Path root = Files.createTempDirectory("projectflow-v39-continuity-");
        try {
            String url = "jdbc:h2:file:" + root.resolve("projectflow").toAbsolutePath().normalize()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            Fixture fixture = new Fixture(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
            );
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                RunScript.execute(connection, new InputStreamReader(
                    getClass().getClassLoader().getResourceAsStream("db/migration/V1__v39_schema_baseline.sql"),
                    StandardCharsets.UTF_8
                ));
                seed(connection, fixture);
            }

            Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .baselineVersion("1")
                .validateOnMigrate(true)
                .load();
            new ProjectFlowFlywayMigrationStrategy(
                new DatabaseSchemaSignature(),
                new EmbeddedH2BackupService(new ObjectMapper().findAndRegisterModules()),
                new PostgresBackupAcknowledgement(),
                root.resolve("backups").toString(),
                false
            ).migrate(flyway);

            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                for (String table : List.of(
                    "projects", "project_memories", "project_facts", "project_history_events",
                    "project_history_snapshots", "project_history_corrections", "project_agent_candidates",
                    "ai_providers"
                )) {
                    assertThat(queryInt(connection, "SELECT COUNT(*) FROM " + table)).isEqualTo(1);
                }
                assertThat(queryString(connection, "SELECT revision FROM project_facts"))
                    .isEqualTo("fact-revision-v39");
                assertThat(queryString(connection, "SELECT fact_fingerprint FROM project_facts"))
                    .isEqualTo("fact-fingerprint-v39");
                assertThat(queryString(connection, "SELECT project_revision FROM project_history_events"))
                    .isEqualTo("project-revision-v39");
                assertThat(queryString(connection, "SELECT payload_hash FROM project_history_events"))
                    .isEqualTo("payload-hash-v39");
                assertThat(queryString(connection, "SELECT source_event_fingerprint FROM project_history_snapshots"))
                    .isEqualTo("snapshot-fingerprint-v39");
                assertThat(queryString(connection, "SELECT source_fingerprint FROM project_history_corrections"))
                    .isEqualTo("correction-fingerprint-v39");
                assertThat(queryString(connection, "SELECT source_revision FROM project_agent_candidates"))
                    .isEqualTo("candidate-revision-v39");
                assertThat(queryString(connection, "SELECT model_name FROM ai_providers"))
                    .isEqualTo("legacy-model");
                assertThat(queryString(connection, "SELECT api_key FROM ai_providers"))
                    .isEqualTo("legacy-non-provider-sentinel");
                assertThat(queryString(connection, "SELECT secret_ref FROM ai_providers")).isNull();
                assertThat(queryInt(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                    + "WHERE success = TRUE AND version IS NOT NULL"))
                    .isEqualTo(2);
            }
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Best-effort cleanup of the isolated synthetic fixture.
                    }
                });
            }
        }
    }

    private void seed(Connection connection, Fixture f) throws Exception {
        String now = "TIMESTAMP WITH TIME ZONE '2026-08-25 00:00:00+00'";
        connection.createStatement().executeUpdate("""
            INSERT INTO users(created_at,updated_at,id,username,email,password_hash)
            VALUES (%s,%s,'%s','fixture-user','fixture@example.invalid','fixture-hash')
            """.formatted(now, now, f.user()));
        connection.createStatement().executeUpdate("""
            INSERT INTO projects(created_at,updated_at,id,user_id,status,name,description,tech_stack)
            VALUES (%s,%s,'%s','%s','BUILDING','V3.9 fixture','synthetic','[]')
            """.formatted(now, now, f.project(), f.user()));
        connection.createStatement().executeUpdate("""
            INSERT INTO project_memories(version,created_at,updated_at,id,project_id,current_stage,positioning)
            VALUES (7,%s,%s,'%s','%s','V3.9','continuity fixture')
            """.formatted(now, now, f.memory(), f.project()));
        connection.createStatement().executeUpdate("""
            INSERT INTO project_facts(created_at,updated_at,id,project_id,confidence,origin,record_status,
                fact_fingerprint,revision,title)
            VALUES (%s,%s,'%s','%s','HIGH','INCREMENTAL_SCAN','RECORDED',
                'fact-fingerprint-v39','fact-revision-v39','Synthetic fact')
            """.formatted(now, now, f.fact(), f.project()));
        connection.createStatement().executeUpdate("""
            INSERT INTO project_history_events(created_at,occurred_at,updated_at,id,project_id,history_scope,
                rewrite_state,authority,epistemic_status,event_category,source_type,transition_type,payload_hash,
                stable_event_key,project_revision,source_revision,source_identity,affected_paths_json,coverage_json,
                evidence_refs_json,limitations_json,relation_refs_json,safe_source_label,subject_keys_json)
            VALUES (%s,%s,%s,'%s','%s','CURRENT','CURRENT','SOURCE_BACKED','OBSERVED','PROJECT_FACT','PROJECT_FACT','CREATED',
                'payload-hash-v39','stable-event-v39','project-revision-v39','source-revision-v39','fixture-source',
                '[]','{}','[]','[]','[]','fixture','[]')
            """.formatted(now, now, now, f.event(), f.project()));
        connection.createStatement().executeUpdate("""
            INSERT INTO project_history_snapshots(source_event_count,created_at,updated_at,id,project_id,status,
                prompt_version,strategy_version,source_event_fingerprint,project_revision,chapters_json,coverage_json,
                diagnostics_json,overview_json,stories_json,threads_json)
            VALUES (1,%s,%s,'%s','%s','READY','v39','v39','snapshot-fingerprint-v39',
                'project-revision-v39','[]','{}','{}','{}','[]','[]')
            """.formatted(now, now, f.snapshot(), f.project()));
        connection.createStatement().executeUpdate("""
            INSERT INTO project_history_corrections(created_at,updated_at,actor_user_id,id,project_id,status,
                target_type,correction_type,source_fingerprint,before_presentation_revision,target_id,target_ids_json)
            VALUES (%s,%s,'%s','%s','%s','ACTIVE','EVENT','TITLE','correction-fingerprint-v39',
                'project-revision-v39','stable-event-v39','[\"stable-event-v39\"]')
            """.formatted(now, now, f.user(), f.correction(), f.project()));
        connection.createStatement().executeUpdate("""
            INSERT INTO project_agent_candidates(created_at,id,project_id,epistemic_status,candidate_type,
                validation_status,source_agent_id,source_revision,assertion_text)
            VALUES (%s,'%s','%s','OBSERVED','FACT','VALID','fixture-agent','candidate-revision-v39','synthetic')
            """.formatted(now, f.candidate(), f.project()));
        connection.createStatement().executeUpdate("""
            INSERT INTO ai_providers(default_enabled,max_tokens,temperature,created_at,updated_at,id,user_id,type,
                name,model_name,base_url,api_key)
            VALUES (TRUE,2048,0.1,%s,%s,'%s','%s','OPENAI','Legacy fixture','legacy-model',
                'https://example.invalid/v1','legacy-non-provider-sentinel')
            """.formatted(now, now, f.provider(), f.user()));
    }

    private int queryInt(Connection connection, String sql) throws Exception {
        try (var result = connection.createStatement().executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String queryString(Connection connection, String sql) throws Exception {
        try (var result = connection.createStatement().executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private record Fixture(
        UUID user,
        UUID project,
        UUID memory,
        UUID fact,
        UUID event,
        UUID snapshot,
        UUID correction,
        UUID candidate,
        UUID provider
    ) {}
}
