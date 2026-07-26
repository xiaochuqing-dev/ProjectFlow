# ADR: Evidence Diversity under a Global Cap

Status: Accepted

## Decision

Evidence Discovery retains the global candidate and Scout limits but selects with deterministic diversity constraints:

- guarantee scarce high-value categories such as manifest/build, CI, tests, migration, infrastructure, ADR/changelog and Agent result when present;
- cap repeated category and module contributions;
- compress duplicate normalized candidates;
- fill remaining capacity by deterministic importance and path order;
- expose selected-by-category, quota drops, duplicate compression, category coverage, current/history balance and sample-cache hits.

Filename and directory signals remain candidates, not final semantics. Generated, vendor, binary and sensitive content does not enter samples.

## Rationale

A global top-N alone overrepresents large documentation or source clusters. Category/module diversity protects small but decisive operational evidence while keeping selection deterministic and bounded. The Scout may reinterpret semantic roles but cannot add unknown evidence IDs or change selection membership.

## Validation target

Synthetic tests create many documents plus isolated CI, test, migration and infrastructure evidence and require all scarce categories to survive the cap. Real-repository acceptance records selected category count and coverage.
