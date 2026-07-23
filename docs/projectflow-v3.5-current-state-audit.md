# ProjectFlow V3.5 Current State Audit

审计日期：2026-07-23

审计基线：当前工作树，版本标识 V3.4.5，分支 master。工作树已有与构建可靠性相关的未提交修改，本阶段不覆盖或回退这些修改。

## 结论

V3.4.5 的事实、历程、能力、模型网关、记忆网关、Hermes、Obsidian 和持久化 Job 资产仍然存在，适合直接复用。V3.5 不需要重写这些层，真正缺少的是一条与浏览器无关、能够先识别任意目录再决定分析策略的 Current Project Understanding Pipeline。

当前整体项目分析主要读取导入 ZIP 的摘要材料；本地工作台扫描要求绑定 Git 目录。无 Git 代码目录、空目录、非代码目录、大型仓库和 Monorepo 尚未拥有统一的 Intake、规模分类、结构覆盖率和稳定理解快照。

## 已核验资产

| 资产 | 当前事实 | V3.5 决策 |
| --- | --- | --- |
| 本地目录绑定 | ProjectMemory.localProjectPath 已存在，LocalProjectPathGuard 可验证普通目录和 Git 目录 | 继续复用普通目录入口，不把 Git 作为项目理解前置条件 |
| Git 与命令执行 | FixedCommandExecutor 已提供有超时和输出上限的固定参数进程执行 | 直接复用，不实现 Git |
| Durable Job | ProjectAnalysisJob 已有队列、幂等指纹、取消、重试、请求/token/时间预算和恢复语义 | 新增项目理解任务类型，不新建第二套任务系统 |
| Model Gateway V2 | 所有真实模型入口统一走 ModelGatewayService 和 ModelTaskType | 新增明确任务注册；不直接发送 HTTP |
| ProjectFact | 真实发生过的重要工程事实，仍是唯一事实来源 | 不改写、不删除，也不把当前模型推断写成 Fact |
| Timeline | 由 ProjectFact 按发生时间派生 | 无 Git 时不生成历史；V3.5 只提供历史可用性和连接边界 |
| ProjectCapability / Evolution | 长期能力及其演进的派生层 | 保留；Understanding Snapshot 只表达“当前项目是什么” |
| Project Memory Gateway | 只读、模型无关的事实/派生层语义门面 | 保持不变；当前理解先使用独立、受所有权保护的读 API |
| Hermes / Obsidian | Gateway 的只读消费者 | 本阶段不改变协议和投影 |
| 前端 | Next.js 16、React 19，当前为 Web delivery | 只增加聚焦的项目理解页面，不进行 GUI 重建 |
| 数据库 | H2 embedded 与 PostgreSQL，Hibernate ddl-auto update | 新表采用新增、可空兼容策略，不迁移历史事实 |

## 当前主要耦合与缺口

1. WorkSessionScanService 和历史重建链路要求 Git，无法作为任意目录 Intake。
2. ProjectAnalysisService 的整体项目分析主要依赖已导入 ZIP 材料，不是本地目录结构索引。
3. 目录、语言、规模、manifest、Monorepo、generated/vendor、二进制和可读性信号尚未形成稳定 read model。
4. 没有可替换的 ProjectStructureIndexer 边界；当前文件角色判断散落在 ZIP 分析规则中，并带有目录名启发式。
5. 没有持久化的 ProjectUnderstandingSnapshot，无法表达 CURRENT/STALE、Observed/Inferred/Explained、coverage 和 unknowns。
6. 当前侧边栏仍显示 V3.4.1，README 和版本文件仍是 V3.4.5。
7. Dashboard Bootstrap 是轻量持久化读模型，按现有规则不得加入文件扫描或模型调用。

## Web coupling 判断

核心 Fact、Timeline、Capability、Gateway、Job 和 Model Gateway 已位于 Spring Service，不依赖 React 页面。需要防止的新耦合是把目录扫描和语义理解写进 Controller 或页面。

V3.5 的 Core Boundary 采用：

1. RepositoryIntakeService：确定性目录识别。
2. ProjectStructureIndexer：可替换结构来源。
3. ProjectUnderstandingService：编排、缓存、模型边界和持久化。
4. ProjectUnderstandingController：仅作为 HTTP delivery adapter。
5. ProjectAnalysisJobRunner：复用后台生命周期。

这些能力可由未来 CLI、Desktop sidecar 或后台引擎直接调用 Service，不要求浏览器保持打开。

## 数据语义边界

ProjectUnderstandingSnapshot 表示当前项目是什么，允许随当前目录变化而替换。

ProjectFact 表示真实发生过的重要工程事实，只追加或按既有异常规则处理。

Timeline 表示事实的时间视图。

ProjectCapability 表示由事实支持的长期能力。

Evolution 表示长期实体如何变化。

Understanding 中的模型结论必须标为 Inferred；只有工程工具直接证明的内容可标为 Observed。存在明确意图或因果证据时才能使用 Explained。本阶段不会用模型推断创建 ProjectFact。

## 本阶段最小闭环

本阶段实现：

本地目录 → Intake 与分类 → 文件/manifest 结构索引 → 自适应分析计划 → 可选一次有界模型语义调用 → 持久化 Understanding Snapshot → Job/API/UI 读取。

本阶段不实现：

完整 Tree-sitter/SCIP indexer 打包、完整调用图、最终 Project Evolution UI、Tauri/Electron 迁移、后台 watcher、Agent session recorder、向量平台或全量 GUI 重建。
