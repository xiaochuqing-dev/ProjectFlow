# V3.9 Independent Semantic Review

`independent-semantic-review-package.json` is the bounded, blind input package for the original twelve `V39-HUMAN-*` continuity scenarios. It contains only:

- the prior bounded history;
- the new delta;
- a candidate continuation;
- relevant Evidence summaries and Story/Thread/Chapter references;
- explicit conflicts and unknowns.

The package deliberately contains no scenario title, frozen answer, implementation status, test outcome, or another model's judgement. The evaluator validates the exact twelve IDs, field set, size and sensitive-content boundary before any provider call. It then sends the twelve bounded items in one logical review call through the existing `ModelGatewayService` and provider protocol adapter.

The evaluator keeps only these bounded judgement fields:

| Field | Allowed values |
| --- | --- |
| `attachmentSemanticallySupported` | `yes`, `no`, `uncertain` |
| `shouldRemainIndependent` | `yes`, `no`, `uncertain` |
| `oldHistoryUnexpectedlyChanged` | `yes`, `no` |
| `truthfulnessConcern` | `yes`, `no` |
| `rationale` | redacted, single-line text, at most 500 characters |
| `confidence` | `high`, `medium`, `low` |

The artifact keeps one shared call diagnostic because the twelve judgements come from one bounded logical call. It records an explicit `COMPLETE` or `FAILED` status, success/failure counts, a maximum of four physical requests including the gateway's bounded retry/recovery paths, and bounded token ceilings. API keys, authorization material, prompts, raw model responses, reasoning text, request IDs, base URLs, and machine-local absolute paths are never written. A separate artifact gate rejects missing, incomplete, over-budget, malformed or unsafe output before the provider workflow can pass.

This review is acceptance evidence only. It cannot mutate ProjectFact, history, Story/Thread/Chapter identity, or projection state; it cannot promote a claim to Strong Fact. Any disagreement or uncertainty remains unresolved and is handed to deterministic gates or the human/Sol review boundary. The human worksheet remains unchanged with `status: NOT_REVIEWED`.
