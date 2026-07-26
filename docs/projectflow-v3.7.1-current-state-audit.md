# ProjectFlow V3.7.1 Current-state Audit

Date: 2026-07-26

## Observed baseline

V3.7.0 already persisted a bounded Evidence Source Map, Semantic Scout, validated Analysis Plan, Dynamic Profile, Historical Coverage and Evolution Preview. Repository Intake, Structure Index V2, persistent jobs, Model Gateway, ProjectFact, Timeline, Capability Map, Evolution Bridge, Gateway, Hermes and Obsidian were working boundaries.

The main gap was execution. Planner output exposed `toolsToInvoke`, but no coordinator executed DOC_READER, Git-history, Tag, worktree, manifest or Agent-result capabilities and fed newly obtained evidence back into validation and synthesis. The Scout/profile request also built JSON and then truncated the serialized string, which could produce incomplete JSON. Evidence sampling used a global order without explicit category/module quotas. Historical coverage combined heterogeneous signals into one coarse number. Repeat scans reopened unchanged content.

## Risks closed

| Risk | V3.7.1 result |
| --- | --- |
| Plan exists but tools do not run | Registry-validated coordinator and bounded local provider execute fixed capabilities |
| Model can influence commands | Model output is capability intent only; Provider owns fixed command arrays |
| Serialized JSON truncation | Category-aware tree packing produces and validates complete JSON |
| One source class crowds out rare evidence | Category/module quotas and duplicate compression expose diversity diagnostics |
| Many commits imply high confidence | Weighted dimensions and per-period confidence keep 0 Fact/0 Tag histories low |
| Repeat scans reread unchanged files | Metadata-signature inspection and sample caches expose read/cache counts; cold huge fallback bounds source-content opens |
| Deep reads leak credentials | Sensitive paths are metadata-only and all outbound text crosses a redactor |
| SCIP producer mutates projects | Production invocation remains deferred; current consumer/fallback boundary stays unchanged |

## Intentionally unchanged

No database schema, parser, Symbol protocol, vector database, watcher, daemon, Desktop shell, workflow engine, generic agent runtime or new model client was added. GET routes remain read-only. ProjectFact remains the only factual source. Existing H2 data and V3.7 JSON stay compatible.

## Remaining limits

The caches are process-local and rebuild safely after restart. Secret redaction is a model-boundary guard, not a replacement for a full repository security scanner. Git-history execution reads bounded metadata and names, never patches. Real Provider semantic quality was not tested without a user-supplied key. External SCIP index generation remains a future opt-in, sandboxed integration.
