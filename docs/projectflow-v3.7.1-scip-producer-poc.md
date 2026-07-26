# ProjectFlow V3.7.1 SCIP Producer PoC Decision

Date: 2026-07-26

Decision: production invocation remains deferred.

## Evaluated producers

| Ecosystem | Mature option | Observed requirement | Production decision |
| --- | --- | --- | --- |
| Java | Sourcegraph scip-java 0.12.3 | runs against a compatible JVM/build and derives semantic data through compiler/build integration | defer automatic invocation |
| TypeScript | `@sourcegraph/scip-typescript` | Node/npm runtime, dependency resolution and potentially substantial memory | defer automatic invocation |
| Python | `pyright-scip` / scip-python | Node-based Pyright tooling and project environment assumptions | defer automatic invocation |

References:

https://github.com/sourcegraph/scip

https://github.com/sourcegraph/scip-java

https://www.npmjs.com/package/@sourcegraph/scip-typescript

https://github.com/sourcegraph/scip-python

## Safety findings

An arbitrary bound directory may lack dependencies, contain build scripts with side effects, require network access, use an incompatible runtime or be too large for an implicit index build. Automatically downloading a producer or invoking Maven, Gradle, npm or Python environment setup would mutate or execute user-controlled project state outside the established evidence boundary.

ProjectFlow therefore keeps the V3.6 production contract:

- consume only an already present, path-contained, bounded and valid official `index.scip`;
- keep `ProjectStructureIndexer` as the stable SPI;
- report unavailable/stale/invalid/oversized provider diagnostics;
- fall back to manifest/filesystem structure without inventing Symbol relationships;
- never block current understanding.

## Preconditions for a future opt-in implementation

A separate phase must define explicit user consent, producer discovery without auto-install, sandbox/process isolation, network-off default, build-script policy, CPU/memory/time/output limits, cancellation, index provenance/revision, temporary-output cleanup, Windows/Linux parity and real Java/TypeScript/Python fixtures. Until those checks pass, the UI and reports must not claim built-in SCIP generation.
