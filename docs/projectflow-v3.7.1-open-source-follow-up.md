# ProjectFlow V3.7.1 Open-source Follow-up

Date: 2026-07-26

## Classification

| Project | Mature pattern reviewed | Decision | ProjectFlow use |
| --- | --- | --- | --- |
| Aider RepoMap | budgeted selection, PageRank, reusable caches | PATTERN_REUSE | complete-JSON category budgets and bounded evidence selection; no source copied |
| GitNexus | graph/RAG-style repository exploration | REJECT | current custom license requires commercial licensing; no code/runtime reused |
| CodeBoarding | repository explanation with language-server setup | REFERENCE_ONLY | product flow reference; runtime downloads/build assumptions are outside the trust boundary |
| RepoAgent | repository documentation agents | REFERENCE_ONLY | documentation pipeline reference; no agent runtime or orchestration embedded |
| deepwiki-rs | local repository knowledge generation | REFERENCE_ONLY | lightweight product reference; no generic RAG or new runtime adopted |
| Gitleaks | rule, keyword and entropy layering | PATTERN_REUSE | outbound trust-boundary redaction |
| Yelp detect-secrets | plugin-oriented detectors and entropy checks | PATTERN_REUSE | testable detector separation principle |
| TruffleHog | verified detector concept | REFERENCE_ONLY | no live verification or network side effect |
| Sourcegraph SCIP protocol | official Symbol protocol | DIRECT_REUSE | existing official protobuf dependency remains the only Symbol wire format |
| scip-java | Java compiler/build-owned indexing | REFERENCE_ONLY | producer is not automatically invoked |
| scip-typescript | TypeScript/JavaScript indexing | REFERENCE_ONLY | Node/runtime/dependency side effects require a future opt-in PoC |
| scip-python / pyright-scip | Python indexing through Pyright | REFERENCE_ONLY | environment/runtime assumptions require a future opt-in PoC |
| PyDriller | bounded Git traversal filters | PATTERN_REUSE | fixed-count metadata history execution |
| CodeScene | behavioral hotspots and history quality | PATTERN_REUSE | history confidence is multidimensional, not commit-volume maturity |
| MSR research/tooling | change coupling, churn and defect-history evidence | REFERENCE_ONLY | informs future evolution windows; no new mining framework in V3.7.1 |

No new item required ADAPTER_INTEGRATION because ProjectFlow's existing `ProjectStructureIndexer`, Model Gateway and fixed local command boundaries already cover the accepted integration points. Adding another adapter solely to fill a category would create redundant code.

Primary references:

- Aider RepoMap: https://github.com/paul-gauthier/aider/blob/main/aider/repomap.py
- GitNexus: https://github.com/nxpatterns/gitnexus
- CodeBoarding: https://github.com/CodeBoarding/CodeBoarding
- RepoAgent: https://github.com/OpenBMB/RepoAgent
- deepwiki-rs: https://github.com/sopaco/deepwiki-rs
- Gitleaks: https://github.com/gitleaks/gitleaks
- detect-secrets: https://github.com/Yelp/detect-secrets
- TruffleHog: https://github.com/trufflesecurity/trufflehog
- SCIP: https://github.com/sourcegraph/scip
- PyDriller: https://pydriller.readthedocs.io/en/latest/
- CodeScene hotspots: https://codescene.io/docs/guides/technical/hotspots.html

## Not adopted

- GitNexus runtime or code: current licensing is not suitable for unqualified reuse.
- A generic agent/workflow framework: the current six capability providers fit existing Spring boundaries.
- Repository-wide security scanning: dedicated projects are more mature; ProjectFlow only protects its model/persistence boundary.
- Automatic SCIP install/build: unsafe without an explicit sandboxed user-controlled workflow.

## Upgrade watchlist

Review SCIP schema and official producer releases before a future PoC. Keep secret-detector patterns covered by tests rather than silently importing community rule sets. Revisit persistent/distributed caches only if desktop multi-process profiling proves the process-local cache insufficient. Revisit a dedicated tokenizer only when Model Gateway diagnostics show character budgets materially misallocate provider context.
