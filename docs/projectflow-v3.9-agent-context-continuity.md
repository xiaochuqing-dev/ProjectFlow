# ProjectFlow V3.9 Agent Context 连续性

## 单一记忆视图

Context Package v2 继续由 `ProjectAgentHistoryService` 从持久化 ProjectFact、corrected history、Agent candidate 和 Current Project State 组装。Codex、Claude Code、Hermes 或其他 Agent 读取的是同一项目状态，不各自重建语义。

## 有界内容

Package 可包含当前项目状态、相关 Story、活跃/最近 Thread、当前/latest Chapter、validated changes、Strong Fact、声明/过程证据、Unknown、Conflict、Correction 状态、coverage、limitations 与 source revision。现有 task/scope/Evidence depth/budget 排序和数量边界保持不变。

Agent Result 和 candidate 始终保持 PROCESS_EVIDENCE、INFERRED、UNKNOWN 或 CONFLICTED 边界，不能因为被多个 Agent 重复读取而晋升为 OBSERVED/VERIFIED。

## Revision 与读取安全

Package canonical identity 现包含 Current Project State 的 `stateRevision`，从而把 corrected history、Correction、source delta 和内部 dirty 状态带入 package revision。`generatedAt` 不参与 hash：no-change revision 稳定，相关 delta 必须变化，另一 project 的变化不得污染当前 project。

Context GET 是 owned、persisted-only、model-free、scan-free；不返回私有 raw Prompt、聊天历史、secret 或无界源文档。

