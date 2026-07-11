# Model Analysis

ProjectFlow V3.3.6 separates transport success, visible content, finish reason, usage, reasoning-field metadata, truncation, JSON parsing, target-structure recognition, evidence binding, and persistence.

V3.3.7 adds task-level request, elapsed-time and token budgets above provider and transport behavior. Initial calls, transport retries and compact retries contribute to one persisted request total. Cancellation is checked before a new model call, after its response and before formal persistence. A restart at an unknown model-call state never automatically replays the request.

Empty content is classified as exhausted output when `finish_reason=length`, completion tokens approach the effective limit, or a Provider reasoning field exists. It enters one compact retry with an output limit of 2000 tokens. The compact retry does not add another transport retry, so one structured task sends at most three requests.

Reasoning text is never stored, logged, or returned. Diagnostics retain only whether a reasoning field exists and its character length. Usage is labelled `ACTUAL`, `ESTIMATED`, or `UNAVAILABLE`.

Project analysis, file analysis, development-segment analysis, capability interpretation, and capability-card analysis use the same gateway diagnostics. A model failure may produce a local fact result where the product permits it, but that result is never presented as a successful model analysis.
