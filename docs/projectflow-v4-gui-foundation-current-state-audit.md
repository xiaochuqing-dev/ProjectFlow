# ProjectFlow V4.0-A GUI Foundation 现状审计

## 范围与基线

本报告对现有前端路由、Shell、页面、共享组件和样式层进行只读审计，记录 V4.0-A 实施前的产品边界；不修改路由、API、数据或导航。

代码库已具备可复用的 ProjectFact、历史、证据、项目选择和 Provider 管理展示路径。但当前信息架构仍是 V3 的模块式工作台：全局 Dashboard、项目记录、项目记忆、Timeline、成果输出和设置。V4 应以项目工作区为主，并保留全局项目库与设置，而不是新增一个一级模块。

下列分类描述未来的展示角色，不构成删除路由的授权。迁移期间，所有现有深链和旧数据视图均应保持可用。

## 路由清单与迁移分类

| 路由或界面 | 当前实际功能 | V4.0-A 分类 | 证据 |
| --- | --- | --- | --- |
| `/` | 静态深色开发工具落地页，唯一 CTA 指向 `/dashboard`。 | RESTRUCTURE 为项目库、继续上次项目和首次接入入口。 | `frontend/src/app/page.tsx:5-34` |
| `/dashboard` | 跨版本操作工作台，同时加载材料、建议、演进、任务、旧记忆、工作会话、证据包、冲突、输出和 GitHub 状态。 | DEPRECATE_FROM_PRIMARY_NAV；在项目工作区内 REUSE 其中的接入与访问模块。 | `frontend/src/app/dashboard/page.tsx:278-289,601-879` |
| `/projects` | 项目列表及手动创建、删除项目。 | KEEP 并 REUSE 为全局项目库。 | `frontend/src/app/projects/page.tsx:10-79,81-178` |
| `/projects/[projectId]` | 仅展示名称、描述、状态、仓库、日期和技术栈的元数据页。 | RESTRUCTURE 为默认的项目 Current State 入口，或后续重定向目标。 | `frontend/src/app/projects/[projectId]/page.tsx:10-68` |
| `/projects/[projectId]/history` | 历史概览、当前状态、章节、故事、线程、事件证据和校正控件。 | KEEP 并 REUSE 为 V4 的主项目 History。 | `frontend/src/app/projects/[projectId]/history/page.tsx:57-82,155-224,227-495` |
| `/projects/[projectId]/files` | 面向 zip/路径的模块、文件、架构和文件分析钻取页。 | DEPRECATE_FROM_PRIMARY_NAV；保留为工程深链。 | `frontend/src/app/projects/[projectId]/files/page.tsx:21-95,123-238,290-440` |
| `/project-intelligence` | 混合 ProjectFact 概览、旧 ProjectMemory、沉淀、分析和导航中心。 | DEPRECATE_FROM_PRIMARY_NAV；RESTRUCTURE 为 Current State、Records 和 Agent Context。 | `frontend/src/app/project-intelligence/page.tsx:96-142,181-270` |
| `/project-intelligence/understanding` | 持久化项目理解，具备显式刷新/取消/重试，以及证据、能力、预算和覆盖率诊断。 | REUSE 为次级 Agent Context 或工程诊断。 | `frontend/src/app/project-intelligence/understanding/page.tsx:24-175,205-306` |
| `/project-intelligence/capabilities` 及详情 | 基于事实的能力地图和能力溯源。 | REUSE 为软件项目可选次级视图，不作为通用项目默认页。 | `frontend/src/app/project-intelligence/capabilities/page.tsx:68-87,129-223` |
| `/project-intelligence/fact-sources` | 项目记忆字段的来源列表，含确认状态和溯源详情。 | REUSE 为从 History 或 Records 进入的 Evidence 深链。 | `frontend/src/app/project-intelligence/fact-sources/page.tsx:73-155` |
| `/project-intelligence/analysis-records` 及 `/project-analysis-records/[recordId]` | 项目/文件分析结果历史和详情；详情明确说明其不会自动成为正式项目记忆。 | DEPRECATE_FROM_PRIMARY_NAV；保留为分析记录深链。 | `frontend/src/app/project-intelligence/analysis-records/page.tsx:62-107`；`frontend/src/app/project-analysis-records/[recordId]/page.tsx:89-134` |
| `/project-intelligence/changes` 及 `/project-intelligence/timeline` | 旧 ProjectEvolutionRecord 的归档变化和成长时间线视图。 | LEGACY_COMPATIBILITY_ONLY。 | `frontend/src/app/project-intelligence/changes/page.tsx:57-108`；`frontend/src/app/project-intelligence/timeline/page.tsx:57-116` |
| `/sediment-review` 及 `/sediment-review/[batchId]` | 虽为旧路径名，但读取 ProjectRecordBatch 和 FactHistoryState，展示自动 ProjectFact 与溯源详情。 | RESTRUCTURE 为项目 Records 与 Record Detail。 | `frontend/src/app/sediment-review/page.tsx:71-88,115-219`；`frontend/src/app/sediment-review/[batchId]/page.tsx:28-205` |
| `/timeline` | 全局的 ProjectFact 派生周期/生命周期时间线。 | DEPRECATE_FROM_PRIMARY_NAV；在 History 吸收入口前保留为记录/时间深层视图。 | `frontend/src/app/timeline/page.tsx:43-166,168-320` |
| `/ai-review` | 基于旧 ProjectMemory、DevLog、Task 和 EvolutionRecord 生成输出。 | RESTRUCTURE 为项目级次级 Output 能力，不进入主导航。 | `frontend/src/app/ai-review/page.tsx:101-128,181-345,438-499` |
| `/settings` | Provider CRUD、测试、重复项清理、项目连接状态和模型调用记录。 | KEEP 并 REUSE 为全局 Provider/安全设置；项目级状态移入项目设置或 Agent Context。 | `frontend/src/app/settings/page.tsx:82-126,283-563` |
| `/imports` | Markdown 导入会写入旧开发日志记录。 | LEGACY_COMPATIBILITY_ONLY。 | `frontend/src/app/imports/page.tsx:77-115,118-298` |
| `/tasks` | 旧手动 ProjectChange/Sediment 确认流。 | LEGACY_COMPATIBILITY_ONLY。 | `frontend/src/app/tasks/page.tsx:107-351` |
| `/dev-logs`、`/dev-logs/sources` 及详情 | 基于 DevLog、Task、旧 ProjectMemory 和 EvolutionRecord 的旧每日回顾流。 | LEGACY_COMPATIBILITY_ONLY。 | `frontend/src/app/dev-logs/page.tsx:67-125,337-391`；`frontend/src/app/dev-logs/sources/page.tsx:87-130` |
| `/project-changes/[changeId]`、证据详情及 `/project-sediments/[sedimentId]` | 旧手动变更/沉淀和证据数据。 | LEGACY_COMPATIBILITY_ONLY。 | `frontend/src/app/project-changes/[changeId]/page.tsx:72-349`；`frontend/src/app/project-changes/[changeId]/evidence/page.tsx:74-190`；`frontend/src/app/project-sediments/[sedimentId]/page.tsx:12-130` |
| `/work-sessions/[sessionId]` | 旧 WorkSession 证据详情；Dashboard 已明确将 WorkSession 与 EvidenceBundle 标为兼容证据记录。 | LEGACY_COMPATIBILITY_ONLY。 | `frontend/src/app/work-sessions/[sessionId]/page.tsx:27-95,187-197`；`frontend/src/components/dashboard/PendingChangesPanel.tsx:266-270` |
| `/login`、`/register`、`/reset-password` | 三个路由目前均重定向到 `/`；AuthPanel/AuthPageShell 没有活跃页面消费者。 | LEGACY_COMPATIBILITY_ONLY；旧链接和身份迁移退役后再 REMOVE_LATER。 | `frontend/src/app/login/page.tsx:1-5`；`frontend/src/app/register/page.tsx:1-5`；`frontend/src/app/reset-password/page.tsx:1-5`；`frontend/src/components/AuthPanel.tsx:15-46` |

