# ProjectFlow Project Context

Last updated: 2026-08-24

V3.9 Project Continuity Closure 已从最终 master `ab29b1ff0f842c029b5cf121bd584bd40fcf74b2` 在 `codex/v3.9-project-continuity-closure` 开始。当前已完成源码复用审计并在实现前冻结 15 Calibration + 15 Holdout：继续复用来源 fingerprint/affectedFrom/31 天 overlap、16 Story/360 Event/最多 16 Window、durable checkpoint、`USER_DECLARED_PRESENTATION`、Agent Context Package v2、Gateway/Hermes/Obsidian corrected view。真实缺口限定为统一 Continuity Delta、未受影响 Chapter 工程身份稳定、安全 additive correction replay、派生 Current Project State，以及 history/current-state 与 Context revision 联动。不得新建第二套 History/Fact/增量/投影引擎，不做 daemon/watcher、最终 GUI、Tag 或 Release。当前仍是开发中状态，Provider、dogfood、真人验收、CI、合并与 backfill 均不得预填 PASS。

V3.8.5 Final Chapter Closure 当前状态：Representative Cluster、dominant/co-dominant/minor、代表覆盖、Claim ceiling、有界 repair 与保守 split 工程链已完成。源码头 `e1b67f28428e73f39fc23aa6f85961155a20ffd8` 的同头 run `32609107531` 中，GPT 5.6 Luna Responses/max、DeepSeek V4 Flash Chat/max、Qwen3.7 Plus Messages/max 均通过 qualification 19/19 和 Chapter scenarios 9/9；没有替代 Qwen 模型。Final Chapter 已冻结为三 Provider 各 4 个、共 12 个 Chapter；Round 3 的 30 Story/8 Chapter 与 canonical-LF 哈希不变，展示变更 30/30 的 Truth/Evidence semantic hash 不变。2026-08-24 项目所有者明确批准最终 package 与 merge，并明确豁免本轮量化人工评分；独立签字结论为 `PASS_BY_EXPLICIT_OWNER_OVERRIDE`。PR #15 已以 `29c154eb618ca43edf58c631c14cc1d296e14f3f` 合入 master，merge 后 run `32652683003` 和干净 master 根启动器均通过；acceptance-backfill PR #16 承载最终元数据并在合并时生效。冻结工件继续保持人工字段空白和 reviewerCount=0，独立签字 reviewerCount=1，不得伪造数值分数；仍禁止 Tag 和 Release。

2026-08-24 PHASE A0 复核确认当时本地、远端与 PR Head 均为 `7d181af2b1bea20d8dab35778da2abf64d446dfa`，base master 为 `5cb5e49661206feb8f59885bea672c314c9374e8`，push/PR CI 全绿，冻结 manifest/worksheet 合同 2/2 通过。交接材料记录用户对最终展示的总体判断约为 8.5/10，并把剩余主要问题归为 GUI、ID/Hash 默认过长、内部 diagnostics 过载和中英文产品标签混排；这项总体意见没有换算成逐样本 1-5 分。正式中文与渐进披露合同已沉淀到 `docs/product-language-and-progressive-disclosure-contract.md`，最终 GUI 延后到 V4.0。后续项目所有者用显式 override 完成签字，限制见 `docs/projectflow-v3.8.5-final-human-signoff.md`。

历史 RC3 仍保留：Round 1 与 Round 2 均为 NEEDS_REVISION_NOT_APPROVED。Round 2 的 ProjectFlow skeleton/login P0 证明 Story-wide Evidence 聚合会让一个主体借用同 Commit 无关代码的实现强度；RC3 已改为 Technical Atom 级 subject/action/state 归因，区分 direct Evidence 与不可提升状态的 indirect context，并对不够精确的 `project-area-*` 主体设置 OBSERVED 上限。2026-08-14 的 GLM / DeepSeek 双 Provider run 与 Round 3 继续作为不可改写历史证据，不代表 Final Chapter 已获真人批准。

Use this file as the first read for substantial ProjectFlow work. It is a compact routing layer, not a replacement for source code. After reading it, open only the docs and modules relevant to the current task.

## Product Position

ProjectFlow V3.8.5 is a local-first project-history reconstruction, strong-fact and shared project-memory system for anyone who uses a computer to conduct a project. It turns real project materials into a human-readable history while preserving ProjectFact as the only strong-fact source. Replaceable understanding and history snapshots remain separate from durable Project Facts, compatibility Timeline, optional Capability Map, Agent candidates, Project Memory Gateway, Hermes reads and Obsidian projection.

ProjectFlow V3.4.5 established the backend intelligence and model-protocol foundation. ProjectFlow V3.5.0 added the bounded intake foundation retained by V3.6.0 and V3.7.0.

