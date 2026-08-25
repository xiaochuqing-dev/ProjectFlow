# ProjectFlow

ProjectFlow V3.9 is a local-first project continuity and long-term project-memory layer. Its universal product axis is 项目历程: it turns Git, files, documents, Project Facts, Agent results and optional collaboration metadata into stable incremental Events, Stories, Evolution Threads, Chapters, Current Project State, Agent Context and Evidence drill-down.

V3.9 — Project Continuity Closure extends the V3.8.5 final baseline without a second History, Fact or incremental engine. Existing source-event upsert, 31-day overlap, durable window checkpoint, Correction overlay, Context Package v2, Gateway and Obsidian projector are reused. Small changes produce a bounded Continuity Delta, retain unaffected Story/Thread/Chapter identity, safely preserve corrections, update a model-free Current Project State and propagate its revision to Agent and projection consumers. External changes are discovered by explicit refresh; ProjectFlow-owned Agent candidate, Correction and Fact writes can mark continuity dirty without a watcher or model call.

The final V3.9 production/eval head is `f3c3adbd79206fc21a8a5209774a0b71ef47e185`. Frozen 30-case deterministic execution, T0–T7 Dogfood, full Backend/H2, PostgreSQL 16, Frontend/Playwright, Hermes, Obsidian, sensitive-content and ordinary same-head CI pass. Luna, DeepSeek and Qwen each pass the same-head 12/12 blind semantic review, 9/9 Chapter regression and 3/3 continuity suite. PR #17 is merged as master `d4bceed673d7cd630bc3b6663f673be6f2ac3c5b`; master CI and the clean-master launcher pass. The original human worksheet remains truthful `NOT_REVIEWED`; closure uses the documented owner-approved automated and independent semantic-review mode without inventing scores. No Tag or Release is created.

V3.8.5 Final Chapter Closure adds deterministic Representative Clusters, coverage, Claim ceilings and bounded repair on top of the RC3 claim-level Evidence boundary. The same-head final matrix uses GPT 5.6 Luna Responses/max, DeepSeek V4 Flash Chat/max and Qwen3.7 Plus Messages/max; all three passed qualification 19/19 and Chapter scenarios 9/9. Round 1 and Round 2 remain `NEEDS_REVISION_NOT_APPROVED`, and the frozen Round 3 files remain immutable. On 2026-08-24 the project owner explicitly approved the final package and merge while waiving this round's quantitative scores. The result is `PASS_BY_EXPLICIT_OWNER_OVERRIDE`; no 1-5 score is invented, and the single-reviewer and scope limitations remain disclosed in the separate final sign-off evidence.

PR #15 is merged into master as `29c154eb618ca43edf58c631c14cc1d296e14f3f`. Post-merge master quality run `32652683003` passed Backend/H2, PostgreSQL, Frontend, Browser, Hermes, Obsidian and sensitive-content; the root Windows launcher also passed from that clean revision. Exact closure metadata is recorded in `docs/acceptance-evidence/v3.8.5/final-acceptance-backfill.json`.

User-facing language follows the formal Chinese and progressive-disclosure contract in `docs/product-language-and-progressive-disclosure-contract.md`: the first layer shows the story and current result, while full SHA, Evidence IDs and internal diagnostics remain available through engineering detail. The final GUI implementation remains deferred to V4.0.

ProjectFact remains the only persistent strong-fact source. `OBSERVED`/`VERIFIED` stay separate from `DECLARED`, `INFERRED`, `CONFLICTED`, `UNKNOWN` and `PROCESS_EVIDENCE`; a story or chapter is a replaceable read model and cannot promote a claim. Missing reasons remain UNKNOWN, raw events are retained, and GET reads never scan Git or call a model.

ProjectFlow V3.4.5 established the model-protocol foundation, V3.5.0 added bounded repository intake, V3.6.0 added precise SCIP consumption and the minimum evolution bridge, and V3.7.0 added universal evidence discovery and adaptive planning. V3.7.1 closes the plan-to-execution gap with bounded DOC_READER, Git history, Tag, worktree, manifest and Agent-result providers, complete-JSON context packing, diversity-aware evidence selection, honest dimensional history coverage, discovery caches and outbound secret redaction.

