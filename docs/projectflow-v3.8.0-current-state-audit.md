# ProjectFlow V3.8.0 当前状态审计

审计日期：2026-08-02

## 审计结论

V3.7.5 已把强事实、通用 Evidence Discovery、持久化任务、Model Gateway、Project Memory Gateway、Hermes、Agent Context Package v2 和 Obsidian 非破坏性投影做成可复用基础。V3.8.0 不需要重建事实系统、模型客户端、Git 客户端或 Vault 管理器。

当前真实缺口是：系统能保存 ProjectFact，并能按日、周、月和生命周期展示事实，也能生成局部结构 Evolution Bridge，但不能把完整来源事件稳定保存为可分页原始层，不能把数百次事件组织成动态时间篇章、变化故事和跨时间演变链，也没有面向未来 GUI 的六层项目历程读模型。

因此 V3.8.0 应新增“来源事件归一化 + 可替换项目历程快照”边界，并复用现有事实、任务、模型、Gateway、Hermes 和 Obsidian 边界。

## 仓库与基线

| 项目 | 审计结果 |
| --- | --- |
| 目标仓库 | xiaochuqing-dev/ProjectFlow |
| 默认分支 | master |
| 阶段起点版本 | 3.7.5 |
| 最新 master SHA | fd5ce827245f4fc4a20ecda15c63fc03313505ab |
| 最近合并 PR | #12，V3.7.5 最终验收证据回填 |
| 功能 PR | #11，V3.7.5 cross-model strong-fact closure |
| V3.7.5 最终功能 master | 2d54d91d67b89190ab8b2ac3f5a92345dd61cd9c |
| V3.7.5 最终验收回填 merge | fd5ce827245f4fc4a20ecda15c63fc03313505ab |
| 开放 PR | 0 |
| Tag | 0 |
| GitHub Release | 0 |
| GitHub 权限 | 已连接账户具有 admin、push 和 workflow 权限 |
| 开发分支 | codex/v3.8.0-project-history-reconstruction |

GitHub 分支列表仍保留多个既往阶段分支。它们在 V3.8.0 开始前已经存在，不属于本阶段可擅自删除的对象。本阶段最终只清理 V3.8.0 功能分支和可能产生的 acceptance-backfill 分支。

## 原工作区保护

原工作区 master 落后远端基线并存在用户修改：

- frontend/playwright.config.ts
- start-projectflow-embedded.ps1
- start-projectflow.ps1
- .projectflow/agent-results/20260717-104723-artifact-size-control/

本阶段没有暂存、覆盖、丢弃或混入这些修改。开发使用独立检出，基线为 fd5ce827。

## V3.7.5 已确认基础

### 强事实与认知状态

- ProjectFact 是唯一持久强事实来源。
- OBSERVED、VERIFIED、DECLARED、INFERRED、CONFLICTED、UNKNOWN、PROCESS_EVIDENCE 七种状态已经锁定。
- 只有项目绑定且 Evidence 有效的 OBSERVED，以及经过独立工程验证的 VERIFIED，能进入强事实路径。
- 模型共识、Agent 完成声明、测试声明和降级结果不能自行升级为强事实。

### Evidence 与模型执行

- 已有通用 Evidence Discovery、来源分类、大文件 Content Map、有界深读和敏感信息出口过滤。
- 已有 FILESYSTEM、MANIFEST、GIT_HISTORY、GIT_TAG、WORKTREE、DOC_READER、AGENT_RESULT 等注册 Capability。
- 模型入口统一登记在 ModelTaskType，并只经 ModelGatewayService 调用。
- 任务、重试、取消、heartbeat、请求超时和 overall deadline 已由持久化 ProjectAnalysisJob 管理。
- Agent Context Package v2 已能按任务、范围、revision、深度和预算生成确定性上下文。

### 现有项目历史相关数据

