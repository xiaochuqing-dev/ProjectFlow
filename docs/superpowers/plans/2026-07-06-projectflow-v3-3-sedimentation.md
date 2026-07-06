# ProjectFlow V3.3 Sedimentation Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade ProjectFlow from the today-based evidence flow to a cursor-based pending-change, development-segment, suggested-sediment, confirmed-sediment workflow without breaking existing entry points or data.

**Architecture:** Add explicit cursor, batch, segment, and sediment persistence while extending `ProjectChange` as the backward-compatible suggestion object. Keep `/scan`, WorkSession, EvidenceBundle, and legacy AiSuggestion operational; expose additive V3.3 API fields and render the new workflow first. Local Git remains authoritative, model output is bounded and validated, and GitHub CLI is optional enrichment.

**Tech Stack:** Java 17+, Spring Boot 3.5, Spring Data JPA, H2/PostgreSQL, Next.js 16, React 19, TypeScript, Node test runner, Maven.

---

## File map

New backend files own one responsibility each:

- `entity/ProjectReviewCursor.java`: last user-confirmed Git boundary.
- `entity/ChangeBatch.java`: one persisted scan range and lifecycle.
- `entity/DevelopmentSegment.java`: human-readable grouping of facts.
- `entity/ProjectSediment.java`: confirmed reusable project knowledge.
- `service/PendingChangeScanService.java`: range selection, batch creation, idempotency, and cursor fallback.
- `service/DevelopmentSegmentationService.java`: deterministic grouping and optional model enrichment.
- `service/ProjectSedimentService.java`: four confirmation actions and cursor advancement.
- `service/AgentBridgeHealthService.java`: protocol health reporting without mutation.
- `service/GitHubCliService.java`: fixed-command optional GitHub enrichment.
- `controller/ProjectSedimentController.java`: V3.3 batch, segment, sediment, and confirmation endpoints.

Existing files retain compatibility and route ownership:

- `WorkSessionScanService` continues producing legacy sessions but delegates range facts to the V3.3 scanner.
- `ProjectChange` remains the pending suggestion representation.
- `ProjectAgentBridgeService` writes V3.3 protocol files and scans both new and legacy result paths.
- `api.ts` remains the frontend contract hub.

### Task 1: Persist the review cursor, scan batch, segments, and confirmed sediments

**Files:**
- Create: `backend/src/main/java/com/projectflow/entity/ProjectReviewCursor.java`
- Create: `backend/src/main/java/com/projectflow/entity/ChangeBatch.java`
- Create: `backend/src/main/java/com/projectflow/entity/ChangeBatchStatus.java`
- Create: `backend/src/main/java/com/projectflow/entity/DevelopmentSegment.java`
- Create: `backend/src/main/java/com/projectflow/entity/DevelopmentSegmentStatus.java`
- Create: `backend/src/main/java/com/projectflow/entity/EvidenceConfidence.java`
- Create: `backend/src/main/java/com/projectflow/entity/ProjectSediment.java`
- Create: matching repositories under `backend/src/main/java/com/projectflow/repository/`
- Modify: `backend/src/main/java/com/projectflow/entity/ProjectChange.java`
- Create: `backend/src/main/java/com/projectflow/entity/SedimentAction.java`
- Test: `backend/src/test/java/com/projectflow/V33PersistenceTest.java`

- [ ] **Step 1: Write the failing persistence test**

Create a `@DataJpaTest` that saves one cursor, one batch, one segment, one enhanced ProjectChange, and one sediment. Assert the project-scoped repository queries return the same relationships and list fields.

