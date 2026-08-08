# Update history

## ProjectFlow V3.8.5 RC2 模型与消费者一致性收口 - 2026-08-08

- 模型 schema 缩为 Story/Chapter 措辞，Primary/Supporting、Chapter membership、生命周期和 Evidence 由工程层唯一维护。
- Frontend、Gateway、Agent、Hermes、Obsidian 统一 corrected view 与 presentationRevision；补齐 hidden/pinned、split/merge 和 revision 漂移保护。
- 本地 H2 546、PostgreSQL 16、前端 58、Playwright 9、Hermes 10、Obsidian 25 均通过；真实双 Provider 与人工 30/8 仍为 BLOCKED。

## ProjectFlow V3.8.5 真实资格收口 - 2026-08-07

补充记录真实 Provider 结果：GLM `glm-5.2` Responses 合同通过但 19-case qualification FAIL（20 请求、103,268 token、16 个降级窗口、24 个失败/未处理窗口）；DeepSeek Chat 合同通过但 19-case qualification FAIL（20 请求、79,702 token、14 个降级窗口、24 个失败/未处理窗口）。DeepSeek 11 个真实场景通过 10 个，ProjectFlow Dogfood 因 Primary/Supporting 引用不一致失败；五类非代码场景通过。GLM 真实场景、旧版项目理解/历程入口和人工可读性抽样未运行。PR #15 保持 Draft，未合并、未创建 Tag/Release、未清理分支或 worktree。

## ProjectFlow V3.8.5 大众可读项目历程质量封顶与用户修正闭环 - 2026-08-06

在 V3.8.0 的来源事件和可替换历史快照之上，新增 Raw Event → Technical Atom → Primary/Supporting Change → Story → Thread → Chapter 的多层语义压缩。首层中文优先呈现 Before/Change/After、成果摘要和工程详情下钻；模型只在有界窗口内改善措辞与展示角色，确定性规则保留成员、时间、Evidence 和安全边界。新增窗口 cache/checkpoint、失败/取消/跳过/未处理范围诊断，以及 `USER_DECLARED_PRESENTATION` 的重命名、摘要、分组、隐藏、置顶、合并/拆分、章节声明、恢复自动结果和乐观冲突处理。Gateway、Agent Context、Hermes、Frontend 和 Obsidian 共用修正后的只读展示视图；默认 Obsidian CORE 保持有限密度。确定性、H2、Playwright、Hermes、Obsidian 与 GitHub PostgreSQL 16 门禁已通过；双真实 Provider、非代码项目和人工可读性验收仍待隔离环境补跑，详细状态见 V3.8.5 Acceptance Report。

## ProjectFlow V3.8.0 基于证据的项目历程重建 - 2026-08-03

项目历程取代 Capability Map 成为任意项目类型的通用主轴。新增来源事件库存和可替换历程快照，把 Git、文件、文档、ProjectFact、Agent Result 与可选 GitHub 元数据组织为总览、动态篇章、变化故事、演变链、原始事件和 Evidence 六层只读合同。显式刷新复用持久化 Job；工程层固定成员、时间、transition 和 Evidence，模型最多一次只改善措辞。同秒 Git 事件按 parent 拓扑和 Commit 内类别稳定排序，不再由 project-scoped 哈希或 SHA 字典序影响 Story 边界。Gateway、Hermes、Obsidian 和最小开发者预览复用同一读模型；旧 Timeline、Capability 与 Vault 内容保持兼容。本阶段无新依赖、无 Tag、无 Release，最终 CI、PR 与合并结果以 V3.8.0 Acceptance Report 为准。

## ProjectFlow V3.7.5 跨模型强事实闭环与产品宪法固化 - 2026-08-01

