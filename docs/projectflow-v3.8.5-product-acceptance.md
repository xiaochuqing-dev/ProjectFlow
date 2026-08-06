# V3.8.5 产品验收清单

通过

- ProjectFact、ProjectHistoryEvent、Evidence 和 rewrite 状态未被历史或修正写入覆盖。
- Primary/Supporting、中文 fallback、工程详情下钻、用户修正 API/读取覆盖和多窗口 checkpoint 已实现。
- 完整 `ProjectHistoryReconstructionTest` 通过。
- planner/checkpoint 边界测试、Ground Truth 契约和 correction service 测试通过。
- 前端 lint、生产构建、55 项契约测试通过。
- Playwright 浏览器 E2E 8/8 通过，覆盖真实前端、嵌入后端和固定模型服务。
- Hermes 9/9、Obsidian 21/21 通过。

阻断或未运行

- PostgreSQL 16 Testcontainers：BLOCKED，本机 Docker daemon 不可用。
- GLM 与 DeepSeek 真实模型：NOT_RUN；没有用固定 Mock 结果替代真实质量证明。
- GitHub push、PR、CI、merge 和分支清理：本地收口未执行，报告保留为未完成项。

质量结论

确定性实现和本地消费链达到可交付候选状态；“HUMAN-READABLE PROJECT HISTORY QUALITY GATE = PASS” 仍需真实 Provider、PostgreSQL 和人工 holdout 抽样的独立证据，当前不能提前标记 PASS。
