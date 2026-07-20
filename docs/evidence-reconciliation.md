# Evidence Reconciliation Contract

## Truth and reachability

ProjectFact is the only factual source. A fact states that evidence-backed work happened at a historical time; later Git reachability is a different property. Timeline organizes facts by occurrence time, Capability/Evolution derive long-lived meaning, and Gateway/Hermes/Obsidian only consume those layers.

The next-stage reconciliation result must use one of four explicit states:

- CURRENTLY_REACHABLE: the recorded commit/reference remains reachable in the selected evidence boundary.
- REVERTED_OR_NEGATED: the historical change occurred but later evidence explicitly reversed its effect.
- UNREACHABLE_AFTER_REWRITE: the reference disappeared after reset/rebase/force-push or branch rewrite, without proof that the historical event was false.
- UNKNOWN: evidence is incomplete, unavailable or unsafe to reconcile.

These states annotate evidence currentness; none authorizes deleting ProjectFact or rewriting recorded Evolution history.

## Invariants

- Evidence identity uses project, batch/segment, commits, Agent-result references and bounded evidence references. Title similarity never merges facts.
- A rewrite or unknown boundary cannot silently advance incremental/history cursors.
- Conflicting or incomplete evidence enters attention without blocking unrelated valid facts.
- Capability “currently proven” status must consider reachability and negation, while maturity/history remain explainable.
- Every capability-map input fact remains explicitly classified; unknown/cross-project IDs are rejected.
- Reconciliation runs outside fact transactions and never calls a model from GET, Gateway, Hermes or Obsidian paths.
- Manifest/projection state is never used to repair source facts.

## Read contract

Search returns bounded lexical candidates with truth labels and stable trace links. Trace returns commit IDs, safe relative file/Agent references, bounded evidence references and related capability IDs. It removes absolute/traversal paths and never returns diff, fingerprint, prompt, response, reasoning, Key or Authorization. A future reachability field should be added to this stable read boundary rather than exposing Git implementation details to consumers.

## V3.4.6 responsibilities

Automatic Memory Maintenance may add a Project Observer, scheduled cheap check, stable evidence-decision use case, bounded automatic analysis, resumable history bootstrap and controlled memory/projection update. The Observer must invoke the existing Analyze boundary, never protocol adapters or repositories directly. It must debounce unstable worktrees, keep model calls bounded, preserve cursor safety and treat rewrite/revert as reconciliation rather than destructive cleanup.

V3.4.5 implements no scheduler, watcher, background Git poll, automatic model call, tray or Desktop GUI. Its deliverable is the boundary and invariant set above.
