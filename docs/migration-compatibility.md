# Migration compatibility

## V3.4.1 Timeline assignment migration

Current startup adds nullable Timeline assignment fields and derived tables through the existing `ddl-auto=update` boundary. A paged migration assigns old Project Facts from their occurrence time and normalizes file refs idempotently without loading every fact or deleting legacy rows. Legacy segment migration suppresses per-batch Timeline jobs; the startup Timeline bootstrap coalesces the completed fact set. H2 copied/current-database and PostgreSQL gates must verify rerun stability, batch/fact counts, zero fingerprint duplicates and unchanged legacy relations.

## V3.4.0 Project Fact migration

V3.4.0 adds Project Fact, incremental Fact Cursor, and persistent history-coverage state, plus fact-recording metadata required by batches/read models. The project still uses Hibernate `ddl-auto=update`; this is not a versioned Flyway migration system and must be reported as such.

Compatibility rules:

- Current H2 data upgrades without clearing `.projectflow/local-data/`; PostgreSQL tables and old rows remain intact.
- New nullable fields use null-safe reads and new required fields use safe defaults that do not fabricate historical meaning.
- Existing Change Batches, Development Segments, ProjectChanges, ProjectSediments, ProjectReviewCursor, jobs, Providers, memories, and capability cards are not deleted or reassigned blindly.
- Legacy Development Segments are the preferred migration source. Migration derives a stable source/evidence fingerprint and may be rerun without increasing the fact count.
- A legacy sediment with `sourceSegmentId` reuses the segment-derived fact and records compatibility provenance instead of creating a second fact.
- A legacy sediment without a segment creates a `LEGACY_SEDIMENT_MIGRATION` fact only when objective evidence is usable. Evidence-free legacy text remains compatibility content and never becomes a strong fact.
- Old pending ProjectChanges remain readable and do not block Fact Cursor initialization or advancement.
- A missing Fact Cursor initializes from the legacy Review Cursor when available; otherwise the bounded first-scan policy remains. History checkpoint state is separate.
- Migration failure must stop cursor advancement and preserve all prior data. It must never be “fixed” by deleting the database or old batches.

The file-backed H2 upgrade test must seed a V3.3.8.1-style database, restart the current application, verify old relationships and idempotent fact creation, and rerun migration. PostgreSQL Testcontainers must verify the same uniqueness and transaction boundaries. Actual test counts and observed upgrade results belong in the V3.4.0 implementation report, not in this compatibility contract.

## V3.3.8.1 nullable analysis history

Historical ChangeBatch, ProjectChange, and DevelopmentSegment rows may lack fields added by later releases. Read-time defaults keep list and detail APIs usable without destructive migration: blank diagnostics remain blank, missing status/quality values use conservative review states, missing collections become empty, missing counters become zero, and missing timestamps fall back to related persisted times or the epoch. The service labels a batch with absent model status as historical incomplete. V3.3.8.1 performs no data backfill, so existing correct values and historical evidence are not overwritten.

The former single dashboard snapshot key is migrated lazily to a schema-versioned project key. Invalid cache JSON is ignored; database bootstrap remains the recovery path.

The project still uses Hibernate ddl-auto update and does not yet have Flyway. This is an explicit risk.

New ProjectAnalysisJob reliability columns are nullable in the database so populated H2 and PostgreSQL tables can be altered without fabricating values. PostLoad applies defaults of 2 attempts, 3 model requests, 10 minutes and 60,000 tokens. Legacy rows are not assigned to new batches and completed results are not rewritten.

ProjectAnalysisJobCompatibilityTest verifies null-field legacy rows. ProjectFlowH2UpgradeIntegrationTest adds a file-backed restart test: it seeds project, Provider, failed/succeeded jobs, confirmed sediment, confirmed capability and candidate capability; removes the V3.3.7 job columns; then starts the current application with `ddl-auto=update`. Counts, content, statuses and relationships remain intact, defaults are restored, and retry/cancel work without creating a new analysis batch.

The optimistic-lock `version` column is added with database default `0`. This is required because legacy rows are normalized after load; a nullable version would fail the first flush. New entities keep a Java-side null version until persist so Spring Data does not misclassify them as detached.

## V3.4.2 capability compatibility

Hibernate `ddl-auto=update` adds capability, evolution, relation, coverage, attention and map-state tables without changing ProjectFact, FactCursor, Timeline or legacy card rows. Startup reclassifies only evidence-complete PASS facts whose sole attention reason is the documented fallback occurrence time; fingerprints and cursors are untouched. A CONFIRMED legacy card may seed a stable capability only when its source references resolve through owned sediment/segment data to ProjectFact. Candidate, needs-confirmation and ignored cards remain unchanged. Migration is idempotent, never deletes cards or facts, and unresolved confirmed sources become attention rather than invented evidence.

## V3.4.3 Gateway compatibility

Hibernate adds only the safe memory-read audit table; Gateway DTOs and indexes reuse current entities. Existing ProjectFact, Timeline, Capability, Evolution, cursors, history and legacy rows are not rewritten. The one compatibility correction is temporal: a legacy sediment linked to an owned source batch inherits the batch's actual occurrence window and receives its Timeline assignment instead of being placed under migration time. The migration remains idempotent and preserves the original sediment and source batch. Current-database acceptance must run on a byte-identical safe copy and re-hash the untouched original afterward.

## V3.4.4 projection compatibility

Obsidian projection requires no schema migration and does not modify H2/PostgreSQL. Projection version `1` is stored in note metadata and manifest for future migrations. Missing/corrupt manifests are reconstructed from stable note identity and current Gateway output; established note paths survive capability rename and user move. Capability merge and profile/source removal are non-destructive redirects or archived entries. Existing Vault files outside the configured managed root are never inspected or changed.
