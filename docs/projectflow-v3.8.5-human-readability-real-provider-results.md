# V3.8.5 RC3 Real Provider Results

2026-08-24 final sign-off note: all Final Chapter Provider evidence remains unchanged, and the project owner explicitly approved the final package while waiving quantitative scores. Human acceptance is `PASS_BY_EXPLICIT_OWNER_OVERRIDE`; this does not convert the blank frozen worksheets into scored artifacts or erase any failed Provider attempt.

2026-08-23 Provider superseding note: the active Final Chapter profiles are GPT 5.6 Luna `gpt-5.6-luna` / Responses/max, DeepSeek V4 Flash `deepseek-v4-flash` / Chat/max, and Qwen3.7 Plus `qwen3.7-plus` / Messages/max. Current qualification, Chapter scenario, retry and security evidence is consolidated in `projectflow-v3.8.5-final-closure-report.md`. Automated Provider success did not by itself change the then-blank human gate.

Current status: FINAL THREE-PROVIDER MATRIX PASS; HUMAN ACCEPTANCE `PASS_BY_EXPLICIT_OWNER_OVERRIDE`. Historical RC3 rerun status follows.

The RC3 acceptance profiles are GLM `glm-5.2`, Ark Coding v3, `OPENAI_RESPONSES`, max; and DeepSeek `deepseek-v4-flash`, OpenCode Go `/v1`, `OPENAI_CHAT_COMPLETIONS`, max. Credentials are injected only through GitHub Actions Secrets. No Key, Authorization value, Prompt, raw response, reasoning or machine absolute path is stored in normalized artifacts.

## Preserved pre-RC3 evidence

- GLM run `31523413972`: 19-case qualification and 11/11 scenarios passed under the earlier high profile.
- DeepSeek run `31517037532`: 19-case qualification and 11/11 scenarios passed under max.
- Correction run `31532558352`: both Providers passed the bounded correction scenario after the indexed-placeholder fix.
- Round 2 nevertheless failed human truthfulness review. Those earlier passes do not approve RC3 and are not reused for affected claim-attribution or Chapter gates.

## RC3 attempts

- Run `31571883309`, head `98d3d4812b207c781594070ef8bac253590d43a2`: DeepSeek qualification failed with one missing-reason-unknown, three Holdout Title AOR failures and one Chapter precision failure. GLM and both scenario jobs were cancelled. The run remains `completed/cancelled` and is not qualification evidence.
- Run `31574016609`, head `4935884b12ad2be0ea4ab668687d7f0aa21134d4`: DeepSeek qualification passed. GLM scored all 19 cases as passing with zero safety counters, but two Chapter repair failures caused strict qualification failure. The cause was a Provider-neutral protocol mismatch: Chapter repair referenced Story-only OUTPUT_TEMPLATE_JSON instead of CHAPTER_SYNTHESIS_JSON.
- The same run completed scenarios at 10/11 for both Providers. Their only failure was ProjectFlow Dogfood: the ae9f `project-area-frontend` Claim remained IMPLEMENTED. DeepSeek used 64 physical requests, 1,030,298 tokens, 2,453,365 ms model latency and 3 repairs; GLM used 53 requests, 808,194 tokens, 3,575,463 ms model latency and 2 repairs. Both artifacts report all sensitive-persistence flags false.

## Final candidate run

Run `31580355605` uses head `92053e58b7ead35ffc84cc4db7eeea6bda76e17c`, Chapter prompt v6 and the broad-area OBSERVED ceiling. DeepSeek qualification passed with 38 requests, 158,776 tokens, 670,331 ms elapsed and one successful repair. GLM qualification failed only the strict title action/object/result gate: 41 requests, 161,970 tokens, 1,305,646 ms elapsed, five successful repairs, zero repair failures, and failures in `holdout-rename-move-split-merge` (0.0000) and `holdout-unrelated-commit` (0.3333). It had zero failed model windows and every sensitive-persistence flag was false. The exact weak pairs named actions and objects but did not say what result had formed.

