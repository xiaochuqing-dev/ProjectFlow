# Provider Settings

Provider Max Tokens is a capability/user ceiling, not a value sent by every task. ProjectFlow calculates a task request from entrypoint, input size, expected structure and reasoning behavior, then applies the Provider ceiling. Diagnostics show all three values and the decision reason.

Configured Temperature is not globally capped. Diagnostics also show the task recommendation and final value. Reasoning or other capability profiles that do not support Temperature omit the field completely. JSON mode is sent only when the Provider/model capability profile declares support.

The current built-in profiles cover DeepSeek chat, DeepSeek reasoning, standard OpenAI-compatible, and conservative custom compatibility. Unknown Providers do not receive private parameters.

Reasoning control remains a tri-state capability override. ProjectFlow sends `reasoning.effort` only for an explicitly supported OpenAI Responses profile: `high` for the first structured analysis and `low` for the connection probe or single recovery request. Automatic, unsupported, Chat and Anthropic profiles omit it rather than guessing a private field.

A connection test uses the same gateway and capability policy as business analysis, but only proves basic URL, model and Key availability. It does not prove long-input quality or Schema compliance.

Keys are never returned to the frontend. Blank edits retain the existing key and explicit clearing is required. Diagnostics never include keys, Authorization, full prompts, raw responses, or reasoning text.

V3.7.2 distinguishes logical ProjectFlow model stages from transport/recovery requests. Empty/blank inputs use 0 logical calls, ordinary Semantic Scout work uses 1, and Final Synthesis uses at most 1 additional logical call after the high-value evidence gate. Transport retry, truncation recovery and schema repair remain Model Gateway diagnostics and do not authorize an extra business stage.

Real-model evaluation is an internal test workflow, not a Provider comparison page. Hallucination, accuracy, repeatability, cost and model-score fields must not be added to Provider DTOs or settings UI.

V3.7.3 keeps three different time controls explicit. Provider Settings owns the single-request processing timeout; application runtime owns the bounded connection timeout; the analysis refresh request owns AUTO/FINITE/UNLIMITED overall duration. No setting silently replaces another. UNLIMITED overall duration still has bounded network retry, cancellation and heartbeat.

Provider compatible does not mean quality qualified. All Providers use the same Evidence and Prompt Contract; dated internal real-model acceptance determines whether a Provider/model combination satisfies ProjectFlow quality. Settings must not add a model leaderboard, benchmark score, hidden quality mode or Provider-specific business prompt.
