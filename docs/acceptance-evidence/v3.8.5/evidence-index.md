# V3.8.5 验收证据索引

本索引只指向可复核的仓库内证据，不保存凭据、Authorization、完整 Prompt、raw response、reasoning、绝对路径或私有项目内容。当前结论是 BLOCKED：RC2 本地确定性门禁通过，current required CI 尚在运行；RC2 双真实 Provider 因 Secrets 缺失未运行，人工可读性仍为 0/0。

实现、审计与合同

- `docs/projectflow-v3.8.5-current-state-audit.md`
- `docs/projectflow-v3.8.5-rc-code-audit-and-fix-report.md`
- `docs/projectflow-v3.8.5-human-readable-history-contract.md`
- `docs/projectflow-v3.8.5-semantic-compression-architecture.md`
- `docs/projectflow-v3.8.5-user-correction-contract.md`
- `docs/projectflow-v3.8.5-obsidian-history-projection-contract.md`
- `docs/projectflow-v3.8.5-acceptance-report.md`
- `docs/projectflow-v3.8.5-rc2-current-state-audit.md`
- `docs/projectflow-v3.8.5-rc2-provider-failure-taxonomy.md`
- `docs/projectflow-v3.8.5-rc2-model-portability-contract.md`
- `docs/projectflow-v3.8.5-rc2-cross-consumer-consistency.md`
- `docs/projectflow-v3.8.5-rc2-human-readability-review.md`
- `docs/projectflow-v3.8.5-rc2-real-provider-results.md`
- `docs/projectflow-v3.8.5-rc2-targeted-reuse-decisions.md`
- `docs/projectflow-v3.8.5-rc2-final-acceptance.md`
- `docs/acceptance-evidence/v3.8.5/provider-failure-taxonomy.json`

Ground Truth 与代码评测

- `backend/src/test/resources/projectflow-v385/history-ground-truth.json`
- `backend/src/test/java/com/projectflow/ProjectHistoryV385GroundTruthTest.java`
- `backend/src/test/java/com/projectflow/ProjectHistoryV385GroundTruthExecutionTest.java`
- `backend/src/test/java/com/projectflow/eval/ProjectHistoryV385QualityEvaluator.java`
- `backend/src/test/java/com/projectflow/eval/ProjectHistoryV385RealOutputEvaluatorTest.java`
- `backend/src/test/java/com/projectflow/eval/ProjectHistoryV385RealScenarioEvaluatorTest.java`
- `.projectflow/agent-results/20260806-v385-history-quality-closure/result.json`
- `.projectflow/agent-results/20260807-v385-final-qualification/result.json`
- `.projectflow/agent-results/20260808-v385-rc2-final-closure/result.json`

固定兼容模型证据

- `backend/target/projectflow-eval/v385-real-scenarios-openai_chat_completions/history-real-scenarios.json`：固定兼容执行器 11/11 场景通过，60 个物理请求，12,000 token；只证明流程、边界和故障恢复。
- `backend/target/projectflow-eval/history-real/project-history-real-model.json`：固定兼容 Dogfood 合同结果，1 请求；不代表真实 Provider 质量。
- `backend/target/projectflow-eval/fixed-v385-scenarios/history-real-scenarios.json` 是较早的兼容重跑工件，不作为当前资格结论。

真实 Provider 工件

- `backend/target/projectflow-eval/glm-v385-contract/project-history-real-model.json`：GLM `glm-5.2` Responses 合同 PASS，1 请求，4,850 token，41,659 ms。
- `backend/target/projectflow-eval/deepseek-contract/project-history-real-model.json`：DeepSeek Chat 合同 PASS，1 请求，4,271 token，81,987 ms。
- `backend/target/projectflow-eval/glm-v385/history-ground-truth-real-result.json`：GLM 19-case qualification FAIL，20 请求，103,268 token，616,966 ms，16 个降级窗口，24 个失败/未处理窗口，12 个 UNSUPPORTED_CLAIM 拒绝。
- `backend/target/projectflow-eval/deepseek-v385/history-ground-truth-real-result.json`：DeepSeek 19-case qualification FAIL，20 请求，79,702 token，1,002,070 ms，14 个降级窗口，24 个失败/未处理窗口，12 个 UNSUPPORTED_CLAIM 拒绝。
- `backend/target/projectflow-eval/deepseek-v385-scenarios/history-real-scenarios.json`：11 个真实场景 10/11，通过 5 类非代码和恢复类场景；ProjectFlow Dogfood 因 Primary/Supporting 引用不一致失败。

可复核命令与结果

- `backend: mvn.cmd -q test`：PASS，496 项，0 失败，0 错误，1 跳过。
- `frontend: npm.cmd run build`：PASS；`npm.cmd run lint`：PASS；`npm.cmd run test:contracts`：PASS，55/55。
- `frontend: npm.cmd run test:e2e`：PASS，8/8，真实前端/后端与固定模型服务。
- `python -m unittest discover -s integrations/hermes -p 'test_*.py'`：PASS，9/9。
- `python -m unittest discover -s integrations/obsidian -p 'test_*.py'`：PASS，21/21。
- `cmd.exe /c Start-ProjectFlow.bat -CheckOnly`：PASS，版本 3.8.5。
- `docker info`：本机 BLOCKED；GitHub PostgreSQL 16 Testcontainers 独立通过。
- GitHub push run [`31069320457`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31069320457)：required jobs PASS。
- GitHub PR run [`31069362971`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31069362971)：required jobs PASS，optional-real-provider SKIPPED。

安全与范围

- 本轮扫描覆盖 871 个文本文件；token-like/Bearer 命中均为 0，7 个绝对路径匹配分布在 4 个脱敏/敏感内容测试夹具中。扫描只记录聚合数量和诊断，不保存扫描原文；`git diff --check` 退出码为 0。
- `.github/workflows/quality-gates.yml` 的 DeepSeek endpoint 已修正为 `https://opencode.ai/zen/go/v1`；不包含任何 Key。
- PR [#15](https://github.com/xiaochuqing-dev/ProjectFlow/pull/15) 继续 Draft；未执行 merge、Ready for Review、Tag、Release、分支删除或 worktree 清理。
