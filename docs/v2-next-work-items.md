# ProjectFlow V2 Next Work Items

This file tracks capabilities that are intentionally not presented as complete in the current V2 Core implementation.

## Current V2 Core Baseline

- ZIP upload is the primary low-cost initial project import path.
- ProjectFlow can infer a project name from the ZIP root folder and extract a local project profile from README, package config, Maven config, Docker config, source structure, tests, and startup scripts.
- Project Material, AI suggestions, Project Memory, Snapshot, and Evolution Record remain behind user confirmation.
- Model provider configuration lives in personal settings, not in the project management workspace.

## Next Work

### 1. Real Model-Enhanced Analysis

- Replace local-only project suggestions with model-enhanced structured analysis when a real DeepSeek or OpenAI-compatible provider is configured.
- Keep local project profile extraction as deterministic context for the model.
- Validate model output against the expected JSON structure before saving suggestions.
- On failure, show a friendly retry/settings message without exposing API key, headers, prompts, or stack traces.

### 2. Suggestion Editing Before Apply

- Let users edit suggestion title, reason, and payload before applying.
- Keep the original generated suggestion for traceability.
- Show changed fields clearly before confirmation.

### 3. Undo Or Audit Trail

- Add a durable record of which suggestion created which task, dev log, memory update, snapshot, or evolution record.
- Support at least manual rollback guidance; full undo can come later.

### 4. Project Detail Memory View

- Add a project detail tab for Project Memory.
- Show long-term identity, current stage, module completion, risks, decisions, developer learnings, showcase assets, and next steps.

### 5. Browser Folder Upload

- Defer browser folder selection until it is clearly cheaper and stable enough.
- ZIP remains the default because it is lower cost and works consistently across browsers and deployments.

### 6. Better ZIP Profiling

- Improve package and build-file parsing with structured parsers where useful.
- Detect monorepos, frontend/backend split, test frameworks, Docker services, and missing startup paths more accurately.
- Keep `.env`, large binaries, images, build outputs, and dependency folders excluded.

### 7. Incremental Material Workflow

- After initial ZIP import, support agent summaries, commit logs, md/txt/docx files as incremental materials for the selected project.
- Compare each round against the previous Snapshot.
- Clearly separate first-time project import from ongoing update import in the UI.

