# V3.3.5 requirements

1. Distinguish model request, response, truncation, JSON syntax, target schema, evidence binding, and persistence failures.
2. Record finish reason, token usage, effective Max Tokens/Temperature, timeout, latency, Provider/model, repair, retry, and partial recovery diagnostics without exposing secrets or raw responses.
3. Perform one compact retry for suspected truncation and retain only complete recoverable items.
4. Store complete normalized content; clamp only list previews and mark legacy ellipsis-ended data for re-analysis.
5. Show a recommended sediment action, target context, consequence preview, concrete confirmation result, and direct sediment link.
6. Associate capability cards with analysis jobs and separate the current successful batch, latest failure, and history while preserving old results.
7. Support Provider testing, editing, explicit key clearing, unique default selection, protected deletion, and user-confirmed duplicate cleanup.
8. Keep existing H2 and PostgreSQL data compatible; never require database deletion.

# V3.3.4 requirements

1. Split model failure notices into plain Chinese reasons (not configured / call failed / invalid response format / invalid evidence reference); remove "增强本地摘要" and always state the result source as "本地事实摘要".
2. Rewrite local fallback titles and summaries into Chinese; do not echo raw English commit messages in user-visible main content; keep originals in evidence details.
3. Surface GitHub access in the "项目接入" area (local path / model / GitHub together), not only in the pending-changes card.
4. Provide a GitHub login wizard: "打开登录终端" (fixed whitelisted command only), "复制登录命令", "重新检查"; "查看安装说明" when not installed. Never read, display, or store GitHub tokens.
5. State clearly that GitHub refresh reads remote commit info only and never modifies local code (no pull/merge/rebase).
6. Never show raw internal enums (CALL_FAILED / LOCAL_RULE / CONNECTED / local_ahead etc.); translate via shared status-labels.ts.
7. Base evidenceGap on real evidence conditions (not GitHub participation) and include an evidenceGapReason.
8. Make "分析项目能力" a recoverable async job (CAPABILITY_CARD_ANALYSIS) with stages, elapsed time, and input scale; survive refresh/leave; re-analysis replaces only unconfirmed candidates and preserves confirmed capabilities.

# V3.3.3 requirements
# V3.3.3 requirements

1. Scan from the last confirmed review cursor, not only today.
2. Group new evidence into development segments.
3. Generate evidence-backed suggested sediment.
4. Require users to confirm new, merge, evidence-only, or ignore.
5. Hide subjective fields without hard evidence by default.
6. Preserve add-project, zip import, and local-path binding.
7. Use `.projectflow/AGENT_PROTOCOL.md`, structured Agent results, and an AGENTS entry rule.
8. Keep GitHub CLI optional and local Git fully usable without it.
9. Reject directory-level, count-only, duplicated, or behavior-free development summaries.
10. Persist scan fingerprints, model/fallback diagnostics, worktree state, remote relation, and timings.
11. Use **分析项目能力** to generate 3-8 structured candidates from confirmed project evidence; do not use legacy capability strings as the primary source.
12. Keep list and detail sediment confirmation on the same four-action V3.3 flow.
13. Show analysis stage progress (stage / message / elapsed time / input scale) during 分析新变化; long model runs must not degrade on a timeout.
14. Retain model results by default and tag quality issues (marker, not batch rejector); fall back to local rules only when the model is fully unavailable.
15. Force user-visible analysis content into natural Simplified Chinese; English commits/paths/identifiers stay in evidence details only.
16. Surface GitHub on the home screen as “GitHub” (not “GitHub 增强”) with login guidance and read-only sync refresh (never pull/merge/rebase, never read/store tokens).
17. Organize multi-source evidence into an analysis input snapshot fed to the model; the model judges the real development state flexibly, not by hard-coded GitHub-vs-local priority.
18. Model-dependent entries (分析新变化, 分析项目能力) require a configured model; when missing, show facts-only and guide the user to configure a model.
19. Display the analysis scope (which sources participated, uncommitted/remote-unsynced content, evidence gaps) after each scan.