产品宪法成为七种事实状态、Strong Fact Promotion、Agent Candidate、Timeline 权威和外部投影的唯一语义来源。Semantic Scout v13 补齐小证据集、Capability 决策及 Agent Result 深读契约；Context Package v2、Candidate Work Result 与五类局部复验向人和 Agent 提供同一套可追溯项目状态。GLM `glm-5.2` 与 DeepSeek `deepseek-v4-flash` 在冻结 Holdout 和产品 E2E 中完成双模型闭环，首次 DeepSeek 失败与 GLM 降级仍被保留。QUALITY_FIRST 明确耗时和模型用量只作诊断，显式 reasoning profile 的连接、语义和恢复均保持 high，并从首次请求使用用户配置的宽松 Provider 上限。最终 GitHub PR、PostgreSQL CI、合并和 master 元数据仍以 Acceptance Report 为准。

## ProjectFlow V3.4.5 Backend Intelligence Foundation - 2026-07-20

完成真实 ProjectFact/Timeline/Capability/Gateway/Hermes/Obsidian 价值审计；保留 ProjectFact 唯一事实来源。Model Gateway V2 使用官方 OpenAI 与 Anthropic Java SDK 支持 Responses、Chat Completions、Messages，统一 canonical response、finish/usage、动态预算、单一 retry ownership 与分型恢复。Provider 新增协议、端点、认证、超时、能力覆盖和两阶段兼容性档案，旧配置幂等迁移。Project Memory Gateway 保持 API 稳定，跨层搜索和事实证据追踪拆为独立只读服务。下一阶段是 Automatic Memory Maintenance，完整前端重建继续延后。

## ProjectFlow V3.4.4 Obsidian 高价值知识投影与安全增量同步 - 2026-07-20

在 Project Memory Gateway 上新增仓库内 Obsidian CLI，提供 validate、dry-run、status 与 sync。默认 CORE 将项目概览、按真实发生时间组织的月度历程、长期能力、月度事实索引和导航索引投影到专用 managed root，不默认一 Fact 一文件；EXTENDED 与显式 FULL_FACTS 控制独立事实 Note。每个受管 Note 使用稳定实体元数据和 managed block，manifest、source version/content hash、原子替换与 conflict side file 支持 no-op、增量、移动、重命名、merge redirect、中断与 manifest 恢复。路径遍历、symlink/junction、保留文件名、Unicode 和大小写碰撞受到约束，用户内容不被静默覆盖，同步不调用模型、不写回 ProjectFlow，也不新增前端入口。下一阶段是 backend business/logic consolidation。

## ProjectFlow V3.4.3 Project Memory Gateway 与 Hermes MCP - 2026-07-20

在 Fact、Timeline、Capability 和 Evolution 之上增加统一只读 Project Memory Gateway，提供 snapshot、occurredAt recent changes、跨层 search、timeline、capabilities、chronological evolution、fact trace 和 budgeted brief，并用 SOURCE/DERIVED、稳定 ID、时间字段和证据引用保持语义一致。新增安全读取审计，不保存完整私有 query/caller 或凭证。仓库内无依赖 Python stdio MCP 暴露 9 个只读工具，只连接 loopback backend。真实 Hermes 在当前 H2 安全副本上能按发生时间回答 7 月变化、追溯 FactCursor，并对不存在的 Obsidian 正式同步明确无事实；原始 H2 哈希未变化。完整门禁结果写入 V3.4.3 report。

## ProjectFlow V3.4.2 事实原生全生命周期能力地图 - 2026-07-16

在 ProjectFact 与 Timeline 之上新增长期 ProjectCapability、不可破坏的 Capability Evolution、规范化 fact relation、逐 fact coverage、attention 和 map state。系统对全历史分块 bootstrap，之后只处理未覆盖或已变化 facts；模型通过内部 NEW/ENHANCE/ADD_EVIDENCE/MERGE 协议提议，规则完成所有权、全覆盖、稳定身份、成熟度与安全 merge 校验并自动应用。旧能力卡片退出主页面和正常主链，只保留兼容读取及可追溯 CONFIRMED 迁移。42 条旧 attention 按确定性证据规则重新分类，失败刷新保留旧 READY。未修改系统文件或全局机器配置；真实门禁状态记录在 V3.4.2 report。

## ProjectFlow V3.4.1 自动项目历程 - 2026-07-16

