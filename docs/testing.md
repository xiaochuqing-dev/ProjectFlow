# Testing

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
