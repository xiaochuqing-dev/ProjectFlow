# V3.8.5 验收证据索引

当前状态：PENDING_HUMAN_REVIEW / NOT PASS。PR #15 保持 Draft。

## 真实模型来源

- GLM 修复前完整资格与 11/11 场景基线：run [`31523413972`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31523413972)，`glm-5.2`、Responses、high；仅复用未受影响 strata。
- DeepSeek 修复前完整资格与 11/11 场景基线：run [`31517037532`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31517037532)，`deepseek-v4-flash`、Chat Completions、max；仅复用未受影响 strata。
- 双 Provider 受影响纠正复验：run [`31532558352`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31532558352)，code head `aee0160cf1d4cf11224055548107098fd12e6de1`，两家 1/1 PASS。

## 当前规范化工件

GLM：

- `real-model/glm/history-v380-real-model.json`：`822b3ed6d868c0f0e44b3afac2bc8a74434006dcdf0bf58dc5ebeb49a27d01fe`
- `real-model/glm/history-ground-truth-real-result.json`：`143b88fce34afdf6a0f873046514d7962b4bfbcc245f58796a8dd327e50638b8`
- `real-model/glm/history-real-scenarios.json`：`a9dcb8fe6cd01d784e4be400be24b16406d8fe7a1c39e1c7997cf835e31f53b8`
- `real-model/glm/history-real-scenarios-affected.json`：`0aff9ec06f28c73fd7a445d5e6385d4300bcbcb7b5584e1b4113b292a044e97a`

DeepSeek：

- `real-model/deepseek/history-v380-real-model.json`：`7150ba2d9cda34175640b3049295acce95f5a4d557af9f13ab3eccd1a87004d0`
- `real-model/deepseek/history-ground-truth-real-result.json`：`0f99bedf6028724681b9c347b7050078569325cf00f13a3a896601ebc9023654`
- `real-model/deepseek/history-real-scenarios.json`：`e2fdc145f3f1b8783aa35288954cbaca8559774efc7f4096da3524aefcdf29fd`
- `real-model/deepseek/history-real-scenarios-affected.json`：`97cb5bc097ad86cd5a78271af2e752d04e5be6487ad4ae9571baad2bc158869d`

这些工件只保存规范化结果。Key、Authorization、完整 Prompt、raw response、reasoning、私有绝对路径和私有项目内容不进入仓库。

## 人工复核

- Round 1 原 manifest/worksheet 保持冻结，正式结论 NEEDS_REVISION_NOT_APPROVED。canonical-LF SHA-256：`524391f2137a7b72d2920efbefaee1190177bbeb588594ef47fa9099d92554d9`、`dbe05a47548ba72cd2c379c05b987e5915e68c32bb935e5bbd3fcf173c420408`。
- Round 2 manifest：`human-review-round2-manifest.json`，30 Story/8 Chapter，双 Provider 各 15/4，raw SHA-256 `e1aca397b469c4d1e4e4b4f6bb856306b2b3340bcb5df97e80d71a286a247349`，canonical-LF `b2841c74491d172919db4a37e723d6533ad99f77799e0191fd5d2a7bdb90e887`。
- Round 2 worksheet：`human-review-round2-worksheet.md`，所有人工字段空白，raw SHA-256 `8e9c04bde787b6bb6c2528f96e5d296dcf66186f66290298cf18ca21f68d73e7`，canonical-LF `44655c49ef0d21c58e7aef7df4e1295dba6e48a5ddff3039f4d42edb96824692`。
- `ProjectHistoryHumanReviewRound2ManifestTest` 固定数量、Provider 比例、来源 run、相对工件、Round 1 哈希、空白人工字段与安全标志。

## 验证与报告

- 本地 backend/H2：579 项，0 失败，0 错误，5 个条件跳过。
- run `31532558352`：GLM/DeepSeek affected scenarios、frontend、Playwright、sensitive-content、Hermes、Obsidian 通过；backend/PostgreSQL 因该 head 尚无最终 Round 2 文件而失败，故不作为最终静态 CI 权威，evidence commit 以自身 PR checks 为准。
- 主要报告：`projectflow-v3.8.5-acceptance-report.md`、`projectflow-v3.8.5-human-readability-real-provider-results.md`、`projectflow-v3.8.5-human-readability-round2-review.md`、`projectflow-v3.8.5-human-readability-final-closure-audit.md`、`projectflow-v3.8.5-final-acceptance.md`。

历史失败包括 run `31468663795` 的 DeepSeek 9/11 和 run `31517037532` 的较早 GLM 资格失败。不得用后续成功覆盖。用户批准 Round 2 前不得合并、backfill、Tag、Release 或清理分支/worktree。
