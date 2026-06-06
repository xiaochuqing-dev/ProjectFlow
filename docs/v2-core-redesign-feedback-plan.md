# ProjectFlow V2 Core Redesign Feedback Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 ProjectFlow V2 从“普通后台总览 + 分散功能入口”改成“先导入完整项目材料，再由 AI 生成项目画像、建议和长期档案”的真实用户主流程。

**Architecture:** 首页不再以统计总览为中心，而是作为项目管理入口和项目导入工作台。模型配置从项目管理页移到个人设置页；项目名由导入材料自动推断，用户可以后续编辑。项目分析以 zip 为第一阶段首选输入，零散文本只作为后续增量材料。

**Tech Stack:** Next.js + TypeScript + Tailwind CSS frontend, Spring Boot + JPA backend, existing JWT auth, existing V2 Project Material / Suggestion / Memory / Snapshot entities.

---

## 1. Feedback Understanding

本轮反馈确认以下问题需要修正：

- 当前首页仍像 V1 普通后台，入口顺序不对：创建项目、任务看板、开发日志、导入/AI 复盘被并列展示，没突出 V2 的“智能驾驶舱”。
- 真正的第一步应该是导入完整项目资料，尤其是项目文件夹或 zip，而不是先要求用户定义项目名。
- 项目名不是核心输入。若用户没有手动设置，应从上传文件夹/zip 根目录、README、package.json、pom.xml 等材料自动推断。
- API key / provider 配置不应该挤在智能驾驶舱卡片里，应移到个人设置页；分析需要模型时再提示用户去设置页配置。
- “总览”这种通用后台概念不适合当前产品。登录后应直接进入智能项目驾驶舱，先展示最关键操作和项目状态。
- 当前“分析片面文件”的价值不足。ProjectFlow 要做项目管理，初次分析应优先要求完整项目资料；零散文本适合作为后续增量更新。
- 当前未完整实现的能力要明确列入下一步计划，不要继续把半成品散落在页面里。
- 第一阶段选择 zip 上传，不做浏览器文件夹选择。zip 成本更低、浏览器支持更稳定、后端已有可复用解析基础。
- 不把 mock 当成面向用户的核心功能展示。没有真实模型时，页面应提示去个人设置配置 provider；本地规则分析可以作为项目画像的基础能力，但不要包装成假 AI 演示。
- UI 风格要参考 AdsPower 这类务实工具：左侧导航、顶部页签/状态条、中心表单/表格、右侧摘要栏，避免商城、酒店预订、图书馆管理系统式的大卡片首页和宣传感布局。

## 2. Target User Flow

V2 调整后的正常用户路径：

1. 用户登录后进入 `/dashboard`，页面标题和内容就是“项目管理”，不是普通总览。
2. 页面第一屏提供主要入口：“导入完整项目 zip”。
3. 用户上传项目 zip。
4. 系统提取项目根目录名、目录树、README、关键配置、docs、src/test 结构、启动脚本、`.env.example`。
5. 如果项目还不存在，系统自动创建一个 draft project：
   - 优先使用 zip 根目录名或文件夹名作为项目名。
   - 其次使用 `package.json.name`、`pom.xml.artifactId`、README 一级标题。
   - 都没有时使用 `Imported Project YYYY-MM-DD`。
6. 系统生成 Project Material，并触发“项目画像分析”。
7. 如果没有真实 provider：
   - 本地规则分析仍可生成基础项目画像。
   - 需要模型增强建议时，页面提示“真实模型分析需要在个人设置中配置 API key”。
   - 提供跳转 `/settings` 的明确按钮。
8. AI 输出项目画像和待确认建议：
   - 项目简介。
   - 技术栈识别。
   - 模块结构。
   - 当前阶段。
   - 是否有 README / 测试 / 启动脚本 / 部署配置。
   - 是否像空壳。
   - 当前最该补的能力。
   - 建议写入 Project Memory、任务、日志、风险、技术决策。
9. 用户确认后写入 Project Memory、Snapshot、Evolution Record，并生成下一步任务。
10. 后续用户再导入 agent 总结、commit log、md/docx/txt，作为同一项目的增量材料。

## 3. Information Architecture Changes

### 3.1 Sidebar

调整导航为更贴近 V2 主流程：

- `项目管理` -> `/dashboard`
- `项目档案` -> `/projects`
- `材料库` -> `/materials` 或保留在驾驶舱内的材料页签
- `建议确认` -> 可作为驾驶舱内区域，不必独立导航
- `任务` -> `/tasks`
- `开发日志` -> `/dev-logs`
- `成果输出` -> `/ai-review`
- `个人设置` -> `/settings`