```java
assertThat(cursorRepository.findByProjectId(projectId)).get()
    .extracting(ProjectReviewCursor::getLastReviewedCommitSha).isEqualTo("base-sha");
assertThat(batchRepository.findFirstByProjectIdOrderByScanStartedAtDesc(projectId)).get()
    .extracting(ChangeBatch::getStatus).isEqualTo(ChangeBatchStatus.PENDING);
assertThat(segmentRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()))
    .extracting(DevelopmentSegment::getTitle).containsExactly("Agent protocol recovery");
assertThat(change.getSuggestedAction()).isEqualTo(SedimentAction.MERGE_EXISTING);
assertThat(sedimentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)).hasSize(1);
```

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -q -Dtest=V33PersistenceTest test`

Expected: compilation failure because the V3.3 entities and repositories do not exist.

- [ ] **Step 3: Add the minimal entities and repositories**

Use UUID identifiers, `@Enumerated(EnumType.STRING)`, text columns for long content, existing `StringListConverter` for string lists, and unique `project_id` on the cursor. Add nullable V3.3 fields to `ProjectChange` so old rows remain valid:

```java
private UUID developmentSegmentId;
@Enumerated(EnumType.STRING)
private SedimentAction suggestedAction;
private UUID targetSedimentId;
@Column(columnDefinition = "text")
private String problemSolved;
@Convert(converter = StringListConverter.class)
private List<String> evidenceRefs;
@Enumerated(EnumType.STRING)
private EvidenceConfidence confidence;
private boolean needsUserReview;
```

- [ ] **Step 4: Run GREEN**

Run: `mvn.cmd -q -Dtest=V33PersistenceTest test`

Expected: PASS.

- [ ] **Step 5: Commit the persistence slice**

```powershell
git add backend/src/main/java/com/projectflow/entity backend/src/main/java/com/projectflow/repository backend/src/test/java/com/projectflow/V33PersistenceTest.java
git commit -m "feat: add v3.3 sedimentation domain model"
```

### Task 2: Replace the today boundary with a safe pending-change range

**Files:**
- Create: `backend/src/main/java/com/projectflow/service/PendingChangeScanService.java`
- Create: `backend/src/main/java/com/projectflow/service/GitChangeCollector.java`
- Create: `backend/src/main/java/com/projectflow/dto/V33WorkflowDtos.java`
- Modify: `backend/src/main/java/com/projectflow/service/WorkSessionScanService.java`
- Modify: `backend/src/main/java/com/projectflow/controller/WorkSessionScanController.java`
- Modify: `backend/src/main/java/com/projectflow/dto/V2ProjectDtos.java`
- Test: `backend/src/test/java/com/projectflow/PendingChangeScanControllerTest.java`

- [ ] **Step 1: Write failing scan-range tests**

Cover four independent behaviors using temporary Git repositories:

```java
@Test void firstScanUsesAtMostThirtyRecentCommitsAndReportsFirstScan() { }
@Test void nextScanStartsAfterTheConfirmedCursor() { }
@Test void unreachableCursorFallsBackByTimeAndReturnsWarning() { }
@Test void scanningTheSameRangeReusesTheExistingBatch() { }
```

Assert the response contains `batch`, `segments` (initially empty), `firstScan`, and warnings while retaining `sessions`.

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -q -Dtest=PendingChangeScanControllerTest test`

Expected: FAIL because `/scan` has no batch/range contract and still uses midnight.

- [ ] **Step 3: Implement fixed-argument Git collection**

`GitChangeCollector` must execute only fixed argument lists through `ProcessBuilder`, never a shell string. Range rules:

```java
if (cursor == null) return collect(List.of("log", "--max-count=30", ...), true);
if (isAncestor(cursor.sha(), head)) return collect(List.of("log", cursor.sha() + ".." + head, ...), false);
return collect(List.of("log", "--since=" + cursor.lastReviewedAt(), "--max-count=200", ...), false)
    .withWarning("检测到提交历史变化，本次将按最近未整理时间重新扫描。");
```

Exclude `.git`, `.projectflow`, dependency, build, generated, binary, and archive paths. Represent commits and files as immutable DTO records.

- [ ] **Step 4: Persist idempotent batches and extend `/scan`**

Create/reuse a batch by project, branch, base and head. Keep legacy WorkSession creation, but remove the `LocalDate.now()` boundary and derive the committed WorkSession from collected facts. Do not update the review cursor during scan.

- [ ] **Step 5: Run GREEN and regression test**

Run:

```powershell
mvn.cmd -q -Dtest=PendingChangeScanControllerTest,WorkSessionScanControllerTest test
```

Expected: PASS; legacy sessions remain available and new scan ranges are cursor-based.

- [ ] **Step 6: Commit the scanner slice**

```powershell
git add backend/src/main/java/com/projectflow backend/src/test/java/com/projectflow/PendingChangeScanControllerTest.java backend/src/test/java/com/projectflow/WorkSessionScanControllerTest.java
git commit -m "feat: scan pending changes from review cursor"
```

### Task 3: Group facts into validated development segments

**Files:**
- Create: `backend/src/main/java/com/projectflow/service/DevelopmentSegmentationService.java`
- Create: `backend/src/main/java/com/projectflow/service/SegmentEvidenceValidator.java`
- Modify: `backend/src/main/java/com/projectflow/service/PendingChangeScanService.java`
- Modify: `backend/src/main/java/com/projectflow/service/ModelGatewayService.java` only if a small reusable strict-JSON call is missing
- Test: `backend/src/test/java/com/projectflow/DevelopmentSegmentationServiceTest.java`

