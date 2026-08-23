<!-- PROJECTFLOW V3.9 CONTEXT START -->
ProjectFlow V3.9 正在 `codex/v3.9-project-continuity-closure` 开发。V3.9 必须在 V3.8.5 同一来源事件、History Snapshot、Window Planner/Checkpoint、Correction、Agent Context v2、Gateway/Hermes/Obsidian 链上完成持续闭环；禁止建立第二套 History、Fact、增量、向量或投影引擎。实现前冻结的 30 个 Calibration/Holdout 与硬门位于 `docs/acceptance-evidence/v3.9/continuity-ground-truth.json`，case ID、预期和阈值不得进入 production Prompt。

Continuity refresh 仍是显式持久化 Job。no-change 必须 0 模型请求并保持 Story、Thread、Chapter、Current State、Context Package 与 Obsidian 语义身份稳定；小 delta 只允许重算 affected scope。rewrite/delete/restore 保留 Event 并公开 STALE/INVALIDATED。修正只能在工程层证明旧成员仍安全属于同一稳定目标时续接，否则必须冲突，绝不静默丢失或错绑。Current Project State 只是 corrected persisted history 的派生读模型，不是 ProjectFact。GET、Gateway、Hermes 与 Obsidian 禁止扫描或调用模型。V3.9 不做 daemon/watcher、最终 GUI、V4 工作、Tag 或 Release。

<!-- PROJECTFLOW V3.8.5 CONTEXT START -->
ProjectFlow 当前版本为 V3.8.5。后续 Agent 必须按“真实项目材料 -> 规范化来源事件 -> Technical Atom -> Primary/Supporting Change -> 可读变化故事 -> 动态时间篇章 -> 演变链 -> 原始事件与 Evidence 下钻 -> 当前结果 -> 人与 Agent 继续工作”理解产品。项目历程是通用主轴，Capability 只是部分项目的可选视图。

2026-08-24 最终签字规则：项目所有者已明确批准 V3.8.5 合并并要求不再等待本轮量化人工评分。必须把该结论写成 `PASS_BY_EXPLICIT_OWNER_OVERRIDE`，不得伪造 Story/Chapter 1-5 分或声称原定量阈值已经得到证明。Round 1/2/3 与 Final Chapter 冻结工件继续原样、人工字段继续空白；独立签字证据为 `docs/acceptance-evidence/v3.8.5/final-human-signoff.json`，`reviewerCount=1` 且必须披露 single-reviewer、最终批准未重确认项目计数和 P0 只按“没有新报告”记录的限制。PR #15 已以 `29c154eb618ca43edf58c631c14cc1d296e14f3f` 合入 master，merge 后 run `32652683003` 与根启动器通过；回填证据为 `docs/acceptance-evidence/v3.8.5/final-acceptance-backfill.json`。完成 backfill merge 与最终 master 验证后才清理两条 V3.8.5 分支；继续禁止 Tag 和 Release。

V3.8.5 RC3 追加规则：每个用户可见强状态 Claim 必须绑定同一 subject/action 的直接 Technical Atom 与 Evidence；同 Commit、同 Story、时间接近、相同区域或 Supporting 关系只提供间接上下文，不能提升状态。`project-area-*` 是宽泛工程区域而非具体功能主体，只能形成 OBSERVED Claim，精确主体仍按直接 Evidence 判定。模型输出仍只允许 Story 的 storyId/humanTitle/oneSentenceSummary/beforeWording/changeWording/afterWording/reason/reasonEvidenceRefs/unknownWording 与 Chapter 的 chapterId/title/summary。工程层唯一维护 Claim state、direct/indirect Evidence、role、primaryStoryId、supportingChangeRefs、storyRefs、事实语义、冲突、时间边界和当前性。Story 与 Chapter 的一次安全重生成必须各自使用原始有界输入和匹配 schema，Chapter repair 禁止引用 Story 专用的 OUTPUT_TEMPLATE_JSON。若 Provider 的标题/摘要组合没有明确动作、对象和受支持结果，必须保留已校验的确定性标题/摘要，并用 `MODEL_VALIDATED_WITH_DETERMINISTIC_TITLE` 与计数 diagnostics 公开该回退。Round 2 已冻结为 NEEDS_REVISION_NOT_APPROVED；2026-08-24 之前的自动门禁仍不能代替真人，之后只允许按上述独立 owner-override 签字推进，不能反向改写旧 Gate。

V3.8.5 大众可读历程与用户修正规则（后续 Agent 必须遵守）：
- Raw Event、Technical Atom、Primary Story、Supporting Change、Thread 和 Chapter 是分层派生展示；ProjectFact 仍是唯一强事实来源，任何展示修正都不得改写 Fact、Event、Commit 或 Evidence。
- 默认第一层必须表达动作、可理解对象和有证据支持的结果；路径、类名、SHA、Evidence ID、内部枚举和泛化模板只能在工程详情中出现。Before/Change/After、后续结果、未知、冲突和覆盖限制必须保持可达。
- 确定性分组负责成员、角色、时间、transition、authority、Evidence 和事件守恒；模型只能在已知 ID、受约束主体和有界 Evidence 状态内改写标题、摘要与 Before/Change/After 措辞，失败时显式降级。
- 历史语义使用稳定、有界、多窗口、可缓存、可断点的执行。窗口 cache key 绑定来源指纹、strategy/Prompt 版本、窗口身份和展示修正 revision；失败、取消、跳过和未处理范围不得伪装成完整成功。
- 用户修正使用 `USER_DECLARED_PRESENTATION`，必须持久、可审计、可回退并处理来源重写/乐观冲突；Gateway、Agent Context、Hermes、Frontend 和 Obsidian 读取同一修正后视图。
- Obsidian CORE 只投影概览、索引、可读篇章、置顶/修正结果和少量高价值 Thread；全部 Story/Thread 与审计投影只能显式选择。V3.8.5 不新增 Tag、Release、daemon、watcher、最终 GUI 或通用 RAG。

