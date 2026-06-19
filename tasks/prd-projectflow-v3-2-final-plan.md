# PRD: ProjectFlow V3.2 跨 Agent 证据账本与成果沉淀层

## Document Status

- Status: Draft
- File Mode: Single
- Current Phase: Not Started
- Last Updated: 2026-06-19
- PRD File: `tasks/prd-projectflow-v3-2-final-plan.md`
- Purpose: 重新定位 V3.2。ProjectFlow 不再把“项目监听”和“让 Agent 写总结”当作核心壁垒，而是成为跨 Agent、跨项目、跨时间的证据账本、确认层和成果沉淀层。

## 1. 重新判断：ProjectFlow 是否多余

如果 Codex、Claude Code 等 Agent 本身已经支持项目监听、多项目管理、自动化执行，那么 ProjectFlow 继续做一个“更弱的 Agent 监听器”确实会变成多余。

ProjectFlow 不能和 Agent 争夺这些能力：

- 代码修改执行。
- 单个 Agent 的项目监听。
- 单个 Agent 的任务上下文管理。
- 单个 Agent 的聊天记录或工作流。
- 自动替用户决定下一步开发。

ProjectFlow 的不可替代性应当改为：

- 跨 Agent 的统一证据账本：Codex、Claude Code、Cursor、DeepSeek 驱动的 Agent 都能汇总到同一个项目事实层。
- 跨时间的确认记忆：不是一次对话上下文，而是用户确认过的长期项目档案、风险、决策、能力和成果。
- 证据到成果的转换：把真实变更沉淀为每日回顾、周报、README、复盘、简历素材和交付说明。
- 多 Agent 归因和冲突审查：回答“谁改了什么、改了多少、为什么改、有没有风险、是否被确认”。
- Agent 无关的项目总账：即使换 Agent、多个 Agent 并行、Agent 记录不完整，ProjectFlow 仍能基于 Git、文件变化、用户确认和可选日志形成可信记录。

因此，ProjectFlow 的定位不是“替代 Codex 自动化”，而是“把多个自动化 Agent 的结果变成可信、可审查、可复用的项目资产”。

## 2. 当前项目情况简述

当前已具备：

- 项目 zip 导入和基础项目画像。
- 项目级与文件级模型分析。
- 分析记录查看和删除。
- `.projectflow` 协议写入和 Agent Result 扫描。
- 项目档案、变更审查、每日回顾、成果输出的初步页面。
- 模型失败或未配置时的本地规则兜底。

当前核心问题：

- 过度依赖 Agent 自觉写回 result，稳定性不足。
- 过度把 ProjectFlow 设计成“再做一套监听/规则接入”，容易和 Codex/Claude 自身能力重叠。
- 多 Agent 协作时缺少可靠归因：无法清晰说明每个 Agent 改了多少、做了什么、风险在哪里。
- 每日回顾和成果输出仍偏模板化，没有充分利用已确认的项目证据。
- 项目档案与 Agent 上下文之间仍缺少稳定的“确认后同步”机制。

## 3. V3.2 产品目标

V3.2 的目标是建立一条证据主链：

```text
多个 Agent 工作
  -> ProjectFlow 获取证据
  -> 归因到 Agent / 会话 / 任务 / 文件
  -> 生成候选总结、风险、决策、成果素材
  -> 用户确认
  -> 写入长期项目账本
  -> 生成每日回顾、周报、README、复盘等成果
  -> 同步给下一轮 Agent 作为确认上下文
```

V3.2 的一句话目标：

> 不做另一个 Agent，而做多个 Agent 的事实账本、审查台和成果工厂。

## 4. 设计原则

- 不和 Codex/Claude 的自动化能力重复竞争。
- 不强制 Agent 稳定写总结，因为任何提示词规则都可能失效。
- 不要求用户复制粘贴机械信息。
- 任何 Agent 输出都只是“声明”，必须被 Git diff、文件变化、测试结果或用户确认支撑。
- ProjectFlow 只保存确认事实和可追溯候选，不把模型猜测直接当事实。
- 同一项目可以有多个 Agent、多次会话、多条分支、多种证据来源。
- 详细内容进入详情页，首页只展示今日待审查、风险、成果入口和同步状态。

