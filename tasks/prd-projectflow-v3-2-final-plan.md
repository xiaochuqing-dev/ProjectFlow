# PRD: ProjectFlow V3.2 跨 Agent 证据账本与成果沉淀层

## Document Status

- Status: Final Draft (Revised)
- File Mode: Single
- Current Phase: Not Started
- Last Updated: 2026-06-19 (revised same day)
- PRD File: `tasks/prd-projectflow-v3-2-final-plan.md`
- Purpose: 重新定位 V3.2。ProjectFlow 不再把”项目监听”和”让 Agent 写总结”当作核心壁垒，而是成为跨 Agent、跨项目、跨时间的证据账本、确认层和成果沉淀层。核心创新：Agent 自动识别引擎 + 证据分层归因 + 用户零输入即可看到今日变化全貌。

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
- Agent 身份应由 ProjectFlow 自动识别，不应要求用户手动标记每次会话的 Agent 类型。
- 用户什么都不写，ProjectFlow 也能给出八九不离十的归因判断；手动修正只是锦上添花。

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

创建方式（按优先级排列）：

- **自动推断（主路径）**：ProjectFlow 通过 git 时间窗口、commit 风格、Agent 本地日志自动切分并归因 Work Session。用户不需要手动创建。
- **事后补充（辅助路径）**：用户可以事后调整自动推断结果：修正 Agent 类型、合并或拆分 session、补充任务意图。
- **手动创建（兜底路径）**：极少数情况下（如 Agent 完全没有本地痕迹），用户可手动创建一个 Work Session。

字段建议：

- sessionId。
- projectId。
- agentType：Codex、Claude Code、Cursor、DeepSeek、Other、Unknown。
- agentName。
- taskIntent（可选，可事后由模型根据改动反推）。
- branchName。
- baseCommit。
- startTime。
- endTime。
- evidenceBundleId。
- attributionConfidence：high、medium、low、unknown。
- detectionMethod：git_commit_style | agent_log | agent_result_file | time_window_clustering | user_manual。（新增：记录归因方式）

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

- ProjectFlow 自动推断的 Work Session（或用户手动修正后的）。
- 自动识别的 Agent 类型（或用户修正后的）。
- Session 开始和结束时间。
- 该时间窗口内发生的文件变化。
- 从 Agent 本地日志中提取的会话摘要。

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

### 6.5 Agent 自动识别引擎

ProjectFlow 不应要求用户手动标记"这次是 Codex 做的，那次是 Claude Code 做的"。它必须能自动识别。

实现方式：扫描项目本地环境中的 Agent 痕迹，与 git 证据交叉验证。

#### 6.5.1 各 Agent 的本地痕迹

Claude Code：

- `~/.claude/projects/<hash>/` 下的会话 transcript（JSONL 格式）。
- 项目中的 `.claude/settings.json`、`.claude/hooks/`。
- git commit message 风格特征：英文为主、结构化列表、标题用现在时动词开头。
- 典型 commit 示例：`fix: stabilize local startup health checks`、`feat: add import and ai reflection workflows`。

Codex（OpenAI）：

- 本地 Codex 会话日志路径（视版本而定，通常与 OpenAI CLI 或 VSCode 扩展相关）。
- git commit message 风格：英文为主、偏简洁、直接描述动作。
- 项目中的 `.codex/` 或类似工作目录。

Cursor：

- 项目中的 `.cursor/` 目录，含会话和规则。
- commit message 风格介于 Claude Code 和 Codex 之间。

DeepSeek 驱动的 Agent（如 Trae）：

- `~/.traecli/`、`~/.trae-cn/` 下的会话和记忆文件。
- commit message 可能中英混合。

#### 6.5.2 自动归因流程

用户打开 ProjectFlow 时，系统在后台执行以下步骤：

1. 读取自上次扫描以来的 `git log`。
2. 对每条 commit 分析：
   - commit author/email 模式匹配已知 Agent 的默认配置。
   - commit message 语言和风格分类。
   - 时间戳聚类：同一时间段内的 commits 大概率属同一会话。