<!-- PROJECTFLOW V3.8.0 CONTEXT START -->

V3.8.0 项目历程重建规则（后续 Agent 必须遵守）：
- ProjectFact 仍是唯一强事实来源。ProjectHistoryEvent 是来源事件库存，ProjectHistorySnapshot、Chapter、Story、Thread 都是可替换派生读模型，不能反向晋升或改写事实。
- 只有显式刷新 Job 可以发现 Git、文件、文档、Agent Result 或可选 GitHub 元数据并运行模型。所有 History GET、Gateway、Hermes 和 Obsidian 读取必须只读持久化结果。
- 工程层固定事件成员、时间顺序、transition、authority、epistemic status 与 Evidence。模型最多一次、只能改写已知 story/chapter 的中文表达，不能新增 ID、成员、时间、原因、证据、里程碑、成熟度或成功判断。
- 原因必须有明确 eligible Evidence；否则保持空并显示 UNKNOWN。Commit message、文件名、Agent 声明、模型共识和 README 宣传都不能单独证明原因或结果。
- 大历史必须分页、有界、缓存和增量处理，不得逐 Commit 调模型。历史 rewrite 必须保留旧事件并标记 STALE/INVALIDATED，不能删除不利记录或静默沿用旧摘要。
- 第一层展示“发生了什么”；SHA、路径、文件数、Evidence ID 和内部状态只能在原始事件或 Evidence 下钻中出现。
- Obsidian 零插件官方 URI 是基线；Advanced URI、REST/MCP、Dataview/Bases 都只能可选。用户 frontmatter、managed block 外内容、移动笔记和旧 Capability 笔记必须兼容。
- 不得新增最终 GUI、通用看板、Git 客户端、Vault 管理器、Agent 管理器、Provider 排行榜、Token 仪表盘、通用 RAG、daemon、Tag 或 Release。

<!-- PROJECTFLOW V3.7.5 CONTEXT START -->
ProjectFlow 当前版本为 V3.7.5。后续 Agent 必须按“开放世界项目材料 -> 有界 Evidence Discovery 与 Content Map -> Provider-neutral Semantic Scout -> 注册 Capability 执行与引用校验 -> 七种事实状态严格分层 -> ProjectFact 唯一事实源 -> 任务相关 Agent Context Package -> Candidate Write 与局部复验 -> Timeline/Capability/Evolution/Projection 只读消费”理解产品。

V3.7.5 Cross-model Strong-fact / Product Constitution / Agent Context 规则（后续 Agent 必须遵守）：
- 产品宪法唯一权威来源是 `docs/projectflow-v3.7.5-product-constitution.md`。只有 project-bound `OBSERVED` 和独立工程验证的 `VERIFIED` 可进入强事实主链；`DECLARED`、`INFERRED`、`CONFLICTED`、`UNKNOWN`、`PROCESS_EVIDENCE` 永不因模型、Agent 或共识晋升。
- Scout 使用 Evidence Ledger。小证据集必须逐项处置；每个 eligible capability 必须恰好 REQUEST 或 SKIP 一次。契约缺口写入 `SemanticContractDiagnostics` 并以 `FAILED_DEGRADED` 对外披露，不能静默成功。
- 生产与 Eval 共用 Prompt contract v3、Semantic Scout v13、Final Synthesis v7。Ground Truth、case ID、文件名答案、阈值和模型名不得进入 Prompt 或业务特判；V3.7.4 Frozen Holdout 答案保持冻结。
- Context Package v2 必须任务相关、revision-aware、project-isolated、可追溯，保留冲突、UNKNOWN、unread scope、limitations、range 和确定性 package revision。GET 不调用模型。
- Agent Work Result 只能写 Candidate。changed file 可由工程层有界复读并绑定 hash；Agent 的行为、命令和测试声明仍是 `PROCESS_EVIDENCE`，提交 `OBSERVED`/`VERIFIED` 必须在任何写入前被拒绝。
- 局部复验只允许 `VERIFY_FACT`、`REFRESH_EVIDENCE`、`REREAD_RANGE`、`VALIDATE_CURRENTNESS`、`RESOLVE_PACKAGE_LATEST`，复用固定 Git 命令和有界项目内文件读取，不重跑整个项目，不修改 ProjectFact。
- Timeline 模型摘要是 `INFERRED` 且 `NON_AUTHORITATIVE`。用户阶段/里程碑是 `DECLARED`，模型阶段/重点是 `INFERRED`；原始事件不能因评分、折叠或摘要被删除。
- 不持久化或返回 Key、Authorization、raw response、reasoning、完整 Prompt、完整文档、patch 或绝对路径；敏感文件只读 metadata。不得建设模型排行榜、通用 RAG、Agent Manager、最终 GUI、Tag 或 Release。

V3.7.3 Long-running Multi-provider Reliability / Prompt Intelligence 规则（后续 Agent 必须遵守）：
- Connection Timeout、Provider Request Timeout、Overall Analysis Deadline 必须分离。AUTO/UNLIMITED 不设置隐藏短总体截止时间；FINITE 尊重显式用户值。坏连接仍有界、transport retry 最多一次、取消和 heartbeat 始终有效。
- 不得为了响应速度、Token 或成本静默减少 Evidence、必要深读或满足门控的 Final Synthesis。当前质量模式是显式 QUALITY_FIRST。
- 耗时、Token、请求数与费用只作过程诊断，不得作为质量缺点、降档理由或自动减少思考的依据。显式支持 reasoning control 的 Responses/Chat Provider 在连接、语义与恢复请求均使用 high；reasoning 任务首次请求即可使用用户配置的宽松 Provider 上限，上限不是消耗目标。
- Semantic Scout 与 Final Synthesis 的生产和 Eval Prompt 只能通过共享 `ProjectUnderstandingPromptBuilder`；Ground Truth、case ID、期望标签、评分和门槛不得进入 Prompt。
- 工程系统只负责广泛发现、安全采样、客观来源分类、多样性、allow-list、capability/view 可用性和结果验证；不得从文件名、类型、README 身份或内部采样分数决定语义 HIGH/LOW。
- 模型负责结合整个项目判断 Evidence 语义重要性、信息缺口、深读需要、适用视图、冲突和当前性；请求只能包含 eligible capability name 和已知 evidence ID。
- Provider compatible 不等于 quality qualified。OPENAI_RESPONSES、OPENAI_CHAT_COMPLETIONS、ANTHROPIC_MESSAGES 共用事实和 Prompt Contract，不得按 Provider 复制业务规则。
- 内部 Eval 继续只存在于测试、CI artifact 和阶段报告。原始 18-case Ground Truth、38-run 公式和门槛不得修改；V3.8 只有在真实 GLM、八个生产链案例、安全和 CI 全部通过后才可放行。
- 不创建 Git Tag 或 GitHub Release，不提交 Key、raw response、reasoning、完整 Prompt、绝对路径或未脱敏项目内容。

