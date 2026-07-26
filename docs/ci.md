# CI quality gates

`.github/workflows/quality-gates.yml` runs on pushes and pull requests.

The blocking jobs are backend unit/H2 tests, PostgreSQL Testcontainers, frontend TypeScript/production build/contracts, Playwright browser E2E and a basic committed-secret scan. JUnit and Playwright failure evidence are uploaded as artifacts. API keys, Authorization values, raw reasoning, model bodies and local database snapshots are not uploaded.

V3.7.2 keeps real GLM optional because it consumes external quota. The workflow-dispatch job executes a focused Provider probe, the ProjectFlow-only 18-case representative evaluation through `ModelGatewayService`, and the eight-case real `ProjectUnderstandingService.refresh()` acceptance. It uploads only sanitized evaluation artifacts. Fixed-model tests remain blocking and must never be reported as real GLM evidence.

The PostgreSQL job intentionally fails when Docker is unavailable; it never silently substitutes H2. The optional real Provider job runs only through workflow_dispatch with `run_real_model` enabled. `PROJECTFLOW_REAL_MODEL_API_KEY` is injected from GitHub Actions secrets and is never persisted or uploaded; a missing secret produces an explicit SKIPPED message. The current optional configuration uses GLM `glm-5.2`, the Ark coding v3 base URL and `OPENAI_RESPONSES`.

`browser-e2e` starts the backend, frontend, and a deterministic OpenAI-compatible test server. All eight current core tests are blocking. `postgres-integration` runs the full PostgreSQL 16 workflow tests; it is not a repository CRUD substitute.
