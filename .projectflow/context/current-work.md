# Current work

ProjectFlow 当前版本为 V3.4.4。主链是 Git/worktree/Agent evidence → DevelopmentSegment → 自动 ProjectFact → 项目记录 / 项目记忆 → 自动项目历程 → 全生命周期能力地图 → Project Memory Gateway → Hermes 即时只读查询 / Obsidian 长期知识投影。`ProjectFact` 是唯一事实来源；Timeline、Capability、Evolution 是可追溯派生层，Obsidian 只是可重建的消费视图。

Project Memory Gateway 提供 snapshot、按 occurredAt 的 recent changes、跨层 search、timeline、capabilities、chronological evolution、fact trace 和 budgeted brief；仓库内 Python stdio MCP 暴露 9 个只读工具。Obsidian CLI 复用同一 Gateway，默认 CORE 生成 Overview、月度 Timeline、长期 Capability、月度 Fact Index 与导航索引，并提供 validate/dry-run/status/sync。同步只写专用 managed root 和 managed block，使用稳定 ID、version/hash、manifest、原子替换、冲突保护和安全路径；rename 稳定，merge 保留 redirect，默认不调用模型、不新增前端页面。下一阶段是 backend business/logic consolidation，之后才进行完整前端重建。