- [ ] **Step 1: Write failing segmentation tests**

Use real atom DTOs and assert bounded output:

```java
assertThat(service.segment(smallBatch)).hasSizeBetween(1, 3);
assertThat(service.segment(mediumBatch)).hasSizeBetween(2, 5);
assertThat(service.segment(largeBatch)).hasSizeBetween(3, 8);
assertThat(service.segment(batchWithDocsAndFeature).getFirst().getAffectedFiles())
    .contains("README.md", "backend/src/main/java/example/Feature.java");
```

Add a validator case where a model invents a commit and file; assert both are removed and an empty-evidence segment is rejected.

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -q -Dtest=DevelopmentSegmentationServiceTest test`

Expected: compilation failure because segmentation services do not exist.

- [ ] **Step 3: Implement deterministic grouping first**

Group by normalized top-level module, conventional-commit topic, file overlap, and time gap. Merge docs/tests/config with the nearest feature when files or topic overlap. For more than 30 atoms, create summaries in chunks of 25 before reducing to 3–8 segments.

- [ ] **Step 4: Add optional strict-JSON enrichment**

Send only bounded atom summaries. Parse a record matching:

```java
record SegmentCandidate(
    String segmentTitle,
    String plainSummary,
    List<String> includedAtomIds,
    List<String> mainChanges,
    String userVisibleValue,
    List<String> evidenceRefs,
    EvidenceConfidence confidence
) {}
```

Intersect atom IDs and evidence refs with facts before persistence. Any model exception, malformed JSON, empty evidence, or out-of-range segment count returns the deterministic result with a warning.

- [ ] **Step 5: Run GREEN**

Run: `mvn.cmd -q -Dtest=DevelopmentSegmentationServiceTest,PendingChangeScanControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit the segmentation slice**

```powershell
git add backend/src/main/java/com/projectflow/service backend/src/test/java/com/projectflow/DevelopmentSegmentationServiceTest.java
git commit -m "feat: group pending changes into development segments"
```

### Task 4: Confirm suggestions through new, merge, evidence-only, or ignore actions

**Files:**
- Create: `backend/src/main/java/com/projectflow/service/ProjectSedimentService.java`
- Create: `backend/src/main/java/com/projectflow/controller/ProjectSedimentController.java`
- Modify: `backend/src/main/java/com/projectflow/service/EvidenceDraftChangeService.java`
- Modify: `backend/src/main/java/com/projectflow/service/ProjectChangeReviewService.java`
- Modify: `backend/src/main/java/com/projectflow/controller/ProjectChangeController.java`
- Modify: `backend/src/main/java/com/projectflow/dto/V33WorkflowDtos.java`
- Test: `backend/src/test/java/com/projectflow/ProjectSedimentControllerTest.java`

- [ ] **Step 1: Write failing action tests**

Create separate tests for:

```java
@Test void newActionCreatesOneConfirmedSediment() { }
@Test void mergeActionUpdatesTheTargetWithoutCreatingADuplicate() { }
@Test void evidenceOnlyAddsEvidenceWithoutRewritingTheDescription() { }
@Test void ignoreCreatesNoSediment() { }
@Test void cursorAdvancesOnlyAfterEverySuggestionInTheBatchIsResolved() { }
```

Also assert a target sediment from another user/project returns 404 and a nonexistent evidence ref returns 400.

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -q -Dtest=ProjectSedimentControllerTest test`

Expected: FAIL because confirm and sediment endpoints do not exist.

- [ ] **Step 3: Generate evidence-backed ProjectChange candidates**

For each segment, create at most one pending ProjectChange with `sourceType=DEVELOPMENT_SEGMENT`, a default action, evidence refs, confidence, and `needsUserReview=true`. Prefer merge only when an existing sediment has the same normalized title/problem; otherwise use new. Documentation/test-only changes use evidence-only only when a target exists.

- [ ] **Step 4: Implement transactional confirmation**

Expose `POST /api/project-changes/{id}/confirm` with:

```json
{ "action": "MERGE_EXISTING", "targetSedimentId": "uuid" }
```

Validate ownership and allowed transitions. In one transaction update the ProjectChange, segment, sediment/fact sources, batch state, and cursor eligibility. Existing `/accept` delegates to `NEW_SEDIMENT`; `/ignore` delegates to `IGNORE`.

- [ ] **Step 5: Implement sediment read/update endpoints**

Return source counts and collapsed evidence summaries. `PATCH /api/project-sediments/{id}` may update only developer notes and user-editable title/summary fields; it must not mark inferred facts as confirmed automatically.

- [ ] **Step 6: Run GREEN and legacy review regression**

Run:

```powershell
mvn.cmd -q -Dtest=ProjectSedimentControllerTest,WorkSessionScanControllerTest,V2CoreControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the confirmation slice**

