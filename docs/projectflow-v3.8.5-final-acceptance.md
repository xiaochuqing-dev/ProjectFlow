# V3.8.5 Final Acceptance

Decision: PENDING_HUMAN_REVIEW_ROUND3. V3.8.5 is NOT PASS.

Round 1 and Round 2 are immutable NEEDS_REVISION_NOT_APPROVED evidence. RC3 production behavior is at `539dfc9802069dec40207179f65b873bf862872c`; validation head `b9e9c2d` additionally makes the recorded Round 2 LF/CRLF hashes portable without changing either frozen file. Run `31586433372` passed both 19/19 qualifications and DeepSeek scenarios 11/11, but GLM scenarios ended 1/11 and 0/11. Correction-only run `31592405476` then reproduced `HTTP 429` in three attempts, so the GLM 11/11 gate, Round 3 freeze and final evidence-head CI are not complete.

Even after automated gates succeed, Round 3 human fields must remain blank until the user reviews them. Only explicit user approval after the frozen thresholds pass can authorize ready/merge, acceptance backfill, final master CI and branch/worktree cleanup.

PR #15 is OPEN, Draft and unmerged. No Tag or Release exists for this closure.
