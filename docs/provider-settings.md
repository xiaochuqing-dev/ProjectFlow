# Provider Settings

Provider Max Tokens is a capability/user ceiling, not a value sent by every task. ProjectFlow calculates a task request from entrypoint, input size, expected structure and reasoning behavior, then applies the Provider ceiling. Diagnostics show all three values and the decision reason.

Configured Temperature is not globally capped. Diagnostics also show the task recommendation and final value. Reasoning or other capability profiles that do not support Temperature omit the field completely. JSON mode is sent only when the Provider/model capability profile declares support.

The current built-in profiles cover DeepSeek chat, DeepSeek reasoning, standard OpenAI-compatible, and conservative custom compatibility. Unknown Providers do not receive private parameters.

A connection test uses the same gateway and capability policy as business analysis, but only proves basic URL, model and Key availability. It does not prove long-input quality or Schema compliance.

Keys are never returned to the frontend. Blank edits retain the existing key and explicit clearing is required. Diagnostics never include keys, Authorization, full prompts, raw responses, or reasoning text.
