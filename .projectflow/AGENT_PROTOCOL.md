# ProjectFlow V3.3.3 Agent Protocol

Before starting substantial work, read `PROJECT_CONTEXT.md` and the task-relevant source files. ProjectFlow follows: rules collect facts, models interpret, rules validate, users confirm.

After completing a task, create `.projectflow/agent-results/<timestamp-topic>/result.json` and optionally `summary.md`.

`result.json` must contain:

- `taskGoal`: the actual task objective.
- `actualChanges`: changes that were implemented, never plans presented as completed work.
- `keyFiles`: 仓库相对路径 only; do not write machine absolute paths.
- `verification`: `build`, `tests`, and `manualCheck`; use `not_run` when a check was not run.
- `unfinished`: known incomplete work or an empty array.
- `sedimentCandidates`: evidence-backed capabilities or outcomes worth user confirmation.

`actualChanges` and every sediment candidate must describe the concrete development result in human-readable language. Do not submit directory names, file/commit counts, “development progress”, or generic optimization claims as the result. Include user/developer-visible behavior, affected entry or flow, verification performed, and explicit uncertainty when tests, builds, model analysis, GitHub access, or other evidence is missing.

Use `.projectflow/templates/result.json` as the shape. Do not include secrets, tokens, full diffs, generated artifacts, or unsupported claims. ProjectFlow imports results as candidate evidence; an Agent result never confirms a project sediment by itself.
