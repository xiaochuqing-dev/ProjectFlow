# Project profile

ProjectFlow V3.4.4 是面向 AI 辅助独立开发者的本地优先项目记忆系统，自动读取真实 Git、worktree 和 Agent evidence，把客观结果记录为长期 ProjectFact，维护 DAY/WEEK/MONTH/LIFECYCLE 项目历程和全生命周期能力地图，并通过统一只读 Gateway 让 Hermes 与 Obsidian 消费同一套事实与派生语义。

产品读取边界为数据库事实、按项目快速快照和 React 当前视图三层。工作台通过数据库 Bootstrap 恢复最新成功扫描；项目记录和记忆展示长期事实；项目历程组织时间；能力地图展示稳定 ProjectCapability、Evolution、成熟度、近期变化和 attention，并逐层追溯 batch 与 evidence。旧沉淀、Daily Review、能力卡片与档案字段保留在兼容区域。

模型侧继续使用统一模型网关、Provider/model capability、任务与输入感知动态参数、Schema repair、截断/reasoning 分型恢复和安全 diagnostics。Project Memory Gateway、Hermes MCP 与 Obsidian Projection 本身不调用模型、不写回事实。Obsidian 默认 CORE 是克制的长期知识投影，依靠 managed root/block、manifest、原子增量写入和 conflict 保护用户内容；下一阶段先整理后端业务逻辑，再重建完整前端。
