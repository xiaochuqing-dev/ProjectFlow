# Confirmed V3.4.4 decisions

- Obsidian 是 Project Memory Gateway 的可重建知识投影消费者，不是事实源、数据库镜像或新的模型摘要入口。
- 默认 CORE 只生成 Overview、月度 Timeline、Capability、月度 Fact Index 与三个索引；EXTENDED 只增加高价值 Fact，FULL_FACTS 必须显式选择。
- 同步只写现有 Vault 内的 dedicated managed root，只替换 managed block；用户 frontmatter 与 block 外内容必须保留。
- 稳定 entity ID、source version、content hash、projection version 与 manifest 驱动 deterministic plan；UNCHANGED 零写入，文件原子替换。
- managed 内容、marker、身份、重复实体或路径边界冲突时保留原文件并写 conflict；禁止静默覆盖、清空或目录全量重建。
- 用户移动由实体元数据发现，Capability rename 保持旧路径，merge 保留旧 Note、历史与 redirect。
- repository-local CLI 提供 validate、dry-run、status、sync；不增加前端、watcher、系统配置或用户 Vault 猜测。
- V3.4.4 后先做 backend business/logic consolidation，再进行完整前端重建。

# Confirmed V3.4.3 decisions

- Project Memory Gateway 是 Facts、Timeline、Capabilities、Evolutions 的统一只读业务语义层，不是内部 REST 聚合泄漏层。
- ProjectFact 唯一事实来源不变；Timeline/Capability/Evolution 显式为 DERIVED，所有消费者共享 stable IDs 和 occurredAt。
- Gateway 和 Hermes MCP 只有读工具，GET 不调用模型，不写 facts/cursors/history/map；compact、分页和输出预算有硬边界。
- Fact trace 仅返回有界、脱敏、仓库相对证据；审计只保存 query/caller 哈希与长度。
- V3.4.3 只支持 local stdio + loopback ProjectFlow；远程 MCP 和 Telegram 不实现。
- Hermes 是即时查询消费者；Obsidian 是长期阅读投影消费者，两者都不能成为事实源。
- 前端冻结，核心 Fact/Timeline/Capability/ModelGateway/Job 不无故重构。

# Confirmed V3.4.2 decisions

- ProjectFact 是唯一事实来源，Timeline 是时间派生层，ProjectCapability 是稳定长期能力层；Timeline Theme 不等于 Capability。
- Bootstrap 覆盖全部 ProjectFact 并逐条分类；增量刷新只处理未覆盖或事实版本已变化的输入，不允许 recent-N 冒充完整地图。
- NEW_CAPABILITY、ENHANCE_CAPABILITY、ADD_EVIDENCE、MERGE_CAPABILITY 仅为内部模型协议；正常结果经规则校验后自动应用，无需用户确认。
- stable identity 绑定项目、问题、产品区域与规范语义，不只依赖名称；名称变化进入 aliases。
- Evolution 不被重写，能力 merge 保留来源实体、旧关系、旧演进和 redirect；高风险 merge 进入 attention。
- 成熟度只由 fact、batch、commit、evidence、evolution、时间跨度和 attention 的确定性规则计算，模型 maturity 字段无效。
- source fingerprint、持久化 job 与 coverage 提供幂等、取消、retry 和 restart 语义；GET 不调用模型，失败保留旧 READY。
- 旧 CONFIRMED 卡片在来源可追到 ProjectFact 时迁移；CANDIDATE、NEEDS_CONFIRMATION、IGNORED 和无来源卡片不进入主链。
- V3.4.0 Fact、V3.4.1 Timeline、V3.3.8 ModelGateway 与 V3.3.7 Job 基础设施不重构；所有能力 API 校验所有权。
- 只能修改仓库内代码、测试、文档、项目脚本与项目数据库结构，禁止修改系统文件或全局机器配置。

- 项目历程是 ProjectFact 之上的时间 read model / derived layer；摘要、主题和关系不得修改或替换事实。
- factEventAt 优先 occurredTo、回退 occurredFrom，并在统一 Timeline Zone 下持久化 day、ISO week 和 month assignment。
- DAY 直接读 facts；WEEK/MONTH/LIFECYCLE 通过 ModelGateway 生成派生摘要，必须显式覆盖所有来源 facts。
- 未知、跨项目、遗漏或非法 fact ID 使模型输出失败；recent-N 截断不得冒充完整摘要。
- Timeline output 禁止下一步、路线图、优先级和未来规划；用户不保存、不确认摘要。
- Timeline Theme 是期间局部主题，不是 Project Capability；主题必须通过关系表追溯 facts、batch 与 evidence。
- fact after-commit 事件合并 dirty scope；GET 不调用模型；历史补齐期间延后生成，完成后恢复。
- 失败刷新保留旧 READY 内容；没有模型时事实与确定性统计仍可读，配置模型后自动恢复等待项。
- `/timeline` 是主时间视图；`/dev-logs` 只作 Daily Review 兼容入口。

