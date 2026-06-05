# V1 Acceptance Checklist

ProjectFlow V1 is considered complete when the local workflow below is available and verified.

## Product Flow

- [x] User can register, login, logout, and keep project data isolated by account.
- [x] User can create project spaces with description, status, tech stack, repo URL, and dates.
- [x] User can create project tasks and move them through Backlog, Todo, In Progress, Review, and Done.
- [x] User can create structured development logs with category, date, time spent, tags, and risk marker.
- [x] User can paste Markdown, preview parsed fields, and confirm it as a development log.
- [x] User can generate saved Markdown outputs for weekly report, project summary, resume bullets, and README section.
- [x] User can copy or download generated Markdown.

## Engineering Flow

- [x] Frontend uses Next.js, TypeScript, Tailwind CSS, persistent sidebar, and protected routes.
- [x] Backend uses Spring Boot, JPA repositories, service-layer ownership checks, and unified API responses.
- [x] PostgreSQL and Redis are defined in Docker Compose for local development.
- [x] Startup script runs the local app from one console window.
- [x] Backend controller tests cover auth, projects, tasks, dev logs, imports, and AI outputs.
- [x] Frontend production build includes dashboard, projects, tasks, dev logs, imports, and AI review pages.

## Remaining Portfolio Polish

- [ ] Add final screenshots after the UI stabilizes.
- [ ] Add demo data script if a clean portfolio walkthrough needs repeatable sample content.
- [ ] Replace Mock AI provider with a real provider only after API key handling is finalized.
