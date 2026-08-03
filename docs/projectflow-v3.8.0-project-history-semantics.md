# ProjectFlow V3.8.0 项目历程语义

更新日期：2026-08-03

## 产品边界

Project History 是 V3.8.0 面向任意项目类型的通用展示主轴。Capability 仍是软件项目中可选的长期视图，不再是所有项目成立的前提。

ProjectFact 继续是唯一强事实来源。ProjectHistoryEvent 只保存有界来源暴露出的规范化历史事件，ProjectHistorySnapshot、时间篇章、变化故事和演变链都是可替换读模型。刷新历程不会新增、修改或删除 ProjectFact、兼容 Timeline、Capability 或既有 Evolution。

## 信息层级

1. Overview：回答项目最早可确认状态、当前可确认状态、覆盖范围、篇章和最近变化。
2. Chapter：动态时间区间，不等于版本、里程碑、成熟阶段或成功判断。
3. Story：围绕一个稳定项目主体组织 Before、Change、After、原因、冲突、未知和后续结果。
4. Thread：把同一主体跨时间的多个 Story 串成演变链。
5. Raw Event：保留每个已规范化来源事件、时间、来源、transition、authority 和 rewrite state。
6. Evidence：继续下钻到合法 Evidence 引用和安全来源链接。

Snapshot 只持久化 Overview、Chapter、Story 和 Thread。Raw Event 独立持久化，以便快照替换、增量重建和 Git 重写后仍可审计。

## ProjectHistoryEvent

来源类型包括 GIT、GITHUB、FILESYSTEM、PROJECT_FACT、AGENT_RESULT、DOCUMENT、USER 和 EXTERNAL。

事件类别包括 Commit、Merge、Pull Request、Issue、Tag、File Change、Document Version、Agent Result、Validation、User Declaration、Project Fact 和 External。

Transition 包括：

- CREATED、MODIFIED、REMOVED、RESTORED。
- RENAMED、MOVED、REPLACED、SPLIT、MERGED。
- REVERTED、REAPPLIED。
- UNKNOWN_TRANSITION。

Authority 包括 SOURCE_BACKED、FACTUAL_SOURCE、DECLARED、PROCESS_EVIDENCE、INFERRED_NON_AUTHORITATIVE 和 UNKNOWN。事件同时保留 V3.7.5 的七种 epistemic status：OBSERVED、VERIFIED、DECLARED、INFERRED、CONFLICTED、UNKNOWN、PROCESS_EVIDENCE。只有原有 ProjectFact 规则有权认定强事实；History 不提升 authority。

Rewrite state 包括 CURRENT、STALE 和 INVALIDATED。Git、GitHub、文件系统或文档来源在已确认重写后可标记 INVALIDATED；其他未再出现但不能安全判定删除的来源标记 STALE。事件不物理删除。

## 有界来源收集

单次刷新使用固定命令和安全相对路径，当前主要边界为：

- 最多读取 5,000 个 Git Commit。
- 最多保留 20,000 个来源事件。
- 单 Commit 最多保留 500 个文件变化。
- 最多读取 200 个 Agent Result。
- 无 Git 时最多读取 5,000 个当前文件元数据。
- Git 命令默认 30 秒上限，可选来源命令默认 8 秒上限。

超过边界不伪装为完整历史。Coverage 必须进入 PARTIAL，并在 gaps 或 limitations 中说明浅克隆、提交未完全读取、事件截断、未知总数或来源失败。

敏感文件只生成 metadata，不读取内容。进入事件、模型上下文、诊断或投影前执行脱敏。完整文档、原始 patch、绝对路径、Prompt、raw response、reasoning、Key 和 Authorization 不持久化。

## 主体与故事形成

主体键优先来自路径语义、显式来源关系和稳定区域，不直接把文件名或 Commit message 当作事实。稳定键归一化与 Conventional Commit 文案归一化分离，避免 docs 等合法路径区域被当成提交前缀噪声删除。

单次大提交跨多个明显区域时，以 project-area-backend、project-area-frontend、project-area-docs 等稳定区域折叠重复文件事件。Commit 文案只有在不空白、不属于 fix、update 等泛化词时才可形成补充主体。