<!-- PROJECTFLOW V3.7.2 CONTEXT START -->
V3.7.2 兼容基线仍然有效。后续 Agent 必须按“任意真实输入 -> 有界 Evidence Discovery -> Evidence Source Map -> Semantic Scout -> 自适应分析计划 -> 注册 Capability 执行 -> High-value Evidence Gate -> 条件 Final Synthesis / 当前结果降级 -> Dynamic Project Profile -> Historical Coverage -> 证据支持的演进 -> ProjectFact / Timeline / Capability / Gateway 消费”理解既有链路。

V3.7.2 Real Model Quality / Integration Boundary 规则（后续 Agent 必须遵守）：
- 内部 Eval 只属于测试、CI artifact 和阶段报告；hallucination、accuracy、repeatability、cost、model score 不得进入产品 API、Snapshot、数据库或 UI。
- 空目录、空白文本继续 0 模型；普通语义路径 1 次；只有可审计 High-value Evidence Gate 通过才允许第二次。门控必须暴露 trigger/skipped reasons 与 evidence IDs，不能用“有任意工具文本”代替高价值判断。
- Final Synthesis timeout、取消、Schema 或 Provider 失败必须保留 Stage 1、已校验工具证据和当前降级档案，状态为 FAILED_DEGRADED；不得回滚成旧快照。
- Agent Result 只作 PROCESS_EVIDENCE，不自动成为 ProjectFact；token、耗时、request count、模型名只作 PROCESS_METADATA，不能证明能力、质量、成熟度或完成结果。
- 当前源码不能独自证明历史；README、历史文档与源码冲突时保留证据、unknown/currentness/conflict，不得替用户裁决。
- Tool cache identity 必须覆盖 source/content/structure revision、capability、deep-read target、Provider version、execution/semantic budget、strategy version 和相关 source signatures。
- 外部集成只通过 Evidence Source Adapter、Intelligence Provider Adapter、Projection Adapter；ExternalEvidenceEnvelope 必须 project-bound、bounded、revisioned、redacted、raw-payload-free，且不得自行晋升为事实。
- Semantic Scout / Final Synthesis 当前 Prompt version 为 v3。2026-07-27 使用 GLM `glm-5.2` / OpenAI Responses 完成原样 38-run 重验，但 19 次有界传输超时，Tool recall 0.1667、Dynamic View recall 0.0941、Repeatability 0.4130，真实 `ProjectUnderstandingService.refresh()` 核心场景仅 2/8 通过；质量状态继续保持 NOT PASSED，不得进入 V3.8。原 DeepSeek pilot 与 HTTP 402 历史不得删除。
- ProjectFlow 只拥有项目事实、当前解释、历史覆盖、证据支持的演进及其展示；不得扩张为 Coding Agent、Agent Manager、Provider Switcher、Token Dashboard、模型排行榜、GitHub/GitLab/Obsidian/Hermes 替代品、通用 RAG/Workflow、Parser/SCIP Producer、Updater 或工具控制中心。

V3.7.1 Adaptive Execution / Technical-debt Closure 规则（仍然有效）：
- Scout 和模型只能请求 capability name 与 evidence ID，不能拼命令、参数、绝对路径或任意文件读取；执行只通过 registry 校验后的 Provider。
- FILESYSTEM 和 SCIP 复用既有结果；DOC_READER、MANIFEST、AGENT_RESULT、GIT_HISTORY、GIT_TAG、WORKTREE 使用固定参数、有界 item/字符/超时、取消与失败回退。
- 模型调用总数只能是 0、1 或 2。只有 Execute 产生新的高价值 Evidence 时才允许第二次 Final Synthesis；不得把所有输入强制升级为两次模型调用。
- 模型上下文必须先按 project intake、manifest、document、structure、Git/history、unknown/conflict、tool result 分配预算，再生成完整 JSON；禁止序列化后 substring。
- Discovery 必须保留来源类别与模块多样性、压缩重复候选并暴露 cache/read/drop diagnostics；大类不能挤掉测试、CI、migration、infra 等稀缺证据。
- Historical Coverage 必须拆分 Git metadata、Fact、Tag、document history、Agent result、structural snapshot 和 remote collaboration；提交很多但 0 Fact/0 Tag 不得判为高覆盖。
- 敏感文件只做 metadata；进入模型、持久化诊断或工具摘要前必须脱敏。完整文档、patch、绝对路径、prompt、raw response、reasoning、Key 和 Authorization 不得持久化。
- SCIP producer 不得自动下载、静默构建项目、安装全局运行时或修改用户机器。未完成安全 PoC 时保持生产延期和明确 diagnostics。
- GET understanding/structure/evolution 继续只读持久化结果；执行链只在显式刷新持久化 Job 中运行，不写回 ProjectFact、Timeline、Capability 或已有 Evolution。

