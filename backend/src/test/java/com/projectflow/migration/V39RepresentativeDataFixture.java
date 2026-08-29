package com.projectflow.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * Representative V3.9 records used by the exact-old-code release proof. The
 * values are deliberately synthetic and non-secret.
 */
final class V39RepresentativeDataFixture {
    private static final String LEGACY_CREDENTIAL = "fixture-credential-v39";

    private final UUID user = UUID.randomUUID();
    private final UUID project = UUID.randomUUID();
    private final UUID memory = UUID.randomUUID();
    private final UUID fact = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();
    private final UUID snapshot = UUID.randomUUID();
    private final UUID correction = UUID.randomUUID();
    private final UUID candidate = UUID.randomUUID();
    private final UUID provider = UUID.randomUUID();

    void seed(Connection connection) throws SQLException {
        execute(connection, """
            INSERT INTO users(created_at,updated_at,id,username,email,password_hash)
            VALUES (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'%s','fixture-user','fixture@example.invalid','fixture-hash')
            """.formatted(user));
        execute(connection, """
            INSERT INTO projects(created_at,updated_at,id,user_id,status,name,description,tech_stack)
            VALUES (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'%s','%s','BUILDING','V3.9 fixture','synthetic','[]')
            """.formatted(project, user));
        execute(connection, """
            INSERT INTO project_memories(version,created_at,updated_at,id,project_id,current_stage,positioning,
                technical_decisions,current_risks)
            VALUES (7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'%s','%s','V3.9 final','continuity fixture',
                '["history-single-source"]','[]')
            """.formatted(memory, project));
        execute(connection, """
            INSERT INTO project_facts(created_at,updated_at,occurred_from,occurred_to,timeline_event_at,
                timeline_day_key,timeline_week_key,timeline_month_key,id,project_id,confidence,currentness,
                epistemic_status,origin,record_status,validation_status,fact_fingerprint,revision,title,
                fact_statement,evidence_refs,limitations)
            VALUES (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
                TIMESTAMP WITH TIME ZONE '2026-08-25 00:00:00+00',
                TIMESTAMP WITH TIME ZONE '2026-08-25 00:00:00+00',
                TIMESTAMP WITH TIME ZONE '2026-08-25 00:00:00+00',
                '2026-08-25','2026-W35','2026-08','%s','%s','HIGH','CURRENT','OBSERVED',
                'INCREMENTAL_SCAN','RECORDED','VALIDATED','fact-fingerprint-v39','fact-revision-v39',
                'Synthetic fact','Evidence-backed V3.9 fact','["source:v39-fixture"]','[]')
            """.formatted(fact, project));
        execute(connection, """
            INSERT INTO project_history_events(created_at,occurred_at,updated_at,id,project_id,history_scope,
                rewrite_state,authority,epistemic_status,event_category,source_type,transition_type,payload_hash,
                stable_event_key,project_revision,source_revision,source_identity,affected_paths_json,coverage_json,
                evidence_refs_json,limitations_json,relation_refs_json,safe_source_label,subject_keys_json)
            VALUES (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'%s','%s','CURRENT','CURRENT',
                'SOURCE_BACKED','OBSERVED','PROJECT_FACT','PROJECT_FACT','CREATED','payload-hash-v39',
                'stable-event-v39','project-revision-v39','source-revision-v39','fixture-source','[]','{}',
                '["source:v39-fixture"]','[]','[]','fixture','["project:v39-fixture"]')
            """.formatted(event, project));
        execute(connection, """
            INSERT INTO project_history_snapshots(source_event_count,continuity_dirty_generation,created_at,
                updated_at,id,project_id,status,prompt_version,strategy_version,source_event_fingerprint,
                continuity_dirty_reason,continuity_dirty_revision,project_revision,chapters_json,coverage_json,
                diagnostics_json,overview_json,stories_json,threads_json)
            VALUES (1,7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'%s','%s','READY','v39','v39',
                'snapshot-fingerprint-v39','FACT_INGESTION','dirty-revision-v39','project-revision-v39','[]',
                '{"sourceEventCount":1}','{"continuity":"v39"}',
                '{"currentProjectState":{"stateRevision":"state-revision-v39"}}','[]','[]')
            """.formatted(snapshot, project));
        execute(connection, """
            INSERT INTO project_history_corrections(created_at,updated_at,version,actor_user_id,id,project_id,
                status,target_type,correction_type,source_fingerprint,target_membership_fingerprint,
                before_presentation_revision,target_id,target_ids_json,target_membership_refs_json,declared_title)
            VALUES (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'%s','%s','%s','ACTIVE','EVENT','TITLE',
                'correction-fingerprint-v39','membership-fingerprint-v39','project-revision-v39',
                'stable-event-v39','["stable-event-v39"]','["stable-event-v39"]','Corrected V3.9 title')
            """.formatted(user, correction, project));
        execute(connection, """
            INSERT INTO project_agent_candidates(created_at,id,project_id,currentness,epistemic_status,
                candidate_type,validation_status,source_agent_id,source_revision,assertion_text,evidence_refs,limitations)
            VALUES (CURRENT_TIMESTAMP,'%s','%s','CURRENT','PROCESS_EVIDENCE','WORK_RESULT',
                'PENDING_ENGINEERING_VALIDATION','fixture-agent','candidate-revision-v39','synthetic',
                '["source:v39-fixture"]','["process evidence only"]')
            """.formatted(candidate, project));
        execute(connection, """
            INSERT INTO ai_providers(default_enabled,max_tokens,supports_json_mode,supports_reasoning,
                supports_reasoning_control,temperature,created_at,updated_at,id,user_id,auth_mode,protocol,type,
                name,model_name,base_url,api_key,purpose_tags,safe_headers)
            VALUES (TRUE,2048,TRUE,TRUE,TRUE,0.1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'%s','%s',
                'PROTOCOL_DEFAULT','OPENAI_RESPONSES','OPENAI','Legacy fixture','legacy-model',
                'https://example.invalid/v1','%s','["V39_FIXTURE"]','{}')
            """.formatted(provider, user, LEGACY_CREDENTIAL));
    }

