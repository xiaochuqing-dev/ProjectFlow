# V3.8.5 产品验收清单

当前结论：PENDING_HUMAN_REVIEW_ROUND2，不是 PASS。

已完成：

- ProjectFact、ProjectHistoryEvent 与 Evidence 权威边界未改变；Primary/Supporting、Chapter membership、Before/Change/After 和强事实判断由工程层拥有。
- 本地后端/H2 579 项通过，0 失败，0 错误，5 个条件跳过。
- GLM `glm-5.2` Responses/high 与 DeepSeek `deepseek-v4-flash` Chat/max 的完整 qualification、11/11 scenarios、ProjectFlow Dogfood 和五类非代码基线通过。
- 两家在 run `31532558352` 上完成受影响纠正复验：64 Story、2 窗口、单窗口失效、缓存命中、编号占位符泄漏 0。
- Round 2 已冻结 30 Story/8 Chapter，双 Provider 各 15/4，模型自评关闭，人工字段空白。

当前阻断：

- 人工评分仍为 0/30 Story、0/8 Chapter、平均分 NOT_RUN；低分和直接失败项必须如实保留。
- 最终 evidence commit 必须以 GitHub backend/H2、PostgreSQL、frontend、Playwright、Hermes、Obsidian 与 sensitive-content 全绿为合并前置条件。
- PR #15 保持 Draft；未合并、未 backfill、未创建 Tag/Release、未清理分支或 worktree。
- npm audit 的既有 4 high、0 critical 不在 RC2 中静默升级。
