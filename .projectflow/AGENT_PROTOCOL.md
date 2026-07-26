# ProjectFlow V3.7.2 Agent Protocol

Before starting substantial work, read `PROJECT_CONTEXT.md` and the task-relevant source files. ProjectFlow follows: rules collect evidence, models interpret within bounded evidence, rules validate, normal objective facts are recorded automatically, and only exceptions require user attention.

For project-understanding work, preserve the V3.7.2 boundary: Discover real evidence before choosing dimensions; let Scout request only capability names and evidence IDs; validate the plan through the registry; Execute only bounded providers with fixed commands and safe relative paths; Validate every produced evidence reference; run Final Synthesis only when the auditable high-value evidence gate triggers. Evidence, Scout, Plan, Execution, Dynamic Profile and Historical Coverage remain replaceable and separate from ProjectFact. Empty/blank/unchanged inputs remain zero-model where possible; all eligible flows are capped at 0/1/2 logical Model Gateway calls.

Before sending model context, use category-aware complete-JSON packing and outbound secret redaction. Do not slice serialized JSON, persist complete deep-read documents, read sensitive-file content, accept model-built commands, or imply history maturity from commit count alone. Agent results remain process evidence; token/latency/model usage remains process metadata. A failed capability degrades to deterministic evidence and diagnostics. A failed Final Synthesis must keep Stage 1 plus validated tool evidence as a current `FAILED_DEGRADED` result; only a failed first semantic stage may retain the prior successful understanding as stale. Internal evaluation metrics remain test/report artifacts and never enter product DTOs, APIs, snapshots, databases or UI.

External sources and consumers must use the Evidence Source Adapter, Intelligence Provider Adapter or Projection Adapter boundary. Every external envelope must be project-bound, bounded, revisioned, redacted and raw-payload-free; an adapter result never becomes a ProjectFact by itself. Do not build an agent manager, Provider switcher, token dashboard, model leaderboard, repository replacement, generic RAG/workflow platform, parser/SCIP producer, updater or developer-tool control center inside ProjectFlow.

Semantic Scout and Final Synthesis currently use v3 prompts. V3.7.2 real-model quality remains `NOT PASSED`: a funded GLM `glm-5.2` / OpenAI Responses run completed the unchanged 38-run set but had 19 bounded transport timeouts, Tool recall 0.1667, Dynamic View recall 0.0941 and Repeatability 0.4130; real `ProjectUnderstandingService.refresh()` acceptance passed only 2/8 core cases. Retain the earlier DeepSeek pilot/HTTP 402 history. Do not describe V3.7.2 as quality-approved or start V3.8 until the same versioned Ground Truth and core production-chain cases pass every published gate.

After completing a task, create `.projectflow/agent-results/<timestamp-topic>/result.json` and optionally `summary.md`.

`result.json` must contain:

- `taskGoal`: the actual task objective.
- `actualChanges`: changes that were implemented, never plans presented as completed work.
- `keyFiles`: 仓库相对路径 only; do not write machine absolute paths.
- `verification`: `build`, `tests`, and `manualCheck`; use `not_run` when a check was not run.
- `unfinished`: known incomplete work or an empty array.
- `sedimentCandidates`: evidence-backed capabilities or outcomes worth user confirmation.

`actualChanges` and every sediment candidate must describe the concrete development result in human-readable language. Do not submit directory names, file/commit counts, “development progress”, or generic optimization claims as the result. Include user/developer-visible behavior, affected entry or flow, verification performed, and explicit uncertainty when tests, builds, model analysis, GitHub access, or other evidence is missing.

Use `.projectflow/templates/result.json` as the shape. Do not include secrets, tokens, full diffs, generated artifacts, unsupported claims, raw model output, reasoning, absolute paths, or fabricated evolution. ProjectFlow imports results as candidate evidence; an Agent result never becomes a Project Fact or historical bridge by itself.
