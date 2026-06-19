# ProjectFlow V3.2 执行报告：AgentSignatureFeedback 校正反馈闭环

日期：2026-06-19
范围：Phase 1/2 之间的识别反馈闭环，让用户校正能影响同项目后续扫描。

## 本轮已完成

- 新增 `AgentSignatureFeedback` 实体。
- 新增 `AgentSignatureFeedbackRepository`。
- 新增接口：`GET /api/projects/{projectId}/agent-signature-feedback`。
- 用户通过 `PATCH /api/work-sessions/{sessionId}` 修正 Agent 类型后，系统会保存反馈记录。
- 反馈记录保存：
  - 项目 ID。
  - agentName。
  - 原始 Agent 类型。
  - 修正后的 Agent 类型。
  - 修正后的任务意图。
  - 生效范围：当前实现为 `PROJECT`。
- 后续扫描同一项目、同一 `agentName` 且原始类型为 `UNKNOWN` 的候选时，会自动套用最新反馈。
- 自动套用反馈后的 `detectionMethod` 标记为 `USER_FEEDBACK`，与用户直接校正后的 `USER_CORRECTED` 区分。
- 首页侧栏新增“归因校正规则”摘要，展示当前项目已保存的校正反馈。
- 保存 Work Session 校正后，前端会刷新反馈摘要。

## 安全与产品边界

- 反馈只在当前项目生效，不做全局跨项目自动套用。
- 反馈匹配使用同项目 `agentName`，不读取用户主目录或全局 Agent 日志。
- 反馈不会把 Git evidence 变成 Agent Claim，只影响候选归因展示。
- 无法识别时仍默认 `UNKNOWN`，只有用户明确校正后才复用。

## 现实取舍

- 暂未实现“是否应用到全局”的用户选项。
- 暂未实现复杂 Agent 指纹，例如 author/email、commit style、项目内 `.codex`/`.cursor` 痕迹组合。
- 暂未提供删除反馈规则的 UI。
- 当前复用逻辑较保守：同项目、同 agentName、原始 Agent 类型为 `UNKNOWN` 才会套用。

## 验证结果

- 先写红灯测试：`WorkSessionScanControllerTest` 初次运行失败，因为 `/api/projects/{projectId}/agent-signature-feedback` 不存在。
- 实现后，`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q -Dtest=WorkSessionScanControllerTest test` 通过。
- 后端全量测试通过：`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q test`。
- 前端构建首次失败，原因是 JSX 文本中的 `->` 被解析为标签符号；已改为 `{"->"}`。
- 修复后前端构建通过：`npm.cmd run build`。

## 下一步

1. 实现冲突识别：同一时间窗口或同一天内多个 Work Session 修改同一文件或模块时生成冲突候选。
2. 接入项目内 Agent Result 作为 Agent Claim，提升 Evidence Bundle 的证据层次。
3. 增加反馈规则管理：查看、删除、选择项目级或全局级。
