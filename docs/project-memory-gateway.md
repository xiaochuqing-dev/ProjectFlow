# Project Memory Gateway

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

Recent changes accept inclusive ISO-8601 `from` and `to`. Timeline accepts `DAY`, `WEEK`, `MONTH`, or `LIFECYCLE`. Search accepts FACT, TIMELINE, CAPABILITY and EVOLUTION entity filters and explains matched fields. Paged reads accept zero-based `page`, bounded `size`, and `compact` or `detailed` output.

## Trace, privacy and audit

Fact trace returns only bounded batch, commit, repository-relative file, Agent-result, evidence and capability references. It excludes diffs, absolute paths, fingerprints, prompts, raw responses, reasoning, credentials and authorization data.

Every Gateway operation records a safe audit event with user/project, operation, result count, latency, status, entity/filter summary, query length/hash and caller hash. Full private queries and caller text are never persisted. Project deletion removes the matching audit rows.

## Compatibility and failure behavior

The Gateway is additive. It does not replace the existing frontend APIs, ProjectFact ingestion, FactCursor, history backfill, Timeline generation or Capability Map generation. Invalid ownership remains a normal not-found boundary. Back-end unavailability, invalid responses and oversized results are surfaced as machine-readable adapter errors. Remote MCP transport is intentionally outside V3.4.3; the supported transport is local stdio connected to a loopback ProjectFlow backend.
