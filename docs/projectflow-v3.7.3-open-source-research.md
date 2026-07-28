# ProjectFlow V3.7.3 Open-source and Official Research

调研日期：2026-07-27

## 采用映射

| 来源 | 分类 | 采用内容 | 未采用内容 |
| --- | --- | --- | --- |
| OpenAI openai-java | DIRECT_REUSE | 官方 SDK `Timeout` 的 connect/read/write/request 分离、SDK retry 关闭；Responses `Reasoning` / `ReasoningEffort` 类型 | 不让 SDK 管理业务重试、取消或总预算 |
| Anthropic anthropic-sdk-java | DIRECT_REUSE | 同样使用官方 timeout builder 和 `maxRetries(0)` | 不新增第二套业务 Gateway |
| OpenAI Evals build-eval guidance | PATTERN_REUSE | Ground Truth 与 Prompt 分离、先小批次后完整评测、保持同一门槛 | 不引入通用 Eval 平台或依赖 |
| GitHub Actions workflow cancellation | PATTERN_REUSE | Durable Job/外部调用边界中反复检查取消并保留可诊断状态 | 不复制 GitHub runner 或 workflow engine |
| 火山方舟 Responses API 文档与官方 Go SDK | PROTOCOL_REUSE | `max_output_tokens` 同时包含 reasoning 与可见回答；Responses 暴露 `reasoning.effort` 和 reasoning usage | 不按模型名猜能力，不持久化 reasoning，不改变事实标准 |
| DietrichGebert/ponytail | REFERENCE_ONLY | 先复用现有代码、标准库和依赖，再写最小 helper | 不作为依赖，不复制项目业务 |

## 核验结果

本地 Maven 依赖中的 OpenAI Java 4.43.0 与 Anthropic Java 2.49.0 均暴露独立 connect/read/write/request timeout。现有项目已安装两套官方 SDK，因此只调整 adapter 配置，不新增依赖或 THIRD_PARTY_NOTICES 条目。

火山方舟官方 Responses 文档确认输出预算包含模型回答与思维内容，官方 Go SDK 的 `ResponsesRequest` 提供 `ResponsesReasoning` / `ReasoningEffort`，本地 OpenAI Java 4.43.0 也提供同名标准类型。对用户给定的 GLM 5.2 Coding v3 端点执行了不保存正文的最小探针：`reasoning.effort=low` 返回 completed，84 output tokens、0 reasoning tokens。实现因此复用标准 Responses 字段，但只在 Provider 显式声明支持时启用；首次分析 high 保持质量，连接探针与唯一恢复 low。ProjectFlow 不保存思维内容，只保留 presence/长度/usage diagnostics。

## 明确拒绝

拒绝自研 HTTP/Provider SDK、通用 Durable Workflow、Agent runtime、模型路由器/排行榜、Eval SaaS、parser/grammar、SCIP producer、secret scanner、RAG/vector store、daemon、watcher 和 Desktop shell。项目继续使用既有 Model Gateway、Durable Job、Capability Registry、CI secret scan 和 SDK。

## 参考链接

- https://github.com/openai/openai-java
- https://github.com/anthropics/anthropic-sdk-java
- https://github.com/openai/evals/blob/main/docs/build-eval.md
- https://docs.github.com/en/enterprise-cloud@latest/actions/reference/workflows-and-actions/workflow-cancellation
- https://www.volcengine.com/docs/6492/2241840
- https://www.volcengine.com/docs/82379/1795150
- https://pkg.go.dev/github.com/volcengine/volcengine-go-sdk/service/arkruntime/model/responses
- https://github.com/DietrichGebert/ponytail
