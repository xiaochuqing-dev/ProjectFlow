# ProjectFlow V3.3 沉淀工作流设计

## 目标

把 ProjectFlow 的主流程从“今日开发 / EvidenceBundle / 项目资产字段”调整为：

```text
待整理变更 → 开发推进段 → 建议沉淀 → 新建 / 合并 / 补证据 / 忽略 → 项目沉淀
```

本地 Git 是事实主源，Agent result 补充任务意图和验证结果，GitHub CLI 只补充远程仓库信息与链接。规则采证并校验，模型只做语义归并和候选生成，用户负责最终确认。

## 方案选择

采用兼容演进，不重解释旧实体，也不推倒旧链路：

- 新增扫描游标、待整理批次、开发推进段和项目沉淀实体。
- 复用 `ProjectChange` 作为“建议沉淀”兼容载体，增加建议动作、目标沉淀、证据引用和置信度。
- 保留 WorkSession、EvidenceBundle、ProjectChange 旧接口；`/scan` 返回结构只做向后兼容扩展。
- 新页面与新接口优先使用推进段和项目沉淀；旧 AiSuggestion 收入折叠兼容区。

未采用的方案：

1. 把 WorkSession/EvidenceBundle 直接改名为批次/推进段。改动较少，但一对多关系、状态与证据语义不成立。
2. 删除旧链路后重建。结构最整齐，但会破坏现有数据、路由和已验证流程。

## 领域模型

### ProjectReviewCursor

每个项目最多一条，记录最后完成整理的边界：

- `lastReviewedCommitSha`
- `lastReviewedAt`
- `lastReviewedBranch`
- `lastReviewedRemoteSha`
- `lastSnapshotId`

扫描只读取游标，不更新游标。只有某批次全部建议被用户确认或忽略后，才把游标推进到该批次 `headCommitSha`。

### ChangeBatch

一次“分析新变化”的持久化结果：

- Git 范围：base/head/branch、commit 数、文件数。
- Agent result 数、推进段数、警告。
- 状态：`PENDING / PARTIAL / REVIEWED / FAILED`。
- 首扫或历史变化 fallback 标记。

同一项目、分支、base/head 的成功扫描应幂等复用，避免重复生成建议。

### DevelopmentSegment

把原子变化归并成人能理解的开发推进：

- 标题、白话摘要、主要变化、用户价值。
- commit、Agent result、文件和统一证据引用。
- `HIGH / MEDIUM / LOW` 置信度。
- `PENDING / CONFIRMED / IGNORED / NEEDS_REVIEW` 状态。

### ProjectChange（兼容增强）

继续承担待确认候选，但新增：

- `developmentSegmentId`
- `suggestedAction`: `NEW_SEDIMENT / MERGE_EXISTING / EVIDENCE_ONLY / IGNORE`
- `targetSedimentId`
- `problemSolved`
- `evidenceRefs`
- `confidence`
- `needsUserReview`

旧 ProjectChange 缺少这些字段时按 `NEW_SEDIMENT` 兼容展示，旧 accept 接口等价于确认新建。

### ProjectSediment

用户确认后的稳定对象：

- 名称、一句话说明、解决的问题、类型和状态。
- 来源推进段、证据引用、最近更新时间。
- 开发者备注单独存储，明确区别于自动判断。

合并会更新目标沉淀；只补证据不会改写核心描述；忽略不会创建沉淀。

## 扫描与归并

### Git 范围

1. 有可达游标：扫描 `lastReviewedCommitSha..HEAD`。
2. 首次扫描：最多读取最近 30 个 commit，并返回首次扫描提示。
3. 游标不可达：从 `lastReviewedAt` 做限量时间 fallback，并返回历史变化提示。
4. 未提交变化继续作为独立事实来源，但不作为可推进的 commit 游标。
5. `.git`、`.projectflow`、依赖和构建目录继续排除。

### 分量策略

- 小量：1～3 commit 或不超过 10 个主要文件，规则生成 1～3 段。
- 中量：4～30 commit 或 10～80 文件，先按模块、时间、关键词和文件重叠预分组，再限制为 2～5 段。
- 大量：先把每 25 条原子变化压缩为批次摘要，再归并为 3～8 段；不把完整 diff 交给模型。

