# ProjectFlow V3.2 Phase 2 执行报告：Evidence Bundle 最小闭环

日期：2026-06-19
范围：Phase 2 第一段最小闭环，从已检测的 Work Session 生成可持久化 Evidence Bundle。

## 本轮已完成

- 新增 `EvidenceBundle` 实体，用于保存证据包。
- 新增 `EvidenceBundleRepository`。
- 新增 `EvidenceBundleService`。
- 新增接口：`POST /api/work-sessions/{sessionId}/evidence-bundles`。
- 新增接口：`GET /api/projects/{projectId}/evidence-bundles`。
- Evidence Bundle 当前保存：
  - Work Session 归属。
  - Agent 类型。
  - 任务意图。
  - 分支名。
  - 归因置信度。
  - 改动文件数。
  - 新增/删除行数。
  - 文件列表。
  - 客观证据列表。
  - Agent Claim 列表。
  - 证据来源清单。
- 同一个 Work Session 重复生成 Evidence Bundle 时会更新同一条记录，不重复污染证据账本。
- 首页 Work Session 卡片新增“生成证据包 / 更新证据包”按钮。
- 首页新增 Evidence Bundle 摘要区域，展示已生成证据包数量、来源、文件统计和 Agent Claim 数量。
- API 客户端新增 `EvidenceBundle`、`EvidenceSource`、`createEvidenceBundle()`、`listProjectEvidenceBundles()`。

## 关键边界

- 本轮只从已持久化的 Work Session 生成 Evidence Bundle。
- Git evidence 是客观证据，写入 `objectiveEvidence`。
- Agent Claim 初始为空，不把 Git 推测、用户补充任务意图或模型推断冒充为 Agent 自己声明。
- 未引入用户主目录日志扫描。
- 未扫描 `~/.claude`、`~/.traecli`、Codex 全局日志等路径。
- 所有 Evidence Bundle 接口都通过 Work Session 或 Project 所属关系校验用户权限。

## 现实取舍

- 暂未把 `.projectflow/inbox/` Agent Result 自动并入 Evidence Bundle；现有 Agent Result 扫描仍由已有桥接功能处理。
- 暂未创建单独 `EvidenceSource` 表；当前以 Evidence Bundle 内部来源清单满足最小可审查闭环。
- 暂未实现非 Git 项目的文件哈希 baseline。
- 暂未收集测试、构建、Lint 输出。

## 验证结果

- 先写红灯测试：`WorkSessionScanControllerTest` 初次运行失败，因为 `/api/work-sessions/{sessionId}/evidence-bundles` 不存在。
- 实现后，`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q -Dtest=WorkSessionScanControllerTest test` 通过。
- 后端全量测试通过：`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q test`。
- 前端构建通过：`npm.cmd run build`。

## 下一步

1. 新增 `AgentSignatureFeedback`，保存用户修正的 Agent 类型和任务意图，让同项目后续识别可以复用。
2. 接入项目内 `.projectflow/inbox/` Agent Result 到 Evidence Bundle 的 Agent Claim 层。
3. 增加冲突识别：同一时间窗口内多个 session 修改同一文件或模块时进入待审查队列。
