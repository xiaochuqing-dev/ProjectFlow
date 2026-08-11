# V3.8.5 Human Readability Real Provider Results

Current status: AUTOMATED_PROVIDER_EVIDENCE_COMPLETE / PENDING_HUMAN_REVIEW. This is not V3.8.5 PASS.

Repository Secrets were injected only by GitHub Actions. No credential value is stored in code, commits, reports or normalized artifacts. The exact profiles were GLM `glm-5.2`, `https://ark.cn-beijing.volces.com/api/coding/v3`, `OPENAI_RESPONSES`, high; and DeepSeek `deepseek-v4-flash`, `https://opencode.ai/zen/go/v1`, `OPENAI_CHAT_COMPLETIONS`, max. V4 Pro was not used.

Full qualified baselines were produced before the indexed-placeholder defect was detected. They remain immutable evidence and are reused only for unaffected qualification, Dogfood and non-code strata:

- GLM run [`31523413972`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31523413972): 19-case qualification qualified with 40 requests, 156,848 tokens, 6 validation repairs and 0 repair failures; real scenarios 11/11 with 52 requests, 884,266 tokens and 1 repair. ProjectFlow Dogfood and five non-code scenarios passed.
- DeepSeek run [`31517037532`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31517037532): 19-case qualification qualified with 35 requests, 133,653 tokens, 1 validation repair and 0 repair failures; real scenarios 11/11 with 59 requests, 945,342 tokens and 2 repairs. ProjectFlow Dogfood and five non-code scenarios passed.

The first Round 2 freeze exposed an indexed placeholder such as `主题00000内容000` in the correction sample. It was not accepted. The provider-neutral fix normalizes that first-layer subject, rejects it in entailment and quality evaluation, and keeps stable internal subjects for window planning.

Affected correction rerun [`31532558352`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31532558352), code head `aee0160cf1d4cf11224055548107098fd12e6de1`:

- GLM: PASS, 1/1 scenario, 3 real Story requests, 68,769 tokens, 273,301 ms model latency, 0 repairs.
- DeepSeek Flash: PASS, 1/1 scenario, 3 real Story requests, 59,386 tokens, 160,766 ms model latency, 0 repairs.
- Both retained 64 Story and 2 windows, invalidated only the corrected window, reached a final cache hit, and reported indexed-placeholder leakage count 0.
- Both normalized artifacts report Key, Prompt, raw response, reasoning and machine absolute path persistence as false.

Historical fluctuations remain evidence: run `31468663795` had DeepSeek scenarios 9/11, while GLM scenarios were 11/11; run `31517037532` also contained an earlier GLM qualification failure before the later GLM-only qualification passed. Later success does not erase those outcomes.

No Provider-specific business rule was added and no Evidence, Strong Fact, ID, role-graph or security gate was lowered. Human readability is still decided only by the frozen Round 2 worksheet.
