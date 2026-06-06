# ProjectFlow V2 Core Plan

## 1. V2 定位

V2 的核心不是继续增加普通管理页面，而是把 ProjectFlow 从“项目记录工具”推进为“AI 项目过程整理与智能管理台”。

ProjectFlow 不替代 Codex、Claude Code、Cursor 这类 agent。它负责补足这些 agent 不擅长的长期项目管理能力：

- 维护多个项目的长期身份档案。
- 把零散开发过程整理成任务、日志、决策、风险和成果。
- 基于上一轮项目状态识别本轮变化。
- 把项目过程转化成 README、周报、简历、复盘和未来规划。
- 让用户少填表，多导入已有材料，由 AI 生成待确认建议。

## 2. V2 Core 成功标准

V2 Core 完成后，用户应该能跑通这条主链路：

1. 用户配置 DeepSeek 或自定义 OpenAI-compatible 模型。
2. 用户进入某个项目。
3. 用户上传或粘贴项目材料，例如 agent 总结、commit log、txt、md、docx、项目 zip。
4. ProjectFlow 把材料转换成统一的 Project Material。
5. AI 基于 Project Memory 和上一次 Snapshot 分析本轮变化。
6. 系统生成待确认建议，包括更新项目档案、新增任务、记录日志、记录风险、记录技术决策、记录开发者收获。
7. 用户选择全部采纳、部分采纳、编辑后采纳或忽略。
8. 系统更新 Project Memory，并生成一条 Project Evolution Record。
9. Dashboard 展示当前阶段、本轮变化、下一步建议、风险和可生成成果。

## 3. 模型配置中心

### 3.1 目标

支持用户自带 API Key 和模型地址。ProjectFlow 不绑定单一模型，默认优先支持 DeepSeek，同时保留 OpenAI-compatible provider 扩展能力。

### 3.2 必需字段

- Provider 名称。
- API Base URL。
- API Key。
- Model Name。
- Provider 类型：DeepSeek、OpenAI-compatible、自定义。
- Temperature。
- Max Tokens。
- 是否默认启用。
- 用途标签：项目分析、材料解析、成果生成、代码结构分析。

### 3.3 安全规则

- API Key 不提交到 Git。
- `.env.example` 只提供变量名。
- 本地开发可使用后端环境变量。
- 未来部署版支持用户在设置页填写自己的 key。
- 返回给前端的 provider 信息必须隐藏 key。
- AI 调用失败时返回友好错误，不暴露完整请求头、key、内部堆栈。

### 3.4 第一阶段 provider

- `mock`：无 key 演示模式。
- `deepseek`：真实调用 DeepSeek API。
- `openai-compatible`：允许用户填写 base URL、key、model。

## 4. 项目材料 Project Material

### 4.1 统一入口

所有外部输入都先进入 Project Material，不直接写任务或日志。

支持来源：

- 自然语言记录。
- Agent 总结。
- Agent 原始对话。
- Codex 输出。
- Claude Code 输出。
- Cursor 输出。
- Commit log。
- README / Markdown。
- txt 文本。
- docx 文档。
- json / log 文本。
- 项目 zip。
- 未来扩展：GitHub 仓库、PDF、截图 OCR、本地目录监控。

### 4.2 推荐用户输入方式

最推荐的 agent 输入方式不是复制整段聊天，而是让 agent 先生成一段本轮总结：

```text
请总结本轮开发：完成了什么、改了哪些文件、遇到什么问题、验证了什么、下一步建议是什么。
```

用户可以把这段总结直接粘贴，或保存成 md、txt、docx 文件上传。

### 4.3 Commit log 输入方式

ProjectFlow 应允许用户直接粘贴命令输出：

```bash
git log --oneline --decorate -20
git log --since="7 days ago" --pretty=format:"%h | %ad | %s" --date=short
git log --stat --since="7 days ago"
```

系统负责把 commit log 转换成项目语言：

- 本轮完成了哪些能力。
- 哪些问题被修复。
- 哪些模块发生变化。
- 项目阶段是否推进。
- 哪些内容可以进入开发日志或成果摘要。

## 5. 轻量项目导入

### 5.1 当前阶段目标

V2 Core 先做轻量分析，不做全量代码理解。目标是验证“导入一个真实项目后，AI 能生成靠谱项目画像和下一步建议”。

### 5.2 支持方式

- 上传 zip。
- 上传 txt、md、docx。
- 粘贴项目说明或 agent 总结。