V3.7.2 validates that boundary with a ProjectFlow-only internal evaluation harness, an 18-case human ground-truth set, real Model Gateway runs, versioned semantic prompts, an auditable high-value evidence gate, current-result degradation when Final Synthesis fails, complete tool-cache identity, and three thin integration contracts. Evaluation rates and costs remain test/report artifacts and never enter product APIs, snapshots, databases or UI.

V3.7.3 separates connection timeout, Provider request timeout and overall analysis deadline; AUTO/FINITE/UNLIMITED keep cancellation, heartbeat and bounded retry semantics explicit. Production and Eval share one versioned Prompt Builder. Engineering code discovers, classifies and validates Evidence, while the model decides semantic importance, information gaps and applicable views inside objective Tool/View eligibility. No time or Token setting silently reduces necessary Evidence or skips a qualified Final Synthesis.

V3.7.4 strengthened content-first discovery, large-file ranges and shared Agent history. V3.7.5 locks the product constitution into code and tests, makes small Evidence sets and Capability decisions auditable across models, and upgrades Agent Context Package to a task-relevant, revision-aware contract with bounded local revalidation and candidate-only work-result writes.

Authorized Agents can list all loaded projects, query bounded cross-project history, read evidence/knowledge/context packages, and continue from the same durable memory after an Agent or model switch. Reads remain project-isolated and audited. Agents may submit only validated candidates; they cannot directly write strong facts. Hermes exposes the same boundary through read-only MCP tools and project context resources.

V3.8.5 reuses the existing provider-neutral Model Gateway and persistent Job system. Deterministic reconstruction always remains available; bounded semantic windows may improve wording and Primary/Supporting presentation, but validated engineering data still owns membership, chronology, transitions and Evidence. User corrections are durable presentation declarations and never mutate ProjectFact or raw events. This phase includes only a minimal history preview, not the final GUI, and creates no Git Tag or GitHub Release.

## V3.9 Project Continuity Closure

- `ProjectContinuityDelta` exposes bounded added/updated/stale/invalidated Event changes, affected time, safe paths, hashed document/Agent identities, source/presentation revisions and no-op state.
- Stable deterministic lineage continues or separates Story/Thread safely; semantic uncertainty stays new/UNKNOWN/attention instead of a false strong attachment.
- Unaffected historical Chapters are reused exactly and only the affected tail uses the existing representation planner/checkpoints.
- Correction membership refs prove safe additive continuation; rewrite replacement or incomplete legacy proof remains conflict.
- Current Project State is a persisted corrected-history read model exposed natively, through Gateway, Frontend and the 21-tool Hermes adapter. It never becomes ProjectFact.
- Context Package v2 includes Current State revision. Obsidian updates only affected managed notes and remains 0 write on no-op.
- Database Agent Result candidates enter the next explicit History refresh as bounded process evidence; internal writes use a concurrent-safe dirty revision and never start a daemon.

## V3.8.5 Human-readable Project History Quality Closure

- A Technical Atom layer separates raw source events from the default reading unit. Primary changes are shown as complete outcomes; tests, documents, configuration and other supporting work remain available through engineering detail.
- Stories and chapters use Chinese-first action/object/result wording with Before, Change, After, later outcome, unknowns, conflicts and bounded Evidence drill-down. Generic technical templates are rejected by deterministic validation.
- Large histories are processed through stable bounded windows with source/strategy/Prompt/correction-aware cache keys, durable checkpoints, retryable failures and explicit partial/degraded scope. No model call is made per Commit or file.
- A presentation-only correction API supports rename, summary edit, merge/split, Primary/Supporting changes, hide/pin, declared chapters and restore-automatic. The corrected view is shared by Gateway, Agent Context, Hermes, the frontend preview and Obsidian.
- Obsidian CORE stays dense: overview, history index, readable chapters, pinned/corrected stories and selected high-value threads. Full Story/Thread projection remains explicit opt-in.
- V3.8.5 adds no runtime dependency, Tag or Release and does not change the ProjectFact authority boundary.

