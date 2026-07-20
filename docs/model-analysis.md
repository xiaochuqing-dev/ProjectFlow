# Model Analysis

## V3.4.1 Timeline model tasks

`PROJECT_TIMELINE_PERIOD_SUMMARY` and `PROJECT_TIMELINE_LIFECYCLE_SUMMARY` are registered ModelTaskTypes and use ModelGateway. Period prompts contain compact recorded-fact DTOs and an allowlist; lifecycle prompts contain compact month statistics/summaries and month keys. Output is rejected for unknown or missing IDs, cross-project membership, incomplete coverage, invalid schema, or next-step/roadmap/future-planning content. Large periods use bounded 120-fact chunks plus one synthesis request. Diagnostics retain only safe parameter, usage, finish and recovery metadata.

## V3.4.0 fact boundary

V3.4.0 does not make the model the database truth and does not redesign the V3.3.8 gateway. The model continues to organize an `AnalysisInputSnapshot` into `DevelopmentSegment` analysis results. Backend rules then validate project/batch ownership, source references, evidence, quality, content, occurrence time, and idempotency before fact persistence.

A normal MODEL segment with valid evidence and usable content may become a `RECORDED` ProjectFact automatically. A complete item recovered from truncation/reasoning output may also be recorded when its fact boundary is reliable; a recovery request by itself does not force human review. LOCAL_RULE with Git evidence and Agent result bound to objective code evidence may become facts under the same evidence rule. Agent-result-only or evidence-free output cannot masquerade as a strong fact.

The model is not asked to guess occurrence time. Commit and Agent-result timestamps are collected by evidence readers and passed to the persistence boundary. Fact fingerprinting uses source/evidence identity rather than generated title or summary.

`NEEDS_ATTENTION` represents a final evidence/quality exception such as conflicting sources, an incomplete fact boundary, unsafe duplication, or degraded time. It does not mean the model job or entire batch failed and does not block the Fact Cursor.

History backfill reuses the registered development-segmentation model path and current request/token/time budgets in bounded chunks. It never sends the entire Git history in one prompt and never re-analyzes already covered commits.

Because V3.4.0 keeps the ModelGateway, Provider capability, dynamic parameter, Schema repair, truncation, and reasoning-recovery contracts, fixed-model automation is used for fact ingestion, history, and browser regression. The V3.3.8 full real-DeepSeek matrix is not repeated unless this boundary is actually changed; any affected real check must be reported separately and must never be conflated with fixed-model evidence.

ProjectFlow V3.3.8 registers six real model entrypoints: Provider connection test, development-segment merge, whole-project analysis, file analysis, capability interpretation, and project-capability analysis. Production business services call `ModelGatewayService` with a `ModelTaskType`; direct model HTTP calls outside the gateway are not allowed.

The request policy combines Provider type, model name, capability profile, task type, input size, expected output shape, reasoning behavior, Provider output ceiling, and configured Temperature. Diagnostics retain configured, recommended, effective, sent/omitted, and decision reason separately. Unsupported Temperature or JSON mode fields are omitted.

Complex tasks no longer share a fixed 4000-token output limit. Recovery no longer uses a fixed 2000-token compact limit. Initial and recovery budgets are computed separately and remain bounded by Provider capability and the persisted task request/token/duration budgets.

The structured-output pipeline is:

Provider response -> content/reasoning/finish reason/usage extraction -> truncation classification -> balanced JSON candidate scan -> light syntax repair -> target-aware candidate and nested collection selection -> alias normalization -> Schema match -> bounded targeted recovery -> business validation -> persistence.

Retry types are `TRANSPORT_RETRY`, `TRUNCATION_RETRY`, `EMPTY_AFTER_REASONING_RETRY`, and `SCHEMA_REPAIR_RETRY`. Authentication/configuration errors are not retried. A recovery request does not receive another transport retry.

Reasoning text is never stored, logged, or returned. Diagnostics retain only presence, length, and whether reasoning likely exhausted the shared output budget. API keys, Authorization, full prompts and raw Provider responses are also excluded.

Legal JSON with the wrong business shape is not classified as a syntax failure. It receives one Schema repair request that re-encodes existing semantics into the minimal target Schema without re-running the analysis. Complete entries from a truncated array may be retained as a partial result.

## V3.4.2 capability tasks

`PROJECT_CAPABILITY_MAP_BOOTSTRAP` and `PROJECT_CAPABILITY_MAP_INCREMENTAL` are registered ModelTaskTypes and use the unified gateway. The model returns internal operations plus no-change and attention classifications; it never chooses database UUIDs, maturity or user-facing confirmation. Validation requires every allowed fact exactly once, rejects unknown/cross-project fact or capability IDs, duplicate/missing coverage, planning/reasoning/maturity fields and unsafe merge. Provider, model, usage, finish reason, parameters, retries and failure stage remain safe diagnostics; key, Authorization, full prompt, raw response and reasoning text are never stored.

## V3.4.3 integration boundary

Project Memory Gateway, all nine Hermes MCP tools and the budgeted project brief are deterministic reads of already persisted memory. They do not register a ModelTaskType, call ModelGateway, summarize again, or persist agent answers. Any model used by a Hermes host remains outside ProjectFlow and receives only the bounded, sanitized Gateway result selected by the tool call.

## V3.4.4 projection boundary

Obsidian projection has no `ModelTaskType` and never calls ModelGateway. It deterministically formats the already persisted Snapshot, Timeline summaries/themes, Capabilities, Evolutions and Facts returned by Project Memory Gateway. Sync does not create editorial summaries, prompts, responses, reasoning diagnostics or Provider cost; the same source version and content hash therefore produce a stable no-op.