Current direction:

- Local Git supplies objective change evidence; Agent results add task intent and verification context.
- The primary cross-project workflow is 真实材料 → 来源事件 → 时间篇章 → 变化故事 → 演变链 → Evidence 下钻 → 当前状态与长期继续工作.
- Rules collect evidence, models interpret it, rules validate the result, and ProjectFlow automatically records normal evidence-backed facts.
- Human attention is exceptional: evidence conflicts, missing evidence, incomplete boundaries, or unsafe duplicates become `NEEDS_ATTENTION` without blocking later scans.
- GitHub CLI is optional metadata/link enrichment and must never block local Git analysis.

V3.8.5 focus:

- Project History now separates Raw Events, Technical Atoms, Primary Stories, Supporting Changes, Evolution Threads and readable Chapters. The default layer describes action, understandable object and confirmed result; engineering paths, symbols and Evidence remain available on demand.
- Deterministic grouping and validation own event membership, chronology, transitions, authority and Evidence. Bounded semantic windows may improve wording and presentation role, but cannot change facts, events or Evidence.
- Window cache keys include source fingerprint, strategy/Prompt versions, window identity and presentation correction revision. Durable checkpoints retain validated results and expose failed, cancelled, skipped and unprocessed scope.
- `USER_DECLARED_PRESENTATION` corrections are persistent, auditable, reversible overlays. Gateway, Agent Context, Hermes, frontend and Obsidian all read the same corrected view without promoting declarations to ProjectFact.
- Obsidian CORE projects a bounded reading set; all Story/Thread and audit projection remains explicit opt-in. This stage adds no dependency, Tag, Release, daemon, watcher or final GUI.

V3.8.0 focus:

- Project History is the universal product axis. Capability remains an optional software-project view and compatibility layer.
- `ProjectHistoryEvent` is the normalized raw-event inventory. It retains source identity, revision, occurrence time, category, transition, authority, epistemic status, Evidence, relations and rewrite state without becoming a ProjectFact.
- `ProjectHistorySnapshot` is a replaceable Level 0-3 read model containing overview, dynamic chapters, change stories and evolution threads. Failed refresh keeps the prior successful snapshot.
- Refresh runs only through explicit persistent `PROJECT_HISTORY_REFRESH` jobs. GET overview/chapter/story/thread/event/evidence/filter APIs are owned, bounded, read-only and model-free.
- Deterministic grouping owns membership, chronology, transition and Evidence. One bounded `PROJECT_HISTORY_SYNTHESIS` call may improve wording only; unknown IDs, fields, Evidence and unsupported authority claims are rejected.
- Large history is paged, cached by source/strategy/Prompt identity and incrementally rebuilt with overlap. Git rewrite marks removed source events stale or invalidated rather than deleting them.
- The presentation contract is Overview → Chapter → Story → Thread → Raw Event → Evidence. The minimal history page is a developer preview, not the final GUI.
- Project Memory Gateway, 19-tool Hermes MCP and Obsidian Projection consume the same persisted history. Obsidian keeps zero-plugin official URI as the baseline and optional Advanced URI as an enhancement.
- V3.8.0 introduces no new dependency, Tag, Release, daemon, watcher, Git client, project-management board, Agent manager or generic RAG layer.

V3.7.5 focus:

- `docs/projectflow-v3.7.5-product-constitution.md` is the single authoritative product constitution. The seven epistemic states are shared by entities, DTOs, Prompt, API, Agent contract and tests.
- Prompt contract v3, Semantic Scout v13 and Final Synthesis v7 use a complete small-set Evidence Ledger, exact Capability decisions, bounded Claim counts, required Agent-result deep reads for process-evidence dimensions and explicit semantic-contract degradation. Production and Eval remain identical at the semantic boundary.
- Agent Context Package v2 accepts task, scope, revision preference, Evidence depth and budget; ranks persisted facts/evidence/ranges deterministically; retains conflicts, unknowns, limitations and unread scope; and exposes a stable SHA-256 package revision without a model call.
- Agent Work Result Candidate Write re-reads non-sensitive changed files inside the bound project, binds source hashes, keeps commands/tests as process evidence and rejects direct strong-fact status before persistence.
- Local revalidation verifies a Fact, refreshes Evidence, re-reads a range, validates currentness or resolves a package against the latest local revision using fixed commands and bounded reads. It does not rerun project understanding or mutate facts.
- Timeline summaries explicitly expose `INFERRED` and `NON_AUTHORITATIVE`. V3.8 project-life work remains limited to source-backed event semantics; no automatic importance, phase, maturity or milestone authority is introduced.
- V3.7.5 creates no Tag or Release and does not build a final GUI, Agent manager, model leaderboard or generic RAG layer.

