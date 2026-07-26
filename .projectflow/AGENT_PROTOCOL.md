# ProjectFlow V3.7 Agent Protocol

Before starting substantial work, read `PROJECT_CONTEXT.md` and the task-relevant source files. ProjectFlow follows: rules collect evidence, models interpret within bounded evidence, rules validate, normal objective facts are recorded automatically, and only exceptions require user attention.

For project-understanding work, preserve the V3.7 boundary: discover real evidence before choosing analysis dimensions; keep Evidence, Semantic Scout, Analysis Plan, Dynamic Profile and Historical Coverage replaceable and separate from ProjectFact; use only registered tool capabilities; filter unknown evidence IDs; and do not invent absent project shapes, architecture, history or UI sections. Empty/blank/unchanged inputs should remain zero-model where possible.

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
