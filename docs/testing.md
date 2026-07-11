# Testing

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

Browser E2E starts an isolated embedded backend and Next.js frontend, then creates and refreshes a project, submits 10 duplicate scan requests, verifies one job ID, cancels the task and verifies cancellation survives refresh:

cd frontend
npm.cmd run test:e2e

Playwright retains trace, screenshot and video on CI failure. Local Windows runs use installed Edge; CI installs Chromium. Test data lives under frontend/.e2e-data and is ignored.

The final V3.3.7 browser suite has four isolated tests: model-backed analysis batch generation, sediment confirmation plus local-draft isolation, sediment-to-capability analysis with failed re-analysis preserving the prior success, and cancellation/retry idempotency. The local OpenAI-compatible fixed server is test infrastructure, not real DeepSeek.

`ProjectFlowPostgresIT` starts PostgreSQL 16 and runs service/repository/transaction workflows for scan, formal suggestion, sediment confirmation, capability candidate/confirmation, failure preservation, concurrent retry and cancellation. `ProjectFlowH2UpgradeIntegrationTest` removes the V3.3.7 job columns from a populated file database, restarts the current app with `ddl-auto=update`, and verifies old data plus new retry/cancel behavior.

V3.3.8 adds focused tests for Provider/model capability selection, task/input-aware output budgets, unsupported Temperature omission, multiple JSON candidates, unrelated leading arrays, unknown nested wrappers, snake_case aliases, Schema repair success/failure, reasoning-exhausted empty content, truncation recovery and safe H2 enum/column backfill.

`RealDeepSeekIT` is optional and runs only with `PROJECTFLOW_RUN_REAL_MODEL=true` plus a secure `DEEPSEEK_API_KEY`. It validates all six registered small-input entrypoints. Local release acceptance additionally drives actual application APIs against an isolated copy of the configured embedded Provider and records medium/large ProjectFlow self-analysis separately from fixed-model automation.

Real DeepSeek is disabled unless PROJECTFLOW_RUN_REAL_MODEL=true and DEEPSEEK_API_KEY is present. The fixed input caps output at 128 tokens, task requests at 3 and total asserted usage below 1,000 tokens. Without a key the workflow prints SKIPPED.