在 V3.4.0 ProjectFact 事实层上新增 DAY、ISO WEEK、MONTH、LIFECYCLE 时间 read model。factEventAt 与固定时区 assignment 持久化，统计由数据库确定性聚合；周、月和完整生命周期通过统一 ModelGateway 生成全覆盖派生摘要与期间主题，主题可追溯到 fact、batch 和 evidence。after-commit 事件、fingerprint 和持久化 job 自动维护 dirty scope；历史补齐延后生成，失败保留旧 READY，用户不保存或确认。主导航以项目历程替换每日回顾，旧 DevLog 路由继续兼容。生命周期能力地图与 Hermes/Obsidian 正式同步未在本版本实现。

## 开源 Windows 快速启动入口 - 2026-07-14

仓库根目录新增便携 Start-ProjectFlow.bat，克隆后的 Windows 用户可直接双击。入口不包含个人路径、不修改 Git 历史；首次运行或 package-lock 变化时执行 npm ci，每次重新构建生产前端并启动 Spring Boot/H2。成功运行记录版本、源提交、本地修改标记、依赖状态、前端 Build ID 和就绪时间，旧 start.bat 保持兼容。
## ProjectFlow V3.4.0 自动项目事实与长期记忆 - 2026-07-15

产品主链从“模型建议、开发者逐条确认沉淀”切换为“分析新变化、开发推进段、自动 ProjectFact、项目记录与项目记忆”。DevelopmentSegment 保持分析层，ProjectFact 成为长期事实层；正常证据充分结果自动记录，异常进入 NEEDS_ATTENTION 且不阻塞批次或下一次扫描。新增独立 FactCursor 与 bounded history backfill 方向，旧 ProjectChange、ProjectSediment、ProjectReviewCursor 和 ProjectMemory 保留兼容。本阶段不重做 V3.3.7 job 或 V3.3.8 model gateway，也不提前实现完整 timeline、生命周期能力地图、Hermes 或 Obsidian 正式同步。最终自动化、H2/PostgreSQL、浏览器、性能、桌面启动、CI 和提交证据统一记录到 V3.4.0 独立实施报告，本文不预写结果。

## 桌面最新工作树构建可靠性 - 2026-07-14

修复桌面启动器在存在本地修改时直接拒绝启动的问题。工作区干净时仍先快进同步 GitHub master；存在本地修改时跳过远程写入并完整重建当前工作树，不覆盖用户改动。嵌入启动脚本从 package.json 读取版本，每次成功运行记录源提交、本地修改标记、前端 Build ID 和就绪时间；start.bat、兼容启动入口和桌面 BAT 共用同一构建链。已用真实桌面 BAT 完成 V3.3.8.1 前端 production build、后端 Spring Boot/H2 启动及 3000/8080 健康验证。

## ProjectFlow V3.3.8.1 数据读取可靠性 - 2026-07-13

用真实用户 H2 复现并修复沉淀处理中心历史空字段 500；ChangeBatch、ProjectChange、DevelopmentSegment 读取改为保守 null-safe，旧批次显示“历史数据不完整”，不回填、不删除历史。批次列表按 batches/changes/segments 批量读取，50 批次固定 4 条查询。工作台按项目保存 schema v2 快照，完整分析结果不会被 session-only 弱响应清空；新增数据库只读 Dashboard Bootstrap，在无快照 F5 时快速恢复批次、推进段和待处理数量，次要接口失败只显示局部错误。

## ProjectFlow V3.3.8 真实模型可靠性 - 2026-07-12

统一 6 个真实模型入口，新增 Provider/model capability 与任务级动态参数策略，取消 temperature 0.3、复杂任务 4000 和恢复 2000 的固定限制。结构化输出改用 balanced 多候选扫描、目标集合定位、snake_case/外层包装适配、Schema repair、截断与 reasoning 分型恢复。真实 DeepSeek 通过隔离应用副本完成所有入口调用，ProjectFlow 套娃覆盖 30 提交、148 文件、15 份 Agent result；首次真实 Schema 偏离被复现并修复。旧 H2 启动同时补齐 job status enum、计时列与 nullable worktree flag。

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
