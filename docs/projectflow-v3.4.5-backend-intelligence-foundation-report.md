# ProjectFlow V3.4.5 Backend Intelligence Foundation Report

## 1. Main intent and product value

PASSED. V3.4.5 organizes backend intelligence around ProjectFlow’s non-GitHub value: Git/GitHub remain objective evidence; models interpret bounded evidence; ProjectFact stores factual memory; Timeline and Capability/Evolution are derived views; Gateway exposes reusable read semantics; Hermes and Obsidian remain consumers. No observer, scheduler, watcher, Desktop GUI, Gemini or frontend redesign was added.

## 2. Value Audit

PASSED. The audit used a hash-verified H2 copy and reviewed all 68 facts, 18 active capabilities, 22 evolutions, both available months plus lifecycle, five Gateway/Hermes question shapes and the CORE Obsidian projection. Strong recent facts and traceable derived memory were KEEP_CORE. Generic migrated facts, broad capabilities and weak evolution deltas were IMPROVE. Sediment/card/old-link surfaces remain LEGACY_COMPAT. No stored class was proven DEAD_SAFE_TO_REMOVE, so no destructive deletion occurred. Full evidence is in `docs/projectflow-v3.4.5-value-audit.md`.

## 3. Backend business map and consolidation

PASSED. `docs/backend-business-architecture.md` records Evidence, Analysis, Factual Memory, Temporal, Capability, Memory Read, External Adapter, Model Infrastructure and Legacy boundaries with inputs, outputs and prohibited responsibilities.

`ProjectMemoryGatewayService` remains the compatible facade. Bounded lexical retrieval moved to `ProjectMemorySearchService`; fact trace assembly/redaction moved to `ProjectEvidenceTraceService`. About 300 lines and three evidence repositories left the facade. REST/MCP/Obsidian contracts were not renamed, and no new God service or package-wide rewrite was introduced.

Legacy matrix result: ProjectChange/SedimentAction/ProjectSediment and ProjectCapabilityCard are COMPAT_READ_ONLY; DevelopmentSegment and missing Provider protocol fields are MIGRATION_ONLY where applicable; current Facts/Timeline/Capability/Gateway are ACTIVE_CORE. Old paths never re-enter new scanning.

## 4. Model Gateway V2

PASSED. `ModelGatewayService` still owns task registration, dynamic max-token policy, temperature decision, concurrency, cancellation, bounded transport retry, JSON parsing, Schema repair, truncation/empty-after-reasoning recovery and diagnostics. Protocol adapters only translate canonical transport request/response.

Supported protocols are exactly OpenAI Responses, OpenAI Chat Completions and Anthropic Messages. OpenAI official defaults to Responses; DeepSeek/OpenAI-compatible/custom legacy Providers map conservatively to Chat; Anthropic maps to Messages. Protocol is persisted independently from Provider brand and participates in duplicate identity.

Canonical response normalization covers visible content, request ID, actual/unavailable usage, reasoning metadata and COMPLETE/OUTPUT_LIMIT/CONTEXT_LIMIT/REFUSAL/CONTENT_FILTERED/TOOL_USE/INCOMPLETE/ERROR/UNKNOWN. Gateway diagnostics add model, Provider, protocol, latency, request counts, raw/normalized finish, effective parameters, Schema and recovery state. A second incomplete recovery response now fails explicitly.

## 5. Official SDK evaluation and adapters

PASSED. Standard requests use `com.openai:openai-java:4.43.0` and `com.anthropic:anthropic-java:2.49.0`; LangChain4j/Spring AI was not introduced. OpenAI 4.44.0 was checked but was not resolvable from Maven Central during implementation, so 4.43.0 is the latest official release actually available to this build. Both SDK clients use `maxRetries(0)`.

OpenAI Responses handles instructions/input, output budget, optional temperature/JSON format, output/refusal blocks, status/incomplete reason, usage and reasoning tokens. Chat handles system/user messages, output budget, optional JSON/temperature, compatible reasoning fields, choices, finish reason and usage. Anthropic handles top-level system, user messages, output budget, optional temperature, text/thinking/tool blocks, stop reason and usage.

Official SDK credential middleware cannot express a custom API-key header, query key or no-auth relay without also injecting its default credential header. Those three explicit modes therefore use a narrow `CompatibleRelayTransport`. It supports only the three fixed protocols, has no retry/protocol switching/business prompt/Schema logic, rejects redirects and returns the same canonical response. Standard OpenAI/Anthropic/Bearer flows remain on official SDKs.

## 6. Provider configuration, URL, auth and migration

PASSED. Provider state now includes protocol, optional endpoint override, auth mode/name, bounded safe headers, timeout, capability overrides and last safe probe profile. DTOs never return API keys or header values. Settings received only the minimal compatible fields; navigation and visual IA were unchanged.

Base URL suffixing, standard full endpoint override, relay prefixes, localhost HTTP and remote HTTPS are supported. Mismatched protocol paths, remote unsafe literals, URL credentials and base query/fragment are rejected. Query parameters are limited to a matching full endpoint on the compatible-relay path. Auth supports protocol default, Bearer, Anthropic standard, bounded API-key header, explicit query key and none. System/auth/proxy/forwarding headers and CR/LF values are blocked.

PASSED H2 migration. Nullable bridge columns plus idempotent startup backfill preserve Provider ID, key, model, token/temperature settings, purpose tags and default selection. DEEPSEEK, OPENAI_COMPATIBLE and CUSTOM migration tests all map to Chat and a second migration is a no-op. Protocol is part of duplicate identity; default deletion and single-default invariants remain protected.

## 7. Capability registry and compatibility probe

