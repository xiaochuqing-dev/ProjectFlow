# ProjectFlow V3.5 Universal Project Understanding Architecture

## 目标

V3.5 接受任意已绑定本地目录，先用确定性工程证据判断它是什么、规模多大、能分析到什么程度，再按计划选择结构来源和可选模型语义层。结果是可持久化、可追溯、Token 有界的 ProjectUnderstandingSnapshot。

## Pipeline

```text
Bound Local Directory
        |
Repository Intake
        |
Input Classification + Scale
        |
ProjectStructureIndexer
        |
Adaptive Analysis Plan
        |
Optional Model Semantic Synthesis
        |
Evidence Validation
        |
ProjectUnderstandingSnapshot
```

## Core 组件

### RepositoryIntakeService

职责：

1. 使用 LocalProjectPathGuard 验证目录。
2. 使用无 symlink 跟随的 FileVisitor 收集文件、语言、LOC、大小、manifest、二进制、generated/vendor、nested repo 和 Monorepo 信号。
3. 使用现有 FixedCommandExecutor 读取 Git HEAD、branch、commit count 和 worktree 状态。
4. 产生可配置阈值下的 classification、scale、coverage、warning 和 source revision。
5. 产生内容/库存 hash 和相对路径签名，供增量缓存与 dirty set 判断。

不负责：

1. 不解释项目用途。
2. 不解析语言 AST。
3. 不调用模型。

### ProjectStructureIndexer

它是结构来源 SPI，不是 ProjectFlow 自创的跨语言 Symbol Protocol。

首版 provider 为 MANIFEST_FILESYSTEM：

1. 从文件库存、manifest/workspace 声明和目录聚合生成模块候选。
2. 只生成确定性的 CONTAINS/DECLARES_WORKSPACE 关系。
3. 基于显式入口文件信号给出低风险 entry point 候选。
4. 明确 symbolsAvailable=false，并把 AST/call/reference 记入 unsupported areas。

后续 provider：

1. SCC：语言与 LOC 外部指标。
2. TREE_SITTER：容错 tags/AST。
3. SCIP：definition/reference/occurrence。
4. EXTERNAL_GRAPH：受许可允许的外部图谱 Adapter。

业务层只依赖 ProjectStructureIndexer 输出，不依赖具体 Parser。

### Adaptive Analysis Plan

计划由规则产生，不由模型决定。

EMPTY：

1. 0 模型请求。
2. 保存“无可分析内容”的确定性快照。

UNKNOWN_NON_CODE：

1. 默认 0 模型请求。
2. 仅保存文件/manifest/unknown 边界。

CODE_NO_GIT：

1. 完成 current understanding。
2. historicalMode=UNAVAILABLE_NO_GIT。
3. 不创建 Timeline 或历史事实。

SMALL / MEDIUM：

1. 使用完整结构摘要。
2. Provider 可用时执行一个语义阶段。

LARGE / HUGE_MONOREPO：

1. 只发送高价值模块、manifest、入口和工程设施摘要。
2. hierarchical=true。
3. 不逐文件调用模型。

所有计划都有 maxRequests、maxInputTokens、maxDurationMs 和 unavailableCapabilities。网关内部格式恢复仍受现有 Job/Gateway 总预算约束。

### ProjectUnderstandingService

职责：

1. 编排 Intake、结构索引、缓存、计划、Model Gateway 和持久化。
2. 模型任务登记为 PROJECT_UNDERSTANDING_SNAPSHOT。
3. 只把结构压缩摘要送给模型，不发送任意完整源码、prompt 或绝对路径到持久化层。
4. 校验模型 evidenceRefs 必须属于本次允许集合。
5. 将工程直接证明的结论标为 OBSERVED，将模型综合标为 INFERRED；本阶段不自动产生 EXPLAINED 因果结论。
6. 同一库存 hash 命中当前快照时返回缓存，模型请求为 0；有变化时比较前后库存并记录 added/modified/removed/unchanged dirty set。
7. V3.5 的 manifest/filesystem provider 在有变化时仍确定性重建有界结构摘要；dirty set 已成为后续 Tree-sitter/SCIP provider 做细粒度增量更新的稳定输入，不虚称已实现 AST 级增量解析。
8. 模型失败时保留已有成功快照；若没有旧快照，至少保存确定性 fallback，同时 Job 报告模型失败。

### 持久化

project_structure_indexes：

1. 每项目一条当前可重建结构索引。
2. 保存 sourceRevision、contentHash、indexVersion、indexerSource、intakeJson、indexJson 和可空 inventoryJson。
3. 不保存绝对项目路径。
4. 旧记录没有 inventoryJson 时安全执行一次完整重建，再进入增量 dirty-set 模式。

project_understanding_snapshots：

1. 每项目一条最后成功或确定性可用的当前理解。
2. 保存 sourceRevision、structureHash、structureIndexVersion、modelAnalysisVersion、currentStatus、semanticStatus 和 snapshotJson。
3. 失败刷新不覆盖旧成功快照。

ProjectStructureIndex 是 derived/rebuildable intelligence。ProjectUnderstandingSnapshot 是 replaceable current interpretation。两者都不是 ProjectFact。

## API

```text
POST /api/projects/{projectId}/understanding/refresh
GET  /api/projects/{projectId}/understanding
GET  /api/projects/{projectId}/structure-index
```

POST 创建持久化 ProjectAnalysisJob。GET 只读取已持久化结果，不扫描文件、不调用 Git、不调用模型。

所有入口同时校验 userId 和 projectId 所有权。响应不包含绝对路径、prompt、raw response、reasoning、Key、Authorization 或自定义 Header。

## Evidence 与质量

Claim 使用：

1. OBSERVED：manifest、语言、Git、文件、测试/CI/Docker 等确定性证据。
2. INFERRED：模型基于多个允许 evidenceRef 的语义综合。
3. EXPLAINED：需要明确意图或因果证据；首版不自动生成。

Snapshot 质量不是虚构分数，而是：

1. intakeCoverage。
2. structureCoverage。
3. semanticStatus。
4. evidenceBoundClaimCount。
5. unknowns。
6. unsupportedAreas。
7. CURRENT / STALE。

## 与现有层的关系

ProjectUnderstandingSnapshot：当前项目是什么。

ProjectFact：真实发生过的重要工程事实。

Timeline：按发生时间查看 ProjectFact。

ProjectCapability：由事实证明的长期能力。

Evolution：长期实体如何变化。

未来 Project Evolution 可以把 current snapshot 与 historical fact spine 组合，但 V3.5 不迁移或重写已有事实。

## 安全与成本

1. 不跟随 symlink。
2. 扫描文件数、单文件读取、警告数、命令输出和命令时间有上限。
3. generated/vendor/build 目录受控忽略，并反映 coverage。
4. prompt 只含相对 evidenceRef 和压缩结构摘要。
5. 无模型、空目录、非代码目录和无变化重跑为 0 模型调用。
6. 大项目仍只有一个语义阶段，禁止逐文件模型循环。
7. Model Gateway V2 独占 Provider 协议、重试、取消、预算和恢复。
