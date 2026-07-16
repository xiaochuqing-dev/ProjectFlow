# Project profile

ProjectFlow V3.4.1 是面向 AI 辅助独立开发者的本地优先项目记忆系统，自动读取真实 Git、worktree 和 Agent evidence，按分析批次组织 DevelopmentSegment，把证据充分的客观结果记录为长期 ProjectFact，并自动维护从创建至今的 DAY/WEEK/MONTH/LIFECYCLE 项目历程。

产品读取边界为数据库事实、按项目快速快照和 React 当前视图三层。工作台通过数据库 Bootstrap 快速恢复最新成功扫描、批次和推进段；项目记录以批次浏览自动事实与 NEEDS_ATTENTION；项目记忆提供长期事实层；项目历程以 ProjectFact 为 source of truth，提供确定性统计、全覆盖派生摘要和可追溯期间主题。旧沉淀、Daily Review 与档案字段保留在兼容区域。

模型侧继续使用统一网关、Provider/model capability、任务与输入感知动态参数、balanced JSON 多候选识别、目标集合适配、Schema repair、截断/reasoning 分型恢复和安全 diagnostics。V3.4.1 新增 Timeline period/lifecycle 任务但不重构网关；不提前实现生命周期能力地图或 Hermes/Obsidian 正式同步。