V3.7 Universal Evidence Intelligence / Adaptive Analysis 规则（后续 Agent 必须遵守）：
- 不预设项目一定有源码、Git、README、前端、后端、数据库、架构或完整历史；没有证据的视图可以不出现。
- Evidence 是原始或规范化来源；Understanding、Analysis Plan、Dynamic Profile、Historical Coverage 和 Evolution Preview 都是可替换派生智能，不得污染 ProjectFact。
- Discovery 只做有界 metadata、内容信号和安全采样；generated/vendor/binary/credential 不进入模型，绝对路径、Key、Authorization、完整仓库、prompt、raw response 和 reasoning 不持久化或返回。
- Semantic Scout 只接收压缩候选和 evidence ID，Unknown ID 必须过滤。它判断语义角色、项目形态假设、适用维度、当前性风险和工具需要，不负责扫描、Git、Parser、SCIP、PageRank 或 shell。
- Planner 的工具请求必须经 capability registry 校验；模型不得拼命令。FILESYSTEM、MANIFEST、SCIP、GIT、DOC_READER 和 Agent Result 继续复用现有 provider/边界。
- 空目录和空白文本 0 模型；无变化尽量 0 模型；有内容非代码材料允许一次有界语义 Scout；大仓库不得逐文件、逐 Symbol 或逐 Commit 调模型。
- Dynamic Profile section 由证据和适用性决定，允许 0 Architecture、0 Backend、0 Database、0 Timeline、0 Evolution。
- Historical Coverage 必须显示 Git/Tag/Fact 覆盖、周期、gap 和限制。没有历史只展示当前状态；短历史不得伪造成熟阶段；长历史只筛 milestone windows。
- GET understanding/structure/evolution 继续只读持久化结果，不运行扫描、Git、模型或事实写入。刷新继续复用 Durable Job、Model Gateway、Structure SPI 和 Evolution Bridge。
- 不新增 schema 时旧 V3.6 snapshot 允许以兼容字段读取并在下一次主动刷新安全重建；不得为派生智能批量改写 Fact、Timeline、Capability 或已有 Evolution。
- 不自研 parser、grammar、Symbol protocol、Git、PageRank、全文引擎、向量库、通用 RAG、LSP、Agent runtime、workflow engine、daemon、watcher 或 Desktop shell。

<!-- PROJECTFLOW V3.6.0 CONTEXT START (retained compatibility baseline) -->
<!-- PROJECTFLOW V3.4.5 CONTEXT START (retained compatibility baseline) -->
ProjectFlow 当前版本为 V3.6.0。后续 Agent 必须按“当前项目理解 -> 深度结构索引 -> 证据支持的演进桥 -> 分析新变化 -> 开发推进段 -> 自动项目事实 -> 项目记录 / 项目记忆 -> 自动项目历程 -> 全生命周期能力地图 -> Project Memory Gateway -> Hermes 只读消费 / Obsidian 长期知识投影”理解产品。正常事实和能力演进不逐条确认，只有异常进入“需要关注”。

开始任务前请阅读 `.projectflow/AGENT_PROTOCOL.md`。完成开发任务后，按协议把结果写入 `.projectflow/agent-results/`。不要删除添加项目、zip 导入、本地项目绑定、模型配置、登录等核心入口。

开发推进段和项目事实必须描述真实发生的开发结果、用户或开发者可感知变化、验证情况和不确定项。禁止用 backend/frontend/docs/config 等目录名、提交数量或“开发推进”空话替代具体摘要。能力主页面以长期 `ProjectCapability` 地图为主，旧 `ProjectCapabilityCard` 和 `completedCapabilities` 仅作兼容档案。

V3.6 Deep Structural Intelligence / Evolution Bridge 规则（后续 Agent 必须遵守）：
- Structure Index V2 是可重建派生智能，不是 ProjectFact、Timeline 或 Capability 的事实来源。
- ProjectStructureIndexer 保持唯一业务 SPI；MANIFEST_FILESYSTEM 是永久 fallback，SCIP 只通过官方协议消费，不自研语言 Parser 或跨语言 Symbol Protocol。
- Symbol、Definition、Reference 和关系必须来自精确 provider；目录名、文件邻近、README 宣传不得伪装成代码关系。
- Functional Area 成员由代码关系形成；模型只做有界语义命名和解释，不改变成员、关系或证据。
- SCIP 不存在、超限、过期或失败必须降级并显示 diagnostics、coverage 和 unknowns，不得阻断当前理解。
- Evolution Bridge 只连接真实 Git parent/commit、已有 ProjectFact、changed files 与结构区域；无证据时保持空，不编造 before/after。
- Bridge 是幂等派生层，不写回 ProjectFact、Timeline、Capability 或已有 Evolution。
- GET structure/understanding/evolution-bridges 只读持久化结果，不扫描、运行 Git、调用模型或修改事实与派生层。
- V3.6 不新增 watcher、daemon、system tray、开机启动、Tauri/Electron 正式迁移、Installer、Auto Update 或 release。

V3.5 Universal Project Understanding 规则（后续 Agent 必须遵守）：
- ProjectUnderstandingSnapshot 是可替换的当前解释，不是 ProjectFact、Timeline 或 Capability 的事实来源。
- GET understanding/index 只读取持久化结果，不得扫描文件、调用 Git/模型或修改事实和派生层。
- 任意目录先做有界 intake；无 Git 仍可理解当前结构，但历史能力必须明确不可用。
- 结构来源统一经 ProjectStructureIndexer；不得为新增语言自研 Parser 或跨语言 Symbol Protocol，优先 Tree-sitter/SCIP 等成熟边界。
- 模型只接收压缩后的相对路径和证据编号，不逐文件调用；空目录、非代码、无模型和未变化重跑为零模型调用。
- OBSERVED、INFERRED、EXPLAINED、coverage、unknowns 和 CURRENT/STALE 必须保持可诊断，未知 evidence ID 必须过滤或判无效。
- 扫描必须限制文件数、单文件读取、总读取量、详情样本、命令时间和模型上下文；达到上限时降低覆盖率并显示未知项。
- 模型失败不得覆盖上次成功理解；没有旧结果时保留确定性 fallback。Prompt、raw response、reasoning、Key、Authorization 和绝对路径不得持久化。
- scc 只是可选指标 Adapter，缺失时不得阻断；Tree-sitter、SCIP、Agent session Adapter 和 Desktop shell 均不得描述为 V3.5 已内置。
- Desktop GUI 未来必须复用同一 Java Core/API；Tauri/Electron 选择必须经过单独 PoC，不在页面层复制业务规则。