V3.7.4 focus:

- `ProjectFactEpistemicStatus` makes `OBSERVED` and `VERIFIED` the only strong statuses. `DECLARED`, `INFERRED`, `CONFLICTED`, `UNKNOWN` and `PROCESS_EVIDENCE` cannot enter the normal recorded-fact path.
- Historical reasons, deprecation and technical debt need explicit evidence classes. Agent results and model agreement cannot promote a claim.
- `LargeFileContentService` builds a bounded lexical Content Map and representative ranges with source hash, line/byte bounds, revision sensitivity, explicit unread ranges and no parser/platform reinvention.
- Evidence Discovery keeps normal README/manifest/test/CI/migration/infra diversity and recognizes non-binary extensionless text as unknown documents; content remains more authoritative than a filename.
- Authenticated portfolio/history APIs and Hermes MCP resources expose safe multi-project catalog, search, evidence, knowledge and versioned Context Packages. Every project read keeps ownership isolation and safe audit fields.
- Agents may submit project-bound candidates for engineering validation but cannot directly submit `OBSERVED` or `VERIFIED`.
- Strong Fact contract v2, Scout v11 and Final v6 are shared by production and Eval across OpenAI Responses and Chat Completions. Calibration and frozen Holdout are separate; neither labels nor case identities enter the production builder.
- V3.7.4 does not add a model leaderboard, generic RAG, parser, Agent manager, large GUI, complete life narrative, Tag or Release.

V3.7 focus:

- V3.7.3 introduces `AnalysisTimePolicy`: connection timeout stays short and bounded, Provider request timeout honors explicit configuration, and overall analysis deadline is AUTO/FINITE/UNLIMITED. AUTO/UNLIMITED do not impose a hidden short overall deadline; retry remains bounded and cancellation/heartbeat remain active.
- `ProjectUnderstandingPromptBuilder` is the only Scout/Final Prompt source for production and direct Eval. Contract v1, Scout v10 and Final v5 have fixture hashes, production/eval parity tests and a type boundary that cannot accept Ground Truth. Scout context keeps all selected Evidence IDs and short summaries while limiting repeated document samples and structure projections through category/module-diverse representatives. Independent manifest, document, Git-history and Tag-anchor gaps remain separate model decisions.
- Engineering discovery publishes objective classification and `UNKNOWN` importance; the model decides semantic importance, information gap, deep-read need and affected dimensions. Capability/View registries compute only objective eligibility and validate the model choice.
- Long duration or Token pressure never silently removes necessary Evidence, deep reads or qualified Final Synthesis. Elapsed time, Token usage, request count and cost are process diagnostics rather than quality defects. Current quality mode is explicit `QUALITY_FIRST`.
- Reasoning control is capability-gated rather than guessed from model names. Explicitly supported OpenAI Responses and Chat profiles use high for connection, semantic and recovery requests. Reasoning-capable tasks may use the user-configured Provider output ceiling from the first request; it is a loose safety boundary, not a consumption target. Unknown profiles omit unsupported fields without lowering the Evidence or quality gates.
- V3.7.3 repeats the unchanged GLM `glm-5.2` / OpenAI Responses reliability sample, semantic sample, 38-run and eight real production-chain cases. Results and the V3.8 decision belong only to the dated acceptance reports.
- V3.7.2 calibrates Semantic Scout and Final Synthesis with a ProjectFlow-only 18-case internal evaluation harness. Fixed and real Provider results are local/CI artifacts; hallucination, accuracy, repeatability, cost and model-comparison metrics never enter product APIs, snapshots, databases or UI.
- The funded GLM `glm-5.2` / OpenAI Responses revalidation completed the unchanged 38-run set. It remains NOT PASSED: 19 bounded transport timeouts, Tool recall 0.1667, Dynamic View recall 0.0941 and Repeatability 0.4130. Real `ProjectUnderstandingService.refresh()` acceptance passed 2/8 core cases. The earlier DeepSeek pilot/HTTP 402 history is retained; V3.8 remains blocked.
- `HighValueEvidenceGate` triggers a second call only for validated substantive deep content, history anchors, current worktree details or conflict/currentness evidence. The persisted decision contains trigger/skipped reasons and evidence IDs.
- Final Synthesis failures use `FAILED_DEGRADED`: Stage 1, validated tool evidence, trust diagnostics and a current limited profile survive timeout, cancellation, invalid schema and Provider errors.
- Analysis execution cache identity includes source/content/structure revisions, requested capabilities, deep-read targets, Provider version, execution/semantic budgets, strategy version and relevant source signatures.
- External integrations are limited to Evidence Source, Intelligence Provider and Projection adapter contracts. `ExternalEvidenceEnvelope` is project-bound, revisioned, redacted, raw-payload-free and never promotes itself to ProjectFact.
- ProjectFlow owns Facts, current interpretation, historical coverage, evidence-backed evolution and their presentation. It does not become an agent runtime/manager, Provider switcher, token dashboard, model leaderboard, repository replacement, generic RAG/workflow engine, parser/SCIP producer, updater or tool control center.
- V3.7.1 completes Discover → Scout → Plan → Execute → Validate → Synthesize. `AnalysisExecutionCoordinator` executes only validated capability names; providers own fixed commands and safe file access.
- FILESYSTEM and SCIP reuse already computed results. `BoundedLocalAnalysisCapabilityProvider` supplies bounded DOC_READER, MANIFEST, AGENT_RESULT, GIT_HISTORY, GIT_TAG and WORKTREE evidence without persisting full documents, patches, prompts or command construction from model output.
- Semantic analysis is capped at 0/1/2 Model Gateway requests. The second `FinalProfileSynthesisService` call is conditional on newly executed high-value evidence.
- `BudgetAwareContextPacker` constructs complete JSON inside category and global budgets before serialization and exposes packing diagnostics. It never slices serialized JSON.
- Discovery uses category/module quotas, duplicate compression, signature-keyed inspection and sample caches, and outbound secret redaction. Changed-small-set refreshes retain unchanged cached inspection results.
- Historical Coverage exposes weighted dimensions and per-period confidence. Git commit volume is only one bounded signal; absent Fact, Tag, document and Agent evidence remains visible as low coverage.
- External SCIP producer execution remains deferred after safe PoC review. No automatic download, project build, runtime mutation or machine-global setup is introduced.

