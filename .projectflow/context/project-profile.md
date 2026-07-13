# Project profile

ProjectFlow V3.3.8.1 是面向 AI 辅助独立开发者的本地开发变化理解与项目沉淀工具。核心链路为：待整理变更、开发推进段、建议沉淀、项目沉淀、能力分析。

产品读取边界为数据库事实、按项目快速快照和 React 当前视图三层。工作台通过数据库 Bootstrap 快速恢复最新成功扫描、批次、推进段和待处理数；沉淀处理中心对旧数据缺失字段保守降级，并以固定批量查询避免 N+1。

模型侧已形成 6 入口统一网关、Provider/model capability、任务与输入感知动态参数、balanced JSON 多候选识别、目标集合适配、Schema repair、截断/reasoning 分型恢复和安全 diagnostics。真实 DeepSeek 已通过实际应用 API 验证，固定模型、H2/PostgreSQL、浏览器 E2E 与真实 Provider 证据保持分层。