不再使用“总览”作为用户看到的主导航名称。

### 3.2 Dashboard First Screen

第一屏必须突出：

- 主标题：`导入项目`
- 主按钮：`上传项目 zip`
- 辅助入口：`粘贴 agent 总结 / commit log`
- 当前最重要项目的 AI 判断阶段。
- 待确认建议数量。
- 最近一次项目演进。
- 当前风险。
- 下一步建议。

不再把“创建项目名、任务看板、开发日志、AI 复盘”做成同级四卡片。首页避免宣传式 hero 和大卡片网格，采用工具型布局。

### 3.3 Settings Page

新增 `/settings`：

- 账号信息区域。
- AI Provider 区域：
  - Provider name。
  - Provider type: `mock`, `deepseek`, `openai-compatible`, `custom`。
  - API Base URL。
  - API Key password input。
  - Model name。
  - Temperature。
  - Max tokens。
  - Purpose tags。
  - Test connection。
- 安全提示：
  - API key 只保存到后端。
  - 前端响应只显示 `apiKeyConfigured`。
  - 测试失败不显示完整请求头、key、内部堆栈。

项目管理页只显示 provider 状态摘要和去设置页的按钮，不再承载完整配置表单。

## 4. Backend Work Plan

### Task 1: Add Full Project Import Contract

**Files:**
- Modify: `backend/src/main/java/com/projectflow/dto/V2ProjectDtos.java`
- Modify: `backend/src/main/java/com/projectflow/service/ProjectIntelligenceService.java`
- Modify: `backend/src/main/java/com/projectflow/controller/ProjectIntelligenceController.java`
- Test: `backend/src/test/java/com/projectflow/V2CoreControllerTest.java`

- [ ] Add response DTO `ProjectImportAnalyzeResponse` with:
  - `project`
  - `material`
  - `projectProfile`
  - `suggestions`
  - `modelEnhancementAvailable`
  - `providerConfigured`
- [ ] Add profile DTO fields:
  - `inferredProjectName`
  - `summary`
  - `techStack`
  - `moduleStructure`
  - `currentStage`
  - `hasReadme`
  - `hasTests`
  - `hasStartScript`
  - `hasDeployConfig`
  - `looksEmptyShell`
  - `mostImportantGap`
- [ ] Add endpoint:
  - `POST /api/project-imports/zip`
- [ ] Endpoint behavior:
  - Accept zip file.
  - Extract root folder name and key files.
  - If request has no `projectId`, create draft project automatically.
  - Save extracted material as `PROJECT_ZIP`.
  - Generate local project profile and pending suggestions.
  - Do not write formal task/log/memory until user confirms suggestions.
- [ ] Add tests:
  - Upload zip without project id creates project with inferred name.
  - Zip analysis does not leak `.env`.
  - Suggestions remain pending until apply.

### Task 2: Improve Project Name Inference

**Files:**
- Modify: `backend/src/main/java/com/projectflow/service/ProjectIntelligenceService.java`
- Test: `backend/src/test/java/com/projectflow/V2CoreControllerTest.java`

- [ ] Implement inference priority:
  1. zip root folder name.
  2. `package.json.name`.
  3. `pom.xml.artifactId`.
  4. README first H1.
  5. `Imported Project YYYY-MM-DD`.
- [ ] Add tests for each source.
- [ ] Keep project name editable later in existing project page.

### Task 3: Move Provider Configuration Out Of Cockpit