- `ProjectEvidenceDiscoveryService` builds a bounded Evidence Source Map over code, manifests, documents, configuration, tests, CI, migration, Agent context/results, Git and unknown text candidates. It samples UTF-8 content with hard limits and redacts likely credential fields.
- Empty directories and blank text use zero model calls. Non-empty documents and oddly named TXT/Markdown files can enter the same single `PROJECT_UNDERSTANDING_SNAPSHOT` Model Gateway task used for bounded Scout and profile synthesis.
- `SemanticScoutService` emits evidence-bound project-shape hypotheses, source assessments, applicable dimensions, tool requests, conflicts and currentness warnings. Unknown evidence IDs are discarded.
- `AdaptiveAnalysisPlanner` combines semantic hypotheses with deterministic guardrails. `AnalysisToolRegistry` accepts only registered and available capabilities; a model cannot construct shell commands or claim unavailable Git/SCIP/remote evidence.
- `DynamicProjectProfileSynthesizer` produces applicable sections rather than filling a fixed six-section template. Empty, document, script, code and short/long-history inputs may expose different views.
- `HistoricalCoverageService` reports Git and Tag availability, earliest/latest evidence, ProjectFact commit coverage, covered/gap periods and explicit limits. Evolution Preview selects a bounded strategy and never performs per-commit model calls.
- V3.7 persists these additions inside the replaceable understanding JSON. No new database schema is introduced; V3.6 snapshots remain readable through compatibility fields and are safely rebuilt on the next explicit refresh.
- The refreshed UI exposes Evidence Sources, Analysis Plan, Applicable Views, Dynamic Sections, Historical Coverage, Unknowns and the existing evidence-backed Evolution Bridge without a visual redesign.
- SCIP producer auto-install/invocation remains deferred after research: producer/runtime/build side effects are not silently introduced into arbitrary user projects. Existing official SCIP consumption and fallback stay production-safe.
- No daemon, watcher, system tray, automatic background analysis, Desktop migration, generic RAG framework, parser, vector database or second model client is added.

V3.6 focus:

