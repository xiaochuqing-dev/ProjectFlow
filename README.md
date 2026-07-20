# ProjectFlow

ProjectFlow V3.4.3 is a local-first project memory system for AI-assisted solo developers. It reads real Git, worktree, and Agent evidence, automatically organizes development facts, maintains a traceable timeline and capability map, and exposes the same bounded project-memory semantics to local agents through a read-only Gateway and MCP server.

ProjectFlow 自动维护项目从创建至今的长期记忆。让每一个项目记得自己是怎样成长到今天的。

Git and GitHub retain commits, diffs, files, and branches. ProjectFlow turns that raw development evidence into durable Change Batches, evidence-backed Project Facts, and long-lived Project Memory.

## V3.4.3 Project Memory Gateway and Hermes MCP

- Project Memory Gateway is the shared read-only semantic layer for Snapshot, occurrence-time Recent Changes, cross-layer Search, Timeline, Capabilities, chronological Evolution, Fact Trace and a budgeted Project Brief.
- ProjectFact remains the only factual source. Timeline, Capability and Evolution results are explicitly derived and keep stable entity IDs and evidence links.
- Recent/history reads use when work actually occurred, not when it was analyzed, recorded or synchronized. Failed derived refreshes retain deterministic facts and the last successful view.
- Every endpoint is project-owned, compact by default, paged/bounded, model-free on GET and safely audited without storing full private queries.
- `integrations/hermes/projectflow_mcp.py` exposes nine narrow, idempotent, non-destructive stdio tools. It accepts only a loopback ProjectFlow backend in this release and contains no generic REST or write tool.
- Hermes is a consumer, never a fact source. Obsidian formal projection remains V3.4.4 and will reuse the same Gateway semantics.

## V3.4.2 Fact-native Lifecycle Capability Map

- `ProjectFact` remains the factual source of truth. Timeline organizes facts over time; Capability Map explains the stable capabilities proved by the complete fact history.
- `ProjectCapability` is a long-lived entity with a stable identity, aliases, deterministic maturity, non-destructive merge history, and traceable versions. It is not a per-run card.
- New facts automatically create, enhance, or add evidence to capabilities. Routine changes are applied without confirmation; every input fact is classified as capability evidence, no capability change, or attention.
- Every capability and evolution traces to facts, batches, commits, files, Agent results, and evidence. Timeline Theme remains period-local and is never promoted into a capability by itself.
- Full-history bootstrap uses bounded chunks and explicit coverage. Incremental refresh processes only uncovered or changed facts and reuses the V3.3.7 durable job and V3.3.8 model gateway boundaries.
- Stable identity uses project, problem, product area, and canonical meaning rather than name alone. Merges preserve source entities, relations, evolutions, aliases, and redirects; unsafe merges become attention.
- Maturity is determined by explainable fact, batch, commit, evidence, evolution, time-span, and attention rules. Model-provided maturity scores are rejected.
- Failed refreshes preserve the previous successful map. GET requests never trigger model calls, and history backfill does not rebuild the whole map after every chunk.
- Legacy `ProjectCapabilityCard` data remains readable for compatibility. Only traceable confirmed cards may seed a long-lived capability; candidate and ignored cards do not enter the main chain.
- Hermes and Obsidian formal integration remains the next phase and will consume Facts, Timeline, and Capabilities rather than becoming new fact sources.

## V3.4.1 Automatic Project Timeline

- “Analyze new changes” keeps the proven Git/worktree/Agent evidence and model-segmentation pipeline, then automatically ingests valid Development Segments as Project Facts.
- Normal evidence-backed facts are recorded immediately. Users no longer process every item through create, merge, evidence-only, or ignore decisions.
- Evidence conflicts, missing evidence, incomplete fact boundaries, and unsafe duplicates become `NEEDS_ATTENTION`; they do not block the rest of the batch or the next incremental scan.
- `ProjectFactCursor` advances only after fact ingestion succeeds. It is independent from the legacy manual `ProjectReviewCursor`.
- Existing Development Segments and confirmed Project Sediments migrate idempotently without deleting old batches, changes, sediments, links, or H2 data.
- Uncovered Git history is rebuilt oldest-first in bounded background chunks with persistent coverage and checkpoints. Already covered commits are not sent to the model again.
- Project Records is the batch-oriented fact browser. Project Memory uses Project Facts as its primary factual layer and keeps V3.3.x sediment/profile fields in a compatibility area.
- `ProjectFact` remains the factual source of truth. Timeline is a temporal read model and derived layer over those immutable facts.
- Day view exposes facts directly. Week and month receive automatic derived summaries; lifecycle uses hierarchical month synthesis with explicit full-fact coverage.
- Timeline summaries and period-local Timeline Themes never contain next-step planning, never mutate facts, and never require users to save or confirm them.
- Every theme traces back to its facts, source batch, and evidence. Unknown IDs, missing coverage, and cross-project references invalidate a generated summary.
- Failed refreshes preserve the previous successful summary while deterministic fact statistics remain available. History backfill expands older periods automatically without regenerating all summaries after every chunk.
- Timeline Theme is not Project Capability. Lifecycle capability maps, Hermes sync, and Obsidian sync remain later phases.
- V3.4.1 reuses the V3.3.7 job infrastructure and V3.3.8 model gateway rather than redesigning them.