## V3.8.0 Evidence-backed Project History Reconstruction

- `ProjectHistoryEvent` retains normalized Git, GitHub, filesystem, document, ProjectFact and Agent-result events with stable identity, occurrence time, authority, epistemic state, Evidence, relations and CURRENT/STALE/INVALIDATED rewrite state.
- `ProjectHistorySnapshot` stores replaceable overview, dynamic chapters, change stories and evolution threads. Refresh failure keeps the last successful snapshot and exposes degraded diagnostics.
- The read contract is Overview → Chapters → Change Stories → Evolution Threads → Raw Events → Evidence. The first layer says what happened; SHA, paths and Evidence IDs stay in drill-down.
- Explicit `PROJECT_HISTORY_REFRESH` jobs reuse active-job uniqueness, cancellation, retry, heartbeat, Provider timeout and ownership checks. Reads are model-free and source-scan-free.
- Large histories are paged and reconstructed by deterministic subject/time/transition windows. They do not call a model per Commit; the bounded synthesis request can only rewrite allowed story/chapter wording.
- Gateway and Hermes expose bounded history reads. Obsidian projects Project Overview and Project History as primary notes, preserves existing Capability notes and user-authored content, and provides official URI plus optional Advanced URI deep links.
- The minimal `/projects/{projectId}/history` page validates information hierarchy and stable local links. It is intentionally not a final navigation or visual redesign.
- V3.8.0 adds no runtime dependency and copies no third-party implementation. Research patterns and license boundaries are recorded in the V3.8.0 reports.

## V3.7.5 Cross-model Strong Fact and Agent Context Closure

- The authoritative constitution is `docs/projectflow-v3.7.5-product-constitution.md`; strong facts require `OBSERVED` or independently checked `VERIFIED` plus valid project-bound Evidence.
- Historical reasons, deprecation and technical debt require explicit supporting Evidence. Current structure or model opinion is insufficient.
- Content Map performs bounded streaming discovery and range sampling for ordinary and 80,000-line text/code inputs, preserves source hashes and revisions, and discloses unread or partial ranges.
- Normal projects retain README, manifest, source, test, CI, migration and infrastructure diversity; strange names and unknown text extensions are judged by bounded content signals.
- Portfolio, knowledge, evidence and Context Package v2 APIs are authenticated, task-relevant, bounded, provenance-carrying and read-only.
- Agent Work Results accept changed files, behavior claims, commands, tests, refs, conflicts and UNKNOWN-resolution candidates; they re-read safe project files but never promote Agent claims.
- Local revalidation supports Fact verification, Evidence refresh, bounded range reads, currentness checks and latest-package resolution without a model call or fact mutation.
- Production and evaluation use Prompt contract v3, Semantic Scout v13 and Final Synthesis v7 through the same `ProjectUnderstandingPromptBuilder`.
- Real-provider, calibration, regression, holdout and product E2E results belong only to dated acceptance reports; they are not universal accuracy promises.
- The V3.7.5 frozen result is GLM Holdout 8/8 and product E2E 8/8; DeepSeek Freeze-2 Holdout 8/8 and product E2E 8/8. The first DeepSeek formal failure and the GLM E2E degradation remain disclosed in the model-qualification report.

ProjectFlow 不预设用户的项目是什么。它先发现真实存在的材料，再结合工程分析与有界的大模型推理判断什么值得分析、能知道什么，并基于证据理解项目当前状态及可还原的演进过程。

Git and GitHub retain commits, diffs, files, and branches. ProjectFlow turns that raw development evidence into durable Change Batches, evidence-backed Project Facts, and long-lived Project Memory.

## V3.7.3 Long-running Multi-provider Reliability and Prompt Intelligence

