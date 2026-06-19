# ProjectFlow V3.2 Phase 1 执行报告：今日变化概览与 Git 归因候选

日期：2026-06-19
范围：PRD Phase 1 的第一段最小闭环，目标是让用户在工作台看到“今日项目发生了什么”的可审查候选。

## 本轮已完成

- 新增后端接口：`POST /api/projects/{projectId}/scan`。
- 新增 `WorkSessionScanService`，基于已绑定的 `ProjectMemory.localProjectPath` 读取 Git evidence。
- 扫描当天 commit 的 `numstat`，聚合为 Work Session 候选。
- 扫描当前工作区未提交 diff，作为额外候选。
- 每个候选返回：
  - 推测 Agent 类型：当前为 `UNKNOWN`。
  - 归因置信度：当前 Git-only 候选为 `MEDIUM`。
  - 检测方式：`GIT_EVIDENCE`。
  - 分支名、基础 commit、时间范围、改动文件数、增删行数、影响模块、证据列表、文件列表。
- 过滤 `.projectflow/`、`.git/`、`node_modules/`、`target/`、`.next/` 等内部或生成目录，避免把 ProjectFlow 自身接入文件当成用户项目变更。
- 首页新增“今日变化概览 / 自动归因候选”面板。
- 首页新增“刷新变化”按钮，调用 `/scan`，展示候选 session、置信度、时间范围、文件/新增/删除统计和影响模块。
- API 客户端新增 `WorkSessionCandidate`、`WorkSessionScanResult` 类型和 `scanProjectWorkSessions()`。
- 新增 `WorkSession` 持久化实体与 `WorkSessionRepository`。
- 扫描结果会写入 `work_sessions` 表，刷新页面后仍可通过 `GET /api/projects/{projectId}/work-sessions` 查看。
- 新增 `PATCH /api/work-sessions/{sessionId}`，支持用户修正 Agent 类型和任务意图。
- 用户校正后 `detectionMethod` 会变为 `USER_CORRECTED`，后续 Git 重新扫描不会覆盖已校正的 Agent 类型和任务意图。
- 首页候选卡片新增 Agent 类型下拉、任务意图输入框和“保存校正”按钮。
- API 客户端新增 `listProjectWorkSessions()` 与 `updateWorkSession()`。

## 安全边界

- 本轮只读取已绑定项目路径，不扫描用户主目录。
- 不读取 Claude Code、Codex、Cursor、Trae 等全局日志目录。
- Git 命令使用 `ProcessBuilder` 参数数组调用，不拼接 shell 字符串。
- 前端不传入任何 Git 命令或任意路径；扫描路径来自已确认绑定的项目记忆。
- 未绑定本地项目路径时，接口返回明确错误：需要先绑定本地路径。

## 现实取舍

- 已新增 `WorkSession` 持久化表和用户校正能力。
- 没有实现 Agent 类型自动识别。Git-only evidence 不能可靠判断具体 Agent，因此默认仍诚实显示 `UNKNOWN` 和 `MEDIUM`。
- 没有实现合并、拆分 session。当前优先完成“扫描 -> 保存 -> 用户校正 -> 复用”的最小闭环。
- 没有接入授权日志采集器。该能力涉及隐私授权、撤销和索引删除，必须单独实现。

## 验证结果

- 先写红灯测试：`WorkSessionScanControllerTest` 初次运行失败，因为 `/api/projects/{projectId}/scan` 不存在。
- 实现后，`mvn.cmd -q -Dtest=WorkSessionScanControllerTest test` 通过。
- 前端构建通过：`npm.cmd run build`。
- 完整后端测试通过：`mvn.cmd -q test`。
- 全量测试时发现新测试固定邮箱与复用测试上下文冲突，已改为每次生成唯一邮箱，避免测试顺序依赖。
- 持久化与校正实现后，`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q -Dtest=WorkSessionScanControllerTest test` 通过。
- 持久化与校正实现后，`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q test` 通过。
- 持久化与校正实现后，`npm.cmd run build` 通过。

## 下一步建议

1. 引入项目内 Agent 痕迹扫描：`.projectflow/inbox/`、`.cursor/`、`.claude/` 等，仅限项目目录内。
2. 生成 Evidence Bundle，把 Git 证据、Agent 结果、测试/构建证据串成可审查链路。
3. 后续再实现合并、拆分 session 和授权日志采集器。
