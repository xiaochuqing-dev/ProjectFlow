# ProjectFlow V3.7.5 Existing Coverage Matrix

This matrix prevents duplicate work that adds no new Evidence. Time and Token are not treated as quality defects; `Rerun` means the V3.7.5 Prompt, Evidence ordering, Promotion or Agent Context changes materially affect the row.

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
| Conflict | Covered | deterministic + two-model Holdout | Semantic diagnostics; V3.7.5 frozen runs | Freeze 2 | Completed | Both final Holdouts detected applicable conflict; rate 1.0000 |
| Unknown | Covered | deterministic + real model | inference/unknown tests; Holdout | V3.7.5 worktree | Minimal | Prompt changed |
| Strong Fact promotion | Covered | deterministic | FactPromotionGuardTest and related tests | V3.7.5 worktree | Yes, completed | Seven-state contract changed |
| Model consensus boundary | Covered | deterministic | TwoModelContractTest; Prompt snapshot | V3.7.5 worktree | Yes, completed | Contract v3 |
| Multi-project list/read/search | Covered | product H2 + Hermes | ProjectMemoryGatewayTest; Hermes suite | V3.7.5 worktree | Yes, completed | Context v2 |
| Authorization isolation | Covered | product H2 | MultiProjectAuthorization and API tests | V3.7.5 worktree | Yes, completed | New endpoints |
| Candidate write | Covered | unit + product H2 | AgentWorkResultWriteTest; API contract test | V3.7.5 worktree | Yes, completed | New batch flow |
| Direct strong-fact rejection | Covered | unit + product H2 | Candidate and API contract tests | V3.7.5 worktree | Yes, completed | Reject-before-write |
| Context Package | Covered | unit + product H2 + Hermes | AgentContextPackageTest; API/Hermes tests | 2d54d91 | Yes, completed | v2 contract, PR and master gates passed |
| Model switch continuity | Covered | deterministic | persisted Context Package contract | V3.7.5 worktree | Yes, completed | No model call |
| Agent switch continuity | Covered | deterministic + Hermes | package revision and resource tests | V3.7.5 worktree | Yes, completed | Persisted source |
| Restart | Covered | deterministic/product | job and Hermes restart tests | V3.7.5 worktree | Minimal | Core unchanged |
| Cancellation | Covered | deterministic + Playwright | Gateway/job tests; Playwright | V3.7.5 worktree | Minimal, completed | Prompt still bounded |
| Persistence/readback | Covered | H2 + product E2E | full backend; Project Understanding E2E | V3.7.5 worktree | Yes for real models | Product chain required |
| GLM | Qualified for frozen V3.7.5 scope | real Holdout + product E2E | V3.7.5 safe model-run summary | Freeze 1 | Completed | Holdout 8/8; E2E 8/8; one disclosed Final degradation |
| DeepSeek | Qualified for frozen V3.7.5 scope | preserved failed run + Freeze-2 Holdout + product E2E | V3.7.5 safe model-run summary | Freeze 2 | Completed | First run failed 5/8; explicit JSON Mode refreeze passed 8/8 without lowering high reasoning |
| Product E2E | Covered for both profiles | fixed + real product persistence/readback | Playwright; GLM and DeepSeek E2E artifacts | Freeze 2 | Completed | Both 8/8; invalid Evidence refs 0 |
| PostgreSQL | Covered; local unavailable | PostgreSQL 16 Testcontainers CI | final PR Run 30701958206; master Run 30702083514 | 2d54d91 | Completed | PR and functional master runs passed; Docker unavailable locally |
| Frontend | Covered | deterministic build | lint, 50 contracts, Next build | V3.7.5 worktree | Yes, completed | DTO compatibility |
| Playwright | Covered | product E2E fixed model | 8 browser tests | V3.7.5 worktree | Yes, completed | Existing GUI compatibility |
| Hermes | Covered | subprocess integration | 7 tests, 13 tools | V3.7.5 worktree | Yes, completed | New Context parameters |
| Obsidian | Covered | subprocess integration | 18 tests | V3.7.5 worktree | Minimal, completed | Metadata semantics only |

Formal model and CI rows are updated only after their actual runs. Historical failures remain visible.
