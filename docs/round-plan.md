# Round Plan

Each round should end with:

- Summary of files changed.
- Verification commands and results.
- Suggested commit message.
- Next round focus.

The assistant should not automatically commit until explicitly asked.

## Round 0: Product and Engineering Blueprint

Goal: Freeze the V1 product scope and engineering direction before scaffolding code.

Deliverables:

- `README.md`
- `docs/product-scope.md`
- `docs/ui-design-direction.md`
- `docs/architecture.md`
- `docs/api-design.md`
- `docs/data-model.md`
- `docs/dev-log-format.md`
- `.env.example`
- `.gitignore`

Verification:

```powershell
rg --files
git status --short
```

Suggested commit:

```text
docs: define ProjectFlow V1 blueprint
```

## Round 1: Engineering Skeleton

Goal: Create a runnable full-stack skeleton.

Deliverables:

- `frontend/` Next.js + TypeScript + Tailwind project.
- `backend/` Spring Boot 3 + Java 17 + Maven project.
- `docker-compose.yml` with PostgreSQL and Redis.
- Backend `GET /api/health`.
- Frontend health check page.

Verification:

```powershell
cd frontend
npm.cmd run build
cd ..\backend
mvn.cmd -q -DskipTests compile
cd ..
docker compose config
```

Suggested commit:

```text
chore: initialize full-stack skeleton
```

## Round 2: Authentication and App Shell

Goal: Add the authenticated user foundation and persistent app layout.

Deliverables:

- Register and login endpoints.
- JWT access token creation and verification.
- BCrypt password hashing.
- `users` table.
- Auth pages.
- Persistent left sidebar.
- Protected app routes.

Verification:

```powershell
cd backend
mvn.cmd test
cd ..\frontend
npm.cmd run build
```

Suggested commit:

```text
feat: add authentication and app shell
```

## Round 3: Project Spaces

Goal: Let users create and manage project spaces.

Deliverables:

- `projects` table.
- Project CRUD APIs.
- Project list page.
- Project detail page.
- Ownership checks.

Verification:

```powershell
cd backend
mvn.cmd test
cd ..\frontend
npm.cmd run build
```

Suggested commit:

```text
feat: add project spaces
```

## Round 4: Kanban Tasks

Goal: Show project progress through task states.

Deliverables:

- `tasks` table.
- Task CRUD APIs.
- Status transition endpoint.
- Kanban board UI.
- Priority and status filters.

Verification:

```powershell
cd backend
mvn.cmd test
cd ..\frontend
npm.cmd run build
```

Suggested commit:

```text
feat: add task board workflow
```

## Round 5: Dev Logs

Goal: Record structured development activity.

Deliverables:

- `dev_logs` table.
- Log create, edit, list, and detail APIs.
- Log timeline page.
- Structured log detail page.

Verification:

```powershell
cd backend
mvn.cmd test
cd ..\frontend
npm.cmd run build
```

Suggested commit:

```text
feat: add structured dev logs
```

## Round 6: Markdown Import

Goal: Import AI-generated Markdown logs into structured dev logs.

Deliverables:

- Markdown parser.
- Preview endpoint.
- Confirm endpoint.
- Import records.
- Paste import UI.

Verification:

```powershell
cd backend
mvn.cmd test
cd ..\frontend
npm.cmd run build
```

Suggested commit:

```text
feat: add markdown dev log import
```

## Round 7: AI Reflection and Export

Goal: Generate portfolio-ready materials from real project history.

Deliverables:

- AI provider interface.
- Mock provider.
- AI output APIs.
- Weekly report, project summary, resume bullets.
- Markdown copy and `.md` download.

Verification:

```powershell
cd backend
mvn.cmd test
cd ..\frontend
npm.cmd run build
```

Suggested commit:

```text
feat: add ai reflection outputs
```

## Round 8: Redis and Async Workflow

Goal: Add engineering depth around task state, caching, and limits.

Deliverables:

- Redis-backed AI task state.
- Project statistics cache.
- Basic generation rate limit.
- Actuator health checks.
- Unified error response hardening.

Verification:

```powershell
cd backend
mvn.cmd test
cd ..
docker compose config
```

Suggested commit:

```text
feat: add redis-backed workflow state
```

## Round 9: Portfolio Packaging

Goal: Prepare the project for GitHub and resume presentation.

Deliverables:

- Final README.
- Screenshots.
- Architecture diagram polish.
- Demo data.
- V1 acceptance checklist.
- Roadmap update.

Verification:

```powershell
cd frontend
npm.cmd run build
cd ..\backend
mvn.cmd test
cd ..
git status --short
```

Suggested commit:

```text
docs: prepare portfolio release
```
