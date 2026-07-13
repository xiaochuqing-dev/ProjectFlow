# Background jobs

V3.3.8.1 does not change job creation, cancellation, retry, queue, budget, or restart semantics. After a successful `WORK_SESSION_SCAN`, the persisted job identifies the latest successful execution while the dashboard reconstructs the full visible result from persisted batch, segment, and work-session facts. The frontend writes that same complete result to the selected project's disposable snapshot immediately; a weak session-only refresh cannot replace its batch or segments.

V3.3.7 uses ProjectAnalysisJob for project, file, capability interpretation, work-session scan and capability-card analysis.

V3.3.8 keeps the same persisted job boundary. Gateway transport and output-recovery requests contribute to the same request/token/time budget. Retry type is diagnostic metadata, not a new job and not a path around active-job uniqueness.

Normal creation and retry share the same active-job lookup. Retry never bypasses equivalent `QUEUED`, `RUNNING`, or `CANCEL_REQUESTED` work; if none exists, the new row records `retried_from_job_id` and `retry_reason=USER_RETRY`. Successful jobs still reject retry.

Creation locks the owned project row, calculates a SHA-256 input fingerprint and returns an existing active job for identical input. Local defaults are 2 core threads, 4 maximum threads, queue length 16 and 20 globally active jobs. Queue rejection records REJECTED before any model call. Model HTTP concurrency is limited to 4.

Each job allows at most 3 model requests, 10 minutes total elapsed time and 60,000 total tokens. Transport retry and compact retry count toward the same recorded request total. Authentication/configuration errors, cancellation, database persistence errors and exhausted budgets are not automatically retried.

Cancellation is idempotent. QUEUED becomes CANCELLED directly; RUNNING becomes CANCEL_REQUESTED and reaches CANCELLED at the next safe checkpoint. Checkpoints surround Git, GitHub, model dispatch/response and formal persistence. Existing successful results and confirmed sediments/cards remain intact.

On restart, QUEUED is re-enqueued. A pre-model RUNNING job becomes RETRYABLE. A job at a model or persistence stage becomes INTERRUPTED and requires explicit retry because billing state may be unknown.