**Files:**
- Backend unchanged unless tests reveal gaps.
- Modify: `frontend/src/app/project-intelligence/page.tsx`
- Create: `frontend/src/app/settings/page.tsx`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/lib/api.ts` if provider update/delete helpers are missing.

- [ ] Remove full provider form from `project-intelligence`.
- [ ] Add provider status strip:
  - `本地规则分析可用`
  - `真实模型未配置`
  - `已配置 DeepSeek / OpenAI-compatible`
- [ ] Add `/settings` provider form using existing provider APIs.
- [ ] Add test connection button on settings page.
- [ ] If user starts model-enhanced analysis without provider, show settings callout. Do not show mock as a fake production capability.

### Task 4: Redesign Dashboard As Cockpit

**Files:**
- Modify: `frontend/src/app/dashboard/page.tsx`
- Modify: `frontend/src/components/AppShell.tsx`
- Possibly split: `frontend/src/components/ProjectImportPanel.tsx`
- Possibly split: `frontend/src/components/ProjectCockpitSummary.tsx`

- [ ] Rename sidebar label from `总览` to `项目管理`.
- [ ] Make `/dashboard` first screen the project import workspace.
- [ ] Remove four equal action cards from the top priority area.
- [ ] Put complete project import panel first.
- [ ] Put incremental material paste/upload below complete project import.
- [ ] Put pending suggestions, current risk, next step, recent evolution beside or below the import panel.
- [ ] Keep tasks/dev logs as downstream navigation, not first-step cards.

### Task 5: Separate Initial Import From Incremental Materials

**Files:**
- Modify: `frontend/src/app/project-intelligence/page.tsx`
- Modify: `backend/src/main/java/com/projectflow/service/ProjectIntelligenceService.java`
- Test: `backend/src/test/java/com/projectflow/V2CoreControllerTest.java`

- [ ] Initial project import:
  - zip is primary.
  - creates or selects project.
  - generates project profile.
- [ ] Incremental material import:
  - agent summary, commit log, md/txt/docx.
  - requires selected project.
  - updates existing project through suggestions.
- [ ] UI copy must clearly distinguish:
  - `首次导入完整项目`
  - `更新本轮开发材料`

### Task 6: Define Incomplete Features And Next Starts

**Files:**
- Create or modify: `docs/v2-core-plan.md` or a new `docs/v2-next-work-items.md`
- Modify UI only if needed for labels.

- [ ] Document incomplete but planned capabilities:
  - Browser folder upload support only if later proves cheaper and stable enough.
  - Real model analysis call replacing mock analyzer.
  - JSON schema validation for model output.
  - Better zip project profile extraction.
  - Suggestion edit UI before apply.
  - Undo or audit trail for applied suggestions.
  - Project detail page showing full Project Memory.
- [ ] Mark these as planned work, not completed features.
- [ ] Remove or hide UI controls that imply completed functionality when backend behavior is still mock or partial.

## 5. Frontend Layout Rules For This Redesign

- Do not use a generic statistics dashboard as the first screen.
- Do not make model configuration compete visually with project import.
- Do not require a manual project name before import.
- Do not present tasks/dev logs as the first user journey.
- Do put full project import as the primary first action.
- Do keep settings, project analysis, suggestion confirmation, and downstream project management as separate mental steps.
- Use a restrained developer-tool layout similar to the supplied AdsPower screenshot: compact left sidebar, tab-like sections, dense form rows, tables/lists, and a right-side summary panel.
- Keep visual polish in spacing, alignment, state badges, and readable controls, not in promotional hero sections or large decorative cards.
- Keep cards purposeful and compact: import panel, suggestion item, memory summary, risk item. Avoid decorative card grids and consumer-app templates.

## 6. Acceptance Checklist

The redesign is acceptable when:

- [ ] A new user logs in and immediately sees complete project import as the main action.
- [ ] User can upload a project zip without first creating a project manually.
- [ ] System infers a reasonable project name from the uploaded project.
- [ ] API key configuration lives in `/settings`, not inside the cockpit.
- [ ] If no provider is configured, project import still gives local project profile, and model-enhanced analysis clearly points to `/settings`.
- [ ] `总览` is no longer the primary mental model.
- [ ] Primary nav label is `项目管理` or another direct utility label, not promotional wording.
- [ ] First screen uses a practical tool layout, not a big-card marketing/dashboard layout.
- [ ] Task board and dev logs are downstream results, not the first required setup path.
- [ ] Partial text/material analysis is positioned as incremental update, not the best initial project-management path.
- [ ] The UI does not imply unfinished capabilities are complete.
- [ ] Backend tests cover automatic project creation from import and no direct task/log mutation before confirmation.
- [ ] `npm.cmd run build` passes.
- [ ] `mvn.cmd -q test` passes.

## 7. Execution Order

1. Backend full project import contract and tests.
2. Project name inference tests and implementation.
3. Settings page and provider relocation.
4. Dashboard/cockpit layout rewrite.
5. Split initial import vs incremental material UI.
6. Documentation of incomplete features and next work.
7. Full verification.

## 8. Confirmed Decisions

- ZIP upload is the first implementation. Browser folder selection is deferred.
- Mock is not a user-facing product mode. Local deterministic project profiling may exist, but it must not be presented as fake AI.
- Keep `/dashboard` route for compatibility, but the visible nav/page name should be `项目管理`.
- UI must be practical developer software, closer to AdsPower-style dense configuration/workspace screens than consumer card dashboards.