V3.4.5 Backend Intelligence Foundation 规则（后续 Agent 必须遵守）：
- 所有真实模型入口继续登记为 ModelTaskType，并只通过 ModelGatewayService；业务 Service 不得直接依赖 Provider SDK 或拼协议请求。
- Model Gateway 支持 OPENAI_RESPONSES、OPENAI_CHAT_COMPLETIONS、ANTHROPIC_MESSAGES；协议差异只存在于 adapter 层。
- 标准协议必须使用官方 Java SDK。SDK 内建重试必须关闭，重试、取消、并发、预算和恢复只由 ProjectFlow 统一管理。
- Provider 必须显式保存 protocol；旧 DeepSeek、OpenAI-compatible 和 Custom 配置幂等迁移为 Chat Completions，不得改变 Key、模型、默认项或参数。
- finish reason、usage、request ID、reasoning presence、截断、拒绝、过滤、tool-use 和 incomplete 必须归一化；非完整结果不得静默当成成功 JSON。
- 不支持 temperature、JSON mode、structured output 或 reasoning control 的模型不得收到相应字段；用户覆盖必须可诊断。
- 兼容性测试至少覆盖连接、协议响应、结构化结果和 ProjectFlow 最小任务；Mock 不能描述为真实 Provider 验收。
- Prompt、raw response、reasoning 原文、Key、Authorization 和自定义 Header 值不得持久化、记录或返回。
- ProjectFact 仍是唯一事实来源；V3.4.5 不得以质量清理为由批量改写历史事实。
- ProjectMemoryGatewayService 是稳定门面；跨层搜索由 ProjectMemorySearchService 承担，事实证据追踪由 ProjectEvidenceTraceService 承担。
- Gateway、Hermes 和 Obsidian 读取不得触发模型或修改事实与派生层。
- API Key 当前数据库存储仅是本地兼容方案，桌面产品化前必须迁移到操作系统安全存储。
- V3.4.5 完成 backend business/logic consolidation 和模型协议基础；完整前端重建仍是后续阶段，不在本版本扩张。

V3.4.4 Obsidian Projection / Sync 规则（后续 Agent 必须遵守）：
- Obsidian 是知识投影，不是事实来源或数据库镜像；必须复用 Project Memory Gateway，不得直接重拼 Fact、Timeline、Capability Repository。
- 默认 CORE 只生成 Overview、月度 Timeline、长期 Capability、月度 Fact Index 和导航索引，禁止默认一条 Fact 一个文件；EXTENDED 只增加高价值 Fact，FULL_FACTS 必须显式选择。
- ProjectFlow 只能写已配置 Vault 下的专用 managed root，只能替换 ProjectFlow managed block；用户 frontmatter 与 block 外内容必须原样保留。
- 增量同步必须使用稳定 entity ID、source version、content hash、projection version 和 managed-root manifest；UNCHANGED 必须零写入。
- 所有写入使用临时文件、flush/fsync 和原子替换；中断后重试不得产生半文件或重复文件。
- managed block、marker、实体身份或归属冲突时禁止静默覆盖，必须保留用户文件并返回 conflict。
- 路径遍历、绝对路径逃逸、symlink、Windows junction、保留设备名、非法字符、Unicode 和大小写碰撞必须安全处理。
- 用户移动或重命名受管 Note 后以稳定实体元数据重建索引；Capability rename 保留稳定路径，merge 保留旧 Note、历史和 redirect。
- manifest 仅是可重建的投影状态，不是事实来源；损坏时从 Gateway 与受管 Note 恢复，禁止清空 Vault。
- Obsidian 同步默认不调用模型，不写回 ProjectFact、Timeline、Capability 或 Evolution，不输出 diff、原文件、raw Agent result、模型原文、reasoning、凭证、诊断或绝对路径。
- 本阶段只提供仓库内 validate/dry-run/status/sync CLI，不新增前端页面、一级导航、watcher 或全局配置。
- V3.4.5 已完成 backend business/logic consolidation 基础；下一阶段是 Automatic Memory Maintenance，完整前端重建继续延后。

V3.4.3 Project Memory Gateway / Hermes 规则（后续 Agent 必须遵守）：
- Project Memory Gateway 是 Facts、Timeline、Capabilities、Evolutions 对外的统一只读业务语义层；不得让外部消费者直接拼接内部 Repository 或全部 REST。
- ProjectFact 仍是唯一事实来源；Timeline、Capability、Evolution 必须显式标为派生层。
- Recent Changes 和 Timeline 只按 occurredAt / factEventAt 归属；recordedAt、analyzedAt、syncedAt 不得冒充发生时间。
- Gateway GET、Hermes MCP 和 project brief 不得触发模型，不得修改事实、游标、Timeline 或 Capability Map。
- Snapshot、Search、Recent、Timeline、Capabilities、Evolution、Fact Trace、Brief 必须有紧凑默认、分页或硬上限，并保留所有权校验。
- Fact Trace 不返回 diff、绝对路径、fingerprint、prompt、raw response、reasoning、Key 或 Authorization。
- 审计只能保存 operation、数量、耗时、状态、query 长度/哈希和 caller 哈希，不得保存完整私有查询。
- Hermes 仅支持仓库内 local stdio + loopback backend；远程 MCP、Telegram 和写工具不在 V3.4.3 实现。
- Hermes 是消费者，不是新的事实源；Obsidian V3.4.4 必须复用同一 Gateway 语义。

