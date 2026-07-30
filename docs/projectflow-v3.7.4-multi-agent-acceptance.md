# ProjectFlow V3.7.4 Multi-agent Acceptance

## Product boundary

Authenticated Agents can list every loaded project they own, search bounded history across those projects, read a same-project Evidence item, retrieve status-partitioned knowledge and obtain a versioned provenance Context Package. Project and model switches read the same persisted ProjectFlow state.

Agents cannot write ProjectFact directly through this surface. The candidate endpoint accepts assertions, Evidence links, corrections, conflict reports and review requests only. `OBSERVED` and `VERIFIED` are rejected before persistence; unknown or cross-project Evidence IDs are rejected.

## Verified results

| Check | Result |
| --- | --- |
| List authorized projects | PASS |
| Read owned project history | PASS |
| Bounded cross-project search | PASS |
| Cross-project Evidence isolation | PASS |
| Unauthorized project access | PASS, no data returned |
| Candidate write and validation status | PASS |
| Direct strong-fact candidate write | PASS, rejected |
| Context Package provenance/revision/budget | PASS |
| Read audit without private query/Evidence text | PASS |
| Agent/model switch continuity over persisted state | PASS |

Hermes subprocess acceptance passed 6 tests and discovered 13 read-only tools. Project context resources were listed/read with provenance; six concurrent reads completed with a measured maximum concurrency of six. Startup/discovery was 161.6 ms, concurrent reads 298.4 ms and a paged tool call 150.9 ms. Remote backend defaults, timeout, backend errors and credential leakage remained guarded.

Obsidian remains an unchanged projection consumer. Its 18-test suite passed, including 5,000 facts, 36 months, 100 capabilities, 1,000 evolutions, atomic writes, no-op zero writes and traversal/symlink protection. First sync completed in 450.1 ms with 177 writes; the unchanged rerun performed zero writes.
