# CI quality gates

`.github/workflows/quality-gates.yml` runs on pushes and pull requests.

The blocking jobs are backend unit/H2 tests, PostgreSQL Testcontainers, frontend TypeScript/production build/contracts, Playwright browser E2E and a basic committed-secret scan. JUnit and Playwright failure evidence are uploaded as artifacts. API keys, Authorization values, raw reasoning, model bodies and local database snapshots are not uploaded.

The PostgreSQL job intentionally fails when Docker is unavailable; it never silently substitutes H2. The optional real DeepSeek job runs only through workflow_dispatch with run_real_model enabled. Missing DEEPSEEK_API_KEY produces an explicit SKIPPED message.

`browser-e2e` starts the backend, frontend, and a deterministic OpenAI-compatible test server. All four core tests are blocking. `postgres-integration` runs the full PostgreSQL 16 workflow tests; it is not a repository CRUD substitute.
