# ProjectFlow 个人开发者项目资产工作台重构计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有后端数据链路和核心 API 的基础上，重构 ProjectFlow 的用户路径、页面入口、可见术语和前端信息架构，把它从“内部证据处理后台”调整为“个人开发者项目资产工作台”。

**Architecture:** 本计划不推倒重写 ProjectFlow，不优先改数据库，不把内部实体删除。后端的 Work Session、Evidence Bundle、Project Change、Project Memory、Fact Source、Evolution Record 等继续作为可信链路存在；前端通过术语映射、页面重组、入口收敛和信息层级调整，把这些内部对象包装成用户能理解的“今日开发、待确认内容、项目资产、可信依据、项目时间线”。

**Tech Stack:** Next.js App Router、TypeScript、React、Tailwind CSS、Spring Boot 3.5.x、Java 17、现有 ProjectFlow API。

---

## 1. 背景与判断

ProjectFlow 的核心方向是成立的：它不应该成为普通任务管理工具、看板、Notion 替代品或 SaaS 管理后台，而应该专注于把真实开发过程中的代码变化、Agent 输出、Git 证据和用户判断，沉淀为可信、可复用、可展示的个人工程资产。

当前主要问题不是功能缺失，而是产品表达仍被内部实体牵引。用户在完成“导入项目 zip -> 查看项目画像 -> 查看目录结构 -> 绑定本地路径”之后，开始被迫理解 Work Session、Evidence Bundle、Project Change、Fact Source、字段来源链、档案变化、sourceId、payload JSON 等系统概念。

这些概念对系统实现有意义，但不应该成为用户默认路径。个人开发者真正关心的是：

- 今天改了什么？
- 这次修改有什么价值？
- 哪些内容值得沉淀？
- 沉淀后能生成什么？
- 这些内容能否用于 README、简历、周报、复盘或作品集？

因此，本轮重构的核心不是继续堆功能，而是收敛主路径、降低内部术语暴露、把证据链路隐藏到可信追溯层，把 ProjectFlow 的第一层体验改成“开发变化 -> 确认入档 -> 项目资产 -> 成果输出”。

## 2. 重构原则

1. **不推倒重写**  
   保留现有后端实体、数据链路和核心 API。除非前端无法表达新路径，否则不优先改数据库结构。

2. **先改用户可见层**  
   第一阶段优先调整页面命名、导航入口、文案、卡片层级、默认展示内容和跳转路径。

3. **内部实体后台化**  
   Work Session、Evidence Bundle、Fact Source 等继续存在，但默认不作为主标题、主入口、主按钮或第一屏内容。

4. **证据不删除，只降层级**  
   ProjectFlow 的可信性来自证据链，不能隐藏到不可访问。做法是把证据放进“查看依据 / 为什么可信 / 原始依据 / 高级信息”。

5. **主路径围绕用户价值**  
   页面组织不再按数据库实体铺开，而按用户要完成的事情组织：理解项目、查看今日开发、确认入档、查看项目资产、生成成果输出。

6. **模型输出仍是候选**  
   AI 和本地规则只生成候选总结、候选资产和候选输出。正式项目资产必须经过用户确认。

7. **避免 ProjectFlow 变成通用 Kanban**  
   “任务”只能作为开发证据的一部分，不能把产品方向拉回普通任务管理。

## 3. 目标一级入口

### 3.1 工作台

**职责：** 告诉用户当前项目状态、下一步应该做什么、最近一次开发发生了什么、已经沉淀出多少资产、现在可以生成什么输出。

工作台应该回答：

- 当前项目是什么？
- 现在最应该做什么？
- 最近有什么变化？
- 已经沉淀了多少能力、决策、风险、经验、成果？
- 现在可以生成哪些材料？

工作台不应该成为系统控制台，也不应该堆满 Agent 协议、证据包状态、内部扫描状态和重复跳转按钮。

### 3.2 项目理解

**职责：** 展示 ProjectFlow 对项目本身的理解。

包括：

