# ProjectFlow V3.8.5 RC2 真实 Provider 结果

历史结果必须保留：GLM protocol/schema contract PASS，但 19-case qualification FAIL；DeepSeek protocol/schema contract PASS，但 19-case qualification FAIL；DeepSeek 真实场景 10/11，ProjectFlow Dogfood 因角色引用不一致 FAIL；GLM 完整 real scenarios NOT_RUN。

RC2 使用统一合同：GLM 为 glm-5.2、OPENAI_RESPONSES；DeepSeek 为用户指定 OpenCode Go 端点、OPENAI_CHAT_COMPLETIONS。Key 只允许从进程环境或 GitHub Secrets 注入，报告不记录值。

2026-08-08 已调度 workflow 31264440534。凭据检查因 PROJECTFLOW_REAL_MODEL_API_KEY 与 PROJECTFLOW_DEEPSEEK_API_KEY 未配置而失败，真实步骤全部跳过；没有请求、token 或计费。不得把该结果写成模型失败，也不得写成通过。

RC2 新资格、19-case、real scenarios、ProjectFlow Dogfood、非代码、请求数、token、降级窗口和安全计数均为 NOT_RUN，等待安全凭据配置后重跑。
