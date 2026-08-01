# ProjectFlow V3.7.5 Current State Audit

Audit date: 2026-08-01

## Repository state at phase start

| Item | Observed state |
| --- | --- |
| Repository | `xiaochuqing-dev/ProjectFlow` |
| Default branch | `master` |
| Latest master | `78e57ffdcc6d0bac952de6ca48b58cb08a60def5` |
| Latest merged PR | #10, V3.7.4 acceptance metadata backfill |
| Functional V3.7.4 PR | #9 |
| Version before this phase | Backend/frontend/Hermes `3.7.4` |
| Latest master CI | Run 30504805160, success |
| Local execution branch | `codex/v3.7.5-cross-model-closure` from latest master |
| Unmerged remote branches at start | None |
| GitHub access | Authenticated account has admin, push and workflow permission |
| Tags | None |
| Releases | None |

The original user workspace had unrelated local changes, so V3.7.5 work was isolated in a separate worktree. The baseline commit was verified before implementation.

## V3.7.4 capabilities already complete

- Seven-state Strong Fact foundation with `OBSERVED` and `VERIFIED` as the only strong states.
- Content-first discovery for normal, oddly named and extensionless text.
- Bounded large-file Content Map and head/middle/tail/targeted ranges.
- Project-isolated portfolio, history, Evidence, knowledge and Context Package reads.
- Candidate-only Agent writes and read-only Hermes delivery.
- Shared production/Eval Prompt Builder and provider-neutral Model Gateway.
- Large-file, authorization, secret-redaction, H2, PostgreSQL CI, frontend, Playwright, Hermes and Obsidian engineering gates.

## V3.7.4 acceptance that passed

- GLM Calibration 21/21 and DeepSeek Calibration 21/21.
- GLM original V3.7.3 regression 38/38.
- GLM product E2E 8/8 checks, with one disclosed Final fallback.
- Backend/H2 432 tests, frontend 50 contracts, Playwright 8, Hermes 6 and Obsidian 18 in the recorded V3.7.4 acceptance.
- Final V3.7.4 PR, pull-request and master CI all passed, including PostgreSQL 16 Testcontainers.
- Invalid Evidence, committed-secret, cross-project and direct Agent strong-fact boundaries remained zero in the recorded gates.

## V3.7.4 acceptance that ran but did not pass

- DeepSeek Frozen Holdout completed structurally, but Critical Evidence Recall was 0.8182, Deep-read Sufficiency 0.6667 and Conflict Detection 0.
- GLM Frozen Holdout completed 8/8 but had one Schema failure and one explicit degradation.
- DeepSeek supplementary regression showed weaker Tool recall, deep-read and conflict behavior than GLM.

## External-account acceptance not completed

DeepSeek product E2E could not run after the V3.7.4 Holdout because the official API returned HTTP 402. This was an execution blocker, not passing evidence. V3.7.5 must run the official second-model product chain if the supplied account permits it.

## Blocking debt entering V3.7.5

- Small Evidence sets could be silently omitted by the model.
- Eligible Capability decisions were not guaranteed to be complete and exact.
- DeepSeek missed Agent/CI conflict and required history/Agent deep reads.
- GLM Structured Output had a long-tail fallback case.
- Context Package v1 lacked task, scope, ranges, trust tiers, unread scope and deterministic package revision.
- Agent work-result batching and local Evidence revalidation were incomplete.
- The product constitution was distributed across code and version reports rather than one authoritative document.

## Non-blocking engineering debt

- Hibernate `ddl-auto=update` remains the migration mechanism; Flyway/Liquibase is not installed.
- Local Docker Desktop was unavailable during this audit, so PostgreSQL must be verified by GitHub CI.
- `npm audit` reports three existing high-severity dependency findings. The automated fix proposes a destructive Next downgrade and is not safe to apply blindly.
- The full project-life reconstruction and final GUI remain intentionally deferred.

## Snapshot differences verified

The task snapshot named V3.7.4 master `78e57ff...`; GitHub and local origin matched it. The latest repository PR is #10, while #9 remains the functional V3.7.4 delivery. The workflow name was still V3.7.4 at phase start. No Tag or Release existed.

## V3.7.5 actual scope

- Common Prompt/Evidence/Capability contract fixes for both real models.
- Explicit semantic-contract diagnostics and safe degradation.
- Product-constitution status and timeline authority boundaries.
- Agent Context Package v2, Candidate Work Result and bounded local revalidation.
- Deterministic regression, controlled real-project evidence-structure checks, two-model Holdout and product E2E.
- Version, protocol, architecture, API, provider, testing, CI and acceptance documentation.
- PR, blocking CI, merge, final master verification, with no Tag or Release.
