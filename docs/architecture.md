# Architecture

## System Overview

ProjectFlow uses a separated frontend and backend architecture. The current V3.3.7 product spine is a developer workbench that turns real project activity into confirmed project sediment and then into traceable capability analysis.

## V3.3.7 Job Execution Boundary

HTTP creation requests only validate ownership, lock the project row, reuse or persist a job, and submit its ID to the bounded executor. External Git, GitHub and model waits run without method-level database transactions. Each stage reloads cancellation and budget state; formal results are persisted only after a final checkpoint. Queue saturation becomes REJECTED rather than a model failure.

The restart listener requeues untouched QUEUED jobs. RUNNING work before model dispatch becomes RETRYABLE; work at or beyond a model stage becomes INTERRUPTED because automatic replay could duplicate billing. Completed and confirmed records are never deleted by cancellation or recovery.

## V3.3.6 Batch Review and Transaction Boundaries

`ChangeBatch` is the sediment-processing batch. The batch list aggregates formal suggestions, local drafts, processed items, pending items, model status, and time group without expanding every item. The batch detail loads one formal suggestion at a time; local fact drafts remain a separate read-only queue until a new model analysis or explicit manual organization.

Confirmed `ProjectSediment` records source batch IDs, affected files, source/quality labels, and capability-analysis state. Capability analysis snapshots sediment IDs, performs the model call outside a transaction, atomically replaces only unconfirmed cards, and marks sediments analyzed only after successful persistence.

Git commands, GitHub inspection, Agent-result file scans, project/file model analysis, capability interpretation, and capability-card model calls run outside method-level database transactions. Repository reads and writes remain short transactions; progress stages use independent transactions.

## V3.3.5 Reliability Flow

Structured model calls pass through one gateway that records transport success, content presence, finish reason, usage, effective parameters, timeout, latency, truncation, JSON repair, and compact retry. Suspected truncation receives one smaller retry; if a truncated root array contains complete objects, only those complete objects may continue with a warning. Development-segment evidence and capability evidence are restored by the backend from S-number sources.

Display sanitization removes unsafe/noisy evidence markers but does not shorten persisted content. List pages use CSS preview clamps, while change, sediment, capability, and evidence details render the complete normalized text. Legacy ellipsis-ended content is treated as unrecoverable source loss and is marked for re-analysis.

Capability analysis uses the existing durable job as its batch record. Cards store `analysis_job_id`; failed jobs keep diagnostics and an acknowledgement flag, while successful replacement remains atomic and never deletes confirmed cards. Provider selection is explicit: only the unique default Provider is eligible for new model tasks.

```mermaid
flowchart LR
    User[User] --> Frontend[Next.js Frontend]
    Frontend --> Backend[Spring Boot API]
    Backend --> Postgres[(PostgreSQL)]
    Backend --> Redis[(Redis)]
    Backend --> AI[AI Provider]
    Frontend --> Export[Markdown Copy or Download]
    Backend --> Git[Local Git Evidence]
    Backend --> PF[.projectflow Context]
```

## Components

| Component | Responsibility |
| --- | --- |
| Next.js frontend | App shell, dashboard flow state, project profile, change review, daily review, output display |
| Spring Boot backend | Auth, ownership checks, import analysis, work-session scanning, evidence lifecycle, AI orchestration, persistence |
| PostgreSQL | Source of truth for users, projects, tasks, logs, materials, changes, memory, evidence bundles, AI outputs |
| Redis | AI task state, project statistics cache, rate limits, future import locks |
| AI provider | Mock provider first; later DeepSeek or OpenAI-compatible provider |

## Frontend Architecture

The frontend uses Next.js App Router with a `src/` directory.

Current route groups:

```text
frontend/src/app/
+-- login/
+-- register/
+-- dashboard/
+-- projects/
+-- projects/[projectId]/
+-- projects/[projectId]/files/
+-- project-analysis-records/[recordId]/
+-- project-changes/[changeId]/
+-- sediment-review/
+-- sediment-review/[batchId]/
+-- project-intelligence/
+-- project-intelligence/[section]/
+-- project-intelligence/[section]/[itemId]/
+-- tasks/
+-- dev-logs/
+-- dev-logs/[section]/
+-- dev-logs/[section]/[itemId]/
+-- ai-review/
+-- ai-review/[section]/
+-- ai-review/[section]/[itemId]/
+-- work-sessions/[sessionId]/
+-- imports/
+-- settings/
`-- page.tsx
```

Frontend layers:

| Layer | Purpose |
| --- | --- |
| `app/` | Routes and layouts |
| `components/` | Reusable UI components |
| `features/` | Domain components where a page grows beyond route-level composition |
| `lib/api.ts` | Typed API client and request helpers |
| `lib/auth/` | Auth token handling and route guards |
| `lib/project-insights.ts` | Project/file insight classification rules |
| `components/ui/` | Shared cards, badges, toasts, project context bar, and layout primitives |

## Backend Architecture

The backend will use conventional Spring Boot layering:

```text
backend/src/main/java/com/projectflow/
+-- ProjectFlowApplication.java
+-- config/
+-- controller/
+-- dto/
+-- entity/
+-- repository/
+-- security/
+-- service/
`-- support/
```

