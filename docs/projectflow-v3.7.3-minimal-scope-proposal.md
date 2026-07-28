# ProjectFlow V3.7.3 Minimal Scope Proposal

Status: accepted and implemented in V3.7.3. Final quality qualification remains controlled by the dated acceptance report.

## Evidence

The V3.7.2 funded GLM `glm-5.2` probe passed, but that historical complete run failed 19 of 38 cases. All failures were bounded SDK transport timeouts. Successful responses also missed Tool Selection, Dynamic View, Conflict and Repeatability gates. The direct Eval copied production prompt text, while the real end-to-end test proved only 2 of 8 core cases. These observations are retained as the V3.7.3 input baseline, not overwritten by later results.

## Implemented minimal scope

1. Failing-case Provider reliability
   - Removed the test-only 45-second clamp and separated connection, Provider-request and overall-analysis time semantics.
   - Kept transport recovery bounded and SDK retry disabled.
   - Runs the historical latency sample before semantic and complete unchanged-Ground-Truth batches.

2. Tool Selection reliability
   - Requires structured information-gap requests and exposes only objectively eligible capabilities.
   - Preserves registry rejection and contains no production case-ID rules.

3. Dynamic View applicability
   - Uses an objective View registry, then lets the model select and explain only eligible views.
   - Keeps forbidden and unavailable views filtered without deterministic semantic promotion.

4. Repeatability
   - Rechecks important cases after timeout parity is fixed.
   - Keeps the 0.80 threshold and measures agreement on Ground Truth critical Shape、Evidence、Tool、View、Conflict decisions. Accuracy、must-not、unsupported claim、precision and recall remain independent gates, so supported extra content is not falsely counted as instability.

5. Production/eval Prompt parity
   - Production Scout、Final Synthesis 与 direct Eval now use the same `ProjectUnderstandingPromptBuilder`.
   - Ground Truth labels and former fixed Tool Evidence cannot enter the shared Prompt input records; direct Eval executes the real bounded Provider and real High-value gate.

## Explicit exclusions

No Ground Truth edits, threshold reductions, case-ID hardcoding, product UI metrics, V3.8 evolution work, new model architecture, Adapter redesign, Tag or Release.

## Exit rule

V3.7.3 may close only after the unchanged 18-case aggregate and all eight core production-chain cases pass every existing gate with a funded Provider. Until then, V3.8 remains blocked.