## 5. 核心概念

### 5.1 Project Ledger

项目总账。它不是聊天记录，也不是任务看板，而是项目事实的长期存储层。

包含：

- 已确认能力。
- 已确认阶段。
- 已确认技术决策。
- 已确认风险。
- 已确认变更。
- 已确认成果素材。
- 来源证据。
- 用户确认记录。

### 5.2 Evidence Bundle

证据包。每次整理变化时，ProjectFlow 把多种来源归并成一个证据包。

来源可以包括：

- Git status / diff / numstat。
- Commit 记录。
- 文件哈希和快照对比。
- Agent Result 文件。
- Codex / Claude Code 可导出的会话摘要或日志。
- 用户手动补充的任务说明。
- 构建、测试、Lint 结果。

### 5.3 Work Session

一次工作会话。它用于归因，不要求 ProjectFlow 控制 Agent。

字段建议：

- sessionId。
- projectId。
- agentType：Codex、Claude Code、Cursor、Other。
- agentName。
- taskIntent。
- branchName。
- baseCommit。
- startTime。
- endTime。
- evidenceBundleId。
- attributionConfidence：high、medium、low、unknown。

### 5.4 Agent Claim

Agent 自己声称做了什么。它是证据之一，不是事实本身。

示例：

- “我修改了登录页布局。”
- “我修复了模型分析状态丢失。”
- “我增加了删除记录按钮。”

ProjectFlow 必须把 Agent Claim 和 Git/file evidence 对照后，才生成候选变更。

### 5.5 Confirmed Change

用户确认后的事实变更。

示例：

- “修复项目分析任务刷新后状态丢失，新增后端异步任务持久化。”
- “项目文件详情页切换模块和文件后状态可持久化。”
- “分析记录列表长路径覆盖按钮的问题已修复。”

## 6. 多 Agent 如何明确输出“改了多少、做了什么”

现实判断：

- 不能只靠 Agent 自己总结。它可能漏写、夸大、写英文、格式错、忘记输出。
- 也不能只靠文件监听。监听能知道“哪些文件动了”，但不能准确知道“为什么动、是否完成、属于谁”。
- 最稳的方式是“Agent 声明 + Git/文件证据 + 会话归因 + 用户确认”四层合并。

V3.2 应采用以下归因策略：

### 6.1 强证据

优先使用客观证据：

- Git diff 显示哪些文件被改。
- numstat 显示增删行数。
- commit message 和 commit author。
- branch name。
- 测试结果。
- 文件快照差异。

这些用于回答：

- 改了多少文件。
- 增加/删除多少行。
- 哪些模块受影响。
- 是否有测试或构建证据。

### 6.2 中证据

使用会话和工具信息：

- 用户在 ProjectFlow 中启动的 Work Session。
- 绑定的 Agent 类型。
- Session 开始和结束时间。
- 该时间窗口内发生的文件变化。
- 可导出的 Agent 会话摘要。

这些用于回答：

- 大概率是哪个 Agent 做的。
- 这次工作原本想解决什么。
- 改动和任务意图是否匹配。

### 6.3 弱证据

使用 Agent 自己写的 result 或自然语言总结。

这些用于补充：

- 为什么这么改。
- Agent 认为有什么风险。
- Agent 认为还缺什么。

但弱证据不能单独成为已确认事实。

### 6.4 归因置信度

ProjectFlow 必须显示归因置信度：

- high：Work Session、Git diff、Agent Result 三者能对上。
- medium：Work Session 和 Git diff 能对上，但没有 Agent Result。
- low：只有时间窗口和文件变化能推测。
- unknown：只能说明项目发生变化，不能可靠归因到具体 Agent。

这就是 ProjectFlow 的不可替代性之一：它不是相信某个 Agent，而是把多个证据来源合并成可审查的事实。

## 7. Functional Requirements