- Repository intake works for empty, non-code, non-Git, ordinary Git, large, and multi-workspace directories. Missing Git only disables historical understanding.
- `ProjectStructureIndexer` remains the stable provider boundary. `CompositeProjectStructureIndexer` always builds the bounded manifest/filesystem fallback and optionally consumes a safe project-local `index.scip` through Sourcegraph's official protobuf.
- Structure Index V2 records bounded Symbol, Definition, Reference, relation graph, JGraphT PageRank important nodes, relation-driven functional areas, provider diagnostics, metrics, coverage, and unknowns. ProjectFlow does not invent language parsers or a symbol protocol.
- Intake and structure work is bounded by file count, per-file bytes, total read bytes, command timeout, sampled detail count, and compact model prompt size.
- Adaptive plans are deterministic. Empty/non-code/no-model/unchanged inputs make zero model calls; eligible code projects use one bounded semantic stage through `ModelGatewayService` and the existing persistent Job system.
- `ProjectUnderstandingSnapshot` distinguishes observed, inferred, evidence-bound, unknown, coverage, confidence, and current/stale state. It is current interpretation, never a new factual source.
- GET understanding/index requests only read persisted results. They do not scan files, invoke Git, call models, advance facts, or modify derived history.
- Failed semantic refreshes preserve the previous successful snapshot as stale; without a previous snapshot, a deterministic fallback remains readable.
- The minimum Evolution Bridge is a derived, idempotent link among a real Git parent/commit, an existing Project Fact, changed files, and an affected structural area. Missing evidence produces no fabricated history.
- Tree-sitter, external SCIP index production, Agent session adapters, and Desktop shells remain explicit extension boundaries, not falsely claimed V3.6 built-ins.

V3.4.5 focus:

- Model Gateway V2 keeps one business facade while official SDK adapters support OpenAI Responses, OpenAI Chat Completions and Anthropic Messages.
- ProjectFlow alone owns retry, cancellation, concurrency, dynamic budgets, finish/usage normalization, truncation/reasoning recovery and Schema repair; SDK retry is disabled.
- Provider protocol, endpoint override, auth mode, timeout and capability overrides are explicit. Legacy providers migrate idempotently to Chat Completions without changing keys or defaults.
- ProjectMemorySearchService and ProjectEvidenceTraceService are focused read slices behind the unchanged Project Memory Gateway API.
- Real-data audit findings remain traceable; generic historical facts are not silently rewritten. The old “Automatic Memory Maintenance next” statement is historical and superseded; V3.8 Evidence-backed Evolution Reconstruction follows only after V3.7.2 quality acceptance.

V3.4.4 focus (still applies):

- Obsidian consumes Project Memory Gateway as a knowledge projection, not a source of truth or database mirror. It never calls a model or writes ProjectFlow facts and derived entities.
- Default CORE produces Overview, monthly Timeline, stable Capability notes, compact monthly Fact indexes and navigation indexes. EXTENDED adds high-value facts; FULL_FACTS is explicit.
- The repository-local CLI provides validate, dry-run, status and one-shot sync against an existing Vault and dedicated managed root. There is no frontend page, watcher or global configuration.
- Stable entity metadata, source version/content hash, projection version and a recoverable manifest drive deterministic incremental plans; unchanged notes receive zero writes.
- Managed blocks preserve user frontmatter and authored content. Identity/marker/hash conflicts do not overwrite, and atomic writes recover from interruption.
- Traversal, absolute escape, symlink/junction, reserved names, invalid characters, Unicode normalization and case collisions are constrained. Capability rename keeps stable paths and merge preserves redirect notes.
- The projection boundary remains stable in V3.4.5; watcher and remote projection transport are still deferred.

V3.4.3 focus:

- Project Memory Gateway supplies snapshot, occurrence-time recent changes, unified cross-layer search, timeline, capabilities, evolution, fact trace and budgeted brief semantics.
- ProjectFact remains SOURCE; Timeline, Capability and Evolution remain DERIVED. Stable IDs and occurrence time are shared across consumers.
- Reads are owned, paged/bounded, compact by default, model-free and safely audited without full query text or credentials.
- The repository-local Python stdio MCP exposes nine read-only tools to Hermes and accepts only a loopback backend. Remote transport remains a later secured boundary; Obsidian now reuses the same Gateway locally.

V3.4.2 focus (still applies):

- `ProjectFact` remains the only factual source. Timeline is the temporal derived layer; `ProjectCapability` is the stable long-lived capability layer.
- Full-history bootstrap classifies every fact in bounded chunks. Incremental refresh handles only uncovered or changed facts with internal NEW/ENHANCE/ADD_EVIDENCE/MERGE operations.
- Stable identity is system-owned and includes project, problem, product area and canonical meaning. Every evolution and relation traces back to facts, batches, commits, files, Agent results and evidence.
- Maturity is deterministic and explainable. Merges are non-destructive; unsafe or incomplete classification becomes capability attention.
- Durable dirty/fingerprint/job state keeps GET model-free, coalesces duplicate work, defers full-map refresh during history backfill, and preserves the last successful map on failure.
- `/project-intelligence/capabilities` is the main capability map. Legacy cards remain a compatibility section; only traceable CONFIRMED cards may seed stable capabilities.
- Timeline Theme is not Project Capability. Hermes and Obsidian now consume the Gateway read-only for different immediate-query and long-term-reading uses.

V3.4.1 focus (still applies):

