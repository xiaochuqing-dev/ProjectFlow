# Migration compatibility

## V3.3.8.1 nullable analysis history

Historical ChangeBatch, ProjectChange, and DevelopmentSegment rows may lack fields added by later releases. Read-time defaults keep list and detail APIs usable without destructive migration: blank diagnostics remain blank, missing status/quality values use conservative review states, missing collections become empty, missing counters become zero, and missing timestamps fall back to related persisted times or the epoch. The service labels a batch with absent model status as historical incomplete. V3.3.8.1 performs no data backfill, so existing correct values and historical evidence are not overwritten.

The former single dashboard snapshot key is migrated lazily to a schema-versioned project key. Invalid cache JSON is ignored; database bootstrap remains the recovery path.

The project still uses Hibernate ddl-auto update and does not yet have Flyway. This is an explicit risk.

New ProjectAnalysisJob reliability columns are nullable in the database so populated H2 and PostgreSQL tables can be altered without fabricating values. PostLoad applies defaults of 2 attempts, 3 model requests, 10 minutes and 60,000 tokens. Legacy rows are not assigned to new batches and completed results are not rewritten.

ProjectAnalysisJobCompatibilityTest verifies null-field legacy rows. ProjectFlowH2UpgradeIntegrationTest adds a file-backed restart test: it seeds project, Provider, failed/succeeded jobs, confirmed sediment, confirmed capability and candidate capability; removes the V3.3.7 job columns; then starts the current application with `ddl-auto=update`. Counts, content, statuses and relationships remain intact, defaults are restored, and retry/cancel work without creating a new analysis batch.

The optimistic-lock `version` column is added with database default `0`. This is required because legacy rows are normalized after load; a nullable version would fail the first flush. New entities keep a Java-side null version until persist so Spring Data does not misclassify them as detached.
