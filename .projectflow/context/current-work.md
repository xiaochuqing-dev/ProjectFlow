# Current work

ProjectFlow 当前版本为 V3.4.3。主链是 Git/worktree/Agent evidence → DevelopmentSegment → 自动 ProjectFact → 项目记录 / 项目记忆 → 自动项目历程 → 全生命周期能力地图 → Project Memory Gateway → Hermes 只读查询。`ProjectFact` 是唯一事实来源；Timeline、Capability、Evolution 是可追溯派生层。

Project Memory Gateway 已提供 snapshot、按 occurredAt 的 recent changes、跨层 search、timeline、capabilities、chronological evolution、fact trace 和 budgeted brief；所有读取 compact/paged、所有权校验、GET 无模型并安全审计。仓库内 Python stdio MCP 暴露 9 个只读工具，真实 Hermes 已在隔离配置和当前 H2 安全副本上发现并调用，能按 7 月发生时间回答、追溯 FactCursor，并对不存在的 Obsidian 正式同步明确返回无事实。V3.4.4 将只在同一 Gateway 上实现 Obsidian projection，不新增正式前端页面。