3. 扫描本地 Agent 日志目录：
   - 查找时间窗口匹配的会话记录。
   - 如果 Agent 日志中有文件列表，与 commit 中的文件交叉比对。
4. 按时间窗口 + 文件聚集度自动切分为 Work Session。
5. 为每个 Work Session 标注：
   - 推测的 Agent 类型。
   - 归因置信度。
   - 检测方式（commit_style | agent_log | time_clustering 等）。

#### 6.5.3 归因结果展示

```
今天检测到 2 次工作会话：

Session A | 09:00 - 11:30 | 推测 Claude Code | 置信度 high
  改动：12 个文件，+340 / -120 行
  证据：commit style 匹配 + .claude/ 会话记录吻合
  涉及模块：backend/auth, frontend/login

Session B | 14:00 - 15:20 | 推测 Cursor | 置信度 medium
  改动：8 个文件，+200 / -50 行
  证据：commit style 匹配，未找到 Agent 日志
  涉及模块：frontend/dashboard, frontend/settings
```

#### 6.5.4 用户交互

- 用户直接看到自动归因结果，**不需要先手动创建任何东西**。
- 如果自动归因正确，用户无需操作，直接进入审查。
- 如果不对，用户可以：修正 Agent 类型、合并两个 session、拆分为更多 session、补充任务意图。
- 极少数完全无法自动归因的情况（纯手动 git commit、未知 Agent），归因显示为 unknown，用户可手动指定。

#### 6.5.5 这个能力本身就是壁垒

没有任何单个 Agent 能回答"这个项目今天被哪些 AI 改了什么"——Claude Code 不知道 Codex 做了什么，Codex 不知道 Claude Code 做了什么。只有 ProjectFlow 站在项目层面，读取 git + 各 Agent 的本地日志，能拼出完整画面。

Agent 自动识别引擎是 ProjectFlow 的不可替代性核心实现，不是辅助功能。

## 7. Functional Requirements

- FR-1: 系统必须支持跨 Agent 的项目证据账本，而不是只支持单个 Agent Result。
- FR-2: 系统必须能自动扫描 git 记录和 Agent 本地日志，推断 Work Session 边界和 Agent 类型，不要求用户手动创建。
- FR-2a: 用户可事后修正自动归因结果（修正 Agent 类型、合并/拆分 session、补充任务意图），手动创建仅为兜底。
- FR-3: 系统必须能从 git diff、文件快照、Agent Result、Agent 本地日志、测试结果中生成 Evidence Bundle。
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

## 8.5. 部署简化：消除启动摩擦

当前的 Docker + PostgreSQL + Redis + Spring Boot + Next.js 启动链，对个人开发者"下班看一眼今天改了什么"的场景太重了。必须在 V3.2 实施初期提供轻量化方案。

### 方案：嵌入式数据库模式

- 引入 H2 或 SQLite 作为嵌入式数据库选项，替代 PostgreSQL。
- Redis 用内存缓存替代（Caffeine 或 Spring Cache 内置实现）。
- 最终目标：单条命令启动整个 ProjectFlow。

```bash
# 目标体验
java -jar projectflow.jar
# 浏览器打开 http://localhost:8080 即可使用
```

### 实施策略

- 保留 Docker Compose + PostgreSQL + Redis 作为生产部署方案。
- 新增 `application-embedded.yml` profile，启用 H2 文件数据库 + Caffeine 缓存。
- 默认开发/个人使用场景使用嵌入式模式。
- 团队或长期使用可切换到 PostgreSQL 模式。

### 这是一个创新相关决策

轻量化不是运维优化，是产品可用性的前提。如果用户打开 ProjectFlow 需要先等 Docker 启动再等 Spring Boot 启动再等 Next.js 编译，使用频率会趋近于零。

启动时间目标：
- 嵌入式模式冷启动 < 5 秒。
- 浏览器打开即用，不需要额外步骤。