V3.4.2 事实原生能力地图规则（后续 Agent 必须遵守）：
- ProjectFact 是唯一事实来源。
- Timeline 是时间派生层。
- ProjectCapability 是稳定、长期的能力实体。
- ProjectCapabilityCard 是旧版兼容数据。
- 能力刷新不得删除 ProjectFact。
- 能力刷新不得重写已经记录的历史 Evolution。
- 正常能力变化无需用户确认。
- 模型 operation 只是内部协议，不是用户选择。
- 全历史 bootstrap 必须覆盖全部 ProjectFact。
- 不得用 recent-N 冒充完整能力地图。
- 每个输入 ProjectFact 必须被明确分类。
- Timeline Theme 不是 Project Capability。
- 稳定身份不得只依赖名称。
- 未知 capability ID 必须判为无效。
- 未知或跨项目 fact ID 必须判为无效。
- 成熟度必须由确定性规则给出并可解释。
- 模型数值成熟度没有权威性。
- 模型调用必须在事实事务之外执行。
- GET 请求不得触发付费模型调用。
- 刷新失败必须保留上一次成功地图。
- 历史补齐不得在每个 chunk 后重建完整地图。
- 合并必须非破坏性，保留来源能力、关系、演进与 redirect。
- 高风险合并必须进入 attention。
- 旧 CONFIRMED 卡片必须保留，但不是事实；只在来源可追到 ProjectFact 时迁移。
- V3.4.0 ProjectFact 架构保持稳定。
- V3.4.1 Timeline 架构保持稳定。
- V3.3.8 ModelGateway 核心保持稳定。
- 所有能力 API 必须校验 userId 与 projectId 归属。
- 禁止修改操作系统文件或全局机器配置。
- 只能使用仓库内或当前进程内方案解决环境问题。

V3.4.1 自动项目历程规则（后续 Agent 必须遵守）：
- 工作台只分析新变化，不得重新加入 daily 或下一步规划主链。
- `ProjectFact` 是事实来源；Timeline 只是按事实时间组织的 read model / derived layer，摘要不得修改事实。
- Timeline 模型只总结已经记录的事实，禁止下一步、路线图、优先级、未来规划或未发生能力。
- DAY 直接展示事实，不调用模型；WEEK/MONTH 必须显式覆盖该周期全部事实，不得用 recent-N 冒充完整摘要。
- 模型返回的未知 fact ID、跨项目 ID、遗漏覆盖或无法安全去重必须判为无效；覆盖数量必须可诊断。
- Timeline Theme 只属于一个时间段，不是长期 Project Capability。
- 摘要生成必须在事实事务之外执行；GET 请求不得触发模型调用。
- fact after-commit 事件合并 dirty week/month/lifecycle；历史补齐不得在每个 chunk 后重建全部摘要。
- 摘要刷新失败必须保留上次成功内容；用户不保存、不确认 Timeline 摘要，retry 只用于异常恢复。
- DevLog / Daily Review 只作旧链接兼容，项目历程是主时间视图。
- V3.4.0 的 ProjectFact、FactCursor、history 架构和 V3.3.8 ModelGateway 核心除非有已证明缺陷不得重构。
- 所有 Timeline API 必须同时校验 userId 与 projectId 归属。

V3.4.0 关键决策（后续 Agent 必须遵守）：
- 正常 evidence-backed 项目事实自动记录，不得要求逐条人工确认；人工确认不是 ProjectFact 主链的一部分。
- `DevelopmentSegment` 是单次分析产物，`ProjectFact` 是稳定长期事实；后续分析、历程或能力生成不得删除或批量替换原始事实。
- ProjectFact 只描述已经发生的事情；“下一步建议”、重要性判断和未来规划不得自动写成事实。
- ProjectFact 不按标题相似度自动主题合并；事实指纹优先基于项目、来源 segment/batch 和排序后的 commit、Agent result、evidence 引用。
- 同一 batch/job/retry、reusable batch、服务恢复和 history backfill 必须幂等；并发写入不得生成重复事实。
- `FactCursor` 只在该批 facts 成功持久化后推进；写入失败不得提前推进。
- `NEEDS_ATTENTION` 只承载无有效证据、证据冲突、边界不完整或无法安全去重等异常，不阻塞其他事实、批次完成或 cursor。
- 增量 `FactCursor` 与历史 backfill cursor 必须分离；历史重建不得改变普通增量扫描边界。
- 历史 backfill 必须分 bounded chunk、oldest-to-newest 执行并持久化 checkpoint；禁止一次把完整 Git 历史塞给模型。
- 已有 facts、segments 或 sediments 覆盖的 commit 不得重复模型分析；重启、取消和 retry 从 checkpoint 继续。
- 新的正常扫描不得创建人工 sediment suggestion；`ProjectChange`、`SedimentAction`、`ProjectSediment` 和 `ProjectReviewCursor` 只作 V3.3.x 数据与旧链接兼容。
- 旧 `DevelopmentSegment` 优先迁移为 ProjectFact；有 source segment 的旧 sediment 不得再生成第二份事实，无证据旧数据不得伪装成普通事实。
- 事实时间来自 commit/Agent result/批次证据时间，不得用今天的写入时间冒充历史发生时间；降级时间必须可诊断。
- V3.3.7 job infrastructure 与 V3.3.8 model gateway 保持稳定，不得为本阶段重写。
- 数据库中的 batch、segment、fact、cursor 和 history state 是事实来源；页面缓存只作加速层。
- 所有 ProjectFact、记录、记忆、游标和历史状态 API 必须校验 userId 与 projectId 归属。
- Timeline 与全生命周期 capability map 已分别在 V3.4.1、V3.4.2 实现；本阶段不提前实现 Hermes 正式同步或 Obsidian 正式同步。
- V3.4.0 收尾必须生成 `docs/projectflow-v3.4.0-project-fact-memory-report.md`，只记录实际观察到的测试、性能、H2、PostgreSQL、Playwright、桌面启动、CI、提交和已知风险，不得预写通过结果。

