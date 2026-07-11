# Troubleshooting

PostgreSQL Testcontainers says no valid Docker environment: start Docker Desktop and rerun `mvn.cmd -Ppostgres-it verify`. This is a real test failure, not a model or H2 failure.

Playwright cannot find a browser: run `npm.cmd run test:e2e:install`. On Windows local verification can use installed Microsoft Edge; CI installs Chromium and ffmpeg.

A job is REJECTED: the bounded queue or global active limit is full. No model request was sent; retry later.

A job is INTERRUPTED: the service restarted after a model request may have been sent. ProjectFlow will not replay it automatically. Review the preserved old result and explicitly rerun if needed.

A job is RETRYABLE: the service restarted before model dispatch. It is safe to run again.

A job remains CANCEL_REQUESTED: the current synchronous Git, GitHub or model HTTP call must return or reach its single-request timeout. No later retry or formal write will start.

Retry returns an existing job ID: this is expected when an equivalent `QUEUED`, `RUNNING`, or `CANCEL_REQUESTED` job exists. Retry never creates a parallel equivalent task.

An old H2 database fails on a null optimistic-lock version: V3.3.7 finalization adds the job version column with database default `0`. Run the current application once with its normal `ddl-auto=update`; do not delete the database.
