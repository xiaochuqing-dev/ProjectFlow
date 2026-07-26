# ProjectFlow V3.7.2 Model Evaluation

Evaluation date: 2026-07-26

Scope notice: 本指标仅代表本阶段人工标注代表性测试集，不构成对任意项目的通用准确率承诺。

## Test set and Ground Truth

The test-only dataset contains 18 ProjectFlow cases: empty directory, blank text, strange important document, small script, frontend, backend, desktop, fullstack, monorepo, no Git, short Git, long Git, stale README, README/source conflict, Agent Result, token usage metadata, ProjectFlow itself and a large repository.

Ground Truth is a stage-specific human label set. Each case declares bounded Stage 1 context, optional separately supplied Tool Evidence, multi-label project shapes, must-find evidence IDs, must-not claims, expected and forbidden capability names, expected and forbidden dynamic views, unknowns, conflicts, history mode and deep-read targets. It is not derived from the model and is not a general benchmark.

## Provider and versions

- Provider: DeepSeek
- Protocol: OPENAI_CHAT_COMPLETIONS
- Model: deepseek-v4-pro
- Successful calibration pilot: semantic-scout-v2 + final-synthesis-v2
- Final code prompt: semantic-scout-v3 + final-synthesis-v3
- Code version: 3.7.2
- Price source: unavailable; no currency cost is fabricated

The Key was read only from a copied local Provider database by the test process. It was not printed, persisted in artifacts or committed. The current local database compatibility risk remains: Provider keys are stored locally until OS secure storage is implemented.

## Metric definitions

- Unsupported Claim Rate: claims with no evidence outside UNKNOWN/INFERRED, manually unsupported claims, or must-not-claim text, divided by evaluated claims.
- Critical Evidence Recall: expected critical evidence IDs found by the normalized result.
- Evidence Precision: selected evidence IDs that are Ground Truth critical evidence.
- Shape F1 and exact accuracy: multi-label atomic project-shape comparison.
- Tool precision, recall and unnecessary rate: registered requested capabilities compared with expected and forbidden capability labels.
- Dynamic View precision and recall: evidence-applicable profile dimensions compared with the labelled set.
- Conflict Detection: labelled conflicts detected without silently choosing a winner.
- Repeatability: mean Jaccard similarity over shape, evidence, tool and key-claim sets for repeated important cases.
- Second-stage gain: Stage 2 minus Stage 1 evidence recall, unsupported-claim rate and view recall.
- Failure and degradation: real-call failure rate and the proportion whose safe fallback/degraded result remained usable.

## Successful pre-calibration pilot

The dated pilot completed 38 runs: each important case ran three times and the other cases once. It used the real Model Gateway, but its test harness still selected Stage 2 from the labelled deep-read target and reused the broad Scout schema for Final Synthesis. Therefore it is valid calibration evidence, not a release PASS result.

| Metric | Result |
|---|---:|
| Runs / failures | 38 / 0 |
| Unsupported Claim Rate | 0.0200 original artifact; 0.0100 after negation-aware recalculation |
| Critical Evidence Recall | 0.8831 |
| Evidence Precision | 1.0000 |
| Shape F1 / exact accuracy | 0.8148 / 0.7368 |
| Tool precision / recall | 0.7619 / 0.6667 |
| Unnecessary-tool rate | 0.2381 |
| Dynamic View precision / recall | 0.6951 / 0.6706 |
| Conflict Detection | 1.0000 |
| Repeatability | 0.5708 |
| Second-stage evidence / view gain | 0.0444 / 0.1333 |
| Requests / total tokens | 67 / 160256 |
| Average latency | 38460 ms |
| Estimated currency cost | UNAVAILABLE |

## Per-case pilot result

| Case | Runs | Critical evidence recall | Observed shape | Observed registered tools | Calibration finding |
|---|---:|---:|---|---|---|
| empty-directory | 1 | 1.000 | EMPTY | none | Correct zero-model |
| blank-text | 1 | 1.000 | EMPTY_CONTENT | none | Correct zero-model |
| strange-important-document | 3 | 1.000 | DOCUMENT | DOC_READER | Stable and useful |
| small-script | 1 | 0.000 | empty | none | Missed evidence and shape |
| frontend-only | 3 | 1.000 | FRONTEND | MANIFEST, occasional DOC_READER | Extra tool request |
| backend-only | 3 | 1.000 | BACKEND | MANIFEST, occasional DOC_READER | Extra tool request |
| desktop-app | 1 | 1.000 | DESKTOP | MANIFEST, DOC_READER | Extra tool request |
| fullstack | 3 | 0.333 | compound free-text shape | MANIFEST | Two unstable empty results |
| monorepo | 3 | 1.000 | MONOREPO | MANIFEST, occasional FILESYSTEM/DOC_READER | Core shape stable, tool plan noisy |
| no-git | 1 | 0.000 | empty | none | Failed to expose current-state evidence |
| short-git-history | 1 | 1.000 | CODE_PROJECT | none | Missed expected bounded Git request |
| long-git-history | 1 | 1.000 | CODE_PROJECT | GIT_HISTORY, GIT_TAG | Correct bounded history plan |
| stale-readme | 3 | 1.000 | CODE_PROJECT | DOC_READER/MANIFEST, one Git request | Conflict stable, tool plan noisy |
| readme-source-conflict | 3 | 1.000 | CODE_PROJECT | DOC_READER/MANIFEST, one Git request | Conflict stable |
| agent-result | 3 | 0.667 | AGENT_RESULT_MATERIAL or empty | occasional MANIFEST/GIT_HISTORY | PROCESS_EVIDENCE handling unstable |
| token-usage | 3 | 1.000 | PROCESS_METADATA | none | Did not promote usage to project quality |
| projectflow-itself | 3 | 1.000 | BACKEND/DEVELOPER_WORKBENCH | DOC_READER/MANIFEST/GIT_HISTORY or none | Frontend shape and tool stability insufficient |
| large-repository | 1 | 1.000 | LARGE_REPOSITORY | MANIFEST | Bounded, diversity claim preserved |

