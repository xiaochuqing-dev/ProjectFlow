# ProjectFlow V3.10 Final Release Closure Report

Recorded at: 2026-08-29T16:08:47+08:00

## Status at feature evidence freeze

V3.8.5 History Quality and V3.9 Project Continuity remain FINAL. V3.10 implementation evidence is complete with P0=0 and P1=0, but formal FINAL remains gated on PR #19 Ready/merge, merge-after master CI, acceptance backfill and cleanup. V4.0 Entry therefore remains PENDING at this report checkpoint.

NO TAG. NO GITHUB RELEASE. V4 GUI was not started.

## Source and synchronization

The task-book audit baseline matched GitHub: master `dd5ee41b6afcbd7703fa0883dc115c11f4821447`, Draft PR #19 and remote feature Head `f85b7d4ceada7d053a0bbe0d8b216eab32795e63`. Work continued in a separate clean tracking worktree so the original worktree and its user-owned untracked files were not changed.

The final behavior-bearing implementation evidence Head is `3c3dac91160b23f52e4ea680e423e3886dbda8ed`. The following commit only synchronizes closure documentation and workflow dispatch cost; its exact final PR Head is intentionally recorded after merge in the acceptance backfill rather than fabricated inside a self-referential source file.

## Changes limited to the closure gaps

The required quality workflow now builds the exact final V3.9 application in a temporary detached worktree and runs a non-skippable two-test real-old-version upgrade proof. A `credential-smoke` dispatch scope reuses the existing `ProjectFlowRealProviderProbeIT`; it runs one minimal structured request per Provider and skips the historical semantic matrix. Credential-smoke-only dispatches also skip unrelated ordinary jobs; push and pull-request events continue to run all required gates.

No History, Continuity, Fact, migration, schema detector, Secret Store, Provider Gateway, backup/restore or portable-runtime architecture was duplicated or redesigned.

## Real V3.9 upgrade proof

Both legacy databases are created by running the exact V3.9 final application at `dd5ee41b6afcbd7703fa0883dc115c11f4821447`; `V1__v39_schema_baseline.sql` does not create either legacy input.

- H2: PASS. The actual old schema is exactly `KNOWN_V39`; representative Project, Memory, Fact, History Event/Snapshot/Correction, Agent Candidate and Provider records survive H2 pre-upgrade backup, controlled V1 baseline, V2, secure credential migration and two current-version starts. Backup manifest/checksum is valid and no duplicate upgrade backup appears on restart.
- PostgreSQL 16: PASS. The actual old schema is exactly `KNOWN_V39`; a real `pg_dump -Fc` is checksummed and restored into an isolated database. Migration without explicit acknowledgement fails with `BACKUP_REQUIRED` and changes neither schema nor protected records. Confirmed migration applies controlled V1/V2, clears legacy plaintext after secure-store verification and remains stable on restart.
- The real schemas matched the frozen signature exactly. No wildcard, ignored extra object, relaxed UNKNOWN classification or migration rewrite was added.

Local required proof: 2 tests, 0 failures, 0 errors, 0 skipped. Required remote proof passed in quality run `33242125619` and again in credential run `33242290300`.

## Secure Provider credential-path smoke

Run `33242290300` is bound to implementation Head `3c3dac91160b23f52e4ea680e423e3886dbda8ed` and passed all three existing secure probes:

- GPT-5.6 Luna, OpenAI Responses: PASS.
- DeepSeek V4 Flash, Chat Completions: PASS.
- Qwen3.7 Plus, Anthropic Messages: PASS.

Each probe follows in-memory secure-store write/read verification, opaque `secretRef`, `ModelGatewayService`, the registered protocol adapter and a real small structured response. The existing repository Secret was reused. No key, Authorization, raw response or reasoning was persisted in the repository, docs, Agent results or test artifacts.

## Verification

- Focused migration, signature, backup, credential, DPAPI, Gateway boundary and runtime preflight tests: PASS.
- Release script contracts: PASS.
- Local PostgreSQL profile: backend unit suite 717 tests with 0 failures/errors; integration suite 13 tests with 0 failures/errors.
- Required Linux quality run `33242125619`: PASS, including Backend/H2, PostgreSQL Testcontainers, frontend lint/build/contracts, Playwright, Hermes, Obsidian, sensitive-content, npm production High/Critical and OSV locked-dependency gates.
- Windows portable run `33242125279`: PASS, including portable build, clean unpack, bundled runtime, start/stop/restart, DPAPI, migration, H2 backup/restore, port conflict, install-tree immutability and sensitive-marker scan.

## Failure history and review

Remote run `33242086857` failed before creating jobs because the first workflow draft referenced runner temporary state at job-expression time. The run is retained. The fix resolves the path inside the execution step and the subsequent required runs pass. Two obsolete Windows runs for the superseded Head were cancelled after the corrected Head was pushed; they remain visible. Two early local proof-harness attempts exposed an H2 file-lock orchestration mistake, an exception-code assertion issue and incomplete Timeline fixture fields; all were corrected before the final proof, without changing production migration behavior.

The focused 12-boundary review found no unresolved P0 or P1. UNKNOWN/partial schema remains fail-closed; Flyway remains the only release schema owner; credential DB/store failures preserve the safe prior state; Windows has no plaintext credential fallback; local release remains loopback-only; external mode remains fail-closed; install/data separation, source-independent portable runtime, conservative backup/restore and V3.8.5/V3.9 continuity behavior remain covered.

## Remaining contractual risks

There is no DOWN migration. PostgreSQL backup custody and restore drills remain an operator responsibility. DPAPI credentials are bound to the current Windows user and machine. Final product GUI, installer experience, short IDs, product-language polish and updater work remain V4.0 scope.

At this checkpoint PR #19 is still Draft. Ready, merge, master checks, acceptance backfill, branch/worktree cleanup and final V4 Entry approval must use their later real GitHub facts; none is claimed early here.
