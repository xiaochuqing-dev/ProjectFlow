# Testing

## V3.7.5 constitution, Context Package and two-model gates

Blocking deterministic coverage now includes the seven epistemic states, model/attention/phase/fallback non-promotion, declaration and process-evidence boundaries, conflict/UNKNOWN preservation, currentness, bounded Dynamic Profile claims, complete small-set Evidence Ledgers, exact eligible-capability decisions, semantic-contract degradation, Context Package v2 relevance/ranges/revision/package identity, candidate-only work-result writes, five local revalidation actions, project isolation and Timeline non-authority.

The V3.7.4 Calibration and frozen Holdout labels remain unchanged. V3.7.5 formally freezes code SHA, Prompt/fixture/Ground Truth hashes, thresholds and Provider profile before one Holdout per model. Product E2E uses `ProjectUnderstandingRealModelIT` only after that Provider's Holdout passes and writes to a Provider-specific `projectflow.eval.output-name`. Raw prompts, responses, reasoning and keys are never artifacts.

Real-project gap checks are deterministic and 0-model unless the coverage matrix proves a semantic gap. A repository that changes during the scan must be reported as volatile/partial rather than treated as a stable fingerprint. Actual model, real-project, CI and regression results belong in the V3.7.5 acceptance evidence and report.

## V3.7.4 strong-fact and generalization gates

Blocking deterministic coverage includes strong status promotion, declaration/inference isolation, Agent-result boundaries, explicit historical reason/deprecation/technical-debt evidence, Evidence allow-lists, extensionless content discovery, Content Map head/middle/tail/marker ranges, tail-sensitive revisions, cross-chunk merge, project ownership, portfolio isolation, Agent candidates, Context Packages, MCP history/resources, provider-neutral prompt contracts and Ground Truth leakage.

The original V3.7.3 18-case/38-run set remains unchanged. V3.7.4 adds a separate Calibration resource and frozen Holdout resource. Production and all direct Eval prompts use the same builder; set identity, expected labels, forbidden claims and scoring thresholds remain outside its input type. Formal Holdout records code, prompt, Ground Truth and Provider hashes before its first run and preserves failures.

Giant fixtures are generated locally with `tools/generate-v374-large-fixtures.ps1`; raw files stay outside Git. The committed specification and manifest identify hashes, sizes, line counts and expected fact locations. Both real Providers must pass the same Strong Fact contract; Provider-specific prompts or fact rules are forbidden.

## V3.4.1 timeline gates

Timeline acceptance includes 60 focused backend cases for time boundaries, ownership, deterministic statistics, coverage, invalid IDs, chunk synthesis, refresh/failure/cancel/retry semantics and history interaction; 15 frontend contracts; Playwright A-H business flows; and a real synthetic performance gate with 100 batches, 5000 facts, 36 months, 300 themes and a 230-fact month. Full backend/H2, PostgreSQL Testcontainers, production build, all Playwright flows, copied and current file-backed H2, desktop BAT, sensitive scan, push and current CI remain release gates. Actual results belong only in the V3.4.1 report.

## V3.4.0 project fact memory gates

V3.4.0 release acceptance must prove the automatic fact path, not only entity persistence. Required backend coverage includes:

- MODEL, complete partial-model, LOCAL_RULE + Git, and Agent result + code evidence ingestion rules.
- Evidence-free or conflicting content never becoming a strong `RECORDED` fact.
- No new manual ProjectChange suggestion after a normal scan; batch completion and the next scan require no confirm API.
- Idempotency for repeated ingest, reusable batch, job retry, concurrent writes, restart, migration, and history replay in both H2 and PostgreSQL.
- Fact Cursor initialization from a legacy Review Cursor, advancement after successful persistence, advancement with Needs Attention, and no advancement after ingestion failure.
- Legacy Development Segment and Project Sediment migration without duplicate facts; old pending ProjectChange never blocks the new cursor.
- 100+ commit history reconstruction in bounded oldest-first chunks, covered-commit skipping, cancellation, restart/checkpoint resume, retry, and coexistence with normal scans.
- Evidence occurrence time: an old commit backfilled today retains its historical time range; unreliable time is explicitly degraded.

