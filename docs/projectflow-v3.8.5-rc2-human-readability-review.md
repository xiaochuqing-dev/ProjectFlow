# ProjectFlow V3.8.5 RC2 人工可读性复核

状态：BLOCKED / NOT_RUN。历史人工结果仍为 0 Story / 0 Chapter，不能用自动 evaluator、固定模型或本 Agent 自评替代真实人工评分。

计划冻结至少 30 个 Story 和 8 个 Chapter，覆盖 GLM、DeepSeek、ProjectFlow Dogfood、五类非代码、长短历史、generic Commit、多 Commit/多成果、生命周期、rename/move、split/merge、unknown、conflict、Supporting 和 correction。样本必须分层选取并保留低分，不只挑最好结果。

Story 逐项按 1 至 5 分评价“原来怎样、改了什么、现在怎样、结果是否可复述”，同时记录术语泄漏、内部 enum、空模板、文件即成果和原因猜测。Chapter 评价时间层次、中心变化、自然标题、主要成果压缩和 Supporting 处理。4 分表示普通用户读一遍后能大致转述变化。

真实 Provider 新工件尚未产生，因此 human-review-sample-manifest.json 尚不能诚实冻结。完成真实重跑后生成清单并由独立人工评分；在平均分、最低分和缺陷分布被记录前，本门禁保持 NOT_RUN。
