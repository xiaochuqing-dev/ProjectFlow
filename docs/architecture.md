# Architecture

## V3.7 universal evidence intelligence boundary

An explicit `PROJECT_UNDERSTANDING_REFRESH` job now follows:

`Repository Intake → Evidence Discovery → Structure Index → Historical Coverage → Semantic Scout → Capability-validated Plan → Dynamic Profile → persisted read model`.

`ProjectEvidenceDiscoveryService` reuses the bounded V3.5 inventory and adds relative, typed source candidates plus redacted UTF-8 samples. It does not become a parser or content database. `SemanticScoutService` reuses the existing `PROJECT_UNDERSTANDING_SNAPSHOT` Model Gateway task for one combined Scout/profile response; there is no second client or per-file model loop. Unknown evidence IDs are filtered before output.

`AdaptiveAnalysisPlanner` owns deterministic guardrails and combines them with bounded Scout suggestions. `AnalysisToolRegistry` is the allow-list between model intent and engineering providers; the model never constructs a shell command. Existing filesystem/manifest, SCIP consumer, Git CLI, Agent result and document-reader boundaries remain the implementations.

`DynamicProjectProfileSynthesizer` emits only applicable sections. `HistoricalCoverageService` uses bounded local Git metadata and existing ProjectFact commit references, then selects current-state-only, early-project, milestone-window or clustered-long-history strategies. It does not write Facts, Timeline, Capability or Evolution.

The V3.7 fields live in the replaceable `snapshot_json`, so no migration is required. V3.6 fixed sections remain readable compatibility projections and the next explicit refresh rebuilds the V3.7 profile. All GET endpoints remain persisted reads.

## V3.6 deep structural intelligence boundary

`CompositeProjectStructureIndexer` is the production `ProjectStructureIndexer` SPI. It always builds a bounded `MANIFEST_FILESYSTEM` fallback, then optionally consumes a safe project-local `index.scip` through Sourcegraph's official protobuf. SCIP definition/reference occurrences form a code-relation graph; JGraphT supplies PageRank important-node scoring and Label Propagation clustering. Missing, invalid, oversized, or truncated SCIP input degrades coverage and diagnostics without breaking current understanding.

Structure Index V2 is rebuildable intelligence, not a fact source. Deterministic code owns provider parsing, symbol/reference relations, graph membership, coverage, budgets, and the evidence allow-list. The existing Model Gateway receives only a compact summary of important nodes, functional areas, relative paths, and evidence IDs for user-readable semantic synthesis. There is no per-file model loop.

The minimum `ProjectEvolutionBridgeService` runs only during an explicit changed refresh. It joins an existing Project Fact to a real Git commit and parent, a bounded `git diff-tree` file set, and a current structural area. Its rows are idempotent derived records; they never mutate Facts, Timeline, Capability, or prior Capability Evolution. GET endpoints remain database-only.

Tree-sitter, language-specific SCIP index production, Desktop shells, watchers, daemons, installers, and automatic updates remain outside the V3.6 runtime boundary.

## V3.4.1 automatic timeline boundary

`ProjectFact` remains the factual source of truth. `ProjectTimelineService` is a database read model for deterministic day/ISO-week/month/lifecycle statistics; `ProjectTimelineSummaryService` maintains replaceable summaries and period-local themes through the existing persistent job executor and ModelGateway. Fact after-commit events mark scopes dirty, GET requests never invoke a model, history backfill defers generation until completion, and a failed refresh preserves the previous successful content. See `docs/project-timeline.md`.

## System Overview

ProjectFlow uses a separated frontend and backend architecture. The V3.4.1 product spine is a local-first project memory system that turns real Git, worktree, and Agent evidence into durable Project Facts and a traceable automatic Timeline without routine manual confirmation.

## V3.4.0 automatic fact memory boundary