规则结果始终可用。模型已配置时只接收限量事实摘要，返回严格 JSON；解析失败、字段越界或证据不存在时降级到规则结果。

### 证据校验

- commit 必须属于本批次。
- 文件必须属于本次变更集合。
- 测试或构建结论必须有对应文件、日志或 Agent result。
- 模型返回的 evidenceRefs 取事实集合交集；交集为空的候选不进入建议沉淀。
- 所有候选都包含 confidence 和 needsUserReview。

## API

保留现有接口并增加：

- `POST /api/projects/{projectId}/scan`：兼容返回 sessions，同时增加 batch、segments 和首次/fallback 提示。
- `GET /api/projects/{projectId}/change-batches/latest`
- `GET /api/projects/{projectId}/development-segments`
- `POST /api/project-changes/{changeId}/confirm`：请求动作和可选目标沉淀。
- `GET /api/projects/{projectId}/sediments`
- `GET/PATCH /api/project-sediments/{sedimentId}`
- `GET /api/projects/{projectId}/agent-bridge/health`
- `GET /api/projects/{projectId}/github/status`
- `POST /api/projects/{projectId}/github/refresh`

所有项目资源必须先通过 `projectId + userId` 所有权校验。

## Agent 写回协议

新协议结构：

```text
.projectflow/AGENT_PROTOCOL.md
.projectflow/agent-results/<result>/result.json
.projectflow/agent-results/<result>/summary.md
.projectflow/templates/
AGENTS.md
```

初始化时只在 AGENTS.md 顶部插入带标记的入口块，不覆盖原内容。扫描优先读取新目录，同时兼容旧 `inbox` 和 task result。健康检查只报告缺失、过期和可选规则文件，不默认修改 CLAUDE.md、GEMINI.md 或其他工具规则。

## GitHub CLI

- 使用固定参数的 `ProcessBuilder`，工作目录必须通过现有本地项目路径保护。
- 检测 `gh --version`、`gh auth status`、GitHub remote 和 `gh repo view`。
- 设置执行超时，不使用 shell 字符串，不执行 `--show-token`，不读取环境中的 token。
- 所有失败转换为 warnings；本地 Git 扫描不依赖该服务。
- commit 链接优先根据已校验的 GitHub remote URL 和 SHA 纯拼接。

## 前端

- 导航保留路由，文案改为“沉淀确认”“项目沉淀”。
- Dashboard 保留添加项目、Zip 导入、本地绑定和模型配置；主操作改为“分析新变化”。
- `EvidenceFlowPanel` 替换为 `PendingChangesPanel`，主卡只显示推进段摘要和计数，证据细节折叠。
- `/tasks` 以建议沉淀为主，四种动作明确展示；旧 AiSuggestion 收入“旧版候选”。
- `/project-intelligence` 默认显示已确认沉淀；空的主观字段不渲染，开发者备注有独立入口。
- 增加沉淀详情页，第一屏只展示名称、说明、问题、状态、来源概览、更新时间和复用出口。

## 错误与恢复

- Git 不可用、本地路径失效：扫描失败并返回可操作错误，不推进游标。
- 模型或 GitHub CLI 失败：返回 warning 并使用本地规则结果。
- 单条候选确认失败：事务回滚，批次保持 PARTIAL/PENDING。
- rebase/force push：按时间 fallback，清楚标记，不覆盖旧证据。
- 重复扫描：复用同范围批次，避免重复沉淀候选。

## 验证

- 后端控制器测试覆盖首扫、游标增量、历史变化 fallback、幂等、三档归并、证据校验和四种确认动作。
- Agent 协议测试覆盖非覆盖插入、新旧目录兼容和健康检查。
- GitHub CLI 测试覆盖未安装、未登录、非 GitHub remote、成功读取及失败不阻塞。
- 前端静态契约测试覆盖核心入口保留、旧术语退出主视图、主观空字段隐藏和旧候选折叠。
- 每阶段执行针对性测试；最终执行 Maven 全量测试、前端测试和生产构建。

## 非目标

- 不做 PR、CI、Issue、Review 或 GitHub OAuth。
- 不迁移或删除旧数据。
- 不上传源码，不展示 token。
- 不重做视觉系统，不重构无关模块。
