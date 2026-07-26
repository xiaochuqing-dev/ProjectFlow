# ADR: Final Synthesis Current-result Degradation

Status: Accepted

## Decision

Stage 1 and Stage 2 failures have different semantics.

- If Semantic Scout/Stage 1 fails, there is no new semantic result. The existing deterministic/previous-stale behavior remains.
- If Final Synthesis/Stage 2 fails after Stage 1 and tools succeeded, ProjectFlow persists a current result with `finalSynthesisStatus=FAILED_DEGRADED`.

The degraded result retains:

- Stage 1 root and semantic interpretation;
- validated tool evidence and merged Source Map;
- allowed evidence IDs and execution diagnostics;
- a current Dynamic Profile synthesized under the Stage 1 root;
- an explicit quality limitation;
- logical request count including the failed second attempt.

It does not retain the failed raw response or reasoning and does not promote tool evidence to ProjectFact.

## Covered failures

Parameterized tests cover Provider I/O failure, timeout, invalid response/schema class, cancellation and interruption during Final Synthesis. Capability failure already degrades independently and does not destroy the base Source Map.

## Consequence

Users see the newest supported understanding and its limitation instead of a misleading rollback to an older snapshot. The status remains trust information, not an accuracy metric.