The proven scan front half remains: `PendingChangeScanService` prepares an incremental range, evidence readers build an `AnalysisInputSnapshot`, and `DevelopmentSegmentationService` plus optional model enrichment produce `DevelopmentSegment` records. The persistence back half changes: validated segments enter fact ingestion, produce idempotent `ProjectFact` rows, update batch fact statistics, and advance `ProjectFactCursor` only after successful persistence.

```mermaid
flowchart LR
    Evidence[Git / worktree / Agent evidence] --> Snapshot[AnalysisInputSnapshot]
    Snapshot --> Segment[DevelopmentSegment]
    Segment --> Validate[Evidence and quality validation]
    Validate -->|recordable| Fact[ProjectFact]
    Validate -->|exception| Attention[Needs Attention]
    Fact --> Records[Project Records]
    Fact --> Memory[Project Memory read models]
    Fact --> Cursor[Fact Cursor advances]
    Attention --> Records
```

`DevelopmentSegment` belongs to one analysis batch and retains model/fallback diagnostics. `ProjectFact` is the stable long-term record of what happened. Facts are append-oriented, are not merged by title similarity, and are not replaced by later timeline or capability generation. Evidence conflicts, missing evidence, incomplete boundaries, and unsafe duplicates become `NEEDS_ATTENTION`; they never block other facts or the next incremental range.

Historical reconstruction uses the existing durable-job reliability infrastructure but a separate history state/checkpoint from the incremental Fact Cursor. It processes bounded commit chunks oldest-first, skips already covered commits, preserves completed chunks on cancellation, and resumes from persisted state. It never sends the full repository history to one model request.

`ProjectChange`, `SedimentAction`, `ProjectSediment`, `ProjectReviewCursor`, and the legacy `ProjectMemory` profile row remain compatibility boundaries. New normal scans do not create the manual sediment suggestion queue. Complete timeline, lifecycle capability map, Hermes sync, and Obsidian sync are future fact consumers, not V3.4.0 architecture components.

## V3.3.8.1 dashboard read boundary

The database is the source of truth for analysis jobs, change batches, development segments, Project Facts, fact cursors, history state, and legacy compatibility records. A project-scoped sessionStorage snapshot may render immediately, but the lightweight `dashboard-bootstrap` read model always calibrates it from persisted state. Bootstrap performs only latest/count/bounded projection reads; Git, GitHub CLI, model calls, filesystem scans, history reconstruction, and long analysis remain outside this boundary. Secondary reads cannot clear the core scan result on failure.

## V3.3.8 model reliability boundary

`ModelTaskType` is the model-entry registry. `ModelCapabilityRegistry` describes Provider/model features, `ModelRequestPolicy` calculates task-aware parameters, `ModelGatewayService` owns HTTP/retry/diagnostics, and `ModelOutputAdapter` owns balanced candidate extraction and target-aware normalization. Business services own prompts and evidence validation only.

Provider testing also uses the gateway. This prevents Settings, project analysis, file analysis, change scanning and capability flows from drifting into different parameter or retry rules.

## V3.3.7 Job Execution Boundary

All job entry points converge on one project-locked active lookup. “Retry completed history” is separate from active uniqueness: a retry cannot force a second equivalent active job. Retry lineage is persisted for audit without exposing request bodies or model responses.

HTTP creation requests only validate ownership, lock the project row, reuse or persist a job, and submit its ID to the bounded executor. External Git, GitHub and model waits run without method-level database transactions. Each stage reloads cancellation and budget state; formal results are persisted only after a final checkpoint. Queue saturation becomes REJECTED rather than a model failure.

The restart listener requeues untouched QUEUED jobs. RUNNING work before model dispatch becomes RETRYABLE; work at or beyond a model stage becomes INTERRUPTED because automatic replay could duplicate billing. Completed and confirmed records are never deleted by cancellation or recovery.

## V3.3.6 legacy batch review and transaction boundaries

