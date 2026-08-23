# ProjectFlow V3.9 增量连续性合同

## 边界

V3.9 继续使用 `ProjectHistorySourceCollector`、`ProjectHistoryEvent`、`ProjectHistoryReconstructionService.upsert` 和持久化窗口 checkpoint。`ProjectContinuityDelta` 只是一次显式刷新的有界派生描述，不是第二套 Event Store、Fact Store 或增量引擎。

## Delta 身份与范围

Delta 从上一成功快照、当前 source collection、既有 Event upsert 结果和 corrected presentation revision 确定性生成，表达：

- added、updated、stale、invalidated Event ID；
- rewrite mode、earliest affected time、前后 source fingerprint 与 project revision；
- 前后 presentation revision；
- 安全相对 changed path；
-哈希化 document identity 与 Agent Result ref；
- no-op 与 truncated 状态。

单次最多返回 500 个各类 Event ID、200 个路径、100 个文档身份和 100 个 Agent Result 引用。路径必须是项目内相对路径；文档和 Agent Result 仅输出短哈希身份，不输出正文、Prompt、密钥或机器绝对路径。

## No-op

来源 upsert 没有 mutation，且 corrected presentation revision 未变化时，`continuityNoOp=true`。此路径必须满足：

- Model request 为 0；
- Story、Thread、Chapter JSON 与工程身份不变；
- corrected presentation 与 Current State revision 不变；
- Agent Context Package revision 不变；
- Obsidian 可执行 0 write。

Correction 变化即使没有 source mutation，也属于 presentation delta，不得误报 no-op。

## 触发合同

外部 Git、文件、文档、rename、delete、restore 或 rewrite 只在下一次 source discovery / explicit refresh 被发现。V3.9 不安装 daemon、watcher 或常驻同步进程。

ProjectFlow 已知的内部写入使用 `ProjectContinuityDirtyMarker` 在既有 `ProjectHistorySnapshot` 上记录待消费 revision。目前覆盖 Agent Result candidate、History correction/revert 和 ProjectFact ingestion。标记本身不扫描、不调用模型。

刷新开始时捕获 dirty revision，完成时只确认同一个 revision。刷新运行期间出现的新 revision 不会被旧刷新清除，快照继续显示 STALE。数据库 Agent Result candidate 同时作为有界 `AGENT_RESULT` 过程证据进入下一次 collection；它不能自行提升为 Strong Fact。

## Rewrite、失败与恢复

现有 31 天 overlap、CURRENT/STALE/INVALIDATED、affectedFrom 和 durable window checkpoint 保持不变。rewrite 不删除旧 Raw Event；未受影响的旧 Story/Thread/Chapter 保持稳定。失败保留上一次成功快照和未消费 dirty marker，成功 checkpoint 不重放，failed/pending window 只按既有恢复合同重试。

## 审计字段

快照 diagnostics 至少包含 `continuityDeltaRevision`、`continuityNoOp`、`continuityRewriteMode`、前后 revision/fingerprint、`continuityAffectedFrom`、四类 Event ID、changed path、document identity、Agent Result ref、delta size、truncated 和已消费 dirty revision。

