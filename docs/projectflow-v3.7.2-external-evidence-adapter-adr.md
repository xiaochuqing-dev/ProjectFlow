# ADR: External Evidence Adapter Contract

Status: Accepted

## Contracts

`EvidenceSourceAdapter` collects bounded `ExternalEvidenceEnvelope` values for a known project/revision.

`IntelligenceProviderAdapter` receives normalized envelopes and requested dimensions under input/output budgets. It returns a summary, evidence references and unknowns; it does not own scanning, commands or Facts.

`ProjectionAdapter` receives existing ProjectFlow evidence/views and a configured projection root. It returns changed/unchanged/conflict counts and does not modify ProjectFlow truth.

## Envelope

The envelope includes source system/type/ref, project binding, normalized summary, occurred/collected time, confidence, currentness, temporal role, evidence references, redaction/raw-payload flags, adapter ID/version, source revision and bounded process metadata.

Temporal role distinguishes CURRENT_STATE, HISTORICAL_EVENT, PROCESS_EVIDENCE, PROCESS_METADATA and UNKNOWN. This prevents Agent results or token usage from being silently treated as facts.

## Validation

`ExternalEvidenceEnvelopeValidator`:

- rejects missing project binding and source identity;
- rejects absolute/traversal locators;
- rejects `rawPayloadStored=true`;
- validates confidence/currentness/temporal role;
- redacts summary and process metadata;
- bounds fields and evidence references;
- creates a project/source/revision fingerprint and reports duplicates.

## PoC decision

No external product PoC is shipped in V3.7.2. The contracts and validator are the smallest useful proof; a concrete GitHub/Codex/Hermes/Obsidian adapter would require product-specific authentication, transport and storage decisions outside this phase.