```powershell
git add backend/src/main/java/com/projectflow backend/src/test/java/com/projectflow/ProjectSedimentControllerTest.java
git commit -m "feat: confirm suggestions into project sediments"
```

### Task 5: Upgrade Agent result protocol and add health checks

**Files:**
- Modify: `backend/src/main/java/com/projectflow/service/ProjectAgentBridgeService.java`
- Create: `backend/src/main/java/com/projectflow/service/AgentBridgeHealthService.java`
- Modify: `backend/src/main/java/com/projectflow/controller/ProjectAgentBridgeController.java`
- Modify: `backend/src/main/java/com/projectflow/dto/V2ProjectDtos.java`
- Test: `backend/src/test/java/com/projectflow/AgentBridgeV33ControllerTest.java`

- [ ] **Step 1: Write failing protocol tests**

Verify initialization creates `.projectflow/AGENT_PROTOCOL.md`, `agent-results`, and `templates`; inserts one marked block at the top of an existing AGENTS.md without changing its original body; repeated initialization is idempotent; scans structured result JSON; still scans legacy inbox; and reports missing/outdated files through the health endpoint.

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -q -Dtest=AgentBridgeV33ControllerTest test`

Expected: FAIL on new protocol paths and health endpoint.

- [ ] **Step 3: Implement the V3.3 protocol safely**

Use repository-relative paths in generated examples. Reject absolute `keyFiles`, malformed JSON, oversized result files, and traversal outside `agent-results`. Record `not_run` literally when verification is absent. Scan the new directory first, then legacy paths without duplicating processed results.

- [ ] **Step 4: Implement read-only health reporting**

Return local path access, same-repository status, protocol/result/AGENTS presence, entry marker, protocol version, and detected optional agent-rule files. Health checks do not mutate files.

- [ ] **Step 5: Run GREEN**

Run: `mvn.cmd -q -Dtest=AgentBridgeV33ControllerTest,V2CoreControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit the bridge slice**

```powershell
git add backend/src/main/java/com/projectflow backend/src/test/java/com/projectflow/AgentBridgeV33ControllerTest.java
git commit -m "feat: upgrade agent result protocol to v3.3"
```

### Task 6: Add fail-open GitHub CLI enrichment

**Files:**
- Create: `backend/src/main/java/com/projectflow/service/GitHubCliService.java`
- Create: `backend/src/main/java/com/projectflow/controller/ProjectGitHubController.java`
- Modify: `backend/src/main/java/com/projectflow/dto/V33WorkflowDtos.java`
- Test: `backend/src/test/java/com/projectflow/GitHubCliServiceTest.java`

- [ ] **Step 1: Write failing status tests**

