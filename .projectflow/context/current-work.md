# Current work

ProjectFlow 当前版本为 V3.4.1。主链是 Git/worktree/Agent evidence → DevelopmentSegment → 自动 ProjectFact → 项目记录 / 项目记忆 → 自动项目历程。`ProjectFact` 仍是事实来源；Timeline 只按 factEventAt 组织 DAY、ISO WEEK、MONTH、LIFECYCLE，并维护可重建、全覆盖、无下一步规划的派生摘要与期间主题。

`/timeline` 已成为主时间视图，Daily Review 从主导航退出，`/dev-logs` 保留兼容。摘要刷新由 after-commit dirty event 和持久化 job 自动维护，GET 不调用模型，历史补齐期间延后生成，失败保留旧 READY。完整生命周期能力地图、Hermes 与 Obsidian 正式同步仍属下一阶段。最终 Gate 0、完整回归、真实 H2、Provider、桌面、CI、提交与推送证据只以 `docs/projectflow-v3.4.1-automatic-project-timeline-report.md` 的实际状态为准。
