# ADR: Honest Dimensional Historical Coverage

Status: Accepted

## Decision

Historical Coverage is a derived weighted score with visible components:

| Dimension | Weight |
| --- | ---: |
| Git metadata | 0.25 |
| ProjectFact-linked commits | 0.35 |
| Tag anchors | 0.10 |
| historical documents | 0.08 |
| Agent-result evidence | 0.07 |
| structural snapshot | 0.10 |
| optional remote collaboration | 0.05 |

Git history is sampled with a fixed maximum and reports truncation. Per-period confidence combines bounded Git presence, Fact linkage and Tag/document/Agent anchors. Gaps and limitations remain explicit.

The regression counterexample is mandatory: 1000 Git commits with 0 Fact, 0 Tag, 0 historical document and 0 Agent evidence produces only 0.25 overall coverage and low per-period confidence. Commit volume cannot manufacture maturity.

## Boundaries

Historical Coverage does not create Facts, infer missing stages or rewrite Timeline, Capability or Evolution. No Git means current-state-only. Short history remains short. Long history is sampled and evolution still uses bounded milestone windows.

## References

PyDriller documents bounded commit traversal filters, while CodeScene's hotspot model shows why history value comes from change behavior rather than raw volume:

https://pydriller.readthedocs.io/en/latest/

https://codescene.io/docs/guides/technical/hotspots.html
