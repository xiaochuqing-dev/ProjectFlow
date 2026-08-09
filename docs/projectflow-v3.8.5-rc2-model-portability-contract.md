# ProjectFlow V3.8.5 RC2 模型可移植合同

GLM Responses 与 DeepSeek Chat Completions 使用同一份 Provider-neutral Prompt、同一最小 schema、同一语义校验和同一工程后处理。Prompt 不包含 Provider 名、评测答案或 case ID。

Story 模型只可返回 storyId、humanTitle、oneSentenceSummary、reason、reasonEvidenceRefs、unknowns。Chapter 模型只可返回 chapterId、title、summary。role、primaryStoryId、supportingChangeRefs、storyRefs、before/change/after、Evidence 归属、冲突、时间边界和当前性均由工程系统控制；未知 ID、缺失项、重复项、无 Evidence 的 reason 和禁用字段均拒绝。

Prompt v8 对首次未通过统一语义校验的整窗输出只允许一次安全重生成。修复请求只包含原始有界输入和安全失败类别，不携带上一次 raw response；第二次仍失败时拒绝该窗并保留 checkpoint，不过滤非法引用、不接纳部分非法输出，也不按模型名称分支。

reasoning-controlled 结构化请求保持配置的思考强度。系统提示要求先结束 reasoning，再在可见 content 中输出完整 JSON；首次出现“只有 reasoning、可见 content 为空”时，只允许在同一有界输入上执行一次 `EMPTY_AFTER_REASONING_RETRY`。第二次仍为空即以 `REASONING_EXHAUSTED_OUTPUT` 失败，绝不发第三次语义请求，也不复用或持久化 reasoning。DeepSeek 最终配置保持 `reasoning_effort=max`。

模型失败只影响可替换展示文字；ProjectFact、Raw Event、Evidence、checkpoint 和上次安全结果不被覆盖。Prompt、raw response、reasoning、Key、Authorization 和绝对路径不持久化。没有新增 Provider 分支或依赖。

合同由 ModelPortabilityContractTest、ModelOutputMinimalSchemaTest、ProviderNeutralPromptSnapshotTest、ProviderNeutralSemanticValidationTest、ModelGatewayServiceTest 和 ProjectHistoryReconstructionTest 固化。workflow `31318477841` 已在 GLM `glm-5.2` Responses/high 与 DeepSeek `deepseek-v4-flash` Chat/max 上完成真实验证：两者 Understanding 17/17、19-case qualified、最终场景 11/11。
