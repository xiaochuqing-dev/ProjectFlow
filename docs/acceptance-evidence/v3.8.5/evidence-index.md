# V3.8.5 验收证据索引

本目录只索引可复核的仓库内证据，不保存凭据、完整 Prompt、raw response、reasoning、绝对路径或私有项目内容。当前报告的结论是“本地实现与产品门禁通过，外部 PostgreSQL/真实 Provider 未闭环”。

实现与合同

- `docs/projectflow-v3.8.5-current-state-audit.md`
- `docs/projectflow-v3.8.5-reuse-and-research-decision.md`
- `docs/projectflow-v3.8.5-human-readable-history-contract.md`
- `docs/projectflow-v3.8.5-semantic-compression-architecture.md`
- `docs/projectflow-v3.8.5-user-correction-contract.md`
- `docs/projectflow-v3.8.5-obsidian-history-projection-contract.md`
- `docs/projectflow-v3.8.5-acceptance-report.md`

质量与固定输入

- `docs/projectflow-v3.8.5-human-ground-truth-and-quality-gates.md`
- `docs/projectflow-v3.8.5-model-qualification.md`
- `docs/projectflow-v3.8.5-real-project-validation.md`
- `docs/projectflow-v3.8.5-product-acceptance.md`
- `backend/src/test/resources/projectflow-v385/history-ground-truth.json`
- `backend/src/test/java/com/projectflow/ProjectHistoryV385GroundTruthTest.java`
- `backend/src/test/java/com/projectflow/eval/ProjectHistoryV385QualityEvaluator.java`
- `.projectflow/agent-results/20260806-v385-history-quality-closure/result.json`

可复核命令与结果

- `backend: mvn.cmd -q test`：PASS，496 项，0 失败，0 错误，1 跳过。
- `frontend: npm.cmd run build`：PASS；`npm.cmd run lint`：PASS；`npm.cmd run test:contracts`：PASS，55/55。
- `frontend: npm.cmd run test:e2e`：PASS，8/8，真实前端/后端与固定模型服务。
- `python -m unittest discover -s integrations/hermes -p 'test_*.py'`：PASS，9/9。
- `python -m unittest discover -s integrations/obsidian -p 'test_*.py'`：PASS，21/21。
- `cmd.exe /c Start-ProjectFlow.bat -CheckOnly`：PASS，版本 3.8.5。
- `docker info`：BLOCKED，Docker Desktop Linux engine named pipe 不可用；未运行或伪造 PostgreSQL 结果。

安全与范围

- UTF-8 扫描 856 个文本文件，真实 token/Bearer 命中 0。
- 2 个绝对路径命中仅是脱敏单元测试夹具，不在验收产物或 Agent Result 中。
- GLM/DeepSeek 真实 Provider、calibration/holdout、非代码项目和 GitHub CI：NOT_RUN；用户凭据未写入仓库、日志、命令或报告。
