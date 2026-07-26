# ProjectFlow V3.7.2 Final Funded-provider Revalidation

Revalidation date: 2026-07-27

Scope notice: 本指标仅代表本阶段人工标注代表性测试集，不构成对任意项目的通用准确率承诺。

## Decision

`V3.7.2 REAL MODEL QUALITY GATE = NOT PASSED`

`V3.8 ENTRY = BLOCKED`

GLM Provider is available and the one-request structured probe passed, but the complete 38-run aggregate and the real production-chain core cases did not pass the fixed release gates. No Prompt, Ground Truth, metric formula or threshold was changed.

## Final questions

| Item | Answer |
|---|---|
| A. Provider READY | YES |
| B. Provider / model / protocol | GLM / `glm-5.2` / `OPENAI_RESPONSES` |
| C. Key injection | Process-only `PROJECTFLOW_REAL_MODEL_API_KEY` environment variable |
| D. Key in Git/log/report/artifact | NO; source and sanitized artifact scans found zero leak |
| E. Prompt versions | `semantic-scout-v3` and `final-synthesis-v3` |
| F. Ground Truth modified | NO |
| G. Thresholds modified | NO |
| H. Complete runs | 38 |
| I. Failed runs | 19 |
| J. Failure rate | 0.5000 |
| K. Critical Evidence Recall | 0.3636 |
| L. Evidence Precision | 1.0000 |
| M. Unsupported Claim Rate | 0.0000 |
| N. Critical must-not violation | 0 |
| O. Shape F1 / exact | 0.4691 / 0.1842 |
| P. Tool precision / recall | 0.2222 / 0.1667 |
| Q. Unnecessary Tool Rate | 0.7778 |
| R. Dynamic View precision / recall | 0.1026 / 0.0941 |
| S. Conflict Detection | 0.1111 |
| T. Repeatability | 0.4130 |
| U. Stage 2 evidence / unsupported / view gain | 0.0000 / 0.0000 / 0.0000 |
| V. Stage 1 / Stage 2 token | Stage 1 input/output 11394/41214; Stage 2 input/output 2346/7788 |
| W. Total token | 62742 |
| X. Average latency | 65213.79 ms |
| Y. Logical requests | 0 calls: 2 runs; 1 stage: 32 runs; 2 stages: 4 runs |
| Z. Physical repair requests bounded | YES; 45 total physical requests, maximum 3 per run, 5 recorded retries |
| AA. End-to-end projects | strange document, small script, frontend, backend, fullstack, no Git, Agent Result, ProjectFlow itself |
| AB. Actual `ProjectUnderstandingService.refresh()` | YES |
| AC. Actual Capability Provider | YES |
| AD. Tool Evidence source | Real `BoundedLocalAnalysisCapabilityProvider`; no fixed Tool Evidence string |
| AE. Final Profile real Evidence ID | YES for the two completed refreshes; zero invalid Evidence refs |
| AF. Small Script | PASS |
| AG. Fullstack | FAIL: Stage 1 Provider timeout |
| AH. No-Git | FAIL: Stage 1 Provider timeout |
| AI. Agent Result | FAIL: Stage 1 Provider timeout |
| AJ. ProjectFlow itself | FAIL: Stage 1 Provider timeout |
| AK. Final Synthesis degradation | Fixed regression remains 100%; the completed real Stage 2 succeeded |
| AL. Secret leak | 0 |
| AM. Internal metrics excluded from product | YES |
| AN. Final quality state | NOT PASSED |
| AO. V3.8 entry | BLOCKED |
| AP. Minimal V3.7.3 scope | Provider-timeout failing cases, Tool Selection reliability, Dynamic View applicability, Repeatability and production/eval Prompt parity |
| AQ. Implementation SHA | `d0b0fa7dc6af241d00a9d09902b270952971f697` |
| AR. Documentation SHA | `48f65df724f3d8bad2a730b61742b95480bf2f1f` |
| AS. PR number | #6 |
| AT. PR merge SHA | `00e8294fcd89ab165c356d68b19099e8c21a9b85` |
| AU. Final functional master SHA | `00e8294fcd89ab165c356d68b19099e8c21a9b85` |
| AV. CI run IDs/status | PR `30211864937` PASS; functional master `30211953173` PASS |
| AW. Tag created | NO |
| AX. Release created | NO |

## Provider probe

