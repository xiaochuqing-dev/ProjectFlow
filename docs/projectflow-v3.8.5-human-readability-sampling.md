# ProjectFlow V3.8.5 人工可读性抽样记录

状态：PENDING_HUMAN_REVIEW。Round 2 已冻结，尚未评分。

固定脚本从 qualified 的 GLM/DeepSeek 规范化工件选择 30 Story 与 8 Chapter，双 Provider 各 15/4。完整资格/场景来源分别为 GLM run `31523413972`、DeepSeek Flash run `31517037532`；纠正样本由 run `31532558352` 的受影响工件覆盖。脚本拒绝未 qualified 工件、敏感值、机器绝对路径和中文编号占位符。

抽样覆盖 ProjectFlow、非代码、长短历史、generic Commit、多 Commit 单成果、单 Commit 多成果、生命周期、rename/move、split/merge、unknown reason、conflict、Supporting 与 correction。清单绑定相对工件路径、实体 ID、内容哈希和 presentation revision。

当前 Story 评分 0/30、Chapter 评分 0/8、平均分 NOT_RUN。低分不能删除，模型或本 Agent 不得代填。完成单一真实评审后必须披露 single-reviewer limitation；用户批准前 PR #15 保持 Draft。