- `ProjectFact` is the only factual source for Timeline. `factEventAt` uses `occurredTo`, falling back to `occurredFrom`, and is assigned to day, ISO week, and month in the configured Timeline zone.
- Day exposes facts directly. Week/month/lifecycle summaries are derived, replaceable model output with explicit complete coverage and no next steps, roadmap, priority, or future planning.
- Timeline summaries and period-local themes never mutate facts. Theme membership traces to owned facts and evidence; Timeline Theme is not Project Capability.
- Fact after-commit events mark affected week/month/lifecycle scopes dirty. Persistent refresh jobs coalesce work, keep GET model-free, defer while history backfill runs, and preserve old READY content on refresh failure.
- `/timeline` is the main time view. `/dev-logs` and Daily Review remain legacy compatibility; external consumers reuse the Gateway and do not alter Timeline.

V3.4.0 focus:

- `DevelopmentSegment` remains the analysis-layer result; `ProjectFact` is the stable long-term memory entity.
- New scans no longer create the normal manual ProjectChange/sediment suggestion queue. V3.3.x changes, actions, sediments and review cursors remain readable compatibility records.
- Fact fingerprints use source and evidence identity rather than title similarity. Reusable batches, retries, restarts, concurrent ingestion and history replay must not duplicate facts.
- `ProjectFactCursor` advances only after fact persistence succeeds. `NEEDS_ATTENTION` does not block it.
- Existing Development Segments and confirmed sediments migrate idempotently; H2 and PostgreSQL data are preserved.
- Uncovered Git history is rebuilt oldest-first in bounded background chunks with separate persistent coverage/checkpoint state.
- Project Records presents batches and facts; Project Memory treats Project Facts as the primary factual layer and legacy ProjectMemory fields as compatibility archive content.
- The V3.3.7 job infrastructure, V3.3.8 model gateway and V3.3.8.1 read boundary stay intact.
- Complete timeline, lifecycle capability map, Hermes sync and Obsidian sync are later phases, not V3.4.0 deliverables.

V3.3.8.1 focus:

- Persisted jobs, batches, development segments, Project Facts, and cursors are authoritative for the new flow; formal changes and sediments remain authoritative inside their compatibility views. Project-scoped sessionStorage snapshots are disposable first-render cache only.
- Dashboard Bootstrap restores core persisted state without Git, GitHub CLI, filesystem, or model work. Secondary reads cannot clear the core result when they fail.
- Legacy nullable batch/change/segment fields degrade safely, and sediment batch lists use fixed bulk queries rather than per-batch N+1 reads.
- V3.3.7 job reliability and V3.3.8 model reliability remain unchanged.

V3.3.8 focus (still applies):

- Six model entrypoints share one task registry, Provider capability layer, dynamic request policy, output adapter, failure classifier and diagnostics vocabulary.
- Temperature is configured/recommended/effective/sent separately. Unsupported parameters are omitted; there is no global 0.3 ceiling.
- Output budget is task- and input-aware. The former fixed 4000 complex-task and fixed 2000 compact-retry rules are retired.
- Balanced multi-candidate JSON extraction, target-aware nested collection discovery, Schema repair, truncation recovery and reasoning-exhaustion recovery are bounded and separately diagnosed.
- Real DeepSeek acceptance uses actual application APIs and ProjectFlow self-analysis; Mock/fixed-model tests remain separate CI evidence.

V3.3.7 focus:

- Real acceptance is layered into H2/unit tests, PostgreSQL Testcontainers, production frontend build, Playwright with real backend/frontend processes, and optional explicitly enabled DeepSeek validation.
- Analysis jobs persist cancellation, heartbeat, queue position, idempotency fingerprint, request/time/token budgets, interruption and restart-recovery meaning.
- Duplicate active input returns the existing job; bounded executors and explicit rejection prevent unlimited work; cancellation checkpoints stop later model calls and formal persistence.
- Restart recovery requeues untouched queued work only. A potentially sent model request is never replayed automatically because its billing state is unknown.

V3.3.6 compatibility focus:

- Sediment processing is organized by analysis batch and time; the workbench shows a summary and the processing center handles one formal suggestion at a time.
- Local fact and Agent-result drafts remain visible but never automatically enter the formal suggestion flow.
- Confirmed sediments persist source batches and affected files, enter pending capability analysis, and are the direct input to capability analysis.
- Capability analysis records input sediment IDs and updates their analysis state only after successful card persistence.
- Empty truncated model output triggers one bounded recovery. Reasoning-capable profiles keep their configured effort and Provider ceiling; the current RC3 acceptance profiles use GLM max and DeepSeek Flash max. Reasoning text is not retained, and external waits do not hold method-level database transactions.