PASSED. Persisted capability overrides take precedence over protocol/preset defaults; model-name heuristics are conservative fallback only and never choose protocol. Structured output falls back in order from native support to JSON mode to prompt constraint plus ProjectFlow Schema/recovery.

The Provider compatibility action performs a `{\"ok\":true}` transport/protocol/Schema task and a second minimal real ProjectFlow `summary/architecture` task. It separately reports connection, auth, protocol, structured-output/JSON/temperature/reasoning/usage, adapter output-limit coverage, ProjectFlow compatibility, warnings and actual Gateway request count. Only the safe compact profile and timestamp are persisted.

## 8. Protocol, recovery and request-count evidence

PASSED deterministic fake-provider E2E. Every one of the 12 registered `ModelTaskType` values ran through all three official SDK protocol paths: 36 task/protocol combinations with valid business Schema. The focused matrix generated 74 bounded local HTTP attempts in total, including three intentional 429 responses; each successful fixture response declares 10 input and 20 output tokens. These are deterministic, non-billable fixture counts, not real Provider quality evidence.

PASSED across all three protocols: complete parse, output-limit normalization, first-incomplete then successful truncation recovery, second-incomplete explicit failure, Schema mismatch repair, reasoning-only empty-output recovery and transient 429 retry. Custom header, query-key, no-auth and Anthropic Bearer assertions prove that extra default credentials are not leaked. SDK retries are disabled, so observed request counts match ProjectFlow retry/recovery decisions.

Existing V3.3.8 regressions for dynamic token budget, reasoning temperature omission, partial JSON, timeout, cancellation, diagnostics, failure classification and no-double-retry remain in the full backend suite.

## 9. Database and real Provider acceptance

PASSED current H2 and old-file upgrade. Before audit, source and safe copy were 6,270,976 bytes with SHA-256 `489D059BB2B9D7BCB8133BF47E5FE593F9A39994B35A8C502FB2E43829436E0D`. A separate V3.4.5 upgrade copy retained 68 facts, 18 capabilities, 22 evolutions, eight timelines, one configured key and one default Provider while backfilling protocol/auth. The mandatory final launcher smoke later upgraded the active local database idempotently after that backup; its post-startup hash became `F52359C2CA707405078DA6A9D188A5F01FAC887B30AD2D378AD83CB3C6EB6053` with the same 68 facts and preserved Provider state.

BLOCKED locally for PostgreSQL Testcontainers. `mvn -q -Ppostgres-it verify` reached the failsafe layer but Docker discovery reported no valid Docker environment; the result was one initializer error and one skip, not a claimed database pass. The existing Docker-backed CI gate remains authoritative.

SKIPPED real DeepSeek/OpenAI/Anthropic calls because no safe `DEEPSEEK_API_KEY` or equivalent protocol key was present. No mock is described as real-model acceptance. Deterministic protocol E2E is PASSED; real language quality and live billing/request counts remain SKIPPED.

## 10. Local quality gates

- PASSED backend/H2 full suite: 309 tests in 49 reports, zero failures, errors or skips.
- PASSED frontend contracts: 44/44; TypeScript/lint PASSED; Next.js production build PASSED with 22 generated static-page units and dynamic routes compiled.
- PASSED Playwright: 7/7 real frontend/backend flows.
- PASSED Hermes MCP: 5/5; startup/discovery 141.6 ms, six concurrent reads 258.3 ms, tool call 131.5 ms.
- PASSED Obsidian: 18/18; 5,000 facts/36 months/100 capabilities/1,000 evolutions produced 176 files in 572.3 ms, then zero no-op writes.
- PASSED protocol/SDK/Provider migration/security focused suites as part of the 309-test backend run.
- PASSED production package and embedded startup before release evidence finalization; `logs/last-embedded-build.json` records V3.4.5, current-tree build and health success.
- PASSED committed-secret scan and `git diff --check` before commit. No real key, raw model response, prompt or reasoning was added.
- SKIPPED npm automatic vulnerability remediation: install reported two moderate and two high dependency advisories; unrelated forced upgrades were not applied in this release.

## 11. Security and system safety

PASSED. Work stayed inside the repository, isolated temporary H2 copies and localhost test processes. No registry, hosts, system path, global Git/Maven/npm, proxy, firewall, service, scheduler, browser/IDE or OS configuration was changed. No new watcher or arbitrary Provider header/script mechanism exists.

API keys remain database-backed for local compatibility. They are absent from response DTOs, logs, diagnostics, docs and Agent result, but at-rest storage must move behind an OS-backed SecretStore before broader Desktop productization.

## 12. CI, release and V3.4.6 readiness

PASSED GitHub Actions run `29760842081` for implementation commit `175166225efb48b83fc0dd0a436b7d5d50b46c71`. Backend/H2, PostgreSQL Testcontainers, frontend quality, browser E2E, Hermes, Obsidian and sensitive-content jobs all completed successfully. Optional real DeepSeek was SKIPPED because no CI secret was supplied. This resolves the local Docker BLOCKED status with a successful Docker-backed PostgreSQL gate.

V3.4.6 readiness is PASSED at the boundary level. `docs/evidence-reconciliation.md` defines CURRENTLY_REACHABLE, REVERTED_OR_NEGATED, UNREACHABLE_AFTER_REWRITE and UNKNOWN without deleting historical facts or silently advancing cursors. The next stage is Automatic Memory Maintenance through Project Observer → bounded cheap check → stable evidence decision → Analyze use case → history/memory update → controlled Obsidian/Hermes availability. No V3.4.6 runtime was implemented here.

Final implementation commit: `175166225efb48b83fc0dd0a436b7d5d50b46c71`

CI: PASSED, `https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/29760842081`

Report: `docs/projectflow-v3.4.5-backend-intelligence-foundation-report.md`
