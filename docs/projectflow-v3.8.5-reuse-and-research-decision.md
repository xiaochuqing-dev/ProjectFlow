# ProjectFlow V3.8.5 复用与研究决策

问题

V3.8.0 的历史结构证据正确，但普通用户面对过多技术主体 Story。需要提升语义粒度、可读性、模型边界和用户修正的一致性。

已有内部能力

复用 `ProjectHistoryEvent`、`ProjectHistorySnapshot`、`ProjectHistorySourceCollector`、`ProjectHistoryReadService`、`ModelGatewayService`、Project Memory Gateway、Hermes stdio 和 Obsidian Projection。已有 V3.8.0 历史语义、展示合同、Obsidian 研究和安全门禁继续作为约束。

外部研究是否需要：否

本阶段没有引入新的核心依赖、解析器、Git 引擎、向量库或工作流框架。JDK 的 `LinkedHashSet`、SHA-256、Spring Data JPA、Jackson 和已有 Python 投影工具已经覆盖稳定顺序、cache key、持久化 checkpoint 与增量投影需求。V3.8.0 的开源研究已经回答 Git 历史展示、Obsidian URI 和投影密度问题，重复调研不会增加实现信息量。

选择方案

1. 在现有重建服务内增加 `ProjectHistoryWindowPlanner`，使用故事/事件边界生成稳定窗口。
2. 使用 `ProjectHistoryWindowCheckpoint` 保存脱敏状态、诊断和 validated presentation JSON。
3. 使用 `ProjectHistoryCorrection` 作为只读 Snapshot 上的声明覆盖，不复制事实。
4. 使用 `ProjectHistoryLanguageService` 集中处理中文对象、动作和技术细节降级。

拒绝的替代方案

- 引入通用 RAG 或向量数据库：无法解决成员归属和 Evidence 权威，且扩大部署边界。
- 按 Commit 逐次请求模型：成本和延迟不可界定，也会把技术噪声直接变成故事。
- 让模型决定 Story 成员、时间、Evidence 或原因：破坏事实边界。
- 直接改写 Snapshot JSON 作为用户修正：无法审计、回退，也会让下一次刷新丢失声明。

依赖、许可证和安全影响

无新增依赖、无许可证变化、无全局机器配置变化。checkpoint 只保存已校验展示和紧凑诊断；不保存 Key、Authorization、完整 Prompt、raw response、reasoning、绝对路径或完整私有材料。