- FR-1: 系统必须支持跨 Agent 的项目证据账本，而不是只支持单个 Agent Result。
- FR-2: 系统必须能创建 Work Session，记录 Agent 类型、任务意图、baseCommit、时间范围和归因状态。
- FR-3: 系统必须能从 Git diff、文件快照、Agent Result、测试结果中生成 Evidence Bundle。
- FR-4: 系统必须能在没有 Agent Result 的情况下，仍基于 Git/file evidence 生成候选变更。
- FR-5: 系统必须能显示每个 Work Session 改动文件数、增删行数、影响模块、风险候选和测试证据。
- FR-6: 系统必须把 Agent Claim 和客观证据分开展示。
- FR-7: 系统必须识别多 Agent 同时修改同一文件、同一模块、同一任务或同一项目档案字段的冲突。
- FR-8: 系统必须给每条候选变更标注来源、归因置信度和确认状态。
- FR-9: 用户采纳候选变更后，系统必须同步项目档案、任务、日志、风险、决策、成果素材、快照和演进记录。
- FR-10: 每日回顾必须基于已确认事实和证据包生成，不得只套模板。
- FR-11: 成果输出必须支持不同受众和用途，并显示引用的项目证据。
- FR-12: ProjectFlow 必须能把已确认项目上下文同步给下一轮 Agent，但不控制 Agent 的执行。
- FR-13: 模型不可用时必须用本地规则生成保守候选，并明确标记。
- FR-14: Token 使用、失败原因、模型输出质量必须可追踪。

## 8. Non-Functional Requirements

- NFR-1: 不得把 `.env`、密钥、二进制、大文件、依赖目录、构建产物发送给模型。
- NFR-2: 归因不确定时必须明确显示 unknown 或 low，不能假装知道是哪个 Agent 做的。
- NFR-3: 多 Agent 结果导入和证据包生成必须幂等。
- NFR-4: 模型输出中文优先，技术标识可以保留英文。
- NFR-5: ProjectFlow 不应成为通用 Agent 编排器。
- NFR-6: 首页不堆重复卡片，只展示证据待审查、今日变化、风险和输出入口。
- NFR-7: 所有项目数据必须按用户和项目隔离。

## 9. V3.2 实施阶段

### Phase 1: 重新定位工作台为证据入口

Objective: 从“接入 Agent 规则和监听文件”改成“创建 Work Session 和收集证据”。

Implementation checklist:

- [ ] 首页主入口改为“开始一次工作记录”，而不是强调“写入 Agent 规则”。
- [ ] 创建 Work Session 时记录 agentType、taskIntent、branch、baseCommit、startTime。
- [ ] 用户可以选择 Codex、Claude Code、Cursor、Other，也可以选择“不指定 Agent”。
- [ ] Work Session 不控制 Agent，只记录证据归属边界。
- [ ] 保留 Agent 规则写入，但降级为辅助功能：只用于提示 Agent 读取确认上下文和尽量输出 result。
- [ ] 规则写入不得作为主流程必需步骤。
- [ ] 工作台显示正在进行的 Work Session、捕获到的文件变化、当前归因置信度。
- [ ] 支持用户手动结束 Work Session 并触发证据整理。

Validation:

- [ ] 不写任何 Agent 规则，也能创建 Work Session。
- [ ] 不指定 Agent，也能记录项目变化并生成候选证据。
- [ ] 已绑定 Agent 的 session 能显示 agentType 和 baseCommit。

### Phase 2: Evidence Bundle 生成

Objective: 让 ProjectFlow 从客观证据中知道“改了多少、改了哪里、影响什么”。

Implementation checklist:

- [ ] 对 Git 项目读取 `git status --porcelain`、`git diff --name-status`、`git diff --numstat`。
- [ ] 记录 changedFiles、addedLines、deletedLines、changeType、moduleName。
- [ ] 对非 Git 项目使用 baseline 文件哈希和最新哈希对比。
- [ ] 收集可选 Agent Result，但只作为 Agent Claim。
- [ ] 收集可选测试、构建、Lint 输出。
- [ ] 生成 Evidence Bundle，并保存来源清单。
- [ ] Evidence Bundle 必须排除敏感路径和大文件。

Validation:

- [ ] 修改多个文件后能生成文件数和增删行统计。
- [ ] 没有 Agent Result 时仍能生成候选变更。
- [ ] 敏感文件不会进入 Evidence Bundle。

### Phase 3: 归因与冲突识别

