# ProjectFlow V3.7.3 真实端到端模型验收

## 运行边界

本轮使用 GLM 5.2、OPENAI_RESPONSES、生产 Prompt Builder 和真实 ProjectUnderstandingService.refresh()。每个样本都经过 Repository Intake、Evidence Discovery、Structure Index、Historical Coverage、Semantic Scout、Adaptive Planner、注册 Capability 执行、High-value Evidence Gate、条件 Final Synthesis、Dynamic Profile、持久化和 readback。

## 结果

| Case | 状态 | 逻辑/物理请求 | 实际 Capability | Final | Evidence refs | 延迟 |
| --- | --- | ---: | --- | --- | ---: | ---: |
| strange-important-document | PASS | 2 / 3 | DOC_READER | FAILED_DEGRADED | 3 | 334,319 ms |
| small-script | PASS | 1 / 1 | MANIFEST | 无高价值新证据，跳过 | 4 | 109,484 ms |
| frontend-only | PASS | 1 / 1 | MANIFEST | 无高价值新证据，跳过 | 4 | 152,341 ms |
| backend-only | PASS | 1 / 1 | MANIFEST | 无高价值新证据，跳过 | 5 | 166,618 ms |
| fullstack | PASS | 1 / 2 | MANIFEST | 无高价值新证据，跳过 | 7 | 463,669 ms |
| no-git | PASS | 1 / 2 | DOC_READER | 无高价值新证据，跳过 | 4 | 340,396 ms |
| agent-result | PASS | 2 / 2 | AGENT_RESULT | SUCCEEDED | 4 | 253,316 ms |
| projectflow-itself | PASS | 2 / 4 | MANIFEST、GIT_HISTORY、DOC_READER、AGENT_RESULT | SUCCEEDED | 26 | 983,932 ms |

汇总：

- 8/8 通过
- 逻辑模型调用 11 次，物理请求 16 次
- Total tokens 291,900
- 最终 Evidence refs 57，非法 Evidence refs 0
- 8 个 snapshot 全部完成持久化和 readback

## 降级与真实性

strange-important-document 的 Final Synthesis 未完成时，系统保留 Stage 1 与已校验 DOC_READER Evidence，标记 FAILED_DEGRADED，仍生成当前、可读、可追溯的结果。这验证了 Final Synthesis 失败的 100% 安全降级要求。

no-git 没有请求 Git capability，也没有生成虚假历史。Agent result 保持 PROCESS_EVIDENCE，不自动升级为 ProjectFact。ProjectFlow itself 实际执行四个注册 Provider，最终引用均来自真实允许 Evidence ID，不使用固定 Tool Evidence 代替 Provider。

## 结论

8 个核心端到端全部通过。最长 ProjectFlow 样本超过 16 分钟仍正常完成，证明分析总时长不再被短硬上限截断；Provider 请求仍保持有限超时、取消轮询、心跳和有界恢复。
