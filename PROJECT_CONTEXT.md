# ProjectFlow Project Context

Last updated: 2026-07-07

Use this file as the first read for substantial ProjectFlow work. It is a compact routing layer, not a replacement for source code. After reading it, open only the docs and modules relevant to the current task.

## Product Position

ProjectFlow V3.3.5 is a local-first development-change understanding and project-sedimentation tool for AI-assisted solo developers.

Current direction:

- Local Git supplies objective change evidence; Agent results add task intent and verification context.
- The primary workflow is 待整理变更 → 开发推进段 → 建议沉淀 → 项目沉淀.
- Rules collect and validate facts, models interpret them, and users make the final confirmation.
- AI/model calls are candidate generators only; user confirmation is required before treating output as official project facts.
- GitHub CLI is optional metadata/link enrichment and must never block local Git analysis.

V3.3.5 focus:

- Model responses carry finish reason, token usage, effective parameters, timeout, Provider/model, truncation, JSON repair, compact-retry, and partial-recovery diagnostics.
- Truncated structured output receives one compact retry; complete items in a truncated root array may be retained with warnings.
- Display sanitization no longer truncates persisted text. Lists clamp previews; detail pages show full normalized content; legacy ellipsis-ended records are marked for re-analysis.
- Sediment review uses recommendation, consequence preview, concrete confirmation feedback, and direct sediment navigation.
- Capability cards reference their analysis job; the page separates the current successful batch, latest failure, and history while preserving old successful and confirmed cards.
- Provider management supports editing, unique default selection, protected deletion, and explicit duplicate cleanup. Blank edit keys preserve existing secrets; only explicit clearing removes them.

V3.3.4 focus (still applies):

- Model failure notices are split into plain Chinese reasons (not configured / call failed / invalid response format / invalid evidence reference); "增强本地摘要" is removed and the result source is always "本地事实摘要".
- Local fallback titles and summaries are Chinese; raw English commit messages are rewritten or labeled "根据提交记录整理的变更".
- GitHub access moved into the "项目接入" area (local path / model / GitHub together) with a login wizard ("打开登录终端" runs only the fixed whitelisted `gh auth login --web --clipboard`).
- Internal enums (CALL_FAILED / LOCAL_RULE / CONNECTED / local_ahead etc.) are translated to Chinese via shared `status-labels.ts`.
- evidenceGap is based on real evidence conditions (not GitHub participation) and carries an evidenceGapReason.
- "分析项目能力" is a recoverable async job (CAPABILITY_CARD_ANALYSIS); refresh/leave does not lose the task; re-analysis replaces only unconfirmed candidates.

V3.3.3 focus (still applies):

- Analysis progress is visible (stage / elapsed time / input scale); long model runs no longer look like spinning.
- Model results are retained by default and marked for review; the quality gate is a marker, not a batch rejector.
- User-visible analysis content must be natural Simplified Chinese; English commits/paths/identifiers stay in evidence details only.
- GitHub is surfaced on the home screen (not "GitHub 增强") with login guidance and read-only sync refresh.
- Multi-source evidence (local Git / worktree diff / GitHub / Agent result / scan scope) is organized into an analysis input snapshot fed to the model.
- Model-dependent entries (分析新变化, 分析项目能力) require a configured model; missing model shows facts-only and guides the user to configure one.

Do not treat ProjectFlow as a generic Kanban app, SaaS admin panel, hotel/library system, or marketing site. The product should feel like a serious developer tool focused on project understanding.

## Current Stage

The project has moved beyond the V1/V2 planning baseline. Current V3.3.3 focus:

- Workbench first screen exposes: add/import project → bind local path → analyze new changes → review development segments → confirm sediment → reuse output.
- Scan boundaries use the last confirmed review cursor rather than the current day.
- Suggested sediment supports NEW_SEDIMENT, MERGE_EXISTING, EVIDENCE_ONLY, and IGNORE.
- Subjective fields without confirmed evidence stay hidden from the default project-sediment view.
- ProjectFlow must be usable without reading docs or requiring an agent to explain the workflow.
- Workbench actions should prioritize the next concrete step, not a flat set of unrelated buttons.
- Evidence Bundle lifecycle is now product-visible through `status`, `nextAction`, and `changeId`.
- Change review is the project asset intake desk: accepted changes write to Project Memory and Fact Sources.
- Project profile should show both current fields and project growth history.
- Daily review and outputs should explain which confirmed sources they use.
- Left-side first-screen actions still include raw project connection: import zip, bind real project path, write `.projectflow` protocol, scan agent result.
- "Project facts" UI language should become "项目画像" / "项目档案".
- Model API value should move upstream into project analysis, module/file explanation, risk identification, agent result parsing, and profile update suggestions.
- Model-not-configured and model-failed states must keep local-rule fallback usable.
- Segment output must describe concrete results and pass evidence plus quality validation.
- Scan fingerprints stabilize repeated analysis and diagnostics explain model, fallback, GitHub, worktree, and remote state.
- Capability output is stored as structured cards generated from the whole confirmed project evidence set.