浏览器部署版不能直接读取用户电脑任意文件夹路径，必须由用户主动选择文件或上传 zip。

### 5.3 zip 分析范围

读取：

- 目录树。
- README。
- package.json。
- pom.xml。
- docker-compose.yml。
- tsconfig、next config、vite config。
- docs 目录。
- src 下入口文件。
- test 目录结构。
- 启动脚本。
- `.env.example`。

排除：

- `.git`。
- `node_modules`。
- `target`。
- `dist`。
- `.next`。
- `build`。
- `logs`。
- `.env`。
- 图片、视频、压缩包、二进制文件。
- 超大文件。

### 5.4 输出

轻量项目分析生成：

- 项目简介。
- 技术栈识别。
- 模块结构。
- 当前阶段。
- 是否有启动脚本。
- 是否有测试。
- 是否有 README。
- 是否有部署配置。
- 是否像空壳。
- 当前最该补的能力。

## 6. Project Memory

### 6.1 目标

每个项目都有一份长期身份档案，由用户输入和 AI 分析逐步维护。

### 6.2 内容结构

- 项目名称。
- 一句话定位。
- 项目初衷。
- 目标用户。
- 当前阶段。
- 技术栈。
- 核心模块。
- 已完成能力。
- 正在进行能力。
- 未完成能力。
- 当前风险。
- 技术决策。
- 重要问题。
- 开发者收获。
- 可展示成果。
- 未来发展方向。
- AI 对下一步的建议。

### 6.3 更新规则

Project Memory 不由 AI 直接静默覆盖。AI 只能生成更新建议，用户确认后写入。

## 7. Snapshot 与 Diff

### 7.1 Project Snapshot

每次确认 AI 建议后，保存一份项目状态快照。

Snapshot 包含：

- 当前阶段。
- 任务状态摘要。
- 技术栈摘要。
- 模块完成度。
- 风险摘要。
- 最近成果。
- 下一步建议。
- Project Memory 版本。

### 7.2 Diff 分析

AI 分析新材料时，必须参考上一份 Snapshot，判断本轮变化：

- 新增了什么。
- 修复了什么。
- 哪些任务推进了。
- 哪些风险出现或解除。
- 项目阶段是否变化。
- 哪些内容值得沉淀为成果。
- 哪些地方仍然像空壳。

### 7.3 Evolution Record

每次确认后生成项目演进记录：

- 输入来源。
- 本轮摘要。
- 与上一轮相比的变化。
- 关键成果。
- 关键问题。
- 技术决策。
- 开发者收获。
- 下一步建议。

## 8. AI 建议确认台

### 8.1 目标

减少用户机械记录，但避免 AI 自动乱改数据。

### 8.2 建议类型

- 更新项目身份描述。
- 新增任务。
- 更新任务状态。
- 新增开发日志。
- 记录技术决策。
- 记录风险。
- 记录开发者收获。
- 更新当前阶段。
- 生成成果摘要。

### 8.3 用户操作

- 全部采纳。
- 部分采纳。
- 编辑后采纳。
- 忽略。
- 重新生成。

## 9. Dashboard 交互风格

### 9.1 设计方向

登录页当前偏深蓝、专业、科技感。登录后的主页不应该完全割裂。V2 Dashboard 建议转为“浅色工作台 + 深蓝智能中枢”的混合风格：

- 主背景保持浅色，保证长期使用舒适。
- 顶部或主区域加入深蓝 AI 状态面板，呼应登录页。
- 重点不是统计卡片，而是“当前项目状态”和“下一步建议”。
- 页面应像项目驾驶舱，不像普通表单后台。

### 9.2 首页第一眼

用户进入后应该立刻看到：

- 当前最重要项目。
- AI 判断的当前阶段。
- 最近一次项目变化。
- 待确认 AI 建议。
- 下一步建议。
- 当前风险。
- 可生成成果。

### 9.3 主交互

首页应该有一个明显入口：

```text
把今天的开发记录、agent 总结、commit log 或项目文件丢进来，ProjectFlow 帮你整理。
```

交互不是聊天框，而是“项目材料收集箱”：

- 粘贴文本。
- 上传文件。
- 上传 zip。
- 选择来源类型。
- 开始 AI 解析。

## 10. V2 Core 数据模型扩展

建议新增：

- `ai_providers`
- `project_materials`
- `project_memories`
- `project_snapshots`
- `project_evolution_records`
- `ai_suggestions`
- `technical_decisions`
- `project_risks`
- `developer_learnings`