The same run's DeepSeek scenario job completed at 10/11: Dogfood and all five non-code scenarios passed, while `schema-failure-isolation-and-retry` reported that an independent window did not continue after the injected local schema failure. It used 55 physical requests, 955,811 tokens, 2,059,855 ms model latency and three repairs; every sensitive-persistence flag was false. GLM scenarios passed 11/11 with 54 requests, 842,640 tokens, 3,287,611 ms model latency and three repairs; every sensitive-persistence flag was also false. The DeepSeek failure remains evidence and GLM scenario success does not rescue the failed GLM qualification.

Production head `539dfc9802069dec40207179f65b873bf862872c` adds a Provider-neutral action/object/result boundary. Otherwise safe weak pairs retain the already validated deterministic title/summary and are exposed as `MODEL_VALIDATED_WITH_DETERMINISTIC_TITLE`; qualification and scenario artifacts aggregate `deterministicTitleFallbackCount`. Story prompt is v12 and artifact schemas are history output v4 / scenarios v3. This is not a Provider-specific phrase exception and does not lower the frozen evaluator threshold.

Final run `31586433372` uses validation head `b9e9c2d`, affected scope and both max-reasoning profiles. Both qualifications passed 19/19 with Title AOR 1.0, Chapter precision 1.0, zero failed/pending windows, zero repair failures and every sensitive-persistence flag false. DeepSeek used 39 requests, 180,073 tokens and 843,690 ms, with three validation repairs and three disclosed deterministic-title fallbacks. GLM used 43 requests, 166,130 tokens and 1,057,851 ms, with seven validation repairs and nine disclosed deterministic-title fallbacks.

DeepSeek scenarios passed 11/11 in attempt 1: 60 physical requests, 1,023,867 tokens, 2,510,634 ms model latency, three validation repairs and 129 disclosed deterministic-title fallbacks. All five non-code cases, 17-window continuation/Chapter synthesis, correction, schema-failure isolation/retry, cancellation/restart, raw-payload minimization and ProjectFlow Dogfood passed. The Dogfood P0 remained OBSERVED and did not claim login implementation. Every artifact security flag was false; raw scanning found no credential, Authorization, raw payload or machine absolute path. The normalized artifact SHA-256 is `639a24891a0917b4a9498c262664ceb457caa8fc2111e40db98ec1454c357d27`.

The first GLM scenario attempt in the same run is retained as FAIL 1/11. `non-code-presentation` passed with one physical request, 10,012 tokens, 66,455 ms model latency and two disclosed deterministic-title fallbacks. Every later scenario recorded zero successful model calls, so research/data/brand/no-Git, continuation, correction, injected failure recovery, cancellation, raw-payload and Dogfood could not qualify. The isolated job retry on the same `b9e9c2d` SHA and exact profile is separately retained as FAIL 0/11 with zero successful requests. Neither sanitized artifact preserves an HTTP status, so the exact external rejection cannot be claimed; the evidence supports only “Provider calls became unavailable.” All artifact security flags remained false. A correction-only diagnostic will expose only a safe failure category and request count; it will not erase either failed attempt.

Correction-only run `31592405476` on diagnostic head `f3d520432a0be857cd21255051c796b28359fbfb` failed both GLM Story windows with safe category `HTTP 429` after two bounded requests each. The log contains no response body, Prompt, credential or absolute path. This proves the immediate failure was external HTTP availability rather than a local schema mismatch; the safe evidence does not distinguish throttling from exhausted allocation. Browser, frontend, Hermes, Obsidian and sensitive-content jobs passed; backend/H2 and PostgreSQL failed only because the deliberately pending Round 3 manifest does not yet exist.

Job-only attempt 2 after about five minutes and attempt 3 after the longer deterministic-test cooldown both repeated the same two `HTTP 429` classifications, two bounded requests per window and zero successful calls. The full GLM suite is paused until the Actions Secret has usable external capacity; a later correction probe must pass before an 11-scenario retry is justified.

No Provider-specific business rule or quality-threshold reduction is present. Later success will not remove any failure, cancellation, fallback, request, token or latency evidence above.
