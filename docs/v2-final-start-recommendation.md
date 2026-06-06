# ProjectFlow V2 Final Start Recommendation

## 结论

下一阶段不要继续做传统“总览 + 大卡片 + 手动任务表单”的项目管理系统。ProjectFlow 应该围绕开发者的真实工作方式来设计：

- 人负责提出需求、判断方向、确认项目事实。
- ProjectFlow 负责整理项目资料、发现信息缺口、生成候选路线、生成 agent 可读的工作说明。
- agent 负责在真实项目文件夹里执行开发工作。
- ProjectFlow 负责识别 agent 产出的结果，并把它转成待确认的项目更新。

最推荐的开工方案是：直接把 `.projectflow` 项目文件夹桥接作为下一阶段主线，同时保留复制粘贴作为兜底。

这里要明确一件事：ProjectFlow 生成 agent 工作说明是可选项，不是必选项。很多开发者会直接在 agent 里说“我要改什么功能”，这是正常工作方式，ProjectFlow 不应该强制用户先回到项目管理软件里生成任务书。

真正必须标准化的是“完工写回协议”：无论任务是 ProjectFlow 生成的，还是用户直接在 agent 里发起的，agent 完成后都应该按固定格式把结果写到 `.projectflow` 目录，ProjectFlow 再扫描并生成待确认更新。

## 为什么 AI 初始分析很关键

“AI 形成项目档案和初始任务建议”不能理解为 AI 替用户决定项目方向。它应该是一个受控分析流程：

1. 先读取项目事实。
2. 再整理用户已经表达过的需求。
3. 再指出信息缺口。
4. 再给出几个可选路线。
5. 最后才生成任务候选。

AI 不能凭空决定“项目应该加什么功能”。真正的灵感来源仍然是用户。ProjectFlow 的价值是把用户零散表达的想法、项目已有代码、历史决策和当前工作目标整理成 agent 能执行的上下文。

如果用户没有说清楚需求，ProjectFlow 也不应该假装已经知道方向，而应该给出“决策选项”，让用户选择。

## 初始分析需要的信息

### 1. 项目硬事实

来自项目文件夹或 ZIP：

- 文件树。
- README 和文档。
- package、pom、requirements、配置文件。
- 主要源码入口。
- 路由、页面、接口、实体、服务、测试目录。
- 最近的开发日志或已有 ProjectFlow 记录。

这些信息用来形成“项目档案”的事实部分。

### 2. 用户需求

来自用户输入：

- 这次想解决什么问题。
- 哪些功能优先。
- 哪些方向不要做。
- UI 风格偏好。
- 成本、时间、技术复杂度限制。
- 对测试节奏的要求。

例如这次已经明确的需求包括：

- 面向开发者，不做商城、酒店、图书馆管理系统式产品。
- 项目导入和项目管理要放在最显眼的位置。
- 项目档案并入项目管理。
- 任务看板尽量由 AI 和 agent 工作结果驱动。
- UI 要务实、紧凑，不要满屏大卡片。
- 流程稳定前先不做完整测试。

### 3. 历史项目状态

来自 ProjectFlow 自身：

- 已确认项目档案。
- 已确认任务。
- 已确认决策。
- 已确认风险。
- 已确认开发日志。
- 待确认 AI 建议。
- 上一次 agent 工作结果。

这些信息决定后续工作说明不能只看当前文件，而要知道项目过去为什么这样设计。

## AI 应该生成什么建议

### 1. 项目档案建议

项目档案不是宣传介绍，而是开发者工作上下文：

- 项目目标。
- 当前阶段。
- 技术栈。
- 目录结构。
- 核心模块。
- 数据模型。
- 关键接口。
- 已知约束。
- 已知风险。
- 已确认的产品方向。
- 明确不要做的方向。

这些内容需要区分“已确认”和“AI 推断”。AI 推断不能直接变成项目事实。

### 2. 信息缺口建议

如果需求不清楚，ProjectFlow 应该直接列出缺口，而不是乱拆任务：

- 目标用户是否明确。
- 当前最重要的使用场景是什么。
- 要新增功能还是优化现有流程。
- 是否需要后端持久化。
- 是否允许写入项目文件夹。
- 是否需要兼容 ZIP 导入。
- 测试做到什么程度。

