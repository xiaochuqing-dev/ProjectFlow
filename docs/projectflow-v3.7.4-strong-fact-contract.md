# ProjectFlow V3.7.4 Strong Fact Contract

## 状态

- OBSERVED：工程系统直接从文件、Git、配置、测试或持久化结果观察。
- VERIFIED：OBSERVED 结论又被独立工程来源或实际运行结果验证。
- DECLARED：文档、用户或 Agent 的声明，尚未工程验证。
- INFERRED：模型或规则基于 Evidence 的推断。
- CONFLICTED：来源间存在未解决冲突。
- UNKNOWN：证据不足。
- PROCESS_EVIDENCE：Agent Result、请求、Token、日志等过程材料。

RECORDED 强事实只允许 OBSERVED 或 VERIFIED。其他状态可读、可搜索、可进入 Context Package，但必须留在 attention/candidate/understanding 层。

## Promotion Guard

- Agent Result 单独存在时只能是 PROCESS_EVIDENCE。
- 推断词或模型解释不能成为 RECORDED。
- “为什么这样设计”需要 ADR、Issue、PR discussion、commit body、设计文档、用户明确说明或可追溯 Agent 决策说明。
- “已废弃”需要 deprecated、替代、删除、迁移或关闭原因等明确证据。
- “技术债”需要 TODO/FIXME、Open Issue、失败测试、风险文档、已知限制或可验证缺口。
- 两个模型同意不会产生 VERIFIED；模型/provider 只保留为过程元数据。
- 无效、跨项目或未知 Evidence ID 必须拒绝或转 NEEDS_ATTENTION。

## 兼容字段

ProjectFact 保留既有事实内容、指纹、时间、游标和来源关系；新增 statement、epistemicStatus、sourceTypes、currentness、revision、observedAt、effectiveAt、supersededBy、limitations、conflictRefs、createdBy、sourceAgentId、sourceModelProvider 和 validationStatus。旧行按已有 recordStatus 保守映射，不批量改写历史内容。

