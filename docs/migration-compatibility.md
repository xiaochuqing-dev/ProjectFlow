# V3.3.7 migration compatibility

The project still uses Hibernate ddl-auto update and does not yet have Flyway. This is an explicit risk.

New ProjectAnalysisJob reliability columns are nullable in the database so populated H2 and PostgreSQL tables can be altered without fabricating values. PostLoad applies defaults of 2 attempts, 3 model requests, 10 minutes and 60,000 tokens. Legacy rows are not assigned to new batches and completed results are not rewritten.

ProjectAnalysisJobCompatibilityTest creates a row, removes all V3.3.7 values and verifies safe defaults after a fresh entity load. The broader existing H2 suite verifies confirmed sediment, capability and Provider behavior without clearing the database. PostgreSQL Testcontainers verifies the same entity mappings against PostgreSQL 16.
