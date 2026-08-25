# CI quality gates

## V3.9 Project Continuity Closure

V3.9 keeps Backend/H2, PostgreSQL 16, frontend TypeScript/contracts/build, Playwright, Hermes, Obsidian, root launcher and sensitive-content as blocking gates. It adds the frozen 30-case continuity Ground Truth, no-op/identity/correction/current-state/context/projector invariants, three-Provider real acceptance, T0–T7 Dogfood and genuine human continuity review.

Real acceptance remains explicit workflow dispatch and uses the existing protected Secret with the exact Luna Responses/max, DeepSeek Chat/max and Qwen Messages/max profiles. A later pass never deletes an earlier failure. Human review is not automated. No Tag or Release job is authorized.

For production/eval source head `eb38c78fe70d3cf9280e716f7fc906d8729b15b1`, push run `32666198144` and PR run `32666201528` passed every ordinary blocking job. Explicit affected-set rerun `32666372066` repeated those jobs and passed all three 19/19 qualifications, all three 9/9 Chapter regressions and all three 3/3 V3.9 continuity suites. Earlier run `32659635453` remains recorded as failed because its Qwen Chapter regression exposed one unsupported claim; later success does not delete that evidence. The production/eval source head stays authoritative even though a later evidence-only commit may add sanitized artifacts and reports without changing backend, frontend, integrations or workflow code.

## V3.8.5 Final Chapter Closure execution

Final Chapter Closure uses one same-head, explicitly dispatched three-Provider matrix: GPT 5.6 Luna `gpt-5.6-luna` over `OPENAI_RESPONSES`, DeepSeek V4 Flash `deepseek-v4-flash` over `OPENAI_CHAT_COMPLETIONS`, and Qwen3.7 Plus `qwen3.7-plus` over `ANTHROPIC_MESSAGES`. All three profiles use `max` reasoning. No alternative Qwen model may be substituted.

Use `run_real_model=true`, `real_model_scope=affected` and `real_model_provider=all`. Qualification runs the frozen 19-case Calibration/Holdout set. The dependent Chapter scope runs large coherent/heterogeneous Chapters, repair, correction, non-code presentation/report/data, review fixtures and current ProjectFlow Dogfood. A qualification case may perform at most one explicit persisted refresh after unresolved window/checkpoint work; successful windows remain cached, the retry count and initial/final degradation are retained, and final qualification still requires zero unresolved, rejected or unsafe output. Chapter completion also stops after one unchanged failed-checkpoint retry instead of repeatedly billing the same non-progressing state. Failed Provider calls are included in request, Token and latency totals. This is a product retry exercise, not a third request inside one logical model call.

Only sanitized normalized JSON is uploaded. The Final Chapter freeze requires all six qualification/scenario artifacts from the same code head, exact Provider/model/protocol/effort profiles, every scenario PASS, all security persistence flags false and Chapter `scenarioScope=chapter`. Automated green status stops at `HUMAN_REVIEW_REQUIRED / NOT PASS`; it never fills the worksheet or authorizes Ready, merge, backfill, Tag, Release or cleanup.

## V3.8.5 RC3 execution

The final RC3 evidence head is merge-ineligible until backend/H2, PostgreSQL 16 Testcontainers, frontend production build/contracts, Playwright, Hermes, Obsidian and sensitive-content all pass with the Round 3 manifest and worksheet present. A real-model workflow run before those files exist may legitimately fail backend/PostgreSQL artifact assertions and is not the final static CI authority.

The historical RC3 dispatch used `real_model_provider=both` for GLM `glm-5.2` Responses/max and DeepSeek Flash Chat/max. It is retained only to explain old evidence and is not the Final Chapter command. Current final closure uses the exact three-profile `real_model_provider=all` matrix above; qualification and scenario failures, fallbacks, repairs, token usage and latency remain evidence, and a later pass never deletes an earlier failure.

