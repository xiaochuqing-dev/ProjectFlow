# Current work

ProjectFlow 当前版本为 V3.4.5。主链是 Git/worktree/Agent evidence → DevelopmentSegment → 自动 ProjectFact → 项目记录 / 项目记忆 → 自动项目历程 → 全生命周期能力地图 → Project Memory Gateway → Hermes 即时只读查询 / Obsidian 长期知识投影。`ProjectFact` 是唯一事实来源；Timeline、Capability、Evolution 是可追溯派生层，Obsidian 只是可重建的消费视图。

Model Gateway V2 使用官方 OpenAI/Anthropic Java SDK 支持 Responses、Chat Completions 和 Messages，统一动态预算、重试、取消、恢复、finish/usage 归一化与安全诊断。Provider 显式保存 protocol、endpoint、auth、timeout 和能力覆盖，旧配置幂等迁移为 Chat Completions。Project Memory Gateway 保持稳定门面，search 与 fact trace 已拆成独立只读业务服务。下一阶段是 Automatic Memory Maintenance；完整前端重建继续延后，不得扩张远程 MCP 或新的事实源。
