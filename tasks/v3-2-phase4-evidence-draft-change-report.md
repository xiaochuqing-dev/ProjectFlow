# ProjectFlow V3.2 Phase 4 执行报告：Evidence Bundle 生成候选变更

日期：2026-06-19
范围：Phase 4 第一段最小闭环，从 Evidence Bundle 生成可审查 ProjectChange。

## 本轮已完成

- 新增 `EvidenceDraftChangeService`。
- `ProjectChangeSourceType` 新增 `EVIDENCE_BUNDLE`。
- `ProjectChangeRepository` 新增按 `sourceType + sourceRef` 查找方法，保证同一 Evidence Bundle 重复生成时更新同一条候选变更。
- 新增接口：`POST /api/evidence-bundles/{bundleId}/draft-changes`。
- 从 Evidence Bundle 生成的候选变更会进入现有 `ProjectChange` 审查列表。
- 候选变更包含：
  - 来源：`EVIDENCE_BUNDLE`。
  - 标题：Evidence Bundle 候选变更。
  - 摘要：文件数、增删行、归因 Agent、置信度。
  - 详情：客观 Git evidence。
  - 影响文件列表。
  - 测试/构建证据缺失提示。
  - Agent Claim 缺失提示。
- 前端 `ProjectChangeSourceType` 增加 `EVIDENCE_BUNDLE`。
- Evidence Bundle 摘要卡片新增“生成候选变更”按钮。

## 关键边界

- 当前为本地规则生成的保守候选，不调用模型。
- 不把 Agent Claim 缺失的内容伪装成 Agent 声明。
- 不自动采纳候选变更；仍必须进入现有审查流程。
- 不新增并行审查页面，复用已有 ProjectChange 审查模型，避免重复 UI。

## 现实取舍

- 暂未根据模型生成更细粒度的风险、决策和成果素材。
- 暂未从一份 Evidence Bundle 拆成多条候选变更。
- 暂未把冲突信息写入候选变更详情。
- 暂未做候选合并/拆分 UI。

## 验证结果

- 先写红灯测试：`WorkSessionScanControllerTest` 初次运行失败，因为 `/api/evidence-bundles/{bundleId}/draft-changes` 不存在。
- 实现后，`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q -Dtest=WorkSessionScanControllerTest test` 通过。
- 后端全量测试通过：`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q test`。
- 前端构建通过：`npm.cmd run build`。

## 下一步

1. 实现 `.projectflow/context` 同步，把已确认项目事实写回给下一轮 Agent。
2. 确认 context 不包含未确认候选。
3. 在后续版本中加入模型增强候选变更生成。
