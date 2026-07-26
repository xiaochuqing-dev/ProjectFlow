# ADR: V3.7.1 Adaptive Execution

Status: Accepted

## Decision

Use one explicit-refresh chain:

Discover → Scout → Plan → Execute → Validate → Synthesize

Scout returns evidence IDs, semantic assessments and capability names. `AnalysisToolRegistry` filters unknown or unavailable capabilities. `AnalysisExecutionCoordinator` reuses FILESYSTEM and SCIP results, then calls registered `AnalysisCapabilityProvider` implementations. Providers receive a normalized project root, allow-listed evidence IDs and fixed budgets. They cannot accept model-authored commands.

The local provider implements DOC_READER, MANIFEST, AGENT_RESULT, GIT_HISTORY, GIT_TAG and WORKTREE. File reads are relative-path contained, sensitive-path denied, text/binary checked, redacted and item/character bounded. Git commands are fixed metadata-only arrays with timeout and cancellation; patches are never read.

Every Tool Result must have a `tool:` ID and cite an existing allowed evidence ID. Unknown references are dropped. Failures become bounded diagnostics and deterministic fallback, not fabricated evidence.

## Model-call policy

- Empty, blank, unchanged and no-model paths: 0 calls.
- Semantic Scout with no new high-value executed evidence: 1 call.
- Scout plus executed evidence that materially changes synthesis: 2 calls maximum.

Both calls use the existing `PROJECT_UNDERSTANDING_SNAPSHOT` ModelTaskType and `ModelGatewayService`; no Provider SDK or HTTP client is introduced in business services.

## Alternatives rejected

- Model-generated shell commands: violates the trust boundary.
- A general agent runtime or workflow engine: unnecessary for six bounded capabilities.
- One mandatory second call: wastes budget when execution adds no evidence.
- Direct writes to ProjectFact/Timeline/Capability: execution output is replaceable understanding, not factual truth.
