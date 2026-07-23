# ProjectFlow V3.6 当前状态审计

审计日期：2026-07-23

审计基线：master，HEAD `7c92c484546e43c7a5e9351611f57f8691aba989`，正式版本 V3.5.0，远端 `origin/master` 与本地 HEAD 对齐。

审计时工作区已有 `frontend/playwright.config.ts`、`start-projectflow-embedded.ps1`、`start-projectflow.ps1` 的用户修改，以及一个未跟踪 Agent result。本阶段不覆盖、不回退，也不把它们纳入 V3.6 提交。

## 结论

V3.5 文档与主实现基本一致：任意本地目录先做有界 intake，`ProjectStructureIndexer` 当前只有 `MANIFEST_FILESYSTEM` 实现，持久化 `ProjectStructureIndex` 和 `ProjectUnderstandingSnapshot`，无变化快速命中缓存并保持零模型调用。AST、Symbol、Definition、Reference、Import、Call Graph 和代码关系驱动的 Functional Area 仍未实现。

V3.6 不需要重写 Intake、Job、Model Gateway、ProjectFact、Timeline、Capability、Gateway、Hermes 或 Obsidian。主要缺口是成熟代码索引的消费适配、关系图压缩，以及当前结构和已有事实证据之间的只读桥接。

## 已核验的 V3.5 资产

| 资产 | 当前真实实现 | V3.6 处理 |
| --- | --- | --- |
| Repository Intake | `RepositoryIntakeService` 有文件数、单文件读取、总读取量、目录忽略、symlink、Git 命令和规模上限 | 直接复用 |
| 结构 SPI | `ProjectStructureIndexer.build(ScanResult)`，业务编排只依赖接口 | 保持接口边界，增加组合 provider |
| 当前 provider | `ManifestFilesystemProjectStructureIndexer` 生成文件、一级模块、入口候选、包含关系和 coverage | 保留为永远可用的 fallback |
| 当前索引 | 每项目一条 `project_structure_indexes`，JSON 可替换、可重建，含 inventory dirty set | 升级 JSON read model，不把索引变成 Fact |
| 当前理解 | 每项目一条 `project_understanding_snapshots`，区分 CURRENT/STALE、OBSERVED/INFERRED、coverage/unknowns | 直接复用并增加深结构上下文 |
| 无变化 | inventory fingerprint 命中后直接返回旧快照 | 保持零模型、零 bridge 重建 |
| 模型边界 | `PROJECT_UNDERSTANDING_SNAPSHOT` 只经 `ModelGatewayService` | 保持一个有界语义阶段 |
| Durable Job | refresh 由 `PROJECT_UNDERSTANDING_REFRESH` 持久化任务执行 | 不建第二套 Job |
| ProjectFact | 唯一事实来源，已有 commit/file/evidence 规范化引用 | 只读连接，不修改历史事实 |
| Capability Evolution | `ProjectCapabilityEvolution` 已保存能力版本事件及来源 Fact 关系 | 保持不变，桥接层只引用 |
| Gateway/Hermes/Obsidian | 只读消费 Facts、Timeline、Capabilities、Evolutions | 不改变既有协议 |

## 当前接口、表、Service 和页面

接口：

- `POST /api/projects/{projectId}/understanding/refresh`
- `GET /api/projects/{projectId}/understanding`
- `GET /api/projects/{projectId}/structure-index`

持久化：

- `project_structure_indexes`
- `project_understanding_snapshots`
- `project_facts` 及 commit/file/evidence 引用表
- `project_capability_evolutions` 与 `project_capability_facts`

核心 Service：

- `RepositoryIntakeService`
- `ManifestFilesystemProjectStructureIndexer`
- `ProjectUnderstandingService`
- `ProjectAnalysisJobService` / `ProjectAnalysisJobRunner`
- `ModelGatewayService`
- `ProjectFactService`
- `ProjectCapabilityMapService`
- `ProjectMemoryGatewayService`

页面：

- `/project-intelligence/understanding`
- `/project-intelligence/timeline`
- `/project-intelligence/capabilities`

## 真实结构缺口

1. `symbolCoverage` 固定为 0。
2. relation 只有目录包含关系，没有 definition/reference 或代码依赖。
3. entry point 主要由文件名确定，只能作为候选。
4. module 是一级目录聚合，不能证明功能或架构边界。
5. 模型上下文只有模块、入口、manifest 和工程文件，无法基于调用/引用关系命名功能区域。
6. dirty set 已记录文件级变化，但 provider 有变化时仍重建整个有界摘要。

## Evolution 现状与瓶颈

`ProjectCapabilityEvolution` 适合继续保存长期能力版本事件，但它没有当前结构版本、结构区域或 before/after revision 引用。旧 `ProjectEvolutionRecord` 含人工变更兼容语义和 next steps，不适合作为 V3.6 结构演进主模型。

当前 Git 历史、ProjectFact、Capability Evolution 各自可追溯，但没有稳定 read model 把：

`before revision → meaningful fact/change → after revision → current structural area`

连接起来。V3.6 应增加最小、幂等、派生的 Evolution Bridge，而不是改写 Fact 或扩张旧兼容记录。

## Web coupling 与 Desktop 边界

核心业务已位于 Spring Service，Controller 只负责 HTTP delivery，React 页面只读取持久化结果并提交 Job。需要避免的新耦合包括：

1. 不在 GET 或 React 页面中运行 indexer、Git 或模型。
2. 不把 provider 安装和解析逻辑写入 Web 层。
3. 不增加 watcher、system tray、开机启动或 GUI 关闭后常驻。
4. 未来 Desktop GUI 与 Java Core 默认同生命周期；打开先读旧结果，用户主动刷新才分析，关闭即退出。

当前页面和 README 的 V3.5 版本字样需要在实现完成后统一升级。旧文档中把 Desktop sidecar 或后台引擎描述为主路线的表述需要收敛为“同生命周期 Core，按用户操作刷新”。
