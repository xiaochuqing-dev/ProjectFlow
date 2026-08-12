# Model Provider Configuration

## Concepts

Preset identifies the Provider family; protocol identifies the wire contract. OpenAI defaults to Responses, Anthropic defaults to Messages, and DeepSeek/OpenAI-compatible/custom legacy presets default conservatively to Chat Completions. Protocol is persisted and participates in duplicate identity.

The existing settings page exposes only the necessary fields: preset, protocol, base URL, optional endpoint override, model, API key, auth mode, timeout and optional capability overrides. Capability overrides are tri-state: automatic, supported or unsupported.

## Examples

| Use case | Preset | Protocol | Base URL | Auth |
| --- | --- | --- | --- | --- |
| OpenAI official Responses | OPENAI | OPENAI_RESPONSES | `<BASE_URL>` such as `https://api.openai.com/v1` | PROTOCOL_DEFAULT with `<API_KEY>` |
| OpenAI official Chat | OPENAI | OPENAI_CHAT_COMPLETIONS | `<BASE_URL>` | BEARER with `<API_KEY>` |
| OpenAI-compatible relay | OPENAI_COMPATIBLE or CUSTOM | OPENAI_CHAT_COMPLETIONS | `<BASE_URL>` | Relay-documented Bearer/API-key mode |
| DeepSeek compatible | DEEPSEEK | OPENAI_CHAT_COMPLETIONS | `<BASE_URL>` | BEARER with `<API_KEY>` |
| Anthropic official | ANTHROPIC | ANTHROPIC_MESSAGES | `<BASE_URL>` such as `https://api.anthropic.com` | ANTHROPIC_STANDARD with `<API_KEY>` |
| Anthropic-compatible relay | CUSTOM | ANTHROPIC_MESSAGES | `<BASE_URL>` | Relay-documented standard/Bearer/API-key mode |
| Local relay | CUSTOM | Relay protocol | `http://127.0.0.1:<PORT>/v1` | NONE or explicit local relay mode |

Every example also requires `<MODEL>`. Use endpoint override only when the relay gives a complete URL, for example `<BASE_URL>/custom/chat/completions`; ProjectFlow will not append the endpoint twice. A query key belongs on the explicit query-auth setting, not in the saved API key field or documentation.

## Authentication and URL safety

Supported modes are protocol default, Bearer, API-key header, Anthropic standard, query API key and none. Custom authentication names and extra headers are bounded. Extra headers reject Authorization, API-key headers, Host, Content-Length, Connection, proxy/forwarding headers, Anthropic-Version, invalid names and CR/LF values. Only configured header names are returned by the API; values and API keys are never returned.

Remote endpoints require HTTPS. HTTP is limited to localhost/127.0.0.1. A base URL cannot carry query/fragment parameters; use a protocol-matching full endpoint override for a relay that requires a query. Endpoint user-info, mismatched protocol paths and unsafe IP literals are rejected.

## Migration and compatibility profile

Startup migration only fills missing protocol and auth mode. It preserves Provider ID, key, model, temperature, output ceiling, default selection and purpose tags, and is idempotent. DEEPSEEK, OPENAI_COMPATIBLE and CUSTOM map to Chat rather than being guessed as Anthropic.

Compatibility testing performs a basic structured transport/protocol task and a minimal ProjectFlow task. It reports connection, auth, protocol, structured-output/JSON/temperature/reasoning capability, usage, output-limit contract, overall ProjectFlow compatibility, warnings and request count. `FULL`, fallback compatibility and transport reachability are distinct states.

API keys are still stored in the local application database for compatibility. They are excluded from DTOs, logs, diagnostics and artifacts, but at-rest storage remains a known risk. Desktop productization must move them behind an OS-backed SecretStore without changing the Provider contract.

## V3.7.2 real-model evaluation

The optional real-model quality gate uses the existing Provider configuration or CI environment injection and always calls Model Gateway V2. It does not construct a direct HTTP client or change the saved Provider. Local database-based evaluation reads a copied database in read-only mode; CI uses `DEEPSEEK_API_KEY`.

