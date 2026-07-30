# ProjectFlow V3.7.4 Two-model Compatibility

## Shared contract

Both acceptance profiles use `ProjectUnderstandingPromptBuilder`, Strong Fact contract v2, Semantic Scout v11, Final Synthesis v6, the same Evidence allow-list, capability/view registries, schema adapter, promotion guard, cancellation and bounded repair. There is no Provider-specific fact prompt and model agreement has no promotion authority.

## Profiles

| Role | Provider/model | Protocol | Result boundary |
| --- | --- | --- | --- |
| Main acceptance | Volcano Ark Coding / GLM `glm-5.2` | OpenAI Responses | connectivity passed; formal results recorded in the model report |
| Compatibility | DeepSeek official / `deepseek-v4-pro` | OpenAI Chat Completions | connectivity passed; formal results recorded in the model report |

The DeepSeek V4 Pro profile explicitly declares `supportsReasoning=true`. This is a Provider capability, not a model-name branch: ProjectFlow reserves the task's bounded useful output ceiling and omits temperature, while sending no private reasoning-control field. The first nine-case diagnostic run used an incomplete capability profile and produced one truncated schema repair; the corrected profile completed the same nine cases 9/9. Both artifacts are retained.

DeepSeek official documentation dated at acceptance time lists `https://api.deepseek.com` as the OpenAI base URL and `deepseek-v4-pro` as the current Pro model. Ark Coding uses the user-supplied OpenAI-compatible Coding v3 base. Sources:

- https://api-docs.deepseek.com/guides/function_calling/
- https://api-docs.deepseek.com/zh-cn/quick_start/pricing
- https://www.volcengine.com/docs/82379/1795150

## Security and interpretation

Keys are injected into one Maven process through a masked secure prompt, cleared when it exits, and never written to Provider settings, shell scripts, environment files, reports or Git. Artifacts contain normalized observations and aggregate diagnostics only. Raw responses, reasoning, prompts and Authorization values are not archived.

Connectivity proves only transport and structured protocol compatibility. Calibration, original regression, frozen Holdout and product-chain E2E decide bounded qualification. ProjectFlow does not call both models by default, compare them in product UI, rank them, or use one model to validate the other as factual evidence.
