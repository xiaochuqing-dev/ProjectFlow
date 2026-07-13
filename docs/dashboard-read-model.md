# Dashboard Read Model

## Authority and loading order

Persisted ProjectAnalysisJob, ChangeBatch, DevelopmentSegment, ProjectChange, and ProjectSediment records are authoritative. React state is the current view. The schema-versioned, project-scoped sessionStorage snapshot is only an immediate-render cache.

The dashboard loads in this order:

1. Render a matching project snapshot if available, even when stale.
2. Call `GET /api/projects/{projectId}/dashboard-bootstrap` to calibrate core state.
3. Load materials, suggestions, evolution history, tasks, evidence bundles, conflicts, outputs, Providers, and GitHub status independently.
4. Preserve core state when any secondary read fails and show a local retry notice.

## Bootstrap payload

The response contains the owned project summary, memory/path summary, latest successful WORK_SESSION_SCAN job projection, latest persisted batch and its development segments, up to 20 persisted work sessions, pending formal-review count, latest project-analysis projection, Provider availability, and generation time. It does not return keys, prompts, raw model responses, or reasoning.

The service performs database-only latest/count/bounded-list reads. It never runs Git, GitHub CLI, a model request, filesystem scanning, or text analysis.

## Merge and isolation rules

Sessions may refresh independently. A session-only response with no loaded batch or segments preserves an existing complete batch and non-empty segment list. An authoritative bootstrap may explicitly establish absence. Every response and snapshot is guarded by project ID and request generation so a late A response cannot overwrite B.

Snapshot schema version 2 records `projectId`, `capturedAt`, `latestScanJobId`, `latestBatchId`, and `latestBatchUpdatedAt`. The default freshness diagnostic is five minutes; stale cache can render while database calibration runs. Logout clears all snapshots, project deletion clears only that project's snapshot, and the legacy single key migrates only when its project matches.