## V3.3.8.1 Data Read Reliability

- The sediment processing center tolerates legacy nullable batch, change, and segment fields; one incomplete historical row no longer makes the full list fail.
- Batch review uses fixed bulk queries for batches, formal changes, and development segments instead of per-batch reads.
- Completed analysis results remain database facts. Project-scoped sessionStorage snapshots are only an immediate-render cache and cannot replace persisted state.
- `GET /api/projects/{projectId}/dashboard-bootstrap` restores the first-screen project, memory, latest scan job, batch, segments, pending-review count, project-analysis summary, and Provider availability from persisted data only.
- Returning to the workbench renders the project snapshot immediately; F5 without a snapshot uses the lightweight bootstrap. Secondary GitHub/history/material/output failures preserve the core result and show a local retry notice.
- This patch does not change the V3.3.7 job model or the V3.3.8 model gateway, parameter, reasoning, JSON recovery, or real-Provider paths.

## V3.3.8 Real Model Reliability

- Six real model entrypoints are registered explicitly: Provider connection test, development-segment merge, whole-project analysis, file analysis, capability interpretation, and project-capability analysis. Business services use the same gateway and task definition.
- Provider/model capability profiles decide whether Temperature and JSON mode are sent, identify reasoning models, expose safe reasoning-field metadata, and keep unknown OpenAI-compatible services on a conservative standard profile.
- Temperature no longer has a global 0.3 ceiling. Diagnostics separate configured, task-recommended, effective, sent/omitted, and decision reason.
- Max Tokens is calculated from task type, input size, expected structure, reasoning behavior, and Provider capability. Complex tasks no longer share a fixed 4000 budget, and recovery no longer falls back to a fixed 2000 budget.
- Balanced multi-candidate JSON scanning, target-aware collection discovery, common wrapper/snake_case aliases, trailing-comma repair, and partial-array recovery reduce avoidable format loss.
- Legal JSON with the wrong business shape receives one targeted Schema repair retry. Truncation and reasoning-exhausted empty content use separate recovery types and budgets. Transport retry remains bounded.
- Diagnostics include entrypoint, capability profile, parameter decisions, retry type, Schema match, reasoning-budget signal, usage, latency, and failure stage without storing keys, Authorization, prompts, raw responses, or reasoning text.
- The Settings page now describes Provider Max Tokens as a capability ceiling and explains that unsupported parameters are omitted.
- Local real DeepSeek acceptance used the configured Provider through actual application APIs. ProjectFlow self-analysis covered 30 commits, 148 files, 15 Agent results, and a 10,148-token dynamic output budget; eight model segments were retained.

## V3.3.7 Real Acceptance and Reliable Jobs

- Analysis jobs now persist queue, heartbeat, cancellation, retry, interruption, budget, token, idempotency, fingerprint, failure-code, and restart-recovery state. Duplicate active requests return the same job ID.
- Retry no longer has a force path that bypasses active-job uniqueness. A retry may ignore completed history, but it always reuses an equivalent `QUEUED`, `RUNNING`, or `CANCEL_REQUESTED` job and records `retriedFromJobId` / `retryReason` when a new retry job is created.
- Users can cancel queued or running change and capability analysis. Safe checkpoints stop later model calls and formal writes; old successful results and confirmed content remain unchanged.
- A bounded executor, bounded model-request semaphore, global active limit, queue rejection state, three-request task budget, 10-minute duration budget, and 60,000-token budget prevent uncontrolled resource use.
- Service restart requeues untouched queued jobs, marks pre-model interruptions retryable, and never automatically replays a model request whose billing state is unknown.
- CI blocks regressions through backend/H2 tests, PostgreSQL Testcontainers, TypeScript and production build, Playwright browser E2E, and a basic committed-secret scan. Real DeepSeek validation is optional and reports `SKIPPED` without a safe key.
- Finalization acceptance includes 174 backend/H2 tests, 2 PostgreSQL 16 workflow tests, 18 frontend contract tests, and 4 Playwright flows covering analysis batches, sediment confirmation, capability analysis failure preservation, cancellation, refresh, navigation, and retry idempotency.