- Large-project analysis taking several minutes is normal and is not itself a failure. Analysis time is not connection timeout.
- Connection timeout remains independently bounded; each Provider request honors its explicit configured timeout; the whole analysis uses AUTO, user-selected FINITE or UNLIMITED semantics.
- UNLIMITED means no ProjectFlow overall-duration termination. It does not mean infinite retries, an unbounded bad connection, missing heartbeat, or disabled cancellation.
- `QUALITY_FIRST` does not silently trade Evidence coverage, necessary deep reads, Final Synthesis or reasoning effort for response speed, Token savings or a hidden SLA. Elapsed time, Token usage, requests and cost are diagnostics, not quality defects.
- Engineering discovery keeps source/module diversity and objective availability. It does not infer HIGH/LOW semantic importance from a filename, extension, README identity or internal sampling score.
- Semantic Scout v10 and Final Synthesis v5 share `ProjectUnderstandingPromptBuilder` across production and Eval. V10 keeps every selected Evidence ID and short summary, carries full samples only for a small cross-category set, asks the model to assess only sources that can change a decision, and treats independent manifest/document/Git/Tag gaps as non-substitutable. Ground Truth and thresholds cannot enter its type boundary.
- Eligible Capability and View registries prevent objectively impossible requests; the model still chooses what is semantically useful and explains the information gap.
- Provider compatible does not mean quality qualified. Differences among real models are part of the product environment, while the Evidence and Prompt contracts remain common.
- V3.7.5 reasoning control is capability-gated: explicitly probed/supported OpenAI Responses and Chat profiles receive `high` on connection, semantic and recovery requests. Reasoning-capable tasks may use the configured Provider ceiling from the first request; the ceiling is a loose safety boundary, not a Token target. Unknown profiles receive no unsupported field.
- V3.7.3 uses GLM `glm-5.2` with OpenAI Responses for the unchanged reliability, semantic and production-chain stress revalidation. The dated result and V3.8 decision are recorded in the V3.7.3 acceptance reports.
- Internal accuracy, hallucination, benchmark and ranking metrics remain test/report artifacts and never enter the product.
- V3.8 may start only if the unchanged quality gate, all eight production-chain cases, security gates and CI all pass.
- V3.7.3 creates no Git Tag or GitHub Release.

## V3.7.2 Real Model Quality and Integration Boundary

- Empty and blank inputs remain deterministic with 0 model calls. Other eligible inputs use one Semantic Scout call, and only a validated high-value evidence decision may trigger a second Final Synthesis call.
- The second-stage decision exposes trigger reasons, skipped reasons and evidence IDs. Short, duplicated, metadata-only or unsupported tool output does not trigger it.
- Final Synthesis timeout, cancellation, invalid schema or Provider failure produces `FAILED_DEGRADED`: Stage 1, validated tool evidence and a current limited profile are preserved instead of rolling back to an old snapshot.
- Tool result identity includes source revision/hash, requested capabilities, deep-read targets, Provider version, execution budgets, strategy version and relevant source signatures.
- The internal harness covers 18 ProjectFlow shapes and boundaries, fixed and real Providers, repeat runs, evidence/tool/view/conflict metrics, second-stage comparison, token/latency diagnostics and sanitized JSON/Markdown artifacts under `backend/target`.
- Semantic Scout v3 and Final Synthesis v3 explicitly treat Agent results as process evidence, usage counters as process metadata, current source as current-state evidence, and stale/conflicting documents as uncertainty rather than facts. They also require atomic shapes, explicit unknowns, stable dimensions and information-gap-driven tool/deep-read choices.
- External integrations are constrained to Evidence Source Adapter, Intelligence Provider Adapter and Projection Adapter contracts. Envelopes are project-bound, revisioned, redacted, bounded and raw-payload-free; they do not promote facts.
- The real Provider result applies only to the dated, human-labelled representative set and prompt/model version; it is not an accuracy promise for arbitrary projects. V3.8, not V3.7.2, owns full Evidence-backed Evolution Reconstruction.
- The funded GLM `glm-5.2` / OpenAI Responses revalidation completed all 38 runs but remains NOT PASSED: 19 transport timeouts, failure rate 0.5000, Tool recall 0.1667, Dynamic View recall 0.0941 and Repeatability 0.4130. The real eight-case production-chain acceptance passed strange document and small script only. V3.8 remains blocked; the earlier DeepSeek pilot and HTTP 402 attempt remain historical evidence.
- V3.7.2 adds no daemon and no Desktop product migration, and it creates no Git Tag or GitHub Release.