Objective: 多 Agent 协作时，尽量明确“谁做了什么”，不确定时诚实标记。

Implementation checklist:

- [ ] 新增 attributionConfidence：high、medium、low、unknown。
- [ ] high 条件：Work Session、Git evidence、Agent Claim 三者一致。
- [ ] medium 条件：Work Session 和 Git evidence 一致，但缺少 Agent Claim。
- [ ] low 条件：只能通过时间窗口和文件变化推测。
- [ ] unknown 条件：无法归因到具体 Agent。
- [ ] 识别同一时间窗口内多个 session 修改同一文件。
- [ ] 识别多个 Agent 对同一模块、任务、风险、项目档案字段的冲突。
- [ ] 冲突进入待审查队列，不自动判定谁正确。

Validation:

- [ ] 构造两个 session 修改同一文件，系统能显示冲突。
- [ ] 缺少 Agent Claim 时归因不超过 medium。
- [ ] 无法判断来源时显示 unknown。

### Phase 4: 候选变更审查与确认

Objective: 把证据包转成可审查、可确认的项目事实。

Implementation checklist:

- [ ] 模型可用时，根据 Evidence Bundle 生成中文候选变更、风险、测试建议、成果素材。
- [ ] 模型不可用时，用本地规则生成保守候选。
- [ ] 每条候选必须显示：证据来源、影响文件、归因置信度、Agent Claim、客观证据。
- [ ] 用户可编辑、合并、拆分、采纳、忽略候选。
- [ ] 采纳时同步项目档案、任务、日志、风险、决策、成果素材、快照和演进记录。
- [ ] 采纳失败必须事务回滚。

Validation:

- [ ] 一份 Evidence Bundle 可拆成多条候选变更。
- [ ] 采纳后相关档案和演进记录一致。
- [ ] 忽略候选不会污染确认事实。

### Phase 5: Project Ledger 与 Agent 上下文同步

Objective: 把确认事实变成长期资产，并回馈给下一轮 Agent。

Implementation checklist:

- [ ] 建立 Project Ledger 视图：能力、阶段、风险、决策、成果素材、最近变化。
- [ ] 只展示用户确认过的事实，候选内容不能混入。
- [ ] 支持按时间、Agent、模块、任务筛选确认事实。
- [ ] 自动生成 `.projectflow/context`，供任意 Agent 读取。
- [ ] context 必须简短、中文优先、面向下一轮开发。
- [ ] context 同步失败时显示待同步状态和重试入口。

Validation:

- [ ] 确认变更后 Ledger 更新。
- [ ] `.projectflow/context` 不包含未确认候选。
- [ ] 换 Agent 后仍能读取同一份确认上下文。

### Phase 6: 每日回顾与成果输出升级

Objective: 把账本事实转成不同用途的人类可读成果。

Implementation checklist:

- [ ] 每日回顾基于 Confirmed Change 和 Evidence Bundle，不再只靠模板。
- [ ] 支持工程日志、管理汇报、个人复盘、风险清单。
- [ ] 成果输出支持周报、README、项目复盘、简历素材、发布说明。
- [ ] 支持受众：自己、团队、负责人、开源用户、招聘方。
- [ ] 输出必须带来源引用和证据不足提示。
- [ ] 用户编辑后保存为版本。

Validation:

- [ ] 无确认事实时不生成虚假总结。
- [ ] 不同模式输出结构和语气不同。
- [ ] README、周报、简历素材都能追溯来源。

### Phase 7: Token、质量和可观测性

Objective: 让模型调用成本、失败原因和输出质量可见。

Implementation checklist:

- [ ] 记录 ModelUsageRecord。
- [ ] 区分真实 usage 和估算 usage。
- [ ] 记录 operation、modelName、latencyMs、status、errorType。
- [ ] 设置页展示今日、7 天、30 天 Token。
- [ ] 对模型输出检查中文、来源引用、未确认事实引用。

Validation:

- [ ] 模型超时能显示失败原因。
- [ ] 估算 Token 不冒充真实 usage。
- [ ] 输出引用未确认事实时能被拦截或警告。

## 10. 推荐用户流程

### 首次接入项目