| 现有结构 | 已能表达 | 不能表达 |
| --- | --- | --- |
| ChangeBatch | 一次增量或历史扫描的范围、首尾 Commit、输入规模、模型诊断 | 全部来源事件的稳定身份、事件关系和历史重写状态 |
| DevelopmentSegment | 一批分析中的语义推进段、Commit/Agent Result/Evidence 引用 | 跨批次长期故事、一个 Commit 多故事、完整原始事件层 |
| ProjectFact | 证据支持的已发生事实、发生时间、Commit/文件/Agent/Evidence 引用 | 低权威来源事件、删除或撤销等未形成强事实的完整原始记录 |
| ProjectTimelineSummary / Theme | 周、月、生命周期的 Fact 摘要和周期内主题 | 动态篇章、非固定月切割、故事前后状态、跨时间演变链 |
| ProjectCapability | 软件项目中的长期能力视图 | 任意项目类型的通用历史主体 |
| ProjectEvolutionBridge | 相邻 revision、Fact、changed path 与结构区域之间的 before/after 桥 | 同一对象多次新增、修改、删除、恢复、替换形成的长期链 |
| ProjectUnderstandingSnapshot | 当前项目的动态档案与 Historical Coverage | 完整时间序列、原始事件分页和变化故事 |

### 当前历史补齐

ProjectFactHistoryService 以每批 25 个 Commit 执行有界历史补齐，能持久化 checkpoint 并避免一次加载完整 Git 历史。这一执行可靠性应保留。

现有链路仍以“每个历史 chunk 进行 WorkSession 扫描和事实生成”为主，没有独立的原始事件库存，也没有跨 chunk 的故事和演变链读模型。V3.8.0 应复用其 checkpoint、活动任务唯一性和失败恢复，而不是继续把 Timeline 扩成第二套历史系统。

## 当前消费者

### Project Memory Gateway

Gateway 已提供 snapshot、recent changes、search、timeline、capabilities、capability evolution、fact trace 和 brief。GET 全部只读，不触发模型。当前 snapshot 和 brief 仍以 Fact、固定 Timeline 和 Capability 为中心，需要增加项目历程读接口，但不能破坏既有接口。

### Hermes

Hermes 是本地 stdio、loopback 后端上的只读消费者，当前工具围绕 snapshot、search、recent changes、timeline、capability、fact trace 和 brief。V3.8.0 应增加项目历程查询工具，保持只读和有界，不让 Hermes 成为新事实源。

### Obsidian

现有投影具备：

- CORE、EXTENDED、FULL_FACTS 三种 profile。
- 专用 managed root。
- 用户 frontmatter 与 managed block 外内容保留。
- stable entity metadata、manifest、backup、redirect 和 conflict。
- 原子替换、symlink/junction/path escape 防护。
- dry-run、status、sync、validate。

当前默认目录仍包含项目概览、按月项目历程、项目事实和项目能力。V3.8.0 应把“项目历程”提升为主入口，并兼容既有 Capability 笔记；不需要重写投影引擎。

## 已确认工程债与限制

- Hibernate ddl-auto=update 仍是当前 schema 演进机制，未安装 Flyway 或 Liquibase。
- 本地 Docker Desktop 在上一阶段不可用，PostgreSQL 16 必须继续由 GitHub CI 阻断验证。
- 前端存在三个既有 high npm audit 发现；自动修复会破坏性降级 Next，不能在本阶段顺手处理。
- 当前没有最终 GUI；本阶段只应交付稳定 read model、API 和最小可读验收产物。
- 当前没有 Tag 或 Release，V3.8.0 也明确禁止创建。

## V3.8.0 必须新增的最小能力

1. 可分页、可筛选、不会因摘要变化而丢失的来源事件库存。
2. 可替换且失败保留上次成功结果的项目历程快照。
3. 总览、动态篇章、变化故事、演变链、原始事件、Evidence 详情六层合同。
4. 显式刷新任务；GET 不运行 Git、文件扫描或模型。
5. 大历史有界、可缓存、可增量，历史重写能使受影响结论 stale。
6. Gateway、Hermes 和 Obsidian 的薄扩展。
7. 小项目、300+ Commit、1000+ 原始事件、删除恢复链和真实临时 Vault 的确定性验证。

## 研究门禁判定

在本审计与配套开源研究、Obsidian 工作流研究、复用矩阵和架构决策提交前，不修改生产代码。研究结论不授权复制 GitButler、GitLab、OpenProject、Gource 或 Obsidian 的实现，也不授权新增未经审计的依赖。
