# CI quality gates

`.github/workflows/quality-gates.yml` runs on pushes and pull requests.

The blocking jobs are backend unit/H2 tests, PostgreSQL Testcontainers, frontend TypeScript/production build/contracts, Playwright browser E2E and a basic committed-secret scan. JUnit and Playwright failure evidence are uploaded as artifacts. API keys, Authorization values, raw reasoning, model bodies and local database snapshots are not uploaded.

V3.7.3 keeps real GLM optional because it consumes external quota. The workflow-dispatch job has a 360-minute test-process watchdog and executes a focused Provider probe, the unchanged 18-case/38-run representative evaluation through `ModelGatewayService`, and the eight-case real `ProjectUnderstandingService.refresh()` acceptance. Each Provider request receives the explicit 600-second test configuration; there is no 45-second cap. The GLM Provider ceiling is 32k because Responses reasoning and visible JSON share the output budget: Scout starts at its 16k task ceiling and only a detected truncation may use one bounded 24k recovery. It uploads only sanitized evaluation artifacts. Fixed-model tests remain blocking and must never be reported as real GLM evidence.

The PostgreSQL job intentionally fails when Docker is unavailable; it never silently substitutes H2. The optional real Provider job runs only through workflow_dispatch with `run_real_model` enabled. `PROJECTFLOW_REAL_MODEL_API_KEY` is injected from GitHub Actions secrets and is never persisted or uploaded; a missing secret produces an explicit SKIPPED message. The current optional configuration uses GLM `glm-5.2`, the Ark coding v3 base URL and `OPENAI_RESPONSES`. A committed-content gate rejects common OpenAI/Ark/Bearer secret shapes.

Product reliability and conditional semantic quality are reported separately, but the final gate requires both. Ground Truth, metric formulas and thresholds remain unchanged. Internal Eval fields have no production API, snapshot, database or UI path.

`browser-e2e` starts the backend, frontend, and a deterministic OpenAI-compatible test server. All eight current core tests are blocking. `postgres-integration` runs the full PostgreSQL 16 workflow tests; it is not a repository CRUD substitute.
