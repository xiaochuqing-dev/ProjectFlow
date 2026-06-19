# ProjectFlow V3.2 最终执行报告

## 执行日期

2026-06-19

## 执行依据

- 任务计划书：`tasks/prd-projectflow-v3-2-final-plan.md`
- 产品方向：ProjectFlow 作为跨 Agent 证据账本、确认层和成果沉淀层，不做通用 Agent 编排器。

## 总体完成情况

V3.2 主链已按阶段落地为可运行版本：

1. Phase 0：嵌入式个人模式和一键启动。
2. Phase 1：基于 Git/项目痕迹的 Work Session 扫描、持久化与用户修正。
3. Phase 2：Evidence Bundle 生成。
4. AgentSignatureFeedback：用户修正归因后反哺后续识别。
5. Phase 3：同文件冲突检测。
6. Phase 4：Evidence Bundle 生成候选 ProjectChange。
7. Phase 5：确认上下文同步到 `.projectflow/context`。
8. Phase 6：成果输出读取已确认变更。
9. Phase 7：模型用量记录、Token 统计和基础输出质量提示。

## 阶段报告

- `tasks/v3-2-phase0-embedded-mode-report.md`
- `tasks/v3-2-phase1-work-session-scan-report.md`
- `tasks/v3-2-phase2-evidence-bundle-report.md`
- `tasks/v3-2-agent-signature-feedback-report.md`
- `tasks/v3-2-phase3-conflict-detection-report.md`
- `tasks/v3-2-phase4-evidence-draft-change-report.md`
- `tasks/v3-2-phase5-context-sync-report.md`
- `tasks/v3-2-phase6-confirmed-output-report.md`
- `tasks/v3-2-phase7-model-usage-observability-report.md`

## 关键实现结果

### 证据主链

- 可从绑定项目路径扫描 Git 变化，形成 Work Session 候选。
- 可对 Work Session 生成 Evidence Bundle。
- 可从 Evidence Bundle 生成候选变更。
- 用户确认后的变更进入成果输出和 Agent 上下文同步链路。

### 归因与审查

- Work Session 显示 Agent 类型、任务意图、分支、改动文件数、增删行数、影响模块和证据。
- 用户可修正 Agent 类型和任务意图。
- 修正会保存为 AgentSignatureFeedback，并影响同项目后续 UNKNOWN 归因。
- Evidence Bundle 分离客观证据和 Agent Claim；仅 Git 证据时不伪造 Agent 声明。
- 多个 Evidence Bundle 触碰同一文件时生成待审查冲突。

### 成果沉淀

- 成果输出读取已确认 ProjectChange，不把 PENDING 候选当作官方事实。
- `.projectflow/context/projectflow-context.md` 只同步项目档案和已确认变更。
- 设置页可查看模型调用记录、今日/7天/30天 token 估算和质量提示。

## 验证汇总

已完成验证：

```powershell
& 'C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd' -q test
```

```powershell
npm.cmd run build
```

额外阶段性验证包括：

- `AiOutputControllerTest`
- `WorkSessionScanControllerTest`
- 嵌入式 profile 配置测试
- 多轮前端生产构建

## 保守边界

- 未默认扫描用户主目录或 Agent 全局日志；这符合隐私边界，但也意味着首版 Agent 归因主要依赖 Git 和项目内痕迹。
- 非 Git 项目的文件哈希 baseline 降级路径尚未完整实现。
- Agent 日志授权、撤销、删除索引 UI 尚未完整实现为独立授权管理模块。
- Evidence Bundle 到 ProjectChange 当前按一份证据包生成一条保守候选；复杂拆分/合并可以作为下一轮增强。
- 当前模型 usage 为估算路径；真实 provider usage 需要在模型适配层接入后记录。

## 结论

V3.2 的核心创新链路已经可运行：用户可以在不要求 Agent 主动写总结的情况下，从项目变化生成证据、形成候选、审查确认、沉淀成果，并把确认上下文同步给下一轮 Agent。

