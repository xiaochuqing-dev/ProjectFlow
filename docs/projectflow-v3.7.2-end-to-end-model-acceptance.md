# ProjectFlow V3.7.2 Real End-to-end Model Acceptance

Acceptance date: 2026-07-27

## Scope

The test invoked real `ProjectUnderstandingService.refresh()` over isolated temporary project directories and a read-only ProjectFlow self-analysis. It exercised Repository Intake, Evidence Discovery, Structure Index, Historical Coverage, Semantic Scout, Adaptive Planner, `AnalysisExecutionCoordinator`, the high-value evidence gate, optional Final Synthesis, Dynamic Profile synthesis and snapshot persistence/readback.

Repositories and snapshots used in-memory test repository boundaries. Capability execution used the real bounded local Provider. The test did not create ProjectFact, alter real Git history, push a remote repository, call GitHub writes or write an Obsidian Vault.

## Result

Overall: `NOT PASSED`

Corrected bookkeeping result: 2 of 8 core cases passed. The first artifact counted three physical requests in the strange-document case as three logical requests; code was corrected to record two logical stages and three bounded physical requests without rerunning or changing any model output.

| Case | Result | Evidence |
|---|---|---|
| strange-important-document | PASS | `DOC_READER` actually executed, one real Tool Evidence item, Stage 2 triggered, Final Synthesis succeeded, zero invalid Evidence refs, persistence/readback passed |
| small-script | PASS | One logical/physical request, `SCRIPT` and `CODE_PROJECT`, no false architecture expansion, persistence/readback passed |
| frontend-only | FAIL | Stage 1 transport timeout; no semantic snapshot |
| backend-only | FAIL | Stage 1 transport timeout; no semantic snapshot |
| fullstack | FAIL | Stage 1 transport timeout; no final shape validation |
| no-git | FAIL | Stage 1 transport timeout; no final no-Timeline validation |
| agent-result | FAIL | Stage 1 transport timeout; no final non-promotion validation |
| ProjectFlow itself | FAIL | Read-only intake/structure completed, Stage 1 transport timeout |

## Completed production-chain evidence

The strange-document flow recorded these real stages: Repository Intake, Structure Index, Evidence Discovery, Historical Coverage, Evolution Bridge call boundary, Semantic Scout, Capability Execution, Final Synthesis, Dynamic Profile and persistence. It planned `FILESYSTEM` and `DOC_READER`, executed `DOC_READER`, produced one bounded Provider evidence item and cited only real Evidence IDs.

The small-script flow used one logical model stage, skipped Final Synthesis because the high-value gate did not trigger, preserved no-Git history limitations and persisted a readable snapshot.

The six timeout failures were classified as `UnderstandingModelException > ModelTransportException > OpenAI SDK I/O timeout`. No raw exception message, project content, absolute path, request body, response, reasoning or credential was stored in the sanitized artifact.

Corrected sanitized end-to-end artifact SHA-256: `2BBED6CD2BD7D46472CEC320640E25D42E9AA8B0504C8A7E5B9C17C06768C1BA`.

## Decision

The required core end-to-end cases did not all pass. The Stage 2 real Tool Evidence path is proven for the strange-document case, but frontend, backend, fullstack, no-Git, Agent Result and ProjectFlow itself remain unaccepted with glm-5.2 under the current evaluation timeout boundary.