`ProjectFlowH2UpgradeIntegrationTest` must use a file-backed V3.3.8.1-style database and restart with the current application. It must preserve old project/batch/segment/sediment/change/job data, create or migrate facts idempotently, and require no database deletion. `ProjectFlowPostgresIT` must exercise the same core fact, cursor, migration/idempotency, history, retry, and cancellation boundaries against PostgreSQL 16 Testcontainers.

Playwright uses a real backend/frontend and the explicitly labelled fixed compatible model server. Required product scenarios are: automatic facts after “分析新变化”; batch detail without four-way confirmation controls; a second scan that reads only new commits without user action; Needs Attention that does not block the cursor; V3.3.8.1 data upgrade; and long-history backfill progress/resume/completion. Existing Dashboard Bootstrap, F5, project isolation, secondary GitHub failure, cancellation, and retry coverage must remain.

Performance acceptance seeds at least 100 batches, 1,000 Project Facts, and a 5,000-fact pagination case. Record actual elapsed time and prepared-query count for the Project Records batch list, fact-memory overview, first facts page, and batch-detail facts. Tests must reject batch-to-fact N+1, Java-side full evidence filtering, and loading all facts to calculate count/min/max/latest.

Run the complete local gates from the repository root:

```powershell
cd backend
mvn.cmd test
mvn.cmd -Dtest=ProjectFlowH2UpgradeIntegrationTest test
mvn.cmd -Ppostgres-it verify

cd ..\frontend
npm.cmd run lint
npm.cmd run test:contracts
npm.cmd run build
$env:MAVEN_CMD=(Get-Command mvn.cmd).Source
npm.cmd run test:e2e
```

The CI committed-secret scan remains blocking. It searches tracked non-document files for obvious `sk-...` and `Bearer ...` values; backend/frontend security guardrail tests remain separate behavioral coverage. The final desktop check must actually rebuild and run the current tree through `Start-ProjectFlow.bat` and verify `logs/last-embedded-build.json`; `-CheckOnly` alone is insufficient after code or build changes.

V3.4.0 does not repeat the V3.3.8 full real-DeepSeek stress matrix when the gateway and model-entry contracts are unchanged. Fixed-model automation proves fact/history/browser contracts, not Provider behavior. If a model boundary changes, run the smallest affected real entrypoint with a safe key and report it separately.

Do not record counts, timings, CI links, or success claims in this guide before they are actually observed. Put final values in `docs/projectflow-v3.4.0-project-fact-memory-report.md`.

## V3.3.8.1 data-read gates

`DataReadReliabilityTest` seeds a legacy nullable batch/change/segment beside a normal batch, verifies list/detail degradation, measures a 50-batch list at four prepared statements, and validates the persisted Dashboard Bootstrap response. Frontend behavior tests cover weak-result merging, project-isolated snapshots, legacy-key migration, and freshness metadata. Playwright covers immediate return from Settings, F5 after sessionStorage clearing, A/B project isolation, secondary GitHub failure isolation, and sediment list/detail/confirmation statistics. PostgreSQL workflow acceptance also reads its persisted scan through Dashboard Bootstrap.

Backend unit and H2 compatibility:

cd backend
mvn.cmd test

PostgreSQL Testcontainers, requiring a running Docker service:

cd backend
mvn.cmd -Ppostgres-it verify

Frontend contracts, type checking and production build:

cd frontend
npm.cmd run test:contracts
npm.cmd run lint
npm.cmd run build

Browser E2E starts an isolated embedded backend and Next.js frontend with a fixed compatible model service:

cd frontend
npm.cmd run test:e2e

Playwright retains trace, screenshot and video on CI failure. Local Windows runs use installed Edge; CI installs Chromium. Test data lives under frontend/.e2e-data and is ignored.

The original V3.3.7 browser suite had four isolated tests for model-backed batches, sediment confirmation, capability failure preservation, and cancellation/retry idempotency. V3.4.0 replaces the routine sediment-confirmation product path while retaining the reliability checks. The local OpenAI-compatible fixed server is test infrastructure, not real DeepSeek.

`ProjectFlowPostgresIT` starts PostgreSQL 16 and runs service/repository/transaction workflows for scan, formal suggestion, sediment confirmation, capability candidate/confirmation, failure preservation, concurrent retry and cancellation. `ProjectFlowH2UpgradeIntegrationTest` removes the V3.3.7 job columns from a populated file database, restarts the current app with `ddl-auto=update`, and verifies old data plus new retry/cancel behavior.

