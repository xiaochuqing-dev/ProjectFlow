# ProjectFlow

ProjectFlow is a local-first developer workbench for turning real project activity into confirmed engineering assets.

It is built for developers who use agents such as Codex, Claude Code, or other coding assistants to modify real projects and then need a clear way to understand what changed, review the evidence, maintain a project profile, and generate reusable output such as daily reviews, README material, reports, and resume-ready summaries.

## What ProjectFlow Does

ProjectFlow is not just a Kanban board. The current V3.2 product loop is:

1. Import a project zip.
2. See the first project profile and architecture snapshot.
3. Bind the real local project folder.
4. Develop normally in the real project.
5. Refresh Git / agent evidence after work.
6. Review structured project changes.
7. Accept useful facts into the project archive.
8. Generate daily reviews, README sections, reports, and portfolio material from confirmed sources.

The key rule is simple: evidence is not automatically treated as truth. Evidence becomes a project asset only after review and acceptance.

## Core Concepts

| Concept | Meaning |
| --- | --- |
| Project Profile | Current understanding of the project: purpose, architecture, modules, risks, decisions, progress, and output material |
| Project Material | Imported zip, local files, agent result, or historical source material used for analysis |
| Work Session | A detected slice of development activity, usually from local Git evidence |
| Evidence Bundle | Objective evidence for a work session, such as changed files, line counts, commit/worktree context, and agent claims |
| Project Change | Editable review object generated from evidence; accepted changes update the project archive |
| Project Memory | Confirmed project archive used by daily review, output generation, and `.projectflow/context` sync |
| Fact Source | Field-level trace explaining where a profile field came from |
| Growth Timeline | Long-term history of how the project changed over time |

## Current Features

- JWT authentication with project-scoped ownership checks.
- Project creation, switching, deletion, and local path binding.
- Complete project zip import with architecture/file understanding.
- Local Git scanning from the bound project path.
- Evidence Bundle lifecycle: create, update, convert to candidate change, review, accept, or ignore.
- Project profile and project archive pages with source traceability.
- Daily review and output generation using confirmed project archive data.
- `.projectflow` protocol/context sync for agent collaboration.
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
+-- start-projectflow.bat     embedded Windows launcher
+-- start-projectflow.ps1     Docker/team startup orchestration
`-- README.md
```

## Local Development

Windows embedded mode:

```powershell
.\start-projectflow.bat
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
