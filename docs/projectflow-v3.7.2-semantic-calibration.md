# ProjectFlow V3.7.2 Semantic Calibration

Prompt versions:

- `semantic-scout-v3`
- `final-synthesis-v3`

Final Synthesis uses the registered `PROJECT_UNDERSTANDING_FINAL_SYNTHESIS` schema (`dynamicProfile` plus `unknowns`) instead of reusing the broader Scout schema. This removes a real-Provider schema-repair source without adding another client or stage.

## Calibrated rules

1. A filename, package name or README statement is a candidate signal, not proof of frontend, backend, database, architecture or implemented capability.
2. Agent Result is PROCESS_EVIDENCE. It may explain intent or reported verification but does not automatically become ProjectFact or a stable capability.
3. token count, latency, request count and model name are PROCESS_METADATA. They cannot prove completion, quality, maturity or business value.
4. Current source supports current-state claims. Git, Fact, Tag or historical-document evidence is required for history, release or maturity claims.
5. README/source/manifest conflict preserves both evidence sides and emits conflict/currentness/unknown. The model does not choose a winner without currentness evidence.
6. Missing evidence means unknown, not absence.
7. Empty/blank material produces no semantic call; document/script inputs do not receive a code-architecture template.
8. Tool requests contain capability names only. Commands, parameters, absolute paths and arbitrary reads are forbidden.
9. Final Synthesis may modify only claims directly supported by validated new tool evidence.
10. Shapes are atomic multi-label values instead of compound free text; evidence assessments cannot all be empty when substantive evidence exists.
11. Generic dimensions such as analysis/general/summary are rejected by instruction; tool requests and deep-read flags must correspond to a concrete information gap.

## Before and after

V3.7.1 had general evidence and currentness instructions but left Agent Result, usage metadata and current-source/history distinctions implicit. The v2 real pilot then exposed compound shapes, empty substantive results, generic views, broad tools and unstable deep-read choices. V3.7.2 v3 makes those boundaries explicit in both stages, requires atomic/stable outputs, reduces output bounds and includes prompt versions in real-eval metadata.

## Validation

Fixed tests cover Agent Result, PROCESS_METADATA, no-Git history, strange document deep read, forbidden tools and unsupported claim calculation. Real runs record normalized claims for manual review. Unknown evidence IDs remain filtered by production parsers after the model returns. The final v3 real batch remains pending because the only configured Provider returned HTTP 402 after the calibration pilot; the evaluation report therefore remains NOT PASSED.
