# V4.0-D Acceptance Evidence

本目录区分工程契约、真实模型运行、Agent 来源审计、模型盲审和 Owner Review。阶段结论见 [报告](../../projectflow-v4.0-d-real-project-understanding-report.md)。Owner Review = NOT_REVIEWED；DESKTOP_SHELL_ENTRY = BLOCKED，不能因引用检查通过而声称历史语义已充分成型。

| Evidence | 范围 |
| --- | --- |
| [verification.json](verification.json) | 各层实际结果、失败、跳过和完成边界 |
| [real-project-manifest.json](real-project-manifest.json) | 两个冻结真实 clone 的版本、规模和覆盖 |
| [claim-audit.json](claim-audit.json) | 各 27 条用户陈述的来源、身份与措辞限制 |
| [branch-workline-audit.json](branch-workline-audit.json) | 19 + 1 条工作线的独立 Git/PR 检查 |
| [real-model-usage.json](real-model-usage.json) | 生产 Gateway 的安全计数与规范化诊断 |
| [model-review-summary.json](model-review-summary.json) | 独立 gpt-5.6-sol / xhigh 用户输出评审 |
| [ci.json](ci.json) | 各实施 Head 的实际 GitHub checks |
| [windows-launcher.json](windows-launcher.json) | 工作区外根启动器、构建、正常退出与端口 |
| [visual-manifest.json](visual-manifest.json) | 图片范围、类型、尺寸、hash、Agent 视觉检查 |
| [failed-runs](failed-runs) | 早期真实 Provider、缓存、浏览器与审查失败摘要 |

`screenshots/real` 来自实际生产前端和 Java 后端中的只读真实项目、普通接入和设置页面。`screenshots/synthetic` 包含 Demo、固定 API / 模型测试和故障注入；即使使用真实浏览器，它们也不构成真实仓库语义验收。具体每张的类型以 visual manifest 为准。

真实源仓库未写入。临时 clone、隔离数据库、运行日志和本机路径不提交。Evidence 不包含 Key、Authorization、原始模型 Prompt/response/reasoning 或私有 URL。模型完成状态只证明该次请求或 Job 的结果；确定性 fallback 和尚未读取范围保留。

没有真实 30+ branch 样本，也没有把单分支样本伪造成 Review。多分支边界由真实 19 分支加 36 分支合成测试互补。ProjectFlow 的 D clone 与后续远端 Head 不同，是冻结输入与实现演进的正常差异，界面明确显示该差异。
