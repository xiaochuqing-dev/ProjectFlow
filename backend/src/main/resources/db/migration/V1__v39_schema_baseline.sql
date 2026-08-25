CREATE TABLE "public"."agent_signature_feedback"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "corrected_agent_type" CHARACTER VARYING(40) NOT NULL,
    "original_agent_type" CHARACTER VARYING(40) NOT NULL,
    "scope" CHARACTER VARYING(40) NOT NULL,
    "agent_name" CHARACTER VARYING(180) NOT NULL,
    "corrected_task_intent" CHARACTER VARYING
);
ALTER TABLE "public"."agent_signature_feedback" ADD CONSTRAINT "CONSTRAINT_B" PRIMARY KEY("id");
CREATE TABLE "public"."ai_outputs"(
    "from_date" DATE,
    "to_date" DATE,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "type" CHARACTER VARYING(40) NOT NULL,
    "provider" CHARACTER VARYING(60) NOT NULL,
    "title" CHARACTER VARYING(180) NOT NULL,
    "content" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."ai_outputs" ADD CONSTRAINT "CONSTRAINT_AA" PRIMARY KEY("id");
CREATE TABLE "public"."ai_providers"(
    "default_enabled" BOOLEAN NOT NULL,
    "max_tokens" INTEGER NOT NULL,
    "request_timeout_seconds" INTEGER,
    "supports_json_mode" BOOLEAN,
    "supports_reasoning" BOOLEAN,
    "supports_reasoning_control" BOOLEAN,
    "supports_structured_output" BOOLEAN,
    "supports_temperature" BOOLEAN,
    "temperature" FLOAT(53) NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "last_probed_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "auth_mode" CHARACTER VARYING(40),
    "protocol" CHARACTER VARYING(40),
    "type" CHARACTER VARYING(40) NOT NULL,
    "auth_header_name" CHARACTER VARYING(120),
    "name" CHARACTER VARYING(120) NOT NULL,
    "query_key_name" CHARACTER VARYING(120),
    "model_name" CHARACTER VARYING(160) NOT NULL,
    "base_url" CHARACTER VARYING(500) NOT NULL,
    "endpoint_override" CHARACTER VARYING(500),
    "api_key" CHARACTER VARYING,
    "last_probe_profile" CHARACTER VARYING,
    "purpose_tags" CHARACTER VARYING,
    "safe_headers" CHARACTER VARYING
);
ALTER TABLE "public"."ai_providers" ADD CONSTRAINT "CONSTRAINT_82BA" PRIMARY KEY("id");
CREATE TABLE "public"."ai_suggestions"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "resolved_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "material_id" UUID,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(40) NOT NULL,
    "type" CHARACTER VARYING(60) NOT NULL,
    "title" CHARACTER VARYING(180) NOT NULL,
    "payload" CHARACTER VARYING NOT NULL,
    "reason" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."ai_suggestions" ADD CONSTRAINT "CONSTRAINT_E8C" PRIMARY KEY("id");
CREATE TABLE "public"."change_batches"(
    "agent_result_count" INTEGER NOT NULL,
    "attention_count" INTEGER,
    "changed_file_count" INTEGER NOT NULL,
    "fact_count" INTEGER,
    "first_scan" BOOLEAN NOT NULL,
    "new_commit_count" INTEGER NOT NULL,
    "segment_count" INTEGER NOT NULL,
    "worktree_dirty" BOOLEAN,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "fact_occurred_from" TIMESTAMP(6) WITH TIME ZONE,
    "fact_occurred_to" TIMESTAMP(6) WITH TIME ZONE,
    "git_scan_ms" BIGINT NOT NULL,
    "github_inspect_ms" BIGINT NOT NULL,
    "model_segment_ms" BIGINT NOT NULL,
    "scan_finished_at" TIMESTAMP(6) WITH TIME ZONE,
    "scan_started_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "total_scan_ms" BIGINT NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "github_status" CHARACTER VARYING(40),
    "model_status" CHARACTER VARYING(40),
    "remote_relation" CHARACTER VARYING(40),
    "scan_type" CHARACTER VARYING(40),
    "segmentation_mode" CHARACTER VARYING(40),
    "status" CHARACTER VARYING(40) NOT NULL,
    "base_commit_sha" CHARACTER VARYING(64),
    "head_commit_sha" CHARACTER VARYING(64),
    "scan_fingerprint" CHARACTER VARYING(64),
    "model_provider" CHARACTER VARYING(160),
    "analysis_scope" CHARACTER VARYING,
    "branch_name" CHARACTER VARYING(255),
    "fallback_reason" CHARACTER VARYING,
    "warnings" CHARACTER VARYING
);
ALTER TABLE "public"."change_batches" ADD CONSTRAINT "CONSTRAINT_63" PRIMARY KEY("id");
CREATE TABLE "public"."dev_logs"(
    "blocked" BOOLEAN NOT NULL,
    "log_date" DATE NOT NULL,
    "minutes_spent" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "task_id" UUID,
    "category" CHARACTER VARYING(40) NOT NULL,
    "title" CHARACTER VARYING(180) NOT NULL,
    "content" CHARACTER VARYING NOT NULL,
    "tags" CHARACTER VARYING
);
ALTER TABLE "public"."dev_logs" ADD CONSTRAINT "CONSTRAINT_41" PRIMARY KEY("id");
CREATE TABLE "public"."development_segments"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "occurred_from" TIMESTAMP(6) WITH TIME ZONE,
    "occurred_to" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "batch_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "confidence" CHARACTER VARYING(20) NOT NULL,
    "generation_mode" CHARACTER VARYING(40),
    "quality_status" CHARACTER VARYING(40),
    "status" CHARACTER VARYING(40) NOT NULL,
    "model_provider" CHARACTER VARYING(160),
    "title" CHARACTER VARYING(200) NOT NULL,
    "affected_files" CHARACTER VARYING,
    "commit_urls" CHARACTER VARYING,
    "evidence_refs" CHARACTER VARYING,
    "fallback_reason" CHARACTER VARYING,
    "included_agent_result_refs" CHARACTER VARYING,
    "included_commit_refs" CHARACTER VARYING,
    "main_changes" CHARACTER VARYING,
    "plain_summary" CHARACTER VARYING,
    "quality_reason" CHARACTER VARYING,
    "uncertainties" CHARACTER VARYING,
    "user_visible_value" CHARACTER VARYING
);
ALTER TABLE "public"."development_segments" ADD CONSTRAINT "CONSTRAINT_8528" PRIMARY KEY("id");
CREATE TABLE "public"."evidence_bundles"(
    "added_lines" INTEGER NOT NULL,
    "changed_files" INTEGER NOT NULL,
    "deleted_lines" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "work_session_id" UUID NOT NULL,
    "agent_type" CHARACTER VARYING(40) NOT NULL,
    "attribution_confidence" CHARACTER VARYING(40) NOT NULL,
    "branch_name" CHARACTER VARYING(180),
    "agent_claims" CHARACTER VARYING,
    "file_paths" CHARACTER VARYING,
    "objective_evidence" CHARACTER VARYING,
    "sources" CHARACTER VARYING,
    "task_intent" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."evidence_bundles" ADD CONSTRAINT "CONSTRAINT_CE" PRIMARY KEY("id");
CREATE TABLE "public"."import_records"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "dev_log_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "source" CHARACTER VARYING(80),
    "title" CHARACTER VARYING(180) NOT NULL,
    "raw_markdown" CHARACTER VARYING NOT NULL,
    "warnings" CHARACTER VARYING
);
ALTER TABLE "public"."import_records" ADD CONSTRAINT "CONSTRAINT_E3" PRIMARY KEY("id");
CREATE TABLE "public"."model_usage_records"(
    "completion_tokens" INTEGER NOT NULL,
    "prompt_tokens" INTEGER NOT NULL,
    "total_tokens" INTEGER NOT NULL,
    "usage_estimated" BOOLEAN NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "latency_ms" BIGINT NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(30) NOT NULL,
    "error_type" CHARACTER VARYING(80),
    "operation" CHARACTER VARYING(80) NOT NULL,
    "provider_name" CHARACTER VARYING(80) NOT NULL,
    "model_name" CHARACTER VARYING(120) NOT NULL,
    "error_message" CHARACTER VARYING,
    "quality_warnings" CHARACTER VARYING
);
ALTER TABLE "public"."model_usage_records" ADD CONSTRAINT "CONSTRAINT_7" PRIMARY KEY("id");
CREATE TABLE "public"."project_agent_candidates"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "currentness" CHARACTER VARYING(30),
    "epistemic_status" CHARACTER VARYING(30) NOT NULL,
    "candidate_type" CHARACTER VARYING(40) NOT NULL,
    "validation_status" CHARACTER VARYING(50) NOT NULL,
    "source_agent_id" CHARACTER VARYING(160) NOT NULL,
    "source_revision" CHARACTER VARYING(180),
    "assertion_text" CHARACTER VARYING NOT NULL,
    "evidence_refs" CHARACTER VARYING,
    "limitations" CHARACTER VARYING
);
ALTER TABLE "public"."project_agent_candidates" ADD CONSTRAINT "CONSTRAINT_649" PRIMARY KEY("id");
CREATE INDEX "idx_agent_candidate_project_created" ON "public"."project_agent_candidates"("project_id" NULLS FIRST, "created_at" NULLS FIRST);
CREATE INDEX "idx_agent_candidate_project_validation" ON "public"."project_agent_candidates"("project_id" NULLS FIRST, "validation_status" NULLS FIRST);
CREATE TABLE "public"."project_analysis_jobs"(
    "attempt_count" INTEGER,
    "completion_tokens" INTEGER,
    "failure_acknowledged" BOOLEAN,
    "max_attempts" INTEGER,
    "max_request_count" INTEGER,
    "max_total_tokens" INTEGER,
    "model_returned" BOOLEAN,
    "prompt_tokens" INTEGER,
    "queue_position" INTEGER,
    "request_count" INTEGER,
    "total_tokens" INTEGER,
    "cancellation_requested_at" TIMESTAMP(6) WITH TIME ZONE,
    "cancelled_at" TIMESTAMP(6) WITH TIME ZONE,
    "completed_at" TIMESTAMP(6) WITH TIME ZONE,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "current_step_started_at" TIMESTAMP(6) WITH TIME ZONE,
    "heartbeat_at" TIMESTAMP(6) WITH TIME ZONE,
    "max_duration_ms" BIGINT,
    "queued_at" TIMESTAMP(6) WITH TIME ZONE,
    "started_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "version" BIGINT DEFAULT 0,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "record_id" UUID,
    "retried_from_job_id" UUID,
    "user_id" UUID NOT NULL,
    "status" CHARACTER VARYING(30) NOT NULL,
    "failure_stage" CHARACTER VARYING(40),
    "job_type" CHARACTER VARYING(40) NOT NULL,
    "restart_recovery_state" CHARACTER VARYING(40),
    "stage" CHARACTER VARYING(40),
    "failure_code" CHARACTER VARYING(80),
    "retry_reason" CHARACTER VARYING(80),
    "idempotency_key" CHARACTER VARYING(128),
    "input_fingerprint" CHARACTER VARYING(128),
    "stage_message" CHARACTER VARYING(500),
    "file_path" CHARACTER VARYING(1000),
    "diagnostics_json" CHARACTER VARYING,
    "error_message" CHARACTER VARYING,
    "input_summary" CHARACTER VARYING,
    "result_json" CHARACTER VARYING,
    "warning_message" CHARACTER VARYING
);
ALTER TABLE "public"."project_analysis_jobs" ADD CONSTRAINT "CONSTRAINT_33A" PRIMARY KEY("id");
CREATE TABLE "public"."project_analysis_records"(
    "model_used" BOOLEAN NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "confidence" CHARACTER VARYING(40) NOT NULL,
    "record_type" CHARACTER VARYING(40) NOT NULL,
    "analysis_source" CHARACTER VARYING(60) NOT NULL,
    "provider_name" CHARACTER VARYING(160),
    "file_path" CHARACTER VARYING(1000),
    "details" CHARACTER VARYING NOT NULL,
    "summary" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."project_analysis_records" ADD CONSTRAINT "CONSTRAINT_2B" PRIMARY KEY("id");
CREATE TABLE "public"."project_capabilities"(
    "attention_fact_count" INTEGER NOT NULL,
    "current_version" INTEGER NOT NULL,
    "distinct_commit_count" INTEGER NOT NULL,
    "evidence_count" INTEGER NOT NULL,
    "evolution_count" INTEGER NOT NULL,
    "source_batch_count" INTEGER NOT NULL,
    "source_fact_count" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "first_formed_at" TIMESTAMP(6) WITH TIME ZONE,
    "last_enhanced_at" TIMESTAMP(6) WITH TIME ZONE,
    "row_version" BIGINT,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "latest_analysis_job_id" UUID,
    "legacy_card_id" UUID,
    "merged_into_capability_id" UUID,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(20) NOT NULL,
    "generation_mode" CHARACTER VARYING(40) NOT NULL,
    "maturity_level" CHARACTER VARYING(40) NOT NULL,
    "capability_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "stable_identity_key" CHARACTER VARYING(64) NOT NULL,
    "canonical_name" CHARACTER VARYING(200) NOT NULL,
    "model_name" CHARACTER VARYING(200),
    "model_provider" CHARACTER VARYING(200),
    "maturity_reason" CHARACTER VARYING(1000),
    "aliases" CHARACTER VARYING,
    "current_summary" CHARACTER VARYING,
    "interview_expression" CHARACTER VARYING,
    "long_term_value" CHARACTER VARYING,
    "problem_solved" CHARACTER VARYING,
    "product_areas" CHARACTER VARYING,
    "readme_expression" CHARACTER VARYING,
    "resume_expression" CHARACTER VARYING
);
ALTER TABLE "public"."project_capabilities" ADD CONSTRAINT "CONSTRAINT_CF1E" PRIMARY KEY("id");
CREATE INDEX "idx_capability_project_status" ON "public"."project_capabilities"("project_id" NULLS FIRST, "status" NULLS FIRST);
CREATE INDEX "idx_capability_project_maturity" ON "public"."project_capabilities"("project_id" NULLS FIRST, "maturity_level" NULLS FIRST);
CREATE INDEX "idx_capability_project_updated" ON "public"."project_capabilities"("project_id" NULLS FIRST, "updated_at" NULLS FIRST);
CREATE TABLE "public"."project_capability_attention"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "analysis_job_id" UUID,
    "fact_id" UUID,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "source_capability_id" UUID,
    "target_capability_id" UUID,
    "status" CHARACTER VARYING(20) NOT NULL,
    "attention_type" CHARACTER VARYING(60) NOT NULL,
    "attention_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "reason" CHARACTER VARYING(1000) NOT NULL
);
ALTER TABLE "public"."project_capability_attention" ADD CONSTRAINT "CONSTRAINT_FC" PRIMARY KEY("id");
CREATE INDEX "idx_capability_attention_project_status" ON "public"."project_capability_attention"("project_id" NULLS FIRST, "status" NULLS FIRST);
CREATE TABLE "public"."project_capability_cards"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "analysis_job_id" UUID,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(30) NOT NULL,
    "generation_mode" CHARACTER VARYING(40),
    "model_provider" CHARACTER VARYING(160),
    "name" CHARACTER VARYING(180) NOT NULL,
    "evidence_refs" CHARACTER VARYING,
    "fallback_reason" CHARACTER VARYING,
    "feature_entry" CHARACTER VARYING,
    "interview_expression" CHARACTER VARYING,
    "problem_solved" CHARACTER VARYING,
    "readme_expression" CHARACTER VARYING,
    "resume_expression" CHARACTER VARYING,
    "source_refs" CHARACTER VARYING,
    "summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_capability_cards" ADD CONSTRAINT "CONSTRAINT_D8" PRIMARY KEY("id");