| Layer | Responsibility |
| --- | --- |
| Controller | HTTP endpoints and request validation |
| Service | Business rules, ownership checks, workflow transitions |
| Repository | JPA persistence |
| Entity | Database mapping |
| DTO | API request and response models |
| Security | JWT, password hashing, authenticated user context |
| Support | Shared error responses, parser utilities, AI provider contracts |

Recent service/controller split:

| Area | Primary classes |
| --- | --- |
| Project import and legacy suggestions | `ProjectIntelligenceController`, `ProjectIntelligenceService` |
| Project materials | `ProjectMaterialController`, `ProjectMaterialService` |
| Project analysis jobs and records | `ProjectAnalysisController`, `ProjectAnalysisService`, `ProjectAnalysisJobService`, `ProjectAnalysisRecordService` |
| Project memory and fact sources | `ProjectMemoryController`, `ProjectMemoryService` |
| Structured change review | `ProjectChangeController`, `ProjectChangeReviewService` |
| Zip scanning | `ProjectZipScanService` |
| Model calls and fallback | `ModelGatewayService` |
| Work sessions and evidence | `WorkSessionScanController`, `WorkSessionScanService`, `EvidenceBundleService`, `EvidenceDraftChangeService` |

## Key Flows

### V3.2 Evidence To Growth Flow

```mermaid
flowchart LR
    Import[Add project zip] --> Profile[Project profile]
    Profile --> Path[Bind local path]
    Path --> Scan[Refresh Git changes]
    Scan --> Session[Work Session]
    Session --> Bundle[Evidence Bundle]
    Bundle --> Change[Project Change]
    Change --> Review[User review]
    Review --> Memory[Project Memory]
    Memory --> Timeline[Growth Timeline]
    Timeline --> Outputs[Daily review / README / report]
    Memory --> Context[Sync confirmed context]
```

Rules:

- Zip import creates the initial profile and file structure; it is not the daily change tracker.
- Local project path is required before Git evidence, agent result scanning, and context sync.
- Evidence Bundle is objective evidence, not a confirmed project fact.
- Project Change is the editable review object.
- Only accepted changes and user-confirmed memory become output sources and synced context.
- The dashboard must expose the next action without requiring documentation.

### Authentication

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant B as Backend
    participant DB as PostgreSQL
    U->>F: Submit login form
    F->>B: POST /api/auth/login
    B->>DB: Load user by email
    B->>B: Verify BCrypt password
    B-->>F: JWT access token
    F->>F: Store token for API requests
```

### Markdown Import

```mermaid
sequenceDiagram
    participant F as Frontend
    participant B as Backend
    participant DB as PostgreSQL
    F->>B: POST /api/imports/preview
    B->>B: Parse front matter and sections
    B-->>F: Parsed preview or parse errors
    F->>B: POST /api/imports/confirm
    B->>DB: Save dev log and import record
    B-->>F: Created log result
```

### AI Output Generation

```mermaid
sequenceDiagram
    participant F as Frontend
    participant B as Backend
    participant R as Redis
    participant DB as PostgreSQL
    participant AI as AI Provider
    F->>B: POST /api/ai-outputs
    B->>DB: Load confirmed memory, daily review sources, tasks, and logs
    B->>AI: Generate structured output when provider is available
    AI-->>B: Generated Markdown or local-template fallback
    B->>DB: Save ai_outputs row
    F->>B: GET /api/ai-outputs/{id}
```

## Security Baseline

- Passwords are stored only as BCrypt hashes.
- JWT secret must come from environment variables.
- Every project-owned resource is filtered by authenticated user ownership.
- AI API keys are backend-only environment variables.
- Real `.env` files are ignored by Git.
- Parse errors and AI errors return safe messages without leaking secrets.