## 9. V3.2 实施阶段

### Phase 1: Agent 自动归因引擎 + 工作台重新定位

Objective: 让 ProjectFlow 自动知道”今天有哪些 Agent 改了项目”。用户打开即看到画面，不需要先手动创建任何东西。

核心理念：用户什么都不用写，ProjectFlow 自己扫描 git 和各 Agent 的本地日志，给出八九不离十的归因。

Implementation checklist:

- [ ] 实现 git 日志增量读取：记录上次扫描的 commit，只分析新增 commit。
- [ ] 实现 commit 风格分类器：
  - 基于 commit message 的语言（中文/英文）、结构（前缀如 feat/fix/chore）、语气特征。
  - 映射到已知 Agent 类型的指纹库（Claude Code、Codex、Cursor、DeepSeek 等）。
- [ ] 实现 Agent 本地日志扫描器：
  - Claude Code：扫描 `~/.claude/projects/` 下的 JSONL transcript，按时间窗口匹配。
  - Codex：扫描 Codex 本地会话目录，按时间窗口匹配。
  - Cursor：扫描项目 `.cursor/` 目录。
  - DeepSeek/Trae：扫描 `~/.traecli/` 和 `~/.trae-cn/`。
- [ ] 实现时间窗口聚类：按 commit 时间间隔和文件关联度自动切分 Work Session。
- [ ] 实现归因置信度计算：综合 commit style 匹配度、Agent 日志匹配度、文件聚集度。
- [ ] 首页主入口改为”今日变化概览”，自动展示检测到的 Work Session 列表。
- [ ] 每个自动检测的 Session 显示：时间范围、推测 Agent 类型、置信度、改动文件数、增删行数。
- [ ] 用户可对自动归因结果做轻量修正：
  - 修正 Agent 类型（下拉选择）。
  - 合并两个 Session 为一个。
  - 拆分一个 Session 为多个。
  - 补充任务意图（可选）。
- [ ] 极少数无法自动归因的情况（纯手动 git、未知 Agent），显示 unknown，用户可手动创建 Session 兜底。
- [ ] 保留 Agent 规则写入功能，但降级为辅助：仅用于提示下一轮 Agent 读取确认上下文。
- [ ] 删除当前”开始一次工作记录”的手动入口，改为”刷新变化”按钮（触发重新扫描）。

Validation:

- [ ] 不创建任何手动 Work Session，ProjectFlow 也能自动列出今日的 Agent 活动。
- [ ] 用 Claude Code 做几个 commit 后，ProjectFlow 能识别为 Claude Code（置信度 high）。
- [ ] 非 git 项目仍能通过文件哈希对比检测变化（降级到 medium/low）。
- [ ] 用户修正归因后，修正结果被保存，后续同一 Agent 的识别准确率提升。
- [ ] 无法识别 Agent 时显示 unknown，不伪造。

### Phase 2: Evidence Bundle 生成（含 Agent 痕迹采集）

Objective: 让 ProjectFlow 从客观证据中知道”改了多少、改了哪里、影响什么”，同时采集各 Agent 的本地痕迹作为归因支撑。

Implementation checklist:

- [ ] 对 Git 项目读取 `git status --porcelain`、`git diff --name-status`、`git diff --numstat`。
- [ ] 记录 changedFiles、addedLines、deletedLines、changeType、moduleName。
- [ ] 对非 Git 项目使用 baseline 文件哈希和最新哈希对比。
- [ ] 扫描并解析 Claude Code transcript（`~/.claude/projects/` 下的 JSONL），提取会话时间、文件列表、任务意图。
- [ ] 扫描并解析 Codex 本地会话日志（如可访问）。
- [ ] 扫描并解析 Cursor 会话（`.cursor/` 目录）。
- [ ] 读取 `.projectflow/inbox/` 下的 Agent Result 文件（如有），解析为 Agent Claim。
- [ ] Agent 本地日志中采集的信息作为归因证据，不替代 git diff 作为主证据。
- [ ] 收集可选测试、构建、Lint 输出。
- [ ] 生成 Evidence Bundle，并保存完整的来源清单（含 Agent 痕迹路径）。
- [ ] Evidence Bundle 必须排除敏感路径和大文件。
- [ ] Agent 本地日志中的敏感信息（如 API 调用细节）不采集。

