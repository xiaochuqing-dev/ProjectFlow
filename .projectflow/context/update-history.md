# Update history

## ProjectFlow V3.3.7 正式收尾 - 2026-07-11

修复 retry 的 force 路径绕过活动任务唯一性问题，普通创建、retry、重新分析和恢复统一先复用等价活动 job；新 retry 记录来源任务。新增 10 路并发 retry、活动状态复用、成功冲突和隔离测试。Playwright 扩展为 4 条真实前后端核心流程，并使用明确标识的固定兼容模型服务。PostgreSQL 16 Testcontainers 现在覆盖扫描、正式建议、沉淀确认、能力分析、失败保留、并发 retry 和取消。H2 文件库升级测试发现并修复旧行 optimistic version 为空导致首次 flush 失败的问题，验证旧项目、Provider、任务、沉淀和能力卡片无需清库即可升级。

## ProjectFlow V3.3.7 真实验收与任务可靠性 - 2026-07-11

分析任务新增持久化取消、队列位置、心跳、输入指纹、请求/时间/token 预算、失败代码和重启恢复状态。重复活动输入返回同一 job，排队与模型并发均有上限，取消检查覆盖 Git、GitHub、模型及正式保存边界。服务重启会重新排队未开始任务，并把模型调用状态未知的任务标为需用户确认的中断。新增 PostgreSQL Testcontainers、H2 旧行兼容、Playwright 真实前后端流程、GitHub Actions 阻断门禁与显式启用的低预算 DeepSeek 测试。

## ProjectFlow V3.3.6 沉淀处理闭环 - 2026-07-11

修复空正文伴随截断或 reasoning 时的错误分类，紧凑重试采用更低输出预算并限制请求次数；统一模型诊断并拆除外部调用期间的数据库长事务。正式沉淀建议现在只来自有证据的模型结果，本地规则和 Agent result 保留为草稿。新增按时间分组的沉淀处理中心，正式建议逐条确认；已确认沉淀保存来源批次、涉及文件和能力状态，能力分析只消费这些沉淀并回写形成结果。

## ProjectFlow V3.3.5 模型可靠性与确认体验 - 2026-07-10

模型网关新增 finish reason、token usage、真实生效参数、超时、Provider/model、截断与紧凑重试诊断；截断根数组可保留完整条目。DisplayContentSanitizer 不再永久截断正文，列表预览与详情内容分离，旧省略号数据标记后引导重新分析。沉淀确认增加推荐原因、目标详情、后果预览和具体写入反馈。能力卡片关联分析 job，能力页区分当前成功批次、最近失败和历史并支持关闭失败提示。Provider 设置支持编辑、Key 保留/显式清除、唯一默认、删除保护和用户确认后的重复清理。

## ProjectFlow V3.3.4 模型输出适配与任务容错修复 - 2026-07-10

新增统一 ModelOutputAdapter，模型网关可处理 Markdown 代码块、JSON 前后解释、对象或数组、常见外层字段别名、单对象代替数组和尾逗号。能力分析与开发推进段统一使用 S1/S2 来源编号，由后端恢复真实证据，不再要求模型复制内部 evidenceRefs。能力卡片改为逐项校验、去重、保守补全和最多 8 项截断；局部无效来源或缺证据转为警告，不再整批失败。能力分析拆成短事务读取、无事务模型调用、短事务原子替换候选，生成失败时保留旧候选和全部已确认能力。任务新增 SUCCEEDED_WITH_WARNINGS、失败阶段和结构化诊断摘要；能力页集中显示完整结果与折叠诊断，轮询临时失败自动重试。

## ProjectFlow V3.3.4 小阶段修复（第二轮）- 2026-07-08