- ProjectFlow 的核心定位是“自动维护项目从创建至今的长期记忆”。
- 新主链是“分析新变化 → DevelopmentSegment → 自动 ProjectFact → 项目记录 / 项目记忆”；人退出正常事实确认主链。
- DevelopmentSegment 是分析产物，ProjectFact 是长期事实；事实只能描述已经发生的事情，不承载未来建议或重要性判断。
- 正常 evidence-backed MODEL、可靠 partial、LOCAL_RULE + Git evidence、Agent result + 代码 evidence 可按质量规则自动记录；无证据不得伪装成 RECORDED 强事实。
- NEEDS_ATTENTION 只处理证据冲突、不足、边界不完整或无法安全去重等异常，不阻塞其他 facts、batch 完成、FactCursor 或下一次扫描。
- factFingerprint 优先使用来源 segment/batch 与排序后的 commit、Agent result、evidence 引用，不使用标题相似度作为事实唯一性。
- FactCursor 只在事实持久化成功后推进；旧 ReviewCursor 仅用于首次兼容初始化，之后不再决定增量边界。
- history cursor/checkpoint 与 incremental FactCursor 分离；backfill 必须 bounded、oldest-to-newest，已覆盖 commit 不重复模型分析。
- ProjectChange、SedimentAction、ProjectSediment、ProjectReviewCursor 和旧 ProjectMemory 是 V3.3.x 兼容层，新扫描不得继续创建人工建议队列。
- ProjectFact 是未来项目历程、项目能力、Hermes、Obsidian 和成果输出的事实基础；完整消费者不在 V3.4.0 提前实现。

- 分析结果的事实来源是数据库；sessionStorage 仅用于快速恢复；Dashboard 使用轻量 Bootstrap Read Model 校准核心状态。
- session-only 弱响应不得清空已有 batch 或非空 segments；数据库权威响应才可明确清空。
- Dashboard snapshot 使用 projectId 隔离和 schemaVersion 2，旧单 key 只做一次兼容迁移。
- Bootstrap 只读持久化项目、memory、最新 job/batch/segments、轻量事实统计、sessions、兼容待处理统计、分析摘要和 Provider 可用性；禁止 Git、GitHub CLI、模型和文件扫描。
- 历史 batch/change/segment 缺失字段在读取边界保守降级；不做破坏性回填，单条坏数据不得拖垮列表。
- 批次列表使用固定批量查询，不允许恢复逐批次 changes/segments 的 N+1。
- 仓库 Windows 入口使用相对路径、按 package-lock 验证依赖、重建当前工作树且不修改 Git 历史；桌面启动器在工作区干净时快进同步 origin/master，存在本地修改时保护修改、跳过拉取并继续构建。成功运行后记录版本、提交、本地修改标记和前端 Build ID。
- V3.3.7 后台任务和 V3.3.8 模型可靠性边界保持不变。

- 6 个真实模型入口统一登记并通过网关，Provider 测试不再自行发送 HTTP。
- Temperature 配置值不再被全局 0.3 封顶；不支持时省略。任务建议值只用于策略与诊断，不冒充用户配置。
- Max Tokens 按任务、输入规模、输出结构、reasoning 行为和 Provider ceiling 动态计算；取消固定 4000/2000。
- Schema mismatch 进入一次定向重编码；截断和 reasoning 耗尽分别提高预算恢复。所有恢复共享任务请求/token/耗时上限。
- diagnostics 不保存 Key、Authorization、reasoning 原文、完整 prompt 或原始响应。

- 同用户、项目、任务类型和输入指纹只允许一个活动任务，后端通过项目行锁保证并发幂等。
- retry 只能忽略已完成历史，不能绕过活动任务唯一性；新 retry 持久化来源任务和原因。
- 排队和运行任务可取消；模型调用和正式保存前必须重新读取持久化取消状态。
- 执行器、队列、模型 HTTP 并发、总请求次数、总耗时和 token 均使用保守上限。
- 重启时只重新排队从未开始的 QUEUED；模型请求可能已发送的任务标为 INTERRUPTED，禁止自动重复收费。
- PostgreSQL Testcontainers、H2 兼容、生产构建和 Playwright 是独立质量层；真实 DeepSeek 只在显式开关与安全 Key 同时存在时运行。

- 空 content 不等于普通空响应；若 finish reason 为 length、用量接近上限或存在 reasoning 内容，则按疑似截断处理。
- 输出恢复最多一次；V3.3.8 已用分型动态预算取代固定 2000 tokens。
- 旧正式建议仍只按原证据绑定规则读取；该规则不再决定 V3.4 ProjectFact 是否可自动记录。
- 旧沉淀批次、四类处理动作和推荐强度继续作为兼容数据，不得重新成为新扫描入口。
- 能力分析输入改为已确认项目沉淀；失败不改变沉淀的待分析状态。
- 外部 Git、GitHub、模型调用均不放在数据库长事务中。
- 新增数据库字段使用可空或安全默认值，兼容 H2 与 PostgreSQL 既有数据。
- optimistic version 列使用数据库默认值 0 兼容旧行；Java 新实体仍以 null version 进入 persist，避免误判为 detached。
