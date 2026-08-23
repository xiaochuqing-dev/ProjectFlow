# V3.8.5 RC3 Product Acceptance

2026-08-24 final decision: the project owner explicitly approved the final package and merge, and waived the quantitative human scores for this closure. Product acceptance is `PASS_BY_EXPLICIT_OWNER_OVERRIDE`; no numeric score or original threshold pass is inferred. The single-reviewer, scope and P0-basis limitations are recorded in `projectflow-v3.8.5-final-human-signoff.md`. V3.9 ENTRY is approved after merge/backfill verification.

2026-08-23 historical note: deterministic Chapter representation and the same-head GPT 5.6 Luna, DeepSeek V4 Flash and Qwen3.7 Plus acceptance matrix were the automated closure boundary. Human Chapter representativeness was still NOT_RUN at that point; see `projectflow-v3.8.5-final-closure-report.md`.

Current decision: `PASS_BY_EXPLICIT_OWNER_OVERRIDE`. PR #15 merged as `29c154eb618ca43edf58c631c14cc1d296e14f3f`; master required CI run `32652683003` and the clean-master root launcher passed. The backfill evidence records the exact GitHub, human, Provider, hash and risk metadata. The pre-signoff blocked analysis below is retained as historical evidence.

Automated implementation completed:

- Claim-level subject/action/state attribution with direct and indirect Evidence.
- Conservative state ceilings, including OBSERVED for broad `project-area-*` subjects while precise subjects keep normal implementation eligibility.
- The exact ae9f skeleton/login P0 and nine additional adversarial contracts.
- Correction and deterministic fallback through the same truthfulness gate.
- Story v12 action/object/result validation with an explicit deterministic title/summary fallback status and aggregate diagnostic.
- Specific Chapter wording, deterministic independent-outcome boundaries and Chapter prompt v6 repair from CHAPTER_SYNTHESIS_JSON.
- Frontend attribution drill-down without changing ProjectFact, raw events, chronology, membership or consumer authority.

Before the title-quality follow-up, 119 affected ProjectHistory/Provider-neutral tests had 0 failures/errors and 4 conditional skips; the new targeted title fallback, Prompt and artifact tests also pass. The final local backend/H2 run is 602 tests, 0 failures/errors and 11 conditional skips after excluding local-Docker PostgreSQL and the pending Round 3 manifest only; Maven exited cleanly in 308.5 seconds. The 19 deterministic Ground Truth cases still pass with all safety counters zero. Root `Start-ProjectFlow.bat -NoBrowser` rebuilt and started Next 16.2.11 plus Spring Boot/H2 from `f3d5204` with current local documentation changes, recorded Build ID `20JnrO0wTzUPAG3ebVDwu`, and exited with no 3000/8080 listener residue.

Historical RC3 state: Provider run `31586433372` passed both 19/19 qualifications and DeepSeek scenarios 11/11, while its GLM scenario attempt and retry failed. Correction-only run `31592405476` later classified the GLM failure as `HTTP 429`. These failures remain evidence, but are superseded for the current Final Chapter gate by same-head run `32609107531`: Luna, DeepSeek and Qwen each passed qualification 19/19 and Chapter scenarios 9/9.

Historical RC3 decision: the product was blocked and PR #15 stayed Draft. The later project-owner override authorized closure; PR #15 is now merged and master checks are green. Tag and Release remain forbidden. The existing npm audit 4 high/0 critical risk remains recorded and was not mixed into acceptance.
