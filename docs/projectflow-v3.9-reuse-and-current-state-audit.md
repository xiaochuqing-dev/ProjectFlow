# ProjectFlow V3.9 复用与当前状态审计

审计日期：2026-08-24

基线：master `ab29b1ff0f842c029b5cf121bd584bd40fcf74b2`

结论：V3.9 不需要第二套 History、Fact、增量或投影引擎。现有 V3.8.5 已具备可持续演进所需的大部分基础，V3.9 应在同一显式刷新链上补齐 Continuity Delta、未受影响篇章身份稳定、修正安全续接、Current Project State 和 Agent Context 修订联动。

## 已有能力与复用位置

| 能力 | 当前实现 | V3.9 决策 |
| --- | --- | --- |
| 来源增量与 rewrite | `ProjectHistorySourceCollector`、`ProjectHistoryReconstructionService.upsert`；稳定事件键、payload hash、CURRENT/STALE/INVALIDATED、added/updated/reused/affectedFrom、31 天 overlap | 原样复用；把现有差异提升为有界 `ContinuityDelta` 诊断，不另建事件账本 |
| 增量 Story 重建 | previous successful snapshot、affectedFrom、保留 overlap 之前且 Event 仍有效的 Story | 原样复用并增加 continued/new/unchanged/relinked/ambiguous/rejected 诊断 |
| Story/Thread 身份 | Story 由 subject + 首个稳定事件确定；Thread 由 canonical subject 确定 | 继续由工程规则拥有；不新增 per-commit 或 per-file 模型判断 |
| 模型窗口 | `ProjectHistoryWindowPlanner`：16 Story、360 Event、最多 16 个窗口，identity/cache key 包含来源、策略、Prompt、修订 | 原样复用；只向受影响、已知候选 ID 开窗 |
| 持久化恢复 | `ProjectHistoryWindowCheckpointService`：租约、乐观版本、SUCCEEDED/FAILED/CANCELLED/SKIPPED_OVERSIZE、validated result、retry/resume | 原样复用；禁止重放成功 checkpoint，失败仅影响可替换语义层 |
| 篇章表示 | `ProjectHistoryChapterRepresentationPlanner` 与 chapter synthesis checkpoint | 复用表示规划器；改变篇章维护范围，保留 affectedFrom 之前的工程篇章并只重算受影响尾部 |
| 修正覆盖层 | `ProjectHistoryCorrectionService`；所有修正类型、修订、可逆、冲突诊断、`USER_DECLARED_PRESENTATION` | 原样复用覆盖层；增加安全 additive continuation 证明，rewrite 不可证明时仍冲突，绝不静默错绑 |
| 读取面 | `ProjectHistoryReadService`、Gateway、Hermes、Frontend 都从持久化 corrected history 读取；GET 不扫描、不调用模型 | 增加同源的 Current Project State 只读接口，不新建事实源 |
| Agent Context | `ProjectAgentHistoryService` 的 Package v2、持久化-only 检索、稳定 SHA-256、modelCalled=false | 保留 v2；把 corrected-history/current-state 语义修订纳入 package revision |
| Obsidian | `integrations/obsidian/projectflow_obsidian.py`；presentation revision 一致性校验、managed-block hash、原子写、manifest、冲突保护、no-op 0 write | 继续显式同步；增加 Current State 输入和受影响笔记验证，不安装 watcher/daemon |
| 非 Git 项目 | 文件、文档、Agent Result、ProjectFact 已进入统一 source collection | 继续依赖 stable file/document identity 与 source hash；Git 缺失不是失败 |

## 当前 no-change 路径

显式 refresh 会先有界收集来源并 upsert。来源指纹、策略、Prompt、修正修订均未变化，且没有 retryable/pending checkpoint 时，`recordCacheHit` 只更新作业诊断，模型请求为 0，Story/Thread/Chapter 的语义 JSON 保持不变。该路径已满足 V3.9 基线；V3.9 只补充 `continuityNoOp=true`、稳定 delta revision 与身份计数。

## 已确认的真正缺口

1. `chapters(stories, events)` 当前从全部 Story 重新分章，而且全局密度预算可能因小增量改变旧边界。V3.9 必须冻结未受影响篇章工程身份，只重算 affected chapter tail。
2. 修正只保存目标成员指纹。任何成员增加都会被视为冲突，无法区分“同一稳定 Story 的安全追加”与“rewrite 后危险换绑”。需要保存有界原始成员引用并做旧成员为当前成员子集的安全证明；旧数据无引用时保持保守冲突。
3. Agent Context Package revision 没有把 corrected-history narrative/presentation revision 放入 canonical hash。历程或用户展示修正变化时，上下文修订可能不变。V3.9 必须纳入 Current State/History 的语义修订，同时保持 generatedAt 不参与哈希。
4. Current State 目前只是 Overview 中的一段字符串，没有独立的 revision、currentness、coverage、conflict、unknown、affected thread/story/chapter 引用。需要从持久化 corrected snapshot 派生一个只读模型。
5. 现有 delta 只在内部 `PersistedEvents` 与 diagnostics 中分散表达，缺少一个可测试、可观测、项目隔离且不含秘密的统一合同。

## 明确不引入

- 不引入第二套 History、Fact、事件溯源、向量数据库、模型客户端或 Projector。
- 不改变 V3.8.5 冻结的 Story/Chapter 真值与 Evidence 语义边界。
- 不做后台 daemon、watcher、常驻 Obsidian 同步或每次文件写入自动扫描。
- 不让 Current Project State 变成 ProjectFact，也不由 GET 推断新事实。
- 不让模型决定 Event、Evidence、Story/Thread/Chapter 身份或跨项目引用。

## Current Project State 设计结论

Current Project State 是持久化 corrected history 的派生展示模型。它只读取上一次成功快照、当前 correction overlay、coverage 与 diagnostics，输出状态修订、来源修订、展示修订、currentness、confirmed state、最近确认变化、活跃 Thread、相关 Story/Chapter、conflict、unknown、limitations 和 stale/degraded 原因。其 GET 路径必须 model-free、scan-free、write-free；相关 delta 或 correction 改变 revision，无变化时 revision 稳定。

## 并发、失败与外部变化

作业系统和 checkpoint 租约继续拥有并发与恢复语义。内部明确写入可以标记 dirty，但不在写入事务内触发扫描；外部文件、Git rewrite、删除或恢复只在下一次显式 refresh 被发现。Provider 失败保留工程层 delta 与上次可读快照，不删除 Event、不覆盖较新 checkpoint、不把 degraded 伪装为 current。