Inject a package-private fixed-command executor and cover installed/authenticated, not installed, not logged in, non-GitHub remote, timeout, and malformed `gh repo view` JSON. Assert every failure returns warnings rather than throwing.

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -q -Dtest=GitHubCliServiceTest test`

Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Implement fixed commands and timeout**

Only execute:

```text
gh --version
gh auth status
git remote -v
git branch --show-current
gh repo view --json nameWithOwner,url,defaultBranchRef,primaryLanguage,visibility
```

Never pass `--show-token`, never return command stderr verbatim if it may contain credentials, and kill timed-out processes. Normalize HTTPS and SSH GitHub remotes before generating commit URLs.

- [ ] **Step 4: Add ownership-scoped endpoints**

Add GET status and POST refresh endpoints. Refresh performs the same bounded read and does not persist secrets.

- [ ] **Step 5: Run GREEN and scanner regression**

Run: `mvn.cmd -q -Dtest=GitHubCliServiceTest,PendingChangeScanControllerTest test`

Expected: PASS and scanner behavior is independent of GitHub CLI state.

- [ ] **Step 6: Commit the integration slice**

```powershell
git add backend/src/main/java/com/projectflow backend/src/test/java/com/projectflow/GitHubCliServiceTest.java
git commit -m "feat: add optional github cli enrichment"
```

### Task 7: Make pending changes and sediment confirmation the primary UI

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/components/dashboard/PendingChangesPanel.tsx`
- Delete: `frontend/src/components/dashboard/EvidenceFlowPanel.tsx`
- Modify: `frontend/src/app/dashboard/page.tsx`
- Modify: `frontend/src/components/dashboard/DashboardStats.tsx`
- Modify: `frontend/src/components/dashboard/ProjectAccessCard.tsx`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/app/tasks/page.tsx`
- Modify: `frontend/src/components/tasks/ChangeReviewList.tsx`
- Modify: `frontend/src/components/tasks/ChangeReviewSidebar.tsx`
- Test: `frontend/tests/v33-pending-workflow.test.mjs`

- [ ] **Step 1: Write a failing frontend contract test**

Assert source contains the retained project/Zip/local binding controls, `分析新变化`, `待整理变更`, `开发推进段`, four actions, and a collapsed `旧版候选`; assert primary panel/task files no longer contain `刷新今日开发`, `开发成果审查`, or default-visible `结构化变更`.

- [ ] **Step 2: Run RED**

Run: `node --test tests/v33-pending-workflow.test.mjs`

Expected: FAIL on missing new vocabulary and old primary vocabulary.

- [ ] **Step 3: Add typed API contracts**

Define `ChangeBatch`, `DevelopmentSegment`, `ProjectSediment`, `SedimentAction`, `AgentBridgeHealth`, and `GitHubStatus`, plus scan/confirm/list/update calls. Preserve existing exports.

- [ ] **Step 4: Replace the dashboard panel**

Render segment title, plain summary, commit/file/Agent-result counts, suggestion count, and status. Put hashes, paths, and source identifiers in a collapsed `<details>` element. Keep add project, Zip import, local binding, model config, and project selection unchanged.

- [ ] **Step 5: Reframe `/tasks`**

Primary list uses enhanced ProjectChange records and posts one of four actions. Require a target selection for merge/evidence-only. Keep AiSuggestion under a closed-by-default `旧版候选` section.

- [ ] **Step 6: Run GREEN and build**

Run:

```powershell
node --test tests/v33-pending-workflow.test.mjs
npm.cmd run build
```

Expected: both exit 0.

- [ ] **Step 7: Commit the primary UI slice**

```powershell
git add frontend/src frontend/tests/v33-pending-workflow.test.mjs
git commit -m "feat: surface pending changes and sediment confirmation"
```

### Task 8: Add sediment detail, hide unsupported subjective fields, and expose connection health

**Files:**
- Modify: `frontend/src/app/project-intelligence/page.tsx`
- Modify: `frontend/src/components/project-intelligence/ProjectAssetPanels.tsx`
- Create: `frontend/src/app/project-sediments/[sedimentId]/page.tsx`
- Modify: `frontend/src/app/settings/page.tsx`
- Modify: `frontend/src/app/ai-review/page.tsx`
- Modify: `frontend/src/app/dev-logs/page.tsx`
- Test: `frontend/tests/v33-sediment-detail.test.mjs`

- [ ] **Step 1: Write failing visibility tests**

Assert project intelligence maps confirmed sediments rather than every `fieldConfig`; empty unsupported fields are filtered; developer notes are explicitly labeled; evidence details are collapsed; settings displays Agent protocol and GitHub CLI states without making GitHub mandatory.

- [ ] **Step 2: Run RED**

Run: `node --test tests/v33-sediment-detail.test.mjs`

Expected: FAIL on the current all-field rendering.

- [ ] **Step 3: Implement the sediment overview and detail**

First screen fields: title, one-line summary, problem solved, status, source counts, updated time, and reuse exits. Keep raw IDs, full paths, payloads, hashes, and logs inside evidence details. Developer notes use the sediment PATCH endpoint and are never presented as AI-confirmed facts.

- [ ] **Step 4: Synchronize secondary page language**

Use “项目沉淀” in daily UI. Keep “项目资产” only in product-level positioning. Remove “托管块” everywhere. Do not make daily review the pending-change boundary.

- [ ] **Step 5: Run GREEN and all frontend tests**

Run:

```powershell
node --test tests/*.test.mjs
npm.cmd run build
```

Expected: all tests and build pass.

- [ ] **Step 6: Commit the sediment UI slice**

```powershell
git add frontend/src frontend/tests/v33-sediment-detail.test.mjs
git commit -m "feat: add evidence-backed project sediment views"
```

### Task 9: Update launchers, protocol files, README, AGENTS, and project context

**Files:**
- Create: `start.bat`
- Modify: `start-projectflow.bat`
- Modify: `start-projectflow-embedded.bat`
- Modify: `start-projectflow-embedded.ps1`
- Modify: `README.md`
- Modify: `PROJECT_CONTEXT.md`
- Modify: `AGENTS.md`
- Create: `.projectflow/AGENT_PROTOCOL.md`
- Create: `.projectflow/agent-results/.gitkeep`
- Modify: `.projectflow/context/project-profile.md`
- Modify: `.projectflow/context/requirements.md`
- Modify: `.projectflow/context/confirmed-decisions.md`
- Modify: `.projectflow/context/known-risks.md`
- Modify: `.projectflow/context/update-history.md`
- Test: `frontend/tests/v33-documentation.test.mjs`

- [ ] **Step 1: Write failing documentation checks**

Assert V3.3 positioning, the four-stage workflow, local Git/Agent result/GitHub CLI roles, retained core entry points, new protocol paths, and absence of `托管块`. Assert `start.bat` delegates to the supported embedded launcher and prints V3.3 plus the access URL.

- [ ] **Step 2: Run RED**

Run: `node --test tests/v33-documentation.test.mjs`

Expected: FAIL because the repository still describes V3.2 and old protocol paths.

- [ ] **Step 3: Update launchers without changing startup architecture**

Create a thin `start.bat` that delegates to the existing embedded launcher. Update banners and dependency error text only; keep Maven/npm process orchestration in the existing scripts.

- [ ] **Step 4: Update durable documentation and protocol**

Preserve existing AGENTS content and insert the marked V3.3 context block. Document `not_run`, repository-relative paths, no exaggerated completion, and result JSON/Markdown layout. Keep legacy `agent-protocol.md` as a short compatibility pointer rather than deleting it.

- [ ] **Step 5: Run GREEN**

Run: `node --test tests/v33-documentation.test.mjs`

Expected: PASS.

- [ ] **Step 6: Commit the documentation slice**

```powershell
git add start.bat start-projectflow*.bat start-projectflow-embedded.ps1 README.md PROJECT_CONTEXT.md AGENTS.md .projectflow frontend/tests/v33-documentation.test.mjs
git commit -m "docs: align projectflow context with v3.3"
```

### Task 10: Full regression, security review, and acceptance audit

**Files:**
- Modify only files required by discovered regressions
- Create: `.projectflow/agent-results/2026-07-06-projectflow-v3-3/result.json`
- Create: `.projectflow/agent-results/2026-07-06-projectflow-v3-3/summary.md`

- [ ] **Step 1: Run the complete backend suite**

Run: `mvn.cmd -q test`

Expected: exit 0 with no test failures.

- [ ] **Step 2: Run all frontend tests and production build**

Run:

```powershell
node --test tests/*.test.mjs
npm.cmd run build
```

Expected: exit 0 for both commands.

- [ ] **Step 3: Audit security boundaries**

Search for shell execution, tokens, unsafe absolute paths, unscoped project lookups, raw HTML, and new endpoints without ownership checks:

```powershell
rg -n "show-token|Runtime\.getRuntime|cmd /c|powershell|dangerouslySetInnerHTML|findById\(" backend/src/main/java frontend/src
```

Confirm GitHub and Git invocations use fixed `ProcessBuilder` argument arrays and bounded timeouts; all project resources verify ownership; Agent result paths stay below the bound repository root.

- [ ] **Step 4: Audit every acceptance criterion**

Re-read the enhanced V3.3 prompt and map all 28 acceptance points to code, automated tests, or explicit documented fallback. Fix any uncovered gap through a new RED/GREEN cycle.

- [ ] **Step 5: Inspect final diff and write Agent result**

Run:

```powershell
git status --short
git diff --check
git log --oneline --decorate -12
```

Write repository-relative changed files, actual verification commands/results, unfinished items, and sediment candidates. Use `not_run` for anything not executed.

- [ ] **Step 6: Create the final feature commit if needed**

```powershell
git add .projectflow/agent-results
git commit -m "feat: upgrade ProjectFlow to v3.3 sedimentation workflow"
```

- [ ] **Step 7: Invoke `superpowers:finishing-a-development-branch`**

Re-run fresh verification before any completion claim, then integrate without overwriting the user's dirty master worktree.
