# Confirmed V3.3.4 decisions

- 结构化模型输出统一经过 ModelOutputAdapter；模型负责内容理解和 S 编号选择，后端负责 JSON 适配、字段归一化、逐项校验和真实证据恢复。
- 能力分析允许部分成功并使用 SUCCEEDED_WITH_WARNINGS；单卡片缺字段、重复、英文表达、无效来源编号或证据不足不再自动导致整批失败。
- 能力分析采用“短事务读取、无事务模型调用、短事务原子保存”；新结果可保存前不删除旧候选，已确认能力始终保留。
- 能力分析完整警告只在能力页显示；工作台只保留简短任务状态，轮询临时失败先自动重试。
- Model failure notices are split into plain Chinese reasons (not configured / call failed / invalid response format / invalid evidence reference); the old "增强本地摘要" wording is removed and the result source is always stated as "本地事实摘要".
- Local fallback titles and summaries must be Chinese; raw English commit messages are rewritten or labeled "根据提交记录整理的变更", with originals kept in evidence details only.
- GitHub access lives in the "项目接入" area (local path / model / GitHub together), not only in the pending-changes card.
- The GitHub login wizard offers "打开登录终端" which runs only the fixed whitelisted command `gh auth login --web --clipboard`; the backend never accepts arbitrary commands and never reads, displays, or stores GitHub tokens.
- GitHub refresh reads remote commit info only and never modifies local code (no pull/merge/rebase); the UI states this explicitly.
- Internal enums (CALL_FAILED / LOCAL_RULE / CONNECTED / local_ahead etc.) are never shown raw; a shared `frontend/src/lib/status-labels.ts` translates them to Chinese.
- `evidenceGap` is based on real evidence conditions (not GitHub participation) and carries an `evidenceGapReason`; GitHub not participating with sufficient local evidence is not a gap.
- "分析项目能力" is a recoverable async job (`CAPABILITY_CARD_ANALYSIS`) with stages and progress; refreshing or leaving the page does not lose the task; re-analysis replaces only unconfirmed candidates and preserves confirmed capabilities.

# Confirmed V3.3.3 decisions
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