CREATE TABLE "public"."project_capability_evolutions"(
    "generation_version" INTEGER NOT NULL,
    "source_batch_count" INTEGER NOT NULL,
    "source_fact_count" INTEGER NOT NULL,
    "version_after" INTEGER NOT NULL,
    "version_before" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "occurred_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "analysis_job_id" UUID,
    "capability_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "merged_from_capability_id" UUID,
    "project_id" UUID NOT NULL,
    "evolution_type" CHARACTER VARYING(40) NOT NULL,
    "operation_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "model_name" CHARACTER VARYING(200),
    "model_provider" CHARACTER VARYING(200),
    "title" CHARACTER VARYING(200) NOT NULL,
    "source_timeline_periods" CHARACTER VARYING,
    "summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_capability_evolutions" ADD CONSTRAINT "CONSTRAINT_F95" PRIMARY KEY("id");
CREATE INDEX "idx_capability_evolution_capability" ON "public"."project_capability_evolutions"("capability_id" NULLS FIRST, "occurred_at" NULLS FIRST);
CREATE INDEX "idx_capability_evolution_project" ON "public"."project_capability_evolutions"("project_id" NULLS FIRST, "occurred_at" NULLS FIRST);
CREATE TABLE "public"."project_capability_fact_coverage"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "source_fact_updated_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "capability_id" UUID,
    "fact_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "source_evolution_id" UUID,
    "classification" CHARACTER VARYING(40) NOT NULL,
    "source_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "reason" CHARACTER VARYING(1000)
);
ALTER TABLE "public"."project_capability_fact_coverage" ADD CONSTRAINT "CONSTRAINT_409" PRIMARY KEY("id");
CREATE INDEX "idx_capability_coverage_classification" ON "public"."project_capability_fact_coverage"("project_id" NULLS FIRST, "classification" NULLS FIRST);
CREATE TABLE "public"."project_capability_facts"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "linked_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "capability_id" UUID NOT NULL,
    "fact_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "source_evolution_id" UUID NOT NULL,
    "relation_role" CHARACTER VARYING(20) NOT NULL
);
ALTER TABLE "public"."project_capability_facts" ADD CONSTRAINT "CONSTRAINT_D8CE" PRIMARY KEY("id");
CREATE INDEX "idx_capability_fact_capability" ON "public"."project_capability_facts"("capability_id" NULLS FIRST);
CREATE INDEX "idx_capability_fact_fact" ON "public"."project_capability_facts"("fact_id" NULLS FIRST);
CREATE INDEX "idx_capability_fact_evolution" ON "public"."project_capability_facts"("source_evolution_id" NULLS FIRST);
CREATE INDEX "idx_capability_fact_project" ON "public"."project_capability_facts"("project_id" NULLS FIRST);
CREATE TABLE "public"."project_capability_map_states"(
    "assigned_fact_count" INTEGER NOT NULL,
    "attention_fact_count" INTEGER NOT NULL,
    "covered_fact_count" INTEGER NOT NULL,
    "generation_version" INTEGER NOT NULL,
    "no_change_fact_count" INTEGER NOT NULL,
    "source_fact_count" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "dirty_since" TIMESTAMP(6) WITH TIME ZONE,
    "last_processed_fact_at" TIMESTAMP(6) WITH TIME ZONE,
    "latest_attempt_at" TIMESTAMP(6) WITH TIME ZONE,
    "latest_successful_at" TIMESTAMP(6) WITH TIME ZONE,
    "row_version" BIGINT,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "latest_attempt_job_id" UUID,
    "latest_successful_job_id" UUID,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(30) NOT NULL,
    "source_fact_fingerprint" CHARACTER VARYING(64),
    "error_code" CHARACTER VARYING(80),
    "error_summary" CHARACTER VARYING(1000)
);
ALTER TABLE "public"."project_capability_map_states" ADD CONSTRAINT "CONSTRAINT_26C" PRIMARY KEY("id");
CREATE TABLE "public"."project_changes"(
    "needs_user_review" BOOLEAN,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "reviewed_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "development_segment_id" UUID,
    "id" UUID NOT NULL,
    "linked_suggestion_id" UUID,
    "material_id" UUID,
    "project_id" UUID NOT NULL,
    "source_batch_id" UUID,
    "target_sediment_id" UUID,
    "evidence_confidence" CHARACTER VARYING(20),
    "recommendation_strength" CHARACTER VARYING(30),
    "change_kind" CHARACTER VARYING(40) NOT NULL,
    "content_source" CHARACTER VARYING(40),
    "impact_level" CHARACTER VARYING(40) NOT NULL,
    "quality_status" CHARACTER VARYING(40),
    "source_type" CHARACTER VARYING(40) NOT NULL,
    "status" CHARACTER VARYING(40) NOT NULL,
    "suggested_action" CHARACTER VARYING(40),
    "title" CHARACTER VARYING(180) NOT NULL,
    "affected_files" CHARACTER VARYING,
    "asset_candidates" CHARACTER VARYING,
    "build_evidence" CHARACTER VARYING,
    "decision_notes" CHARACTER VARYING,
    "details" CHARACTER VARYING,
    "evidence_refs" CHARACTER VARYING,
    "learning_notes" CHARACTER VARYING,
    "problem_solved" CHARACTER VARYING,
    "related_tasks" CHARACTER VARYING,
    "risk_notes" CHARACTER VARYING,
    "source_ref" CHARACTER VARYING,
    "suggestion_reason" CHARACTER VARYING,
    "summary" CHARACTER VARYING NOT NULL,
    "test_evidence" CHARACTER VARYING
);
ALTER TABLE "public"."project_changes" ADD CONSTRAINT "CONSTRAINT_9D5F2B9" PRIMARY KEY("id");
CREATE TABLE "public"."project_evolution_bridges"(
    "generation_version" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "occurred_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "confidence" CHARACTER VARYING(20) NOT NULL,
    "epistemic_status" CHARACTER VARYING(20) NOT NULL,
    "after_structure_version" CHARACTER VARYING(40) NOT NULL,
    "before_structure_version" CHARACTER VARYING(40) NOT NULL,
    "after_revision" CHARACTER VARYING(64) NOT NULL,
    "before_revision" CHARACTER VARYING(64) NOT NULL,
    "bridge_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "affected_area_id" CHARACTER VARYING(80) NOT NULL,
    "affected_area_label" CHARACTER VARYING(200) NOT NULL,
    "after_state" CHARACTER VARYING NOT NULL,
    "before_state" CHARACTER VARYING NOT NULL,
    "changed_paths" CHARACTER VARYING NOT NULL,
    "evidence_refs" CHARACTER VARYING NOT NULL,
    "meaningful_change" CHARACTER VARYING NOT NULL,
    "source_commit_refs" CHARACTER VARYING NOT NULL,
    "source_fact_ids" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."project_evolution_bridges" ADD CONSTRAINT "CONSTRAINT_6E" PRIMARY KEY("id");
CREATE INDEX "idx_evolution_bridge_project_time" ON "public"."project_evolution_bridges"("project_id" NULLS FIRST, "occurred_at" NULLS FIRST);
CREATE TABLE "public"."project_evolution_records"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "material_id" UUID,
    "project_id" UUID NOT NULL,
    "detected_changes" CHARACTER VARYING,
    "developer_learnings" CHARACTER VARYING,
    "key_achievements" CHARACTER VARYING,
    "key_issues" CHARACTER VARYING,
    "next_steps" CHARACTER VARYING,
    "summary" CHARACTER VARYING NOT NULL,
    "technical_decisions" CHARACTER VARYING
);
ALTER TABLE "public"."project_evolution_records" ADD CONSTRAINT "CONSTRAINT_A6" PRIMARY KEY("id");
CREATE TABLE "public"."project_fact_agent_result_refs"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "fact_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "agent_result_ref" CHARACTER VARYING(1200) NOT NULL
);
ALTER TABLE "public"."project_fact_agent_result_refs" ADD CONSTRAINT "CONSTRAINT_21" PRIMARY KEY("id");
CREATE INDEX "idx_project_fact_agent_project_ref" ON "public"."project_fact_agent_result_refs"("project_id" NULLS FIRST, "agent_result_ref" NULLS FIRST);
CREATE TABLE "public"."project_fact_commit_refs"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "fact_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "commit_sha" CHARACTER VARYING(64) NOT NULL
);
ALTER TABLE "public"."project_fact_commit_refs" ADD CONSTRAINT "CONSTRAINT_5" PRIMARY KEY("id");
CREATE INDEX "idx_project_fact_commit_project_sha" ON "public"."project_fact_commit_refs"("project_id" NULLS FIRST, "commit_sha" NULLS FIRST);
CREATE TABLE "public"."project_fact_cursors"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "last_recorded_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "last_batch_id" UUID,
    "project_id" UUID NOT NULL,
    "last_recorded_commit_sha" CHARACTER VARYING(64),
    "branch_name" CHARACTER VARYING(255)
);
ALTER TABLE "public"."project_fact_cursors" ADD CONSTRAINT "CONSTRAINT_1" PRIMARY KEY("id");
CREATE TABLE "public"."project_fact_file_refs"(
    "fact_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "file_path" CHARACTER VARYING(1000) NOT NULL
);
ALTER TABLE "public"."project_fact_file_refs" ADD CONSTRAINT "CONSTRAINT_84" PRIMARY KEY("id");
CREATE INDEX "idx_project_fact_file_project" ON "public"."project_fact_file_refs"("project_id" NULLS FIRST);
CREATE INDEX "idx_project_fact_file_fact" ON "public"."project_fact_file_refs"("fact_id" NULLS FIRST);
CREATE TABLE "public"."project_fact_history_states"(
    "completed_chunk_count" INTEGER,
    "covered_commit_count" INTEGER,
    "current_chunk" INTEGER,
    "remaining_commit_count" INTEGER,
    "total_commit_count" INTEGER,
    "completed_at" TIMESTAMP(6) WITH TIME ZONE,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "started_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "last_batch_id" UUID,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(40) NOT NULL,
    "head_snapshot_sha" CHARACTER VARYING(64),
    "last_processed_commit_sha" CHARACTER VARYING(64),
    "error_code" CHARACTER VARYING(80),
    "error_summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_fact_history_states" ADD CONSTRAINT "CONSTRAINT_AD1" PRIMARY KEY("id");
CREATE TABLE "public"."project_fact_sources"(
    "confirmed_by_user" BOOLEAN NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "source_id" UUID,
    "confidence" CHARACTER VARYING(40) NOT NULL,
    "source_type" CHARACTER VARYING(40) NOT NULL,
    "field_key" CHARACTER VARYING(80) NOT NULL,
    "fact_value" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."project_fact_sources" ADD CONSTRAINT "CONSTRAINT_5EA" PRIMARY KEY("id");
CREATE TABLE "public"."project_facts"(
    "affected_file_count" INTEGER,
    "agent_result_count" INTEGER,
    "commit_count" INTEGER,
    "evidence_count" INTEGER,
    "timeline_month_key" CHARACTER VARYING(7),
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "effective_at" TIMESTAMP(6) WITH TIME ZONE,
    "observed_at" TIMESTAMP(6) WITH TIME ZONE,
    "occurred_from" TIMESTAMP(6) WITH TIME ZONE,
    "occurred_to" TIMESTAMP(6) WITH TIME ZONE,
    "timeline_event_at" TIMESTAMP(6) WITH TIME ZONE,
    "timeline_week_key" CHARACTER VARYING(8),
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "timeline_day_key" CHARACTER VARYING(10),
    "batch_id" UUID,
    "id" UUID NOT NULL,
    "legacy_sediment_id" UUID,
    "project_id" UUID NOT NULL,
    "source_segment_id" UUID,
    "superseded_by" UUID,
    "confidence" CHARACTER VARYING(20) NOT NULL,
    "currentness" CHARACTER VARYING(30),
    "epistemic_status" CHARACTER VARYING(30),
    "origin" CHARACTER VARYING(40) NOT NULL,
    "quality_status" CHARACTER VARYING(40),
    "record_status" CHARACTER VARYING(40) NOT NULL,
    "source_mode" CHARACTER VARYING(40),
    "validation_status" CHARACTER VARYING(40),
    "fact_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "created_by" CHARACTER VARYING(80),
    "source_agent_id" CHARACTER VARYING(160),
    "source_model_provider" CHARACTER VARYING(160),
    "revision" CHARACTER VARYING(180),
    "title" CHARACTER VARYING(200) NOT NULL,
    "affected_files" CHARACTER VARYING,
    "agent_result_refs" CHARACTER VARYING,
    "attention_reason" CHARACTER VARYING,
    "commit_refs" CHARACTER VARYING,
    "commit_urls" CHARACTER VARYING,
    "conflict_refs" CHARACTER VARYING,
    "evidence_refs" CHARACTER VARYING,
    "fact_statement" CHARACTER VARYING,
    "limitations" CHARACTER VARYING,
    "main_changes" CHARACTER VARYING,
    "source_types" CHARACTER VARYING,
    "summary" CHARACTER VARYING,
    "user_visible_value" CHARACTER VARYING
);
ALTER TABLE "public"."project_facts" ADD CONSTRAINT "CONSTRAINT_C74F2E" PRIMARY KEY("id");
CREATE INDEX "idx_project_fact_project_time" ON "public"."project_facts"("project_id" NULLS FIRST, "occurred_to" NULLS FIRST);
CREATE INDEX "idx_project_fact_batch" ON "public"."project_facts"("batch_id" NULLS FIRST);
CREATE INDEX "idx_project_fact_segment" ON "public"."project_facts"("source_segment_id" NULLS FIRST);
CREATE INDEX "idx_project_fact_status" ON "public"."project_facts"("project_id" NULLS FIRST, "record_status" NULLS FIRST);
CREATE INDEX "idx_project_fact_timeline_day" ON "public"."project_facts"("project_id" NULLS FIRST, "timeline_day_key" NULLS FIRST);
CREATE INDEX "idx_project_fact_timeline_week" ON "public"."project_facts"("project_id" NULLS FIRST, "timeline_week_key" NULLS FIRST);
CREATE INDEX "idx_project_fact_timeline_month" ON "public"."project_facts"("project_id" NULLS FIRST, "timeline_month_key" NULLS FIRST);
CREATE TABLE "public"."project_history_corrections"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "version" BIGINT,
    "actor_user_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "replaced_by_id" UUID,
    "status" CHARACTER VARYING(20) NOT NULL,
    "declared_role" CHARACTER VARYING(30),
    "target_type" CHARACTER VARYING(30) NOT NULL,
    "correction_type" CHARACTER VARYING(40) NOT NULL,
    "automatic_presentation_fingerprint" CHARACTER VARYING(64),
    "source_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "target_membership_fingerprint" CHARACTER VARYING(64),
    "before_presentation_revision" CHARACTER VARYING(180) NOT NULL,
    "declared_chapter_id" CHARACTER VARYING(180),
    "target_id" CHARACTER VARYING(180) NOT NULL,
    "conflict_reason" CHARACTER VARYING,
    "declared_summary" CHARACTER VARYING,
    "declared_title" CHARACTER VARYING,
    "secondary_declared_summary" CHARACTER VARYING,
    "secondary_declared_title" CHARACTER VARYING,
    "target_ids_json" CHARACTER VARYING NOT NULL,
    "target_membership_refs_json" CHARACTER VARYING
);
ALTER TABLE "public"."project_history_corrections" ADD CONSTRAINT "CONSTRAINT_9E1" PRIMARY KEY("id");
CREATE INDEX "idx_history_correction_project_updated" ON "public"."project_history_corrections"("project_id" NULLS FIRST, "updated_at" NULLS FIRST);
CREATE INDEX "idx_history_correction_project_target" ON "public"."project_history_corrections"("project_id" NULLS FIRST, "target_type" NULLS FIRST, "target_id" NULLS FIRST);
CREATE INDEX "idx_history_correction_project_status" ON "public"."project_history_corrections"("project_id" NULLS FIRST, "status" NULLS FIRST);
CREATE TABLE "public"."project_history_events"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "effective_at" TIMESTAMP(6) WITH TIME ZONE,
    "occurred_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "history_scope" CHARACTER VARYING(20) NOT NULL,
    "rewrite_state" CHARACTER VARYING(20) NOT NULL,
    "authority" CHARACTER VARYING(30) NOT NULL,
    "epistemic_status" CHARACTER VARYING(30) NOT NULL,
    "event_category" CHARACTER VARYING(30) NOT NULL,
    "source_type" CHARACTER VARYING(30) NOT NULL,
    "transition_type" CHARACTER VARYING(30) NOT NULL,
    "payload_hash" CHARACTER VARYING(64) NOT NULL,
    "stable_event_key" CHARACTER VARYING(64) NOT NULL,
    "project_revision" CHARACTER VARYING(180) NOT NULL,
    "source_revision" CHARACTER VARYING(180) NOT NULL,
    "actor_label" CHARACTER VARYING(200),
    "source_identity" CHARACTER VARYING(500) NOT NULL,
    "raw_source_deep_link" CHARACTER VARYING(1000),
    "affected_paths_json" CHARACTER VARYING NOT NULL,
    "coverage_json" CHARACTER VARYING NOT NULL,
    "evidence_refs_json" CHARACTER VARYING NOT NULL,
    "limitations_json" CHARACTER VARYING NOT NULL,
    "relation_refs_json" CHARACTER VARYING NOT NULL,
    "safe_source_label" CHARACTER VARYING NOT NULL,
    "subject_keys_json" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E98D90A_0" PRIMARY KEY("id");
CREATE INDEX "idx_history_event_project_time" ON "public"."project_history_events"("project_id" NULLS FIRST, "occurred_at" NULLS FIRST);
CREATE INDEX "idx_history_event_project_source" ON "public"."project_history_events"("project_id" NULLS FIRST, "source_type" NULLS FIRST);
CREATE INDEX "idx_history_event_project_category" ON "public"."project_history_events"("project_id" NULLS FIRST, "event_category" NULLS FIRST);
CREATE INDEX "idx_history_event_project_rewrite" ON "public"."project_history_events"("project_id" NULLS FIRST, "rewrite_state" NULLS FIRST);
CREATE TABLE "public"."project_history_snapshots"(
    "source_event_count" INTEGER NOT NULL,
    "continuity_dirty_at" TIMESTAMP(6) WITH TIME ZONE,
    "continuity_dirty_generation" BIGINT,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "earliest_event_at" TIMESTAMP(6) WITH TIME ZONE,
    "generated_at" TIMESTAMP(6) WITH TIME ZONE,
    "latest_event_at" TIMESTAMP(6) WITH TIME ZONE,
    "latest_successful_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "version" BIGINT,
    "analysis_job_id" UUID,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(30) NOT NULL,
    "prompt_version" CHARACTER VARYING(60) NOT NULL,
    "strategy_version" CHARACTER VARYING(60) NOT NULL,
    "source_event_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "continuity_dirty_reason" CHARACTER VARYING(80),
    "error_code" CHARACTER VARYING(80),
    "continuity_dirty_revision" CHARACTER VARYING(96),
    "project_revision" CHARACTER VARYING(180) NOT NULL,
    "chapters_json" CHARACTER VARYING NOT NULL,
    "coverage_json" CHARACTER VARYING NOT NULL,
    "diagnostics_json" CHARACTER VARYING NOT NULL,
    "error_summary" CHARACTER VARYING,
    "overview_json" CHARACTER VARYING NOT NULL,
    "stories_json" CHARACTER VARYING NOT NULL,
    "threads_json" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."project_history_snapshots" ADD CONSTRAINT "CONSTRAINT_8985" PRIMARY KEY("id");
CREATE TABLE "public"."project_history_window_checkpoints"(
    "event_count" INTEGER NOT NULL,
    "request_count" INTEGER NOT NULL,
    "story_count" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "version" BIGINT,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "status" CHARACTER VARYING(24) NOT NULL,
    "cache_key" CHARACTER VARYING(64) NOT NULL,
    "source_fingerprint" CHARACTER VARYING(64) NOT NULL,
    "window_identity" CHARACTER VARYING(120) NOT NULL,
    "last_error" CHARACTER VARYING(500) NOT NULL,
    "diagnostics_json" CHARACTER VARYING NOT NULL,
    "validated_result_json" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."project_history_window_checkpoints" ADD CONSTRAINT "CONSTRAINT_2C" PRIMARY KEY("id");
CREATE TABLE "public"."project_materials"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "source_type" CHARACTER VARYING(60) NOT NULL,
    "file_name" CHARACTER VARYING(260),
    "content" CHARACTER VARYING NOT NULL,
    "normalized_summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_materials" ADD CONSTRAINT "CONSTRAINT_941" PRIMARY KEY("id");
CREATE TABLE "public"."project_memories"(
    "version" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "completed_capabilities" CHARACTER VARYING,
    "current_risks" CHARACTER VARYING,
    "current_stage" CHARACTER VARYING NOT NULL,
    "developer_learnings" CHARACTER VARYING,
    "in_progress_capabilities" CHARACTER VARYING,
    "local_project_path" CHARACTER VARYING,
    "next_step_suggestions" CHARACTER VARYING,
    "positioning" CHARACTER VARYING NOT NULL,
    "showcase_assets" CHARACTER VARYING,
    "technical_decisions" CHARACTER VARYING
);
ALTER TABLE "public"."project_memories" ADD CONSTRAINT "CONSTRAINT_932" PRIMARY KEY("id");
CREATE TABLE "public"."project_memory_read_audits"(
    "query_length" INTEGER NOT NULL,
    "result_count" INTEGER NOT NULL,
    "latency_ms" BIGINT NOT NULL,
    "occurred_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID,
    "user_id" UUID NOT NULL,
    "status" CHARACTER VARYING(30) NOT NULL,
    "caller_hash" CHARACTER VARYING(64),
    "query_hash" CHARACTER VARYING(64),
    "operation_name" CHARACTER VARYING(80) NOT NULL,
    "entity_types" CHARACTER VARYING(200),
    "filter_summary" CHARACTER VARYING(500)
);
ALTER TABLE "public"."project_memory_read_audits" ADD CONSTRAINT "CONSTRAINT_53" PRIMARY KEY("id");
CREATE INDEX "idx_memory_audit_project_time" ON "public"."project_memory_read_audits"("project_id" NULLS FIRST, "occurred_at" NULLS FIRST);
CREATE INDEX "idx_memory_audit_user_time" ON "public"."project_memory_read_audits"("user_id" NULLS FIRST, "occurred_at" NULLS FIRST);
CREATE TABLE "public"."project_review_cursors"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "last_reviewed_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "last_snapshot_id" UUID,
    "project_id" UUID NOT NULL,
    "last_reviewed_commit_sha" CHARACTER VARYING(64),
    "last_reviewed_remote_sha" CHARACTER VARYING(64),
    "last_reviewed_branch" CHARACTER VARYING(255)
);
ALTER TABLE "public"."project_review_cursors" ADD CONSTRAINT "CONSTRAINT_C4" PRIMARY KEY("id");
CREATE TABLE "public"."project_sediments"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "last_capability_analyzed_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "last_capability_analysis_job_id" UUID,
    "project_id" UUID NOT NULL,
    "capability_status" CHARACTER VARYING(40),
    "content_source" CHARACTER VARYING(40),
    "quality_status" CHARACTER VARYING(40),
    "status" CHARACTER VARYING(40) NOT NULL,
    "sediment_type" CHARACTER VARYING(80) NOT NULL,
    "title" CHARACTER VARYING(200) NOT NULL,
    "affected_files" CHARACTER VARYING,
    "developer_notes" CHARACTER VARYING,
    "evidence_refs" CHARACTER VARYING,
    "problem_solved" CHARACTER VARYING,
    "source_batch_ids" CHARACTER VARYING,
    "source_segment_ids" CHARACTER VARYING,
    "summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_sediments" ADD CONSTRAINT "CONSTRAINT_65" PRIMARY KEY("id");
CREATE TABLE "public"."project_snapshots"(
    "memory_version" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "current_stage" CHARACTER VARYING NOT NULL,
    "module_completion" CHARACTER VARYING,
    "next_step_suggestions" CHARACTER VARYING,
    "recent_achievements" CHARACTER VARYING,
    "risk_summary" CHARACTER VARYING,
    "task_status_summary" CHARACTER VARYING,
    "tech_stack_summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_snapshots" ADD CONSTRAINT "CONSTRAINT_79" PRIMARY KEY("id");
CREATE TABLE "public"."project_structure_indexes"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "index_version" CHARACTER VARYING(40) NOT NULL,
    "content_hash" CHARACTER VARYING(64) NOT NULL,
    "indexer_source" CHARACTER VARYING(80) NOT NULL,
    "source_revision" CHARACTER VARYING(180) NOT NULL,
    "index_json" CHARACTER VARYING NOT NULL,
    "intake_json" CHARACTER VARYING NOT NULL,
    "inventory_json" CHARACTER VARYING
);
ALTER TABLE "public"."project_structure_indexes" ADD CONSTRAINT "CONSTRAINT_5A3" PRIMARY KEY("id");
CREATE TABLE "public"."project_timeline_summaries"(
    "covered_fact_count" INTEGER NOT NULL,
    "generation_version" INTEGER NOT NULL,
    "source_fact_count" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "generated_at" TIMESTAMP(6) WITH TIME ZONE,
    "period_end" TIMESTAMP(6) WITH TIME ZONE,
    "period_start" TIMESTAMP(6) WITH TIME ZONE,
    "source_fact_max_updated_at" TIMESTAMP(6) WITH TIME ZONE,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "version" BIGINT,
    "analysis_job_id" UUID,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "granularity" CHARACTER VARYING(20) NOT NULL,
    "period_key" CHARACTER VARYING(20) NOT NULL,
    "status" CHARACTER VARYING(30) NOT NULL,
    "source_fact_fingerprint" CHARACTER VARYING(64),
    "error_code" CHARACTER VARYING(80),
    "timeline_zone" CHARACTER VARYING(80) NOT NULL,
    "model_name" CHARACTER VARYING(200),
    "model_provider" CHARACTER VARYING(200),
    "error_summary" CHARACTER VARYING(1000),
    "summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_timeline_summaries" ADD CONSTRAINT "CONSTRAINT_B513" PRIMARY KEY("id");
CREATE INDEX "idx_timeline_summary_project_status" ON "public"."project_timeline_summaries"("project_id" NULLS FIRST, "status" NULLS FIRST);
CREATE INDEX "idx_timeline_summary_project_period" ON "public"."project_timeline_summaries"("project_id" NULLS FIRST, "granularity" NULLS FIRST, "period_key" NULLS FIRST);
CREATE TABLE "public"."project_timeline_theme_facts"(
    "fact_id" UUID NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "theme_id" UUID NOT NULL
);
ALTER TABLE "public"."project_timeline_theme_facts" ADD CONSTRAINT "CONSTRAINT_FB" PRIMARY KEY("id");
CREATE INDEX "idx_timeline_theme_fact_theme" ON "public"."project_timeline_theme_facts"("theme_id" NULLS FIRST);
CREATE INDEX "idx_timeline_theme_fact_fact" ON "public"."project_timeline_theme_facts"("fact_id" NULLS FIRST);
CREATE INDEX "idx_timeline_theme_fact_project" ON "public"."project_timeline_theme_facts"("project_id" NULLS FIRST);
CREATE TABLE "public"."project_timeline_themes"(
    "sort_order" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "summary_id" UUID NOT NULL,
    "title" CHARACTER VARYING(200) NOT NULL,
    "summary" CHARACTER VARYING
);
ALTER TABLE "public"."project_timeline_themes" ADD CONSTRAINT "CONSTRAINT_3A" PRIMARY KEY("id");
CREATE INDEX "idx_timeline_theme_summary" ON "public"."project_timeline_themes"("summary_id" NULLS FIRST, "sort_order" NULLS FIRST);
CREATE INDEX "idx_timeline_theme_project" ON "public"."project_timeline_themes"("project_id" NULLS FIRST);
CREATE TABLE "public"."project_understanding_snapshots"(
    "analyzed_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "current_status" CHARACTER VARYING(20) NOT NULL,
    "semantic_status" CHARACTER VARYING(30) NOT NULL,
    "structure_index_version" CHARACTER VARYING(40) NOT NULL,
    "model_analysis_version" CHARACTER VARYING(60) NOT NULL,
    "structure_hash" CHARACTER VARYING(64) NOT NULL,
    "source_revision" CHARACTER VARYING(180) NOT NULL,
    "snapshot_json" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."project_understanding_snapshots" ADD CONSTRAINT "CONSTRAINT_556" PRIMARY KEY("id");
CREATE TABLE "public"."projects"(
    "end_date" DATE,
    "start_date" DATE,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "status" CHARACTER VARYING(40) NOT NULL,
    "name" CHARACTER VARYING(160) NOT NULL,
    "repo_url" CHARACTER VARYING(500),
    "description" CHARACTER VARYING,
    "tech_stack" CHARACTER VARYING
);
ALTER TABLE "public"."projects" ADD CONSTRAINT "CONSTRAINT_C479" PRIMARY KEY("id");
CREATE TABLE "public"."tasks"(
    "due_date" DATE,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "priority" CHARACTER VARYING(40) NOT NULL,
    "status" CHARACTER VARYING(40) NOT NULL,
    "title" CHARACTER VARYING(180) NOT NULL,
    "description" CHARACTER VARYING,
    "tags" CHARACTER VARYING
);
ALTER TABLE "public"."tasks" ADD CONSTRAINT "CONSTRAINT_6907" PRIMARY KEY("id");
CREATE TABLE "public"."users"(
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "username" CHARACTER VARYING(80) NOT NULL,
    "email" CHARACTER VARYING(255) NOT NULL,
    "password_hash" CHARACTER VARYING(255) NOT NULL
);
ALTER TABLE "public"."users" ADD CONSTRAINT "CONSTRAINT_6A6" PRIMARY KEY("id");
CREATE TABLE "public"."work_sessions"(
    "added_lines" INTEGER NOT NULL,
    "changed_files" INTEGER NOT NULL,
    "deleted_lines" INTEGER NOT NULL,
    "created_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "end_time" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "start_time" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "updated_at" TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    "id" UUID NOT NULL,
    "project_id" UUID NOT NULL,
    "agent_type" CHARACTER VARYING(40) NOT NULL,
    "attribution_confidence" CHARACTER VARYING(40) NOT NULL,
    "detection_method" CHARACTER VARYING(60) NOT NULL,
    "base_commit" CHARACTER VARYING(80),
    "agent_name" CHARACTER VARYING(180) NOT NULL,
    "branch_name" CHARACTER VARYING(180),
    "affected_modules" CHARACTER VARYING,
    "evidence" CHARACTER VARYING,
    "file_paths" CHARACTER VARYING,
    "task_intent" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."work_sessions" ADD CONSTRAINT "CONSTRAINT_F6" PRIMARY KEY("id");
ALTER TABLE "public"."ai_outputs" ADD CONSTRAINT "CONSTRAINT_A" CHECK("type" IN('WEEKLY_REPORT', 'PROJECT_SUMMARY', 'RESUME_BULLET', 'README_SECTION'));
ALTER TABLE "public"."project_analysis_records" ADD CONSTRAINT "CONSTRAINT_2" CHECK("record_type" IN('PROJECT', 'FILE'));
ALTER TABLE "public"."project_analysis_jobs" ADD CONSTRAINT "CONSTRAINT_3" CHECK("status" IN('QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'CANCELLED', 'SUCCEEDED', 'SUCCEEDED_WITH_WARNINGS', 'FAILED', 'INTERRUPTED', 'RETRYABLE', 'EXPIRED', 'REJECTED'));
ALTER TABLE "public"."dev_logs" ADD CONSTRAINT "CONSTRAINT_4" CHECK("category" IN('FEATURE', 'BUGFIX', 'REFACTOR', 'RESEARCH', 'REVIEW', 'DEPLOYMENT'));
ALTER TABLE "public"."change_batches" ADD CONSTRAINT "CONSTRAINT_6" CHECK("status" IN('PENDING', 'PARTIAL', 'REVIEWED', 'FACTS_RECORDED', 'FACTS_RECORDED_WITH_ATTENTION', 'FAILED'));
ALTER TABLE "public"."development_segments" ADD CONSTRAINT "CONSTRAINT_852" CHECK("status" IN('PENDING', 'CONFIRMED', 'IGNORED', 'NEEDS_REVIEW'));
ALTER TABLE "public"."ai_providers" ADD CONSTRAINT "CONSTRAINT_8" CHECK("auth_mode" IN('PROTOCOL_DEFAULT', 'BEARER', 'API_KEY_HEADER', 'ANTHROPIC_STANDARD', 'NONE', 'QUERY_API_KEY'));
ALTER TABLE "public"."project_changes" ADD CONSTRAINT "CONSTRAINT_9" CHECK("evidence_confidence" IN('HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE "public"."project_materials" ADD CONSTRAINT "CONSTRAINT_94" CHECK("source_type" IN('NATURAL_NOTE', 'AGENT_SUMMARY', 'AGENT_CONVERSATION', 'CODEX_OUTPUT', 'CLAUDE_CODE_OUTPUT', 'CURSOR_OUTPUT', 'COMMIT_LOG', 'README_MARKDOWN', 'TEXT_FILE', 'DOCX_FILE', 'JSON_LOG', 'PROJECT_ZIP', 'OTHER'));
ALTER TABLE "public"."tasks" ADD CONSTRAINT "CONSTRAINT_690" CHECK("status" IN('BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'));
ALTER TABLE "public"."ai_providers" ADD CONSTRAINT "CONSTRAINT_82" CHECK("protocol" IN('OPENAI_RESPONSES', 'OPENAI_CHAT_COMPLETIONS', 'ANTHROPIC_MESSAGES'));
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E98D90" CHECK("source_type" IN('GIT', 'GITHUB', 'FILESYSTEM', 'PROJECT_FACT', 'AGENT_RESULT', 'DOCUMENT', 'USER', 'EXTERNAL'));
ALTER TABLE "public"."development_segments" ADD CONSTRAINT "CONSTRAINT_85" CHECK("confidence" IN('HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE "public"."project_capability_cards" ADD CONSTRAINT "CONSTRAINT_D" CHECK("status" IN('CANDIDATE', 'CONFIRMED', 'NEEDS_EVIDENCE', 'IGNORED'));
ALTER TABLE "public"."ai_suggestions" ADD CONSTRAINT "CONSTRAINT_E" CHECK("status" IN('PENDING', 'APPLIED', 'IGNORED'));
ALTER TABLE "public"."project_capability_attention" ADD CONSTRAINT "CONSTRAINT_F" CHECK("status" IN('OPEN', 'RESOLVED'));
ALTER TABLE "public"."project_capability_map_states" ADD CONSTRAINT "CONSTRAINT_26" CHECK("status" IN('NOT_INITIALIZED', 'DIRTY', 'QUEUED', 'GENERATING', 'READY', 'READY_STALE', 'WAITING_FOR_MODEL', 'FAILED'));
ALTER TABLE "public"."ai_providers" ADD CONSTRAINT "CONSTRAINT_82B" CHECK("type" IN('MOCK', 'DEEPSEEK', 'OPENAI', 'ANTHROPIC', 'OPENAI_COMPATIBLE', 'CUSTOM'));
ALTER TABLE "public"."project_facts" ADD CONSTRAINT "CONSTRAINT_C7" CHECK("confidence" IN('HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE "public"."project_changes" ADD CONSTRAINT "CONSTRAINT_9D5F" CHECK("source_type" IN('AGENT_RESULT', 'EVIDENCE_BUNDLE', 'PROJECT_ZIP', 'MATERIAL_UPDATE', 'USER_MANUAL', 'MODEL_SUMMARY', 'DEVELOPMENT_SEGMENT'));
ALTER TABLE "public"."project_changes" ADD CONSTRAINT "CONSTRAINT_9D" CHECK("change_kind" IN('CAPABILITY', 'BUGFIX', 'REFACTOR', 'CONFIG', 'DOCS', 'TEST', 'RISK', 'DECISION', 'LEARNING', 'ASSET', 'UNKNOWN'));
ALTER TABLE "public"."project_changes" ADD CONSTRAINT "CONSTRAINT_9D5F2B" CHECK("suggested_action" IN('NEW_SEDIMENT', 'MERGE_EXISTING', 'EVIDENCE_ONLY', 'IGNORE'));
ALTER TABLE "public"."project_history_corrections" ADD CONSTRAINT "CONSTRAINT_9E" CHECK("status" IN('ACTIVE', 'REVERTED', 'CONFLICT'));
ALTER TABLE "public"."project_facts" ADD CONSTRAINT "CONSTRAINT_C74F" CHECK("origin" IN('INCREMENTAL_SCAN', 'HISTORY_BACKFILL', 'LEGACY_SEGMENT_MIGRATION', 'LEGACY_SEDIMENT_MIGRATION'));
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E98" CHECK("authority" IN('SOURCE_BACKED', 'FACTUAL_SOURCE', 'DECLARED', 'PROCESS_EVIDENCE', 'INFERRED_NON_AUTHORITATIVE', 'UNKNOWN'));
ALTER TABLE "public"."project_fact_history_states" ADD CONSTRAINT "CONSTRAINT_AD" CHECK("status" IN('NOT_STARTED', 'WAITING_FOR_MODEL', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED'));
ALTER TABLE "public"."project_changes" ADD CONSTRAINT "CONSTRAINT_9D5" CHECK("impact_level" IN('MAJOR', 'MINOR', 'MAINTENANCE', 'UNCERTAIN'));
ALTER TABLE "public"."project_timeline_summaries" ADD CONSTRAINT "CONSTRAINT_B5" CHECK("granularity" IN('DAY', 'WEEK', 'MONTH', 'LIFECYCLE'));
ALTER TABLE "public"."project_facts" ADD CONSTRAINT "CONSTRAINT_C74" CHECK("epistemic_status" IN('OBSERVED', 'VERIFIED', 'DECLARED', 'INFERRED', 'CONFLICTED', 'UNKNOWN', 'PROCESS_EVIDENCE'));
ALTER TABLE "public"."project_timeline_summaries" ADD CONSTRAINT "CONSTRAINT_B51" CHECK("status" IN('DIRTY', 'QUEUED', 'GENERATING', 'READY', 'FAILED', 'WAITING_FOR_MODEL'));
ALTER TABLE "public"."project_history_snapshots" ADD CONSTRAINT "CONSTRAINT_898" CHECK("status" IN('NOT_INITIALIZED', 'RUNNING', 'READY', 'DEGRADED', 'STALE', 'FAILED'));
ALTER TABLE "public"."project_capability_facts" ADD CONSTRAINT "CONSTRAINT_D8C" CHECK("relation_role" IN('FORMATION', 'ENHANCEMENT', 'EVIDENCE'));
ALTER TABLE "public"."project_changes" ADD CONSTRAINT "CONSTRAINT_9D5F2" CHECK("status" IN('PENDING', 'EDITED', 'ACCEPTED', 'IGNORED', 'MERGED'));
ALTER TABLE "public"."project_facts" ADD CONSTRAINT "CONSTRAINT_C74F2" CHECK("record_status" IN('RECORDED', 'NEEDS_ATTENTION'));
ALTER TABLE "public"."ai_suggestions" ADD CONSTRAINT "CONSTRAINT_E8" CHECK("type" IN('UPDATE_PROJECT_MEMORY', 'CREATE_TASK', 'CREATE_DEV_LOG', 'RECORD_TECHNICAL_DECISION', 'RECORD_RISK', 'RECORD_DEVELOPER_LEARNING', 'UPDATE_CURRENT_STAGE', 'GENERATE_ASSET_SUMMARY'));
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E98D" CHECK("epistemic_status" IN('OBSERVED', 'VERIFIED', 'DECLARED', 'INFERRED', 'CONFLICTED', 'UNKNOWN', 'PROCESS_EVIDENCE'));
ALTER TABLE "public"."project_analysis_jobs" ADD CONSTRAINT "CONSTRAINT_33" CHECK("job_type" IN('PROJECT', 'FILE', 'CAPABILITY_INTERPRET', 'WORK_SESSION_SCAN', 'PROJECT_FACT_HISTORY_REBUILD', 'PROJECT_TIMELINE_REFRESH', 'PROJECT_HISTORY_REFRESH', 'PROJECT_CAPABILITY_MAP_REFRESH', 'PROJECT_UNDERSTANDING_REFRESH', 'CAPABILITY_CARD_ANALYSIS'));
ALTER TABLE "public"."project_capabilities" ADD CONSTRAINT "CONSTRAINT_CF" CHECK("status" IN('ACTIVE', 'MERGED', 'ARCHIVED'));
ALTER TABLE "public"."project_capabilities" ADD CONSTRAINT "CONSTRAINT_CF1" CHECK("maturity_level" IN('FORMING', 'FORMED', 'CONTINUOUSLY_ENHANCED', 'LONG_TERM_STABLE'));
ALTER TABLE "public"."project_capability_fact_coverage" ADD CONSTRAINT "CONSTRAINT_40" CHECK("classification" IN('CONTRIBUTES_TO_CAPABILITY', 'NO_CAPABILITY_CHANGE', 'NEEDS_ATTENTION'));
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E" CHECK("history_scope" IN('CURRENT', 'HISTORICAL', 'UNKNOWN'));
ALTER TABLE "public"."project_agent_candidates" ADD CONSTRAINT "CONSTRAINT_64" CHECK("epistemic_status" IN('OBSERVED', 'VERIFIED', 'DECLARED', 'INFERRED', 'CONFLICTED', 'UNKNOWN', 'PROCESS_EVIDENCE'));
ALTER TABLE "public"."tasks" ADD CONSTRAINT "CONSTRAINT_69" CHECK("priority" IN('LOW', 'MEDIUM', 'HIGH'));
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E98D9" CHECK("event_category" IN('COMMIT', 'MERGE', 'PULL_REQUEST', 'ISSUE', 'TAG', 'FILE_CHANGE', 'DOCUMENT_VERSION', 'AGENT_RESULT', 'VALIDATION', 'USER_DECLARATION', 'PROJECT_FACT', 'EXTERNAL'));
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E98D90A" CHECK("transition_type" IN('CREATED', 'MODIFIED', 'REMOVED', 'RESTORED', 'RENAMED', 'MOVED', 'REPLACED', 'SPLIT', 'MERGED', 'REVERTED', 'REAPPLIED', 'UNKNOWN_TRANSITION'));
ALTER TABLE "public"."projects" ADD CONSTRAINT "CONSTRAINT_C47" CHECK("status" IN('PLANNING', 'BUILDING', 'PAUSED', 'COMPLETED', 'ARCHIVED'));
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "CONSTRAINT_3E9" CHECK("rewrite_state" IN('CURRENT', 'STALE', 'INVALIDATED'));
ALTER TABLE "public"."project_fact_sources" ADD CONSTRAINT "CONSTRAINT_5E" CHECK("source_type" IN('USER_MANUAL', 'ACCEPTED_CHANGE', 'AGENT_RESULT', 'ZIP_ANALYSIS', 'MODEL_SUMMARY'));
ALTER TABLE "public"."project_capability_evolutions" ADD CONSTRAINT "CONSTRAINT_F9" CHECK("evolution_type" IN('NEW_CAPABILITY', 'ENHANCE_CAPABILITY', 'ADD_EVIDENCE', 'MERGE_CAPABILITY', 'CORRECTION'));
ALTER TABLE "public"."project_fact_agent_result_refs" ADD CONSTRAINT "uk_project_fact_agent_result" UNIQUE ("fact_id", "agent_result_ref");
ALTER TABLE "public"."project_history_snapshots" ADD CONSTRAINT "CONSTRAINT_89" UNIQUE ("project_id");
ALTER TABLE "public"."project_memories" ADD CONSTRAINT "CONSTRAINT_93" UNIQUE ("project_id");
ALTER TABLE "public"."project_capabilities" ADD CONSTRAINT "uk_capability_legacy_card" UNIQUE ("project_id", "legacy_card_id");
ALTER TABLE "public"."project_timeline_summaries" ADD CONSTRAINT "uk_timeline_summary_period" UNIQUE ("project_id", "granularity", "period_key");
ALTER TABLE "public"."project_timeline_theme_facts" ADD CONSTRAINT "uk_timeline_theme_fact" UNIQUE ("theme_id", "fact_id");
ALTER TABLE "public"."evidence_bundles" ADD CONSTRAINT "CONSTRAINT_C" UNIQUE ("work_session_id");
ALTER TABLE "public"."project_capabilities" ADD CONSTRAINT "uk_capability_identity" UNIQUE ("project_id", "stable_identity_key");
ALTER TABLE "public"."project_history_events" ADD CONSTRAINT "uk_project_history_event_key" UNIQUE ("project_id", "stable_event_key");
ALTER TABLE "public"."project_fact_history_states" ADD CONSTRAINT "uk_project_fact_history_project" UNIQUE ("project_id");
ALTER TABLE "public"."project_fact_commit_refs" ADD CONSTRAINT "uk_project_fact_commit" UNIQUE ("fact_id", "commit_sha");
ALTER TABLE "public"."project_capability_fact_coverage" ADD CONSTRAINT "uk_capability_fact_coverage" UNIQUE ("project_id", "fact_id");
ALTER TABLE "public"."project_capability_map_states" ADD CONSTRAINT "uk_capability_map_project" UNIQUE ("project_id");
ALTER TABLE "public"."project_facts" ADD CONSTRAINT "uk_project_fact_fingerprint" UNIQUE ("project_id", "fact_fingerprint");
ALTER TABLE "public"."project_history_window_checkpoints" ADD CONSTRAINT "uk_history_window_cache" UNIQUE ("project_id", "cache_key");
ALTER TABLE "public"."project_fact_file_refs" ADD CONSTRAINT "uk_project_fact_file" UNIQUE ("fact_id", "file_path");
ALTER TABLE "public"."project_fact_cursors" ADD CONSTRAINT "uk_project_fact_cursor_project" UNIQUE ("project_id");
ALTER TABLE "public"."users" ADD CONSTRAINT "CONSTRAINT_6A" UNIQUE ("email");
ALTER TABLE "public"."project_review_cursors" ADD CONSTRAINT "CONSTRAINT_C4C" UNIQUE ("project_id");
ALTER TABLE "public"."project_capability_evolutions" ADD CONSTRAINT "uk_capability_evolution_fingerprint" UNIQUE ("project_id", "operation_fingerprint");
ALTER TABLE "public"."project_evolution_bridges" ADD CONSTRAINT "uk_evolution_bridge_fingerprint" UNIQUE ("project_id", "bridge_fingerprint");
ALTER TABLE "public"."project_structure_indexes" ADD CONSTRAINT "CONSTRAINT_5A" UNIQUE ("project_id");
ALTER TABLE "public"."project_understanding_snapshots" ADD CONSTRAINT "CONSTRAINT_55" UNIQUE ("project_id");
ALTER TABLE "public"."project_capability_attention" ADD CONSTRAINT "uk_capability_attention_fingerprint" UNIQUE ("project_id", "attention_fingerprint");
ALTER TABLE "public"."project_capability_facts" ADD CONSTRAINT "uk_capability_fact" UNIQUE ("capability_id", "fact_id");
