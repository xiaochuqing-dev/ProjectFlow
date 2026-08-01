# ProjectFlow V3.7.5 Model Qualification

Qualification date: 2026-08-01

## Qualification policy

The same Prompt contract, Evidence allow-list, frozen Ground Truth and thresholds were used for GLM and DeepSeek. Provider-specific adaptation is limited to protocol, explicit JSON Mode and supported reasoning fields. Elapsed time, Token usage, request count and cost are process diagnostics, not quality defects; they did not lower reasoning effort, Evidence coverage or acceptance thresholds.

| Profile | Protocol | Model | Reasoning | Output ceiling | JSON |
| --- | --- | --- | --- | --- | --- |
| Main | OpenAI Responses | `glm-5.2` | thinking/high | 65,536 | Provider structured output |
| Compatibility | OpenAI Chat Completions | `deepseek-v4-flash` | thinking/high | 65,536 | explicit JSON Mode |

Credentials existed only in the real-test process. No key, Authorization value, prompt, raw response or reasoning is present in Git or the safe artifacts.

## Frozen inputs

- Prompt contract v3; Semantic Scout v13; Final Synthesis v7.
- Scout snapshot: `806AA0110390095F8961FB8ABEFC96422DCB576B617261153F7393DF0569777D`.
- Final snapshot: `14574307A878B46B2B99689E993522B9C01DEE9EE594C4616988D1302687C136`.
- Freeze 1 manifest: `8EB8E1D5B8C15C09FBEBB30B30FFE7E6D0389BA7D2226E10AEDDEA1023D0E601`.
- DeepSeek Freeze 2 manifest: `5107B639D0BF7C4D1052305AAA8767AA662B66DDFC43AB85E0CBE08827BD3590`.
- V3.7.4 Holdout Ground Truth, capability fixtures and thresholds were unchanged.

## Frozen Holdout

| Result | GLM | DeepSeek first formal run | DeepSeek Freeze 2 |
| --- | --- | --- | --- |
| Completed cases | 8/8 | 3/8 | 8/8 |
| Failure / degradation | 0 / 0 | 5 / 5 | 0 / 0 |
| Schema failures | 0 | 0 | 0 |
| Critical Evidence Recall | 1.0000 | 1.0000 on completed semantic cases | 1.0000 |
| Deep-read Sufficiency | 1.0000 | 1.0000 on completed semantic cases | 1.0000 |
| Conflict Detection | 1.0000 | 1.0000 on completed semantic cases | 1.0000 |
| Unsupported Claim Rate | 0 | 0 | 0 |
| Tool precision / recall | 0.7500 / 0.7500 | 0.5000 / 0.5000 | 0.8000 / 1.0000 |
| Dynamic View recall | 0.2727 | 0.3333 | 0.2727 |
| Requests / Token | 11 / 93,000 | 15 / 102,379 | 15 / 143,652 |

The first DeepSeek formal run is retained as failure evidence: five cases exhausted visible output after reasoning. The official endpoint then passed a focused diagnostic with explicit JSON Mode while keeping high reasoning, the same Prompt, Ground Truth, fixtures and thresholds. Freeze 2 changed only the declared technical JSON capability and evaluation-path parity. The final DeepSeek Holdout passed 8/8.

GLM met the safety and semantic gates. Its Tool precision/recall and Dynamic View recall remain disclosed limitations rather than hidden by the overall pass. They do not create unsupported claims or Strong Fact promotion authority.

## Product E2E

| Result | GLM | DeepSeek Freeze 2 |
| --- | --- | --- |
| Product cases | 8/8 | 8/8 |
| Logical / physical requests | 16 / 17 | 15 / 22 |
| Token | 265,679 | 299,575 |
| Invalid Evidence refs | 0 | 0 |
| Snapshot persistence/readback | 8/8 | 8/8 |
| Explicit degradation | 1 | 0 |

GLM `strange-important-document` retained Stage 1 and validated Evidence as `FAILED_DEGRADED`; all product checks and persisted readback passed. This is disclosed and is not described as zero degradation. DeepSeek had no degraded case; `no-git` correctly skipped Final Synthesis because no new high-value Evidence was produced.

## Post-run runtime-policy clarification

After the formal runs, the general runtime rule was tightened so explicitly controlled connection probes also use high and all reasoning-capable tasks may use the Provider ceiling from their first request. The frozen GLM and DeepSeek semantic/recovery runs already used high and the 65,536 ceiling, so their evaluated semantic request behavior did not change. Deterministic protocol/policy tests cover the broader rule; the frozen Holdouts were not repeated because that would add no new semantic evidence.

## Decision

The V3.7.5 two-model qualification gate passes for the frozen profiles and scope. This is a dated bounded qualification, not a universal model-accuracy promise or a model ranking. Safe run metadata is in `docs/acceptance-evidence/v3.7.5-model-run-summary.json`.
