# ProjectFlow V3.3.2 Design

## Scope

Upgrade the existing V3.3 workflow without removing project creation, zip import, local path binding, local Git scanning, Agent bridge, model settings, sediment review, outputs, or Windows launchers.

## Analysis pipeline

Local Git and Agent results remain authoritative evidence sources. A scan captures commit/file/diff hints, worktree state, GitHub metadata, timings, and a deterministic fingerprint. Rules build evidence-bounded fallback segments; the configured model may rewrite them into a fixed schema. Evidence validation and a quality gate reject invented or directory-level summaries. Failed model output is retried once, then falls back explicitly; inadequate fallback output is marked for manual organization.

An unchanged fingerprint reuses the existing batch and segments. GitHub CLI and remote inspection use short, read-only commands. Missing CLI, login, upstream, permission, proxy, or network access never blocks local analysis.

## Capability flow

Capability analysis runs once for the whole project using confirmed sediments, development segments, evidence refs, commit links, analysis records, and Agent results. It creates three to eight structured candidate cards. Each card has its own status and evidence, so confirming one cannot confirm others. Legacy ProjectMemory strings remain visible only as a collapsed compatibility archive.

## Sediment review and evidence

List and detail routes use the same V3.3 confirmation endpoint and support NEW_SEDIMENT, MERGE_EXISTING, EVIDENCE_ONLY, and IGNORE. Suggestions infer a conservative action from existing sediment similarity and evidence-only changes. Bulk creation is removed from the primary path. Evidence views separate summarized changes, Git evidence, verification evidence, and source material.

## Verification

Backend tests cover quality rejection, fallback diagnostics, fingerprint reuse, GitHub degradation, suggestion inference, structured capability confirmation, and four-action review. Frontend verification uses TypeScript/Next production build. Full Maven tests and launcher/document checks complete the release gate.
