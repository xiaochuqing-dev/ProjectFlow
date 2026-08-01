# ProjectFlow V3.7.5 Acceptance Report

Report date: 2026-08-01

Current verdict: PASS. Local engineering, both frozen real-model gates, branch CI, pull-request CI, merge and final functional master CI all pass, including PostgreSQL 16 Testcontainers. V3.8 Entry is approved under the bounded V3.7.5 constitution and evidence scope.

A. Version: `3.7.5`.

B. Baseline master: `78e57ffdcc6d0bac952de6ca48b58cb08a60def5`.

C. Working branch: `codex/v3.7.5-cross-model-closure`.

D. Final functional master SHA: `2d54d91d67b89190ab8b2ac3f5a92345dd61cd9c`.

E. PR / merge commit: PR `#11`; merge commit `2d54d91d67b89190ab8b2ac3f5a92345dd61cd9c`.

F. Final CI: final feature-head push Run `30701956764` PASS, pull-request Run `30701958206` PASS at `798d4cb7e9c1507b25347fb61e5b5a4a089bcf3c`, and functional master Run `30702083514` PASS at `2d54d91d67b89190ab8b2ac3f5a92345dd61cd9c`. The earlier implementation runs `30694430873`, `30701738449` and `30701772038` also passed.

G. Product constitution: `docs/projectflow-v3.7.5-product-constitution.md` is authoritative. Strong facts require project-bound `OBSERVED` or independently checked `VERIFIED`; `DECLARED`, `INFERRED`, `CONFLICTED`, `UNKNOWN` and `PROCESS_EVIDENCE` never promote through model, Agent, fallback or consensus.

H. Prompt and freeze: Prompt contract v3; Semantic Scout v13; Final Synthesis v7. Scout hash `806AA0110390095F8961FB8ABEFC96422DCB576B617261153F7393DF0569777D`; Final hash `14574307A878B46B2B99689E993522B9C01DEE9EE594C4616988D1302687C136`. Freeze manifests: `8EB8E1D5B8C15C09FBEBB30B30FFE7E6D0389BA7D2226E10AEDDEA1023D0E601` and `5107B639D0BF7C4D1052305AAA8767AA662B66DDFC43AB85E0CBE08827BD3590`.

I. Quality mode: `QUALITY_FIRST`. Time, Token, request count and cost are process diagnostics, not quality defects. Qualified Responses/Chat profiles use high for connection, semantic and recovery requests; reasoning-capable tasks may use the configured loose Provider ceiling from the first request.

J. GLM profile/result: Volcano Ark Coding / `glm-5.2` / `OPENAI_RESPONSES`; Holdout 8/8, zero failure/Schema/degradation, Critical Recall 1.0000, Deep-read 1.0000, Conflict 1.0000, Unsupported 0, 11 requests and 93,000 Token. Product E2E 8/8, 16 logical/17 physical requests, 265,679 Token, one disclosed Final degradation, invalid Evidence 0 and readback 8/8.

K. DeepSeek profile/result: official / `deepseek-v4-flash` / `OPENAI_CHAT_COMPLETIONS`, explicit JSON Mode and high reasoning. First formal Holdout is preserved: 3/8 completed, five `REASONING_EXHAUSTED_OUTPUT`, 15 requests and 102,379 Token. Freeze 2 passed 8/8 with zero failure/Schema/degradation, Critical Recall 1.0000, Deep-read 1.0000, Conflict 1.0000, Tool Recall 1.0000, Unsupported 0, 15 requests and 143,652 Token. Product E2E passed 8/8, 15 logical/22 physical requests, 299,575 Token, zero degradation, invalid Evidence 0 and readback 8/8.

L. Model limitations: GLM final Tool precision/recall 0.7500/0.7500 and Dynamic View recall 0.2727; DeepSeek final Dynamic View recall 0.2727. These are disclosed bounded-model limitations, not strong-fact or promotion failures.

M. Structured output/fallback: DeepSeek explicit JSON Mode constrains final structure only and did not lower reasoning, time allowance or Evidence. GLM E2E retained one current `FAILED_DEGRADED` Stage-1 result with validated Evidence. Historical first failures remain visible.

N. Context Package and Agent boundary: Context Package v2 is task/scope/revision/depth/budget aware, deterministic and model-free; it preserves source ranges, currentness, conflicts, unknowns, limitations and unread scope. Candidate Work Result re-reads safe changed files, binds hashes and rejects direct `OBSERVED`/`VERIFIED` before persistence. Local revalidation uses only registered bounded actions and never mutates ProjectFact.

O. Existing Coverage Matrix: complete and updated with deterministic, real-model, product E2E, PostgreSQL 16 and current GitHub CI evidence.

P. Real-project gap validation: three materially different local evidence structures were scanned with bounded deterministic discovery. Two stable projects passed repeat fingerprints; one live project correctly exposed source mutation/currentness. No redundant real-model request was used where engineering evidence already decided the gap.

Q. Open-source reuse: no dependency or copied third-party code was added. Existing JDK/Jackson, path guard, Content Map, fixed Git executor, Gateway, Jakarta Validation and repository-local MCP boundaries were reused. No GPL/AGPL source was incorporated.

R. Local verification: backend/H2 453 tests, 0 failures, 0 errors, 2 skipped real-Provider tests; frontend TypeScript PASS, contracts 50/50, build PASS; Playwright 8/8; Hermes 7/7 and 13 tools; Obsidian 18/18; sensitive scan PASS; embedded launcher build/health/stop PASS.

S. PostgreSQL: local Docker Desktop daemon was unavailable. PostgreSQL 16 Testcontainers passed in branch Run `30701738449` and pull-request Run `30701772038`; H2 was not used as a substitute.

T. Dependency audit: three existing high findings remain. The automated fix would destructively downgrade Next to 9.3.3 and was not applied without a compatible upgrade path.

U. Credentials/artifacts: keys existed only in model-test processes. GitHub receives only freeze manifests, safe aggregate/case metadata and SHA-256 references. It receives no Key, Authorization, prompt, raw response, reasoning, full document, patch or absolute path.

V. Foundation Gate: PASS.

W. V3.8 Entry: PASS for Evidence-backed Evolution Reconstruction under the locked constitution; this does not authorize automatic phase, importance, maturity or milestone truth.

X. Tag: NO.

Y. Release: NO.
