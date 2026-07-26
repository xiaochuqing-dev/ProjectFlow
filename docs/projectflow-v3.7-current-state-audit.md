# ProjectFlow V3.7 当前状态审计

审计日期：2026-07-24

基线：`master`，HEAD `23186ca24e78796538e92444df0474e2bcbf5a28`，版本 V3.6.0。工作区已有 `frontend/playwright.config.ts`、两个启动 PowerShell 脚本和一个 Agent result 的用户修改，本阶段不覆盖或提交。

## 结论

V3.6 的 Intake、Structure SPI、SCIP consumer、JGraphT、Understanding Snapshot、Durable Job、Model Gateway、Evolution Bridge、Facts、Timeline、Capability、Gateway、Hermes 和 Obsidian 与报告基本一致，均应直接复用。

主要缺口不是更深 Parser，而是输入与输出之间缺少通用语义调度：

- Evidence 只围绕代码结构、manifest、入口和工程文件组织，没有统一 Source Map。
- `UNKNOWN_NON_CODE` 被直接判为不适用模型，有内容 TXT 和奇怪命名文档无法得到语义理解。
- `ProjectUnderstandingService` 固定生成 identity、technology、structure、architecture、capabilities、engineeringState 六个 Section。
- 分析计划只按代码/规模/Git/模型做确定性选择，模型不能参与适用维度判断。
- 页面固定展示六个 Section 和演进区域，即使项目不适用。
- Timeline 本身由 Fact 驱动，但 Understanding 页面没有正式 Historical Coverage，用户难以判断可还原历史的比例。

## 当前 Evidence 来源

- 本地文件库存、相对路径、扩展名、字节数、受支持语言和有界内容指纹。
- manifest/workspace、入口候选、测试、CI、部署、迁移、质量和 release 文件信号。
- 本地 Git branch、HEAD、commit count、worktree 和 submodule。
- 可选 `index.scip` 的 Symbol、Definition、Reference 和关系。
- 已有 ProjectFact、Fact commit/file/evidence refs 和 Evolution Bridge。
- `.projectflow/agent-results` 在事实分析链可用，但未进入当前理解的统一 Source Map。

## 死规则与模型边界

文件扩展名、manifest 名称、入口文件名、工程文件路径和 generated/vendor/sensitive 判断均为候选级规则；V3.6 页面却容易把这些候选投影成固定维度。模型只在有代码、非 `UNKNOWN_NON_CODE`、已配置 Provider 时调用一次，输入为模块/区域/重要节点/结构 Evidence；它负责固定六个 Section 的语义归纳。

V3.7 必须保留 deterministic 安全和事实边界，同时让模型在 evidence ID 约束下判断来源角色、项目形态假设和适用维度。

## 输入与 Token 缺口

- 空目录处理正确：0 模型。
- 空白 TXT 与有内容 TXT 都归入 `UNKNOWN_NON_CODE`，缺少“无实质内容”与“文档型材料”的区别。
- 奇怪命名 Markdown/TXT 不属于 key file 时可能只作为普通文件详情存在，模型看不到内容信号。
- README 没有当前性/冲突评估。
- 代码项目的模型输入已限制 48,000 字符，但缺少 Scout/Deep Read/Synthesis 分项指标。
- 大仓库结构压缩成熟，可继续复用；不应增加逐文件、逐 Symbol 或逐 Commit模型请求。

## 直接复用清单

`RepositoryIntakeService`、`ProjectStructureIndexer`、`CompositeProjectStructureIndexer`、SCIP protobuf consumer、JGraphT、`ProjectUnderstandingSnapshot` JSON、CURRENT/STALE、inventory cache、`PROJECT_UNDERSTANDING_REFRESH`、`ModelGatewayService`、Fact commit refs、`ProjectEvolutionBridgeService`、所有只读 GET 和 ownership 校验。

## 本阶段选择

在现有 snapshot JSON 内增加 Source Map、Scout、Plan、Dynamic Profile、Historical Coverage、Evolution Preview 和 Metrics，不建新表。拆出聚焦 Service，保留 `ProjectUnderstandingService` 作为编排/持久化门面。旧 V3.6 字段继续兼容读取，主动刷新后安全重建。
