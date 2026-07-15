# Background jobs

## V3.4.0 fact ingestion and history reconstruction

V3.4.0 reuses the V3.3.7 persisted executor, active-job uniqueness, budgets, cancellation, retry lineage, and restart semantics. It does not introduce an unbounded second worker system.

A successful incremental scan persists the Change Batch and Development Segments, performs idempotent Project Fact ingestion, records fact/attention counts, and advances the incremental Fact Cursor only after persistence succeeds. Cancellation and budget checkpoints remain before external calls, recovery requests, fact persistence, and cursor advancement. A failed ingestion cannot leave a cursor pointing past missing facts.

History reconstruction is a durable background job with its own `ProjectFactHistoryState`. The state freezes an upper-bound commit, calculates covered/uncovered commits, and processes bounded chunks oldest-first. Each completed chunk persists its batch, segments, facts, coverage, last processed commit, and completed-chunk count. Cancellation preserves completed chunks. Restart/retry resumes from the checkpoint and skips covered commits.

History work does not hold a transaction while running Git or model calls, does not move the incremental Fact Cursor, and does not block a normal new-change scan. It starts only when prerequisites are available and may remain `WAITING_FOR_MODEL` without making the workbench unusable. The full Git history is never placed in a single model request.

`NEEDS_ATTENTION` is persisted fact-quality state, not a waiting job state. It does not keep a batch running or prevent subsequent work.

V3.3.8.1 does not change job creation, cancellation, retry, queue, budget, or restart semantics. After a successful `WORK_SESSION_SCAN`, the persisted job identifies the latest successful execution while the dashboard reconstructs the full visible result from persisted batch, segment, and work-session facts. The frontend writes that same complete result to the selected project's disposable snapshot immediately; a weak session-only refresh cannot replace its batch or segments.

V3.3.7 uses ProjectAnalysisJob for project, file, capability interpretation, work-session scan and capability-card analysis.

V3.3.8 keeps the same persisted job boundary. Gateway transport and output-recovery requests contribute to the same request/token/time budget. Retry type is diagnostic metadata, not a new job and not a path around active-job uniqueness.

Normal creation and retry share the same active-job lookup. Retry never bypasses equivalent `QUEUED`, `RUNNING`, or `CANCEL_REQUESTED` work; if none exists, the new row records `retried_from_job_id` and `retry_reason=USER_RETRY`. Successful jobs still reject retry.

Creation locks the owned project row, calculates a SHA-256 input fingerprint and returns an existing active job for identical input. Local defaults are 2 core threads, 4 maximum threads, queue length 16 and 20 globally active jobs. Queue rejection records REJECTED before any model call. Model HTTP concurrency is limited to 4.

Each job allows at most 3 model requests, 10 minutes total elapsed time and 60,000 total tokens. Transport retry and compact retry count toward the same recorded request total. Authentication/configuration errors, cancellation, database persistence errors and exhausted budgets are not automatically retried.

Cancellation is idempotent. QUEUED becomes CANCELLED directly; RUNNING becomes CANCEL_REQUESTED and reaches CANCELLED at the next safe checkpoint. Checkpoints surround Git, GitHub, model dispatch/response and fact/legacy persistence. Existing successful results, Project Facts, completed history chunks, sediments, and confirmed cards remain intact.

On restart, QUEUED is re-enqueued. A pre-model RUNNING job becomes RETRYABLE. A job at a model or persistence stage becomes INTERRUPTED and requires explicit retry because billing state may be unknown.
