# ProjectFlow V3.8.0 项目历程重建架构决策

状态：Accepted for implementation

日期：2026-08-02

## 决策摘要

V3.8.0 新增两个最小持久边界：

1. ProjectHistoryEvent：保存完整、可分页、来源支持且不会因摘要变化而丢失的原始事件。
2. ProjectHistorySnapshot：保存可替换的总览、动态篇章、变化故事和演变链。

ProjectFact 继续是唯一强事实来源。History Event 是来源归一化记录，不自动成为事实；History Snapshot 是派生读模型，不写回 Fact、Timeline、Capability 或 Evolution Bridge。

刷新只在显式持久化 Job 中执行。所有 GET 只读持久化结果，不运行 Git、文件扫描或模型。失败保留上次成功 Snapshot，并显示 stale 或 degraded。

不新增第三方依赖，不复制研究对象代码。

## 为什么不能只扩展现有结构

### 不能只用 ProjectFact

ProjectFact 只保存通过权威和 Evidence 门禁的事实。V3.8.0 需要保留 Merge、branch、tag、删除、撤销、冲突、低权威声明和尚未形成强事实的来源事件。强行塞入 ProjectFact 会破坏唯一事实源和认知状态合同。

### 不能只用 Timeline

现有 Timeline 以 DAY、WEEK、MONTH、LIFECYCLE 组织 Fact。动态篇章不能固定按月切割，且变化故事需要 before/change/after、later outcome、unknown、原始事件成员和长期 thread。

### 不能只用 DevelopmentSegment

DevelopmentSegment 是一次分析批次的语义产物，生命周期受 batch 限制，无法稳定表达跨批次历史、历史重写、原始事件分页和一个 Commit 多故事。

### 不能只用 Capability Evolution

Capability 是软件项目中的可选视图，不能覆盖论文、PPT、数据分析、设计、视频和文档项目。V3.8.0 使用中性 History Subject 和 Evolution Thread 语义，并保留 Capability 映射兼容。

### 不能只用 Evolution Bridge

Evolution Bridge 连接真实 parent/commit、Fact、changed path 和结构区域，适合局部 before/after 证据桥，不是完整项目历程或跨时间 subject thread。

## 事实与派生层

| 层 | 权威 | 可替换 | 写入规则 |
| --- | --- | --- | --- |
| Evidence / Source | 原始或规范化来源 | 可重新发现 | 只在显式刷新中有界读取 |
| ProjectHistoryEvent | 来源归一化记录 | 可按 source revision 重建 | 不自动升级为 Fact |
| ProjectFact | 唯一持久事实源 | 不因历程刷新替换 | 只遵守既有强事实门禁 |
| History Chapter / Story / Thread | 工程分组或模型解释 | 是 | 存入 Snapshot，不写回 Fact |
| Timeline / Capability / Evolution Bridge | 既有派生或兼容层 | 各自按既有规则 | V3.8 不批量改写 |

## ProjectHistoryEvent

### 目标

保存第 4 层原始事件，并支持分页、筛选、Evidence 下钻、历史重写和 Snapshot 复用。

### 最小字段

- id
- projectId
- stableEventKey
- sourceType
- sourceIdentity
- sourceRevision
- projectRevision
- occurredAt
- effectiveAt
- actorLabel，可空且脱敏
- scope：CURRENT、HISTORICAL 或 UNKNOWN
- category：COMMIT、MERGE、PULL_REQUEST、ISSUE、TAG、FILE_CHANGE、DOCUMENT_VERSION、AGENT_RESULT、VALIDATION、USER_DECLARATION、PROJECT_FACT、EXTERNAL
- transition：CREATED、MODIFIED、REMOVED、RESTORED、RENAMED、MOVED、REPLACED、SPLIT、MERGED、REVERTED、REAPPLIED、UNKNOWN_TRANSITION
- safeSourceLabel
- affectedPaths，有界相对路径
- subjectKeys，有界候选主体
- evidenceRefs
- relationRefs：parent、replacement、invalidation、duplicate 或 contained-by
- authority / epistemicStatus
- coverage
- limitations
- rawSourceDeepLink，可空且只允许安全 URL 或相对定位
- rewriteState：CURRENT、STALE、INVALIDATED
- payloadHash
- createdAt / updatedAt

