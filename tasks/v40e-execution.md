# V4.0-E execution design

Status: LOCAL_ACCEPTANCE_PASS_CI_PENDING. Implementation, real dogfood, final independent review and local gates are complete; stacked Draft PR and actual branch CI remain pending. Base: PR #24 at `81730744a325ccc2391009b367ca4ba95aef6d14`; master at `1712841b77fd1e8146ce4ab6beaf404e5b1f7a53`. PRs #21–24 remain open Drafts. The original working tree contains user edits and is not the implementation checkout.

## Scope and sequence

1. Reproduce Current Understanding using the unchanged production Gateway, isolated storage, frozen local clones, the existing Windows User RELAY credential, `gpt-5.6-sol` / Responses / xhigh. Preserve sanitized failure metadata.
2. Diagnose the configured request timeout, SDK transport lifecycle, durable cancellation and exception wrapping. Apply the smallest evidenced transport fix; retain bounded retry, validation and previous successful snapshots. Evaluate stage recovery without creating another Understanding engine.
3. Preserve request attempts and safe partial/unknown usage through failed durable Jobs. Do not represent missing upstream usage as measured zero.
4. Improve generic History wording through evidence selection and existing Technical Atoms, keeping direct-evidence authority ceilings. Add a semantic usefulness check alongside unsupported-claim validation. Distinguish occurrence time from source-record/scan observation time throughout the existing read model.
5. Verify deterministic reliability, evidence/authority, time, pagination, incremental, V4 routes and browser contracts. Run both real Current Understanding cases, unchanged reuse, recovery, full History/Workline dogfood and independent Sol/xhigh review of the complete representative visible product surface.
6. Run all applicable inherited gates, root launcher and Windows portable; audit CI warnings. Record debt classification, failures, remaining risks and stack consolidation readiness. Deliver additive evidence, Agent Result, commit and stacked Draft PR; no Ready, merge, Tag or Release.

## Acceptance boundaries

No model/effort/protocol substitution, raw prompt/response/reasoning or credential persistence. Read-only GETs remain model-free. Derived understanding does not write ProjectFact. Existing midnight-blue IA, Workline source authority, Demo isolation and no-source plan/progress rejection remain intact. Owner review remains NOT_REVIEWED unless the owner actually reviews. Desktop technical entry is READY_FOR_POC only when every stated technical and semantic gate passes.

## Evidence routing

Final report: `docs/projectflow-v4.0-e-semantic-quality-debt-closure-report.md`.
Additive artifacts: `docs/acceptance-evidence/v4.0-e/`.
Transient local runs, clones and runtime data: ignored `tmp/v40e-*` directories.
Prior V4.0-D failures and review rounds remain unchanged.
