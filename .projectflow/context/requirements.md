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