stableEventKey 由 projectId、sourceType、sourceIdentity 和 sourceRevision 的规范化值计算 SHA-256。时间戳不参与稳定身份。

不持久化完整 diff、完整文档、绝对路径、Key、Authorization、Prompt、raw response 或 reasoning。

## ProjectHistorySnapshot

### 目标

保存第 0 至第 3 层的可替换读模型，并在刷新失败时保留上次成功内容。

### 最小字段

- projectId，唯一
- projectRevision
- sourceEventFingerprint
- sourceEventCount
- earliestEventAt / latestEventAt
- strategyVersion
- promptVersion
- status：NOT_INITIALIZED、RUNNING、READY、DEGRADED、STALE、FAILED
- overviewJson
- chaptersJson
- storiesJson
- threadsJson
- coverageJson
- diagnosticsJson
- analysisJobId
- generatedAt
- latestSuccessfulAt
- errorCode / errorSummary
- createdAt / updatedAt

派生数组使用 Jackson JSON 持久化，规模受固定上限控制；原始事件不放入 Snapshot JSON，始终从 ProjectHistoryEvent 分页读取。

刷新开始和失败时不清空上次成功 JSON。只有完整验证的新 Snapshot 才原子替换当前成功内容。

## 六层 Presentation Contract

### 第 0 层：Overview

返回：

- earliestConfirmedState
- currentState
- chapter summaries
- history coverage 与 gaps
- recent changes
- unresolved conflicts 和 unknowns
- freshness / stale / degraded

默认不返回 SHA、文件列表或 Evidence ID。

### 第 1 层：Chapter

- id、title、summary
- from / to
- boundarySignals：time gap、tag、merge、density change、declared boundary
- storyCount / rawEventCount
- authority：ENGINEERING_GROUPING、INFERRED_NON_AUTHORITATIVE 或 DECLARED
- coverage / limitations

系统可以确定时间窗口，但模型标题和解释保持 INFERRED_NON_AUTHORITATIVE。不得自动写“成熟阶段”“里程碑”或“成功”。

### 第 2 层：Change Story

- id
- humanTitle：动作 + 对象 + 结果
- oneSentenceSummary
- beforeState
- change
- afterState
- affectedAreas / subjects
- reason，可空且必须有明确 Evidence
- laterOutcome
- conflicts
- unknowns
- occurredFrom / occurredTo
- evidenceCount / rawEventCount
- authority / summaryStatus
- coverage / limitations
- eventRefs / evidenceRefs

禁止“优化系统”“改进功能”“相关文件变化”等无对象、无前后状态表达。

### 第 3 层：Evolution Thread

- id、subjectKey、subjectLabel、subjectType
- ordered storyRefs
- ordered transitions
- currentOutcome
- gaps、conflicts、unknowns
- evidenceCount
- optional capabilityId

Thread 允许新增、修改、删除、恢复、重构和替换，不要求主体是 Capability。

### 第 4 层：Raw Event

从 ProjectHistoryEvent 分页返回，支持：

- time range
- source type
- category / transition
- authority/status
- subject
- conflict/unknown only
- rewrite state
- cursor/page

### 第 5 层：Evidence

复用 ProjectEvidenceTraceService 和现有安全规则，返回安全来源摘要、revision、验证、currentness、coverage、limitations 和允许的 deep link。

## 显式刷新流程