    void assertLegacyRecords(Connection connection) throws SQLException {
        assertProtectedIdentityAndState(connection);
        assertThat(queryString(connection, "SELECT model_name FROM ai_providers WHERE id='" + provider + "'"))
            .isEqualTo("legacy-model");
        assertThat(queryString(connection, "SELECT protocol FROM ai_providers WHERE id='" + provider + "'"))
            .isEqualTo("OPENAI_RESPONSES");
        assertThat(queryString(connection, "SELECT api_key FROM ai_providers WHERE id='" + provider + "'"))
            .isEqualTo(LEGACY_CREDENTIAL);
    }

    String assertMigratedRecords(Connection connection) throws SQLException {
        assertProtectedIdentityAndState(connection);
        assertThat(queryString(connection, "SELECT api_key FROM ai_providers WHERE id='" + provider + "'"))
            .isNull();
        String secretRef = queryString(connection, "SELECT secret_ref FROM ai_providers WHERE id='" + provider + "'");
        assertThat(secretRef).isEqualTo("memory:v1:" + provider);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM ai_providers WHERE api_key IS NOT NULL "
            + "AND TRIM(api_key) <> ''")).isZero();
        return secretRef;
    }

    private void assertProtectedIdentityAndState(Connection connection) throws SQLException {
        for (String table : List.of(
            "projects", "project_memories", "project_facts", "project_history_events",
            "project_history_snapshots", "project_history_corrections", "project_agent_candidates",
            "ai_providers"
        )) {
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM " + table)).as(table).isEqualTo(1);
        }
        assertThat(queryString(connection, "SELECT id FROM projects")).isEqualTo(project.toString());
        assertThat(queryString(connection, "SELECT current_stage FROM project_memories"))
            .isEqualTo("V3.9 final");
        assertThat(queryLong(connection, "SELECT version FROM project_memories")).isEqualTo(7);
        assertThat(queryString(connection, "SELECT revision FROM project_facts"))
            .isEqualTo("fact-revision-v39");
        assertThat(queryString(connection, "SELECT fact_fingerprint FROM project_facts"))
            .isEqualTo("fact-fingerprint-v39");
        assertThat(queryString(connection, "SELECT project_revision FROM project_history_events"))
            .isEqualTo("project-revision-v39");
        assertThat(queryString(connection, "SELECT payload_hash FROM project_history_events"))
            .isEqualTo("payload-hash-v39");
        assertThat(queryString(connection, "SELECT stable_event_key FROM project_history_events"))
            .isEqualTo("stable-event-v39");
        assertThat(queryString(connection, "SELECT source_event_fingerprint FROM project_history_snapshots"))
            .isEqualTo("snapshot-fingerprint-v39");
        assertThat(queryLong(connection, "SELECT continuity_dirty_generation FROM project_history_snapshots"))
            .isEqualTo(7);
        assertThat(queryString(connection, "SELECT continuity_dirty_revision FROM project_history_snapshots"))
            .isEqualTo("dirty-revision-v39");
        assertThat(queryString(connection, "SELECT overview_json FROM project_history_snapshots"))
            .contains("state-revision-v39");
        assertThat(queryString(connection, "SELECT source_fingerprint FROM project_history_corrections"))
            .isEqualTo("correction-fingerprint-v39");
        assertThat(queryString(connection, "SELECT target_membership_fingerprint FROM project_history_corrections"))
            .isEqualTo("membership-fingerprint-v39");
        assertThat(queryString(connection, "SELECT epistemic_status FROM project_agent_candidates"))
            .isEqualTo("PROCESS_EVIDENCE");
        assertThat(queryString(connection, "SELECT source_revision FROM project_agent_candidates"))
            .isEqualTo("candidate-revision-v39");
        assertThat(queryString(connection, "SELECT model_name FROM ai_providers"))
            .isEqualTo("legacy-model");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
