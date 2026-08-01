# ProjectFlow V3.7.5 Product Acceptance

Current status: LOCAL GATES PASSED; FINAL GITHUB CLOSURE PENDING

## Product contract

| Check | Result |
| --- | --- |
| Product constitution has one authoritative source | PASS |
| Only project-bound OBSERVED and independently VERIFIED can become strong facts | PASS |
| Model/Agent/fallback/consensus cannot promote facts | PASS |
| Conflict, UNKNOWN, currentness, limitations and unread scope remain visible | PASS |
| Raw events are not deleted by importance, folding or summary | PASS |
| User phase/milestone is DECLARED; model phase/importance is INFERRED | PASS |
| Dynamic views omit inapplicable empty sections | PASS |
| Candidate Write rejects direct OBSERVED/VERIFIED before persistence | PASS |
| Context Package v2 is task-related, revision-aware and project-isolated | PASS |
| Hermes remains read-only; Obsidian remains a projection | PASS |

## Quality-first execution

Elapsed time, Token usage, request count and cost are process diagnostics. They are not quality defects and did not reduce Evidence, deep reads, Final Synthesis or reasoning effort. Explicitly supported Responses and Chat requests use high for connection, semantic and recovery calls. Reasoning-capable tasks may use the configured Provider ceiling from the first request; the ceiling is a loose safety boundary, not a consumption target.

## Model qualification

- GLM `glm-5.2` Frozen Holdout: 8/8, zero failure, zero Schema failure, zero degradation; Critical Recall, Deep-read and Conflict all 1.0000.
- DeepSeek `deepseek-v4-flash` first formal Holdout: preserved failure, 5/8 `REASONING_EXHAUSTED_OUTPUT`.
- DeepSeek Freeze 2 with explicit JSON Mode and unchanged high reasoning: 8/8, zero failure, zero Schema failure, zero degradation; Critical Recall, Deep-read and Conflict all 1.0000.
- GLM product E2E: 8/8, one disclosed `FAILED_DEGRADED`, persistence/readback 8/8, invalid Evidence 0.
- DeepSeek product E2E: 8/8, zero degradation, persistence/readback 8/8, invalid Evidence 0.
- Tool/View limitations remain disclosed in the model-qualification report.

## Engineering verification observed locally

| Gate | Result |
| --- | --- |
| Backend/H2 | 453 tests, 0 failures, 0 errors, 2 real-Provider tests skipped |
| Frontend | TypeScript PASS; contracts 50/50; production build PASS |
| Playwright | 8/8 PASS |
| Hermes | 7/7 PASS; 13 tools |
| Obsidian | 18/18 PASS |
| Sensitive-content scan | PASS |
| Embedded launcher | Build, frontend/backend health and normal stop PASS |
| PostgreSQL Testcontainers | Local Docker unavailable; required PR CI gate pending |
| Dependency audit | Existing 3 high findings; automated fix would destructively downgrade Next and was not applied |

## Remaining external closure

Final PASS requires the branch and pull-request quality gates, PostgreSQL Testcontainers, merge to master, final master verification and acceptance metadata backfill. Until those complete, Foundation Gate and V3.8 Entry remain pending rather than being textually promoted.

No Tag and no GitHub Release are permitted for V3.7.5.
