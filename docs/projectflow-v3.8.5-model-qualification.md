# V3.8.5 RC3 Model Qualification

Current status: both Providers passed 19/19 qualification on `b9e9c2d`, and DeepSeek passed scenarios 11/11. GLM scenarios remain blocked by repeated external `HTTP 429`; V3.8.5 remains NOT PASS.

Models are called only through `ModelGatewayService`. Engineering owns IDs, Claim subject/action/state, direct and indirect Evidence, roles, Chapter membership, chronology, corrections and fallback. Story prompt v12 and Chapter prompt v6 are Provider-neutral. A safe but incomplete model title/summary pair retains the deterministic pair and records `modelDeterministicTitleFallbackCount`; this count is diagnostic and does not hide the mixed origin. A rejected output receives at most one regeneration from the matching original bounded input; rejected raw output is never repeated.

Formal RC3 profiles:

| Provider | Model | Protocol | Reasoning |
| --- | --- | --- | --- |
| GLM | glm-5.2 | OPENAI_RESPONSES | max |
| DeepSeek | deepseek-v4-flash | OPENAI_CHAT_COMPLETIONS | max |

Qualification requires all frozen Calibration/Holdout cases, safety counters, repair outcomes and affected scenarios to pass. Good aggregate wording scores cannot override a failed window, failed Chapter repair or Dogfood P0.

Run history:

- `31571883309`: incomplete/cancelled after DeepSeek qualification failed.
- `31574016609`: DeepSeek qualification passed; GLM qualification failed because two Chapter repairs used the wrong Story schema. Both Providers then reached 10/11 scenarios and failed the same deterministic Dogfood area-level P0.
- `31580355605`: on head `92053e58b7ead35ffc84cc4db7eeea6bda76e17c`, DeepSeek qualification passed with 38 requests, 158,776 tokens, 670,331 ms elapsed and one successful repair. GLM used 41 requests, 161,970 tokens, 1,305,646 ms elapsed and five successful repairs, but failed strict Holdout Title AOR in `holdout-rename-move-split-merge` (0.0000) and `holdout-unrelated-commit` (0.3333). All safety persistence flags were false. DeepSeek scenarios then reached 10/11: Dogfood and non-code passed, while the injected schema-failure isolation case did not continue an independent window after Provider processing variance. Scenario results remain independent evidence and do not rescue qualification.
- Production head `539dfc9802069dec40207179f65b873bf862872c` adds the Provider-neutral title/result boundary, deterministic pair retention, explicit mixed-origin status/count and Story prompt v12. In run `31586433372` from validation descendant `b9e9c2d`, both max-reasoning Providers passed 19/19: DeepSeek 39 requests/180,073 tokens/843,690 ms/3 repairs/3 disclosed title fallbacks; GLM 43 requests/166,130 tokens/1,057,851 ms/7 repairs/9 disclosed title fallbacks. Both had Title AOR and Chapter precision 1.0, zero failed/pending windows, zero repair failures and all safety persistence flags false. DeepSeek scenarios passed 11/11 with 60 requests, 1,023,867 tokens, 2,510,634 ms, three repairs and 129 disclosed title fallbacks. GLM scenario attempt 1 ended 1/11 after the first success; the isolated job retry ended 0/11 with zero successful requests. Neither sanitized artifact contained an HTTP status, so a minimal safe diagnostic was added before considering another full retry.
- Correction-only diagnostic run `31592405476` classified both failed GLM Story windows as `HTTP 429` after two bounded requests each. It emitted no response body, prompt, credential or path. This identifies an external availability/rate boundary rather than a local schema parser failure, but the safe evidence cannot distinguish temporary throttling from exhausted allocation.
- Job-only attempts 2 and 3 after separate cooldowns produced the same two `HTTP 429` classifications and zero successful model calls. The full GLM scenario suite remains paused until external capacity recovers and a correction probe succeeds; every repeated failure remains retained if the Provider later recovers.

Fixed-model and deterministic tests prove contracts and fallback only. They never substitute for these real Provider results. Human Round 3 remains a separate gate even if both Providers qualify.