这些缺口可以展示成紧凑的选择项，让用户快速点选。

### 3. 决策选项建议

当用户没有明确需求时，ProjectFlow 可以给出路线选择，而不是替用户拍板：

- 继续完善现有核心流程。
- 优先修复明显体验问题。
- 优先补齐 agent 交互闭环。
- 优先整理项目档案和任务建议。
- 优先做最终测试和稳定性检查。

每个选项都应该包含：

- 适合什么情况。
- 成本高低。
- 风险。
- 会影响哪些页面或模块。
- 推荐程度。

### 4. 初始任务候选

任务候选必须来自项目事实和用户需求，不能是模板任务。

每个候选任务应包含：

- 任务标题。
- 来源依据。
- 目标。
- 验收标准。
- 影响范围。
- 预计成本。
- 风险。
- 是否需要用户确认需求。
- 是否适合交给 agent 执行。

用户确认后，候选任务才变成真实任务。

### 5. Agent 工作说明草案

ProjectFlow 可以基于已确认任务生成 agent 工作说明，但工作说明必须带上：

- 当前项目档案摘要。
- 用户明确需求。
- 用户明确不要做的方向。
- 当前任务目标。
- 验收标准。
- 允许修改的范围。
- 不要引入的东西。
- 完成后必须写回的结果文件路径。

这会直接影响 agent 的输出质量，所以必须来自已确认信息和明确标注的 AI 推断。

## 推荐的文件桥接方案

下一阶段直接做 `.projectflow` 文件夹桥接，不只停留在复制粘贴。

ProjectFlow 在项目根目录生成：

```text
.projectflow/
  agent-protocol.md
  context/
    project-profile.md
    confirmed-decisions.md
    known-risks.md
    requirements.md
    update-history.md
  tasks/
    PF-001/
      brief.md
      result.md
      status.json
  inbox/
    agent-result.md
```

### 文件用途

`agent-protocol.md`：

保存 agent 必须遵守的 ProjectFlow 写回协议。开发者即使不通过 ProjectFlow 生成任务说明，也可以让 agent 读取这个文件，完工后按协议写入结果。

`project-profile.md`：

保存当前项目档案，给 agent 快速理解项目。

`requirements.md`：

保存用户这次说清楚的需求、非目标、UI 风格、测试节奏。

`confirmed-decisions.md`：

保存用户已经确认过的技术和产品决策。

`known-risks.md`：

保存已知风险，避免 agent 重复踩坑。

`update-history.md`：

保存 ProjectFlow 已经确认过的开发流程记录摘要，帮助 agent 理解这个项目最近做过什么、为什么这样做。

`tasks/PF-001/brief.md`：

保存某个任务的 agent 工作说明。

`tasks/PF-001/result.md`：

agent 完成后写回结果。

`tasks/PF-001/status.json`：

保存任务 ID、当前状态、生成时间和 ProjectFlow 识别状态。

`inbox/agent-result.md`：

兜底入口。agent 不知道具体任务目录时，也可以把结果写到这里。

## 两种 agent 发起方式

### 方式一：ProjectFlow 生成任务说明

适合这些情况：

- 用户想先让 ProjectFlow 整理项目资料。
- 用户希望 AI 给出任务候选和风险提示。
- 用户不确定下一步应该怎么拆任务。
- 任务需要带上明确验收标准和项目上下文。

流程是：

1. 用户在 ProjectFlow 输入需求或选择任务候选。
2. ProjectFlow 写入 `.projectflow/tasks/<task-id>/brief.md`。
3. agent 读取 brief 并执行。
4. agent 写回 result。
5. ProjectFlow 扫描 result 并生成待确认更新。

### 方式二：开发者直接在 agent 里提需求

适合这些情况：

- 用户已经很清楚要改什么。
- 用户正在 agent 里连续开发，不想切回 ProjectFlow。
- 需求比较临时，不需要先做完整规划。
- 用户只希望 ProjectFlow 记录开发流程和项目状态变化。

流程是：

