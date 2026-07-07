# ProjectFlow

ProjectFlow V3.3.3 is a local-first tool for AI-assisted solo developers to understand development changes and turn them into confirmed, reusable project sediment.

## V3.3.3 Analysis Progress, Evidence-Aware Modeling, and Chinese Quality

- **Analysis progress visibility**: analyzing new changes shows the current stage (Git scan / GitHub inspect / model enrichment / persist), elapsed time, and input scale. Long model runs tell the user the analysis continues and the page can be left safely.
- **Model-result retention priority**: the quality gate is now a *marker*, not a batch rejector. As long as the model returns a parseable structure, results are retained and tagged with `PASS` / `NEEDS_REVIEW` / `NEEDS_CHINESE_REWRITE` / `NEEDS_EVIDENCE` / `PARTIAL_EVIDENCE` / `LOW_CONFIDENCE`. Local-rule fallback is used only when the model is fully unavailable (not configured / call failed / no content / unparseable JSON / all evidence invalid).
- **Forced Chinese for user-visible content**: titles, summaries, main changes, capability names, README/resume/interview expressions, and fallback summaries must be natural Simplified Chinese. English commit messages, file paths, class names, and interface names may remain only in evidence details.
- **Model configuration precondition**: entries that depend on model quality (**分析新变化**, **分析项目能力**) check whether a model is configured first. If not configured, ProjectFlow shows Git facts with a "facts-only, no model interpretation" notice and guides the user to configure a model rather than fabricating low-quality local-template results.
- **Unified analysis input snapshot**: local Git, worktree diff (unstaged/staged/untracked), GitHub state, Agent results, and scan scope are organized into a structured snapshot fed to the model. The model is told it is judging the *real* development state from multi-source evidence — not choosing between GitHub and local Git. Rules provide facts; the model interprets flexibly; the user confirms.
- **Analysis scope display**: every completed scan shows what sources participated (local Git / worktree diff / staged / untracked / Agent result count / GitHub status / model status / merge mode / uncommitted content / remote-unsynced / evidence gaps), not a vague "model merge failed".
- **GitHub on the home screen**: the workbench shows GitHub status and action entries directly under "GitHub" (not "GitHub 增强"): login guide (copy `gh auth login --web --clipboard`), refresh sync status (read-only, never pull/merge/rebase), re-check. ProjectFlow never reads, displays, or stores GitHub tokens.
- **Capability page quality**: **分析项目能力** requires a configured model and generates Chinese, concrete, product-specific capability cards tied to real ProjectFlow features — no template names like "项目资产沉淀能力", no raw commit-message card names.

It is built for developers who use agents such as Codex, Claude Code, or other coding assistants to modify real projects and then need a clear way to understand what changed, review the evidence, maintain a project profile, and generate reusable output such as daily reviews, README material, reports, and resume-ready summaries.

## V3.3.3 Workflow

ProjectFlow is not a Kanban board, daily-report generator, or hosted PR/CI system. Its primary workflow is:

1. Add a project through zip import and bind its real local folder.
2. Analyze **待整理变更** from the last confirmed review cursor to the current Git HEAD.
3. Group objective Git and Agent-result evidence into human-readable **开发推进段**.
4. Produce evidence-backed **建议沉淀**.
5. Let the user confirm new, merge, evidence-only, or ignore.
6. Preserve confirmed content as traceable **项目沉淀** for README, resume, interview, review, and Agent context reuse.

The governing rule is: rules collect facts, models interpret, rules validate, and users confirm. ProjectFlow no longer uses “今日开发” as the primary boundary; a persistent review cursor covers changes accumulated across days.

Local Git is the primary data source. Agent result files are an enhancement that adds task intent, verification, and unfinished work. GitHub CLI is an optional enhancement for repository metadata and commit links; missing installation, login, or remote access never blocks local Git analysis, and ProjectFlow does not read or store GitHub tokens.

数据源边界：本地 Git 是主数据源；Agent result 是增强数据源；GitHub CLI 是可选增强数据源。V3.3.3 不再以“今日开发”为主边界。

## Core Concepts

| Concept | Meaning |
| --- | --- |
| Project Profile | Current understanding of the project: purpose, architecture, modules, risks, decisions, progress, and output material |
| Project Material | Imported zip, local files, agent result, or historical source material used for analysis |
| Change Batch | New changes between the last confirmed review cursor and the current HEAD |
| Development Segment | A deterministic, evidence-backed grouping of related commits, files, and Agent results |
| Suggested Sediment | A user-reviewed proposal to create, merge, add evidence, or ignore |
| Project Sediment | Confirmed project capability or outcome with sources and developer notes |
| Work Session / Evidence Bundle / Project Change | Compatibility records retained for existing data and old links |
| Project Memory | Confirmed project archive used by daily review, output generation, and `.projectflow/context` sync |
| Fact Source | Field-level trace explaining where a profile field came from |
| Growth Timeline | Long-term history of how the project changed over time |

## Current Features

- JWT authentication with project-scoped ownership checks.
- Project creation, switching, deletion, and local path binding.
- Complete project zip import with architecture/file understanding.
- Cursor-based local Git scanning with safe first-scan and rewritten-history fallbacks.
- Rule-based grouping plus optional model enrichment and evidence validation.
- Project sediment confirmation and focused evidence-backed detail pages.
- Daily review and output generation using confirmed project archive data.
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
+-- start.bat                 simple V3.3.3 Windows entry
+-- start-projectflow.bat     embedded Windows launcher
+-- start-projectflow.ps1     Docker/team startup orchestration
`-- README.md
```

## Local Development

Windows embedded mode (recommended):

```powershell
.\start.bat
```

This starts the H2-backed backend and production frontend, then opens:

```text
http://127.0.0.1:3000/login
```

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
C:\Users\Administrator\Desktop\apache-maven-3.9.9\bin\mvn.cmd -q test
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