RC3 adds explicit report checks for claim Evidence attribution, deterministic Ground Truth execution, title quality and the Round 3 manifest. The Round 3 check stays red until the qualified Provider outputs are normalized and exactly 30 Story/8 Chapter with blank human fields are frozen. Human approval is never a CI job and automated green checks cannot change `PENDING_HUMAN_REVIEW_ROUND3` into PASS.

Frozen Markdown/JSON may check out as LF on Linux and CRLF on Windows. Round 2 immutability is therefore enforced by its canonical-LF hashes plus an allow-list of the two already recorded raw LF/CRLF hashes; arbitrary content or mixed-byte changes still fail. This portability rule does not rewrite the frozen artifacts.

Story prompt v12 and real artifact schemas v4/v3 expose `deterministicTitleFallbackCount`. A safe model title/summary that omits an explicit result retains the already validated deterministic pair with `MODEL_VALIDATED_WITH_DETERMINISTIC_TITLE`; the count is evidence, not a hidden repair or a lowered Title AOR threshold.

The final evidence commit is eligible for human review only when every blocking job is green with the immutable Round 2 files and the new blank-field Round 3 files present. Real run `31532558352` proved both affected Provider jobs, frontend, Playwright, sensitive-content, Hermes and Obsidian; its backend/PostgreSQL failures are historical evidence from the earlier head where Round 2 files were still absent. They are not the final CI authority, and automated green checks do not authorize merge.

`workflow_dispatch` supports `real_model_scope=correction` for the bounded indexed-placeholder/correction chain. In that scope, full qualification, Dogfood and non-code baselines are explicitly reused and no duplicate model call is made; both Providers still receive their exact configured protocol and reasoning effort. The generated affected artifact states `scenarioScope=correction` and cannot be presented as a full 11-scenario run.

V3.8.5 adds deterministic quality gates for multi-level history compression, Chinese-first presentation, Primary/Supporting roles, correction overlays, window checkpoint recovery and Obsidian CORE density. The acceptance artifact records window/cache/checkpoint counts and unprocessed scope without persisting Prompt, raw response, reasoning or credentials.

RC2 requires the backend job to emit the new portability, minimal-schema, role-graph, language, taxonomy and corrected-view test reports. The optional real-provider job runs both GLM Responses and DeepSeek Chat Completions only when explicitly dispatched with protected Secrets; missing Secrets fails before any request. `DeepSeekDogfoodRegressionTest` and `GLMDogfoodRegressionTest` validate the sanitized scenario artifact after the original real run instead of issuing duplicate calls.

The RC3 closure requires the truthfulness contracts, Round 2 immutable-failure check, Round 3 freeze check and acceptance reports. Current profiles are GLM `glm-5.2` over Responses/max and DeepSeek `deepseek-v4-flash` over Chat Completions/max; V4 Pro is not used. The raw-payload scenario proves that large technical path/Evidence payloads are removed before prompt budgeting instead of forcing unnecessary child-window calls.

PostgreSQL 16, Playwright and real DeepSeek/GLM History runs remain environment-dependent gates. Docker or Provider unavailability is reported as `BLOCKED`/`NOT_RUN`; H2, fixed compatibility services or deterministic tests are never presented as substitutes for those gates.

V3.8.0 uses full Git checkout for backend/H2 and PostgreSQL jobs because fixed ProjectFlow dogfood must reach the V3.7.5 baseline and its ancestors. The backend job runs the complete suite and explicitly verifies that Frozen Dataset, History Prompt, Dogfood and Product Acceptance reports were emitted; it does not rerun duplicate Maven work.

Blocking jobs remain backend/H2, PostgreSQL 16 Testcontainers, frontend TypeScript/build/contracts, Playwright, Hermes, Obsidian and sensitive-content checks. `scripts/verify_v380_acceptance_evidence.py` parses committed JSON and scans JSON/Markdown/text artifacts for credentials and machine absolute paths. It also rejects raw `apiKey`, `authorization`, `prompt`, `rawResponse` or `reasoning` JSON fields while allowing explicit `*Persisted: false` safety assertions. The same check requires the V3.8.0 acceptance freeze manifest, confines every listed path to the evidence root and verifies the committed file length and SHA-256.

