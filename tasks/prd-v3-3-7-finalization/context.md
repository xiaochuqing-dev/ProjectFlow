# V3.3.7 Finalization Context

- `ProjectAnalysisJobService.startJob` 仅在 `force=false` 时查活动任务；`retry` 传入 `true`。
- 活动状态为 `QUEUED/RUNNING/CANCEL_REQUESTED`，项目仓库已有悲观锁查询，可作为单实例/单数据库并发序列化边界。
- `ProjectAnalysisJob` 尚无 retry 来源字段；响应也未暴露来源关系。
- Playwright 真实启动 embedded H2 后端和 Next 前端；现有一条测试覆盖并发启动、取消、刷新恢复。
- localhost HTTP Provider 通过 `AiProviderUrlGuard`，可增加固定 OpenAI-compatible 测试服务。
- PostgreSQL Testcontainers 现有测试是实体持久化，不是服务 workflow。
- H2 兼容测试在当前 schema 中把可靠性字段设为 null，未从旧 DDL 启动升级。
- CI 已包含 backend/H2、PostgreSQL、frontend、browser E2E、敏感内容和 optional real DeepSeek。
- 安全边界：归属校验必须保留；不得将本地测试 Provider描述为真实 DeepSeek；临时 repo、H2 数据、trace 和截图必须清理或忽略。
