# V3.8.5 Claim-level Evidence Attribution Contract

本合同约束自动 Story、确定性 fallback、模型改写和用户 presentation correction。它不改变 ProjectFact、原始事件、Technical Atom、时间顺序、Chapter membership 或 Evidence 所有权。

## Claim 结构

每个可见结论必须拆成：

- subject：被描述的产品能力、工程对象或非代码产物。
- action：本阶段对该 subject 发生的动作，例如规划、声明、配置、实现、验证、移除或恢复。
- state：PLANNED、DECLARED、CONFIGURED、IMPLEMENTED、OBSERVED、VERIFIED、REMOVED、RESTORED、UNKNOWN 或 CONFLICTED。
- outcome：仅描述当前 state 能直接支持的结果。

## Evidence 结构

Technical Atom 是最小归因单元。每个 Atom 保留自己的 subjectKeys、动作/transition、来源类别、权威等级、认识状态、路径和 Evidence refs，禁止先把多个 Atom 的字段扁平合并后再判断强状态。

Direct Evidence 必须同时满足：

1. Atom 的稳定 subjectKey 与 Claim subject 相同。
2. Atom 的 Evidence 类型支持 Claim action。
3. 来源权威和认识状态允许该 state。
4. Evidence 仍属于当前项目和当前 Story。

同 Commit、时间接近、相同 affected area、Primary/Supporting 关系或文本主题相似，只能形成 Indirect Evidence。Indirect Evidence 可以解释上下文，不能提升 Claim state。

## 状态提升上限

- 规划或设计材料只能到 PLANNED/DECLARED。
- 配置文件只能到 CONFIGURED，不能宣称 deployed、production-ready 或 runtime success。
- 与 subject 直接对应的实现 Atom 才能到 IMPLEMENTED。
- VERIFIED 必须同时有同一 subject 的直接实现 Evidence 和独立验证结果 Evidence；测试文件名或“Test”字样本身不够。
- REMOVED/RESTORED 必须来自同一 subject 的直接生命周期 Evidence。
- 同一 Story 出现互相冲突的直接 Evidence 时为 CONFLICTED。
- 没有直接 Evidence 时为 UNKNOWN；不得用 Supporting 或同 Commit 代码补强。
- `project-area-*` 只表示宽泛工程区域，不是具体功能 subject。区域内存在代码只能证明 OBSERVED 变化，不能把整个区域提升为 IMPLEMENTED/VERIFIED；精确 subject 的直接实现与独立验证 Evidence 仍可正常提升。

Supporting Story 保留自己的事实表达，但其 Evidence 永远不能把 Primary Story 从弱状态提升为强状态。

## Evidence 类型与非代码产物

实现、验证、配置、部署、文档、非代码产物、生命周期和过程声明是不同 Evidence 类型。研究报告、PPT、数据分析、设计稿、图片等非代码产物可以由产物文件本身支持 OBSERVED/CREATED，不要求代码 Evidence，也不能反过来证明同名产品功能已经实现。

Agent Result 属于过程声明或候选解释。除非其 Evidence refs 被现有边界重新读取并验证，否则不能单独形成 IMPLEMENTED、VERIFIED、REMOVED 或 RESTORED。

## 降级与可审计性

每个 Attribution 至少保留 subject、action、state、outcome、directEvidenceRefs、indirectEvidenceRefs、sourceAuthority、supportClass 和 downgradeReason。直接证据不足时必须降级到事实可支持的最高状态，并说明原因。不能通过隐藏“实现”二字掩盖错误 state。

## Correction 与模型边界

用户 correction 可以覆盖 presentation，不能改变 Attribution。PLANNED/DECLARED/CONFIGURED/OBSERVED/UNKNOWN/CONFLICTED 的 Story 不能通过改标题或摘要变成 VERIFIED。合并或拆分后如果无法维持直接 Evidence 关系，状态必须重新计算或保守降级。

模型只接收有界的直接支持摘要、明确标为不能提升状态的间接上下文、允许状态和禁止结论。模型只能改写文字，不能选择或扩大 Evidence，不能提高 state。模型失败、被拒绝或输出越界时，确定性 fallback 必须通过同一 Attribution 和 Entailment gate。

## 冻结反例

RC3 必须以确定性测试覆盖以下十类输入：项目骨架提交；同提交三个独立功能；README 规划加无关代码；仅配置；仅测试；实现加独立验证；非代码产物；Agent Result 强声明；用户 correction/rewrite；Round 2 的 `ae9fba1e...` ProjectFlow 登录 P0。所有反例对 GLM、DeepSeek 和 fallback 使用同一合同。
