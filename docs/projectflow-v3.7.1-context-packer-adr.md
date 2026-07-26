# ADR: Complete-JSON Budget-aware Context Packing

Status: Accepted

## Decision

`BudgetAwareContextPacker` converts each allow-listed context category to a redacted JSON tree, allocates minimum/maximum character budgets, fits arrays/objects/text inside each budget, then enforces the global ceiling and serializes once. The final string is parsed again to prove valid JSON.

Categories are project intake, manifests, documents, structure, Git, historical coverage, unknowns/conflicts and Tool Results. Diagnostics record total/section characters, selected/dropped item counts and truncation reasons.

This implementation deliberately prefers complete smaller JSON items over raw string slices. Full prompts, raw responses and reasoning are not persisted; only diagnostics join the snapshot.

## Reference

Aider RepoMap demonstrates a mature budget-first approach that selects repository context under a token budget and caches reusable structure. ProjectFlow borrows the budget-first principle, not source code or its runtime:

https://github.com/paul-gauthier/aider/blob/main/aider/repomap.py

## Rejected

- Serialize then substring: may create invalid JSON and opaque loss.
- One undifferentiated budget: lets a large source class crowd out rare evidence.
- A tokenizer dependency: character limits already match the existing Model Gateway boundary and avoid another runtime.