## V3.3.6 Batch Sediment Review and Capability Closure

- The workbench now shows the latest analysis batch summary instead of expanding every suggestion. The sediment processing center groups batches by time and opens one formal suggestion at a time with progress, previous/next, skip, defer, and confirmation feedback.
- Model results, partially recovered model items, local fact drafts, Agent-result drafts, and legacy records have explicit source and quality labels. Local fact drafts never automatically become formal sediment suggestions.
- Recommendations have high, medium, reference-only, or not-recommended strength. Strong visual recommendations require model output, sufficient evidence, and more than title similarity.
- Confirmed sediments persist affected files and source batch IDs, immediately enter `PENDING_ANALYSIS`, and show their last capability-analysis job. The capability analysis input is now confirmed project sediment rather than raw development segments.
- Capability analysis records the input sediment snapshot, marks sediments only after successful persistence, preserves the previous successful cards on failure, and shows new, updated, and pending sediment counts.
- Empty content combined with `finish_reason=length`, exhausted completion tokens, or a reasoning field is classified as exhausted output and triggers one lower-budget compact retry. Reasoning text is never stored or returned; only presence and length are diagnosed.
- Git, file, Agent-result, project-analysis, file-analysis, capability-interpretation, and capability-card model waits no longer run inside method-level database transactions.

## V3.3.5 Reliable Model Results, Clear Confirmation, and Provider Management

- Model calls now retain finish reason, real token usage, effective Max Tokens and Temperature, timeout, latency, Provider/model, JSON repair, truncation, and compact-retry diagnostics without exposing API keys or raw responses.
- Length-limited output is handled separately from malformed JSON. ProjectFlow performs one compact retry and can retain complete items recovered from a truncated root array instead of discarding the entire batch.
- Provider configuration, task recommendations, capability limits, and final effective request values are shown separately. V3.3.8 supersedes the former global Temperature 0.3 and fixed recovery-budget rules.
- Normalized title, summary, main-change, user-value, and capability text is stored in full. List cards use CSS previews; detail pages show the complete stored content. Legacy ellipsis-ended records are marked and offer re-analysis because lost text cannot be reconstructed.
- Sediment confirmation starts from a recommended action and reason, shows the target summary and expected field/evidence/file changes before confirmation, and returns the exact write result plus a direct sediment link afterward.
- Capability cards retain their analysis job ID. The capability page separates the current successful batch, the latest failed attempt, and history; a failed re-analysis never replaces the previous successful candidates or confirmed cards.
- Provider settings support create, test, edit without re-showing the key, explicit key clearing, unique default selection, protected deletion, and user-confirmed cleanup of historical duplicates. Only the explicitly selected default Provider is used by new model tasks.

## V3.3.4 GitHub Onboarding, Human-Readable Failure Notices, and Durable Capability Analysis