V3.3.8 adds focused tests for Provider/model capability selection, task/input-aware output budgets, unsupported Temperature omission, multiple JSON candidates, unrelated leading arrays, unknown nested wrappers, snake_case aliases, Schema repair success/failure, reasoning-exhausted empty content, truncation recovery and safe H2 enum/column backfill.

`RealDeepSeekIT` is optional and runs only with `PROJECTFLOW_RUN_REAL_MODEL=true` plus a secure `DEEPSEEK_API_KEY`. It validates all six registered small-input entrypoints. Local release acceptance additionally drives actual application APIs against an isolated copy of the configured embedded Provider and records medium/large ProjectFlow self-analysis separately from fixed-model automation.

Real DeepSeek is disabled unless PROJECTFLOW_RUN_REAL_MODEL=true and DEEPSEEK_API_KEY is present. The fixed input caps output at 128 tokens, task requests at 3 and total asserted usage below 1,000 tokens. Without a key the workflow prints SKIPPED.

## V3.4.2 capability map gates

Focused backend cases cover 42-fact reclassification, exact classification, unknown/duplicate/missing IDs, stable identity, aliases, deterministic maturity, safe and high-risk merge, bootstrap/incremental/no-change, ownership, filters, retry and old-READY preservation. Performance fixtures jointly cover 5000 facts, 100 capabilities, 1000 evolutions, 10000 relations, 300 Timeline themes, a 230-fact month, and 500 incremental facts with measured P50/P95 and Hibernate statement counts. Release gates remain the complete backend/H2 suite, old file-backed H2 upgrade, a safe copy of the current H2 database, PostgreSQL 16 Testcontainers, frontend contracts, TypeScript, production build, all Playwright flows, sensitive scan, repository BAT startup and GitHub Actions. Actual results belong only in the V3.4.2 report.

## V3.4.3 Gateway and MCP gates

Gateway tests must prove ownership, occurrence-time filtering, 7/17 occurrence with 8/20 analysis remaining in July, stale Timeline/Capability fallback, explicit source/derived search layers, chronological evolution, safe fact trace, pagination, bounded output and query-text-free audit. The scale fixture is 5000 facts over 36 months, 100 capabilities, 1000 evolutions and 10000 relations; it measures P50/P95, query count and response bytes for all nine business reads. The MCP subprocess suite verifies nine-tool discovery/calls, read-only annotations, auth forwarding, compact pagination, concurrent read, restart, timeout, unavailable backend, remote rejection and no credential leak. Full H2, PostgreSQL, frontend contracts/lint/build, Playwright, sensitive scan, real repository startup and CI remain blocking; actual observations belong in the V3.4.3 report.

## V3.4.4 Obsidian projection gates

The Python suite uses real temporary Vault directories and a real CLI-to-HTTP Gateway boundary. It verifies complete CORE output, no-op zero writes, scoped incremental updates, 7/17 occurrence analyzed 8/20 remaining in July, frontmatter/user-content preservation, managed-block conflicts, move/rename/merge, collision/path controls, interruption, corrupt-manifest recovery, profile archive idempotency and local-only transport. The scale fixture projects 5000 facts, 36 months, 100 capabilities and 1000 evolutions while measuring first-sync time, bytes, files and no-op writes. Release gates also include the current H2 safe-copy Vault inspection, full backend/H2, PostgreSQL 16, MCP, frontend contracts/lint/build, Playwright, sensitive scan, desktop startup and CI. Only the V3.4.4 report may claim actual status.

## V3.7.4 real-provider gates

Real-provider evaluation can select a separate Calibration or frozen Holdout resource with `projectflow.eval.ground-truth-resource`, restrict case IDs and write an isolated sanitized result directory. Production and evaluation share the same prompt builder and gateway.

Provider capabilities are explicit inputs. `PROJECTFLOW_REAL_MODEL_SUPPORTS_REASONING=true` is used for a reasoning model whose name is not covered by the generic heuristic; the key remains process-only. Expected shapes and views are validated against production prompt and registry vocabulary before a formal run. An Evidence reference is valid only when it belongs to the current case allow-list. The older “evidence precision” metric measures selection specificity and is diagnostic; it is not used as an invalid-reference count.
