# ProjectFlow V3.8.0 项目历程模型资格

更新日期：2026-08-04

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

## GLM 真实运行

运行日期：2026-08-03。

| 项目 | 结果 |
| --- | --- |
| Provider | GLM |
| Model | glm-5.2 |
| Protocol | OPENAI_RESPONSES |
| Story / Chapter | 2 / 1 |
| Prompt 字符 | 2,032 |
| 请求次数 | 1 |
| Token | 4,537 |
| 延迟 | 52,278 ms |
| Finish reason | COMPLETE |
| Schema matched | true |
| Truncated | false |
| Reasoning present | false |

合同中的结构、遗漏、重复、跨项目引用、非法 Evidence、成员变化、无证据原因、漏写 UNKNOWN、空摘要和不支持断言计数全部为 0。

安全产物：docs/acceptance-evidence/v3.8.0/real-model/glm-v380/project-history-real-model.json。

SHA-256：9B3331B5AF2BC57BB0F10C4FA879432BE0DD5038E167B0616B6FE595E8E5D088。

产物不包含 API Key、完整 Prompt、raw response、reasoning 或绝对路径。

## GLM 扩展资格

GitHub Actions run 30832103333、job 91748308607 使用同一生产 Prompt builder 完成：

- 冻结 38-run：38/38 成功，51 次请求，501,188 Token；failure、timeout、Schema failure、degradation 和 unsupported claim 均为 0。
- Critical Evidence Recall 0.9610，Evidence Precision 1.0000，Tool Precision/Recall 0.9792，Deep-read Sufficiency 0.8333，Dynamic View Recall 0.9412，Repeatability 0.9858。
- Conflict Detection 0.6667，作为当前真实限制保留，不能解释为任意项目冲突都能可靠识别。
- 真实产品链路：17/17 成功，33 次逻辑请求、33 次物理请求、433,092 Token、3,246,152 ms；Invalid Evidence 和 Degraded 均为 0。

首次 GLM run 30816468130 中 Provider Probe 与 38-run 通过，但产品链路只有 15/17：large-middle 暴露大型源码未进入 Content Map，conflicting-final-docs 暴露测试使用错误文档类别；History 又因 CI 顺序未执行。代码、断言和工件保存顺序修复后才进行最终验收。

run 30830424132 与 30831241801 的旧诊断出现 reasonWithoutEvidenceCount=1；当时该字段混合“非空原因无 Evidence”的硬违规和“空原因漏写 UNKNOWN”。拆分为 reasonWithoutEvidenceCount 与 missingReasonUnknownCount 后，最终运行两项均为 0，生产规则仍对前者硬拒绝并对后者确定性补齐 UNKNOWN。

## 失败与降级

- 无默认 Provider：使用确定性 Story 和 Chapter，模型状态 NOT_CONFIGURED/NOT_USED。
- Provider 调用失败、截断、Schema 错误或非法引用：拒绝模型结果，保留确定性结果，Snapshot 状态 DEGRADED。
- 模型失败不得覆盖上一次成功快照，不得修改 ProjectFact。
- Token、延迟和 reasoning presence 只作过程诊断，不作为产品质量缺陷，也不写入用户第一层。

## 当前资格结论

生产/Eval Prompt parity、结构校验、Evidence 约束、DeepSeek Chat Completions 和 GLM Responses 固定样本均已通过。至少两种真实 Provider 的跨协议资格门禁已满足；结论只适用于上述日期、模型、Prompt v2 和冻结输入。