- **Human-readable model failure notices**: the vague "模型归并失败，已使用增强本地摘要" is gone. Failure reasons are split into plain Chinese: model not configured / DeepSeek call failed / invalid response format / invalid evidence reference, and the result source is always stated as "本地事实摘要". The old "增强本地摘要" wording is removed everywhere.
- **Local-fact summary is Chinese too**: local fallback titles and summaries no longer echo raw English commit messages. Common commit actions and keywords are rewritten to Chinese; unreliable English titles become "根据提交记录整理的变更" with the original text kept in evidence details only.
- **GitHub access moved to the project-access area**: local path, model, and GitHub are shown together as "项目接入状态". GitHub is no longer buried only inside the pending-changes card.
- **Small-developer-friendly GitHub login wizard**: when not logged in, the UI offers "打开登录终端" (opens a terminal running the fixed whitelisted command `gh auth login --web --clipboard`), "复制登录命令", and "重新检查". When not installed, it offers "查看安装说明" and "重新检查". The backend only ever runs the fixed command - never arbitrary input - and never reads, displays, or stores GitHub tokens.
- **Clear GitHub refresh scope**: refresh sync status reads remote commit info only and never modifies local code (no pull/merge/rebase); the UI states this explicitly, and connection failures suggest a proxy.
- **No raw internal enums in the analysis scope**: `CALL_FAILED` / `LOCAL_RULE` / `CONNECTED` / `local_ahead` and similar enums are translated to plain Chinese via a shared `status-labels.ts` mapping before reaching the user.
- **Fixed evidence-gap judgment**: `evidenceGap` is no longer forced to `true` just because GitHub did not participate. It is based on real conditions (only Agent results without code / code changes without explanation / remote ahead unsynced / diverged / uncommitted-only without explanation) and now carries an `evidenceGapReason`.
- **Durable async capability analysis**: "分析项目能力" is now a recoverable async job (`CAPABILITY_CARD_ANALYSIS`) with stages (LOAD_EVIDENCE / MODEL_CAPABILITY_ANALYSIS / PERSIST_CAPABILITY_CARDS / SUCCEEDED / FAILED), elapsed time, and input scale. Refreshing or leaving the page no longer loses the task; on return the running job resumes and completed cards reload. Re-analysis replaces only unconfirmed candidates; confirmed capabilities are preserved.

## V3.3.3 Analysis Progress, Evidence-Aware Modeling, and Chinese Quality

- **Analysis progress visibility**: analyzing new changes shows the current stage (Git scan / GitHub inspect / model enrichment / persist), elapsed time, and input scale. Long model runs tell the user the analysis continues and the page can be left safely.
- **Model-result retention priority**: the quality gate is now a *marker*, not a batch rejector. As long as the model returns a parseable structure, results are retained and tagged with `PASS` / `NEEDS_REVIEW` / `NEEDS_CHINESE_REWRITE` / `NEEDS_EVIDENCE` / `PARTIAL_EVIDENCE` / `LOW_CONFIDENCE`. Local-rule fallback is used only when the model is fully unavailable (not configured / call failed / no content / unparseable JSON / all evidence invalid).
- **Forced Chinese for user-visible content**: titles, summaries, main changes, capability names, README/resume/interview expressions, and fallback summaries must be natural Simplified Chinese. English commit messages, file paths, class names, and interface names may remain only in evidence details.
- **Model configuration precondition**: entries that depend on model quality (**分析新变化**, **分析项目能力**) check whether a model is configured first. If not configured, ProjectFlow shows Git facts with a "facts-only, no model interpretation" notice and guides the user to configure a model rather than fabricating low-quality local-template results.
- **Unified analysis input snapshot**: local Git, worktree diff (unstaged/staged/untracked), GitHub state, Agent results, and scan scope are organized into a structured snapshot fed to the model. The model judges the *real* development state from multi-source evidence rather than choosing between GitHub and local Git. V3.4.0 now records validated objective facts automatically while preserving subjective user decisions outside the fact layer.
- **Analysis scope display**: every completed scan shows what sources participated (local Git / worktree diff / staged / untracked / Agent result count / GitHub status / model status / merge mode / uncommitted content / remote-unsynced / evidence gaps), not a vague "model merge failed".
- **GitHub on the home screen**: the workbench shows GitHub status and action entries directly under "GitHub" (not "GitHub 增强"): login guide (copy `gh auth login --web --clipboard`), refresh sync status (read-only, never pull/merge/rebase), re-check. ProjectFlow never reads, displays, or stores GitHub tokens.
- **Capability page quality**: **分析项目能力** requires a configured model and generates Chinese, concrete, product-specific capability cards tied to real ProjectFlow features — no template names like "项目资产沉淀能力", no raw commit-message card names.

It is built for developers who use agents such as Codex, Claude Code, or other coding assistants to modify real projects and then need a clear way to understand what changed, review the evidence, maintain a project profile, and generate reusable output such as daily reviews, README material, reports, and resume-ready summaries.

## V3.4.2 Workflow

ProjectFlow is not a Kanban board, daily-report generator, or hosted PR/CI system. Its primary workflow is:

1. Bind a real local Git project.
2. Analyze new changes.
3. Automatically record evidence-backed Project Facts.
4. Rebuild uncovered Git history.
5. Organize facts into Day / Week / Month / Lifecycle Timeline views.
6. Automatically initialize and maintain the lifecycle Capability Map.
7. Trace capabilities and evolution to facts and evidence.
8. Let later external integrations consume Facts, Timeline, and Capabilities.

