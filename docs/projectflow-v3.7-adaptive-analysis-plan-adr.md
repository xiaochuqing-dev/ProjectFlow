# ADR：V3.7 Adaptive Analysis Plan

状态：Accepted

## 决策

`AdaptiveAnalysisPlanner` 组合 deterministic guardrails 与 Semantic Scout 建议。空目录、无实质内容、无 Git、无 SCIP、无模型和 generated/vendor/binary 边界由代码决定；项目形态、材料角色和适用维度允许模型提出 evidence-bound hypothesis。

所有 tool request 通过 `AnalysisToolRegistry`。模型只能选择 capability 名，不能拼 shell command。未注册、不可用或 remote capability 不进入 `toolsToInvoke`。

Plan 记录 shapes、applicable/skipped dimensions、evidence priorities、tools、deep-read targets、history/structure strategy、Scout/Deep Read/Synthesis/Evolution budgets、expected outputs、reasons 和 confidence。

## Token policy

- Empty/blank/unchanged：0 model。
- Semantic 请求上限：1。
- Scout evidence：80。
- bounded sample：单来源 1,600 字符。
- combined prompt：48,000 字符。
- Evolution candidate windows：15。
- 不逐文件、Symbol、commit 调模型。

## 后果

页面可以解释为什么某个视图出现或缺失。Planner 仍是 replaceable derived intelligence，不创建 ProjectFact，也不在 GET 执行。