1. 导入 zip 或绑定本地项目路径。
2. 运行项目分析，形成初始项目画像。
3. 可选：写入 Agent 规则，让 Agent 读取 ProjectFlow 确认上下文。
4. ProjectFlow 建立 Project Ledger。

### 白天多 Agent 开发

1. 用户在 ProjectFlow 点击“开始一次工作记录”。
2. 选择 Agent 类型和任务意图，也可以跳过 Agent 选择。
3. 用户正常使用 Codex、Claude Code 或其他 Agent 工作。
4. ProjectFlow 不干预 Agent 执行，只记录 session 边界和证据。

### 晚上整理变化

1. 用户点击“整理本次变化”。
2. ProjectFlow 生成 Evidence Bundle。
3. 系统显示：改了多少文件、增删多少行、影响哪些模块、可能是谁做的、置信度多少、有什么风险。
4. 模型生成中文候选总结，用户审查并确认。
5. 确认内容进入 Project Ledger。
6. 用户生成每日回顾、周报、README 或复盘。
7. ProjectFlow 同步确认上下文给下一轮 Agent。

## 11. 数据和 API 建议

建议新增或等效扩展：

- `WorkSession`
- `EvidenceBundle`
- `EvidenceSource`
- `AgentClaim`
- `AttributionResult`
- `ChangeConflict`
- `CandidateChange`
- `ConfirmedChange`
- `ProjectLedgerEntry`
- `OutputDraftVersion`
- `ModelUsageRecord`

建议 API：

- `POST /api/projects/{projectId}/work-sessions`
- `POST /api/work-sessions/{sessionId}/finish`
- `POST /api/work-sessions/{sessionId}/evidence-bundles`
- `GET /api/projects/{projectId}/evidence-bundles`
- `POST /api/evidence-bundles/{bundleId}/draft-changes`
- `POST /api/candidate-changes/{changeId}/accept`
- `GET /api/projects/{projectId}/ledger`
- `POST /api/projects/{projectId}/context/sync`
- `POST /api/projects/{projectId}/daily-reviews/draft`
- `POST /api/projects/{projectId}/outputs/draft`

## 12. Success Criteria

- SC-1: 不依赖 Agent Result，ProjectFlow 也能说清本次改了多少文件、增删多少行、影响哪些模块。
- SC-2: 多个 Agent 同一项目工作时，ProjectFlow 能显示来源、证据和归因置信度。
- SC-3: 无法确定归因时，系统明确显示 low 或 unknown，而不是猜测。
- SC-4: 用户能从一份 Evidence Bundle 审查并确认多个候选变更。
- SC-5: 确认后的项目事实能进入 Project Ledger，并同步给下一轮 Agent。
- SC-6: 每日回顾和成果输出来自确认事实和证据，不是固定模板。
- SC-7: ProjectFlow 的价值不依赖某一个 Agent 是否支持监听、规则或导出日志。

## 13. Non-Goals

- NG-1: 不做通用 Agent 编排器。
- NG-2: 不替代 Codex/Claude 的项目监听和自动化执行。
- NG-3: 不自动判定哪个 Agent 正确。
- NG-4: 不把模型生成内容直接写成确认事实。
- NG-5: 不要求用户为每个 Agent 手动复制粘贴总结。

## 14. 最终执行优先级

1. 先做 Work Session 和 Evidence Bundle。
2. 再做 Git/file evidence 统计和安全过滤。
3. 然后做归因置信度和冲突识别。
4. 再做候选变更审查和确认事务。
5. 然后做 Project Ledger 和 context 同步。
6. 最后做每日回顾、成果输出、Token 和质量统计。

V3.2 完成后，ProjectFlow 的核心卖点应当变成：

> 你可以用任意 Agent 开发，但最终由 ProjectFlow 告诉你：今天到底改了什么、谁可能改的、证据是什么、风险在哪里、哪些已确认、能沉淀成什么成果。

## 15. Change Log

- 2026-06-19: 创建最终 V3.2 计划书。
- 2026-06-19: 重新定位为跨 Agent 证据账本与成果沉淀层，删除把 ProjectFlow 当作重复监听器或依赖 Agent 稳定输出 result 的设计。