The governing rule is: rules collect evidence, models interpret it, rules validate the result, and ProjectFlow automatically records objective facts. Users remain responsible for subjective profile edits and exceptional attention items, not routine fact confirmation.

Local Git is the primary data source. Agent result files are an enhancement that adds task intent, verification, and unfinished work. GitHub CLI is an optional enhancement for repository metadata and commit links; missing installation, login, or remote access never blocks local Git analysis, and ProjectFlow does not read or store GitHub tokens.

数据源边界：本地 Git 是主数据源；Agent result 补充任务意图与验证；GitHub CLI 是只读可选增强。数据库中的批次、事实和游标是长期记忆来源，页面缓存只负责加速显示。

## Core Concepts

| Concept | Meaning |
| --- | --- |
| Project Fact | Evidence-backed, durable record of something that actually happened in the project |
| Change Batch | One analyzed range of incremental or historical development changes |
| Development Segment | Analysis-layer semantic grouping produced from raw Git, worktree, and Agent evidence |
| Project Records | Batch-oriented view of automatically recorded Project Facts |
| Project Memory | Long-lived collection of Project Facts and their source batches |
| Needs Attention | Exceptional fact-quality issue that may need human review but never blocks the main flow |
| Fact Cursor | Latest incrementally analyzed commit successfully recorded into Project Facts |
| History Backfill | Bounded background reconstruction of uncovered Git history, oldest first |
| Timeline Period | Deterministic day, ISO week, month, or lifecycle range assigned from a fact's occurrence time |
| Timeline Summary | Automatically generated, replaceable derived summary that covers every fact in its period |
| Timeline Theme | Period-local grouping whose membership traces to Project Facts; it is not a Project Capability |
| Derived Summary | Model-generated content that can be rebuilt without changing source facts or deterministic statistics |
| Project Capability | Stable long-lived capability proved by Project Facts, with a system-owned identity and current version |
| Capability Evolution | Immutable NEW, ENHANCE, ADD_EVIDENCE, MERGE, or correction event bound to source facts |
| Capability Fact Relation | Queryable link from a capability and evolution to the exact supporting Project Fact |
| Capability Maturity | Deterministic, explainable stage derived from evidence breadth, history, evolution, and attention |
| Capability Map State | Durable coverage, dirty fingerprint, latest successful result, and failure-preservation state |
| Capability Change | Recent evolution read model; it describes happened capability change, never future planning |
| Capability Merge | Non-destructive redirect that retains source capability history, relations, and aliases |
| Project Profile / legacy ProjectMemory | Compatibility archive for subjective fields and historical profile content |
| Suggested Sediment / Project Sediment / Project Change | V3.3.x compatibility records retained for old data and links; not the new scan path |

Legacy capability concepts retained only for compatibility are `ProjectCapabilityCard`, `CapabilityCardStatus`, and manual capability confirmation.

## Current Features

- JWT authentication with project-scoped ownership checks.
- Project creation, switching, deletion, and local path binding.
- Complete project zip import with architecture/file understanding.
- Fact-cursor-based local Git scanning with safe first-scan and rewritten-history fallbacks.
- Rule-based grouping plus optional model enrichment, evidence validation, and automatic Project Fact ingestion.
- Batch-oriented Project Records, fact detail evidence, Needs Attention isolation, and bounded history backfill.
- Daily review and output generation with legacy compatibility while later fact-native consumers are developed.
- `.projectflow/AGENT_PROTOCOL.md`, structured Agent results, context sync, and health checks.
- Optional GitHub CLI status and remote-link enrichment with local-only fallback.
- AI provider configuration with local-template fallback when a model is unavailable.
- Embedded Windows startup mode using H2 local data.
- Docker/team startup mode using PostgreSQL and Redis.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | Next.js App Router, TypeScript, React, Tailwind CSS, lucide-react |
| Backend | Spring Boot 3.5.x, Java 17, Maven |
| Embedded Database | H2 under `.projectflow/local-data/` |
| Docker Mode | PostgreSQL and Redis |
| AI Provider | Local template fallback plus OpenAI-compatible provider configuration |
| Local Integration | Bound project folder, Git evidence, `.projectflow` context files |

## Repository Structure

