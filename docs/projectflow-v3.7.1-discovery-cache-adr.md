# ADR: Signature-keyed Discovery Caches

Status: Accepted

## Decision

Repository Intake keeps an in-process cache per normalized project root. A file inspection is reused only when size, modification time, source/manifest/sensitive classification and scanner limits match. Removed paths are evicted. Unchanged inspection preserves binary, bounded line estimate, digest and bounded manifest text; changed files are reopened.

Evidence Discovery keeps a separate root/path sample cache keyed by the current inventory signature. Sensitive, binary and generated files never contribute samples. Both caches have a small root cap and are fully rebuildable.

For a cold large-repository fallback without scc, only 1,500 ordinary source files are content-sampled by default; remaining source files keep complete metadata inventory and a clearly labelled LOC estimate. The limit is configurable. A changed file is still reopened because unchanged cache hits do not consume the changed-file content budget. When scc is installed, its mature language/LOC result remains authoritative.

Metrics expose opened files, cache hits, bytes read and sample-cache hits. They diagnose cold, warm and changed-small-set behavior without turning cache data into a fact source.

## Rejected

- Persistent content cache: adds migration, encryption and invalidation obligations without current value.
- Timestamp-only content identity: the key also includes size and relevant scanner configuration.
- Reusing stale whole snapshots after a changed fingerprint: only unchanged file inspections/samples are reused; the derived snapshot is rebuilt.
