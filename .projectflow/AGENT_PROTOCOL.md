# ProjectFlow V3.7.1 Agent Protocol

Before starting substantial work, read `PROJECT_CONTEXT.md` and the task-relevant source files. ProjectFlow follows: rules collect evidence, models interpret within bounded evidence, rules validate, normal objective facts are recorded automatically, and only exceptions require user attention.

For project-understanding work, preserve the V3.7.1 boundary: Discover real evidence before choosing dimensions; let Scout request only capability names and evidence IDs; validate the plan through the registry; Execute only bounded providers with fixed commands and safe relative paths; Validate every produced evidence reference; run Final Synthesis only when new high-value evidence exists. Evidence, Scout, Plan, Execution, Dynamic Profile and Historical Coverage remain replaceable and separate from ProjectFact. Empty/blank/unchanged inputs remain zero-model where possible; all eligible flows are capped at 0/1/2 model requests.

Before sending model context, use category-aware complete-JSON packing and outbound secret redaction. Do not slice serialized JSON, persist complete deep-read documents, read sensitive-file content, accept model-built commands, or imply history maturity from commit count alone. A failed capability degrades to deterministic evidence and diagnostics. A failed refresh retains the prior successful understanding. External SCIP producers remain opt-in and deferred until a separate sandboxed PoC proves no silent download, build, runtime or machine mutation.

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