Validation:

- [ ] 修改多个文件后能生成文件数和增删行统计。
- [ ] Claude Code 的 JSONL transcript 能被成功解析并关联到对应时间窗口。
- [ ] 没有 Agent Result 时仍能生成候选变更。
- [ ] 没有 Agent 本地日志时仍能生成候选变更（归因降级）。
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
3. ProjectFlow 建立 Project Ledger。
4. 可选：写入 Agent 规则（仅用于提示 Agent 读取确认上下文，不强制）。

### 白天多 Agent 开发

1. 用户正常使用 Codex、Claude Code、Cursor 等任意 Agent 工作。
2. ProjectFlow **完全不参与**。不要求用户打开 ProjectFlow，不要求用户点击任何按钮，不要求 Agent 遵守任何协议。
3. Agent 正常工作，正常 commit，正常产生本地日志。

### 晚上打开 ProjectFlow 审查

1. 用户打开 ProjectFlow 工作台。
2. **首页自动显示今日变化概览**：
   - “检测到 2 次工作会话”
   - Session A：09:00-11:30，推测 Claude Code，12 文件，+340/-120 行，置信度 high
   - Session B：14:00-15:20，推测 Cursor，8 文件，+200/-50 行，置信度 medium
3. 用户确认归因正确（大多数情况），或微调：修正 Agent 类型、合并/拆分 session。
4. 点击”生成审查”，ProjectFlow 合并 git 证据 + Agent 声明 + 模型分析，生成结构化候选变更。
5. 系统展示每条候选的证据来源、影响文件、归因置信度、风险提示。
6. 用户审查、编辑、采纳或忽略。
7. 采纳的内容进入 Project Ledger，同步更新档案、任务、日志、快照、演进记录。
8. 用户可生成每日回顾、周报、README 或复盘。
9. ProjectFlow 自动同步确认上下文到 `.projectflow/context`（如有绑定本地路径）。

### 关键变化

- **白天不需要 ProjectFlow**。它不抢 Agent 的活，也不要求用户改变开发习惯。
- **晚上打开即看到画面**。不需要手动创建 session，不需要手动指定 Agent。
- **Agent 不配合也能用**。没有 Agent Result 文件，没有协议写入，ProjectFlow 依然能从 git 和 Agent 本地日志还原今天的变化。

## 11. 数据和 API 建议

建议新增或等效扩展：

- `WorkSession`（含 detectionMethod 字段）
- `AgentSignature`（Agent 类型指纹：commit style 模式、本地日志路径、默认 author/email）
- `AgentLogSource`（扫描到的 Agent 本地日志记录）
- `EvidenceBundle`
- `EvidenceSource`（含 agentLogSource 引用）
- `AgentClaim`
- `AttributionResult`
- `ChangeConflict`
- `CandidateChange`
- `ConfirmedChange`
- `ProjectLedgerEntry`
- `OutputDraftVersion`
- `ModelUsageRecord`

建议 API：

- `POST /api/projects/{projectId}/scan`（触发自动扫描 git + Agent 日志，返回检测到的 Work Session 列表）
- `GET /api/projects/{projectId}/work-sessions`（查看已检测的 Work Session 列表）
- `PATCH /api/work-sessions/{sessionId}`（用户修正归因：改 Agent 类型、合并/拆分、补充意图）
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
- SC-2: 多个 Agent 同一项目工作时，ProjectFlow 能自动识别 Agent 身份并显示来源、证据和归因置信度。
- SC-3: 无法确定归因时，系统明确显示 low 或 unknown，而不是猜测。
- SC-4: 用户不需要在开发前打开 ProjectFlow 或创建 Work Session，即可在事后看到自动归因结果。
- SC-5: 用户能从一份 Evidence Bundle 审查并确认多个候选变更。
- SC-6: 确认后的项目事实能进入 Project Ledger，并同步给下一轮 Agent。
- SC-7: 每日回顾和成果输出来自确认事实和证据，不是固定模板。
- SC-8: ProjectFlow 的价值不依赖某一个 Agent 是否支持监听、规则或导出日志。
- SC-9: ProjectFlow 能以嵌入式模式单 JAR 启动，冷启动 < 5 秒，不依赖 Docker。

