<!-- PROJECTFLOW V3.4.4 CONTEXT START -->
ProjectFlow 当前版本为 V3.4.4。后续 Agent 必须按“分析新变化 -> 开发推进段 -> 自动项目事实 -> 项目记录 / 项目记忆 -> 自动项目历程 -> 全生命周期能力地图 -> Project Memory Gateway -> Hermes 只读消费 / Obsidian 长期知识投影”理解产品。正常事实和能力演进不逐条确认，只有异常进入“需要关注”。

开始任务前请阅读 `.projectflow/AGENT_PROTOCOL.md`。完成开发任务后，按协议把结果写入 `.projectflow/agent-results/`。不要删除添加项目、zip 导入、本地项目绑定、模型配置、登录等核心入口。

开发推进段和项目事实必须描述真实发生的开发结果、用户或开发者可感知变化、验证情况和不确定项。禁止用 backend/frontend/docs/config 等目录名、提交数量或“开发推进”空话替代具体摘要。能力主页面以长期 `ProjectCapability` 地图为主，旧 `ProjectCapabilityCard` 和 `completedCapabilities` 仅作兼容档案。

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
- V3.4.4 后下一阶段是 backend business/logic consolidation，再进行完整前端重建。

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
<!-- PROJECTFLOW V3.4.4 CONTEXT END -->

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
