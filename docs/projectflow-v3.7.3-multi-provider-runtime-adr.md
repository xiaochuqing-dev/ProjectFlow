# ProjectFlow V3.7.3 Multi-provider Runtime ADR

状态：Accepted

## 统一路径

Provider probe、direct real-model Eval、Semantic Scout、Final Synthesis、`ProjectUnderstandingService.refresh()` 和未来 GUI 分析均遵守同一 runtime 语义。业务入口仍只调用 `ModelGatewayService`，协议差异仅存在于 adapter。

`CanonicalModelRequest` 分别携带 connection timeout 与 request timeout。OpenAI Java SDK 和 Anthropic Java SDK 使用官方 `Timeout` builder 设置 connect/read/write/request，并显式 `maxRetries(0)`；兼容 relay 同样分离建连和整请求 timeout。ProjectFlow Gateway 统一负责最多一次 transport retry、并发、取消、deadline 和安全 diagnostics。

一次 Gateway 请求内若发生 transport retry，latency 从第一个 attempt 开始累计，并包含重试间隔和后续成功 attempt；不得只记录最后一次成功请求而低报真实墙钟耗时。

## Provider 兼容与质量

OPENAI_RESPONSES、OPENAI_CHAT_COMPLETIONS、ANTHROPIC_MESSAGES 继续使用同一事实、Evidence、Prompt 和 Schema Contract。Provider compatible 只说明协议可调用，不代表 semantic quality qualified。Provider 品牌、model 名称或推理风格不得改变 Evidence 事实标准，也不得触发 case-specific Prompt。

对于 reasoning 与可见 JSON 共用 `max_output_tokens` 的模型，Capability Registry 省略不支持的 temperature，并使用任务自身的有界有效输出上限。Provider 只有在显式 capability override 声明支持 reasoning control 时，OpenAI Responses adapter 才发送标准 `reasoning.effort`：首次结构化分析使用 `high` 保持 QUALITY_FIRST，连接探针与唯一恢复请求使用 `low`，避免第二次让隐藏 reasoning 再次挤占可见 JSON。未知 Provider、未声明支持以及 Chat/Anthropic 协议均不猜测、不发送该字段。Evidence、Schema、事实标准和 Final 资格不变，语义质量仍由原门槛真实复验。

## 安全

Key 只从进程环境或已有本地 Provider 配置读取，不写入源码、报告、日志、测试工件或 DTO。Prompt、raw response、reasoning、Authorization、自定义 Header 值和未脱敏绝对路径不持久化。真实评测只上传派生且脱敏的指标工件。

## 不采用

不引入第二套 HTTP client、Provider switcher、SDK retry、无限重试、模型排行榜或按 Provider 复制业务逻辑。