第一阶段可以先实现前 6 个，后 3 个可以作为独立实体或先存入 suggestion payload。

## 11. V2 Core API 草案

### AI Provider

- `GET /api/ai-providers`
- `POST /api/ai-providers`
- `PATCH /api/ai-providers/{id}`
- `DELETE /api/ai-providers/{id}`
- `POST /api/ai-providers/{id}/test`

### Project Materials

- `GET /api/projects/{projectId}/materials`
- `POST /api/projects/{projectId}/materials/text`
- `POST /api/projects/{projectId}/materials/file`
- `POST /api/projects/{projectId}/materials/zip`
- `GET /api/project-materials/{materialId}`

### AI Analysis

- `POST /api/project-materials/{materialId}/analyze`
- `GET /api/projects/{projectId}/suggestions`
- `PATCH /api/ai-suggestions/{suggestionId}`
- `POST /api/projects/{projectId}/suggestions/apply`

### Project Memory

- `GET /api/projects/{projectId}/memory`
- `GET /api/projects/{projectId}/snapshots`
- `GET /api/projects/{projectId}/evolution-records`

## 12. AI 输出结构要求

AI 对材料的解析必须返回结构化 JSON，不允许只返回自然语言。

核心结构：

```json
{
  "summary": "本轮材料摘要",
  "projectMemoryPatch": {},
  "detectedChanges": [],
  "suggestions": [
    {
      "type": "CREATE_TASK",
      "title": "任务标题",
      "reason": "为什么建议创建",
      "payload": {}
    }
  ],
  "risks": [],
  "developerLearnings": [],
  "nextSteps": []
}
```

后端必须校验 JSON schema，失败时提示用户重新生成或改用 mock 解析。

## 13. 开发轮次

### V2 Round 1: Provider 与 DeepSeek 接入

目标：

- 建立 AI provider 抽象。
- 支持 DeepSeek 和 OpenAI-compatible 配置。
- 支持测试连接。
- 保留 mock provider。

验收：

- 无 key 时 mock 可用。
- 有 DeepSeek key 时能完成一次真实请求。
- API key 不出现在前端响应和 Git 文件里。

### V2 Round 2: Project Material 输入箱

目标：

- 新增项目材料页或 Dashboard 收集箱。
- 支持粘贴文本。
- 支持 md、txt、docx 上传。
- 支持来源类型选择。

验收：

- 用户可以把 agent 总结或 commit log 保存为材料。
- 材料不会直接污染任务和日志。

### V2 Round 3: AI 解析与建议确认

目标：

- 对 Project Material 调用 AI。
- 生成结构化建议。
- 做建议确认台。

验收：

- 用户能从一段开发总结生成任务、日志、风险、下一步建议。
- 用户确认后才写入正式数据。

### V2 Round 4: Project Memory 与 Snapshot

目标：

- 建立 Project Memory。
- 每次确认后生成 Snapshot。
- 下一次分析时参考上一份 Snapshot。

验收：

- 系统能说清楚“本轮相比上轮变化了什么”。
- 项目详情页能看到长期项目档案。

### V2 Round 5: 轻量 zip 项目分析

目标：

- 上传项目 zip。
- 过滤无关文件。
- 提取目录树和关键配置。
- 生成项目画像。

验收：

- 对真实项目 zip 能识别技术栈、结构、阶段和缺口。

### V2 Round 6: Dashboard 重构

目标：

- 首页变成 AI 项目驾驶舱。
- 展示 Project Memory、最近演进、待确认建议、下一步建议。

验收：

- 用户登录后一眼知道当前项目状态和下一步能做什么。

## 14. 暂不进入 V2 Core 的能力

- 多人协作。
- GitHub OAuth。
- GitHub App 自动同步。
- 常驻本地目录监控。
- 向量数据库。
- 全量代码语义索引。
- PDF/OCR。
- 精确 token 成本统计。
- Agent 插件生态。

这些能力保留在 V3 或 V2 后续增强中。

## 15. 近期执行原则

- 先把“材料输入 -> AI 解析 -> 用户确认 -> Project Memory 更新”闭环做扎实。
- 每个 AI 写入动作都必须可追溯、可撤销或至少可人工确认。
- 真实模型接入优先，但 mock 模式必须保留，保证项目能稳定演示。
- 页面风格要从普通后台升级为专业 AI 工作台，但不要牺牲信息清晰度。
- 每轮开发结束必须能构建、能启动、能解释新增能力。