同一主体的事件按 occurredAt 和 stable event key 排序。在以下条件出现新 Story：

- 与上一事件相隔超过 10 天。
- 当前 Story 已有 40 个事件。
- 前后出现删除、恢复、替换、撤销、重新实现、拆分或合并边界。
- 前后出现 Tag 边界。

同一 Commit 中属于同一主体的事件保持在同一 Story，避免一次原子变化被人为拆开。

Before、Change 和 After 由工程规则生成。原因默认 UNKNOWN；只有 reasonEligibleEvidenceRefs 非空且全部合法时，模型才可填写 reason。模型不能返回或修改 Before、Change、After、时间、成员关系、eventRefs、storyRefs、Evidence 或 authority。

## 演变链与篇章

Rename、Move、Split、Merge 和 Replace 事件用于建立主体别名关系，同一连通主体生成一条 Thread。Thread 保留完整 transition 顺序，并以最后一个 Story 的 After 作为当前可确认结果。

Chapter 边界由确定性规则产生：

- 相邻 Story 间隔超过 21 天。
- 单 Chapter 达到 20 个 Story。
- 一个 Chapter 跨度超过 60 天。
- 出现 Tag 边界。

Chapter 标题和摘要只描述时间范围、主要主体和 Story 数量，明确声明其为工程分组，不赋予阶段、成熟度或里程碑含义。

## 模型边界

模型任务登记为 PROJECT_HISTORY_SYNTHESIS，并只经 ModelGatewayService 执行。单次刷新最多一次模型调用。当前 Prompt 合同为 project-history-synthesis-v2：

- 最多 60,000 字符。
- Story 区最多 46,000 字符，Chapter 区最多 10,000 字符。
- 每个 Story 记录最多 8,000 字符，每个 Chapter 记录最多 4,000 字符。
- 单批最多 40 个 Story、500 个事件。

模型只改善中文标题、单句摘要、受证据约束的原因、冲突和未知表达。未知 ID、重复 ID、遗漏成员、非法 Evidence、无证据原因、额外字段或不支持的强断言均被拒绝。模型失败时保留确定性结果并将快照标为 DEGRADED。

## 刷新、缓存和失败保留

只有显式 POST refresh 创建持久化 PROJECT_HISTORY_REFRESH Job。所有 GET、Project Memory Gateway、Hermes 和 Obsidian 读取都只消费已持久化结果。

当来源 fingerprint、strategy 和 Prompt 版本未变化且收集完整时，刷新命中 CACHE_HIT，不重建、不调用模型。增量刷新保留受影响窗口 31 天以前且事件仍有效的 Story，对重叠窗口重新计算。

首次刷新失败时状态为 FAILED。已有成功快照后刷新失败时保留上次成功内容，状态为 DEGRADED，并记录安全错误摘要。部分来源、浅克隆或事件上限导致的正常降级也使用 DEGRADED，而不是伪装 READY。

## Coverage 语义

- CURRENT：已确认 Git 总数，单次来源扫描完整，所有提交均已读取。
- PARTIAL：有项目目录和 Git，但存在浅克隆、读取上限、事件上限、来源失败或其他历史缺口。
- CURRENT_STATE_ONLY：项目目录可读但没有可确认 Git 历史，只描述当前材料。
- FACTS_ONLY：没有可读项目目录，只能使用数据库中的既有事实来源。

Commit 很多不代表历史覆盖高。Coverage 同时暴露 discovered/current/stale/invalidated event 数量、来源分类、gap 和 limitation。

## 必须保持的不变量

- 原始事件守恒：每个进入本次快照的语义事件必须被至少一个 Story 覆盖。
- Chapter 的 storyRefs 必须完整覆盖全部 Story，且不能重复创造未知 Story。
- Thread 只能引用同项目已存在 Story。
- 非法 Evidence、跨项目引用、无证据强事实和已知 chronology 错误必须为 0。
- 原因没有合格 Evidence 时必须保持 UNKNOWN。
- 刷新和读取必须同时校验 userId 与 projectId 所有权。
- 任何派生层失败都不能破坏 ProjectFact 或上一次成功快照。