1. 用户直接在 agent 里描述要改的功能。
2. agent 开始前读取 `.projectflow/agent-protocol.md` 和 `.projectflow/context/*`。
3. agent 完成后写入 `.projectflow/inbox/agent-result.md`，或新建 `.projectflow/inbox/<timestamp>-agent-result.md`。
4. ProjectFlow 扫描 inbox。
5. ProjectFlow 生成待确认任务、日志、风险和项目档案更新。

这条路径非常重要，因为它贴近真实开发者习惯：用户主要在 agent 里用自然语言推进开发，ProjectFlow 只负责识别、整理和确认项目追踪信息。

## 全局记忆和项目协议

可以把 ProjectFlow 写回规则放到 agent 的全局记忆里，但不建议把具体项目规划长期塞进全局记忆。原因是：

- 不同项目的目标、风险、技术栈不同，全局记忆容易污染其他项目。
- 全局记忆不一定被所有 agent 稳定读取。
- 具体项目状态应该以项目目录里的 `.projectflow` 文件为准。

更稳妥的做法是“两层规则”：

### 1. 全局轻量规则

全局记忆只放一条通用规则：

```text
如果当前项目根目录存在 `.projectflow/agent-protocol.md`，开始工作前先读取它。完成开发任务后，必须按该协议把结果写入 `.projectflow/inbox/` 或对应任务目录的 `result.md`，不要直接修改 ProjectFlow 的真实任务状态。
```

ProjectFlow 可以提供“一键复制全局规则”，让用户放到 Codex、Cursor、Claude Code 或其他 agent 的全局规则里。

### 2. 项目级协议

真正详细的规则放在项目目录：

```text
.projectflow/agent-protocol.md
```

这个文件包含：

- 当前项目的 ProjectFlow 读取路径。
- agent 完工后必须写入的结果格式。
- 如果没有 TaskId 应该写到哪里。
- 哪些内容必须记录。
- 哪些内容不能直接改。
- ProjectFlow 如何识别 processed 状态。

这样既能支持用户直接在 agent 里发起需求，也不会破坏 ProjectFlow 作为项目真实状态层的方向。

## `agent-protocol.md` 建议内容

ProjectFlow 应该自动生成这个文件：

```markdown
# ProjectFlow Agent Protocol

## Before Work
- Read `.projectflow/context/project-profile.md` if it exists.
- Read `.projectflow/context/requirements.md` if it exists.
- Read `.projectflow/context/confirmed-decisions.md` if it exists.
- Read `.projectflow/context/known-risks.md` if it exists.
- If a task brief exists, follow `.projectflow/tasks/<task-id>/brief.md`.

## If User Starts Work Directly In Agent
- It is allowed to work without a ProjectFlow task brief.
- After finishing, create a result file under `.projectflow/inbox/`.
- Use filename format: `YYYYMMDD-HHMM-agent-result.md`.

## Result Rules
- Do not directly modify ProjectFlow task state files as completed.
- Do not invent product decisions as confirmed facts.
- Record changed files, summary, risks, decisions, and suggested task updates.
- ProjectFlow will import the result and ask the user to confirm updates.

## Required Result Format
Use the `ProjectFlow Agent Result` format.
```

这个协议文件是连接 ProjectFlow 和 agent 的关键，比“必须先生成任务书”更重要。

## Agent 工作说明应该长什么样

工作说明不是普通 prompt，而是项目内可追踪文档。

```markdown
# ProjectFlow Agent Brief

ProjectId: projectflow
TaskId: PF-001
Task: 打通 ProjectFlow 与 agent 的项目更新闭环

## User Intent
用户希望 ProjectFlow 作为真实项目管理软件，负责整理项目资料、生成任务建议、给 agent 交接上下文，并在 agent 完成后识别更新。

## Confirmed Requirements
- 项目档案并入项目管理页。
- 任务看板优先接收 AI 和 agent 工作结果，不以手动填写为主。
- UI 面向开发者，紧凑务实，避免宣传感和大卡片堆叠。
- 流程稳定前不做完整测试，只做必要检查。

## Non Goals
- 不做商城式模板中心。
- 不做酒店预订或图书馆管理系统式布局。
- 不做无确认自动修改任务状态。
- 不引入复杂插件系统。

## Project Context Files
- .projectflow/context/project-profile.md
- .projectflow/context/requirements.md
- .projectflow/context/confirmed-decisions.md
- .projectflow/context/known-risks.md

## Task Goal
实现 ProjectFlow 写入 agent 工作说明、agent 写回结果、ProjectFlow 识别并生成待确认更新的基础闭环。

## Acceptance Criteria
- ProjectFlow 能在项目目录写入 `.projectflow` 文件。
- agent 能读取 brief 并按要求写回 result。
- ProjectFlow 能识别 result 并生成待确认建议。
- 用户确认后才更新真实项目状态。

## Result File
完成后请写入：

`.projectflow/tasks/PF-001/result.md`

## Required Result Format
请按 ProjectFlow Agent Result 格式填写。
```

