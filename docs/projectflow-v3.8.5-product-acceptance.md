# V3.8.5 产品验收清单

## 2026-08-10 RC2 最终可读性状态

Round 1 为 NEEDS_REVISION_NOT_APPROVED。新的 Provider-neutral 叙述契约已通过本地确定性门禁，但新双 Provider 真实重跑、Round 2 30 Story/8 Chapter 冻结和用户明确批准尚未完成。因此当前结论仍为 PENDING_HUMAN_REVIEW_ROUND2，不是 PASS；以下旧运行结果只作历史证据。

## 已通过

- ProjectFact、ProjectHistoryEvent、Evidence 和 rewrite 状态未被历史重建或展示修正覆盖。
- Primary/Supporting、Chapter membership、before/change/after、生命周期与 Evidence 由工程层唯一维护；模型只负责最小措辞合同。
- 后端 H2 557 项、frontend contracts 58/58、build/lint、Playwright 9/9、Hermes 10/10、Obsidian 25/25 通过。
- head `74ba013` 的 push run `31317712835` 与 PR run `31317716057` required jobs 通过，包含 PostgreSQL 16 Testcontainers。
- workflow `31318477841` 中 GLM `glm-5.2` Responses/high 与 DeepSeek `deepseek-v4-flash` Chat/max 的 V3.8.0、V3.7.5 38-run、Understanding 17/17、V3.8.5 qualification 与最终 scenarios 11/11 通过。
- 双 Provider ProjectFlow Dogfood 与五类非代码通过；正式工件安全计数为 0/false。
- 30 Story / 8 Chapter 已按固定分层规则冻结，合同测试通过。

## 当前阻断

- 真实人工评分仍为 0/30 Story、0/8 Chapter、平均分 NOT_RUN；不能用自动 evaluator 代替。
- DeepSeek scenarios attempt 1 的 9/11 真实失败必须保留；attempt 2 成功不改写首次事实。
- 前端基础修正 UI 已覆盖标题、摘要、隐藏、置顶和恢复；高级合并/拆分/角色/章节声明仍以 API/消费者预览为主，不宣称普通用户全量 UI 闭环。
- npm audit 4 high、0 critical，依赖升级不在 RC2 静默执行。

## GitHub 结论

PR #15 仍为 Draft。人工平均分达到 4.0 且无直接失败项前，不执行 Ready for Review、merge master、acceptance backfill、Tag、Release、分支删除或 worktree 清理。