The V3.3.x batch review remains readable for old records and links. Its formal suggestions, local drafts, actions, and confirmed sediments are compatibility data; they do not define V3.4.0 batch completion or Fact Cursor advancement.

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
| PostgreSQL | Source of truth for users, projects, batches, segments, facts, cursors, history state, legacy records, and outputs |
| Redis | AI task state, project statistics cache, rate limits, future import locks |
| AI provider | OpenAI-compatible Provider through ModelGateway; fixed compatible services are test infrastructure only |

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
| Project fact memory | `ProjectFactIngestionService`, `ProjectFactService`, `ProjectFactController`, history-state/backfill coordination |
| Legacy project memory and fact sources | `ProjectMemoryController`, `ProjectMemoryService` |
| Structured change review | `ProjectChangeController`, `ProjectChangeReviewService` |
| Zip scanning | `ProjectZipScanService` |
| Model calls and fallback | `ModelGatewayService` |
| Work sessions and evidence | `WorkSessionScanController`, `WorkSessionScanService`, `EvidenceBundleService`, `EvidenceDraftChangeService` |

## Key Flows

### V3.4 automatic fact and history flow

```mermaid
flowchart LR
    Bind[Bind local Git project] --> Scan[Analyze new changes]
    Scan --> Batch[Change Batch]
    Batch --> Segments[Development Segments]
    Segments --> Facts[Automatic Project Facts]
    Facts --> Cursor[Advance Fact Cursor]
    Facts --> Records[Project Records]
    Facts --> FactMemory[Project Memory]
    Cursor --> Backfill[Start or resume bounded history backfill]
    Backfill --> OlderFacts[Older Project Facts]
    OlderFacts --> Records
```

Rules:

- Fact persistence and incremental cursor advancement share one success boundary.
- `NEEDS_ATTENTION` is a fact-quality state, not a blocking approval queue.
- A reusable batch may idempotently fill missing facts before returning.
- History coverage and incremental coverage are independent; history work cannot move the normal cursor backward.
- Read APIs use ownership checks, pagination and aggregate/projection queries rather than loading all facts.

### V3.2 evidence-to-growth compatibility flow

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
- Evidence Bundle is objective evidence, not a stable V3.4 ProjectFact.
- Project Change is the editable review object.
- Existing accepted changes and user-confirmed memory remain compatibility sources; later fact-native outputs will consume stable ProjectFact read models.
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

## V3.4.2 fact-native capability layer

The durable flow is ProjectFact truth → Timeline temporal read model → ProjectCapability long-lived derived map. Capability bootstrap and incremental refresh reuse the persistent job executor and ModelGateway, call models outside fact transactions, validate exact source-fact coverage, then atomically apply stable capabilities, immutable evolutions, normalized fact relations and coverage. GET endpoints are read-only. Source fingerprints coalesce duplicate work; history backfill defers map generation until completion; a failed refresh retains the last successful map. Legacy capability cards remain a separate compatibility boundary.

## V3.4.3 Project Memory Gateway and Hermes

Project Memory Gateway is an additive read-only business layer over Facts, Timeline, Capabilities and Evolutions. It normalizes snapshot, occurrence-time recent changes, cross-layer search, timeline, capability/evolution, fact trace and budgeted brief semantics while retaining explicit SOURCE versus DERIVED labels. The local stdio MCP adapter maps nine narrow tools to this Gateway. Neither the adapter nor GET endpoints invoke models or write project memory. Remote MCP remains a separate secured boundary.

## V3.4.4 Obsidian projection boundary

The repository-local projection CLI is a second Gateway consumer beside Hermes. It builds curated Markdown in a configured Vault managed root, never queries repositories directly, invokes a model, or writes ProjectFlow state. CORE keeps file growth proportional to months and capabilities rather than facts. Stable metadata and a recoverable manifest form the incremental control plane; managed blocks, conflicts, path containment, atomic replacement and non-destructive redirects protect user content. No frontend, watcher, persistent sync job or operating-system integration is added.
