# ProjectFlow V3.9 验收证据索引

当前状态：`TECHNICAL_ACCEPTANCE_PASS / HUMAN_REVIEW_REQUIRED`。生产/eval 源码 Head 为 `eb38c78fe70d3cf9280e716f7fc906d8729b15b1`；真人 Continuity review 未执行，V3.9 不是 Final PASS。

## 冻结与确定性证据

- `continuity-ground-truth.json`：实现前冻结的 15 Calibration + 15 Holdout，SHA-256 `be0f0967073bda24c7dae2a7f90a4b60c42b12ce1ec23b6c99953b04f00db9e7`。
- `continuity-ground-truth-execution-map.json`：30/30 case 到真实 Maven/Python 测试的逐条绑定，SHA-256 `4d012f555fe0bc853a48d9aa10b49231660cbcad1cfe68d0de5777f1ea4b212a`。
- `dogfood-sequence.json`：ProjectFlow T0–T7 连续使用序列，SHA-256 `600a0d483920ad0e79c8fac6ea05d7f11c9469cf7539e7e4d71908c7a05aad70`。
- `technical-acceptance-evidence.json`：技术门、CI、真实 Provider、安全、失败保留和人工边界的机器可读汇总。
- `human-continuity-review-worksheet.json`：12 个真人场景，当前所有人工字段为空，SHA-256 `75f9f18836d87c77db0d8a89fd3541348d2b48f28673e68ffa57e96ff000f9df`。

## 最终收口新增的 append-only 输入

- `final-owner-acceptance-policy.json`：记录 Owner 免除逐项手填并授权自动化 + 独立盲评的边界；明确禁止伪造 `HUMAN_REVIEW_PASS`，原 worksheet 不修改。
- `independent-semantic-review-package.json`：原 12 个 HUMAN 场景的无答案标题、有界盲评输入；不包含冻结答案、实现状态、测试结果或其他模型判断。
- `independent-semantic-review-schema.md`：独立复核的字段、单 Provider 一次逻辑调用预算、工件完整性和敏感信息边界。
- `../../projectflow-v3.9-final-closure-report.md`：最终收口报告；合并前只记录本地实测与远端待完成门禁，最终 merge/backfill 事实将在完成后追加。

这三项只定义最终收口路径，不预先声明真实 Provider、PR、merge 或 master 门禁通过。实际三 Provider 独立复核工件与最终 CI/merge 事实必须在对应 run 完成后 append-only 回填。

## GitHub 与真实 Provider

- 同头普通 CI：push run `32666198144` 与 PR run `32666201528` 均为 SUCCESS。
- 失败保留：run `32659635453` 的 Qwen Chapter 为 8/9，详见 `failed-runs/32659635453/qwen-chapter-regression-summary.json`。
- 修复后受影响重验：run `32666372066` 为 SUCCESS；三 Provider qualification 均 19/19，Chapter 均 9/9，continuity 均 3/3。
- `real-model/{luna,deepseek,qwen}/history-ground-truth-real-result.json`：三种协议的资格工件。
- `real-model/{luna,deepseek,qwen}/history-chapter-regression.json`：三种协议的 V3.8.5 Chapter 受影响回归。
- `real-model/{luna,deepseek,qwen}/history-continuity-scenarios.json`：三种协议的 V3.9 连续性产品场景。
- `failed-runs/32778908166/luna-independent-semantic-review-summary.json`：最终收口首次 Luna 独立盲评的 reasoning-exhausted 失败摘要与安全诊断；后续成功不得覆盖。

九个真实工件的完整性哈希、请求数、Token、耗时、fallback 和 repair 计数见 `technical-acceptance-evidence.json`。工件不保存 Key、Authorization、Prompt、raw response、reasoning 或机器绝对路径。自动结果不会改写空白的真人 worksheet，也不授权 Ready、merge、V3.10 ENTRY、Tag 或 Release。