## Product Constitution and Integration Boundary

ProjectFlow owns project evidence normalization, Project Facts, current project interpretation, historical coverage, evidence-backed evolution reconstruction, lifecycle capability semantics, trust state, and the user experience that presents those results.

ProjectFlow does not become a coding agent, agent manager, Provider switcher, token billing dashboard, model leaderboard, GitHub/GitLab replacement, Obsidian/Hermes replacement, generic RAG or workflow platform, parser/SCIP producer, updater, CLI version manager, or generic developer-tool control center. Mature external capabilities are connected through thin adapters when they add normalized project evidence or consume ProjectFlow projections; they remain responsible for their own execution, storage, health and product domain.

## V3.7.1 Adaptive Execution and Technical-debt Closure

- The refresh chain is Discover → Scout → Plan → Execute → Validate → Synthesize. The Scout can request only capability names; it cannot emit shell commands.
- FILESYSTEM and existing SCIP results are reused. DOC_READER, MANIFEST, AGENT_RESULT, GIT_HISTORY, GIT_TAG and WORKTREE run through one bounded provider with fixed command arguments, allow-listed evidence IDs, time/item/character limits and cancellation checks.
- A second Model Gateway request is conditional: it runs only when execution produced new high-value evidence. Empty and blank inputs use 0 model requests; ordinary semantic work uses 1; evidence-enriched synthesis uses at most 2.
- `BudgetAwareContextPacker` allocates category budgets before serialization, preserves complete valid JSON, redacts sensitive content, and records selected/dropped counts, characters and truncation reasons.
- Evidence selection keeps category and module diversity under a global cap instead of allowing one large documentation or source cluster to crowd out manifests, tests, CI, migrations and operational evidence.
- Historical Coverage is a weighted, inspectable read model over Git metadata, ProjectFact linkage, tags, historical documents, Agent results, structural snapshots and optional remote evidence. Many commits alone never imply mature history coverage.
- File inspection and shallow evidence sampling reuse signature-keyed in-process caches. Changed-small-set refreshes reopen changed files while retaining unchanged inspection results.
- SCIP producer invocation remains deferred: ProjectFlow never auto-downloads an indexer, silently invokes a project build, or changes a user's runtime. Existing safe SCIP consumption and deterministic fallback remain the production boundary.
- V3.7.1 is still not the final Project Life Reconstruction product or final UI. Timeline and evolution appear only where historical evidence supports them. V3.7.2 adds bounded real-Provider quality evidence without making a universal accuracy promise.

## V3.7 Universal Evidence Intelligence and Adaptive Analysis

- Evidence Discovery inventories code, documents, manifests, configuration, tests, CI, migrations, Agent context/results, Git and unknown text without treating a filename as final semantics.
- Empty directories and blank text use zero model calls. A non-empty TXT or strangely named Markdown file can enter one bounded Semantic Scout request without being forced into a software-architecture template.
- The Scout sees only relative locators, redacted bounded samples and evidence IDs. Unknown evidence references are filtered, generated/vendor content is skipped, and credentials or absolute paths are not sent.
- The Adaptive Analysis Plan validates requested capabilities through a registry. A model cannot construct shell commands or claim unavailable Git, SCIP, document or remote providers.
- Dynamic Project Profile sections are evidence-driven. A project may have no Architecture, Backend, Database, Timeline or Evolution section; document and small-script inputs receive their own applicable views.
- Historical Coverage reports Git availability, earliest/latest evidence, commit and Tag counts, ProjectFact commit coverage, covered/gap periods and limitations. Short histories stay short; no-Git projects remain current-state-only.
- Existing Repository Intake, Structure Index V2, Model Gateway, persistent jobs, Facts, Timeline, Capability Map, Evolution Bridge, Memory Gateway, Hermes and Obsidian remain separate and reusable.
- V3.7 keeps the current explicit-refresh lifecycle. It adds no daemon, watcher, system tray, startup task, Desktop migration, generic RAG framework, parser, Symbol protocol or vector database.

