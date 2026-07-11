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

Real DeepSeek is disabled unless PROJECTFLOW_RUN_REAL_MODEL=true and DEEPSEEK_API_KEY is present. The fixed input caps output at 128 tokens, task requests at 3 and total asserted usage below 1,000 tokens. Without a key the workflow prints SKIPPED.
