# V3.8.5 模型资格与运行边界

模型调用只经 `ModelGatewayService`，任务类型为 `PROJECT_HISTORY_SYNTHESIS`。Provider 协议、超时、取消、重试、usage、finish reason 和 schema 诊断由 Gateway 归一化；业务层不拼接 Provider 请求，也不按模型名硬编码语义答案。

统一最小合同只允许 Story 的 ID、标题、摘要、有 Evidence 的原因与 Unknown，以及 Chapter 的 ID、标题与摘要。角色关系、Chapter 成员、状态三段、冲突与 Evidence 归属由工程层提供。首次语义校验失败只允许一次同输入安全重生成；reasoning-only 空 content 也只允许一次同输入恢复，第二次仍空即失败，不发第三次请求。

每轮最多处理 16 个未完成窗口。缓存要求 source fingerprint、strategy/Prompt、window identity、presentation revision 和成员集合精确一致；失败、取消、运行中、跳过或 pending 都阻止全局 cache hit。Prompt 超限确定性拆分；局部失败保留 checkpoint，独立窗口继续。

## 最终真实配置

- GLM：`glm-5.2`、Ark Coding、`OPENAI_RESPONSES`、high。
- DeepSeek：`deepseek-v4-flash`、OpenCode Go、`OPENAI_CHAT_COMPLETIONS`、max。当前配置不使用 V4 Pro。
- API Key 只由 GitHub Repository Secrets 注入，不进入仓库、工件或报告。

## workflow 31318477841

| 门禁 | GLM | DeepSeek Flash |
| --- | --- | --- |
| V3.8.0 合同 | PASS，1 请求 / 5,131 token | PASS，1 请求 / 3,846 token |
| V3.7.5 38-run | 38/38，52 请求 / 521,726 token | 38/38，64 请求 / 663,829 token |
| Understanding | 17/17 | 17/17 |
| V3.8.5 qualification | PASS，20 请求 / 97,269 token | PASS，21 请求 / 121,540 token |
| 失败/未处理窗口 | 0 | 0 |
| rejected model output | 0 | 0 |
| validation repair failure | 0 | 0 |
| 最终 scenarios | 11/11 | 11/11 |

DeepSeek scenarios attempt 1 为 9/11；17-window 有 1 failed、1 pending，correction 连带不可用。相同 head 只重跑失败 job 后 attempt 2 为 11/11。该波动被保留，不用固定兼容模型、安全计数或第二次成功覆盖首次失败。

固定兼容 11/11 仍只证明执行器、窗口、缓存和故障恢复，不作为真实 Provider 资格。双 Provider 自动资格已通过；最终产品状态仍因人工 30/8 未评分而 BLOCKED。
