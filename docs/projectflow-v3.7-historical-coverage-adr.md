# ADR：V3.7 Historical Coverage

状态：Accepted

## 决策

Historical Coverage 是当前理解的可替换 read model，来源为本地 Git metadata、Tag、bounded commit period sample、已有 ProjectFact commit refs 和历史价值文档候选。

字段包括 history availability、earliest/latest、Git/covered commit、Tag/Release、document/Agent evidence、covered/gap periods、confidence by period、overall coverage 和 limitations。

## Evolution strategy

- 无 Git：CURRENT_STATE_ONLY。
- 1–5 commits：EARLY_PROJECT，只展示短历史。
- 至多 5,000 commits：MILESTONE_WINDOWS，以 Tag、Fact、月度密度和结构变化选择不超过 15 个候选。
- 超过 5,000 commits：CLUSTERED_LONG_HISTORY，只采样周期并记录限制；不逐 commit LLM。

V3.7 Evolution Preview 只表达可执行策略与候选规模。既有 Evolution Bridge 继续连接真实 parent/commit、Fact、changed file 和 area。完整多 revision 结构重建尚未实现。

## 边界

当前源码不能反推完整历史。Document-only history 只能标 limited，不能冒充 Git。Coverage 不写回 Fact、Timeline、Capability 或 Evolution。