1. 校验 userId 与 projectId 所有权。
2. 复用活动 ProjectAnalysisJob，避免等价刷新重复计费或重复写入。
3. 使用注册 Provider 和固定 Git 命令发现全部有界来源。
4. 归一化事件，生成 stableEventKey 和 payloadHash，幂等 upsert。
5. 比较 project revision、source set 和上次 fingerprint，分类为 unchanged、append-only、partial rewrite 或 full rewrite。
6. 工程层生成候选原子和候选窗口：
   - Git parent/merge/revert/ref。
   - 文件状态、rename/copy、内容 hash。
   - PR/Issue/Agent/Fact 显式引用。
   - 时间间隔、tag、活动密度。
   - generated/vendor/lock/formatting 噪声标记。
7. 小项目或确定性足够的项目使用零模型；需要语义时只发送压缩候选和已知 event/evidence ID。
8. Model Gateway 返回 story/window 解释，工程层过滤 unknown ID、跨项目引用、无 Evidence reason 和非法 transition。
9. 工程层合并跨窗口 thread，生成 chapter 边界和完整 coverage。
10. 验证 chronology、event conservation、Evidence traceability、unknown/conflict preservation。
11. 原子更新 Snapshot；发布只读刷新完成事件。

## 模型调用策略

- 空项目、空白材料、无历史且确定性 current-state-only：0 模型。
- 小型且候选规模受控：1 次语义请求。
- 只有执行产生新高价值 Evidence 时才允许条件 Final Synthesis。
- 大历史按候选窗口分持久化步骤处理；每一步最多一次窗口解释和一次条件 synthesis。
- 每个窗口覆盖多个事件，固定 event 数、字符、Evidence 和超时上限。
- 达到单 Job 请求或 deadline 上限时，保存 checkpoint 并明确 incomplete/degraded，不把未处理历史静默标为完成。
- 同一 source fingerprint、strategyVersion、promptVersion 和 window identity 复用缓存。
- 不按 Commit 逐次调用模型。

模型只能：

- 判断候选是否属于同一变化。
- 生成可读标题和 before/change/after。
- 标记 unknown、conflict 和可选 subject。
- 在已知 Evidence 内解释 reason 和 impact。

模型不能：

- 删除原始事件。
- 构造命令或路径。
- 引用未知 ID。
- 猜测原因。
- 把重要性、阶段、成熟度、成功或里程碑写成强事实。

## 候选分组与跨窗口合并

工程评分只生成候选，不直接宣称语义真相。候选信号包括：

- 明确相同 PR、Issue、Agent task 或 Fact。
- Git rename/copy、revert/reapply、cherry-pick 和 merge parent。
- 同一受影响 area、相同公开接口、测试、配置或文档。
- 内容 hash 或安全相似度。
- 时间邻近和活动密度。
- 相同 subject alias。

跨窗口 Thread 合并要求至少一个强结构信号或两个独立弱信号。只有模型相似度不得合并。

一个 Commit 可拆成多个候选原子；多个 Commit 可归入一个 Story。Merge Commit 保留为 Event，但可标记 contained-by，避免主展示重复。

## 动态篇章

Chapter 边界按以下候选排序：

1. 明确 user DECLARED boundary。
2. Tag 或 release boundary。
3. PR merge 或 branch integration。
4. 显著时间 gap。
5. 活动密度变化。
6. 稳定主题切换。

固定月只用于兼容 Timeline 和回退显示，不是默认 Chapter 算法。

没有足够 Evidence 时使用中性标题，例如“2026-07-12 至 2026-07-18 的集中变化”，不写“成熟期”。

## 增量、重绑和历史重写

### Unchanged

source fingerprint 未变化时：

- 0 模型。
- 不改 Snapshot。
- 记录 cache hit 诊断。

### Append-only

- 只新增事件。
- 重算尾部重叠窗口、受影响 Story、Thread 和最后 Chapter。
- 前部 Snapshot 内容按 stable ID 复用。

### Partial rewrite

- 找到共同 ancestor 或保留 source identity。
- 将受影响 Event 标记 STALE/INVALIDATED。
- 只重建相交窗口和 Thread。
- 在新 Snapshot 成功前继续提供旧 Snapshot，并显示 STALE。

### Full rewrite 或重新导入

