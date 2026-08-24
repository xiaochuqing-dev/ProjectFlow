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

The artifact keeps one shared call diagnostic because the twelve judgements come from one bounded logical call. It records an explicit `COMPLETE` or `FAILED` status, success/failure counts, a maximum of four physical requests including the gateway's bounded transport and single semantic recovery paths, and bounded token ceilings. Quality-first reasoning models may use the configured 65,536-token per-request ceiling so reasoning cannot consume the whole 16,384-token allowance before visible JSON; this is a ceiling, not a consumption target. Aggregate completion and total ceilings remain 131,072 and 160,000 tokens. Canonical artifacts always store the documented lowercase strings. A provider boolean is normalized only to the semantically equivalent `yes` or `no` for the four judgement fields; `uncertain` remains an explicit string, and numbers, objects, arrays, unknown labels, and boolean confidence are rejected. API keys, authorization material, prompts, raw model responses, reasoning text, request IDs, base URLs, and machine-local absolute paths are never written. A separate artifact gate rejects missing, incomplete, over-budget, malformed or unsafe output before the provider workflow can pass.

This review is acceptance evidence only. It cannot mutate ProjectFact, history, Story/Thread/Chapter identity, or projection state; it cannot promote a claim to Strong Fact. Any disagreement or uncertainty remains unresolved and is handed to deterministic gates or the human/Sol review boundary. The human worksheet remains unchanged with `status: NOT_REVIEWED`.
