# Dashboard Read Model

## Authority and loading order

Persisted ProjectAnalysisJob, ChangeBatch, DevelopmentSegment, ProjectFact, ProjectFactCursor, and ProjectFactHistoryState records are authoritative. ProjectChange and ProjectSediment remain authoritative only for their legacy compatibility views. React state is the current view. The schema-versioned, project-scoped sessionStorage snapshot is only an immediate-render cache.

The dashboard loads in this order:

1. Render a matching project snapshot if available, even when stale.
2. Call `GET /api/projects/{projectId}/dashboard-bootstrap` to calibrate core state.
3. Load paged Project Records/facts, history detail, materials, legacy suggestions, evolution history, tasks, evidence bundles, conflicts, outputs, Providers, and GitHub status independently.
4. Preserve core state when any secondary read fails and show a local retry notice.

## Bootstrap payload

The response contains the owned project summary, memory/path summary, latest successful WORK_SESSION_SCAN job projection, latest persisted batch and its development segments, the batch's lightweight fact-recording status/counts needed by the workbench, bounded work sessions, latest project-analysis projection, Provider availability, and generation time. Legacy pending-review count may remain for old compatibility records but is not the new workflow action. Full fact pages, evidence collections, and history chunks stay behind focused paged reads. The response does not return keys, prompts, raw model responses, or reasoning.

The service performs database-only latest/count/projection/bounded-list reads. It never runs Git, GitHub CLI, a model request, filesystem scanning, history backfill, migration, or text analysis. It must not load every Project Fact to calculate count, earliest, latest, or coverage.

## Merge and isolation rules

Sessions may refresh independently. A session-only response with no loaded batch or segments preserves an existing complete batch and non-empty segment list. A weak secondary fact/history response cannot clear an authoritative batch or fact summary. An authoritative bootstrap may explicitly establish absence. Every response and snapshot is guarded by project ID and request generation so a late A response cannot overwrite B.

The project-scoped snapshot records display-calibration identifiers such as `projectId`, `capturedAt`, latest scan job/batch IDs, and batch update time. It never becomes Project Fact, Fact Cursor, or history-state storage. The default freshness diagnostic is five minutes; stale cache can render while database calibration runs. Logout clears all snapshots, project deletion clears only that project's snapshot, and a legacy single key migrates only when its project matches.
