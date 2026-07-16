# Current work

ProjectFlow 当前版本为 V3.4.2。主链是 Git/worktree/Agent evidence → DevelopmentSegment → 自动 ProjectFact → 项目记录 / 项目记忆 → 自动项目历程 → 全生命周期能力地图。`ProjectFact` 是唯一事实来源；Timeline 是时间派生层；`ProjectCapability` 是由完整事实历史证明、可持续演进的长期能力。

`/project-intelligence/capabilities` 已改为能力地图主页面。全历史 bootstrap 与增量 refresh 通过持久化 job、source fingerprint 和逐 fact coverage 自动维护；每个 fact 必须进入能力关系、no-change 或 attention。稳定身份不只依赖名称，成熟度由确定性规则给出，merge 非破坏性，刷新失败保留旧 READY。旧 CONFIRMED 卡片仅在来源可追到事实时迁移，CANDIDATE / IGNORED 继续兼容。真实安全副本完成 42→37/5 事实重分类、失败 WEEK 恢复、42/42 bootstrap 和 44/44 incremental；后端 297、PostgreSQL 3、前端契约 44、Playwright 7、桌面启动及实现提交远程 CI 均已通过。Hermes 与 Obsidian 正式同步仍属下一阶段；完整证据见 `docs/projectflow-v3.4.2-fact-native-capability-map-report.md`。
