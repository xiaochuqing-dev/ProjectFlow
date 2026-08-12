# V3.8.5 Narrative Entailment Contract

This contract controls the public wording of every automatically generated Story and Chapter. It is Provider-neutral and does not change ProjectFact, raw events, Technical Atoms, membership, chronology or Evidence.

The engineering layer classifies each Story as exactly one of PLANNED, DECLARED, CONFIGURED, IMPLEMENTED, OBSERVED, VERIFIED, REMOVED, RESTORED, UNKNOWN or CONFLICTED. Classification consumes Technical Atoms without flattening their subject/action relationship. It supplies the only allowed subject label, action, outcome, direct support, indirect context, allowed claims, forbidden claims and eligible reason Evidence. The model may paraphrase within that envelope; it cannot strengthen the state.

PLANNED cannot become implemented. DECLARED cannot become verified. CONFIGURED cannot become deployed. IMPLEMENTED without direct independent validation Evidence cannot become stable, production-ready or verified. OBSERVED cannot become implemented or verified. UNKNOWN and CONFLICTED cannot become a positive strong fact. Negative wording such as “不能确认已经实现” is not treated as a positive implementation claim.

Same-commit membership, temporal proximity, shared affected area, subject similarity and Supporting Story links are indirect context only. They never promote another subject. A strong claim must be supported by a direct Atom whose stable subject, Evidence class and action match that claim. Supporting Evidence cannot raise its Primary Story.

A non-empty reason requires at least one reasonEvidenceRef already allow-listed for the Story. Commit messages, file names, model agreement and README language are not sufficient on their own. When the reason is not supported, it stays empty and the public layer uses one natural sentence explaining that the reason cannot yet be confirmed.

Validation rejects unsupported objects, raw paths or filenames, internal enum/token leakage, fixture identifiers, state upgrades, unsupported reasons, repeated Title/Summary/Before/Change/After wording, and vague or list-like Chapter synthesis. One Provider-neutral regeneration is allowed from the original bounded input. A second failure keeps the deterministic narrative and exposes degraded diagnostics. Both generated and deterministic wording pass the same gate.
