# ProjectFlow V3.7.2 Final Revalidation Audit

Audit date: 2026-07-26

Scope notice: 本指标仅代表本阶段人工标注代表性测试集，不构成对任意项目的通用准确率承诺。

## Baseline

- Repository: `xiaochuqing-dev/ProjectFlow`
- Audited master HEAD: `743e13aa37dc14ce36f00ba779aaf69ab04d7c48`
- Product version: `3.7.2`
- Semantic Scout Prompt: `semantic-scout-v3`
- Final Synthesis Prompt: `final-synthesis-v3`
- Ground Truth: `ProjectFlow V3.7.2 stage-specific human ground truth; not a universal benchmark`
- Eval Harness: `projectflow-v3.7.2-eval-v1`
- Cases: 18
- Important cases: 10, each configured for three repetitions
- Source worktree at branch creation: clean isolated worktree based on `origin/master`
- Audit branch changes before Provider probe: test-only generic real-Provider environment injection and a one-request Model Gateway health probe; production Prompt, Ground Truth, case definitions, metrics and thresholds are unchanged

## Published thresholds

- Failure rate <= 0.05
- Critical Evidence Recall >= 0.85
- Unsupported Claim Rate <= 0.05
- Critical must-not-claim violation = 0
- Tool Selection Recall >= 0.80
- Unnecessary Tool Rate <= 0.15
- Dynamic View Recall >= 0.90
- Repeatability >= 0.80
- At least one positive Deep-read Stage 2 gain
- Empty and blank inputs use zero model calls
- Logical model stages remain 0, 1 or 2
- Final Synthesis failure degradation = 100%
- Secret leak = 0
- No per-file or per-commit model loop and no unbounded retry

## Provider configuration

- Provider: GLM
- Model: `glm-5.2`
- Protocol: `OPENAI_RESPONSES`
- Base URL: `https://ark.cn-beijing.volces.com/api/coding/v3`
- Endpoint resolved by the registered adapter: `/api/coding/v3/responses`
- Authentication: protocol-default Bearer authentication
- Timeout: 120 seconds requested by the test configuration; the focused probe constrains its gateway wait to 45 seconds
- Provider output ceiling: 16000 tokens
- Key injection: `PROJECTFLOW_REAL_MODEL_API_KEY`, process environment only
- Key persistence: not written to source, test resources, Prompt, report, Git, logs or eval artifacts
- Probe status: `PROVIDER_READY`; one real structured Model Gateway request passed in about 19 seconds

The existing `DEEPSEEK_API_KEY` and `DEEPSEEK_MODEL` test inputs remain supported for backward compatibility. When the generic key is used, Provider name, base URL, model, type and protocol come from the generic `PROJECTFLOW_REAL_MODEL_*` variables.

## Existing V3.7.2 evidence

- PR #5 is merged.
- PR #5 merge SHA: `13f59169a66342d7ebc152bd3de9257792fbf017`
- Final documentation HEAD: `743e13aa37dc14ce36f00ba779aaf69ab04d7c48`
- Latest master CI before this revalidation: GitHub Actions Run `30201881835`, success
- Original functional master CI: Run `30201767848`, success
- Existing final quality state: `NOT PASSED`
- Existing failure history is retained: the final DeepSeek v3 attempt returned HTTP 402 and produced no valid final aggregate
- Tags: none
- GitHub Releases: none

## Prompt parity audit

- Eval prompt version constants reference the production `SemanticScoutService.PROMPT_VERSION` and `FinalProfileSynthesisService.PROMPT_VERSION`.
- The Eval Stage 1 and Stage 2 prompt text still contains test-local copies rather than calling the production prompt builders.
- The Eval schema, evidence-ID filtering, Stage 2 trigger and token fields match the intended v3 contracts, but exact text parity is not structurally guaranteed.
- Reusing production builders would require broad fixture-to-production DTO construction and would expand the revalidation scope. This remains explicit V3.7.3 technical debt and prevents the direct Model Gateway aggregate from being described as a complete production-chain substitute.
- The separate end-to-end acceptance must therefore call `ProjectUnderstandingService.refresh()` and real capability providers before any quality PASS decision.

## Dependency and repository audit

- `npm audit --omit=dev --json`: 3 existing high-severity findings, all through the current Next.js dependency graph (`postcss` and `sharp`); npm suggests an incompatible Next.js downgrade, so no automatic remediation is applied in this revalidation.
- Critical npm findings: 0.
- Backend has no committed CVE-scanner profile; dependency compilation and regression tests remain the available gate.
- Focused test compilation and no-key probe path: passed, with the real call explicitly skipped because the key had not yet been injected.
- Secret scan after live execution: passed for source changes and sanitized real/e2e artifacts; no Key, Authorization value, raw response or reasoning field was found.

## Audit decision

The funded GLM Provider health probe passed and authorized the complete run. The later complete aggregate and production-chain acceptance did not pass the fixed quality gates; details remain in the final revalidation reports.
