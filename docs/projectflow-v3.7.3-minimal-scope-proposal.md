# ProjectFlow V3.7.3 Minimal Scope Proposal

Status: proposal only. No V3.7.3 production implementation is included in the V3.7.2 revalidation.

## Evidence

The funded GLM `glm-5.2` probe passed, but the complete run failed 19 of 38 cases. All failures were bounded SDK transport timeouts. Successful responses also missed Tool Selection, Dynamic View, Conflict and Repeatability gates. The direct Eval still copies production prompt text, while the real end-to-end test proved only 2 of 8 core cases.

## Allowed minimal scope

1. Failing-case Provider reliability
   - Make the test-only generic Provider timeout honor the explicitly configured value instead of capping it at 45 seconds.
   - Keep production retry policy unchanged.
   - Rerun only the failing latency sample first, then the complete unchanged Ground Truth.

2. Tool Selection reliability
   - Analyze unnecessary `FILESYSTEM`, Git and SCIP requests in successful GLM outputs.
   - Preserve registry rejection and do not add case-ID rules.

3. Dynamic View applicability
   - Normalize only evidence-supported view names and inspect why successful responses produce low expected-view recall.
   - Do not weaken forbidden-view checks.

4. Repeatability
   - Recheck important cases after timeout parity is fixed.
   - Keep the current Jaccard formula and 0.80 threshold.

5. Production/eval Prompt parity
   - Expose the existing production Prompt builders through a test-safe input boundary or drive all quality claims through production services.
   - Remove copied test prompt text only when the production DTO construction remains bounded and no Ground Truth enters the Prompt.

## Explicit exclusions

No Ground Truth edits, threshold reductions, case-ID hardcoding, product UI metrics, V3.8 evolution work, new model architecture, Adapter redesign, Tag or Release.

## Exit rule

V3.7.3 may close only after the unchanged 18-case aggregate and all eight core production-chain cases pass every existing gate with a funded Provider. Until then, V3.8 remains blocked.
