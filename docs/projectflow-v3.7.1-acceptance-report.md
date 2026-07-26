# ProjectFlow V3.7.1 Acceptance Report

Date: 2026-07-26

## Delivered outcome

V3.7.1 closes the V3.7 plan-to-execution gap. Explicit refresh now performs Discover → Scout → Plan → Execute → Validate → conditional Final Synthesis. New Tool Result evidence is bounded, redacted, evidence-linked and persisted only inside replaceable understanding JSON.

The release also replaces serialized-string truncation with complete-JSON context packing, adds diversity-aware discovery, signature-keyed caches, dimensional Historical Coverage, a 1000-commit low-confidence counterexample, old-snapshot compatibility and focused UI diagnostics.

## Boundary evidence

- 0/1/2 Model Gateway request policy is enforced; there is no new Provider client.
- The model requests capability names only. Fixed command arrays and safe paths stay inside Providers.
- DOC_READER and Agent/manifest reads do not persist full documents.
- Git execution reads bounded metadata/names and never patches.
- Sensitive paths are metadata-only; outbound model and persisted summaries are redacted.
- GET understanding/structure/evolution remains read-only.
- No schema, Fact rewrite, parser, vector database, watcher, daemon, Desktop shell or automatic SCIP producer was added.
- External SCIP generation remains explicitly deferred after the safe PoC review.

## Verification

| Gate | Evidence |
| --- | --- |
| Backend H2/unit | 346 tests passed, 0 failure/error, 1 explicit-path benchmark skipped |
| Focused V3.7.1 tests | execution, provider, packer, redactor, diversity, cache, coverage, compatibility passed |
| Frontend contracts | 49 passed |
| TypeScript/production build | passed |
| Playwright | first full run 7/8; the only retry-job timing failure passed an isolated rerun; GitHub full run remains blocking |
| Hermes / Obsidian | 5/5 and 18/18 passed |
| PostgreSQL 16 Testcontainers | pending GitHub Actions |
| Real-repository benchmark | five real repositories passed with 0 model requests; see product acceptance |
| Root launcher | `Start-ProjectFlow.bat -NoBrowser` rebuilt V3.7.1, started backend/frontend, passed health checks and wrote `logs/last-embedded-build.json` |
| Real Provider | SKIPPED: no safe user-owned key was supplied |

## Required acceptance answers

A. Current master final version: pending merge; the release target and working version are V3.7.1.

B. V3.7 PR final status: PR #3 is MERGED at `bda4017d066d7a858058beb6af0f0a307697396d`.

C. Adaptive Execution complete: yes. The explicit refresh now executes and validates a persisted plan before conditional synthesis.

D. Executable capabilities: DOC_READER, MANIFEST, AGENT_RESULT, GIT_HISTORY, GIT_TAG and WORKTREE. FILESYSTEM and SCIP reuse the current deterministic results.

E. Planner drives execution by passing only registry-validated `toolsToInvoke` and evidence-ID `deepReadTargets` to `AnalysisExecutionCoordinator`. Presence of Git/documents alone no longer forces all executable tools.

F. Tool Result enters Evidence only with a `tool:` ID, the expected capability and at least one already allowed reference. It joins the Source Map as `TOOL_RESULT`; invalid references are dropped.

G. Tool Result enters Final Synthesis through the context packer's `toolResults` category. The two-stage regression captures the second prompt and proves the deep-read content is present after redaction.

H. Two-stage model: conditional, never mandatory.

I. Model requests: 0 for empty/blank/no-model/unchanged paths; 1 for Scout/profile when execution adds no high-value evidence; 2 only when validated execution adds evidence worth resynthesis.

J. Context Packer: redacts JSON-tree values, allocates category minimum/maximum budgets, fits complete nodes, enforces the global ceiling, serializes once and reparses the result. It exposes selected/dropped items, characters and reasons.