`frontend/src/app/global-error.tsx:19-76` 是错误边界而非产品路由，应保留；但其中的原始 stack/href 本地存储诊断和英文按钮标签需要单独进行隐私与 UX 审查。

## Shell、导航与第一屏发现

`AppShell` 当前以扁平方式提供六个 V3 模块：工作台、项目记录、项目记忆、项目历程、成果输出、设置。它标注产品为 `ProjectFlow V3.10`，具有固定 256px 侧栏、页面标题和操作槽，但没有全局 Project Switcher、项目级子导航、分组、折叠行为或响应式导航替代方案。见 `frontend/src/components/AppShell.tsx:15-22,28-76`。

项目选择通过 `ProjectContextBar` 在各页面重复实现，而不是由 Shell 统一拥有。组件本身可复用，但作用域局部且直接写入选择状态。见 `frontend/src/components/ui/layout.tsx:25-74`；当前消费者包括 Dashboard 的 `frontend/src/app/dashboard/page.tsx:615-657`、项目记忆的 `frontend/src/app/project-intelligence/page.tsx:184-243` 和 Timeline 的 `frontend/src/app/timeline/page.tsx:171-208`。

根页自称“本地开发过程整理工具”，强调 Git/开发材料，并将所有用户导向 Dashboard；它未提供 V4 所需的项目库、继续项目或首次接入决策。见 `frontend/src/app/page.tsx:5-34`。版本标签也不一致：Shell 写 V3.10，而 Dashboard 写 V3.9。见 `frontend/src/components/AppShell.tsx:35-36` 和 `frontend/src/app/dashboard/page.tsx:601-613`。

