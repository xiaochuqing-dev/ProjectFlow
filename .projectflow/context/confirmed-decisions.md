# Confirmed V3.3.3 decisions

- Daily UI uses “项目沉淀”; “资产” remains suitable only for broad product positioning.
- Rules collect evidence, models interpret, rules validate, and users confirm.
- Local Git is primary, Agent result is enrichment, and GitHub CLI is optional enrichment.
- ProjectFlow does not decide next goals, in-progress capabilities, or technical decisions for the user.
- Content without a source, confirmation, or hard evidence stays hidden from the default view.
- Models interpret change meaning; rules collect evidence, validate output schema and quality, and expose explicit fallback state.
- **分析项目能力** is the capability page's primary action. Structured capability cards replace legacy strings as the main display source.
- GitHub CLI enriches remote state and commit links but never blocks local analysis.
- 分析新变化 must show stage progress, elapsed time, and input scale; long model runs keep waiting for the full result rather than degrading on a timeout.
- The quality gate is a marker, not a batch rejector: model results are retained and tagged (`PASS` / `NEEDS_REVIEW` / `NEEDS_CHINESE_REWRITE` / `NEEDS_EVIDENCE` / `PARTIAL_EVIDENCE` / `LOW_CONFIDENCE`); local-rule fallback is used only when the model is fully unavailable.
- User-visible analysis content must be natural Simplified Chinese; English commits/paths/identifiers stay in evidence details only.
- The GitHub panel is named “GitHub” (not “GitHub 增强”) and lives on the home screen; refresh is read-only (never pull/merge/rebase) and ProjectFlow never reads, displays, or stores GitHub tokens.
- Multi-source evidence is organized into an analysis input snapshot (local Git / worktree diff / GitHub / Agent result / scan scope) fed to the model; the model judges the real development state flexibly, not by hard-coded GitHub-vs-local priority.
- Model-dependent entries (分析新变化, 分析项目能力) require a configured model; when missing, ProjectFlow shows facts-only and guides the user to configure a model instead of fabricating low-quality local-template results.
