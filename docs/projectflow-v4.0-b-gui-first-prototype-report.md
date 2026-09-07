# ProjectFlow V4.0-B GUI 第一版设计原型报告

日期：2026-09-07。状态：GUI 第一版设计原型完成，已推送 [Draft PR #22](https://github.com/xiaochuqing-dev/ProjectFlow/pull/22)。Owner 已反馈“这个版本不错”并再次明确授权推送；这里记录原话和授权，不换算为量化视觉评分。本轮按设计原型范围交付，PR 保持 Draft。

## 目标与代码基线

按用户提供的 V4.0-B 提示词和 1280 × 801 参考图完成六个核心页面、统一视觉、可运行交互、截图比对与 GitHub Draft PR。代码基线为 `master@1712841b77fd1e8146ce4ab6beaf404e5b1f7a53`，分支为 `codex/v4.0-b-gui-first-prototype`。

V4.0-A 的 [PR #21](https://github.com/xiaochuqing-dev/ProjectFlow/pull/21) 在本轮开始时仍为 Draft，未合入 master。本轮阅读其 `d853e6f` 上的 IA 合同，消费页面职责与全局/项目设置归属，不合并整条分支。原始工作区存在用户未提交修改，本轮使用独立 Git worktree，保留原工作区及数据。

本轮 `npm audit` 复现主线 browserslist 高危、postcss-selector-parser 低危公告；[Apache 官方说明](https://tomcat.apache.org/security-10.html#Fixed_in_Apache_Tomcat_10.1.59)亦确认当前补丁线的安全修复。为通过已有供应链门禁，单独复用 V4-A 已验证的 Tomcat 10.1.59 属性覆写与传递依赖锁刷新（browserslist 4.28.8、postcss-selector-parser 6.1.4），没有升级 Spring Boot、Next、React 或新增依赖。还复用已在 V4-A CI 复现并修复的 Windows 端口占用测试 fixture 上限 45 → 180 秒，检测结束仍立即清理，不改变生产启动行为。

## 参考图如何落地

首先拆解参考图的栏宽、顶部高度、主卡片坐标、正文密度、卡片边框和三处环境背景，再实现真实 HTML/CSS 页面。没有把完整截图作为页面背景，也没有生成含文字的 UI 图片来替代交互。

| 项目 | 本轮实现 |
| --- | --- |
| 桌面视觉基准 | 1280 × 801；左栏 200 px、中央 798 px、证据栏 282 px；顶栏 48 px |
| 当前状态主卡 | 左上坐标约 (216, 128)，宽 766 px、高 211 px；与参考图约 (214, 128)、770 × 211 接近 |
| 信息节奏 | 主卡 → 最近变化/当前判断双栏 → 紧凑历程 → Agent 引语；四周留白与参考图接近 |
| 色彩 | 画布 `#041020`，面板 `#091b2c`，浮层 `#0d2035`，边框 `#193248`，青色 `#31d6eb`，靛蓝 `#5d5af6` |
| 字体与空间 | 系统中文字体；标题约 28 px；正文按卡片密度调整；8 px 空间单位、约 11 px 圆角 |
| 左下装饰 | 专门生成的山脉、流光河谷、低亮夜空 WebP，按侧栏高度重新裁切；保留文字和用户区域 |
| 主卡背景 | 山峰、云层、青蓝山谷路径，左侧叠加暗色阅读层 |
| 右侧气质 | 暗面板、细边框、轻量状态标记、底部蓝紫星球弧光 |

第一轮截图发现左侧山脉位置偏低、主卡峰顶裁切、底部引语空间不足，随后调整背景裁切、背景高度和条目行距。最终增加 390、640、1024、1600 px 宽度检查；窄屏可打开导航和证据抽屉，主内容保留独立滚动。

原始参考图中的 78% 和未来里程碑仅出现在明确标注的设计示例中，计划单独标为“用户计划”。真实项目不计算虚构进度，不从章节名称推导实现程度，不将 Story 自动提升为已确认事实。

## 页面职责与入口

启动仓库根目录 `Start-ProjectFlow.bat` 后，默认本地服务为 `http://127.0.0.1:3000`。旧界面导航中增加“打开 V4 项目工作区”和“查看 GUI 设计示例”。`/workspace` 默认跳转真实项目库；只有显式 `?demo=1` 使用示例。真实项目页使用 `?project=<已有项目 ID>`。

| 页面 | 设计示例路径 | 主要职责与交互 |
| --- | --- | --- |
| 项目库 | `/workspace/projects?demo=1` | 项目入口、搜索、关注筛选、项目切换；添加项目继续使用已有接入流程 |
| 当前状态 | `/workspace/current?demo=1` | 项目现在做到了哪里、已确认变化、冲突与未知、少量历程入口；显式更新状态 |
| 项目历程 | `/workspace/history?demo=1` | 时间篇章与变化故事；示例可切换主题主线，打开 Before/Change/After 和 Evidence |
| Agent 交接 | `/workspace/handoff?demo=1` | 当前状态、强事实、近期变化、未解决项、推荐深读范围；复制有边界说明的交接内容 |
| 项目设置 | `/workspace/project-settings?demo=1` | 项目资料、本地/GitHub 接入、项目级模型选择归属、Obsidian 投影入口说明 |
| 全局设置 | `/workspace/settings?demo=1` | Provider/模型全局配置及凭据状态、外观与关于；实际编辑进入现有安全配置页 |

各页面复用壳层，但中心内容分别采用项目网格、状态摘要、时间篇章、交接文档和设置表单结构。右侧 Evidence 是渐进披露层，不将 SHA 或内部诊断塞进主摘要。Ctrl/Cmd+K 可搜索页面与项目；弹窗可用 Escape 关闭并恢复焦点。

## 已接入的真实能力

- 项目库读取已有项目 API，当前状态读取已持久化的 Current Project State、History Overview 和有界 Story 列表；全部沿用已有用户/项目归属校验。
- 最近已确认变化只消费 `recentConfirmedChanges`，普通 Story 显示“变化记录”。STALE、降级、冲突、未知和读取失败均保留说明，失败不回退到示例内容。
- “更新项目状态”调用现有 History refresh Durable Job，防止重复点击；刷新或离开后能恢复活动任务。任务结束后重新读取持久化结果，已完成缓存任务也触发读取；失败保留上次可信展示。
- Story 详情读取真实 Story 与 Evidence，第一层描述变化前后，工程证据按需展开。
- Agent 交接读取 Context Package v2 的实际 DTO，复制内容包含强事实、冲突、未知、未读范围和截断说明。推荐深读勾选只控制复制范围。
- 全局设置读取真实 Provider 配置状态，不显示 Key。示例刷新、示例设置和示例复制不会向 API 写入，也不会创建 ProjectFact。

生产后端事实语义、数据库 schema、模型协议、Provider 凭据存储、Gateway 和历史演进层没有改动。没有添加 Desktop Shell、Electron/Tauri、watcher、daemon 或通用工作流引擎。

## 截图与素材

[并排/叠加交互比对](acceptance-evidence/v4.0-b/comparison.html)保留原始视口尺寸，可切换叠加并调节透明度；另附[静态并排截图](acceptance-evidence/v4.0-b/comparison.png)。[素材来源和三份生成提示词](acceptance-evidence/v4.0-b/assets.md)记录清晰来源；三张原创环境图由本轮内置 imagegen 生成，WebP 合计约 67 KB，无外链运行依赖。

| 截图 | 目的 |
| --- | --- |
| [当前状态](acceptance-evidence/v4.0-b/current-desktop.png) | 1280 × 801 主视觉验收 |
| [项目库](acceptance-evidence/v4.0-b/projects-desktop.png) | 项目入口与搜索结构 |
| [项目历程](acceptance-evidence/v4.0-b/history-desktop.png) / [主题主线](acceptance-evidence/v4.0-b/history-threads.png) | 页面职责与两条阅读轴 |
| [Agent 交接](acceptance-evidence/v4.0-b/handoff-desktop.png) | 文档式交接布局 |
| [项目设置](acceptance-evidence/v4.0-b/project-settings-desktop.png) / [GitHub 接入](acceptance-evidence/v4.0-b/project-settings-github.png) | 项目级配置归属 |
| [全局设置](acceptance-evidence/v4.0-b/settings-desktop.png) | 全局 Provider 配置归属 |
| [Story 与 Evidence](acceptance-evidence/v4.0-b/story-evidence-detail.png) | 可访问的详情浮层 |
| [稀疏材料](acceptance-evidence/v4.0-b/current-sparse.png) / [空项目](acceptance-evidence/v4.0-b/current-empty.png) | 不虚构历史或实现进度 |
| [真实 DTO 过期状态](acceptance-evidence/v4.0-b/current-live-stale.png) / [服务失败](acceptance-evidence/v4.0-b/current-service-error.png) | 用合成 API fixture 检查读取边界，不冒充真实项目数据 |
| [390 px](acceptance-evidence/v4.0-b/current-390.png) / [640 px](acceptance-evidence/v4.0-b/current-640.png) / [1024 px](acceptance-evidence/v4.0-b/current-1024.png) / [1600 px](acceptance-evidence/v4.0-b/current-wide.png) | 导航、抽屉和窄屏阅读 |

## 主要文件

- `frontend/src/app/workspace/`：六页路由、布局、受作用域限制的设计 token/CSS。
- `frontend/src/components/workspace/Workspace.tsx`：导航、命令入口、任务刷新与恢复、Evidence 面板和详情。
- `frontend/src/components/workspace/WorkspacePages.tsx`：六页各自的内容结构与交互。
- `frontend/src/lib/workspace-preview.ts`：显式示例数据和真实 DTO 适配；`frontend/src/lib/api.ts`：复用现有后端端点的类型化接口。
- `frontend/src/components/AppShell.tsx`：从现有产品进入 V4 原型。
- `frontend/public/workspace/`：三张环境图与代码绘制的标记 SVG。
- `frontend/e2e/workspace.spec.ts`、`frontend/playwright.workspace.config.ts`：独立生产预览/适配层回归；`frontend/e2e/project-history.spec.ts`：真实前后端新 GUI 链路。
- `backend/src/test/java/com/projectflow/ProjectHistoryDogfoodAcceptanceTest.java`：复用 V4-A 已有的测试可靠性修正，按 DOMINANT cluster 校验章节代表性，并固定 T0–T7 checkout 文件时间；未修改冻结期望或生产语义。
- `backend/pom.xml`、`frontend/package-lock.json`、`.github/workflows/windows-release.yml`：已有安全补丁及 Windows 测试 fixture 修复。

## 验证记录

以下记录采用实际执行证据。功能提交为 `42c40f76a4d4162bdd1fce1dedad5087511dbd74`；后续回填仅更新报告与 Agent result，不改变生产代码或测试。

| 验证 | 当前观察结果 |
| --- | --- |
| TypeScript / frontend production build | 通过；新增路由包含在生产构建中 |
| 前端合同测试 | 59/59 通过 |
| 独立生产 GUI Playwright | 10/10 通过，覆盖六页、素材、几何、键盘、宽窄屏、空/稀疏/失败、真实 DTO、刷新恢复和缓存结果 |
| 完整 Playwright | 依赖补丁后 20/20 通过（2.5 分钟）：10 条真实前后端流程 + 10 条 GUI/适配 fixture 检查；另行增强真实 Evidence 非空断言并定向复验 1/1 通过 |
| 后端/H2 | 依赖补丁后全量 717 项：0 failure、0 error、11 项条件跳过；Dogfood 3/3 通过 |
| PostgreSQL 16 | 同一最终依赖配置下，Docker Desktop / PostgreSQL 16 实际运行 7/7 通过（6 项业务/并发约束 + 1 项 Flyway 迁移）；完整 Failsafe profile 共 13 项，0 失败，另外 6 项显式选择的外部/真实模型评测未启用 |
| 根目录启动器 | 工作区外调用相对路径 `Start-ProjectFlow.bat -NoBrowser`，重新安装并校验依赖、生产构建、启动成功。登录、新 GUI、后端 health 均为 200；Build ID 为 `GF0rm56bQ44s6BCs026cZ`。按启动器提示正常退出，3000/8080 无监听残留；见[原始启动证据](acceptance-evidence/v4.0-b/embedded-startup.json) |
| 依赖审计 | 补丁后全量 `npm audit` 0 项漏洞；Spring Boot、Next、React 与生产依赖集合不变 |
| GitHub Quality CI | 功能提交的 [push run 34099041795](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/34099041795) 与 [PR run 34099060412](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/34099060412) 全部成功，包含 backend/H2、PostgreSQL、真实 V3.9 升级、frontend、browser、Hermes、Obsidian、敏感内容及 OSV 门禁；可选真实模型评测未启用 |
| Windows CI | 功能提交的 [push run 34099040982](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/34099040982) 与 [PR run 34099060000](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/34099060000) 全部成功，覆盖 portable 构建、解包运行、备份/恢复、旧 H2 基线、端口冲突与敏感标记检查 |
| 视觉验收 | Agent 已检查截图；Owner 对本版给出正面反馈并批准推送，没有填写或推算人工评分 |

失败与修复记录：首轮全量后端暴露历史测试文案及 checkout 文件时间不稳定，采用 V4-A 已验证的测试层修正。随后一次当前工作区 cacheHit 失败，在停止并发写入、将运行日志移至忽略目录后消失；保留原缓存命中断言并完成 3/3 定向和最终全量复验。首轮浏览器失败分别来自路由公告与业务 alert 的重复匹配、将 Next 开发工具 POST 误计为业务写入，以及窄屏路由尚未稳定就打开抽屉；仅修正选择器、API 范围与等待条件，最终生产 GUI 和完整产品 E2E 均通过。没有放宽生产安全策略或冻结的历史期望。

GUI 适配测试使用合成数据；真实前后端测试使用临时测试项目和本地固定模型服务，只证明工程链路，不声称真实 Provider 语义验收。后端 Dogfood 按已有测试读取 ProjectFlow 仓库自身；除此之外没有读取用户其他真实项目材料，没有新增付费模型调用验收，也未修改已冻结的历史语义样本。

## 必要调整、设计债与下一轮

栏宽、主卡位置、信息节奏、午夜蓝层次、左下山脉和右下星球已按参考图落实。环境插画为重新创作，山峰轮廓、云层纹理、图标和字体栅格不会与原图逐像素相同；原图的装饰性日期改为清楚的示例标记。没有宣称像素级 100% 一致。

真实历程暂展示已有有界章节/故事摘要，并提供完整旧历程入口；新主题主线在示例中可完整交互，真实完整 Thread 阅读仍使用原页面。新的全局设置可查看配置并进入既有 CRUD；项目级 Provider 绑定和 Obsidian GUI 同步没有虚构保存成功。上述边界均在界面标明。

下一轮基于 Owner 已给予正面反馈的这版，收敛更具体的视觉意见；随后按优先级把既有 Thread 详情与 Provider 编辑交互迁入这套样式。无需重新设计事实引擎，也不应从本轮直接扩大到桌面壳或发布工程。