## 主要页面结论

History 路由是最强的 V4 起点。它已实现从概览/当前状态到章节、故事、线程、事件和证据的渐进披露，并将工程细节放入可展开区块。应保留其 read-model 语义和校正控件，仅调整项目框架、路由归属和视觉层级。

Dashboard 适合作为组件来源，而非 V4 主页面。其项目接入、本地路径绑定、GitHub 状态、Agent 协议、扫描和输出能力，应分别拆入首次接入、项目设置、Agent Context 与可选输出操作。`ProjectAccessCard` 在 `frontend/src/components/dashboard/ProjectAccessCard.tsx:86-295` 展示了现有技术接入边界；`ArchitectureQuickEntry` 在 `frontend/src/components/dashboard/ArchitectureQuickEntry.tsx:17-56` 适合作为仅工程项目使用的快捷入口。

项目 Records 的语义强于旧 `/sediment-review` URL：该路由展示自动事实批次和溯源。应先重命名和重构该界面，再考虑替换路径。不可重新提升仍保留旧手动确认模型的 `/tasks`。

项目记忆同时包含可复用的当前事实概览和不兼容的旧归档结构。其事实/溯源和持久化理解读取可分别成为 Current State、Evidence 和 Agent Context；ProjectMemory 与旧演进视图只能保留为兼容内容。

## 共享组件、Token 与临时样式

共享 primitive 集合是良好基础：Card、SectionHeader、Button、Badge、Stat、Field 和 EmptyState 位于 `frontend/src/components/ui/primitives.tsx:22-268`。`PageContainer` 也可复用，见 `frontend/src/components/ui/layout.tsx:9-19`。`ResourceTimeline` 提供适合次级列表的筛选和紧凑记录展示，但其内部状态/颜色映射及路径导向的 meta 展示需要 V4 语义契约。见 `frontend/src/components/ResourceTimeline.tsx:28-185`。`SourceCardList` 是可复用的来源摘要卡片，见 `frontend/src/components/sources/SourceCardList.tsx:19-45`。

样式基线尚不完整。`globals.css` 仅包含 Tailwind 指令、静态字体/背景规则，缺少 CSS 变量主题、密度、统一 focus 和 reduced-motion 层。见 `frontend/src/app/globals.css:5-36`。`tailwind.config.ts` 提供有用的 surface/ink/brand/status 语义 Token，但其值静态且保留旧 panel alias。见 `frontend/tailwind.config.ts:7-42`。

