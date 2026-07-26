# ProjectFlow V3.7.1 Product Acceptance

Date: 2026-07-26

## Acceptance matrix

| Input | Expected behavior | Result |
| --- | --- | --- |
| empty directory | 0 model, no architecture/history invention, no executable capability | Passed by automated test |
| blank text | 0 model, empty-content profile only | Passed by automated test |
| oddly named non-empty document | Scout requests DOC_READER by evidence ID, deep read is bounded/redacted, conditional second synthesis | Passed by automated test |
| code without model | deterministic intake/structure/profile remains usable, 0 model | Passed by automated test |
| Git repository | fixed GIT_HISTORY/TAG/WORKTREE metadata only; no patch, no model command | Passed by provider tests |
| many documents plus scarce CI/test/migration/infra | scarce categories survive global cap | Passed by diversity regression |
| 1000 commits, 0 Fact, 0 Tag | overall coverage remains 0.25 and period confidence low | Passed by coverage regression |
| old V3.7 snapshot JSON | new diagnostics may be null and the snapshot remains readable | Passed by compatibility regression |
| changed-small-set | unchanged inspections hit cache; changed file is reopened | Passed by cache regression |
| secret-bearing content | known formats/keywords/entropy are redacted; sensitive paths are denied | Passed by redaction tests |

## Real-repository benchmark

Windows local run, no scc executable and no model Provider. Cold scan uses the bounded built-in fallback; LOC is a sampled estimate. Warm means a forced second deep scan in the same process; the actual unchanged production path first compares the inventory fingerprint.

| Repository | Revision | Files / estimated LOC | Scout / categories | V3.7 cold | V3.7.1 cold | Forced warm / fingerprint | Cold content reads | Model |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Spring Petclinic | `f182358d02e4a68e52bdbabf55ca7800288511e7` | 130 / 4,450 | 55 / 8 | 1,857 ms | 1,444 ms | 597 / 11 ms | 60 | 0 |
| ProjectFlow | `bda4017d066d7a858058beb6af0f0a307697396d` + V3.7.1 worktree | 676 / 71,577 | 79 / 12 | 3,598 ms | 1,106 ms | 2,318 / 32 ms | 472 | 0 |
| JUnit | `4782f9e4e4b54167510a9eaf01ff007697f67e2a` | 2,328 / 230,205 | 80 / 9 | 17,180 ms | 1,368 ms | 808 / 64 ms | 1,532 | 0 |
| React | `b685b40d870b90a975da28c8d22ecf0ba910b1a1` | 7,274 / 816,315 | 80 / 10 | 68,512 ms | 1,217 ms | 903 / 162 ms | 1,677 | 0 |
| VS Code | `c3707c7be89bff2c6e20e6f863721bc26593d07e` | 16,448 / 3,585,394 | 80 / 11 | 134,367 ms | 1,628 ms | 1,153 / 559 ms | 1,701 | 0 |

Warm inspection-cache hits equal total file counts and warm content reads are 0 for all five repositories; evidence sample-cache hits are 55/80/80/80/80. The ProjectFlow forced-warm 2,318 ms result is retained as measured rather than replaced by a better rerun; its actual unchanged fingerprint remains 32 ms.

The benchmark is deterministic and supplies no model Provider, so its 0-request/0-token result is factual only for intake, discovery and fallback structure performance. It does not prove semantic model quality.

## Product conclusion

1. Plan truly drives tools: yes. The persisted plan's validated `toolsToInvoke` list is the coordinator input.
2. Tool Result enters Final Synthesis: yes, only reference-validated/redacted prompt evidence is packed into `toolResults`.
3. Strange documents are truly deep-read: yes, DOC_READER opens only planned evidence IDs with relative-path, item and character limits.
4. Many-docs retains structural/manifest/CI evidence: yes in the synthetic quota regression; the real benchmark also records category diversity.
5. Prompt is complete legal JSON: yes in packer tests and persisted `validJson` diagnostics; no serialized substring remains.
6. Historical Coverage is more honest: yes, seven dimensions and period confidence replace commit-volume inference.
7. Large-repository improvement: yes. React cold time fell from 68,512 to 1,217 ms and VS Code from 134,367 to 1,628 ms; warm content reads are 0. LOC is explicitly an estimate without scc.
8. Secret leakage: the tested patterns and sensitive paths do not leak; this is not claimed as mathematically complete detection, and the limitation is explicit.
9. No-model remains usable: yes, deterministic execution/profile and 0 model requests are covered.
10. Out-of-box behavior remains: yes, no new runtime, daemon, account or mandatory user operation was introduced.
11. Unnecessary operation: no. Only capabilities justified by discovery/Scout/default evidence are executed; second synthesis is conditional.
12. Lightweight practical direction: retained. Six focused capabilities reuse the existing job, registry, command executor and Model Gateway rather than adding an agent/workflow platform.

Local deterministic product acceptance and the root launcher are PASS for adaptive execution, evidence safety, compatibility and large-repository bounds. Playwright passed 7/8 in the first full run and the single timing failure passed an isolated rerun; complete GitHub CI remains the final release gate. Real Provider acceptance remains SKIPPED because no user-owned safe key was supplied.