## V3.6 Deep Structural Intelligence and Evolution Bridge

- `CompositeProjectStructureIndexer` retains the bounded manifest/filesystem fallback and consumes a safe project-local `index.scip` through Sourcegraph's official SCIP protobuf.
- Structure Index V2 adds Symbol, Definition, Reference, relation graph, JGraphT PageRank important nodes, relation-driven functional areas, provider diagnostics, coverage, and explicit unsupported areas.
- Official language indexers own syntax and compilation knowledge. ProjectFlow does not invent parsers, language rules, or a cross-language symbol protocol.
- The model receives only a compact allow-listed summary of important nodes, functional areas, relative paths, and evidence IDs. Empty, non-code, no-model, unchanged, and failed-provider paths retain zero-model or deterministic fallback behavior.
- The minimum Evolution Bridge links a real Git parent and commit, an existing Project Fact, changed files, and an affected structural area. It is derived and idempotent; it never becomes a new fact source.
- The focused understanding page keeps trust calibration visible and adds a compact evidence-backed before/change/after view.
- Design decisions, open-source borrowing, architecture, performance, product acceptance, and validation evidence are recorded in the V3.6 reports under `docs/`.

## V3.4.5 Backend Intelligence Foundation

- Model Gateway V2 supports OpenAI Responses, OpenAI Chat Completions and Anthropic Messages through official Java SDKs while retaining one ProjectFlow-owned retry, cancellation, dynamic-budget and recovery policy.
- Provider configuration now records protocol, endpoint override, authentication mode, timeout and capability overrides, with an idempotent legacy Chat-Completions migration and a two-stage compatibility probe.
- Canonical diagnostics normalize finish state, usage, request ID, reasoning presence, truncation and Schema failures without persisting prompts, raw responses, reasoning or credentials.
- Project Memory search and fact evidence tracing are focused read services behind the unchanged Gateway facade.
- Official APIs and compatible relays share protocol adapters; transport reachability and real ProjectFlow task compatibility are reported separately.
- Gemini, automatic background monitoring and Desktop GUI are intentionally excluded. “Automatic Memory Maintenance is the next stage” was a historical V3.4.5 roadmap statement and is superseded; after V3.7.2 acceptance, the next scoped phase is V3.8 Evidence-backed Evolution Reconstruction.
- The real-data Value Audit, architecture decisions, Provider guide and release evidence are in `docs/projectflow-v3.4.5-value-audit.md` and the related V3.4.5 reports.

## V3.4.3 Project Memory Gateway and Hermes MCP

- Project Memory Gateway is the shared read-only semantic layer for Snapshot, occurrence-time Recent Changes, cross-layer Search, Timeline, Capabilities, chronological Evolution, Fact Trace and a budgeted Project Brief.
- ProjectFact remains the only factual source. Timeline, Capability and Evolution results are explicitly derived and keep stable entity IDs and evidence links.
- Recent/history reads use when work actually occurred, not when it was analyzed, recorded or synchronized. Failed derived refreshes retain deterministic facts and the last successful view.
- Every endpoint is project-owned, compact by default, paged/bounded, model-free on GET and safely audited without storing full private queries.
- `integrations/hermes/projectflow_mcp.py` exposes thirteen narrow, idempotent, non-destructive stdio tools, including portfolio search, Context Package v2, Evidence and knowledge reads. It accepts only a loopback ProjectFlow backend in this release and contains no generic REST or write tool.
- Hermes is a consumer, never a fact source. Local stdio is the primary integration mode; remote access remains disabled by default and requires a separately secured future design.

## V3.4.4 Obsidian Projection and Sync

