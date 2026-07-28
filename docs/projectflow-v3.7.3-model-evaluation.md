# ProjectFlow V3.7.3 模型评估报告

## 评估范围

- 模型：GLM 5.2
- 协议：OPENAI_RESPONSES
- 真实入口：ModelGatewayService
- Prompt：semantic-scout-v10、final-synthesis-v5、project-understanding-prompt-contract-v1
- 样本：原始 18 cases，加 10 个重要 cases 各重复 3 次，共 38 case-runs
- Ground Truth：沿用 V3.7.2，工作树与基线 blob 均为 `066ee027b53e0884323c24e36d4019f3ae57f70a`
- 边界：Ground Truth、case ID、期望标签和阈值均未进入生产 Prompt Builder

## A. Product-level Reliability

| 指标 | 最终结果 |
| --- | ---: |
| Total runs | 38 |
| Success | 38 |
| Failure | 0 |
| Timeout | 0 |
| Schema failure | 0 |
| Retry | 4 |
| Cancellation | 0 |
| Degradation | 0 |
| End-to-end completion | 1.0000 |
| Failure rate | 0.0000 |
| Average latency | 145,919 ms |
| P95 latency | 337,617 ms |

取消、心跳、显式总体截止时间和重启恢复由自动化测试与浏览器流程单独验证。正式 38-run 没有人为取消样本，因此 cancellation 为 0，不代表取消能力未测试。

## B. Conditional Semantic Quality

38 个样本均产生有效结构化结果，因此 conditional run count 为 38。

| 指标 | 最终结果 | 门槛 |
| --- | ---: | ---: |
| Critical Evidence Recall | 0.9610 | >= 0.85 |
| Evidence Precision | 0.8409 | 记录项 |
| Unsupported Claim Rate | 0.0000 | <= 0.05 |
| Project Shape F1 | 0.7912 | 记录项 |
| Project Shape Exact Accuracy | 0.7895 | 记录项 |
| Tool Selection Precision | 1.0000 | 记录项 |
| Tool Selection Recall | 0.8750 | >= 0.80 |
| Unnecessary Tool Rate | 0.0000 | <= 0.15 |
| Unavailable Tool Request | 0 | 必须为 0 |
| Rejected Dangerous Tool Request | 0 | 必须为 0 |
| Dynamic View Recall | 0.9529 | >= 0.90 |
| Dynamic View Precision | 0.4241 | 记录项 |
| Conflict Detection | 0.6667 | 记录项 |
| Repeatability | 0.9680 | >= 0.80 |
| Stage 2 Evidence Gain | 1.0000 | 至少一项正增益 |
| Stage 2 View Gain | 0.0476 | 至少一项正增益 |
| Stage 2 Unsupported Claim Reduction | 0.0000 | 记录项 |

## Token 与恢复

- 模型物理请求：54
- Input tokens：135,203
- Output tokens：399,999
- Total tokens：535,202
- Cost：Provider 未提供可核验价格信息，不估算
- 恢复：4 次；只允许有界 transport retry、截断恢复或定向 Schema repair

## Gate 结论

正式 38-run 的可靠性与既定语义门槛全部通过。空目录和空白文本保持 0 模型调用；没有逐文件或逐提交模型循环；Ground Truth 未修改；没有不可用或危险 capability 请求。

已知质量空间不伪装为阻断失败：Dynamic View precision、Deep Read target accuracy 和 Conflict Detection 仍有提升空间，但它们没有违反本阶段既定门槛。8-case 端到端、全量自动化、安全检查、PR CI 和最终功能 master CI 也已通过。

V3.7.3 MULTI-PROVIDER QUALITY GATE = PASS

GLM QUALITY QUALIFIED = YES

V3.8 ENTRY = APPROVED
