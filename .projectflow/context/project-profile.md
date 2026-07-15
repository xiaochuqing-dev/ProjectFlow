# Project profile

ProjectFlow V3.4.0 是面向 AI 辅助独立开发者的本地优先项目记忆系统，自动读取真实 Git、worktree 和 Agent evidence，按分析批次组织 DevelopmentSegment，并把证据充分的客观结果记录为长期 ProjectFact。核心链路为：分析新变化、开发推进段、自动项目事实、项目记录与项目记忆。

产品读取边界为数据库事实、按项目快速快照和 React 当前视图三层。工作台通过数据库 Bootstrap 快速恢复最新成功扫描、批次和推进段；项目记录以批次浏览自动事实与 NEEDS_ATTENTION，项目记忆以 facts 数量、覆盖范围、最早/最新事实和近期事实为主。旧沉淀与档案字段保留在兼容区域。

模型侧继续使用 6 入口统一网关、Provider/model capability、任务与输入感知动态参数、balanced JSON 多候选识别、目标集合适配、Schema repair、截断/reasoning 分型恢复和安全 diagnostics。V3.4.0 只替换分析后半链，不提前实现完整 timeline、生命周期能力地图或 Hermes/Obsidian 同步。
