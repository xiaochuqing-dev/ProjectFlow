# ProjectFlow V3.8.5 RC2 人工可读性复核

状态：BLOCKED / PENDING_HUMAN_REVIEW。自动化真实模型门禁已完成，但真实人工评分尚未发生；不能用自动 evaluator、模型自评、固定模型或本 Agent 评分替代。

来源为 workflow `31318477841` 的最终合格工件。`docs/acceptance-evidence/v3.8.5/human-review-sample-manifest.json` 固定 30 个 Story 和 8 个 Chapter，GLM 与 DeepSeek 各 15 Story / 4 Chapter；`human-review-worksheet.md` 提供完整内容与空白评分项。清单绑定 Run、相对工件路径、实体 ID、内容哈希和 presentation revision，状态为 `PENDING_HUMAN_REVIEW`，reviewerCount 为 0，modelSelfScoring 为 false。

分层覆盖 ProjectFlow、五类非代码、长短历史、generic Commit、多 Commit 单成果、单 Commit 多成果、生命周期恢复、rename/move、split/merge、unknown reason、conflict、Supporting 和 correction。样本按固定规则选择，低分必须保留，不允许只挑最好结果。

Story 逐项按 1 至 5 分评价是否能说明原来怎样、改了什么、现在怎样和项目结果，并记录 enum/术语泄漏、空模板、文件变化冒充成果和无 Evidence 原因猜测。Chapter 评价时间层次、中心变化、自然标题、主要成果压缩和 Supporting 处理。4 分表示普通用户读一遍后能大致转述；平均分门槛为 4.0。Invalid Evidence、跨项目引用、Raw Event 丢失、孤立 Supporting 或无 Evidence 的强原因直接失败。

当前 Story 评分 0/30、Chapter 评分 0/8、人工平均分 NOT_RUN。完成单一真实评审后还必须明确 single-reviewer limitation，不得冒充多人一致性。门禁通过前 PR #15 保持 Draft。