The focused probe used ProjectFlow Model Gateway, the registered OpenAI Responses adapter, Provider connection task schema and one structured request. It returned valid JSON, matched the expected schema and completed in about 19 seconds without exposing the request body, raw response, Key or Authorization header.

Provider base URL was `https://ark.cn-beijing.volces.com/api/coding/v3`; the registered adapter resolved the Responses endpoint. The test Provider requested a 120-second configuration, while the existing V3.7.2 evaluation constructor conservatively capped each gateway wait at 45 seconds. The full run retained that published harness behavior and did not change the timeout after failures appeared.

## Complete aggregate

| Metric | Result | Gate | Status |
|---|---:|---:|---|
| Failure rate | 0.5000 | <= 0.05 | FAIL |
| Critical Evidence Recall | 0.3636 | >= 0.85 | FAIL |
| Unsupported Claim Rate | 0.0000 | <= 0.05 | PASS |
| Critical must-not violation | 0 | 0 | PASS |
| Tool Selection Recall | 0.1667 | >= 0.80 | FAIL |
| Unnecessary Tool Rate | 0.7778 | <= 0.15 | FAIL |
| Dynamic View Recall | 0.0941 | >= 0.90 | FAIL |
| Repeatability | 0.4130 | >= 0.80 | FAIL |
| Positive Stage 2 gain | none | at least one | FAIL |
| Empty/blank zero-model | 2/2 | required | PASS |
| Degradation success | 1.0000 | 1.0000 | PASS |
| Secret leak | 0 | 0 | PASS |

All 19 failures were classified as bounded transport/network timeouts through the OpenAI SDK path. There were no 401, 402 or 429 results. Successful runs still showed weak Tool Selection and Dynamic View alignment, so increasing the timeout alone cannot be assumed to make the quality gate pass.

Sanitized real-eval artifact SHA-256: `7BEE0C37BE3360173C66EFDE1551E551B954A832C32EBEEEF84982065FEF4C75`.

## Manual review

- Strange document completed 3/3 and consistently selected `DOC_READER`; one run used a bounded schema-repair request.
- Small script failed in the direct Harness but passed the real production-chain refresh without architecture expansion.
- Frontend, backend, fullstack, monorepo, Git-history and large-repository coverage was heavily reduced by timeouts.
- Successful no-Git direct-Harness output requested unnecessary Git/SCIP capabilities, contributing to the Tool gate failure.
- Agent Result direct-Harness runs completed 3/3 without a must-not violation, but its real production-chain refresh timed out.
- Token-usage successful samples did not create a critical must-not violation.
- Conflict detection was not reliable enough: 0.1111 aggregate.
- No automatic score was used to overwrite the observed failure categories or to change Ground Truth.

## Cost

The run recorded 62742 total tokens. Reliable endpoint-specific real-time currency pricing was not established, so estimated cost remains `UNAVAILABLE`; no RMB amount is fabricated.

## Regression and delivery gates

- Backend/H2: 366 tests passed, with one benchmark skipped by its existing condition.
- PostgreSQL 16 Testcontainers: not passed locally because this machine had no valid Docker environment; PR and functional-master CI both passed the PostgreSQL blocking job.
- Frontend: contracts 50/50, lint and production build passed.
- Playwright: 8/8 passed.
- Hermes: 5/5 passed.
- Obsidian: 18/18 passed.
- Root launcher: `Start-ProjectFlow.bat -NoBrowser` rebuilt the current working tree, started both services, passed the frontend/backend HTTP readiness checks and wrote `logs/last-embedded-build.json`.
- Dependency audit: 0 critical and 3 high advisories in the existing Next.js transitive dependency path; the proposed automated fix is breaking and was not applied in this focused revalidation.
- Secret scan: no GLM key-shaped value was found in source or the sanitized evaluation artifacts; raw evaluation artifacts remain untracked.

## GitHub completion

PR #6 merged after every blocking job passed. PR run `30211864937` and functional-master run `30211953173` both passed Backend/H2, PostgreSQL 16 Testcontainers, Frontend, Playwright, sensitive-content, Hermes and Obsidian jobs. The optional real-Provider job remained skipped because this acceptance used the locally injected process-only Key and did not persist it as a repository secret.

No Tag, GitHub Release, binary publication or V3.8 implementation was created. This metadata-only backfill does not change the evaluated implementation or the `NOT PASSED` quality decision.