K. Whole serialized-JSON truncation: removed. Text nodes may be bounded before serialization, but the final JSON remains complete and valid.

L. Evidence Diversity: guaranteed scarce-category slots, category/module caps, duplicate compression and deterministic fill under the global limit; metrics expose the result.

M. Many-docs case: manifest, CI, test and unknown-document categories all survive the cap; selected category coverage is asserted.

N. Historical Coverage: seven weighted dimensions plus bounded per-period commit, Fact and Tag evidence replace the old coarse Git-presence score.

O. 1000 commits + 0 Fact + 0 Tag: 0.25 overall because only the Git-metadata dimension contributes; it is historical availability, not semantic reconstruction.

P. Period confidence: 0.25 Git metadata for a fully scanned period, or 0.15 for a sampled period, plus up to 0.55 from Fact-linked commit ratio and 0.15 from Tag anchors, capped at 0.95. Missing document/Agent period dates stay 0 rather than being invented.

Q. Large repository benchmark: React fell from 68,512 to 1,217 ms; VS Code from 134,367 to 1,628 ms. JUnit fell from 17,180 to 1,368 ms. All warm content reads were 0; full evidence is in `projectflow-v3.7.1-product-acceptance.md`.

R. Incremental discovery: the unchanged path first uses a metadata inventory fingerprint. Deep refresh reuses root/path caches keyed by relative path, size, mtime and scanner limits; changed files reopen, removed paths evict, evidence samples have a separate signature cache, and cold fallback opens at most 1,500 ordinary source contents by default.

S. Secret detection: private keys, GitHub/AWS tokens, JWT, Bearer, credential URLs, common credential assignments and cautious entropy candidates are redacted; sensitive paths are content-denied.

T. Secret leak tests: yes, including known formats, credential URLs, replacement metacharacters, ordinary hash/example false positives and path allow/deny behavior.

U. SCIP producer PoC: production invocation remains deferred. Official Java/TypeScript/Python producers require runtime/build/environment assumptions that are unsafe to invoke silently in arbitrary projects.

V. Automatic runtime installation: no. ProjectFlow does not download Node, Python, JVM or a producer, and does not silently run a project build.

W. Real Provider acceptance: not run.

X. Real Provider status: explicitly SKIPPED because no safe user-owned key was supplied. Fixed-model tests are not reported as semantic quality.

Y. Backend tests: 346 passed, 0 failure/error; the one skipped test requires an explicit real-repository path and was separately run against all five repositories.

Z. PostgreSQL: local Docker daemon was unavailable; H2 passed and PostgreSQL 16 Testcontainers remains a blocking GitHub Actions gate.

AA. Frontend: TypeScript, production build and 49 contracts passed.

AB. Playwright: first full run passed 7/8; its only retry-job timing failure passed an isolated rerun 1/1. The GitHub full 8-test run remains the release authority.

AC. Hermes/Obsidian: 5 MCP tests and 18 projection tests passed.

AD. Product Acceptance: local behavior and launcher are PASS with one disclosed Playwright timing retry; complete GitHub CI remains the final release gate.

AE. Known risks: process-local cache rebuild after restart; LOC is an estimate without scc; redaction is not a full security scanner; Git history is sampled metadata; no real-model quality claim; no automatic SCIP production.

AF. Next phase: choose a safe real-model semantic-quality acceptance phase or Evidence-backed Evolution Reconstruction. Do not automatically enter Desktop work.

## Git and CI evidence

AG. Implementation commit: `c40b5ec8f75ff107b4a7bde5982789d813bd1d4f`

AH. Documentation commit: pending

AI. Pull request: pending

AJ. PR merge SHA: pending

AK. Final master SHA: pending

AL. Final CI run IDs/status: pending

## Known limits

Process-local caches rebuild after restart. Redaction is a model-boundary guard, not a full security scanner. Git history is intentionally metadata-only and sampled. No real model quality claim is made without a safe key. External SCIP production remains future opt-in work.
