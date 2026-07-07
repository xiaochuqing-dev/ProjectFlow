# Update history

## ProjectFlow V3.3.3 — 2026-07-07

Analysis progress is now visible end-to-end: the workbench shows the current stage (Git scan / GitHub inspect / model enrichment / persist), elapsed time, and input scale, and long model runs explicitly tell the user the analysis continues and the page can be left. The quality gate became a *marker* (`PASS` / `NEEDS_REVIEW` / `NEEDS_CHINESE_REWRITE` / `NEEDS_EVIDENCE` / `PARTIAL_EVIDENCE` / `LOW_CONFIDENCE`) — model results are retained by default and only fully-unavailable models fall back to local rules. User-visible analysis content (titles, summaries, main changes, capability card names) is forced into natural Simplified Chinese; English commits/paths/identifiers stay in evidence details. Multi-source evidence (local Git, worktree diff, GitHub, Agent results, scan scope) is organized into an analysis input snapshot fed to the model, which judges the real development state flexibly instead of hard-coding GitHub-vs-local priority. GitHub is surfaced on the home screen (not "GitHub 增强") with login guidance (copy `gh auth login --web --clipboard`) and read-only sync refresh (never pull/merge/rebase, never read/store tokens). Model-dependent entries (分析新变化, 分析项目能力) require a configured model and guide the user to configure one instead of fabricating low-quality local-template results. Each completed scan shows an analysis-scope summary of which sources participated.

## ProjectFlow V3.3.2 — 2026-07-07

Development segments now pass a result-level quality gate and expose model, fallback, evidence, worktree, GitHub, remote, fingerprint, and timing diagnostics. GitHub CLI participates as a short-timeout optional enrichment source. The capability page now runs one whole-project analysis and stores independent structured capability cards. Sediment list and detail use the same four-action confirmation flow, and batch new creation is no longer the primary action.

## ProjectFlow V3.3 — 2026-07-06

The primary workflow changed from “今日开发 / evidence bundle / 项目资产字段” to “待整理变更 → 开发推进段 → 建议沉淀 → 项目沉淀”. Scanning now uses a persistent review cursor; suggestions support new, merge, evidence-only, and ignore; subjective empty fields are hidden; Agent write-back uses a structured in-project protocol; and GitHub CLI is optional enrichment.