V3.3.8.1 读取与启动可靠性（仍然有效）：
- 分析结果以数据库中的 job、批次、开发推进段、项目事实、游标和历史状态为事实来源；sessionStorage 只用于按项目快速恢复，不得作为永久业务存储。
- 弱读取结果不得覆盖已有完整 batch/segments；只有数据库明确证明不存在时才允许清空。
- 旧批次、正式建议和开发推进段的可空字段必须 null-safe；单条旧数据不完整不得使整个列表失败。
- 工作台第一屏通过轻量 Bootstrap Read Model 读取持久化事实，禁止在该接口执行 Git、GitHub CLI、文件扫描或模型调用。
- 每次完成代码或构建配置修改后，必须验证仓库根目录 `Start-ProjectFlow.bat` 能用相对路径重建并运行当前工作树；桌面入口在工作区干净时先快进同步 GitHub master，存在本地修改时必须保护修改、跳过远程同步并构建当前工作树。成功启动证据记录到 `logs/last-embedded-build.json`。
- V3.3.7 后台任务可靠性和 V3.3.8 模型可靠性已完成；除直接受本轮改动影响外，不得重复重构。

V3.3.8 关键决策（后续 Agent 必须遵守）：
- 所有真实模型入口必须登记为 `ModelTaskType` 并通过统一 `ModelGatewayService`；禁止业务 Service 私自发模型 HTTP 请求。
- 禁止为模型入口硬编码统一 temperature 上限、固定 4000 输出预算或固定 2000 恢复预算。参数必须由 Provider/model capability、任务类型、输入规模和输出结构共同决定并可诊断。
- 不支持 temperature、JSON mode 或私有 reasoning 参数的模型不得收到对应字段。reasoning 原文、Key、Authorization、完整 prompt 和原始响应不得持久化、记录或返回。
- JSON 语法错误、截断、Schema mismatch、证据拒绝必须保持不同语义。Schema mismatch 只允许一次定向重编码；截断与 reasoning 耗尽使用各自恢复类型并受任务请求预算约束。
- Mock/固定模型只证明自动化契约，不能描述为真实 DeepSeek。真实验收必须先小输入，再中等输入，再大输入，并记录入口、规模、有效参数、usage、finish reason、恢复类型和人工质量结论。
- V3.3.8 收尾报告必须更新 `docs/projectflow-v3.3.8-model-reliability-report.md`，明确区分固定模型自动化、真实 DeepSeek 和人工质量抽样，并记录全套门禁、CI Run、提交 SHA 与未解决 Provider 风险。

V3.3.7 关键决策（后续 Agent 必须遵守）：
- 长分析只能通过持久化 Job 执行；重复输入复用活动 job，不得重复调用模型或重复正式写入。
- retry、重新分析、页面重试和恢复入口都不得绕过活动任务唯一性；retry 只允许忽略已完成历史，不能强制创建第二个等价活动 job，并须保留来源 job 关系。
- 取消必须在外部调用、紧凑重试和持久化前检查；取消后不得新增正式结果，已确认沉淀、能力卡片和旧成功结果必须保留。
- QUEUED、RUNNING、CANCEL_REQUESTED、CANCELLED、INTERRUPTED/RETRYABLE、EXPIRED、REJECTED、FAILED 必须保持不同语义和人话提示。
- 线程池、队列、模型 HTTP 并发、请求次数、总耗时和 token 都必须有上限；401/403、取消、配置错误和保存失败不得盲目重试。
- 服务重启只自动恢复尚未外部调用的排队任务；模型请求状态未知时禁止自动重发，避免重复计费。
- 不得把 Mock、固定响应或静态契约描述为真实 PostgreSQL、真实浏览器或真实 DeepSeek 联调。无安全 Key 时真实模型测试必须标记 SKIPPED。
- 测试分层必须分别说明 H2/单元、PostgreSQL 16 Testcontainers、真实前后端 Playwright、固定兼容模型服务和可选真实 DeepSeek 的证据边界。
- 任务 API 必须同时校验 userId 与 projectId 归属，不返回 Key、Authorization、reasoning 原文、请求体、原始响应或未脱敏绝对路径。
- 开发完成至少运行后端测试、H2 兼容、前端生产构建和 Playwright；PostgreSQL Testcontainers 在 Docker/CI 环境运行并作为阻断门禁。
- V3.3.7 收尾报告必须记录测试数量、核心 E2E 范围、PostgreSQL workflow、H2 旧库升级、CI Run、真实 DeepSeek 状态、关键文件和提交 SHA。

V3.3.6 兼容层决策（仅用于旧数据和旧链接）：
- 旧沉淀处理中心、四类 SedimentAction、ProjectChange 和 ProjectSediment API 继续可读，不得重新成为新扫描主链。
- 旧本地草稿和 Agent result 草稿不得在兼容分支中自动升级为人工沉淀建议；V3.4 ProjectFact 是否记录只按客观证据与质量规则判断。
- 旧已确认项目沉淀保留来源批次、涉及文件和能力分析状态；既有能力分析语义保持兼容。
- 能力分析失败不得消耗待分析状态或覆盖上次成功卡片；成功持久化后才更新沉淀参与状态。
- Git、文件、GitHub、Agent result 和模型等外部耗时调用不得放在方法级长事务中。

