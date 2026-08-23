# Project Memory Gateway

## V3.8.5 corrected history view

Final RC2 wording validation occurs before the snapshot is stored. Gateway consumers therefore receive the same validated human title, summary and distinct Before/Change/After wording; they do not rerun subject normalization, infer claim strength or call a model.

RC2 preserves the corrected `presentationRevision` on compact and full History responses. Story pagination supports `includeHidden=false` by default and an explicit true mode for complete reference-graph consumers. The Gateway does not independently apply corrections or infer Primary/Supporting links.

Gateway history reads now apply the persisted `USER_DECLARED_PRESENTATION` overlay at read time. Responses distinguish automatic text from user-declared text, expose presentation authority/revision, Primary/Supporting role, technical atom and commit-summary drill-down, correction conflicts, unknowns and coverage. The overlay is presentation-only: Gateway never changes ProjectFact, raw events or Evidence and never invokes a model.

`GET /api/projects/{projectId}/project-memory/history/corrections` lists the same bounded correction records used by the history controller. Agent Context, Hermes, frontend and Obsidian consume this Gateway view instead of reading correction or history repositories directly.

## V3.8.0 history-first reads

Project Memory Gateway now leads snapshot and brief semantics with persisted Project History: what happened, earliest confirmed state, current state, recent stories, dynamic chapters, conflicts, unknowns and coverage. Existing Fact, fixed Timeline, Capability and Evolution reads remain compatible and keep their SOURCE/DERIVED labels.

New read-only Gateway routes expose history overview, chapters, stories, threads, raw events and event Evidence under `/api/projects/{projectId}/project-memory/history`. They delegate to `ProjectHistoryReadService`, preserve 1–100 page bounds and ownership, and never call a model, Git, filesystem or refresh job. History entities remain DERIVED except the normalized source-event payload fields that directly report their original authority/epistemic status; a Gateway consumer must not treat a Chapter or Story as ProjectFact.

ProjectFlow V3.4.3 adds one read-only business-semantic gateway above ProjectFact, Timeline, ProjectCapability and ProjectCapabilityEvolution. Hermes and later projections consume this layer instead of coupling to repository tables or independently reinterpreting facts.

## Semantic contract

- ProjectFact remains the only factual source. Timeline periods/themes, capabilities and evolutions are explicitly labelled DERIVED.
- `occurredAt` is the event time used for recent-change filtering and timeline membership. `recordedAt` is persistence time, `analyzedAt` is analysis time, and an external consumer's `syncedAt` is projection time. They are never interchangeable.
- Failed Timeline refreshes retain deterministic facts/statistics and the previous successful summary as stale. Failed Capability refreshes retain the previous successful map.
- GET reads never invoke a model and never mutate Facts, Timeline, Capabilities or Evolutions.
- All reads are scoped by authenticated `userId` and owned `projectId`. Compact is the default; pages and context packs have hard bounds.

## Read API

All responses use the normal `{ "data": ..., "message": "OK" }` envelope.

| Purpose | Endpoint |
| --- | --- |
| Visible projects | `GET /api/project-memory/projects` |
| Current snapshot | `GET /api/projects/{projectId}/project-memory/snapshot` |
| Changes by occurrence time | `GET /api/projects/{projectId}/project-memory/recent-changes` |
| Cross-layer search | `GET /api/projects/{projectId}/project-memory/search` |
| Day/week/month/lifecycle timeline | `GET /api/projects/{projectId}/project-memory/timeline` |
| Stable capabilities | `GET /api/projects/{projectId}/project-memory/capabilities` |
| Chronological capability evolution | `GET /api/projects/{projectId}/project-memory/capabilities/{capabilityId}/evolution` |
| Evidence trace | `GET /api/projects/{projectId}/project-memory/facts/{factId}/trace` |
| Budgeted agent context | `GET /api/projects/{projectId}/project-memory/brief` |

V3.7.5 completes the Agent-oriented read layer without bypassing Gateway ownership:

| Purpose | Endpoint |
| --- | --- |
| All authorized projects | `GET /api/project-memory/portfolio` |
| Bounded cross-project query | `GET /api/project-memory/portfolio/search` |
| Evidence lookup | `GET /api/projects/{projectId}/project-memory/evidence/{evidenceId}` |
| Strong/non-strong knowledge partitions | `GET /api/projects/{projectId}/project-memory/knowledge` |
| Task-relevant provenance package v2 | `GET /api/projects/{projectId}/project-memory/context-package` |

Context Package v2 accepts task description, scope, revision preference, Evidence depth and size budget. Its deterministic package revision covers the selected project-bound content; source ranges, currentness, conflicts, unknowns, limitations and unread scope remain explicit. It is derived from persisted ProjectFlow state, not generated by a host model.

Hermes maps these reads to read-only tools and `projectflow://projects/{projectId}/context` resources. Separate candidate APIs accept single candidates or bounded Agent work results but cannot write `OBSERVED` or `VERIFIED`. The local revalidation endpoint can verify a Fact or Evidence range and compare revisions through fixed bounded actions; it does not change the Gateway's read-only fact-consumer semantics.

Recent changes accept inclusive ISO-8601 `from` and `to`. Timeline accepts `DAY`, `WEEK`, `MONTH`, or `LIFECYCLE`. Search accepts FACT, TIMELINE, CAPABILITY and EVOLUTION entity filters and explains matched fields. Paged reads accept zero-based `page`, bounded `size`, and `compact` or `detailed` output.

## Trace, privacy and audit

Fact trace returns only bounded batch, commit, repository-relative file, Agent-result, evidence and capability references. It excludes diffs, absolute paths, fingerprints, prompts, raw responses, reasoning, credentials and authorization data.

Every Gateway operation records a safe audit event with user/project, operation, result count, latency, status, entity/filter summary, query length/hash and caller hash. Full private queries and caller text are never persisted. Project deletion removes the matching audit rows.

## Compatibility and failure behavior

The Gateway is additive. It does not replace the existing frontend APIs, ProjectFact ingestion, FactCursor, history backfill, Timeline generation or Capability Map generation. Invalid ownership remains a normal not-found boundary. Back-end unavailability, invalid responses and oversized results are surfaced as machine-readable adapter errors. Remote MCP transport is intentionally outside V3.4.3; the supported transport is local stdio connected to a loopback ProjectFlow backend.