从根源减少模型调用失败：prompt 瘦身 + 输出预算调整 + 提交数上游收口。模型调用失败的主因不是超时太短，而是 prompt 过大（每个 atom 的文件路径无上限、evidenceRefs 重复文件路径、diffHints 重复 commit subject、无 prompt 大小防护）。修复：每个 atom 发给模型的文件路径截断到 15 个；evidence 只发 commit:hash 不发逐个 file: 路径（validator 仍用完整 evidenceRefs 校验）；diffHints 去掉冗余 commit=subject；新增 prompt 字符预算 45000 超出时截断 atom 列表；开发推进段归并输出 token 从 8000 降到 4000；能力分析 evidence 截断到 10 条、plainSummary 截断到 200 字符、输出 token 降到 4000；项目分析输出 token 从 100000 降到 4000；range scan 加 --max-count=120 安全阀防止 cursor 过期时返回过多提交。backend 136 tests 全部通过。


## ProjectFlow V3.3.4 小阶段修复 - 2026-07-08

补充主视图可读性过滤与模型等待策略修正。模型请求超时从固定 35 秒改为可配置（默认 240 秒，可通过 PROJECTFLOW_MODEL_TIMEOUT_SECONDS 覆盖），复杂分析（开发推进段归并 / 能力分析）不再过早失败。模型失败原因细分为 REQUEST_TIMEOUT / HTTP_401_OR_403 / HTTP_429 / HTTP_5XX / NETWORK_ERROR / JSON_PARSE_FAILED / EVIDENCE_REJECTED / UNKNOWN_CALL_FAILED，前端翻译成具体人话（如"DeepSeek 请求超时""网络连接失败，可能与代理或 baseUrl 有关"）。新增 DisplayContentSanitizer 统一清洗所有进入主视图的内容（开发推进段 title/plainSummary/mainChanges、能力卡片 name/summary/README/简历/面试、本地事实摘要 fallback），去除 commit hash、长 URL、evidenceRefs、JSON 片段、内部枚举、长路径列表、长数字串；超出长度限制截断；无可读中文时用保守兜底。原始证据仍保留在折叠证据细节区。前端主卡片对 plainSummary、mainChanges、能力摘要等加 line-clamp / break-words 兜底，防止长内容撑爆布局。

## ProjectFlow V3.3.3 — 2026-07-07

Analysis progress is now visible end-to-end: the workbench shows the current stage (Git scan / GitHub inspect / model enrichment / persist), elapsed time, and input scale, and long model runs explicitly tell the user the analysis continues and the page can be left. The quality gate became a *marker* (`PASS` / `NEEDS_REVIEW` / `NEEDS_CHINESE_REWRITE` / `NEEDS_EVIDENCE` / `PARTIAL_EVIDENCE` / `LOW_CONFIDENCE`) — model results are retained by default and only fully-unavailable models fall back to local rules. User-visible analysis content (titles, summaries, main changes, capability card names) is forced into natural Simplified Chinese; English commits/paths/identifiers stay in evidence details. Multi-source evidence (local Git, worktree diff, GitHub, Agent results, scan scope) is organized into an analysis input snapshot fed to the model, which judges the real development state flexibly instead of hard-coding GitHub-vs-local priority. GitHub is surfaced on the home screen (not "GitHub 增强") with login guidance (copy `gh auth login --web --clipboard`) and read-only sync refresh (never pull/merge/rebase, never read/store tokens). Model-dependent entries (分析新变化, 分析项目能力) require a configured model and guide the user to configure one instead of fabricating low-quality local-template results. Each completed scan shows an analysis-scope summary of which sources participated.

## ProjectFlow V3.3.2 — 2026-07-07

Development segments now pass a result-level quality gate and expose model, fallback, evidence, worktree, GitHub, remote, fingerprint, and timing diagnostics. GitHub CLI participates as a short-timeout optional enrichment source. The capability page now runs one whole-project analysis and stores independent structured capability cards. Sediment list and detail use the same four-action confirmation flow, and batch new creation is no longer the primary action.

## ProjectFlow V3.3 — 2026-07-06

The primary workflow changed from “今日开发 / evidence bundle / 项目资产字段” to “待整理变更 → 开发推进段 → 建议沉淀 → 项目沉淀”. Scanning now uses a persistent review cursor; suggestions support new, merge, evidence-only, and ignore; subjective empty fields are hidden; Agent write-back uses a structured in-project protocol; and GitHub CLI is optional enrichment.