```text
ProjectFlow/
+-- backend/                  Spring Boot API
+-- frontend/                 Next.js app
+-- docs/                     plans, architecture notes, optimization reports
+-- .projectflow/             local agent protocol/context/runtime data
+-- docker-compose.yml        PostgreSQL and Redis mode
+-- .env.example              repo-safe environment template
+-- Start-ProjectFlow.bat     portable Windows quick launcher
+-- start.bat                 compatibility launcher
+-- start-projectflow.ps1     Docker/team startup orchestration
`-- README.md
```

## Local Development

Windows embedded mode (recommended):

```powershell
.\Start-ProjectFlow.bat
```

On Windows, users can also double-click `Start-ProjectFlow.bat` in the repository root. It uses only paths relative to the cloned project, runs `npm ci` on the first launch or after `package-lock.json` changes, rebuilds the production frontend every time, starts the H2-backed backend, and opens:

```text
http://127.0.0.1:3000/login
```

The repository launcher does not pull, merge, or modify Git history. The desktop wrapper fast-forwards `origin/master` only for a clean worktree and otherwise preserves local changes. Both rebuild the selected working tree; the last successful version, source revision, local-change flag, dependency state, frontend Build ID, and ready time are written to `logs/last-embedded-build.json`. The older `start.bat` name remains compatible.

Docker/team mode:

```powershell
.\start-projectflow.ps1
```

or double-click:

```text
start-projectflow-docker.bat
```

Manual verification:

```powershell
cd frontend
npm.cmd run build

cd ..\backend
mvn.cmd test

# PostgreSQL Testcontainers（需要 Docker；CI 中为阻断门禁）
cd backend
mvn.cmd -Ppostgres-it verify

# 浏览器端真实前后端流程
cd ..\frontend
$env:MAVEN_CMD=(Get-Command mvn.cmd).Source
npm.cmd run test:e2e

# 前端契约、类型和生产构建
npm.cmd run test:contracts
npm.cmd run lint
npm.cmd run build

# 可选真实 DeepSeek，小输入且最多 3 次请求
cd ..\backend
$env:PROJECTFLOW_RUN_REAL_MODEL='true'
$env:DEEPSEEK_API_KEY='<安全测试 Key>'
mvn.cmd -Ppostgres-it -Dit.test=RealDeepSeekIT verify
```

Embedded local data can be exported with:

```powershell
.\export-embedded-data.ps1
```

## Main API Areas

All protected APIs use:

```http
Authorization: Bearer <token>
```

Important endpoint groups:

- `/api/project-imports/zip`
- `/api/projects/{projectId}/memory`
- `/api/projects/{projectId}/materials`
- `/api/projects/{projectId}/analysis/run`
- `/api/projects/{projectId}/files/analyze`
- `/api/projects/{projectId}/scan`
- `/api/projects/{projectId}/work-sessions`
- `/api/work-sessions/{sessionId}/evidence-bundles`
- `/api/evidence-bundles/{bundleId}/draft-changes`
- `/api/projects/{projectId}/changes`
- `/api/project-changes/{changeId}`
- `/api/projects/{projectId}/sediments`
- `/api/project-changes/{changeId}/confirm`
- `/api/projects/{projectId}/capabilities/analyze`
- `/api/projects/{projectId}/capability-cards`
- `/api/projects/{projectId}/agent-bridge/health`
- `/api/projects/{projectId}/github/status`
- `/api/projects/{projectId}/fact-sources`
- `/api/projects/{projectId}/context/sync`
- `/api/projects/{projectId}/ai-outputs`

See [docs/api-design.md](docs/api-design.md) for the detailed API shape.

## Product Direction

ProjectFlow should stay focused on project understanding and developer asset management.

It should not drift into a generic admin dashboard, generic Kanban system, or document-only note app. The competitive value is the evidence-to-growth loop: real project changes become reviewed, traceable, reusable project knowledge.

## Documentation

- [Architecture](docs/architecture.md)
- [API design](docs/api-design.md)
- [Data model](docs/data-model.md)
- [Final optimization plan](docs/projectflow-final-frontend-backend-optimization-plan-2026-06-22.md)
- [Evidence to growth plan](docs/v3.2-evidence-to-project-growth-draft-plan-2026-06-20.md)

## GitHub Topics

```text
nextjs
typescript
spring-boot
developer-tools
ai
project-management
git
markdown
portfolio
local-first
```
