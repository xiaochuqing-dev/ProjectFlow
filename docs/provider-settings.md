# Provider Settings

V4.0-C Draft 的日常入口为 `/workspace/settings` 的“模型与 API”。列表、创建、编辑、删除、默认选择与连接测试都在 V4 Workspace 内完成。类型、模型协议、服务地址在基本表单；认证方式、端点覆盖、超时、输出上限、Temperature、能力三态覆盖和安全 Header 放在高级设置。既有 purpose tags 在编辑中原样保留，批量重复配置清理继续使用旧 `/settings` 兼容入口。

凭据仅通过既有 Provider API 和安全 Credential Store 保存。编辑器不读取原始 Key，新凭据输入默认空白；留空保留旧值，显式清除或替换需要确认。已配置凭据时，改变请求地址或认证目的地也会要求确认。Header 值不回显；普通编辑省略 `safeHeaders` 保留既有值，仅勾选“替换自定义 Header”才提交新的完整集合。保存/删除失败保留原配置并显示错误。

当前只有 `defaultEnabled` 全局默认选择，没有独立 Provider 启用开关或项目级 Provider/Model/Policy 持久化绑定。取消默认会让新的模型分析暂时无默认项；已有 Provider 仍可编辑或单独测试。`/workspace/project-settings` 显示这一边界并链接到全局配置，不存储伪绑定。

显式 `?demo=1` 的编辑、保存、删除、默认选择和测试全部只改变当前页面内的示例，不发送 Provider API 请求；请勿填写真实凭据。

Provider Max Tokens is a capability/user ceiling, not a consumption target. Non-reasoning tasks still calculate a task request from entrypoint, input size and expected structure. Reasoning-capable tasks may use the configured Provider ceiling from the first request so hidden thinking and visible JSON are not crowded into an ordinary-output estimate. Diagnostics show the decision without treating higher use as lower quality.

Configured Temperature is not globally capped. Diagnostics also show the task recommendation and final value. Reasoning or other capability profiles that do not support Temperature omit the field completely. JSON mode is sent only when the Provider/model capability profile declares support.

The current built-in profiles cover DeepSeek chat, DeepSeek reasoning, standard OpenAI-compatible, and conservative custom compatibility. Unknown Providers do not receive private parameters.

Reasoning control remains a tri-state capability override. ProjectFlow sends high effort only for explicitly supported OpenAI Responses or Chat profiles, including connection, semantic and recovery requests. It never selects low to save time or Token. Automatic, unsupported and Anthropic profiles omit unsupported fields rather than guessing a private parameter.

A connection test uses the same gateway and capability policy as business analysis. It checks connection, protocol parsing and the existing minimal ProjectFlow structured summary task; it does not prove long-input quality or compatibility with every business schema. A response with `ok: false` remains a failed test even when its HTTP request succeeded.

Keys are never returned to the frontend. Blank edits retain the existing key and explicit clearing is required. Diagnostics never include keys, Authorization, full prompts, raw responses, or reasoning text.

V3.7.2 distinguishes logical ProjectFlow model stages from transport/recovery requests. Empty/blank inputs use 0 logical calls, ordinary Semantic Scout work uses 1, and Final Synthesis uses at most 1 additional logical call after the high-value evidence gate. Transport retry, truncation recovery and schema repair remain Model Gateway diagnostics and do not authorize an extra business stage.

Real-model evaluation is an internal test workflow, not a Provider comparison page. Hallucination, accuracy, repeatability, cost and model-score fields must not be added to Provider DTOs or settings UI.

V3.7.3 keeps three different time controls explicit. Provider Settings owns the single-request processing timeout; application runtime owns the bounded connection timeout; the analysis refresh request owns AUTO/FINITE/UNLIMITED overall duration. No setting silently replaces another. UNLIMITED overall duration still has bounded network retry, cancellation and heartbeat.

Provider compatible does not mean quality qualified. All Providers use the same Evidence and Prompt Contract; dated internal real-model acceptance determines whether a Provider/model combination satisfies ProjectFlow quality. Settings must not add a model leaderboard, benchmark score, hidden quality mode or Provider-specific business prompt.

V3.7.5 keeps the product constitution provider-neutral. Prompt contract v3, Semantic Scout v13 and Final Synthesis v7 are shared across Responses and Chat profiles. A Provider cannot alter the seven fact statuses, upgrade inference, trust Agent results, skip eligible-capability decisions or relax Evidence validation. The qualified profiles are GLM `glm-5.2` and DeepSeek `deepseek-v4-flash`; the second Provider is an acceptance profile, not a production fan-out setting, and there is still one explicitly selected default Provider per task.