V3.3.5 focus (still applies):

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

The project has moved beyond fixed project-shape understanding. Current V3.7 focus:

- Workbench: add/import project → bind local path → analyze new changes → automatic facts → leave safely.
- Incremental scan boundaries use the last successfully recorded Fact Cursor, not the last manual review decision.
- Project Records groups permanent facts by analysis batch and month; batch detail shows all facts and evidence without routine confirmation controls.
- Project Memory shows fact count, commit coverage, earliest/latest fact and recent facts; old sediments and profile fields remain in a compatibility area.
- Historical reconstruction starts only after a successful recent analysis, runs in bounded chunks, persists checkpoints, and never blocks normal incremental scans.
- Project Timeline organizes facts by day, ISO week, month, and lifecycle, with deterministic database statistics and traceable automatic derived summaries.
- Facts describe what happened. Recommendations, importance judgments and future plans remain outside the automatic fact layer.
- ProjectFlow must remain usable without reading docs or asking an agent to explain the workflow.
- Add/import project, zip import, local binding, model configuration, login, Agent bridge, GitHub read-only status and existing outputs remain available.
- The fact-native lifecycle Capability Map is maintained automatically; legacy capability cards are compatibility outputs outside the main chain.
- Project Understanding now begins with Evidence Discovery and returns a dynamic profile plus honest historical coverage; fixed V3.6 fields remain compatibility projections.
- Open-world input behavior is explicit for empty, blank-text, document, small script, software, Monorepo, no-Git and long-history repositories.

Recent implementation report says these are already present:

- `POST /api/projects/{projectId}/analysis/run`
- `POST /api/projects/{projectId}/files/analyze`
- Project analysis and file analysis can use model when configured, otherwise local rules.
- Sensitive file paths are not sent to the model.
- Analysis records exist and can be listed, opened, and deleted.

Known remaining boundary:

