# V3.8.5 真实项目与确定性验证

已执行

- `ProjectHistoryReconstructionTest`：完整类通过；覆盖 Git 创建/修改/删除/恢复/重命名、独立成果拆分、文档项目、敏感材料 metadata-only、rewrite、事件守恒、大历史和无变化 cache hit。
- 大历史场景保留超过 1,000 个来源事件和 300 个 Commit，并将模型限制在一个有界兼容请求；诊断仍披露事件守恒和完整覆盖。
- `ProjectHistoryV385GroundTruthTest`、`ProjectHistoryCorrectionServiceTest`、窗口 planner/checkpoint 单元测试通过。
- 前端 TypeScript、生产构建和 55 项契约测试通过。
- Playwright 本地浏览器 E2E 8/8 通过，运行真实前端、嵌入后端和固定模型服务。
- Hermes 9 项测试通过；Obsidian 21 项测试通过，包含大投影、CORE 密度、修正展示、冲突、移动、原子写入和 no-op 场景。

未完成或受环境限制

- Docker Desktop 当前不可用，PostgreSQL 16 Testcontainers 未运行，状态为 BLOCKED。
- GLM/DeepSeek 真实模型请求、真实非代码项目和人工可读性抽样未在本次收口执行，不能宣称真实 Provider Gate 通过。

验证原则

确定性测试证明事实守恒、边界、有界性、权限和安全；Mock Gateway 只证明协议和校验契约。真实模型质量、真实 PostgreSQL 和浏览器 E2E 必须保留独立证据，不得由小样本 Mock 替代。
