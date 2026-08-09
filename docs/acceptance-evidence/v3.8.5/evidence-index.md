# V3.8.5 验收证据索引

本索引只指向可复核的仓库内证据，不保存凭据、Authorization、完整 Prompt、raw response、reasoning、机器绝对路径或私有项目内容。当前状态为 BLOCKED / PENDING_HUMAN_REVIEW：自动化双 Provider 门禁已通过，真实人工评分尚未完成。

## 最终自动化来源

- 代码 head：`74ba013615932748b4a41077baf8f89af618a5d2`
- required CI：push run [`31317712835`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31317712835)、PR run [`31317716057`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31317716057)，均 PASS
- 真实 run：[`31318477841`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31318477841)，attempt 2 PASS
- attempt 1 的 DeepSeek scenarios 9/11 失败仍保留在 GitHub 同一 run 的 attempt/artifact 历史中；attempt 2 只重跑失败 job

## 正式归一化模型工件

GLM `glm-5.2` / Responses / high：

- `real-model/glm/history-v380-real-model.json`，SHA-256 `34ddf2edf117d80293ee38ece76de6e50139a7f7df2597c1d83984b175109001`
- `real-model/glm/projectflow-v375-eval-result.json`，SHA-256 `8f3bc2ed54eb0f3e9f67cbc4912df91986532023511fffd60a640ee9fce8e699`
- `real-model/glm/projectflow-v375-eval-result.md`，SHA-256 `7db7c9f5a444261ec0610034d427b371b9d0b73bafa3a46395dbb2e3532f4275`
- `real-model/glm/project-understanding-real-e2e.json`，SHA-256 `8bc288805e1e577e1bdd772b91d117026fd546f12f3b0f0f84c1b557ba0e3fb0`
- `real-model/glm/history-ground-truth-real-result.json`，SHA-256 `6ec6f88ece9294d74cdc098cd1168e7ae2363f607be5ebd4ad556d2250edae10`
- `real-model/glm/history-real-scenarios.json`，SHA-256 `129e207d5569b15aaa875dc7fd896e893138b825c1382c984f33185e1d3d0425`

DeepSeek `deepseek-v4-flash` / Chat Completions / max：

- `real-model/deepseek/history-v380-real-model.json`，SHA-256 `8f013ec401cdbfa480d24dae8dca1a5dde4153d4538098e392a67fc5ef786fd4`
- `real-model/deepseek/projectflow-v375-eval-result.json`，SHA-256 `35d6576d4ee979dd27f2c982dc315d8cce7004288d48c42090859f5bb6c10815`
- `real-model/deepseek/projectflow-v375-eval-result.md`，SHA-256 `453b275518a19f2a1c692551b9305beb5ff42d52142a4f1c7f0bdcc3599bb7a9`
- `real-model/deepseek/project-understanding-real-e2e.json`，SHA-256 `dcae46cfcc53e7791d723aa1097156b54f15085faad4dd118f7f87a55c24bf23`
- `real-model/deepseek/history-ground-truth-real-result.json`，SHA-256 `f78f97f3f1ea2e3e96f5c1b0ac3f2fe171f1be39478d419778d61239f291acba`
- `real-model/deepseek/history-real-scenarios.json`，SHA-256 `e8564cbf4c7cfe4bfa245ba107fc4f004eaf20d29eafd8fdc4877ebf014f1b91`

## 人工复核证据

- `human-review-sample-manifest.json`：30 Story / 8 Chapter，双 Provider 各 15/4；绑定 run、相对工件、实体 ID、内容哈希与 presentation revision；状态 PENDING_HUMAN_REVIEW
- `human-review-worksheet.md`：逐项展示标题、摘要、Before/Change/After、Reason、Evidence、Unknown/Conflict 与空白人工评分项
- `backend/src/test/java/com/projectflow/service/HumanReviewSampleManifestTest.java`：真实清单存在时校验数量、Run URL、相对路径、双 Provider、分层覆盖和安全标志

## 关键实现与报告

- `docs/projectflow-v3.8.5-acceptance-report.md`
- `docs/projectflow-v3.8.5-rc2-real-provider-results.md`
- `docs/projectflow-v3.8.5-rc2-human-readability-review.md`
- `docs/projectflow-v3.8.5-rc2-model-portability-contract.md`
- `docs/projectflow-v3.8.5-rc2-current-state-audit.md`
- `docs/projectflow-v3.8.5-rc2-final-acceptance.md`
- `docs/acceptance-evidence/v3.8.5/provider-failure-taxonomy.json`
- `.projectflow/agent-results/20260808-v385-rc2-final-closure/result.json`

## 验证结果

- `mvn.cmd -q test`：PASS，557 项，0 失败，0 错误，6 个条件跳过
- `mvn.cmd -q -Dtest=HumanReviewSampleManifestTest test`：PASS
- Frontend contracts 58/58、Playwright 9/9、生产 build/lint：PASS
- Hermes 10/10、Obsidian 25/25：PASS
- 根 `Start-ProjectFlow.bat -NoBrowser`：PASS，当前工作树生产重建并确认前后端就绪
- 正式 12 个模型文件独立扫描：密钥样式 0、机器绝对路径 0；所有工件安全字段为 false

## 历史与范围

初始 qualification FAIL、旧 DeepSeek Dogfood 10/11、Secrets 缺失 run `31264440534`、run `31294942095` 双 job 失败、run `31303975027` DeepSeek Understanding 16/17、run `31318477841` attempt 1 scenarios 9/11 和旧 Obsidian CI failure 均在 RC2 报告中保留。PR #15 继续 Draft；人工门禁前未执行 merge、Tag、Release、backfill 或清理。