The optional `workflow_dispatch` real-Provider job additionally runs `ProjectHistoryRealModelIT` after the Provider probe, frozen 18-case evaluation and Project Understanding E2E. GLM uses the same production History Prompt/contract as the locally qualified DeepSeek profile. The job uploads only sanitized aggregate artifacts under `backend/target/projectflow-eval`; it never uploads Key, Prompt, raw response or reasoning.

`.github/workflows/quality-gates.yml` runs on pushes and pull requests.

The blocking jobs are backend unit/H2 tests, PostgreSQL Testcontainers, frontend TypeScript/production build/contracts, Playwright browser E2E and a basic committed-secret scan. JUnit and Playwright failure evidence are uploaded as artifacts. API keys, Authorization values, raw reasoning, model bodies and local database snapshots are not uploaded.

V3.7.5 adds blocking deterministic gates for the seven-state constitution, strong-fact promotion, model/Agent/fallback isolation, bounded profile claims, complete small Evidence Ledgers, eligible-capability REQUEST/SKIP decisions, explicit semantic degradation, Context Package v2, work-result candidates, local revalidation, project authorization, prompt parity and Ground Truth leakage. The existing extensionless-content and 80,000-line Content Map/range/revision gates remain unchanged. Generated giant fixtures stay in the local Acceptance Archive; Git contains only the generator, specification, manifest, hashes and sanitized summaries.

Real GLM and DeepSeek runs remain explicit non-default acceptance work because they require private Provider credentials. Both use Prompt contract v3, Semantic Scout v13, Final Synthesis v7 and the same frozen Holdout. Calibration and frozen Holdout use different classpath resources and Provider-specific output directories. Only normalized observations and aggregate diagnostics may be archived; keys, prompts, raw responses and reasoning are forbidden.

The workflow-dispatch job has a 360-minute process watchdog and executes a focused Provider probe, the unchanged representative evaluation through `ModelGatewayService`, and the eight-case real `ProjectUnderstandingService.refresh()` acceptance. Each Provider request receives the explicit 600-second configuration; there is no hidden short cap. Reasoning-capable requests may use the configured 65,536 ceiling from the first attempt and explicitly supported Responses/Chat profiles retain their configured effort for connection, semantic and recovery calls. Time and Token figures are diagnostic evidence, not negative quality metrics. The job uploads only sanitized evaluation artifacts. Fixed-model tests remain blocking and must never be reported as real-Provider evidence.

The PostgreSQL job intentionally fails when Docker is unavailable; it never silently substitutes H2. The optional real Provider job runs only through workflow_dispatch with `run_real_model` enabled. `PROJECTFLOW_REAL_MODEL_API_KEY` and `PROJECTFLOW_DEEPSEEK_API_KEY` are injected from GitHub Actions Secrets and are never persisted or uploaded; a missing selected Secret fails before any Provider request. The current optional profiles use GLM `glm-5.2` over Ark Coding v3/`OPENAI_RESPONSES`/max and DeepSeek `deepseek-v4-flash` over OpenCode Go `/v1`/`OPENAI_CHAT_COMPLETIONS`/max, with explicit JSON Mode and reasoning support/control. A committed-content gate rejects common OpenAI/Ark/Bearer secret shapes.

Product reliability and conditional semantic quality are reported separately, but the final gate requires both. Ground Truth, metric formulas and thresholds remain unchanged. Internal Eval fields have no production API, snapshot, database or UI path.

`browser-e2e` starts the backend, frontend, and a deterministic OpenAI-compatible test server. All eight current core tests are blocking. `postgres-integration` runs the full PostgreSQL 16 workflow tests; it is not a repository CRUD substitute.
