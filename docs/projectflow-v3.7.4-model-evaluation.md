# ProjectFlow V3.7.4 Model Evaluation

## Contract and profiles

Both models used Strong Fact Contract v2, Semantic Scout v11, Final Synthesis v6, the same Prompt Builder, Evidence allow-list, capability/view registries and bounded Gateway recovery.

| Role | Provider/model | Protocol | Explicit capability |
| --- | --- | --- | --- |
| Model A | Volcano Ark Coding / glm-5.2 | OPENAI_RESPONSES | reasoning inferred from the GLM-5 family; Calibration used 32,000; product rerun profile uses 65,536 |
| Model B | DeepSeek official / deepseek-v4-pro | OPENAI_CHAT_COMPLETIONS | supportsReasoning=true; no private reasoning-control field; 32,000 configured output ceiling |

Keys were entered through a masked secure prompt, existed only in the Maven process, and were cleared on exit. Reports contain normalized observations, request/usage/latency diagnostics and hashes only. They contain no key, Authorization value, raw response, reasoning or complete prompt.

## Calibration result

The final Calibration set contains 21 separated cases. The gate requires failure rate at most 0.05, unsupported fact 0, critical Evidence recall at least 0.90, deep-read sufficiency at least 0.80, conflict recall at least 0.80 when conflicts exist, no invalid Evidence and no forbidden claim.

| Metric | GLM-5.2 | DeepSeek V4 Pro |
| --- | ---: | ---: |
| Runs / structured success | 21 / 21 | 21 / 21 |
| Failure / degradation | 0 / 0 | 0 / 0 |
| Unsupported fact rate | 0.0000 | 0.0000 |
| Critical Evidence recall | 0.96875 | 1.0000 |
| Deep-read sufficiency | 1.0000 | 0.8333 |
| Conflict detection recall | 1.0000 | 1.0000 |
| Second-stage Evidence gain | 0.8333 | 0.8000 |
| Requests | 28 | 26 |
| Tokens | 285,079 | 210,563 |
| Average / P95 latency ms | 121,554 / 244,146 | 80,686 / 154,967 |
| Retry | 1 bounded Schema repair | 0 |

GLM formal artifact SHA-256: `885D2DA7C22C473D4E56E40066981A786630A923DEEC956548C7213B4D58C568`.

DeepSeek formal artifact SHA-256: `C8CA7B557D36A9330F5BBFBB08963942A75D806B657F09FDB5F2F8CF75D451AD`.

The preserved first GLM product E2E run passed 7 of 8 cases and failed on `projectflow-itself` because reasoning and visible JSON exhausted the 32,000 recovery ceiling. The generic recovery policy was corrected to use the Provider's explicit ceiling for the only reasoning-aware truncation retry. With GLM configured at 65,536, the isolated ProjectFlow repository rerun passed with two logical calls, four physical requests and 690,938 ms latency.

The final original eight-case GLM product E2E then passed all eight product checks with 16 logical calls, 23 physical requests, 353,252 tokens and 3,170,874 ms aggregate latency. The `no-git` case completed through the deterministic fallback after one visible Final Synthesis degradation, so this is not described as zero degradation. `projectflow-itself` passed with MANIFEST, GIT_HISTORY, DOC_READER and AGENT_RESULT evidence, four physical requests and 964,466 ms latency. Artifact SHA-256: `0C3B69DC5ECD52580D0748568DAD18981401D8B6BCF74DA1105A0584BA26288A`.

DeepSeek product E2E could not be completed after the formal Holdout consumed the available account quota. Six core cases returned HTTP 402 before semantic execution, and a minimal Provider probe confirmed the same response. The preserved failed artifact SHA-256 is `346287CE3FDD1F67D0BE7C2CD3A0ADDBBC813A02368551D0556C016471F9BB4C`; no result-oriented retry was made.

## Frozen Holdout

| Metric | GLM-5.2 | DeepSeek V4 Pro |
| --- | ---: | ---: |
| Runs / completed | 8 / 8 | 8 / 8 |
| Schema failure / degradation | 1 / 1 | 0 / 0 |
| Unsupported claim rate | 0.0000 | 0.0000 |
| Critical Evidence recall | 0.9091 | 0.8182 |
| Evidence precision | 1.0000 | 0.9000 |
| Deep-read sufficiency | 1.0000 | 0.6667 |
| Conflict detection | 1.0000 | 0.0000 |
| Requests / tokens | 13 / 126,279 | 10 / 71,830 |

GLM's `holdout-tail-revision` Final Synthesis failed Schema validation and its one bounded repair, producing an explicit fallback. DeepSeek completed structurally but missed the frozen Critical Evidence Recall threshold of 0.90, skipped required deep-read capabilities in key cases and missed the Agent/CI conflict. Holdout was run once per model after freeze; Prompt, Ground Truth and thresholds were not changed.

## V3.7.3 regression

The original GLM regression completed 38/38 with zero failure/degradation, Critical Evidence Recall 0.9610, Evidence Precision 1.0000, Tool Recall 0.9375, Deep-read Sufficiency 0.8333 and invalid Evidence 0. The supplementary DeepSeek core regression completed 10/10 with Critical Evidence Recall 0.9583, but its Tool Recall 0.5000, Deep-read Sufficiency 0.6667 and Conflict Detection 0.3333 remained weaker. Regression does not override the failed Holdout.

## Preserved failures and fixes

The first DeepSeek nine-case diagnostic completed 8/9. One normal backend case exhausted a smaller dynamic output budget during Schema repair. The model was using reasoning by default while its explicit Provider capability was absent. Adding `supportsReasoning=true` to the profile, without a model-name branch, completed the same set 9/9.

A GLM three-case capability diagnostic completed the semantic chain but failed the new invalid-reference assertion because its report retained temporary tool Evidence IDs. The harness now maps validated tool IDs through Provider provenance to source IDs; invalid model IDs are filtered exactly as production does, and a factual claim left without Evidence still fails the unsupported-fact gate.

The first DeepSeek 21-case run completed all cases but was correctly rejected at conflict recall 0.50. Its output had found both conflicts; the generic normalizer recognized the SQLite/PostgreSQL wording but not “README 8080 versus runtime 9090”. A general README conflict rule and deterministic fixture were added. The complete rerun reached 1.00 conflict recall. Failed artifacts remain in the local archive and are not counted as passes.

## Interpretation

Selection specificity, project-shape exactness and extra view/tool choices remain diagnostic. Additional allow-listed Evidence is not mislabeled as an invalid reference. Product safety is decided by provenance, epistemic state, promotion guards and forbidden-claim checks. Different wording or optional views across models is acceptable; promotion of inference, Agent claims, historical reasons, deprecation or technical debt is not.

The final decision is therefore `PROJECT UNDERSTANDING FOUNDATION = NOT STABLE` and `V3.8 EVOLUTION RECONSTRUCTION = BLOCKED`. Calibration success and the GLM E2E result cannot be used to conceal the DeepSeek Holdout failure or the observed GLM degradation.