## Repeatability and Stage comparison

Repeatability was 0.5708, below the 0.80 gate. The most unstable cases were fullstack, Agent Result and ProjectFlow itself. Stage 2 showed measurable evidence and view gain, but the pilot made 67 physical requests because the old broad Final Synthesis schema caused bounded schema-repair requests. Product policy still had at most two logical model stages; V3.7.2 registers a dedicated Final Synthesis schema to remove that repair source.

## Performance sample

The pilot artifact retained combined tokens rather than stage-specific token fields. The current harness now records Stage 1 input/output tokens, bounded Tool Evidence characters and Stage 2 input/output tokens separately, but the calibrated final prompt could not be rerun after the Provider returned HTTP 402. Missing stage splits are reported as unavailable, not estimated.

| Scale | Representative | Runs | Avg total tokens | Avg latency | Avg physical requests | Stage 2 runs | Stage split / tool chars |
|---|---|---:|---:|---:|---:|---:|---|
| Small | script | 1 | 1154 | 7457 ms | 1.00 | 0 | UNAVAILABLE in pilot |
| Small | document | 3 | 5482 | 51013 ms | 3.00 | 3 | UNAVAILABLE in pilot |
| Medium | frontend | 3 | 3843 | 37122 ms | 1.00 | 0 | UNAVAILABLE in pilot |
| Medium | backend/JUnit representative | 3 | 2893 | 27227 ms | 1.00 | 0 | UNAVAILABLE in pilot |
| Large | ProjectFlow | 3 | 9048 | 84101 ms | 3.33 | 3 | UNAVAILABLE in pilot |
| Large | large React-style repository material | 1 | 3010 | 31520 ms | 1.00 | 0 | UNAVAILABLE in pilot |
| Historical | long Git | 1 | 3278 | 31241 ms | 1.00 | 0 | UNAVAILABLE in pilot |

There is no per-file or per-commit model loop, no unbounded retry and no fabricated price. Physical recovery requests remain bounded by Model Gateway policy.

## Prompt and code calibration

Before calibration, the model could emit compound shapes, empty arrays for substantive evidence, generic dimensions such as analysis/general, broad DOC_READER/Git requests and inconsistent deep-read flags. Final Synthesis also reused the Scout schema. The original metric also treated “complete coverage is not available” as a must-not violation; the final negation-aware rule no longer converts explicit limitations into hallucinations. One unsupported observed limitation without an evidence reference remains, giving a recalculated pilot rate of 0.0100.

The final v3 prompt requires atomic multi-label shapes, one assessment per supplied evidence ID, explicit UNKNOWN instead of empty avoidance, stable uppercase dimensions, evidence-gap-driven tool selection and conservative shouldDeepRead. Final Synthesis uses its own registered schema and smaller output bounds. The real harness now extracts allowed evidence IDs from the supplied Stage 1 context, triggers comparison from the model’s actual deep-read choice, supplies separately bounded Tool Evidence, records safe failure categories and never injects Ground Truth answers into the prompt.

No production path contains case IDs or expected answers. Ground Truth remains under test resources.

## Final real-Provider attempt

After removing Ground Truth-assisted triggering, the full run produced two deterministic zero-model results and 36 immediate real-call failures. A minimal diagnostic rerun safely classified the response as `ModelHttpException-402`. Only one configured Provider existed. Repeated blind retries were stopped to avoid quota waste. No alternative Provider, local Ollama or LM Studio runtime was present.

The failure is an external balance/quota condition, but the final calibrated v3 prompt has no valid real-Provider aggregate. The successful pilot also fails the final Tool Selection, Dynamic View and Repeatability thresholds.

## Release gates and decision

| Gate | Threshold | Evidence | Result |
|---|---:|---:|---|
| Failure rate | <= 0.05 | final attempt 0.9474 | FAIL |
| Critical Evidence Recall | >= 0.85 | pilot 0.8831; final unavailable | NOT FINAL |
| Unsupported Claim Rate | <= 0.05 | pilot recalculated 0.0100; final unavailable | NOT FINAL |
| Critical must-not violations | 0 | pilot negation-aware review found 0 | PASS FOR PILOT |
| Tool recall | >= 0.80 | pilot 0.6667 | FAIL |
| Unnecessary-tool rate | <= 0.15 | pilot 0.2381 | FAIL |
| Dynamic View recall | >= 0.90 | pilot 0.6706 | FAIL |
| Repeatability | >= 0.80 | pilot 0.5708 | FAIL |
| Deep-read second-stage gain | > 0 in at least one dimension | pilot evidence/view gain positive | PASS FOR PILOT |
| Empty/blank zero-model | required | 2/2 | PASS |
| Stage 2 failure preserves Stage 1 | 100% tested | parameterized fixed tests | PASS |
| Secret leak | 0 | source/artifact scans | PASS |

Final decision: NOT PASSED. V3.7.2 code and boundaries are implemented, but real-model quality acceptance must be rerun with a funded or otherwise available Provider before ProjectFlow enters V3.8. This report deliberately does not convert a quota failure or a pre-calibration pilot into PASS.
