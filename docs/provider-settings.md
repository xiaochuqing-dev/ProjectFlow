# Provider Settings

Provider Max Tokens is a capability/user ceiling, not a consumption target. Non-reasoning tasks still calculate a task request from entrypoint, input size and expected structure. Reasoning-capable tasks may use the configured Provider ceiling from the first request so hidden thinking and visible JSON are not crowded into an ordinary-output estimate. Diagnostics show the decision without treating higher use as lower quality.

Configured Temperature is not globally capped. Diagnostics also show the task recommendation and final value. Reasoning or other capability profiles that do not support Temperature omit the field completely. JSON mode is sent only when the Provider/model capability profile declares support.

The current built-in profiles cover DeepSeek chat, DeepSeek reasoning, standard OpenAI-compatible, and conservative custom compatibility. Unknown Providers do not receive private parameters.

Reasoning control remains a tri-state capability override. ProjectFlow sends high effort only for explicitly supported OpenAI Responses or Chat profiles, including connection, semantic and recovery requests. It never selects low to save time or Token. Automatic, unsupported and Anthropic profiles omit unsupported fields rather than guessing a private parameter.

A connection test uses the same gateway and capability policy as business analysis, but only proves basic URL, model and Key availability. It does not prove long-input quality or Schema compliance.

Keys are never returned to the frontend. Blank edits retain the existing key and explicit clearing is required. Diagnostics never include keys, Authorization, full prompts, raw responses, or reasoning text.

V3.7.2 distinguishes logical ProjectFlow model stages from transport/recovery requests. Empty/blank inputs use 0 logical calls, ordinary Semantic Scout work uses 1, and Final Synthesis uses at most 1 additional logical call after the high-value evidence gate. Transport retry, truncation recovery and schema repair remain Model Gateway diagnostics and do not authorize an extra business stage.

Real-model evaluation is an internal test workflow, not a Provider comparison page. Hallucination, accuracy, repeatability, cost and model-score fields must not be added to Provider DTOs or settings UI.

V3.7.3 keeps three different time controls explicit. Provider Settings owns the single-request processing timeout; application runtime owns the bounded connection timeout; the analysis refresh request owns AUTO/FINITE/UNLIMITED overall duration. No setting silently replaces another. UNLIMITED overall duration still has bounded network retry, cancellation and heartbeat.

Provider compatible does not mean quality qualified. All Providers use the same Evidence and Prompt Contract; dated internal real-model acceptance determines whether a Provider/model combination satisfies ProjectFlow quality. Settings must not add a model leaderboard, benchmark score, hidden quality mode or Provider-specific business prompt.

V3.7.5 keeps the product constitution provider-neutral. Prompt contract v3, Semantic Scout v13 and Final Synthesis v7 are shared across Responses and Chat profiles. A Provider cannot alter the seven fact statuses, upgrade inference, trust Agent results, skip eligible-capability decisions or relax Evidence validation. The qualified profiles are GLM `glm-5.2` and DeepSeek `deepseek-v4-flash`; the second Provider is an acceptance profile, not a production fan-out setting, and there is still one explicitly selected default Provider per task.
