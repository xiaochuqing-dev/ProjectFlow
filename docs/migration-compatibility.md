# V3.3.7 migration compatibility

The project still uses Hibernate ddl-auto update and does not yet have Flyway. This is an explicit risk.

New ProjectAnalysisJob reliability columns are nullable in the database so populated H2 and PostgreSQL tables can be altered without fabricating values. PostLoad applies defaults of 2 attempts, 3 model requests, 10 minutes and 60,000 tokens. Legacy rows are not assigned to new batches and completed results are not rewritten.

ProjectAnalysisJobCompatibilityTest verifies null-field legacy rows. ProjectFlowH2UpgradeIntegrationTest adds a file-backed restart test: it seeds project, Provider, failed/succeeded jobs, confirmed sediment, confirmed capability and candidate capability; removes the V3.3.7 job columns; then starts the current application with `ddl-auto=update`. Counts, content, statuses and relationships remain intact, defaults are restored, and retry/cancel work without creating a new analysis batch.

The optimistic-lock `version` column is added with database default `0`. This is required because legacy rows are normalized after load; a nullable version would fail the first flush. New entities keep a Java-side null version until persist so Spring Data does not misclassify them as detached.
