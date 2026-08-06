# V3.8.5 真实项目与确定性验证

## 已通过的确定性验证

- `ProjectHistoryReconstructionTest`：Git 创建/修改/删除/恢复/重命名、独立成果拆分、文档项目、敏感材料 metadata-only、rewrite、事件守恒、大历史和无变化 cache hit。
- `ProjectHistoryV385GroundTruthTest`、`ProjectHistoryV385GroundTruthExecutionTest`、`ProjectHistoryCorrectionServiceTest`、Window planner/checkpoint 和 language policy 测试通过。
- 后端全量 H2：496 项，0 失败，0 错误，1 跳过。
- 前端 build/lint、55 项 contracts、Playwright 8/8、Hermes 9/9、Obsidian 21/21 通过。
- GitHub push run `31069320457` 与 PR run `31069362971` 的 required jobs 和 PostgreSQL 16 Testcontainers 通过。

## 真实 Provider 结果

- GLM `glm-5.2` Responses 合同通过，但 19-case qualification FAIL；真实场景未运行。
- DeepSeek Chat 合同通过，19-case qualification FAIL；11 场景中 10/11 通过，ProjectFlow Dogfood 因 Primary/Supporting 引用不一致失败。五类非代码场景、17 窗口 continuation/cache/restart、schema failure、取消恢复和 Prompt overflow 场景通过。
- 旧版 `ProjectFlowRealModelEvalIT` 与 `ProjectUnderstandingRealModelIT` 本轮没有执行，不将其写成通过。

## 环境限制与结论

本机 Docker Desktop Linux engine 不可用；PostgreSQL 结果只引用 GitHub Testcontainers，没有用 H2 冒充。人工可读性抽样为 0 Story/0 Chapter，最终质量门禁 BLOCKED。确定性测试证明事实守恒、边界、权限和安全；固定 Gateway 只证明协议与校验契约，不能替代真实模型质量。
