# Troubleshooting

If Timeline facts and statistics are visible but a summary is missing, inspect the persisted summary status before retrying. `WAITING_FOR_MODEL` means facts are safe and Provider configuration will requeue automatically. `DIRTY`, `QUEUED`, and `GENERATING` are normal refresh states. `FAILED` preserves any previous READY content as stale; retry is recovery only. Do not edit facts or delete the H2 database to repair a derived summary.

If coverage is below sourceFactCount, inspect unknown/missing IDs, duplicate membership, planning fields, fact fingerprint changes, and history status. GET endpoints must not call the model. A running history backfill intentionally defers summary jobs until its checkpointed chunks complete.

If a successful V3.4.0 scan shows segments but no Project Facts, inspect the batch fact-ingestion status and safe failure diagnostics before retrying. A reusable batch is expected to idempotently fill missing facts. Do not delete the batch, move the Fact Cursor manually, or rerun the model blindly; an ingestion failure must leave the cursor unchanged.

`NEEDS_ATTENTION` is not a failed batch. It means a fact has missing/conflicting evidence, an incomplete boundary, degraded occurrence time, or an unsafe duplicate decision. Other facts remain recorded and the incremental cursor may advance. If the whole next scan is blocked, treat that as a cursor/transaction defect rather than expected attention behavior.

If history coverage is not moving, distinguish `WAITING_FOR_MODEL`, `RUNNING`, `PAUSED`, and `FAILED`. Check the persisted upper bound, last processed commit, completed chunk count, and safe error summary. Completed facts and chunks must survive cancellation or restart. Retry resumes from the checkpoint and must not resend covered commits; never clear Project Facts to restart history.

If an old commit appears under today's date, inspect its commit/Agent evidence time and fact occurrence window. `createdAt` is ingestion time, not event time. Missing reliable evidence time must be marked as a degraded diagnostic or Needs Attention rather than silently displayed as today.

If duplicate facts appear after retry or migration, compare source batch/segment and canonical commit/Agent/evidence references. The fingerprint is evidence-derived, not title-derived. Do not merge/delete long-term facts by title similarity; preserve data and fix the idempotency boundary.

For V3.3.8.1 H2 upgrades, preserve `.projectflow/local-data/` and validate a copied/file-backed database first. Current startup may add ProjectFact, FactCursor, and history-state schema and idempotently migrate legacy segments/sediments. Never delete the real database, old batches, old changes, old sediments, or Review Cursor as an upgrade workaround.

If the legacy sediment processing center reports “沉淀批次读取失败，请查看本地服务日志后重试”, inspect the backend log for the returned error code. V3.3.8.1 compatibility reads tolerate historical nullable fields and label incomplete batches instead of requiring database deletion. Do not manually erase old batches.

If a completed scan is absent after navigation, call `GET /api/projects/{projectId}/dashboard-bootstrap`. A correct response proves persisted state independently of sessionStorage. Clearing sessionStorage is safe; the page should restore from this endpoint. A GitHub/output/material refresh warning is secondary and must not clear the latest batch or development segments.

PostgreSQL Testcontainers says no valid Docker environment: start Docker Desktop and rerun `mvn.cmd -Ppostgres-it verify`. This is a real test failure, not a model or H2 failure.

Playwright cannot find a browser: run `npm.cmd run test:e2e:install`. On Windows local verification can use installed Microsoft Edge; CI installs Chromium and ffmpeg.

A job is REJECTED: the bounded queue or global active limit is full. No model request was sent; retry later.

A job is INTERRUPTED: the service restarted after a model request may have been sent. ProjectFlow will not replay it automatically. Review the preserved old result and explicitly rerun if needed.

A job is RETRYABLE: the service restarted before model dispatch. It is safe to run again.

A job remains CANCEL_REQUESTED: the current synchronous Git, GitHub or model HTTP call must return or reach its single-request timeout. No later retry or formal write will start.

Retry returns an existing job ID: this is expected when an equivalent `QUEUED`, `RUNNING`, or `CANCEL_REQUESTED` job exists. Retry never creates a parallel equivalent task.

An old H2 database fails on a null optimistic-lock version: V3.3.7 finalization adds the job version column with database default `0`. Run the current application once with its normal `ddl-auto=update`; do not delete the database.

If an old H2 database rejects `CANCEL_REQUESTED` or another current job state, V3.3.8 expands the historical job-status enum on startup. It also adds missing change-batch timing columns and backfills nullable timing/worktree flags before normal reads. Do not delete the database to work around these upgrade errors.

If diagnostics report `SCHEMA_MISMATCH`, ProjectFlow performs one targeted Schema repair. `SCHEMA_REPAIR_FAILED` means the second JSON was still not compatible; old successful results remain. `OUTPUT_BUDGET_EXHAUSTED` and `REASONING_EXHAUSTED_OUTPUT` use different recovery budgets and should not be treated as network failures.

## V3.4.2 capability map

`WAITING_FOR_MODEL` means facts remain readable but no default Provider can generate the map. `FAILED` means no successful map exists; `READY_STALE` means a prior READY map is preserved while the latest refresh failed or new facts are dirty. Retry the failed refresh after correcting Provider connectivity or Schema output; do not delete capability tables or ProjectFact. Unknown fact/capability IDs, duplicate or missing classification, prohibited planning/maturity fields and unsafe merge are validation failures, not database loss. File-backed H2 upgrades must be tested on a copy and allowed to run `ddl-auto=update`; never delete the user database or edit machine-wide configuration.
