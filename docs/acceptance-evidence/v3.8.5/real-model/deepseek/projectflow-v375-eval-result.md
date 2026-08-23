# ProjectFlow V3.7.5 Internal Model Evaluation

本指标仅代表本阶段人工标注代表性测试集，不构成对任意项目的通用准确率承诺。

Dataset standard: ProjectFlow V3.7.2 stage-specific human ground truth; not a universal benchmark
Harness: projectflow-v3.7.5-eval-v1
Prompt versions: semantic-scout-v15+final-synthesis-v7

## Product-level reliability

Total / successful / failed runs: 38 / 38 / 0
Timeout / schema failure / retry: 0 / 0 / 13
Average / P95 latency ms: 80460.55 / 179128.00
End-to-end completion: 1.0000
Degradation / cancellation: 0 / 0

## Conditional semantic quality

Valid structured runs: 38
Unsupported claim rate: 0.0000
Critical evidence recall: 0.9481
Evidence precision: 1.0000
Project shape F1: 0.7097
Tool precision / recall: 0.9792 / 0.9792
Deep-read precision / sufficiency: 0.2456 / 0.7778
Dynamic view precision / recall: 0.4219 / 0.9529
Conflict detection: 0.6667
Repeatability: 0.9578
Second-stage evidence gain: 1.0000
Requests / tokens: 64 / 663829
Estimated cost: UNAVAILABLE

Raw model responses, reasoning and secrets are not stored.
