# ADR: Cross-model Strong-fact Generalization Closure

Status: Accepted for V3.7.5

## Context

V3.7.4 proved transport and schema completion for both models but the frozen DeepSeek Holdout missed a complete small Evidence set, required Agent/Git deep reads and an Agent-versus-CI conflict. GLM met semantic thresholds but had one Structured Output repair failure and explicit fallback. The failures were not caused by an invalid Evidence allow-list or a Ground Truth leak.

The saved observations showed four general weaknesses:

- The Prompt allowed a model to omit a source assessment when it believed the source was unimportant.
- Capability requests were present, but the model did not have to make an exact REQUEST/SKIP decision for every eligible capability.
- Optional view wording was too unconstrained, which reduced stable View recall.
- Large self-check and claim structures increased output length without adding factual authority.

## Decision

1. Prompt contract v3 uses an `evidenceLedger` with `COMPLETE_SMALL_SET` or `BOUNDED_DIVERSE` coverage mode.
2. For at most twelve source Evidence items, every source must have exactly one assessment.
3. Every eligible capability must have exactly one `REQUEST` or `SKIP` decision. REQUEST decisions must carry the information gap, expected Evidence value, target Evidence IDs and why existing Evidence is insufficient.
4. `SemanticContractDiagnostics` validates the small-set ledger, duplicate/missing assessments, exact Capability decisions and view-to-tool dependencies.
5. A contract gap is `FAILED_DEGRADED`. It is added to persisted limitations and unknowns instead of being silently treated as semantic success.
6. Scout v12 and Final v7 use the same seven fact states and a separate semantic role. Model `VERIFIED` output is normalized without granting authority.
7. Scout output is capped at 16 claims, Final at 12 claims and three claims per section. Final records only meaningful changes from Stage 1.
8. Production and direct Eval continue to use the same `ProjectUnderstandingPromptBuilder`. No model name, case ID, repository name, path or expected answer branch is allowed.
9. The V3.7.4 Holdout Ground Truth and thresholds remain unchanged.

## Consequences

- A weak model may still choose a poor interpretation, but it cannot silently ignore a complete small Evidence set or Capability decision without an observable degraded status.
- Provider-specific JSON or reasoning recovery remains in the Model Gateway adapter; product semantics do not fork.
- Output is smaller and easier to repair, while unknowns and conflicts remain explicit.
- Evaluation and product behavior stay comparable because the fix is in the shared builder and parser path.
- A failed formal Holdout remains evidence. Any later run must use a new freeze and preserve the old artifact.

## Rejected alternatives

- DeepSeek-specific instructions: rejected because they do not generalize.
- Case, filename or answer-key hints: rejected as Ground Truth leakage.
- Lowering recall/conflict thresholds: rejected because it changes the Gate rather than the product.
- Deterministically inventing semantic importance or conflicts: rejected because engineering code does not own open-world meaning.
- Always making two model calls: rejected because Final Synthesis remains conditional on validated high-value Evidence.