## Agent 结果应该长什么样

```markdown
# ProjectFlow Agent Result

ProjectId: projectflow
TaskId: PF-001
Status: ready_for_review

## Summary
完成了 `.projectflow` 文件桥接的基础设计或实现。

## Changed Files
- frontend/src/app/dashboard/page.tsx
- backend/src/main/java/com/projectflow/service/ProjectFlowBridgeService.java

## Task Updates
- PF-001: ready_for_review
- New: 补充 result 解析错误提示

## Decisions
- 先使用文件桥接，不引入 MCP。
- agent 结果只生成待确认建议，不直接改真实任务。

## Risks
- 需要处理用户没有项目写入权限的情况。

## Dev Log
本轮完成 ProjectFlow 与 agent 文件桥接基础闭环。
```

## ProjectFlow 如何识别更新

识别流程应该是：

1. 用户在 ProjectFlow 点击“扫描 Agent 更新”。
2. ProjectFlow 读取 `.projectflow/tasks/*/result.md`、`.projectflow/inbox/agent-result.md` 和 `.projectflow/inbox/*-agent-result.md`。
3. ProjectFlow 校验 `ProjectId` 和 `TaskId`。
4. 如果没有 `TaskId`，ProjectFlow 生成“未绑定任务”的待确认更新，并建议创建或关联任务。
5. ProjectFlow 把内容解析成待确认建议。
6. 用户在 ProjectFlow 中确认、修改或拒绝。
7. 确认后才写入任务、项目记忆、开发日志和项目档案。
8. 已处理的 result 标记为 processed，避免重复导入。

### 开发流程记录识别重点

ProjectFlow 扫描 agent 结果时，最重要的不是只看“任务完成了没有”，而是识别开发过程本身：

- 本轮用户想解决什么问题。
- agent 实际改了哪些文件。
- 为什么这样改。
- 有哪些新增决策。
- 有哪些风险或未完成项。
- 是否产生了新的任务。
- 是否改变了项目档案。
- 是否需要用户确认产品方向。

这些内容应该进入项目追踪，而不是被当作普通日志丢掉。

## UI 开工建议

### 项目管理页

不要用大卡片堆叠。建议改成开发工具式布局：

- 左侧：项目列表、导入项目、当前项目。
- 中间：当前项目工作区。
- 右侧：项目档案、待确认建议、风险。

顶部使用长条矩形输入框或操作条：

- 导入 ZIP / 选择文件夹。
- 输入本次需求。
- 生成项目分析。
- 写入 Agent 工作说明。
- 扫描 Agent 更新。

信息少的地方不要做大卡片，改成横向条目、表格行、紧凑面板。

### 任务看板

任务看板可以继续存在，但视觉上更像工作队列：

- 推荐任务。
- 已选任务。
- 正在交给 agent 的任务。
- 待确认更新。
- 已完成任务。

每行任务显示关键字段，不要用大面积卡片：

- 任务 ID。
- 标题。
- 状态。
- 来源。
- 风险。
- 操作按钮。

### 待确认建议

待确认建议应该像代码 review 一样：

- 建议内容。
- 来源文件。
- 关联任务。
- 影响范围。
- 接受。
- 修改后接受。
- 拒绝。

## 后端开工建议

优先复用现有结构，不新增复杂系统。

建议新增或扩展的能力：

- 项目写入路径校验。
- `.projectflow` 文件生成。
- Agent Result 读取。
- Markdown 结构解析。
- 待确认建议生成。
- Result 处理状态记录。

