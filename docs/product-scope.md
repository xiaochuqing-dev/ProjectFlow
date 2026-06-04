# Product Scope

## Positioning

ProjectFlow is a personal project management, developer journal, and AI reflection platform for students, solo developers, and AI-assisted development workflows.

The product goal is to turn real project activity into reusable engineering assets: progress records, technical decisions, weekly reports, project summaries, resume bullets, and README material.

## V1 Success Definition

V1 is complete when this workflow runs end to end:

1. A user registers and logs in.
2. The user creates an `InsightWrite 2.0` project space.
3. The user adds project tasks and moves them through the Kanban workflow.
4. The user writes or pastes a structured Markdown development log.
5. ProjectFlow parses the log, previews the result, and saves it.
6. The user generates a weekly report, project summary, and resume bullets.
7. The user copies or downloads Markdown output for GitHub or resume use.

## Target Users

- Students building portfolio projects.
- Solo developers managing side projects.
- Developers using Codex, Claude Code, or similar tools and needing structured dev logs.
- Job seekers who want real project history transformed into concise project material.

## V1 Required Features

| Area | Required |
| --- | --- |
| Authentication | Register, login, logout, JWT, BCrypt password storage, user isolation |
| App Shell | Chinese-first UI, persistent left sidebar, optional page-level top navigation, protected pages |
| Projects | CRUD, status, tech stack tags, repository URL, project detail |
| Tasks | CRUD, priority, due date, tags, Backlog/Todo/In Progress/Review/Done states |
| Dev Logs | Structured sections, project association, list, detail, edit |
| Markdown Import | Paste input, front matter parsing, section parsing, preview, import records |
| AI Outputs | Weekly report, project summary, resume bullets, README section as lower priority |
| Export | Copy Markdown, download `.md` |
| Documentation | README, architecture, API design, data model, dev log format, roadmap |

## Deferred Features

- GitHub OAuth, GitHub App, or personal access token sync.
- Commit, issue, pull request, or README automatic synchronization.
- Multi-member workspaces and collaboration.
- Drag-and-drop Kanban ordering.
- Project scoring.
- PDF or Word export.
- CLI importer.
- Public SaaS deployment.

## Product Principles

- V1 must be useful without GitHub API integration.
- Logs are the source material; AI is an output generator, not the main product.
- AI calls happen only after explicit user action.
- Generated AI outputs are saved to avoid repeated cost.
- The interface should feel like a serious developer tool, not a generic AI chat app.
- The product interface is Chinese-first; GitHub-facing copy remains English-first.
- Every round should leave the project runnable or clearly documented.
