# Update history

## ProjectFlow V3.3.4 小阶段修复 - 2026-07-08

补充主视图可读性过滤与模型等待策略修正。模型请求超时从固定 35 秒改为可配置（默认 240 秒，可通过 PROJECTFLOW_MODEL_TIMEOUT_SECONDS 覆盖），复杂分析（开发推进段归并 / 能力分析）不再过早失败。模型失败原因细分为 REQUEST_TIMEOUT / HTTP_401_OR_403 / HTTP_429 / HTTP_5XX / NETWORK_ERROR / JSON_PARSE_FAILED / EVIDENCE_REJECTED / UNKNOWN_CALL_FAILED，前端翻译成具体人话（如"DeepSeek 请求超时""网络连接失败，可能与代理或 baseUrl 有关"）。新增 DisplayContentSanitizer 统一清洗所有进入主视图的内容（开发推进段 title/plainSummary/mainChanges、能力卡片 name/summary/README/简历/面试、本地事实摘要 fallback），去除 commit hash、长 URL、evidenceRefs、JSON 片段、内部枚举、长路径列表、长数字串；超出长度限制截断；无可读中文时用保守兜底。原始证据仍保留在折叠证据细节区。前端主卡片对 plainSummary、mainChanges、能力摘要等加 line-clamp / break-words 兜底，防止长内容撑爆布局。

## ProjectFlow V3.3.3 — 2026-07-07

Analysis progress is now visible end-to-end: the workbench shows the current stage (Git scan / GitHub inspect / model enrichment / persist), elapsed time, and input scale, and long model runs explicitly tell the user the analysis continues and the page can be left. The quality gate became a *marker* (`PASS` / `NEEDS_REVIEW` / `NEEDS_CHINESE_REWRITE` / `NEEDS_EVIDENCE` / `PARTIAL_EVIDENCE` / `LOW_CONFIDENCE`) — model results are retained by default and only fully-unavailable models fall back to local rules. User-visible analysis content (titles, summaries, main changes, capability card names) is forced into natural Simplified Chinese; English commits/paths/identifiers stay in evidence details. Multi-source evidence (local Git, worktree diff, GitHub, Agent results, scan scope) is organized into an analysis input snapshot fed to the model, which judges the real development state flexibly instead of hard-coding GitHub-vs-local priority. GitHub is surfaced on the home screen (not "GitHub 增强") with login guidance (copy `gh auth login --web --clipboard`) and read-only sync refresh (never pull/merge/rebase, never read/store tokens). Model-dependent entries (分析新变化, 分析项目能力) require a configured model and guide the user to configure one instead of fabricating low-quality local-template results. Each completed scan shows an analysis-scope summary of which sources participated.

## ProjectFlow V3.3.2 — 2026-07-07

Development segments now pass a result-level quality gate and expose model, fallback, evidence, worktree, GitHub, remote, fingerprint, and timing diagnostics. GitHub CLI participates as a short-timeout optional enrichment source. The capability page now runs one whole-project analysis and stores independent structured capability cards. Sediment list and detail use the same four-action confirmation flow, and batch new creation is no longer the primary action.

## ProjectFlow V3.3 — 2026-07-06

The primary workflow changed from “今日开发 / evidence bundle / 项目资产字段” to “待整理变更 → 开发推进段 → 建议沉淀 → 项目沉淀”. Scanning now uses a persistent review cursor; suggestions support new, merge, evidence-only, and ignore; subjective empty fields are hidden; Agent write-back uses a structured in-project protocol; and GitHub CLI is optional enrichment.