如果当前已有 `ProjectMaterial`、`AiSuggestion`、`ProjectMemory`、`ProjectEvolutionRecord`，优先复用它们。

短期不需要完整 agent session 表。只有在需要追踪多次 agent 会话、并发结果、失败重试时，再新增 `AgentWorkSession`。

## 前端开工建议

优先改“项目管理”页，而不是新建更多页面。

建议页面能力：

- 项目导入区放在最顶部。
- 本次需求输入框放在导入区旁边或下方。
- 项目档案放在当前项目页内。
- 任务候选和待确认建议放在中间主区域。
- 设置类内容继续放个人设置，不出现在主工作流里。
- 所有空状态用横向输入条或紧凑提示，不用大宣传卡片。

## 实施顺序

### 第一步：项目管理页信息架构

- 合并项目档案到项目管理页。
- 减少大卡片。
- 导入项目、本次需求、分析状态放到最前面。
- 任务候选和待确认建议成为主内容。

### 第二步：初始分析模型

- 定义项目档案字段。
- 定义需求字段。
- 定义信息缺口字段。
- 定义决策选项字段。
- 定义任务候选字段。
- 明确哪些字段是已确认，哪些字段是 AI 推断。

### 第三步：`.projectflow` 写入

- 生成 `agent-protocol.md`。
- 生成 context 文件。
- 生成 task brief。
- 生成空 result 文件。
- 生成 status.json。
- 前端提供“写入 Agent 工作说明”按钮。
- 前端提供“复制全局 agent 规则”按钮。

### 第四步：Agent Result 识别

- 读取 result 文件。
- 解析固定 Markdown。
- 校验项目和任务。
- 支持无 TaskId 的 inbox 结果。
- 转成待确认建议。
- 识别开发流程记录、风险、决策和新任务。
- 防止重复导入。

### 第五步：确认后更新项目状态

- 用户接受任务更新。
- 用户接受开发日志。
- 用户接受项目档案变化。
- 用户拒绝不准确建议。
- 所有确认动作写入项目演进记录。

### 第六步：最后再测试完整流程

- 流程没定型前，不做完整测试。
- 开发期间只做必要的编译、类型检查和局部验证。
- 等闭环完成后，再测试从导入项目到 agent 更新识别的完整流程。

## 验收标准

- 用户可以导入真实项目文件夹或 ZIP。
- 用户可以输入本次需求。
- ProjectFlow 可以生成项目档案、信息缺口、决策选项和任务候选。
- 用户确认任务后，ProjectFlow 可以写入 `.projectflow` 工作说明。
- 用户不通过 ProjectFlow 生成任务说明时，agent 也能通过 `agent-protocol.md` 知道完工后写回哪里。
- agent 可以读取工作说明或项目协议，并写回结果。
- ProjectFlow 可以扫描结果并生成待确认建议。
- ProjectFlow 可以识别开发流程记录，而不只是识别任务状态。
- 用户确认后，任务、项目档案、开发日志被更新。
- UI 看起来像开发工具，不像商城、酒店预订或图书馆管理系统。
- 页面减少大卡片堆叠，使用长条输入、表格行、紧凑面板和工具栏。

## 暂不做

- 不让 AI 直接替用户决定产品方向。
- 不让 agent 直接改 ProjectFlow 的真实任务状态。
- 不做复杂 MCP 接入。
- 不做浏览器扩展。
- 不做 GitHub App。
- 不做插件市场。
- 不做模板展示中心。
- 不做完整自动化测试流程，直到核心交互稳定。

## 最小开工范围

下一次开工只做这几件事：

1. 改项目管理页布局。
2. 定义初始分析和任务候选的数据结构。
3. 增加 `.projectflow/agent-protocol.md` 和 context 文件写入能力。
4. 增加 ProjectFlow 任务 brief 写入能力，但作为可选路径。
5. 增加 Agent Result 扫描和待确认建议生成。
6. 支持直接从 agent 发起需求后的 inbox 结果导入。
7. 保留用户确认机制。

这就是当前最适合 ProjectFlow 的主线。它成本低、真实、开发者导向，也能自然连接 ProjectFlow 和 agent 的工作方式。