- Obsidian is a knowledge projection, not a database mirror. It consumes the same Project Memory Gateway semantics as Hermes and never becomes a source of truth.
- Default `CORE` output contains one Overview, monthly Timeline notes, stable Capability notes, compact monthly Fact indexes, and navigation indexes. It does not generate one file per fact.
- `EXTENDED` adds high-value facts referenced by capability evolution or attention; `FULL_FACTS` creates individual Fact notes only when explicitly selected.
- The repository-local `run-projectflow-obsidian.ps1` command supports `validate`, `dry-run`, `status`, and one-shot `sync` against an existing Vault and a dedicated managed root.
- Every note carries stable entity/version/hash metadata. Sync is incremental, atomic, path-safe, manifest-backed, and conflict-aware; unchanged files receive zero writes.
- ProjectFlow replaces only its marked managed block. User frontmatter and authored content outside that block are retained, while managed edits, damaged markers, identity conflicts, traversal, symlink, or junction escape stop safe overwrite.
- Capability rename keeps the stable note path. Capability merge retains the old note and writes a redirect to preserve history and backlinks.
- Projection reads existing Fact, Timeline, Capability, and Evolution results through the Gateway and never regenerates them with a model.

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
- Hermes and Obsidian consume Facts, Timeline, Capabilities, and Evolutions through Project Memory Gateway without becoming new fact sources.

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
- Timeline Theme is not Project Capability. Capability Map, Hermes reads, and Obsidian projection consume facts through their own traceable layers.
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
- Empty content combined with `finish_reason=length`, exhausted completion tokens, or a reasoning field is classified as exhausted output and triggers one bounded recovery. Reasoning-capable profiles keep high effort and may reuse the configured Provider ceiling; reasoning text is never stored or returned, and only presence/length/usage diagnostics are retained.
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

## V3.4.4 Workflow

ProjectFlow is not a Kanban board, daily-report generator, or hosted PR/CI system. Its primary workflow is:

1. Bind local project and required model / GitHub access.
2. Analyze new changes.
3. ProjectFlow records Project Facts.
4. History backfill completes uncovered Git history.
5. Timeline organizes real occurrence history.
6. Capability Map maintains long-lived capabilities.
7. Project Memory Gateway exposes stable read semantics.
8. Hermes queries project memory on demand.
9. Obsidian receives curated long-term knowledge projection.
10. Future frontend and external consumers reuse the same backend business semantics.

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
| Project Memory Gateway | Project-owned, compact and bounded business read semantics shared by external consumers |
| Project Snapshot | Current project position, factual coverage, recent changes, capabilities and health |
| Project Memory Search | Typed SOURCE/DERIVED search over facts, timeline, capabilities and evolutions |
| Memory Context Pack | Budgeted Project Brief assembled from stable read models without exposing internals |
| MCP Read Adapter | Nine-tool local stdio adapter that delegates read-only work to Project Memory Gateway |
| Obsidian Projection | Curated long-term Markdown knowledge view derived from Project Memory Gateway |
| Projection Profile | `CORE`, `EXTENDED`, or explicit `FULL_FACTS` output scope |
| Managed Root | Dedicated Vault subfolder within which ProjectFlow may manage marked content |
| Projection Manifest | Recoverable entity/path/version/hash index for incremental sync |
| Sync Plan | Deterministic CREATED/UPDATED/UNCHANGED/REDIRECTED/ARCHIVED/CONFLICT/ERROR plan |
| Sync Conflict | Safe refusal to overwrite when identity, managed content, markers, or path trust fails |
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

# 可选真实 DeepSeek：通用入口小输入 + ProjectFlow 代表集
cd ..\backend
$env:PROJECTFLOW_RUN_REAL_MODEL='true'
$env:DEEPSEEK_API_KEY='<安全测试 Key>'
mvn.cmd -Dtest=RealDeepSeekIT test
mvn.cmd -Dtest=ProjectFlowRealModelEvalIT test
# 归一化本地结果位于 target/projectflow-eval/real，默认被 Git 忽略
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
