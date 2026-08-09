# ProjectFlow V3.8.5 人工可读性抽样记录

状态：BLOCKED / PENDING_HUMAN_REVIEW。双 Provider 最终工件已产生并冻结样本，但真实人工评分尚未填写。

## 冻结结果

来源 Run：`31318477841`。`docs/acceptance-evidence/v3.8.5/human-review-sample-manifest.json` 固定 30 个 Story 与 8 个 Chapter；GLM 和 DeepSeek 各 15 Story / 4 Chapter。清单绑定相对工件路径、实体 ID、内容哈希、presentation revision、项目类型和覆盖标签。`human-review-worksheet.md` 提供逐项内容及空白人工评分项。

分层覆盖两个真实 Provider、ProjectFlow、五类非代码、短历史、长历史、generic Commit、add/modify/remove/restore、rename/move、merge/split、conflict、unknown reason、Supporting、单 Commit 多成果、多 Commit 单成果和用户修正。固定脚本拒绝未 qualified 工件、敏感值和机器绝对路径。

## 评分规则

每项 1–5 分：是否一眼知道改了什么、是否理解前后状态、是否出现工程术语、是否像完整成果、Evidence 是否可达、是否编造原因、是否过度泛化。4 分表示非工程用户读一遍后能用自己的话说明改变和结果；平均分门槛为 4.0。Invalid Evidence、跨项目引用、Raw Event 丢失、孤立 Supporting 或无 Evidence 强原因直接失败。

低分必须保留，不能只挑最好结果；自动 evaluator、模型自评和本 Agent 自评均无效。完成单一真实评审后必须记录评审人、每项分数、缺陷分布、最低分和平均分，并披露 single-reviewer limitation。

## 当前结果

Story：0/30 已评分。Chapter：0/8 已评分。人工平均分：NOT_RUN。清单状态：PENDING_HUMAN_REVIEW。PR #15 必须保持 Draft。