- V3.4.2 completes the fact-native lifecycle Capability Map over ProjectFact and Timeline.
- Hermes and Obsidian are future consumers of Facts, Timeline and Capabilities; no formal synchronization protocol is implemented here.
- The legacy ProjectMemory entity and completedCapabilities text remain compatibility fields, not the new source of project history.
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
mvn.cmd test
```

Docker/team startup:

```powershell
.\start.bat
```

For local embedded mode, double-click the repository or desktop `Start-ProjectFlow.bat`. The repository entry uses relative paths, verifies dependencies from `package-lock.json`, rebuilds the current checkout, and does not modify Git history. Docker/team mode remains available through `start-projectflow.ps1`.

## Repository Map

```text
backend/                  Spring Boot API
frontend/                 Next.js app
docs/                     product, architecture, data model, stage plans, reports
.projectflow/             agent bridge protocol/context/results for this project
docker-compose.yml        PostgreSQL and Redis
.env.example              repo-safe environment template
Start-ProjectFlow.bat     portable Windows embedded quick launcher
start.bat                 compatibility launcher
start-projectflow.ps1     Docker/team startup orchestration
```

Key docs to read selectively:

- `README.md`: product scope, tech stack, local startup.
- `docs/architecture.md`: frontend/backend layering and core flows.
- `docs/data-model.md`: base V1 data model and ownership rule.
- `docs/project-fact-memory.md`: V3.4 fact definition, ingestion, cursors, history backfill, read models, and legacy compatibility.
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
- `WorkSessionScanService`: reads the bound local project path and Git evidence, persists batches/segments, and hands validated analysis results to automatic fact ingestion.
- `ProjectFactIngestionService` / `ProjectFactService` and history coordination: persist idempotent Project Facts, advance the incremental Fact Cursor, expose project-owned paged fact/read models, and coordinate legacy/history coverage.
- `EvidenceBundleService` and `EvidenceDraftChangeService`: retain the earlier evidence/change workflow for compatibility and non-fact review use cases.

Important backend constraints:

- All project-owned resources must be scoped through project ownership.
- Real secrets belong in environment variables or user provider config, not committed files.
- Model output remains analysis data until evidence validation. Valid objective segments may become immutable Project Facts automatically; model output must never overwrite existing facts or subjective profile content.
- Zip import skips `.git`, dependency/build output, logs, `.env`, binary/media/archive files.
- Development Segments and Evidence Bundles are analysis/evidence records, not the stable Project Fact layer.
- Normal Project Facts do not require confirmation. Missing or conflicting evidence must produce Needs Attention or no strong fact, never fabricated certainty.
- Fact Cursor advancement and fact persistence share one success boundary; history and incremental cursors remain separate.
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
- `项目记录` -> `/sediment-review`
- `项目记忆` -> `/project-intelligence`
- `项目历程` -> `/timeline`
- `成果输出` -> `/ai-review`
- `设置` -> `/settings`

Important routes:

- `/login`, `/register`
- `/dashboard`
- `/projects`, `/projects/[projectId]`, `/projects/[projectId]/files`
- `/tasks`
- `/dev-logs`
- `/timeline`
- `/imports`
- `/project-intelligence`
- `/sediment-review`, `/sediment-review/[batchId]`（V3.4 项目记录路由，保留旧路径以兼容链接）
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
- Project facts: `/projects/{projectId}/facts`, `/project-facts/{factId}`, `/projects/{projectId}/fact-memory-overview`, `/projects/{projectId}/fact-history-state`
- Project records: `/projects/{projectId}/project-record-batches`, `/project-record-batches/{batchId}`
- Project timeline: `/projects/{projectId}/timeline/overview`, `/timeline/periods`, `/timeline/periods/{granularity}/{periodKey}`, `/timeline/themes/{themeId}/facts`, `/timeline/lifecycle`, `/timeline/retry`
- Capability map: `/projects/{projectId}/capability-map/overview`, `/projects/{projectId}/capabilities`, `/project-capabilities/{capabilityId}`, `/project-capabilities/{capabilityId}/evolutions`, `/project-capabilities/{capabilityId}/facts`, `/projects/{projectId}/capability-map/changes`, `/projects/{projectId}/capability-map/attention`, `/projects/{projectId}/capability-map/retry`
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

V2/V3 project intelligence and compatibility:

- `AiProvider`
- `ProjectMaterial`
- `AiSuggestion`
- `ProjectMemory`
- `ProjectSnapshot`
- `ProjectEvolutionRecord`
- `ProjectChange`
- `ProjectFactSource`
- `ProjectAnalysisRecord`

V3.4 project fact memory:

- `ChangeBatch`: one incremental or historical analysis range.
- `DevelopmentSegment`: analysis-layer semantic grouping.
- `ProjectFact`: stable evidence-backed record of what happened.
- `ProjectFactCursor`: latest incremental commit successfully recorded as facts.
- `ProjectFactHistoryState`: history coverage, checkpoint and backfill lifecycle.
- `ProjectTimelineSummary`: versioned derived summary for one week, month, or lifecycle scope.
- `ProjectTimelineTheme`: period-local theme with explicit fact membership; not a capability.
- `ProjectTimelineThemeFact`: traceable theme-to-fact relation.
- `ProjectCapability`: stable long-lived capability with system-owned identity, aliases, deterministic maturity and merge redirect.
- `ProjectCapabilityEvolution`: immutable trace of capability formation, enhancement, evidence addition or merge.
- `ProjectCapabilityFact`: normalized capability-to-fact relation with formation/enhancement/evidence role.
- `ProjectCapabilityFactCoverage`: exact classification of every source fact for the current generation.
- `ProjectCapabilityMapState`: durable source fingerprint, coverage, successful result and failure-preservation state.
- `ProjectCapabilityAttention`: exceptional missing evidence, invalid classification or unsafe merge record.
- Legacy `ProjectChange`, `ProjectSediment`, `ProjectReviewCursor`, and `ProjectMemory`: compatibility data, not the new scan path.

Ownership rule:

- A user can access task/log/material/suggestion/change/memory/output/fact/cursor/history/capability data only through a project owned by that user.

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
- ProjectFlow imports Agent results as candidate evidence. Agent-result-only claims do not become strong facts unless objective evidence and quality rules support them; normal evidence-backed facts do not require a separate sediment decision.

## Implementation Habits

- Keep changes narrow and aligned with the current V3.4 automatic project fact memory direction.
- Prefer existing services, DTO records, repository patterns, and API client types.
- If adding backend behavior, add focused tests in `backend/src/test/java/com/projectflow`, including H2/PostgreSQL parity for persistence and idempotency where relevant.
- If touching frontend routes or API types, verify `npm.cmd run build`.
- If touching backend service/controller/entity behavior, verify targeted tests or full `mvn.cmd -q test`.
- Do not commit local secrets or real `.env`.
- For current docs or API/library behavior, use Context7 first.

## Context-efficiency Rule

For large tasks, first read this file, then read only the material that can change the decision. This avoids duplicate context with no new information; it must not reduce necessary reasoning, Evidence, verification time or model quality.

Read in this order:

1. The latest relevant stage report in `docs/`.
2. The target frontend route/component or backend controller/service.
3. The matching API client types in `frontend/src/lib/api.ts` when frontend/backend contract is involved.
4. The focused tests for the touched backend service or controller.

Avoid rereading all docs and all source files unless the task is cross-cutting or this file is out of date.