Before a real run, confirm Provider family, protocol, endpoint, model, auth mode, timeout, output ceiling and retry policy. Evaluation artifacts contain only normalized evidence IDs, claims, dimensions, aggregate token/latency diagnostics and failure state. They exclude the Key, Authorization, prompt, raw response and reasoning. Estimated monetary cost remains `UNAVAILABLE` unless a dated reliable price source is explicitly configured.

After using a temporary or shared real-model key, revoke or rotate it according to the Provider's policy. ProjectFlow cannot determine whether an external key was exposed outside this process.

## V3.7.3 runtime semantics

Provider configuration contains a per-request processing timeout. It is not a connection timeout and is not the overall project-analysis duration.

- Connection timeout is a separate application runtime value and stays short/bounded.
- Provider request timeout is passed unchanged to probe, direct Eval and production Model Gateway requests.
- Overall analysis deadline belongs to the persisted analysis job and supports AUTO/FINITE/UNLIMITED.
- UNLIMITED never disables connection/request timeout, bounded retry, cancellation or heartbeat.

Official OpenAI/Anthropic SDK adapters set connect/read/write/request independently and disable SDK retries. Model Gateway remains the single owner of retry/recovery diagnostics. Protocol compatibility does not establish ProjectFlow semantic quality; the unchanged real quality gate and production-chain acceptance decide qualification.

Transport retry diagnostics aggregate wall-clock latency from the first attempt through the final attempt, including the bounded backoff. A later successful attempt must not hide time spent in the failed request.

Reasoning-capable models may count hidden thinking and visible JSON against one output budget. Under `QUALITY_FIRST`, a reasoning-capable task may use the configured Provider ceiling from the first request. The ceiling is a loose user-controlled safety bound, not a consumption target; elapsed time, Token usage, request count and cost remain diagnostics and never trigger automatic quality reduction.

Reasoning control is an explicit capability override, not a model-name guess. When OpenAI Responses or Chat Completions support is probed and marked supported, ProjectFlow sends the explicitly configured reasoning effort for connection, semantic and recovery requests. It never switches to low effort to save time or Token. Unsupported/automatic profiles omit the field rather than inventing a private parameter. Optional real-eval injection uses `PROJECTFLOW_REAL_MODEL_SUPPORTS_REASONING=true` and `PROJECTFLOW_REAL_MODEL_SUPPORTS_REASONING_CONTROL=true` only after a successful Provider probe; explicit JSON Mode uses `PROJECTFLOW_REAL_MODEL_SUPPORTS_JSON_MODE=true` only when supported.

For CI or one-process local acceptance, inject `PROJECTFLOW_REAL_MODEL_API_KEY` through the environment. Never put a real value in `.env`, Maven arguments, docs, reports or Git. GLM OpenAI Responses acceptance uses the Ark coding v3 base URL and model `glm-5.2`; the value of the key remains external.

## V3.7.5 two-model acceptance profile

V3.7.5 validates one provider-neutral Strong Fact contract through two official/compatible protocol adapters. The main profile is GLM `glm-5.2` through Ark Coding OpenAI Responses. The compatibility profile is DeepSeek `deepseek-v4-flash` through the official OpenAI Chat Completions endpoint with explicit JSON Mode. Both profiles declare reasoning support/control, use high effort and a 65,536 output ceiling. Model names and protocol details may differ; Evidence allow-lists, fact statuses, promotion guards, schema normalization, bounded repair and cancellation rules do not.

The second model is used for contract compatibility, core regression, Holdout and product-chain acceptance. ProjectFlow does not call both models for every user request and does not treat agreement as evidence. Provider probes establish only connectivity and structured protocol compatibility; dated evaluation reports establish bounded product acceptance.

## V3.8.5 RC3 real-model profile

The current RC3 workflow keeps GLM `glm-5.2` on Ark Coding OpenAI Responses and DeepSeek `deepseek-v4-flash` on the OpenCode Go OpenAI Chat Completions-compatible endpoint. Both acceptance profiles explicitly declare JSON Mode/reasoning control and use max reasoning. These are acceptance configuration values, not business-code model-name branches. Keys are supplied only through the protected `PROJECTFLOW_REAL_MODEL_API_KEY` and `PROJECTFLOW_DEEPSEEK_API_KEY` Repository Secrets.
