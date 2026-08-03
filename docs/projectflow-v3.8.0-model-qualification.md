# ProjectFlow V3.8.0 项目历程模型资格

更新日期：2026-08-03

## 资格范围

模型只承担有界中文措辞改写，不决定事件、时间、主体、Before、Change、After、Story 成员、Chapter 成员、Thread、Evidence、authority 或强事实状态。

本资格测试只证明对应日期、Provider、模型、协议、Prompt 和固定样本满足结构与安全合同，不代表任意项目的通用准确率。

## Prompt 合同

- 任务类型：PROJECT_HISTORY_SYNTHESIS。
- Prompt 版本：project-history-synthesis-v2。
- 生产与 Eval 使用同一个 ProjectHistoryPromptBuilder。
- 单次刷新最多一次模型调用。
- Prompt 最多 60,000 字符。
- 只允许返回 stories 和 chapters 两个根字段。
- Story 只允许 storyId、humanTitle、oneSentenceSummary、reason、reasonEvidenceRefs、conflicts、unknowns。
- Chapter 只允许 chapterId、title、summary、storyRefs。

任何未知 ID、重复 ID、遗漏 ID、成员变化、非法 Evidence、无证据原因、额外字段或不支持的强断言都由工程层拒绝。

## 固定资格样本

样本包含两个 Story 和一个 Chapter：

1. 认证入口：允许一个 ProjectFact Evidence 支持 reason。
2. 成果导出：经历新增、删除和恢复，但没有 reason-eligible Evidence，原因必须保持空并在 unknown 中说明。
3. Chapter 的两个 storyRefs 必须原样返回。

人工规则禁止“优化了系统、改进了功能、进行了重构、提升了体验、修改了相关文件”，也禁止成熟度、关键里程碑、项目成功、下一步、路线图和未来计划。

## DeepSeek 真实运行

运行日期：2026-08-03。

| 项目 | 结果 |
| --- | --- |
| Provider | DeepSeek |
| Model | deepseek-v4-pro |
| Protocol | OPENAI_CHAT_COMPLETIONS |
| Story / Chapter | 2 / 1 |
| Prompt 字符 | 2,032 |
| 请求次数 | 1 |
| Token | 1,539 |
| 延迟 | 13,510 ms |
| Finish reason | COMPLETE |
| Schema matched | true |
| Truncated | false |
| Reasoning present | true，但原文未持久化 |

合同结果全部为 0：

- rootSchemaViolationCount
- entitySchemaViolationCount
- missingEntityCount
- duplicateEntityCount
- crossProjectReferenceCount
- invalidEvidenceRefCount
- chapterMembershipMismatchCount
- reasonWithoutEvidenceCount
- emptyReadableSummaryCount
- unsupportedClaimCount

安全产物：docs/acceptance-evidence/v3.8.0/real-model/deepseek-v380/project-history-real-model.json。

SHA-256：45CFC3B32B58DAEF844B54260D0822B860100BE8EEB79511BB156A81704B2587。

产物不包含 API Key、完整 Prompt、raw response、reasoning 或绝对路径。

## 第二 Provider 状态

2026-08-03 只发现一个已配置且可安全调用的真实 Provider：DeepSeek。没有可用的 GLM、OpenAI 或其他第二 Provider 凭证，因此没有伪造第二次真实验收，也没有把固定模型或 Mock 描述为真实 Provider。

结论：DeepSeek 已通过当前固定项目历程措辞合同；“至少两种真实 Provider”总门禁尚未满足。若用户以后配置第二 Provider，必须使用同一 Prompt builder、同一固定样本和同一结构校验重新运行，并生成独立安全产物。

## 失败与降级

- 无默认 Provider：使用确定性 Story 和 Chapter，模型状态 NOT_CONFIGURED/NOT_USED。
- Provider 调用失败、截断、Schema 错误或非法引用：拒绝模型结果，保留确定性结果，Snapshot 状态 DEGRADED。
- 模型失败不得覆盖上一次成功快照，不得修改 ProjectFact。
- Token、延迟和 reasoning presence 只作过程诊断，不作为产品质量缺陷，也不写入用户第一层。

## 当前资格结论

生产/Eval Prompt parity、结构校验、Evidence 约束和 DeepSeek 固定样本已通过。第二真实 Provider 尚缺，因此 V3.8.0 的跨 Provider 资格仍是未完成门禁，不能在最终验收中写为全通过。
