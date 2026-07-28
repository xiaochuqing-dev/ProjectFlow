# ProjectFlow V3.7.3 Tool and View Eligibility ADR

状态：Accepted

## Capability eligibility

`AnalysisToolRegistry` 根据当前 intake/index/source map 先计算客观可用集合：

- FILESYSTEM：存在可复用 bounded inventory。
- MANIFEST：存在 manifest evidence。
- SCIP：已有安全、可读且当前的 SCIP index；不下载、不构建。
- DOC_READER：存在可深读且非敏感的文档 Evidence。
- AGENT_RESULT：存在 Agent Result Evidence。
- GIT_HISTORY / GIT_TAG：当前目录有 Git 及对应 metadata。
- WORKTREE：Git worktree 可用。

模型只能从该集合选择 capability name 和 Evidence ID。No-Git 不可能执行 Git，缺 SCIP 不可能执行 SCIP；未知名称、命令、参数和越界 target 均被拒绝。默认能力只复用已完成的 FILESYSTEM/SCIP 或确定性安全来源，不代表模型语义重要性。

Eligibility 不会自动变成调用计划。即使 MANIFEST、Git、DOC_READER 客观可用，模型没有给出完整 information gap、expected value、target Evidence 和现有证据不足理由时，Planner 也不会替它补造请求。

## View eligibility

`AnalysisViewRegistry` 根据真实形态允许最小视图集合。空目录无视图；文档项目可有 CURRENT_STATE/DOCUMENT_OVERVIEW；小脚本不自动出现 ARCHITECTURE；代码项目可候选 CURRENT_STRUCTURE、FRONTEND、BACKEND、DATA 等；只有真实 Git/history coverage 才允许 HISTORY/TIMELINE/EVOLUTION。

模型决定 eligible 集合中哪些视图对当前项目有用，并给出 evidence-linked rationale。最终合成再次过滤 section type，禁止模型用常识增加客观不适用视图。

Registry 不根据代码存在、历史存在或冲突候选自动补 View；生产与 Eval 都只统计模型在 `applicableDimensions` 或 Section type 中真实选择并通过校验的 View。

## 采用理由

这保持“工程判定客观可能性、模型判定语义价值”，同时减少 unavailable tool、模板化视图和跨模型漂移，不需要新 Provider 或规则引擎。
