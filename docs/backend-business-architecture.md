# Backend Business Architecture

## Stable business map

| Layer | Responsibility | Input | Output | Must not do |
| --- | --- | --- | --- | --- |
| Evidence Source | Read Git, worktree, GitHub and Agent-result evidence with stable references | Repository state and bounded external reads | Evidence snapshots and ranges | Declare product facts or call models inside transactions |
| Analysis | Coordinate persisted jobs, cancellation, idempotency, model interpretation and evidence validation | Evidence snapshot, task type and provider | DevelopmentSegment or validated operations | Persist invented evidence or bypass ModelGateway |
| Factual Memory | Store what actually happened; ProjectFact is the only factual source | Validated segment/batch evidence | Immutable historical facts, attention and cursors | Delete facts during refresh or store plans as facts |
| Temporal | Derive DAY/WEEK/MONTH/lifecycle views by fact occurrence time | Complete fact period | Timeline summaries and themes | Use recorded/analyzed time as event time or mutate facts |
| Capability | Maintain durable capability identity, fact relations, maturity and Evolution | All classified facts or bounded increment | ProjectCapability, relations, Evolution and attention | Treat Timeline theme as capability or rewrite Evolution history |
| Memory Read | Assemble bounded snapshot, recent, search, timeline, capability, trace and brief semantics | Owned project and read parameters | Stable read models with truth labels | Trigger models, expose entities wholesale or become a fact source |
| External Adapter | Translate stable reads for REST, Hermes and Obsidian | Gateway DTOs | Local stdio/loopback responses and managed notes | Query repositories directly or write facts back |
| Model Infrastructure | Apply task policy, budget, temperature, retry, recovery, diagnostics and protocol translation | ModelTaskType, canonical request and Provider profile | Validated structured response and safe diagnostics | Let an SDK own business retry/recovery or branch business logic by brand |
| Legacy Compatibility | Keep old data and links readable while isolating them from the active chain | Historical entities and URLs | Read-only compatibility/migration results | Reintroduce confirmation/sediment logic into new scans |

The intended flow is Evidence → Analyze → Fact → Timeline/Capability → Memory Gateway → External Consumers. Model infrastructure is a cross-cutting boundary reached only through ModelGateway. A future Observer may invoke the Analyze use case; it must never call a protocol adapter or repository directly.

## V3.4.5 consolidation

`ProjectMemoryGatewayService` remains the API-compatible facade. Cross-layer lexical retrieval moved to `ProjectMemorySearchService`; fact evidence assembly and redaction moved to `ProjectEvidenceTraceService`. This removes three evidence repositories and about 300 lines of search/trace logic from the facade without renaming URLs or DTOs. Snapshot/timeline/capability/brief slices remain in the facade because no proven duplication justified a larger move.

Model calls remain behind `ModelGatewayService`. Business services register a `ModelTaskType`; adapters under `service/model` translate only the three supported protocols. Dynamic token/temperature policy, concurrency, cancellation, transport retry, JSON parsing, schema repair, truncation/reasoning recovery and diagnostics stay centralized.

## Transaction, ownership and performance rules

- External Git, GitHub, filesystem and model calls stay outside long database transactions.
- Project reads check userId plus projectId. Search and trace are read-only, bounded and model-free.
- Timeline and Capability refresh failures preserve the last successful derived state.
- The split reuses existing bounded repository queries; it does not add per-result evidence queries to search.
- Provider probing makes external calls before persisting only a compact safe profile.

## Legacy matrix

| Surface | Data may exist | Old link/reader | V3.4.5 class | Rule |
| --- | --- | --- | --- | --- |
| ProjectChange/SedimentAction/ProjectSediment | Yes | Old processing links/API | COMPAT_READ_ONLY | Readable; never created by new scans |
| ProjectCapabilityCard | Yes | Old capability links/frontend | COMPAT_READ_ONLY | Archived; ProjectCapability is authoritative derived map |
| DevelopmentSegment | Yes | Batch detail and fact migration | MIGRATION_ONLY plus analysis result | May migrate once to ProjectFact; never replaces facts |
| Missing Provider protocol/auth | Yes | Existing settings/API | MIGRATION_ONLY | Idempotently backfill conservative protocol/auth defaults |
| DevLog/Daily Review links | Possibly | Bookmarks | COMPAT_READ_ONLY | Redirect/read compatibility; Timeline is primary |
| Current ProjectFact/Timeline/Capability/Gateway | Yes | Active clients | ACTIVE_CORE | Preserve contracts and ownership |

Nothing was classified DEAD_SAFE_TO_REMOVE, so V3.4.5 performs no destructive legacy deletion. Full package reorganization and frontend reconstruction remain deferred.
