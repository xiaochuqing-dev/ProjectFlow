# ProjectFlow V3.8.5 RC2 模型可移植合同

GLM Responses 与 DeepSeek Chat Completions 使用同一份 Provider-neutral Prompt、同一最小 schema、同一语义校验和同一工程后处理。Prompt 不包含 Provider 名、评测答案或 case ID。

Story 模型只可返回 storyId、humanTitle、oneSentenceSummary、reason、reasonEvidenceRefs、unknowns。Chapter 模型只可返回 chapterId、title、summary。role、primaryStoryId、supportingChangeRefs、storyRefs、before/change/after、Evidence 归属、冲突、时间边界和当前性均由工程系统控制；未知 ID、缺失项、重复项、无 Evidence 的 reason 和禁用字段均拒绝。

Prompt v8 对首次未通过上述统一语义校验的整窗输出只允许一次 Provider-neutral 安全重生成。修复请求只包含原始有界输入和安全失败类别，不携带上一次 raw response；修复输出必须再次通过完全相同的 ID、字段、Evidence 和 Strong Fact 校验。第二次仍失败时拒绝该窗、保留 checkpoint 并记录修复次数与失败次数，不过滤非法引用、不接纳部分非法输出，也不按模型名称分支。

模型失败只影响可替换展示文字；ProjectFact、Raw Event、Evidence、checkpoint 和上次安全结果不被模型覆盖。Prompt、raw response、reasoning、Key、Authorization 和绝对路径不持久化。没有新增 Provider 分支或依赖。

合同由 ModelPortabilityContractTest、ModelOutputMinimalSchemaTest、ProviderNeutralPromptSnapshotTest、ProviderNeutralSemanticValidationTest 和 ProjectHistoryReconstructionTest 固化。真实双 Provider 资格仍需受保护 Secrets 重跑后才能判定。
