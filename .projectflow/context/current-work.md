# Current work

ProjectFlow 当前进入 V3.4.0 自动项目事实与长期记忆主链：保留既有 Git/worktree/Agent evidence、DevelopmentSegment、后台任务和模型网关前半链，后半链改为自动 ProjectFact、FactCursor、项目记录和 bounded history backfill。正常证据充分事实不再逐条确认，异常进入 NEEDS_ATTENTION 且不阻塞下一次扫描；旧 ProjectChange、ProjectSediment、ProjectReviewCursor 和 ProjectMemory 继续兼容。完整 timeline、全生命周期能力地图、Hermes 与 Obsidian 正式同步不在本阶段实施。桌面启动链的本地修改保护、重建和 `logs/last-embedded-build.json` 证据规则保持不变；最终测试、真实旧库验收、CI 和提交结果以 V3.4.0 独立实施报告为准，不在上下文中预写。

2026-07-16 按用户要求立即收尾并推送：实现与定向验证结果见 `docs/projectflow-v3.4.0-project-fact-memory-report.md`。Playwright、PostgreSQL Testcontainers、本机真实 H2、桌面启动、完整后端回归和 CI 结果未在本地完成，必须保持为待验证，不能描述为已通过。