Recent implementation report says these are already present:

- `POST /api/projects/{projectId}/analysis/run`
- `POST /api/projects/{projectId}/files/analyze`
- Project analysis and file analysis can use model when configured, otherwise local rules.
- Sensitive file paths are not sent to the model.
- Analysis records exist and can be listed, opened, and deleted.

Known remaining boundary:

- Analysis records exist, but deeper persistent `ProjectAnalysisRun` / `ProjectFileInsight` style history is still a likely next step.
- Real DeepSeek key/network validation may not have been done locally.
- File analysis is based on imported zip summaries and key content, not full source indexing.

## Tech Stack

- Frontend: Next.js App Router, TypeScript, React, Tailwind CSS, lucide-react.
- Backend: Spring Boot 3.5.x, Java 17, Maven, Spring Web, Validation, Actuator, JPA, Redis, Spring Security Crypto, JJWT.
- Database/cache: PostgreSQL and Redis via Docker Compose.
- Runtime: Windows/PowerShell local workflow, Docker Desktop required for PostgreSQL and Redis.

Important commands:

```powershell
cd frontend
npm.cmd run build

cd ..\backend
C:\Users\Administrator\Desktop\apache-maven-3.9.9\bin\mvn.cmd -q test
```

Startup:

```powershell
.\start-projectflow.ps1
```

or double-click `start-projectflow.bat`.

## Repository Map

```text
backend/                  Spring Boot API
frontend/                 Next.js app
docs/                     product, architecture, data model, stage plans, reports
.projectflow/             agent bridge protocol/context/results for this project
docker-compose.yml        PostgreSQL and Redis
.env.example              repo-safe environment template
start-projectflow.ps1     Windows startup orchestration
start-projectflow.bat     simple Windows launcher
```

Key docs to read selectively:

- `README.md`: product scope, tech stack, local startup.
- `docs/architecture.md`: frontend/backend layering and core flows.
- `docs/data-model.md`: base V1 data model and ownership rule.
- `docs/api-design.md`: V1 API shape and endpoint baseline.
- `docs/v2-core-plan.md`: Project Material, Project Memory, Snapshot/Diff, AI suggestion confirmation model.
- `docs/v3-product-direction-plan.md`: V3 developer workbench direction and agent/ProjectFlow split.
- `docs/v3.1-product-improvement-plan.md`: current product target for project profile, file understanding, states, and model role.
- `docs/v3.1-second-round-implementation-report.md`: latest implementation and verification summary.

When reading Chinese docs in PowerShell, use `Get-Content -Encoding UTF8`; default output may display mojibake.

## Backend Shape

Package root: `backend/src/main/java/com/projectflow`.

Layering:

- `controller`: HTTP endpoints and auth extraction.
- `service`: business rules, ownership checks, parsing, model calls, bridge writes.
- `entity`: JPA entities and enums.
- `repository`: JPA repositories.
- `dto`: request/response records.
- `security`: JWT service.
- `support`: shared app errors and converters.

Core services:

- `AuthService`: register/login/me, BCrypt password handling, JWT issue.
- `ProjectService`: project CRUD and ownership.
- `TaskService`: project-scoped tasks and status transitions.
- `DevLogService`: project-scoped development logs.
- `MarkdownImportService`: Markdown preview/confirm/import records.
- `AiProviderService`: user model provider config and test.
- `AiOutputService`: weekly report, project summary, resume bullets, README section outputs.
- `ProjectIntelligenceService`: materials, zip import, project analysis, file analysis, suggestions, memory, snapshots, evolution records, project changes, fact sources, analysis records.
- `ProjectAgentBridgeService`: writes `.projectflow` protocol/context/task briefs, scans agent result files, creates materials/suggestions/changes.
- `WorkSessionScanService`: reads the bound local project path and Git evidence to create work sessions.
- `EvidenceBundleService`: creates or updates one evidence bundle per work session and exposes lifecycle state.
- `EvidenceDraftChangeService`: turns an evidence bundle into an editable structured project change.

Important backend constraints:

- All project-owned resources must be scoped through project ownership.
- Real secrets belong in environment variables or user provider config, not committed files.
- Model output is candidate data; do not silently overwrite confirmed user/project memory content.
- Zip import skips `.git`, dependency/build output, logs, `.env`, binary/media/archive files.
- Evidence bundles are not confirmed project facts; accepted project changes are.
- File model analysis must skip sensitive paths.
- Local-rule fallback should keep pages useful when model API is missing or fails.

## Frontend Shape

Frontend root: `frontend/src`.

Important files:

- `frontend/src/lib/api.ts`: typed API client and response/error handling.
- `frontend/src/lib/auth.ts`: local session handling.
- `frontend/src/lib/project-insights.ts`: frontend project insight helpers.
- `frontend/src/components/AppShell.tsx`: authenticated layout and main navigation.
- `frontend/src/components/AuthPageShell.tsx`, `AuthPanel.tsx`: auth screens.
- `frontend/src/app/globals.css`: global styling.

Current main nav in `AppShell.tsx`:

