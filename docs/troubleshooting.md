# Troubleshooting

If the sediment processing center reports “沉淀批次读取失败，请查看本地服务日志后重试”, inspect the backend log for the returned error code. Current V3.3.8.1 reads tolerate historical nullable fields and label incomplete batches instead of requiring database deletion. Do not manually erase old batches.

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