- 项目定位
- 技术栈
- 目录结构
- 关键模块
- 关键文件
- 架构说明
- 主要风险
- 分析记录

这里解决的问题是：这个项目是什么。

现有“项目画像”可逐步并入“项目理解”。如果短期内不改路由，可以先保留 `/project-intelligence` 路由，但导航和页面主标题改成“项目理解”。

### 3.3 今日开发

**职责：** 展示刷新变化之后的开发总结。

包括：

- 本次开发做了什么
- 涉及哪些文件或模块
- 系统建议沉淀哪些能力、风险、决策、经验
- 是否有测试或构建证据
- 哪些内容需要确认入档
- 确认入档 / 编辑后入档 / 忽略

这里解决的问题是：我今天改了什么，这些修改有什么价值。

现有 Work Session、Evidence Bundle、Project Change 可以继续支撑这个页面，但用户第一层看到的是“本次开发总结”，不是“证据包状态机”。

### 3.4 项目资产

**职责：** 展示已经被用户确认的长期沉淀。

包括：

- 能力资产
- 技术决策
- 风险记录
- 经验沉淀
- 可展示成果
- 项目时间线

这里解决的问题是：这个项目已经沉淀出了什么。

现有 Project Memory、Fact Source、Growth Timeline、Archive Changes 等应在此收敛展示。

### 3.5 成果输出

**职责：** 把已确认资产转化为可复用材料。

包括：

- README 草稿
- 简历描述
- 项目复盘
- 周报
- 开发日报
- 面试讲解素材

这里解决的问题是：这些沉淀能拿去干什么。

成果输出应该更早出现在工作台和项目资产页，让用户理解“为什么要确认内容、为什么要沉淀资产”。

## 4. 用户可见术语统一

| 内部概念 | 用户可见名称 | 展示层级 |
| --- | --- | --- |
| Work Session | 本次开发 / 今日开发记录 | 今日开发主概念 |
| Evidence Bundle | 原始依据 / 证据依据 | 默认折叠，作为“为什么可信”的一部分 |
| Project Change | 待确认内容 / 待入档内容 | 今日开发与审查主概念 |
| Project Memory | 项目资产 | 项目资产主概念 |
| Fact Source | 可信依据 / 为什么可信 | 资产详情内入口 |
| Growth Timeline | 项目时间线 | 项目资产内入口 |
| Archive Changes | 项目时间线中的档案变化 | 合并，不单独作为一级概念 |
| Tasks 页面 | 开发成果审查 / 待确认内容 | 不再称为普通任务页 |
| AiSuggestion | 候选建议 | 兼容逻辑，不作为主入口 |
| sourceId / payload JSON | 高级调试信息 | 默认隐藏 |

文案规则：

- 第一层标题不使用 Evidence Bundle、EVIDENCE_BUNDLE、UNKNOWN、PENDING、sourceType、candidateCount。
- 第一层摘要不使用“x 个文件，+x/-x 行，置信度 x”作为主叙事。
- 文件、行数、构建、测试是可信依据，不是用户价值的主标题。
- sourceId、payload JSON、内部枚举只在高级信息中展示。

## 5. 新用户主路径

目标路径：

```text
1. 我导入项目。
2. ProjectFlow 帮我看懂项目。
3. 我绑定本地项目。
4. 我开发一天。
5. 我点击刷新今日开发。
6. ProjectFlow 告诉我这次开发做了什么。
7. 我确认哪些内容值得沉淀。
8. ProjectFlow 把它们变成项目资产。
9. 我在项目资产页看到能力、决策、风险、经验和成果。
10. 我用这些资产生成 README、简历描述、项目复盘或周报。
```

这条路径应该在工作台中始终可见，且每一步都要回答：

- 当前状态是什么？
- 下一步做什么？
- 为什么要做？
- 完成后会去哪里？

## 6. 分阶段实施计划

### Phase 0：术语与导航收敛

**目标：** 不动核心后端，先消除用户可见概念冲突。

**涉及文件：**

- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/app/dashboard/page.tsx`
- Modify: `frontend/src/app/tasks/page.tsx`
- Modify: `frontend/src/app/project-intelligence/page.tsx`
- Modify: `frontend/src/app/ai-review/page.tsx`
- Modify: `frontend/src/lib/api.ts`，仅在前端类型名称或展示 helper 必要时调整，不改变 API contract

**任务：**

- [ ] 将导航中的“变更审查”评估为“今日开发”或“开发成果审查”。如果页面仍主要承载 Project Change 审查，优先命名为“开发成果审查”。
- [ ] 将“项目画像 / 项目档案 / 长期档案”在用户可见层统一为“项目理解”和“项目资产”。
- [ ] 将页面中的 Work Session、Evidence Bundle、Project Change、Fact Source 等主标题替换为中文用户语义。
- [ ] 隐藏或降级 sourceId、payload JSON、candidateCount 等调试字段。
- [ ] 保留原路由，避免一次性迁移链接；先改导航文案、页面标题和卡片标题。

**验收：**

- 用户不需要理解 Evidence Bundle 也能知道下一步做什么。
- `tasks` 页面不再让用户误以为这是普通任务管理。
- “项目画像”和“项目档案”的关系不再在同一页面中互相打架。

### Phase 1：重做“刷新今日开发”后的第一屏

**目标：** 刷新变化后优先展示“本次开发总结卡”，而不是证据包状态机。

**涉及文件：**

- Modify: `frontend/src/app/dashboard/page.tsx`
- Modify: `frontend/src/app/tasks/page.tsx`
- Modify: `frontend/src/app/project-changes/[changeId]/page.tsx`
- Modify: related change review components under `frontend/src/components` if present
- Backend optional: `backend/src/main/java/com/projectflow/service/EvidenceDraftChangeService.java` only if现有摘要无法满足中文总结

**任务：**

- [ ] 在工作台或开发成果审查页顶部增加“本次开发总结”区域。
- [ ] 总结区域展示：本次开发做了什么、涉及模块、建议沉淀内容、证据状态、下一步动作。
- [ ] 将“证据包状态、原始文件统计、测试构建详情”放到“查看原始依据”。
- [ ] 审查卡片主按钮保留：确认入档、编辑后入档、忽略。
- [ ] 对没有证据或没有测试的情况给出短提示，例如“缺少测试证据，建议确认前补充一次构建或手动说明”。

**验收：**

- 用户点击“刷新今日开发”后，第一眼看到的是开发成果总结。
- 第一屏不以 Evidence Bundle、行数、置信度作为主要信息。
- 用户能在 10 秒内判断是否进入审查。

### Phase 2：把项目档案页改成项目资产页

**目标：** 默认展示资产卡片，而不是字段表单或内部来源列表。

**涉及文件：**

- Modify: `frontend/src/app/project-intelligence/page.tsx`
- Modify: timeline / fact source / archive changes related routes if already present
- Modify: shared cards or resource timeline components if present

**任务：**

- [ ] 页面结构拆成“项目理解”和“项目资产”两个语义区。
- [ ] 项目资产默认展示五类资产卡片：能力、技术决策、风险、经验、可展示成果。
- [ ] 每张资产卡片展示：名称、说明、最近来源、可复用输出。
- [ ] 每张资产卡片提供“为什么可信？”入口，展开或跳转到来源依据。
- [ ] 字段来源链不再作为主入口名称，改为资产详情下的“可信依据”。

**验收：**

- 用户进入页面后能直接看到“已经沉淀出了什么”。
- 能力不再只是 completedCapabilities 的 bullet，而更像资产卡。
- 用户可以从任意资产追溯到来源，但不会被 sourceId 或 payload 打断阅读。

### Phase 3：合并成长时间线和档案变化为项目时间线

**目标：** 不再让用户在两个相似概念中选择。

**涉及文件：**

- Modify or Create: `frontend/src/app/project-intelligence/timeline/page.tsx`
- Modify: existing archive changes route if present
- Modify: `frontend/src/app/project-intelligence/page.tsx`

**任务：**

- [ ] 统一入口名称为“项目时间线”。
- [ ] 在时间线内使用筛选区分：全部、能力沉淀、技术决策、风险变化、经验记录、成果输出。
- [ ] 档案变化作为时间线的一种事件类型，不再作为并列主入口。
- [ ] 列表第一层只展示日期、中文标题、变化类型、写入字段、一句话摘要。
- [ ] 完整详情进入详情页或详情视图，不在列表中用长 `<details>` 撑开。

**验收：**

- 用户只需要一个入口就能回答“我的项目是怎么一步步变强的”。
- 长期使用 30 天后，时间线仍然可浏览、可筛选、不卡在长文本堆叠。

### Phase 4：提前成果输出入口

**目标：** 让用户更早理解沉淀资产的最终用途。

**涉及文件：**

- Modify: `frontend/src/app/dashboard/page.tsx`
- Modify: `frontend/src/app/project-intelligence/page.tsx`
- Modify: `frontend/src/app/ai-review/page.tsx`

**任务：**

- [ ] 在工作台展示“当前可生成”卡片：README 草稿、简历描述、项目复盘、周报。
- [ ] 在项目资产页展示“可复用输出”区域，说明哪些资产可用于哪些输出。
- [ ] 成果输出页展示每种输出所需来源：项目资产、项目时间线、每日回顾、开发证据。
- [ ] 缺少确认资产时，不生成空泛模板，提示“先确认今日开发内容”或“先补充项目资产”。

**验收：**

- 用户在完成首次入档前就能知道入档的用途。
- 成果输出不再像孤立的最后一页，而是主路径的价值出口。

### Phase 5：清理本地项目接入高级入口

**目标：** 默认路径只保留用户能理解的动作，协议细节放进高级区域。

**涉及文件：**

- Modify: `frontend/src/app/dashboard/page.tsx`
- Modify: local project connection components if present
- Backend unchanged unless API error文案需要补充

**任务：**

- [ ] 默认只保留两个主动作：绑定本地项目、刷新今日开发。
- [ ] 将写入/刷新协议、扫描 Agent Result、同步确认上下文、复制规则放入“Agent 高级设置”。
- [ ] 高级设置每个动作补一句说明：给谁用、什么时候用、执行后会发生什么。
- [ ] 保存路径和绑定项目语义统一，避免同时出现两个相似按钮。

**验收：**

- 新用户不会在“保存路径、写入协议、扫描 Agent Result、同步上下文、复制规则、刷新变化”之间迷路。
- 熟悉 Agent 工作流的用户仍能找到高级能力。

### Phase 6：审查页从“任务页”转为“开发成果入库台”

**目标：** 明确该页面职责是确认哪些内容可以进入项目资产，而不是管理任务。

**涉及文件：**

- Modify: `frontend/src/app/tasks/page.tsx`
- Modify: `frontend/src/app/project-changes/[changeId]/page.tsx`
- Modify: `frontend/src/app/project-changes/[changeId]/evidence/page.tsx` if already present or planned
- Modify: change review components

**任务：**

- [ ] 页面标题改为“开发成果审查”或“待确认内容”。
- [ ] 列表卡片展示：中文标题、一句话说明、将写入哪里、关键证据、状态、主操作。
- [ ] 详情页优先展示“用户实际改了什么”和“入库预览”。
- [ ] 证据详情进入独立页或独立 tab，不在第一屏直接展开长文件列表。
- [ ] 采纳后给出明确去向：进入项目资产、项目时间线、可信依据。

**验收：**

- 用户能理解“采纳”不是完成任务，而是把开发成果沉淀为资产。
- 采纳后用户知道去哪里回看。

## 7. 数据与 API 策略

本轮默认复用现有 API：

- Work Session / Evidence Bundle 继续用于生成今日开发依据。
- Project Change 继续用于待确认内容。
- Project Memory 继续用于项目资产存储。
- Project Fact Source 继续用于可信依据。
- Project Evolution Record / Archive Changes 继续用于项目时间线。
- AI Outputs 继续用于成果输出。

仅在以下情况考虑后端小改：

1. 前端无法从现有字段组合出中文成果摘要。
2. 候选变更标题长期以 Evidence Bundle 或行数为主，严重影响用户判断。
3. 采纳后无法稳定形成“资产 -> 可信依据 -> 时间线 -> 输出来源”的闭环。

后端小改原则：

- 优先改现有 service/helper，不新增大服务。
- 不新增依赖。
- 不让模型结果绕过用户确认。
- 不破坏现有接口返回结构；必要字段可追加，不删除。

## 8. 验收标准

### 8.1 独立上手验收

新用户不读文档、不依赖 Agent 解释，能独立完成：

```text
导入项目 -> 看到项目理解 -> 绑定本地项目 -> 开发一天 -> 刷新今日开发 -> 审查待确认内容 -> 形成项目资产 -> 生成成果输出
```

并且每一步都知道：

- 当前是什么状态。
- 下一步去哪。
- 为什么要做。
- 完成后进入哪里。

### 8.2 术语验收

- 第一层不再暴露 Work Session、Evidence Bundle、Fact Source、sourceId、payload JSON。
- “项目画像 / 项目档案 / 长期档案 / 项目资产”不再混用。
- `tasks` 不再作为用户可见主概念误导用户。

### 8.3 信息层级验收

任意摘要页第一屏：

- 不展示超过 3 条长路径。
- 不展示完整 Markdown。
- 不内联展开完整证据文本。
- 不把内部枚举当主标题。
- 必须有明确下一步动作。

### 8.4 资产化验收

每条已确认资产至少能回答：

- 资产名称是什么。
- 它说明了项目哪方面能力或经验。
- 为什么可信。
- 来源于哪次开发或哪条确认记录。
- 可以用于哪些输出。

### 8.5 成果输出验收

每种输出至少说明：

- 使用了哪些已确认来源。
- 缺少哪些来源。
- 如果暂时不能生成高质量内容，用户下一步应该补什么。

## 9. 不做事项

本轮不做：

- 不重写后端核心模型。
- 不删除证据链路。
- 不把 ProjectFlow 改成通用任务管理或看板。
- 不新增复杂权限体系。
- 不引入新前端状态管理库。
- 不把 AI 候选直接写入正式资产。
- 不用更多卡片堆叠替代信息架构整理。

## 10. 推荐执行顺序

1. 先做 Phase 0：术语与导航收敛。
2. 再做 Phase 1：刷新今日开发后的第一屏体验。
3. 再做 Phase 6：开发成果审查页语义调整。
4. 再做 Phase 2：项目资产页改造。
5. 再做 Phase 3：项目时间线合并。
6. 再做 Phase 4：成果输出提前。
7. 最后做 Phase 5：本地项目接入高级入口清理。

这个顺序的原因是：先解决用户最先遇到的概念和路径问题，再调整长期资产承载页，最后处理高级 Agent 能力的入口分层。这样能在不大改后端的情况下，最快把 ProjectFlow 的产品心智从“证据处理后台”拉回“个人开发者项目资产工作台”。

## 11. 与现有 V3.2 文档的关系

本计划是总纲型重构计划，承接已有局部计划和报告：

- `docs/v3.2-dashboard-interaction-cleanup-plan-2026-06-21.md`：已提出工作台下一步动作和入口去重，本计划将其提升到完整主路径。
- `docs/v3.2-long-term-record-navigation-plan-2026-06-21.md`：已提出长期记录专页和来源链路，本计划进一步统一为“项目资产 / 项目时间线 / 可信依据”。
- `docs/v3.2-change-review-clarity-report-2026-06-24.md`：已提出变更审查要从文件变化转为开发成果入库，本计划将其纳入“今日开发 -> 待确认内容 -> 项目资产”的主路径。

执行时应优先复用这些文档中的页面级细节，不重复制造新概念。
