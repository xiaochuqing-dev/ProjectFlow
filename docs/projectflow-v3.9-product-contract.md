# ProjectFlow V3.9 产品合同

正式名称：ProjectFlow V3.9 — Project Continuity Closure / 项目持续认知与增量维护闭环

## 产品目标

ProjectFlow 在一次项目理解之后，能够在后续 Commit、文件、文档、Agent Result、ProjectFact、删除、恢复或 rewrite 出现时，只重新处理受影响范围，并把新增材料续接到既有 Event、Story、Thread、Chapter、Correction、Current Project State、Agent Context 与 Obsidian 阅读投影。

## 单一闭环

真实材料 → 稳定来源事件 → Continuity Delta → 受影响 Story → 稳定 Evolution Thread → 增量 Chapter → corrected history → Current Project State → Agent Context / Gateway / Hermes / Obsidian。

ProjectFact 仍是唯一 Strong Fact 来源。Event、Delta、Story、Thread、Chapter、Current Project State 和模型措辞都是可替换认知或展示层，不能自行提升为 Strong Fact。

## 显式刷新合同

- 外部 Git 或文件变化在下一次显式 continuity refresh 发现。
- ProjectFlow 已知的内部写入可标记 dirty/revision，但不在写入请求内运行全量扫描或模型。
- GET、Gateway、Hermes 和 Obsidian 读取只消费持久化结果，不扫描、不调用模型、不推进事实。
- 无变化 refresh 必须 0 模型请求，并保持 Story、Thread、Chapter、Current State、Context Package 和 Obsidian 语义身份稳定。

## 连续性安全

- 同一稳定主体与兼容生命周期变化可继续既有 Story；长间隔、独立结果或歧义默认新 Story/attention。
- Thread 身份由工程层稳定 subject lineage 拥有，新增 Story 只能安全 append。
- 未受影响 Chapter 工程身份必须稳定；只有 affected chapter tail 可重算。
- rename/move/delete/restore/force-push 不删除历史 Event；无法证明的连接保持 UNKNOWN 或冲突。
- 用户修正是 durable、auditable、reversible 的 `USER_DECLARED_PRESENTATION`，不得静默丢失或错绑。

## Current Project State

Current Project State 只从最新持久化 corrected history 派生，明确区分 current、stale、degraded、conflicted 与 unknown。它包含来源/展示/状态 revision 和可下钻引用，但不产生 ProjectFact。相关 delta 或 correction 必须改变 state revision；no-op 必须保持不变。

## Agent 与投影连续性

Agent Context Package v2 必须携带同一 corrected current state，相关变化更新 package revision，无变化保持 revision。Gateway、Hermes、Frontend 与 Obsidian 不得各自重建语义。Obsidian 只更新变化的 managed block/note，用户区内容必须保留，无变化同步 0 write。

## 质量优先与有界执行

模型窗口继续使用 16 Story、360 Event、最多 16 Window 的现有规划和持久化 checkpoint。Token、耗时与费用是诊断，不触发自动降质。模型失败不得破坏工程 delta、事实层、修正或上次成功快照。

## 不在 V3.9 实现

最终 GUI、V4 产品化、后台 watcher/daemon、自动 Obsidian 常驻同步、第二 History/Fact 引擎、向量数据库、Agent manager、Provider leaderboard、每 Commit/文件模型请求、Tag 和 Release均不在范围内。

