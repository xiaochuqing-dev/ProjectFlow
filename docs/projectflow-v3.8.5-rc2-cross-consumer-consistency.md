# ProjectFlow V3.8.5 RC2 跨消费者一致性

Frontend、Project Memory Gateway、Agent Context、Hermes 和 Obsidian 均读取同一个 corrected presentation view。overview、Chapter、Story、Thread 响应直接携带 presentationRevision；并发分页或跨资源读取发现 revision 变化时中止，不能混合两个展示版本。

Story 默认列表排除 hidden；显式详情仍可读取。Gateway 与 Hermes 提供 includeHidden；Obsidian 以 includeHidden=true 拉取完整引用图，为隐藏 Story 保留可审计 Note，但默认 Overview、Chapter、Thread 和索引不展示。pinned 只改变默认排序，不改变事实。

split/merge 后必须满足 Chapter 引用存在、无重复、Primary/Supporting 双向一致、merge target 存在。Obsidian 投影在写入前校验这些不变量，并在 revision 漂移时返回 PROJECTFLOW_HISTORY_REVISION_CHANGED。

本地结果：CrossConsumerHistoryConsistencyTest、CorrectedProjectionConsistencyTest、CorrectedReadRevisionParityTest、Hermes 10/10、Obsidian 25/25 和 Playwright 9/9 通过。真实 CI 最新结果以 PR checks 为准。
