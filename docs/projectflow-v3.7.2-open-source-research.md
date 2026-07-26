# ProjectFlow V3.7.2 Open-source Research

Research date: 2026-07-26. No code was copied and no dependency was added.

## Evaluation patterns

- OpenAI Evals documents define → run → analyze → iterate and versioned datasets. The hosted Evals platform documentation also announces 2026 deprecation, so ProjectFlow adopts the workflow pattern rather than a hosted dependency. Source: https://developers.openai.com/api/docs/guides/evals
- OpenAI Structured Outputs recommends explicit schemas, clear field descriptions, evals for structure, and separate refusal/incomplete handling. ProjectFlow retains its registered schema and separate finish/failure semantics. Source: https://developers.openai.com/api/docs/guides/structured-outputs
- openai/evals (MIT), promptfoo (MIT), DeepEval (Apache-2.0), LangSmith evaluation concepts and Ragas (Apache-2.0) demonstrate dataset/experiment/repetition patterns. They are broader than this fixed Java task; direct integration was rejected. Sources: https://github.com/openai/evals, https://github.com/promptfoo/promptfoo, https://github.com/confident-ai/deepeval, https://docs.smith.langchain.com/evaluation, https://github.com/vibrantlabsai/ragas

## Observability and protocols

- OpenTelemetry GenAI conventions warn that prompt/output content is sensitive. Existing Model Gateway diagnostics already provide bounded metadata, so no telemetry dependency is added. Sources: https://opentelemetry.io/docs/specs/semconv/gen-ai/, https://github.com/open-telemetry/semantic-conventions
- MCP separates tools, resources and prompts and emphasizes explicit tool boundaries. ProjectFlow adopts the thin adapter distinction without becoming a general MCP runtime. Source: https://modelcontextprotocol.io/specification/
- GitHub REST versioning and VS Code extension activation/contribution points reinforce versioned adapters with host-owned lifecycle. Sources: https://docs.github.com/en/rest/about-the-rest-api/api-versions, https://code.visualstudio.com/api/references/activation-events

## Integration style

- CC Switch (MIT) centralizes provider configuration and uses adapters/atomic updates across tools. ProjectFlow adopts the thin-integration and single-source-of-truth style, but rejects Provider switching, usage dashboards, updater and tool-control business. Source: https://github.com/farion1231/cc-switch

## Secret tooling

- Gitleaks (MIT) and detect-secrets (Apache-2.0) provide mature repository scanning patterns. TruffleHog (AGPL-3.0) also performs secret verification that may call external services. ProjectFlow keeps only its bounded outbound redactor in this phase; embedding a general scanner or verification runtime is rejected for scope, side effects and licensing review. Sources: https://github.com/gitleaks/gitleaks, https://github.com/Yelp/detect-secrets, https://github.com/trufflesecurity/trufflehog

## Result

Adopted: versioned datasets/prompts, deterministic metrics, repeated observations, schema/failure separation, sanitized telemetry, thin versioned adapters, bounded redaction and atomic/host-owned integration principles.

Rejected: generic eval platform, model leaderboard, vector/RAG stack, new telemetry agent, general secret scanner, provider switcher, updater, agent runtime, parser, SCIP producer or external product replacement.