- `工作台` -> `/dashboard`
- `变更审查` -> `/tasks`
- `项目画像` -> `/project-intelligence`
- `每日回顾` -> `/dev-logs`
- `成果输出` -> `/ai-review`
- `设置` -> `/settings`

Important routes:

- `/login`, `/register`
- `/dashboard`
- `/projects`, `/projects/[projectId]`, `/projects/[projectId]/files`
- `/tasks`
- `/dev-logs`
- `/imports`
- `/project-intelligence`
- `/project-analysis-records/[recordId]`
- `/ai-review`
- `/settings`

Frontend design direction:

- Chinese-first product UI; GitHub/docs can stay English-first.
- Use restrained developer-dashboard visuals, clear density, and useful status indicators.
- Avoid repeated navigation cards on the workbench.
- Empty states must explain what is missing, why it matters, and the next action.
- Do not show fake project profile, fake architecture, or fake model output when prerequisites are missing.

## API Surface

Base path is `/api`. Protected requests use `Authorization: Bearer <token>`.

Core endpoint groups:

- Auth: `/auth/register`, `/auth/login`, `/auth/me`
- Projects: `/projects`
- Tasks: `/projects/{projectId}/tasks`, `/tasks/{taskId}`, `/tasks/{taskId}/status`
- Dev logs: `/projects/{projectId}/dev-logs`, `/dev-logs/{logId}`
- Markdown imports: `/imports/preview`, `/imports/confirm`, `/projects/{projectId}/imports`
- AI providers: `/ai-providers`, `/ai-providers/{providerId}/test`
- AI outputs: `/projects/{projectId}/ai-outputs`, `/ai-outputs/{outputId}`
- Project materials: `/projects/{projectId}/materials`, `/projects/{projectId}/materials/text`, `/projects/{projectId}/materials/file`, `/projects/{projectId}/materials/zip`, `/project-imports/zip`
- Project analysis: `/projects/{projectId}/analysis/run`, `/projects/{projectId}/files/analyze`, `/projects/{projectId}/analysis-records`, `/project-analysis-records/{recordId}`
- Suggestions/changes: `/projects/{projectId}/suggestions`, `/ai-suggestions/{suggestionId}`, `/projects/{projectId}/changes`, `/project-changes/{changeId}`
- Memory/history: `/projects/{projectId}/memory`, `/projects/{projectId}/fact-sources`, `/projects/{projectId}/snapshots`, `/projects/{projectId}/evolution-records`
- Agent bridge: `/projects/{projectId}/agent-bridge/protocol`, `/projects/{projectId}/agent-bridge/scan`, `/projects/{projectId}/agent-bridge/tasks/{taskId}/brief`

Response shape:

```json
{ "data": {}, "message": "OK" }
```

Error shape:

```json
{ "error": { "code": "CODE", "message": "Message", "details": [] } }
```

## Data Concepts

Base V1:

- `User`
- `ProjectSpace`
- `TaskItem`
- `DevLog`
- `ImportRecord`
- `AiOutput`

V2/V3 project intelligence:

- `AiProvider`
- `ProjectMaterial`
- `AiSuggestion`
- `ProjectMemory`
- `ProjectSnapshot`
- `ProjectEvolutionRecord`
- `ProjectChange`
- `ProjectFactSource`
- `ProjectAnalysisRecord`

Ownership rule:

- A user can access task/log/material/suggestion/change/memory/output data only through a project owned by that user.

## Agent Bridge

ProjectFlow can write `.projectflow` files into a real project path:

```text
.projectflow/
  AGENT_PROTOCOL.md
  agent-protocol.md              compatibility pointer
  agent-results/<timestamp-topic>/
    result.json
    summary.md
  templates/
  context/
    project-profile.md
    requirements.md
    confirmed-decisions.md
    known-risks.md
    update-history.md
```

Agent result requirements:

- Read `.projectflow/AGENT_PROTOCOL.md` before substantial work.
- Write structured `result.json` with task goal, actual changes, repository-relative key files, verification, unfinished work, and sediment candidates.
- Use `not_run` for checks that were not run; never present plans as completed capability.
- ProjectFlow imports Agent results as candidate evidence. Users still make the final sediment decision.

## Implementation Habits

- Keep changes narrow and aligned with the current V3.1 direction.
- Prefer existing services, DTO records, repository patterns, and API client types.
- If adding backend behavior, add focused tests in `backend/src/test/java/com/projectflow`.
- If touching frontend routes or API types, verify `npm.cmd run build`.
- If touching backend service/controller/entity behavior, verify targeted tests or full `mvn.cmd -q test`.
- Do not commit local secrets or real `.env`.
- For current docs or API/library behavior, use Context7 first.

## Token-Saving Rule

For large tasks, first read this file, then read only:

1. The latest relevant stage report in `docs/`.
2. The target frontend route/component or backend controller/service.
3. The matching API client types in `frontend/src/lib/api.ts` when frontend/backend contract is involved.
4. The focused tests for the touched backend service or controller.

Avoid rereading all docs and all source files unless the task is cross-cutting or this file is out of date.
