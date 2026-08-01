# ProjectFlow V3.7.5 Existing Coverage Matrix

This matrix prevents repeated real-model spending. `Rerun` means the V3.7.5 Prompt, Evidence ordering, Promotion or Agent Context changes materially affect the row.

| Capability | Status | Coverage type | Source artifact | Last verified revision | Rerun | Reason |
| --- | --- | --- | --- | --- | --- | --- |
| No-Git | Covered | deterministic + product E2E | RepositoryIntakeServiceTest; V3.7.4 E2E | V3.7.4 | No full real rerun | Intake unchanged |
| Git | Covered | deterministic + product E2E | Git/history tests; V3.7.4 E2E | V3.7.4 | Minimal | Prompt contract changed |
| README | Covered | deterministic + real model | declaration/promotion tests; Calibration | V3.7.5 worktree | Minimal | Constitution status changed |
| Agent Result | Covered | deterministic + real model | AgentResultBoundaryTest; Holdout | V3.7.5 worktree | Yes | Prior conflict/deep-read miss |
| Weird filename | Covered | deterministic | WeirdFilenameDiscoveryTest | V3.7.4 | No | Discovery unchanged |
| Extensionless text | Covered | deterministic + Holdout | UnknownExtensionTextTest; Holdout | V3.7.4 | Minimal | Shared Prompt changed |
| Chinese filename | Covered | deterministic | content-first discovery fixtures | V3.7.4 | No | Discovery unchanged |
| Large file middle | Covered | deterministic | LargeFileContentMapTest; generated fixture | V3.7.4 | No | Reader unchanged |
| Large file tail | Covered | deterministic + Holdout | TailRevisionDetectionTest; Holdout | V3.7.4 | Minimal | Prompt changed |
| 80k+ line code | Covered | deterministic | generated large-code fixture | V3.7.4 | No | Reader unchanged |
| Oversized Markdown | Covered | deterministic | generated large-document fixture | V3.7.4 | No | Reader unchanged |
| Oversized Agent Result | Covered | deterministic | generated Agent-result fixture | V3.7.4 | No full rerun | Reader unchanged |
| JSON | Covered | deterministic | large structured fixture | V3.7.4 | No | Parser boundary unchanged |
| YAML | Covered | deterministic + Holdout | YAML fixture; Holdout | V3.7.4 | Minimal | View contract changed |
| Source identity | Covered | deterministic | tail hash, Candidate Work Result tests | V3.7.5 worktree | Yes, completed | New hash/revision flow |
| Duplicate suppression | Covered | deterministic | discovery/cache and cross-chunk tests | V3.7.4 | No | Algorithm unchanged |
| Secret redaction | Covered | deterministic + CI | redactor/security tests; sensitive-content job | V3.7.5 worktree | Yes, completed locally | New task/work-result inputs |
| Partial coverage | Covered | deterministic | Content Map and Context Package tests | V3.7.5 worktree | Yes, completed | New disclosure fields |
| Conflict | Partial pending model | deterministic + real model | Semantic diagnostics; frozen Holdout | V3.7.5 worktree | Yes | Prior DeepSeek miss |
| Unknown | Covered | deterministic + real model | inference/unknown tests; Holdout | V3.7.5 worktree | Minimal | Prompt changed |
| Strong Fact promotion | Covered | deterministic | FactPromotionGuardTest and related tests | V3.7.5 worktree | Yes, completed | Seven-state contract changed |
| Model consensus boundary | Covered | deterministic | TwoModelContractTest; Prompt snapshot | V3.7.5 worktree | Yes, completed | Contract v3 |
| Multi-project list/read/search | Covered | product H2 + Hermes | ProjectMemoryGatewayTest; Hermes suite | V3.7.5 worktree | Yes, completed | Context v2 |
| Authorization isolation | Covered | product H2 | MultiProjectAuthorization and API tests | V3.7.5 worktree | Yes, completed | New endpoints |
| Candidate write | Covered | unit + product H2 | AgentWorkResultWriteTest; API contract test | V3.7.5 worktree | Yes, completed | New batch flow |
| Direct strong-fact rejection | Covered | unit + product H2 | Candidate and API contract tests | V3.7.5 worktree | Yes, completed | Reject-before-write |
| Context Package | Covered deterministic; model qualification pending | unit + product H2 + Hermes | AgentContextPackageTest; API/Hermes tests | V3.7.5 worktree | Yes, completed locally | v2 contract |
| Model switch continuity | Covered | deterministic | persisted Context Package contract | V3.7.5 worktree | Yes, completed | No model call |
| Agent switch continuity | Covered | deterministic + Hermes | package revision and resource tests | V3.7.5 worktree | Yes, completed | Persisted source |
| Restart | Covered | deterministic/product | job and Hermes restart tests | V3.7.5 worktree | Minimal | Core unchanged |
| Cancellation | Covered | deterministic + Playwright | Gateway/job tests; Playwright | V3.7.5 worktree | Minimal, completed | Prompt still bounded |
| Persistence/readback | Covered | H2 + product E2E | full backend; Project Understanding E2E | V3.7.5 worktree | Yes for real models | Product chain required |
| GLM | Pending V3.7.5 qualification | real model | V3.7.4 saved artifacts | V3.7.4 | Yes | Prompt v12/v7 changed |
| DeepSeek | Pending V3.7.5 qualification | real model | V3.7.4 failed Holdout | V3.7.4 | Yes | Blocking prior miss |
| Product E2E | Partial pending models | fixed + real model | Playwright; V3.7.4 GLM E2E | V3.7.5 worktree | Yes | Both real models required |
| PostgreSQL | Covered in prior CI; local unavailable | Testcontainers CI | Run 30504805160 and prior PR runs | 78e57ff | Yes in PR CI | Docker unavailable locally |
| Frontend | Covered | deterministic build | lint, 50 contracts, Next build | V3.7.5 worktree | Yes, completed | DTO compatibility |
| Playwright | Covered | product E2E fixed model | 8 browser tests | V3.7.5 worktree | Yes, completed | Existing GUI compatibility |
| Hermes | Covered | subprocess integration | 7 tests, 13 tools | V3.7.5 worktree | Yes, completed | New Context parameters |
| Obsidian | Covered | subprocess integration | 18 tests | V3.7.5 worktree | Minimal, completed | Metadata semantics only |

Formal model and CI rows are updated only after their actual runs. Historical failures remain visible.
