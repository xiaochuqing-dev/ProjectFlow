# Project profile

ProjectFlow V3.4.5 是面向 AI 辅助独立开发者的本地优先项目记忆系统，自动读取真实 Git、worktree 和 Agent evidence，把客观结果记录为长期 ProjectFact，维护 DAY/WEEK/MONTH/LIFECYCLE 项目历程和全生命周期能力地图，并通过统一只读 Gateway 让 Hermes 与 Obsidian 消费同一套事实与派生语义。

产品读取边界为数据库事实、按项目快速快照和 React 当前视图三层。工作台通过数据库 Bootstrap 恢复最新成功扫描；项目记录和记忆展示长期事实；项目历程组织时间；能力地图展示稳定 ProjectCapability、Evolution、成熟度、近期变化和 attention，并逐层追溯 batch 与 evidence。旧沉淀、Daily Review、能力卡片与档案字段保留在兼容区域。

模型侧使用统一 Model Gateway V2、三种官方 SDK 协议 adapter、Provider capability、任务与输入感知动态参数、Schema repair、截断/reasoning 分型恢复和安全 diagnostics。Gateway 的 search/trace 已形成独立只读业务边界。Project Memory Gateway、Hermes MCP 与 Obsidian Projection 本身不调用模型、不写回事实；下一阶段是 Automatic Memory Maintenance，完整前端重建继续延后。
