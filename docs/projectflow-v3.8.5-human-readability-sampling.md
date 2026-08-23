# ProjectFlow V3.8.5 Human Readability Sampling

2026-08-24 closure note: this file preserves the pre-freeze sampling plan and its failed candidate state. Round 3 was subsequently frozen from run `31733839404`, Final Chapter closure used Luna/DeepSeek/Qwen run `32609107531`, and the project owner later authorized `PASS_BY_EXPLICIT_OWNER_OVERRIDE`. PR #15 is merged and post-merge master CI passed; no historical sample or worksheet was rewritten.

Status: PENDING_ROUND3_FREEZE / PENDING_HUMAN_REVIEW_ROUND3.

Round 1 and Round 2 remain immutable NEEDS_REVISION_NOT_APPROVED packages. Round 3 will be generated only from qualified GLM and DeepSeek normalized artifacts produced by the same final code head.

The fixed script selects exactly 30 Story and 8 Chapter, balanced at 15 Story/4 Chapter per Provider. Coverage includes ProjectFlow, non-code, short and long history, generic Commit, one Commit with multiple results, multiple Commits with one result, lifecycle, rename/move, split/merge, unknown reason, conflict, Supporting, correction, the ae9f truthfulness P0, README/API plan plus unrelated code, genuine direct implementation and large/non-code Chapters.

The Round 3 manifest binds the exact 40-character Provider source head and the canonical-LF SHA-256 of all six normalized source artifacts. Each entry binds Provider, relative artifact path, source case, stable entity ID, presentation revision, canonical entity hash and coverage tags. The script rejects an absent/invalid source head, unqualified artifacts, sensitive values, machine absolute paths and indexed placeholders. Round 3 does not overwrite either earlier round.

All human yes/no fields, scores, reviewer name, notes and PASS/FAIL fields are blank at freeze time. reviewerCount remains 0 and model self-scoring is false. The worksheet exposes each selected Story's `Narrative Status`, including deterministic-title fallback where relevant, without converting it into a human score. Story and Chapter averages, core-dimension averages and P0 status remain NOT_RUN until the user completes the worksheet.

Final candidate source run `31586433372` executes from validation head `b9e9c2d`; both qualifications and DeepSeek scenarios passed, while its first GLM scenario attempt is retained at 1/11. It cannot become the Round 3 source unless the running same-SHA GLM attempt 2 passes all scenarios. Final paths and hashes will be recorded only after that result. PR #15 remains Draft throughout this stage.
