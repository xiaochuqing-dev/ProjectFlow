# ProjectFlow V3.8.5 RC2 模型可移植合同

GLM Responses 与 DeepSeek Chat Completions 使用同一份 Provider-neutral Prompt、同一最小 schema、同一语义校验和同一工程后处理。Prompt 不包含 Provider 名、评测答案或 case ID。

Story 模型只可返回 storyId、humanTitle、oneSentenceSummary、reason、reasonEvidenceRefs、unknowns。Chapter 模型只可返回 chapterId、title、summary。role、primaryStoryId、supportingChangeRefs、storyRefs、before/change/after、Evidence 归属、冲突、时间边界和当前性均由工程系统控制；未知 ID、缺失项、重复项、无 Evidence 的 reason 和禁用字段均拒绝。

模型失败只影响可替换展示文字；ProjectFact、Raw Event、Evidence、checkpoint 和上次安全结果不被模型覆盖。Prompt、raw response、reasoning、Key、Authorization 和绝对路径不持久化。没有新增 Provider 分支或依赖。

合同由 ModelPortabilityContractTest、ModelOutputMinimalSchemaTest、ProviderNeutralPromptSnapshotTest 和 ProviderNeutralSemanticValidationTest 固化。真实双 Provider 资格仍需受保护 Secrets 重跑后才能判定。