V3.3.5 关键决策（仍然有效）：
- 模型链路必须区分请求、响应、截断、JSON 解析、目标结构识别、证据绑定和持久化阶段。诊断保留 finish reason、token usage、实际 Max Tokens/Temperature、超时、Provider/model、紧凑重试与部分恢复结果，不显示 Key 或原始响应。
- 模型疑似截断时执行一次紧凑重试；截断根数组中已经完整的条目可以保留并标记警告，不能笼统归为“格式无效”。
- DisplayContentSanitizer 只负责规范化，不再在持久化前截断。列表使用展示层预览，详情展示完整内容；旧省略号数据标记后引导重新分析，不假装恢复。
- 旧沉淀确认兼容页继续使用“系统推荐 + 后果预览 + 明确结果”，但该规则不适用于自动 ProjectFact 主链。
- 能力卡片关联分析 job。页面区分当前成功批次、最近失败和历史；失败不替换上次成功候选，已确认能力始终保留，旧版无 job 卡片标为来源未知。
- Provider 支持测试、编辑、唯一默认、删除保护和用户确认后的重复清理。Key 留空保留，只有显式勾选才清除；新模型任务只使用明确默认项。

V3.3.4 仍有效的关键决策：
- 模型失败提示人话化：删除所有用户可见的“增强本地摘要”说法，统一改为“本地事实摘要”。按原因拆分：未配置 / 调用失败 / 返回格式无效 / 证据引用无效，用户一眼看懂模型有没有参与、为什么没参与、当前结果是什么来源。
- 本地事实摘要也必须中文化：用户可见主内容（title / plainSummary / mainChanges / userVisibleValue / 能力卡片名）禁止直接复读英文 commit message。英文原文只能出现在证据细节里。无法可靠转写的英文标题标为“根据提交记录整理的变更”。
- GitHub 接入入口前移到“项目接入”区域（本地路径 / 模型 / GitHub 同属项目接入状态），不再只藏在待整理变更卡片里。
- GitHub 小白接入向导：未登录时提供“打开登录终端 / 复制登录命令 / 重新检查”；未安装时提供“查看安装说明 / 重新检查”。“打开登录终端”只执行固定白名单命令 `gh auth login --web --clipboard`，不接受前端传入任意命令，不读取/展示/保存 token。
- GitHub 刷新只读取远程提交信息，不会修改本地代码（不会 pull、merge、rebase）。UI 必须明确说明这一点。
- 分析口径不直接暴露内部枚举（CALL_FAILED / LOCAL_RULE / CONNECTED / local_ahead 等），统一翻译成中文人话。前端使用 `frontend/src/lib/status-labels.ts` 共享映射。
- evidenceGap 不再因为 GitHub 未参与就默认 true。证据缺口基于真实条件判断（只有 Agent result 无代码 / 代码变化无解释证据 / 远程领先未同步 / 本地远程分叉 / 只有未提交变化无解释等），并记录 evidenceGapReason。
- 能力分析改为可恢复异步任务（CAPABILITY_CARD_ANALYSIS job type）。点击“分析项目能力”创建 job，后端异步执行并推进阶段（LOAD_EVIDENCE / MODEL_CAPABILITY_ANALYSIS / PERSIST_CAPABILITY_CARDS / SUCCEEDED / FAILED）；前端轮询，刷新/离开页面后回来能恢复任务状态；完成后重新拉取能力卡片。重新分析只替换未确认候选，已确认能力保留。

V3.3.3 仍有效的关键决策：
- 分析新变化必须显示阶段进度（stage / stageMessage / 已等待时间 / 输入规模）。
- 模型结果保留优先，质量门槛改为标记器（PASS / NEEDS_REVIEW / NEEDS_CHINESE_REWRITE / NEEDS_EVIDENCE / PARTIAL_EVIDENCE / LOW_CONFIDENCE），不再整批丢弃模型结果。只有模型完全不可用（未配置 / 调用失败 / 未返回 / 无法解析 JSON / 证据完全不可用）才回退本地规则。
- 多来源证据（本地 Git / 工作区 diff / GitHub / Agent result / 扫描范围）要整理成分析输入快照交给模型，模型基于证据灵活判断真实开发状态，不写死优先级。
- 需要模型理解的入口（分析新变化、分析项目能力）必须有模型配置前置检查；未配置模型时不生成低质量本地模板结果，明确提示去配置模型。
- 规则负责证据事实，模型负责灵活理解；正常客观事实自动记录，用户只处理主观编辑和异常关注项。
<!-- PROJECTFLOW V3.6.0 CONTEXT END -->
<!-- PROJECTFLOW V3.7.2 CONTEXT END -->
<!-- PROJECTFLOW V3.7.3 CONTEXT END -->

# ProjectFlow Local Rules

For substantial ProjectFlow work, read `PROJECT_CONTEXT.md` first, then inspect only the task-relevant docs and source files.

Keep changes aligned with the current direction: ProjectFlow is a developer workbench for project understanding, agent result review, project profile maintenance, daily review, and asset output. Do not drift it toward a generic Kanban/admin app.

## Ponytail-Inspired Redundancy Control

Reference: `DietrichGebert/ponytail` on GitHub. Use this as an instruction pattern, not as a project dependency.

Before adding code, stop at the first rule that works:

1. Does this need to exist at all? If not, skip it.
2. Can existing project code, standard library, browser/native platform, or installed dependency do it? Reuse that.
3. Can the change be one focused line or one focused helper? Prefer that over new abstractions.
4. Only then write the smallest implementation that satisfies the current requirement.

For ProjectFlow specifically:

- Prefer deleting or consolidating duplicated cards, DTO mappings, API wrappers, and page-local helpers before adding new ones.
- Do not create new services, components, hooks, entities, or dependencies unless existing boundaries cannot cleanly handle the requirement.
- Keep detailed information in focused detail pages; do not duplicate the same summary across dashboard, project profile, tasks, and outputs.
- Mark intentional shortcuts with a `ponytail:` comment only when there is a real ceiling and a clear upgrade path.
- This rule never overrides security, ownership checks, trust-boundary validation, data-loss prevention, accessibility, model-failure fallback, or explicit user requirements.
- Non-trivial new logic still needs the smallest useful test or runnable check.