- 保留旧 Event 作为 historical/stale 诊断，不伪装 current。
- 重建完整候选。
- coverage 明确历史连续性无法确认。

### 路径重新绑定

项目路径不进入 cache identity。相同 project revision 和 source fingerprint 可以复用语义 Snapshot，只重新生成本地 deep link 和 currentness。

## Read API

建议新增：

- POST /api/projects/{projectId}/history/refresh
- GET /api/projects/{projectId}/history/overview
- GET /api/projects/{projectId}/history/chapters
- GET /api/projects/{projectId}/history/chapters/{chapterId}
- GET /api/projects/{projectId}/history/stories
- GET /api/projects/{projectId}/history/stories/{storyId}
- GET /api/projects/{projectId}/history/threads
- GET /api/projects/{projectId}/history/threads/{threadId}
- GET /api/projects/{projectId}/history/events
- GET /api/projects/{projectId}/history/events/{eventId}
- GET /api/projects/{projectId}/history/events/{eventId}/evidence

Gateway 增加对应只读 facade；Hermes 只消费 Gateway，不直连 Repository。

所有 GET：

- 同时校验 userId 和 projectId。
- 不运行 Git、文件扫描或模型。
- 不推进 cursor。
- 不写 Fact、Timeline、Capability、Evolution 或 Snapshot。
- 有分页和硬上限。

## Obsidian 决策

- 继续使用现有 ObsidianProjection。
- CORE 新主入口为项目概览和项目历程。
- 生成 Chapter、Story 和 Thread managed notes。
- 旧 Capability notes 和路径继续保留。
- Level 0 使用官方 URI。
- Level 1 可选 Advanced URI，失败回退 Level 0。
- Level 2 REST/MCP 只作为用户显式开启的高级通道。
- Level 3 Dataview/Bases 只提供可选视图。
- 生成稳定 ProjectFlow 本地链接，不写 Token 或绝对路径。
- ProjectFlow managed notes 不重新成为项目强事实，避免 Obsidian Git 同步循环。

## 安全与隐私

- 敏感文件只保存 metadata，不读取正文。
- affected path 只保存仓库相对路径并有数量、字符上限。
- source label 和 actor 经过脱敏。
- Evidence ID 必须属于同一项目。
- rawSourceDeepLink 只允许 https GitHub/GitLab URL、obsidian URI 或产品认可的相对定位。
- 不持久化完整文档、patch、绝对路径、Prompt、raw response、reasoning、Key 或 Authorization。
- 模型输出先做 schema、ID、Evidence、chronology 和 authority 校验。

## 依赖与许可证

不新增依赖，也不复制第三方实现。

采用的是交互与架构模式：

- GitHub 的筛选和 compare 下钻。
- GitLab 的展示聚合与 spam 控制。
- GitButler 的显式 restore/undo 历史表达。
- OpenProject 的上下文和分页。
- release note 工具的聚合与人工可修正思想。
- Obsidian 官方 URI 的零插件跨工具跳转。

实现继续使用仓库现有 JDK、Jackson、JPA、Model Gateway、Git 执行边界和 Projection。

## 被拒绝的备选方案

### 只扩展 Timeline

拒绝。会混淆固定时间 read model 与动态项目历程，并无法保存完整来源事件。

### 新建完整事件、故事、篇章、主体、线程关系表体系

拒绝。当前可用一个 Event 表和一个压缩 Snapshot 表满足要求；先避免庞大实体图。

### 采用图数据库或向量数据库

拒绝。现有关系规模和查询合同可由 JPA、JSON 和确定性索引完成，新增供应链和运维成本没有证据支持。

### 复用 GitButler、Gource 或 OpenProject 实现

拒绝。许可证、产品边界和技术栈都不合适。

### 强依赖 Dataview 或 Local REST API

拒绝。会破坏零插件和普通 Markdown 可用性，并扩大 Vault 权限面。

## 实现门禁

本 ADR 与当前状态审计、开源研究、Obsidian 工作流研究、复用矩阵形成纯研究提交后，才允许修改生产代码。