## 13. Non-Goals

- NG-1: 不做通用 Agent 编排器。
- NG-2: 不替代 Codex/Claude 的项目监听和自动化执行。
- NG-3: 不自动判定哪个 Agent 正确。
- NG-4: 不把模型生成内容直接写成确认事实。
- NG-5: 不要求用户为每个 Agent 手动复制粘贴总结。
- NG-6: 不要求用户在开发前打开 ProjectFlow 或手动创建 Work Session。
- NG-7: 不侵入 Agent 的私有数据之外的系统区域。

## 14. 最终执行优先级

0. **先做轻量化部署**（嵌入式 H2/SQLite + Caffeine 替代 PostgreSQL + Redis，单 JAR 启动）。这是所有后续工作的前提——如果用户打不开 ProjectFlow，后面所有功能都白做了。
1. **Agent 自动归因引擎 + 工作台重新定位**（Phase 1）。用户打开就能看到"今天哪些 Agent 改了什么"。
2. **Evidence Bundle + Agent 痕迹采集**（Phase 2）。从 git + Agent 本地日志生成完整证据包。
3. **归因置信度和冲突识别**（Phase 3）。多 Agent 协作时的归因和冲突检测。
4. **候选变更审查和确认事务**（Phase 4）。证据→候选→审查→采纳→闭环。
5. **Project Ledger 和 context 同步**（Phase 5）。确认事实持久化并回写给下一轮 Agent。
6. **每日回顾、成果输出**（Phase 6）。基于确认事实生成不同受众的输出。
7. **Token 和质量统计**（Phase 7）。模型调用成本和输出质量可观测。

前两步（0+1）完成后，ProjectFlow 就已经有了不可替代的使用价值：它是唯一一个能自动告诉你"今天你的项目被哪些 AI 改了什么"的工具。

V3.2 完成后，ProjectFlow 的核心卖点应当变成：

> 你白天用任意 Agent 正常开发，晚上打开 ProjectFlow，它自动告诉你：今天到底改了什么、谁改的、证据在哪、风险在哪、哪些已确认、能沉淀成什么成果。你不需要在开发前打开 ProjectFlow，不需要让 Agent 遵守任何协议，不需要手动写一行总结。

## 15. Change Log

- 2026-06-19: 创建最终 V3.2 计划书。
- 2026-06-19: 重新定位为跨 Agent 证据账本与成果沉淀层，删除把 ProjectFlow 当作重复监听器或依赖 Agent 稳定输出 result 的设计。
- 2026-06-19 (revised): 三项重大补充——
  1. 新增 Agent 自动识别引擎（6.5 节）：基于 git commit style + Agent 本地日志 + 时间窗口聚类，自动识别哪个 Agent 做了什么，用户不需要手动标记。
  2. Work Session 改为自动检测优先：Phase 1 重写为 Agent 自动归因引擎，用户打开即看到今日变化概览。手动创建降级为兜底。
  3. 新增部署简化方案（8.5 节）：嵌入式 H2/SQLite + Caffeine 缓存替代 Docker + PostgreSQL + Redis，单 JAR 冷启动 < 5 秒。
  4. 用户流程重写（10 节）：白天不需要 ProjectFlow，晚上打开自动看到画面。
  5. 执行优先级调整（14 节）：轻量化部署提到第 0 步，自动归因提到第 1 步。