许多页面绕过 primitive 层，手写白色/slate 卡片及 blue、emerald、amber、rose 组合。代表性位置为 `frontend/src/app/ai-review/page.tsx:205-345`、`frontend/src/app/imports/page.tsx:122-298` 和 `frontend/src/components/project-intelligence/ProjectAssetPanels.tsx:131-170`。这会造成卡片、状态、focus 行为和视觉语言不一致。根页/认证又是一套与浅色应用界面独立的深色视觉系统。见 `frontend/src/app/page.tsx:12-45`、`frontend/src/components/AuthPageShell.tsx:7-20` 和 `frontend/src/components/AuthPanel.tsx:54-178`。

Dialog、确认与 Toast 语义尚未集中：`FlowGuideDialog` 为手写实现，Toast 未显示 live-region 行为，破坏性操作使用 `window.confirm`。见 `frontend/src/components/dashboard/FlowGuideDialog.tsx:18-86`、`frontend/src/components/ui/toast.tsx:1-25`、`frontend/src/app/settings/page.tsx:225-264` 和 `frontend/src/app/dashboard/page.tsx:436-469`。

## API 与 read-model 概览

前端通过 `frontend/src/lib/api` 读取数据，并未自行拥有第二个数据存储。History 路由已呈现有价值的 V4 read-model 边界：专用的概览/当前状态/校正/证据读取共同供给展示层级。Records 路由读取事实批次和事实历史状态。Understanding 路由读取持久化理解与演进/诊断状态，并使用显式刷新而非隐藏执行。这些是优先保留的 V4 数据来源。

主要债务不是缺少数据，而是 read-model 混用：Dashboard 扇出多代 API，项目记忆混合 ProjectFact 与旧 ProjectMemory，成果输出/每日回顾依赖旧 Task、DevLog 和 EvolutionRecord。V4 页面组合应让每个用户界面只选择一个面向用户的 read-model，并把来源/溯源放入渐进披露。

## 产品债务证据与迁移护栏

目前存在三套相互竞争的历史标签和来源：项目 History、全局 ProjectFact Timeline、旧 EvolutionRecord 成长时间线。见 `frontend/src/app/projects/[projectId]/history/page.tsx:155-495`、`frontend/src/app/timeline/page.tsx:168-320` 和 `frontend/src/app/project-intelligence/timeline/page.tsx:57-116`。

也存在两套相互竞争的记录/确认模型：`/sediment-review` 下的自动 ProjectFact 批次，与 `/tasks` 下旧的手动 Change/Sediment 确认。Dashboard ActivityFeed 和成果输出仍链接旧任务路径。见 `frontend/src/components/dashboard/ActivityFeed.tsx:29-47` 和 `frontend/src/app/ai-review/page.tsx:211-214`。

多个页面直接暴露内部术语或原始状态值，包括项目状态、Provider/模型状态、Token 使用量和 material ID。见 `frontend/src/app/dashboard/page.tsx:631`、`frontend/src/app/projects/page.tsx:148`、`frontend/src/app/settings/page.tsx:533-550` 和 `frontend/src/app/project-intelligence/changes/page.tsx:103-107`。

V4.0-A 迁移规则：

- KEEP History 语义、项目库、全局 Provider 设置，以及事实批次/溯源数据。
- REUSE 共享 primitive、ProjectContextBar 选择语义、ResourceTimeline、来源卡片、项目接入组件和工程快捷组件。
- RESTRUCTURE 根页、AppShell、项目详情、Dashboard 模块、项目记忆、项目 Records 命名和输出位置。
- DEPRECATE_FROM_PRIMARY_NAV Dashboard、项目记忆、全局 Timeline、成果输出、文件/架构、分析历史和可选能力视图。
- 将旧手动任务、每日日志、导入、变更、沉淀、工作会话和旧演进视图保留为 LEGACY_COMPATIBILITY_ONLY。
- 仅当迁移验收证明不存在活跃链接或数据依赖后，才将仅重定向的认证路由和未使用认证展示标记为 REMOVE_LATER。

本审计刻意不删除、重定向或重写任何现有路由。路由移除应在 V4 主导航、深链、旧数据可见性和测试均完成验证后，作为后续验收门控的迁移步骤执行。
