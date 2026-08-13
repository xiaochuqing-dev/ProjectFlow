# V3.8.5 Final Acceptance

Decision: PENDING_HUMAN_REVIEW_ROUND3. V3.8.5 is NOT PASS.

Round 1 and Round 2 remain immutable NEEDS_REVISION_NOT_APPROVED evidence. On 2026-08-14, correction probe run `31733370522` confirmed that GLM capacity had recovered. Same-head affected run `31733839404` at `73d11250cddce3594d5ddb4ef54cd8c6d652dac7` then passed both 19/19 qualifications and both 11/11 scenario suites with max reasoning. The ProjectFlow Dogfood truthfulness P0 stayed OBSERVED for both Providers; persisted secret, prompt, raw response, reasoning and absolute-path flags are false.

Round 3 is frozen as 30 Story and 8 Chapter entries, balanced 15/4 per Provider. Its manifest binds the same code head, run and six canonical-LF artifact hashes. All human fields remain blank and reviewerCount is 0. Automated closure therefore stops at PENDING_HUMAN_REVIEW_ROUND3; only explicit user approval after the frozen thresholds pass can authorize ready/merge, acceptance backfill, final master CI and branch/worktree cleanup.

PR #15 is OPEN, Draft and unmerged. No Tag or Release exists for this closure.
