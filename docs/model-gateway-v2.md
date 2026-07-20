# Model Gateway V2

## Why and what remains stable

V3.4.5 removes the assumption that every Provider speaks OpenAI Chat Completions. `ModelGatewayService` remains the stable business facade and preserves the V3.3.8 reliability core: registered task types, dynamic output budgets, model-aware temperature omission, bounded concurrency/cancellation, transport retry, truncation and empty-after-reasoning recovery, partial JSON parsing, one-shot Schema repair and safe diagnostics.

Provider brand and protocol are independent. The supported protocols are exactly `OPENAI_RESPONSES`, `OPENAI_CHAT_COMPLETIONS` and `ANTHROPIC_MESSAGES`; Gemini, Bedrock, Azure Entra, OAuth and automatic protocol fallback are outside V3.4.5.

## Canonical pipeline

Business task → task/capability policy → canonical transport request → selected protocol adapter → canonical transport response → shared validation/recovery → structured business response.

The canonical request carries Provider/model identity, system/user content, effective output budget, optional temperature, JSON/structured-output intent and timeout. Task type, target Schema, reasoning decision, metadata and cancellation remain in the Gateway orchestration context so adapters cannot copy business policy.

The canonical response carries visible content, Provider finish reason, normalized finish reason, canonical usage, request ID and bounded reasoning metadata. The surrounding Gateway diagnostics add Provider/model/protocol, latency, request/retry counts, task/Schema status and recovery outcome. Raw prompts, responses, reasoning and credentials are never persisted.

Normalized finish states are `COMPLETE`, `OUTPUT_LIMIT`, `CONTEXT_LIMIT`, `REFUSAL`, `CONTENT_FILTERED`, `TOOL_USE`, `INCOMPLETE`, `ERROR` and `UNKNOWN`. Chat `length`, Responses incomplete/max-output, and Anthropic `max_tokens` all enter the same truncation path. A second incomplete recovery response is an explicit failure. Refusal, filtering, tool use and error cannot become successful business JSON.

## Protocol adapters and official SDK evaluation

OpenAI Responses uses instructions/input, max output tokens, optional temperature, JSON format, output blocks, status/incomplete details and reasoning-aware usage. OpenAI Chat uses system/user messages, max tokens, optional temperature/JSON format, compatible reasoning fields, choices, finish reason and usage. Anthropic Messages uses top-level system, messages, max tokens, optional temperature, content/thinking/tool blocks, stop reason and usage.

Standard authentication and requests use the official OpenAI Java 4.43.0 and Anthropic Java 2.49.0 SDKs. OpenAI 4.44.0 was evaluated but was not available from Maven Central during implementation; 4.43.0 was the latest resolvable official release. Both clients set `maxRetries(0)`, leaving ProjectFlow as the only retry authority.

The official SDKs always inject their standard credential header. They cannot faithfully express relay modes that require a custom API-key header, query key or no authentication, because removing the SDK security header still leaves the credential middleware active. Only those three explicit modes use `CompatibleRelayTransport`, a narrow Java HTTP bridge for the same three fixed payload/response shapes. It has no retry, protocol switching, business prompt, Schema validation or recovery logic; it validates the selected endpoint, blocks redirects, applies only prevalidated headers and returns the same canonical response. Standard Bearer/Anthropic/default flows remain on official SDKs.

## Provider, URL and relay rules

OpenAI official defaults to Responses; OpenAI-compatible/DeepSeek/custom legacy Providers default conservatively to Chat; Anthropic defaults to Messages. An explicit user protocol is never silently changed after failure.

Base URLs receive exactly one protocol suffix. Full endpoint overrides must end in `/responses`, `/chat/completions` or `/v1/messages`. Query parameters are permitted only on a full endpoint used by the narrow compatible-relay path; SDK paths reject them. HTTPS is required remotely; localhost/127.0.0.1 HTTP is allowed for development. User-info, unsafe private/metadata literals and mismatched paths are rejected.

Authentication supports protocol default, Bearer, Anthropic standard, bounded API-key header, explicit query key and none. Extra headers cannot replace Authorization/API-key/system/proxy/forwarding headers and cannot contain CR/LF. API values do not appear in API responses, diagnostics, reports or Agent results.

## Capability, structured output and compatibility probe

The capability registry combines persisted user/probe overrides with preset/protocol defaults and uses model-name heuristics only as a conservative fallback. Native structured output is preferred, then JSON mode, then prompt-constrained JSON with ProjectFlow validation/recovery. Unsupported temperature and JSON flags are omitted, not retried with another protocol.

Provider testing performs a bounded `{\"ok\":true}` transport/protocol/Schema task and a second minimal real ProjectFlow task with `summary` and `architecture`. The profile separately reports connection, auth, protocol, structured-output/JSON/temperature/reasoning/usage status, adapter output-limit coverage, ProjectFlow compatibility, warnings and actual Gateway request count. An HTTP 200 alone is never FULL compatibility.

## Retry and diagnostics ownership

- A normal task allows at most two ProjectFlow transport attempts and retries only transient I/O or 429/5xx.
- A recovery call allows one transport attempt, preventing multiplied cost.
- Schema mismatch receives one targeted re-encoding call.
- Truncation and empty-after-reasoning keep separate recovery types and dynamic budgets.
- 401/403, invalid configuration, cancellation and persistence failures are not blindly retried.
- Diagnostics include protocol, raw/normalized finish, effective parameters, latency, usage source, request/transport counts, recovery and Schema/failure state. They omit prompt, response, reasoning and secrets.

The deterministic matrix runs every active `ModelTaskType` through all three protocol endpoints and separately proves output-limit, second-incomplete failure, Schema repair, reasoning recovery and transient retry contracts. Real-Provider tests remain optional and bounded by available keys.
