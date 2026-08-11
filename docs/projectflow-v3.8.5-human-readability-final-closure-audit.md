# V3.8.5 Human Readability Final Closure Audit

Current decision: PENDING_HUMAN_REVIEW / NOT_PASS.

Round 1 remains NEEDS_REVISION_NOT_APPROVED and its frozen files were not rewritten. The RC2 implementation keeps facts, role graphs, Chapter membership, chronology and Evidence in the engineering layer; models only rewrite bounded wording and Evidence-backed reasons.

The first Round 2 candidate exposed indexed synthetic subjects in the correction sample. The final code now maps that precise placeholder class to a public label, rejects it in first-layer entailment and evaluator checks, and keeps raw stable subject identity for grouping/window planning. This is Provider-neutral and does not weaken Evidence or Strong Fact rules.

Verification completed:

- Local backend/H2: 579 tests, 0 failures, 0 errors, 5 conditional skips, including immutable Round 1 hashes and the real Round 2 manifest contract.
- Targeted regression: 10 tests passed for subject normalization, first-layer leakage, 96/1,280-item window planning and correction-local invalidation.
- GitHub run `31532558352`: GLM and DeepSeek Flash affected real scenarios both PASS; frontend, Playwright, sensitive-content, Hermes and Obsidian jobs passed. Backend/PostgreSQL in that run failed only because the final Round 2 files did not yet exist at its head, so that run is not the final static CI authority; the evidence commit is governed by its own PR checks.
- Normalized evidence scan: credential pattern, Authorization, Prompt/raw/reasoning persistence, private absolute path and indexed-placeholder matches are 0/false in the two affected artifacts and final worksheet.

Round 2 is now frozen at 30 Story/8 Chapter, balanced 15/4 per Provider, with all human fields blank. The automated evidence is complete, but the human gate is not. PR #15 remains Draft; no merge, acceptance backfill, Tag, Release or branch/worktree cleanup is allowed before explicit user approval and green final GitHub checks.
