# ProjectFlow Product Constitution

Version: V3.7.5

This document is the single authoritative source for ProjectFlow product semantics. Other documents should link here instead of creating a different definition.

## Product purpose

ProjectFlow turns the material a user actually has into a traceable, verifiable and continuously maintainable project fact foundation. It serves people and Agents through the same evidence-bound state. It may help explain a project, but it cannot decide what the user should value.

ProjectFlow supports software, data, research, writing, design, office, creative and other computer-based projects. Git, source code, README, tests, CI, a manifest, an Agent Result or a complete history are never prerequisites.

## Evidence and derived intelligence

- Evidence is a bounded source or a normalized source envelope.
- `ProjectFact` is the only durable factual source.
- Understanding, Analysis Plan, Dynamic Profile, Historical Coverage, Timeline summaries, Capability maps and Evolution views are replaceable or derived layers.
- A model, Agent, external adapter or projection does not become a fact source by existing.
- No Evidence means no factual claim. No applicable content means no view.

## Seven epistemic states

| State | Meaning | Strong fact authority |
| --- | --- | --- |
| `OBSERVED` | Objective content was directly observed in a project-bound source. This does not make every assertion inside that source true. | Yes, with valid Evidence and currentness |
| `VERIFIED` | A repeatable engineering validation checked the claim for an applicable Revision and limitations. | Yes |
| `DECLARED` | A user, README, document, Obsidian note, Issue, PR text or other source explicitly states a claim or intention. | No |
| `INFERRED` | A model or rule interprets existing material. | No |
| `CONFLICTED` | Sources disagree and the conflict cannot be safely resolved automatically. | No |
| `UNKNOWN` | Existing Evidence is insufficient. | No |
| `PROCESS_EVIDENCE` | An Agent, script, tool or workflow reports an action or result. It does not prove the result succeeded. | No |

Only `OBSERVED` and independently checked `VERIFIED` may enter the normal strong-fact path. Model agreement, model attention, an Agent completion claim, an Agent test claim or a fallback result never promotes a claim.

## Proof package

An important claim must remain traceable to project ID, statement, status, source type and identity, source and project Revision, Evidence refs, observed or verified time, currentness, limitations, validation method, coverage, and explicit invalidation or replacement information when available.

Sensitive files expose metadata only. Keys, Authorization values, full prompts, raw model responses, reasoning, full documents, patches and machine absolute paths are not persisted or returned.

## Engineering and model responsibilities

Engineering code discovers, bounds, classifies, redacts, ranks, executes registered capabilities, validates Evidence IDs and enforces ownership. A model interprets semantic importance, information gaps, applicable views, conflicts and currentness inside those boundaries.

Models receive known Evidence IDs and eligible Capability/View names. They cannot build commands, choose arbitrary paths, change Promotion rules, suppress raw events or create strong facts.

Production and Eval use one provider-neutral semantic contract. Provider adapters may differ technically, but fact states, Ground Truth, thresholds, Promotion rules and fallback meaning cannot differ by model.

## Quality-first model execution

- Model elapsed time, Token usage, request count and monetary cost are process diagnostics. They are not product-quality defects and cannot lower a semantic score, suppress Evidence, skip a qualified deep read or reduce reasoning effort.
- When a Provider explicitly supports reasoning control, ProjectFlow uses `high` for connection, semantic and recovery requests. It does not switch to `low` to save time or Token.
- A reasoning-capable task may use the user-configured Provider output ceiling from the first request. The ceiling is a loose safety boundary, not a consumption target or an instruction to stop thinking early.
- Connection timeout, Provider request timeout, overall analysis deadline, cancellation, heartbeat and bounded retry remain separate safety controls. They prevent runaway or duplicate execution without treating normal long-running analysis as failure.
- ProjectFlow avoids only requests that add no new Evidence or diagnostic value. It does not trade necessary model thought for cheaper or faster completion.

## Open-world and dynamic views

ProjectFlow does not fill a fixed project template. Architecture, backend, database, timeline, evolution or capability views appear only when applicable Evidence exists. A small or non-code project may legitimately have very few views.

An importance score, key event, project phase, maturity level, milestone or success judgment is not automatically authoritative. User-defined milestones and phases are `DECLARED`; model suggestions are `INFERRED` or model-summary material.

## History and project life

Raw source-backed events remain complete, ordered and traceable. A display may fold or group them, but cannot delete them because a model considers them unimportant. Current code cannot prove a historical reason. Deprecation and technical debt require explicit source classes.

Timeline/model summaries are `INFERRED` and `NON_AUTHORITATIVE`. Every summary must resolve back to original events or facts. Evidence gaps remain visible rather than becoming a narrative bridge.

## Human and Agent contract

Agents read the same project-bound fact layer as humans. Context Packages are structured persisted memory, not free-form prompts. They preserve revision, Evidence, conflicts, unknowns, unread scope and limitations.

Agents submit Candidate Work Results. Changed files may be re-read and hashed by ProjectFlow, but Agent behavior and test claims remain process evidence until independently validated. Direct `OBSERVED` or `VERIFIED` candidate writes are rejected before persistence.

## External systems and ownership

GitHub, Obsidian, Hermes, document readers, code indexes and future providers connect through thin Evidence Source, Intelligence Provider or Projection adapters. Their outputs are project-bound, bounded, revisioned, redacted and replaceable.

ProjectFlow is local-first. Every read and write enforces authenticated user and project ownership. Obsidian is a projection, not a fact source. Hermes is a consumer, not an Agent manager.

## Non-goals

ProjectFlow is not a generic RAG system, IDE, code editor, model leaderboard, Provider switcher, Agent manager, GitHub replacement, Obsidian replacement, workflow engine, parser platform, daemon, updater or universal importance/phase engine.

V3.7.5 does not deliver the final GUI, full project-life reconstruction, Tag or Release. V3.8 entry is allowed only when the frozen two-model, product E2E, Strong Fact, Context Package, security and CI gates pass.
