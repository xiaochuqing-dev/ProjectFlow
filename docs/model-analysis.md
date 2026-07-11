# Model Analysis

ProjectFlow V3.3.8 registers six real model entrypoints: Provider connection test, development-segment merge, whole-project analysis, file analysis, capability interpretation, and project-capability analysis. Production business services call `ModelGatewayService` with a `ModelTaskType`; direct model HTTP calls outside the gateway are not allowed.

The request policy combines Provider type, model name, capability profile, task type, input size, expected output shape, reasoning behavior, Provider output ceiling, and configured Temperature. Diagnostics retain configured, recommended, effective, sent/omitted, and decision reason separately. Unsupported Temperature or JSON mode fields are omitted.

Complex tasks no longer share a fixed 4000-token output limit. Recovery no longer uses a fixed 2000-token compact limit. Initial and recovery budgets are computed separately and remain bounded by Provider capability and the persisted task request/token/duration budgets.

The structured-output pipeline is:

Provider response -> content/reasoning/finish reason/usage extraction -> truncation classification -> balanced JSON candidate scan -> light syntax repair -> target-aware candidate and nested collection selection -> alias normalization -> Schema match -> bounded targeted recovery -> business validation -> persistence.

Retry types are `TRANSPORT_RETRY`, `TRUNCATION_RETRY`, `EMPTY_AFTER_REASONING_RETRY`, and `SCHEMA_REPAIR_RETRY`. Authentication/configuration errors are not retried. A recovery request does not receive another transport retry.

Reasoning text is never stored, logged, or returned. Diagnostics retain only presence, length, and whether reasoning likely exhausted the shared output budget. API keys, Authorization, full prompts and raw Provider responses are also excluded.

Legal JSON with the wrong business shape is not classified as a syntax failure. It receives one Schema repair request that re-encodes existing semantics into the minimal target Schema without re-running the analysis. Complete entries from a truncated array may be retained as a partial result.
