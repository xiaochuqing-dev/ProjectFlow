# ProjectFlow V3.9 当前项目状态合同

## 定位

Current Project State 是最新持久化 corrected history 的 DERIVED / PRESENTATION read model，用来回答“当前能确认什么”。它不是 ProjectFact，不写入 Strong Fact，也不自动宣称项目完成、成熟、已部署或生产可用。

## 读取接口

- Native：`GET /api/projects/{projectId}/history/current-state`
- Gateway：`GET /api/projects/{projectId}/project-memory/history/current-state`
- Hermes：`get_project_current_state`
- Frontend：`getProjectCurrentState`

所有 GET 只读取已持久化快照、Correction、coverage 与 diagnostics；不扫描文件、不运行 Git、不调用模型、不写数据库。返回 `modelCalled=false`。

## 内容

响应包含 state/source/presentation revision、history status、currentness、confirmed state、最近确认变化、相关 Story/Thread/Chapter refs、conflict、unknown、limitations、latest successful refresh 和 stale/degraded 状态。ProjectFlow 已知内部写入尚未刷新时，还包含有界 dirty revision、reason 和时间。

confirmed state 优先从未隐藏、未合并的 corrected Primary Story 派生：先取最新有效 Chapter 中的 Primary 候选，再与最新有效 Primary 候选合并，最终统一按真实发生时间倒序并限制为 4 条。`pinned`、用户声明 Chapter 和最新 Supporting 变化都不能把较早结果移动到较新 Primary 之前；Supporting 只保留为近期上下文。

没有可见 Primary 时不会从隐藏 Story 泄露状态：存在被展示修正隐藏的 Primary 会明确说明该限制；只有确实没有活动 Story 时才使用旧 Overview 兼容后备。引用保持项目所有权隔离并有数量上限。第一层不展示 full SHA、内部 checkpoint key 或私有机器路径。

Gateway Snapshot/Brief、Agent Context、Hermes、Frontend Overview 与 Obsidian 概览复用同一 corrected current-state 语义；近期发生统一按时间排序，置顶只影响其他展示位置。

## Revision

`stateRevision` 的 canonical hash 包含项目 ID、历史状态、currentness、source/project/presentation revision、dirty revision、确认状态、引用、conflict、unknown 和 limitations。生成时间、job ID 等非语义字段不参与，因此 no-op 稳定；相关 source、Correction 或内部 dirty revision 变化时必须变化。

## 状态语义

NOT_INITIALIZED 明确要求显式刷新。RUNNING、STALE、DEGRADED、FAILED 与 coverage 缺口不得伪装为 current。Provider 失败仍可读取上一次成功快照，但返回 degraded/limitation；新内部写入等待刷新时返回 stale。

