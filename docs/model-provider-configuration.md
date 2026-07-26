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
