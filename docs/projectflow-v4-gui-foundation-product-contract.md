# ProjectFlow V4.0-A 产品与信息架构合同

状态：READY_FOR_OWNER_REVIEW_UPDATED

复核日期：2026-09-05

本文冻结 V4 的产品心智、信息架构、页面职责、披露边界和交互语义，不冻结最终视觉，也不启动 V4-B。Owner 已认可 Hybrid 项目中心、Current State 第一屏和渐进披露等核心方向；本次只修正审阅中发现的产品边界，修订后仍等待 Owner 最终确认。

## 1. 产品心智模型

推荐心智模型：

ProjectFlow 是一个以项目为中心、以 Evidence 为边界，持续保持“现在是什么、怎样走到这里、下一位 Agent 应知道什么”可读的长期认知工作区。

这不是最终营销文案。它约束产品结构：

- 核心对象是项目及其可追溯状态，不是任务数量、完成率或开发日志。
- ProjectFact 是唯一强事实来源；Current Project State、Project History 和 Agent Context 是适用于通用电脑项目的核心读取或派生视图。
- Capability Map 只适用于部分项目，是可选次级视图。软件项目可能天然适合按能力阅读；研究报告、演示文稿、品牌设计、数据分析等项目不得被迫映射为“能力”。
- 用户先看到可理解的当前结果和演变故事，需要时才下钻到 Commit、路径、完整 ID、Evidence 和诊断。
- 软件、研究文档、演示文稿、数据分析等项目使用同一套 Current State、History、Evidence 语言；Git 只是可能存在的来源。

## 2. IA 方案比较

| 方案 | 普通用户理解 | 差异化与连续性 | 非代码项目 | 多项目与桌面空间 | 扩展与集成 | 主要风险 |
| --- | --- | --- | --- | --- | --- | --- |
| A. 全局功能导航 | 初看入口多、学习成本高 | History、Memory、Intelligence 被拆成内部模块 | 容易被“开发日志、变更、任务”主导 | 切项目后仍需反复确认上下文 | 插件可单列，但会继续膨胀 | 延续 V3 路由包袱，退化为功能集合 |
| B. 纯项目工作区 | 项目内语境最清晰 | Current State、History、Agent Context 很自然 | 适配最好 | 单项目空间利用好 | 全局 Provider、账号和跨项目入口归属不自然 | 首次进入与跨项目操作容易被藏深 |
| C. 混合式项目中心 | 全局只保留项目库与设置，项目内问题清晰 | 最能表达长期认知与 Evidence 下钻 | Git 不再是入口前提 | 支持项目切换、最近项目和窄窗口 | 全局与项目集成有明确归属 | 需要严格防止全局层重新长成功能菜单 |

推荐方案：C，混合式项目中心。

理由：

- 它保留 Project Library 作为跨项目入口，同时让项目工作区只围绕用户真正关心的三个问题组织。
- Current State 可以成为项目第一屏，History 与 Agent Context 各自保持清晰目的。
- Evidence、Correction、GitHub、Provider、Obsidian 不因后端存在 API 就成为一级导航。
- 它能在不删除旧路由的前提下渐进迁移，也为未来命令搜索和桌面壳保留稳定边界。

## 3. 最终推荐导航

    ProjectFlow
    ├─ 项目库
    ├─ 最近项目 / 项目切换
    └─ 全局设置
       ├─ 模型 Provider
       ├─ 运行环境与数据
       └─ 全局集成

    项目工作区
    ├─ 当前状态
    ├─ 项目历程
    ├─ Agent 上下文
    └─ 项目设置与集成

    按需下钻
    ├─ Chapter
    ├─ Story
    ├─ Evolution Thread
    ├─ Raw Event
    ├─ Evidence / 工程详情
    └─ Correction

全局 Rail 只承担项目库、项目切换、全局设置和未来统一搜索入口。项目子导航承担当前状态、项目历程、Agent 上下文和项目设置。路径、完整 SHA、Evidence ID、Provider 诊断和内部状态不进入 Rail。

Agent Context / Context Package 继续作为内部精确概念。用户导航最终使用“Agent 上下文”“Agent 交接”还是“协作上下文”，必须由 V4-B 的真实界面验证，V4-A 不永久锁词：

FINAL_USER_NAV_LABEL = TO_BE_VALIDATED_IN_V4_B

## 4. 启动与首页行为

- 首次运行：进入独立 onboarding，先添加第一个项目或选择项目材料；Provider 是可选步骤并允许跳过，不显示营销型首页。
- 数据目录默认使用 ProjectFlow 已验证的推荐安全位置，不要求普通用户理解数据库、runtime root 或 LocalAppData，也不把数据目录选择作为首次使用门槛。
- 数据目录、portable mode、backup location、runtime 与 diagnostics 只进入高级设置或专门的安装、迁移流程。
- 已有有效最近项目：打开该项目的“当前状态”。
- 没有有效最近项目或最近项目不可访问：打开“项目库”，说明原因并保留修复入口。
- browser dev mode 与 desktop mode 使用同一应用路由行为；根路由只做上述可预测分流。
- 当前根页的 LOCAL-FIRST WORKBENCH 和“本地开发过程整理工具”不再作为 V4 产品首页。

## 5. Current State 与旧 Dashboard 的产品边界

Current State 回答：“这个项目现在能确认到什么状态，哪些地方需要注意？”

它是 persisted、model-free、corrected-history derived read model，不是 ProjectFact，也不是项目成熟度推断。第一层包含：

- 当前能确认的状态；
- 最近主要结果与最近变化；
- 当前或最近 Chapter 的可理解摘要；
- 少量活跃或最近 Evolution Thread；
- Unknown、Conflict、coverage、stale、degraded 与刷新注意；
- 最近成功更新时间。

Current State 不能退化为只读 Dashboard。第一层必须提供带正式文字的主要动作“更新项目状态”或经 V4-B 验证的等价中文，不能只放一个弱化的无文字刷新图标。产品闭环为：进入项目并阅读当前状态 → 发现 stale、新材料或主动更新需要 → 显式更新 → 保留旧可信结果并后台刷新 → Current State、History 与 Agent Context 按既有持久化链增量更新 → 继续阅读。

刷新期间保留旧结果、滚动位置和展开状态，不整页白屏，不清空可信内容。RefreshStatus 使用正式中文说明阶段；Job ID 和内部状态只进入工程详情。

旧 Dashboard 是 V3 工作流聚合页，混合项目接入、变化分析、任务、输出、活动、Provider 和工程状态。它不能作为 V4 首屏合同。V4-B 首屏先消费现有 Current State；只有实际性能证据证明多请求不可接受时，V4-C 才可在既有 History/Gateway 服务上补 compact workspace read model，禁止创建第二套 truth。

## 6. History 信息层级

默认阅读与下钻顺序可以表达为：

Overview → Chapter → Story → Thread → Raw Event → Evidence

该顺序是界面入口的串联，不是对象包含关系，尤其不表示 Thread 是 Story 或 Chapter 的普通子详情。Project History 至少有两条互补阅读轴：

A. 时间阶段轴：Overview → Chapter → Story，回答“项目在不同阶段发生了什么？”

B. 长期主题轴：Evolution Thread → 关联 Story → 必要 Evidence，回答“某一长期主题是怎样跨阶段演进的？”

一个 Thread 可以跨多个 Chapter，一个 Story 可以参与 Thread；Thread 不是 Tag，也不是 Chapter 的子对象。Chapter 与 Thread 是互补导航。History Overview 默认以 Chapter 为主要入口，Current State 或 History Overview 可把少量活跃、最近 Thread 作为旁路入口；Thread 页面必须能返回相关 Story 与 Chapter。本修订不改变 backend membership 或 identity。

- Overview：阶段时间线、当前或最近阶段、最近变化，以及产品化的覆盖、冲突和未知提示。
- Chapter：一个真实阶段的标题、摘要、主要成果与 Story 集合；Supporting 信息按需展开。
- Story：标题、摘要、此前状态、本次变化、当前结果、时间、必要状态与修正入口。
- Thread：一个长期主题如何跨 Story 演进，不是普通标签聚合。
- Raw Event：精确发生记录，仅在进一步核查时出现。
- Evidence：Commit、路径、来源类型、完整标识、claim/authority/currentness 和诊断；默认在 Drawer 或独立工程详情中出现。
- Audit：完整 ID、hash、内部状态、聚类权重等只用于最深层核查。

用户看到的是故事结构，系统内部继续保存事实与 Evidence。

## 7. 一级页面合同

### 7.1 项目库

- PAGE PURPOSE：进入、创建、导入和切换项目。
- PRIMARY USER QUESTION：我有哪些项目，应该进入哪一个？
- PRIMARY ACTION：打开项目。
- SECONDARY ACTION：添加或导入项目；修复不可访问的项目来源。
- FIRST-LAYER DATA：名称、简短说明、最近可确认更新时间、材料连接状态、少量注意项。
- DRILLDOWN DATA：路径、Git/GitHub、技术栈、历史覆盖、精确诊断。
- EMPTY STATE：解释可添加任意电脑项目，提供添加目录、ZIP 或其他现有入口。
- LOADING STATE：保留页面骨架和最近已知排序，不闪现空列表。
- STALE / DEGRADED STATE：项目仍可打开；标明列表摘要可能过期或部分连接暂不可查。
- UNKNOWN / CONFLICT STATE：只显示可确认的项目信息；把来源冲突作为注意项。
- ERROR STATE：说明项目列表无法读取，提供重试和本地服务检查，不暗示项目已丢失。

### 7.2 项目当前状态

- PAGE PURPOSE：成为进入项目后的默认第一屏。
- PRIMARY USER QUESTION：这个项目现在做到哪里了？
- PRIMARY ACTION：使用带文字的“更新项目状态”或经 V4-B 验证的等价正式中文，显式启动既有持久化刷新链。
- SECONDARY ACTION：阅读当前可确认状态；打开最近 Chapter、Thread、Unknown 或 Conflict。
- FIRST-LAYER DATA：当前可确认状态、最近主要结果、最近变化、当前性、更新时间和注意项。
- DRILLDOWN DATA：相关 Chapter/Story/Thread、完整覆盖限制、Evidence 和工程诊断。
- EMPTY STATE：说明尚无可确认状态，并给出连接材料或首次刷新入口。
- LOADING STATE：Current State Summary、最近变化和注意区使用稳定 skeleton。
- REFRESHING STATE：旧可信结果、滚动位置和展开状态继续保留；正式中文 RefreshStatus 说明进度，Job ID 只在工程详情出现。
- STALE / DEGRADED STATE：继续显示上次可确认结果，明确“可能不是最新”或“部分信息暂时无法更新”。
- UNKNOWN / CONFLICT STATE：Unknown 表达证据不足，Conflict 并列显示矛盾信息和核查入口。
- ERROR STATE：只有没有可信可用结果时使用错误态；保留刷新与服务检查。

### 7.3 项目历程

- PAGE PURPOSE：解释项目如何一步步发展到当前状态。
- PRIMARY USER QUESTION：这个项目是怎样走到现在的？
- PRIMARY ACTION：按阶段浏览 Chapter。
- SECONDARY ACTION：切换到长期 Evolution Thread 轴，或浏览最近变化、覆盖限制和修正记录。
- FIRST-LAYER DATA：历程概览、时间阶段轴、代表性项目阶段、最近变化、少量活跃演变主线与产品化覆盖提示。
- DRILLDOWN DATA：时间轴的 Chapter/Story、主题轴的 Thread/关联 Story，以及 Raw Event、Evidence 和 Audit。
- EMPTY STATE：区分“尚未刷新”“确实没有历史”“项目没有 Git 但有其他材料”。
- LOADING STATE：时间线位置与章节卡骨架固定，避免布局跳动。
- STALE / DEGRADED STATE：保留最后成功历程，标注当前性和未覆盖范围。
- UNKNOWN / CONFLICT STATE：挂靠到相关 Chapter/Story，同时提供全局汇总。
- ERROR STATE：说明历程读取失败，不覆盖上次成功结果。

### 7.4 Chapter 详情

- PAGE PURPOSE：阅读一个阶段的目标、成果和内部 Story。
- PRIMARY USER QUESTION：这个阶段发生了什么，留下了什么结果？
- PRIMARY ACTION：阅读阶段摘要并进入 Story。
- SECONDARY ACTION：查看 Supporting 内容、跨阶段 Thread 关联和展示修正。
- FIRST-LAYER DATA：阶段标题、摘要、时间、主要成果、Story 列表。
- DRILLDOWN DATA：成员事件、来源覆盖、完整 ID 与阶段诊断。
- EMPTY STATE：阶段存在但没有可展示 Story 时说明材料边界。
- LOADING STATE：标题、摘要与 Story 列表分区 skeleton。
- STALE / DEGRADED STATE：保留阶段内容并标记受影响范围。
- UNKNOWN / CONFLICT STATE：贴近对应成果展示，不泛化成整个项目错误。
- ERROR STATE：保留返回历程入口，允许重新读取。

### 7.5 Story 详情

- PAGE PURPOSE：用自然语言解释一次可追踪变化。
- PRIMARY USER QUESTION：此前是什么、本次变了什么、现在结果是什么？
- PRIMARY ACTION：阅读状态转换。
- SECONDARY ACTION：修正展示；打开相关 Thread 或 Evidence，并能从 Thread 返回所属 Chapter。
- FIRST-LAYER DATA：标题、摘要、此前状态、本次变化、当前结果、时间、必要状态。
- DRILLDOWN DATA：Raw Event、Commit、路径、完整 ID、claim/authority 与诊断。
- EMPTY STATE：缺少某一段时明确“现有材料未能确认”，不补写故事。
- LOADING STATE：StateTransition 结构固定。
- STALE / DEGRADED STATE：指出具体段落或来源的当前性限制。
- UNKNOWN / CONFLICT STATE：与相关 claim 相邻显示。
- ERROR STATE：修正失败不影响原 Story 阅读；Evidence 失败只影响下钻。

### 7.6 Agent 上下文

- PAGE PURPOSE：先用产品语言展示可交给下一位 Agent 的有界、可追溯内容，再按需提供精确工程详情。
- PRIMARY USER QUESTION：如果现在换一个 Agent，它应该知道什么？
- PRIMARY ACTION：复制交接内容。
- SECONDARY ACTION：查看已确认的重要事实、冲突、未知和建议进一步查看的材料；按需打开工程详情。
- FIRST-LAYER DATA：项目当前状态、已确认的重要事实、最近主要变化、仍未解决的冲突与未确认项、建议进一步查看的材料、交接内容最近更新时间，以及用正式中文表达的适用范围与内容限制。
- DRILLDOWN DATA：Context Package、package revision、retrieval mode、persisted-only、budget、truncation、Evidence refs、trust guidance 和其他精确内部字段。
- EMPTY STATE：说明当前没有足够持久化材料，不自动触发模型或扫描。
- LOADING STATE：保持分区结构，复制操作禁用并说明原因。
- STALE / DEGRADED STATE：继续展示上次交接内容，第一层说明最近更新时间、适用范围和内容限制；revision 进入工程详情。
- UNKNOWN / CONFLICT STATE：作为交接内容的正式组成部分，不包装成“已解决”。
- ERROR STATE：说明读取失败并提供重试；不在 GET 中隐式重建。

### 7.7 项目设置与集成

- PAGE PURPOSE：管理这个项目怎样连接材料和可选能力。
- PRIMARY USER QUESTION：这个项目连接了什么，哪些连接需要处理？
- PRIMARY ACTION：查看或更新项目级连接。
- SECONDARY ACTION：检查 GitHub、Agent bridge、Obsidian 投影能力和 Provider 归属。
- FIRST-LAYER DATA：本地材料连接状态、Git 是否存在、GitHub 配置状态、Provider“已配置”状态、可用的投影能力。
- DRILLDOWN DATA：路径、命令与探测诊断、最近 probe、凭证状态、完整错误。
- EMPTY STATE：逐项说明未配置不是失败，并给出设置入口。
- LOADING STATE：每个 integration 独立加载，页面其他设置仍可用。
- STALE / DEGRADED STATE：区分持久化配置与 live check；不能把“已配置”写成“服务可用”。
- UNKNOWN / CONFLICT STATE：保留可确认配置，要求用户核查冲突连接。
- ERROR STATE：局部 IntegrationStatus 失败不使整个设置页不可用。

全局设置是另一职责页，负责账号、全局 Provider、运行环境、备份和全局集成，不与项目设置混放。

Provider ownership 冻结如下：

- 全局设置拥有 Provider CRUD、API Key / Secret 管理、Credential Store、Endpoint、Protocol、模型配置、默认 Provider、全局连接测试和全局安全状态。
- 项目设置只负责选择当前项目使用哪个已配置 Provider、展示项目级状态与最近探测，并提供跳转到全局 Provider 设置。项目级 override 只有真实需求成立时才引入。
- 项目设置不得再次保存 API Key、复制 Provider CRUD、创建第二套 Credential，或用“已配置”冒充“当前可用”。
- 当前 backend 若没有项目级 Provider binding，只记录为条件性 gap；不得为了 GUI 先创造新的配置系统。

Obsidian 的配置入口与日常使用入口分离：

- 项目设置 / 集成页负责是否启用投影、Vault / projection 配置、当前能力是否配置、dry-run / validation / sync 设置，以及真实可用时的 capability / CLI status。
- Current State、History、Chapter 或 Story 在已有对应投影且当前能力真实支持时，可提供“在 Obsidian 中打开”“查看投影状态”“上次同步时间”或“打开对应 Note”等上下文入口，用户不必每次先进入设置。
- 当前只有既定 CLI 能力且没有 Web REST status，V4-B 不得伪造实时同步状态。未来只读 status adapter 只能扩展现有 integration boundary，不能成为第二套 Obsidian truth。
- ProjectFlow 负责长期项目认知与结构；Obsidian 负责用户知识阅读、编辑和延展，双方互补而非替代。

## 8. Progressive Disclosure 与产品语言

直接采用 docs/product-language-and-progressive-disclosure-contract.md，不另建冲突词典。

第一层：

- 使用正式、自然、简洁的中文用户语言。
- Before、Change、After 分别显示为“此前状态”“本次变化”“当前结果”。
- Git、Commit、PR、CI、API、Token、SHA、HTTP、JSON 等行业标准术语保留。
- 不直接显示 internal enum、Representative Cluster JSON、Evidence UUID 列表和完整 SHA 墙。

用户层命名合同：

| 内部或工程概念 | 用户第一层默认表达 | 冻结状态 |
| --- | --- | --- |
| Current State | 当前状态 | 已冻结 |
| History | 项目历程 | 已冻结 |
| Chapter | 项目阶段 / 历程篇章 | V4-B prototype 决定最终词 |
| Story | 变化故事 / 变化记录 | V4-B prototype 决定最终词 |
| Evolution Thread | 演变主线 | 已冻结 |
| Evidence | 证据 | 已冻结 |
| Engineering Details | 工程详情 | 已冻结 |
| Agent Context | Agent 上下文 / Agent 交接 | V4-B prototype 验证 |
| Before | 此前状态 | 已冻结 |
| Change | 本次变化 | 已冻结 |
| After | 当前结果 | 已冻结 |

Git、Commit、PR、CI、API、Token、SHA、HTTP、JSON 不翻译。本表只约束用户第一层默认展示；技术文档与工程详情仍可使用 Chapter、Story、Thread、Context Package、revision、persisted-only 等精确术语。

标识合同：

- 数据层和业务逻辑始终保留完整值。
- GUI 默认短显示，长度由 V4-B 以碰撞、可读性和窗口证据确定，不在 V4-A 锁死。
- 完整值通过复制、展开或工程详情获得。
- 短显示只用于呈现，绝不参与查找、比较、保存或权限判断。

## 9. 统一状态系统

状态既描述可用性，也描述认识边界。当前性、覆盖、冲突和运行错误是正交维度，不得压成单一红黄绿状态。

| 状态 | 语义 | 默认用户表达 | 有可信旧结果时 |
| --- | --- | --- | --- |
| LOADING | 首次读取尚未完成 | 正在读取当前信息 | 保留稳定骨架 |
| EMPTY | 成功读取但没有对象 | 这里还没有可展示内容，并给出下一步 | 不适用 |
| READY | 当前结果完整可用 | 通常不显示额外横幅 | 显示结果 |
| PARTIAL | 有结果但覆盖不完整 | 已显示可确认部分，并说明未覆盖范围 | 显示结果 |
| STALE | 结果可能过期 | 当前信息可能不是最新状态 | 显示最后可确认结果 |
| DEGRADED | 部分来源或能力不可用 | 部分信息暂时无法更新，当前仍显示上次可确认结果 | 显示结果 |
| UNKNOWN | Evidence 不足以确认 | 现有材料暂时无法确认 | 不猜测 |
| CONFLICT | 来源相互矛盾 | 发现相互冲突的信息，需要进一步确认 | 并列保留冲突 |
| ERROR | 无可信结果且请求失败 | 当前内容无法读取 | 不用空态伪装 |
| NEEDS_ATTENTION | 聚合的可处理异常 | 有若干事项需要处理 | 只聚合真实可操作项 |
| REFRESHING | 后台显式刷新中 | 正在更新，当前结果仍可查看 | 保留布局和结果 |

层级与特殊行为：

- 页面级：只在整个页面没有可信内容时使用 LOADING、EMPTY 或 ERROR。
- section 级：各区独立呈现 PARTIAL、STALE、DEGRADED、UNKNOWN；不得让一个来源失败遮住其他区。
- row/card 级：状态贴近具体 Story、Thread、Integration 或 claim。
- background refresh：保留既有内容、滚动位置和展开状态，禁用重复刷新，不发生整页白屏或布局跳动。
- destructive action：说明对象、影响和不可逆后果；安全选项获得默认焦点，必须显式确认。
- correction save：不把乐观更新当成新 truth；冲突时保留编辑草稿，展示 presentation revision 差异并允许重载。
- provider unavailable：区分未配置、凭证缺失、最近探测失败和本次调用失败；“已配置”不等于“可用”。
- local backend unavailable：全局说明本地服务不可达，保留最近持久化页面外壳，不把项目数据描述为丢失。

## 10. Semantic Component Contract

组件 API 先冻结“表达什么”，不冻结最终外观。交互型组件优先使用成熟无障碍 primitive，业务组件不自行实现 focus trap、popover positioning 或键盘模型。

下表是语义目录，不是 V4-B 一次性实现清单。真实核心页面驱动 component foundation；只有页面出现真实需要时才增加 primitive 或业务组件，不能先脱离页面制造完整 UI Kit。

| 组件 | Purpose / Input semantics | 用户层 / 工程层 | Interaction 与 Accessibility | 长内容与状态 |
| --- | --- | --- | --- | --- |
| AppFrame | 全局应用框架；输入会话、全局导航和当前项目 | 用户层只显示产品入口；不泄露运行时诊断 | landmark、跳过链接、键盘导航、可调整窗口 | 窄窗切换为克制 Rail；服务异常保留框架 |
| GlobalSidebar / Rail | 项目库、最近项目、设置、搜索入口 | 工程模块不进入一级导航 | 当前项语义、tooltip、完整键盘顺序 | 名称截断可展开；折叠不丢上下文 |
| ProjectSwitcher | 切换项目；输入项目列表与最近选择 | 显示名称和少量状态；路径仅下钻 | combobox/listbox 语义、搜索、Esc 恢复焦点 | 长中文可换行；不可访问项目保留修复入口 |
| ProjectHeader | 当前项目身份和全局注意 | 名称、摘要、当前性；工程连接按需 | 正确 heading、动作顺序稳定 | 长名称换行；STALE/DEGRADED 不挤压主标题 |
| ProjectSubNavigation | 四个项目目的页 | 不显示内部模块名 | tab/navigation 语义、键盘可达 | 窄窗可滚动或菜单化，当前项始终可见 |
| CurrentStateHero / Summary | confirmed state、结果、当前性 | 先自然语言；revision 和来源下钻 | heading、更新时间可读、刷新有 live status | 长摘要可展开；各状态保留最后可信内容 |
| ChapterTimeline | 有序 Chapter 和覆盖范围 | 阶段故事；事件密度下钻 | 列表/时间语义、非颜色唯一编码 | 长历史虚拟或分页；空/部分覆盖明确 |
| ChapterCard | 一个阶段摘要 | 标题、时间、成果；成员与诊断下钻 | 整卡不伪装按钮，明确主链接 | 摘要限行可展开；未知贴近卡片 |
| StoryCard | 一次变化的可读摘要 | 变化故事；Event/Evidence 下钻 | 标题链接、修正独立按钮 | 长文本分段；冲突不被截断隐藏 |
| ThreadCard / ThreadPath | 长期主题的演进路径 | 当前结果与关键转折；聚合依据下钻 | 有序列表、键盘逐项访问 | 长 Thread 分段加载；gap 明示 |
| StateTransition | 此前状态、本次变化、当前结果 | 三段用户语言；claim 详情下钻 | 正确顺序与 headings，不只靠箭头/颜色 | 缺段显示 Unknown，不补写内容 |
| StatusNotice | 通用可用性/当前性通知 | 产品化短文案；内部 enum 下钻 | status/alert 按紧急度使用，焦点不被抢夺 | 文案换行；同类聚合避免横幅堆叠 |
| ConflictNotice | 相互冲突信息 | 并列冲突与核查动作；来源下钻 | 不只用红色，关联对象可定位 | 不截掉冲突双方；局部失败不升级整页 |
| UnknownNotice | 无法确认的信息 | 说明证据边界；缺口下钻 | 非错误语义，关联上下文清楚 | 多项可折叠；Unknown 不伪装 Empty |
| CoverageNotice | 覆盖范围与限制 | 简短范围说明；计数和限制下钻 | 可读描述，不只用百分比 | 大量限制摘要后展开 |
| EngineeringDetails | 工程字段容器 | 默认折叠；路径、SHA、状态在内 | disclosure 语义、保留触发器焦点 | code/path 可断行或横向局部滚动 |
| EvidenceDrawer | Evidence 核查面板 | 用户从 claim 进入；完整证据在面板 | Dialog/Drawer focus trap、Esc、关闭后还焦 | 面板独立滚动；加载/错误不关闭背景上下文 |
| ShortIdentifier | 完整 ID 的纯展示映射 | 短值；完整值不进入业务比较 | 有可感知完整标签 | 碰撞时自动加长；UNKNOWN 不显示假值 |
| CopyableIdentifier | 完整标识复制 | 默认短值；复制完整值 | 按钮可键盘触发，成功/失败反馈可读 | 长值不撑破布局；剪贴板失败保留选择 |
| CorrectionEditor | presentation correction 编辑 | 自然语言字段；revision/冲突在高级区 | Dialog 表单、脏数据离开提示、错误聚焦 | 草稿保留；409/冲突不覆盖用户输入 |
| RefreshStatus | durable refresh 的进度与结果 | 人话阶段；job ID 下钻 | live region 节流、取消/重试语义明确 | 后台刷新不跳布局；失败保留旧结果 |
| EmptyState | 成功但无内容 | 原因与一个主要下一步 | heading、说明、可操作按钮 | 不与 ERROR/UNKNOWN 混用 |
| LoadingSkeleton | 首次或区块加载 | 模拟稳定结构，不伪造内容 | aria-busy，避免读屏朗读装饰 | 尊重 reduced motion，不覆盖旧结果 |
| Command/Search Entry | 未来跨项目、Chapter、Story、Thread 和设置跳转 | 显示用户对象；内部索引下钻 | combobox/dialog 键盘模型，候选快捷键 Ctrl+K | 无结果、部分索引和后端不可用分开 |
| IntegrationStatus | 配置与实时能力状态 | “已配置/待检查/不可用”；探测详情下钻 | 状态不只靠颜色，测试动作独立 | 各集成独立错误和刷新 |

## 11. Command / Search 架构预留

应预留统一架构，但 V4-A 不实现：

- scope：全局项目切换、当前项目内搜索、页面跳转和少量安全 quick action；
- entities：Project、Chapter、Story、Thread、设置项；Evidence 默认不进入全局结果，除非用户明确切换工程范围；
- shortcut candidate：Ctrl+K，仅为候选，需与 Windows、浏览器和辅助技术冲突测试；
- future data source：现有 Project Memory Gateway 与 History 分页读取边界；禁止前端下载全库自建第二索引；
- command registry：命令必须声明 scope、可用条件、破坏性和授权，不允许任意 shell 命令。

## 12. Accessibility 与 Desktop UX Foundation

- 完整键盘路径、可见 focus、读屏 landmark/heading/status 语义是组件合同。
- 正文对比度至少 4.5:1；非文本 focus/状态不得只依赖颜色。
- pointer target 基线至少 24×24 CSS px，高频主要动作优先更大。
- 非必要动效尊重 prefers-reduced-motion；刷新和 skeleton 不制造持续运动。
- 支持浏览器 zoom 与 Windows 125%/150% 缩放；V4-B 在真实桌面壳 PoC 中验证。
- 建议最小内容视口 900×600 CSS px，最终最小窗口需以壳 PoC 和 150% 缩放实测冻结。
- 长中文、长项目名、路径、hash 和代码片段必须有明确换行、截断、展开或局部滚动规则。
- 主页面只有一个主要纵向滚动；Drawer/Modal 打开后拥有自己的滚动并锁定背景。
- Dialog 打开、错误定位、关闭还焦和破坏性确认遵循统一行为。
- 复制必须给出可感知成功/失败反馈。
- 后台刷新保留内容、展开状态、焦点和滚动，不发生 layout jump。

## 13. 非代码项目 sanity check

| Ground truth | Current State | History / Chapter / Story | Evidence | 结论 |
| --- | --- | --- | --- | --- |
| 软件项目 | 当前可运行能力、质量状态、最近结果 | 版本与能力演进自然 | Commit、CI、文档、Agent Result | Git/Commit 仅在存在时出现 |
| Markdown 研究报告 | 研究范围、已确认结论、未决问题 | 调研阶段、论证变化、报告定稿 | 文档段落、数据源、Agent Result | 不需要“开发日志”或 tech stack |
| PPT / presentation | 当前叙事、页面完成度、评审状态 | 结构、视觉和评审阶段 | 幻灯片、评语、导出文件、Agent Result | Story/Chapter 仍自然 |
| 数据分析 | 数据范围、方法、可复现结果、限制 | 数据清洗、分析、验证和结论演进 | 数据集元数据、notebook、图表、报告 | Git 可选，无 Git 不降级身份 |

四类项目都能回答“现在是什么、怎样到这里、哪些可确认”。因此 IA 不以 Commit、techStack、开发日志或任务数量作为核心入口。History coverage 要如实说明来源不足，但 no-Git 不是错误。

## 14. Route 迁移策略

V4-A 不删除路由。分类描述目标角色，不代表本轮重定向。

| Route / 能力 | 目标分类 | V4 归属与动作 |
| --- | --- | --- |
| /projects | PRIMARY_V4 | 项目库；V4-B 重构为全局入口 |
| /projects/[projectId] | PRIMARY_V4 | 项目当前状态；吸收项目详情的用户层信息 |
| /projects/[projectId]/history | PRIMARY_V4 | 项目历程 Overview；保留现有 preview 作为实现证据 |
| future project agent-context | PRIMARY_V4 | 使用现有 context-package，V4-B 新增 typed client 和页面 |
| future project settings | PRIMARY_V4 | 项目材料和项目级集成；不得与全局设置混淆 |
| /settings | PRIMARY_V4 | 全局 Provider、运行环境、账号和全局集成 |
| Chapter / Story / Thread / Event / Evidence | DEEP_LINK_ONLY | 作为 History、Current State、Agent Context 的下钻 |
| /dashboard | REDIRECT_LATER | 能力被 Current State、项目设置和辅助视图吸收；V4-C 验收后决定重定向 |
| /project-intelligence/capabilities | OPTIONAL_SECONDARY_V4 | Capability Map 仅是适合部分软件项目的增强视图，不进入通用产品主轴 |
| /project-intelligence 及子路由 | SECONDARY_V4 → LEGACY_COMPATIBILITY | 底层理解、能力、事实来源继续存在；用户层由新工作区表达 |
| /timeline | LEGACY_COMPATIBILITY | 旧 Timeline 兼容，不等于 V4 Project History |
| /sediment-review 及详情 | LEGACY_COMPATIBILITY | 旧确认工作流兼容；不回到 ProjectFact 主链 |
| /project-changes 及详情 | SECONDARY_V4 / DEEP_LINK_ONLY | 变化与 Evidence 可由新 History/工程详情吸收 |
| /project-analysis-records | DEEP_LINK_ONLY | 诊断与审计入口 |
| /ai-review | SECONDARY_V4 | 输出/资产能力保留，是否独立入口由后续真实使用证据决定 |
| /tasks | SECONDARY_V4 | 辅助执行管理，不占据核心心智 |
| /dev-logs、/work-sessions | LEGACY_COMPATIBILITY | 软件项目兼容入口；非通用核心 |
| /imports | SECONDARY_V4 | 从项目库的添加/导入流程进入 |
| auth routes | KEEP | 登录模式继续独立，不并入项目导航 |

REMOVE_AFTER_ACCEPTANCE 候选只能在 V4-C 完成能力吸收、deep link 与回归验证后提出。当前没有任何路由获准删除。

## 15. API → View Model 映射

| UI Need | Current API / DTO | 缺口与可派生性 | Backend owner / V4-B、V4-C |
| --- | --- | --- | --- |
| Project Library | GET /api/projects；ProjectResponse；Gateway project list | 不能从 updatedAt 推断真实变化、历史覆盖和注意项 | V4-B 先用现有列表；V4-C 如需 compact 摘要扩展 Gateway，避免 N+1 |
| Current State | GET history/current-state；ProjectCurrentStateResponse | confirmedState 等足够；Chapter/Thread 只有引用，不宜逐条首屏请求 | V4-B 先展示现有字段；V4-C 仅按性能证据补 compact resolved summary |
| History Overview | GET history/overview；HistoryOverviewResponse | 有阶段、变化和 coverage；没有稳定 currentChapterId | 禁止客户端按日期猜；若产品必须显示，在既有 History read model 补 |
| Chapter | GET history/chapters 与 detail | 后端足够，前端缺完整列表 wrapper | V4-B 补 typed client |
| Story | GET history/stories 与 detail | 后端已含 before/change/after、状态、attribution | V4-B 直接消费 |
| Thread | GET history/threads 与 detail | 后端足够，前端缺列表 wrapper | V4-B 补 typed client |
| Raw Event / Evidence | GET events、event detail、event evidence | 后端足够，前端只封装 Evidence detail | V4-B 补 Event client 和 EvidenceDrawer |
| Correction | GET/POST corrections、revert | 后端已有 revision/conflict 保护；现页只覆盖部分 Story 修正 | V4-B 复用，不新增事实链 |
| Refresh | POST history/refresh；durable ProjectAnalysisJob | 后端状态足够；前端缺统一映射和 wrapper | V4-B 接入既有 job 轮询与 RefreshStatus |
| Agent Context | GET project-memory/context-package；AgentContextPackageResponse | 后端有有界、脱敏、revisioned package；前端无类型和页面 | V4-B 增加只读 typed client，禁止前端重组第二份 package |
| Project Memory | legacy memory；Gateway snapshot/recent/capabilities/brief | legacy 可编辑文本不是 V4 主 truth；Gateway 未内嵌 Current State | V4-B 优先 Gateway；V4-C 仅按性能证据组合 |
| GitHub / local source | memory.localProjectPath、agent-bridge/health、github/status | 尚无统一脱敏 compact binding；两个状态包含 live check | V4-B 在设置中分别表达；V4-C 可在既有服务补脱敏摘要 |
| Obsidian | 仅仓库内 CLI validate/dry-run/status/sync | 无 Web REST status，客户端不能可靠派生实时状态；内容页入口只能基于真实已有投影能力 | V4-B 分开配置与上下文使用入口；Owner 批准后 V4-C 才研究受限 status adapter |
| Provider | GET /ai-providers、POST test、bootstrap availability | 全局 CRUD 已有；前端类型漏 credentialStatus；当前没有证据证明项目级 binding 已存在；configured 不等于 available | V4-B 保持全局 ownership，项目页只展示/跳转；项目 binding 仅在真实需求与 backend gap 明确后另行设计 |
| 状态语义 | Overview、Current State、Correction、Job | 字段足够，缺统一前端 View Model | V4-B 建单一状态映射；不改 backend semantic contract |

读取边界：

- History、Current State 和 Project Memory Gateway 的 GET 读取持久化 read model，不运行扫描或模型。
- Agent Context Package 由持久化事实、理解、结构与历史状态确定性组装，retrieval mode 为 persisted-only，modelCalled=false；它不是新 truth。
- Dashboard Bootstrap 也是 read-only 聚合，但不包含 V4 首屏所需的 Current State、History、Agent Context 或 Obsidian 状态。
- GitHub status 会执行本地 GitHub CLI 检查，Agent bridge health 会读取本地材料；UI 不得把它们伪装成纯缓存读取。

## 16. 后端调整优先级

V4-A 不修改 backend semantic contract。

V4-B 可完成的前端工作：

- 补 Agent Context、Chapter/Story/Thread/Event 列表、History refresh 和 Gateway 的 typed client；
- 建立统一 View Model 与状态文案；
- Current State 先使用 confirmedState、recentConfirmedChanges、currentness 和注意字段；
- Current State 以带文字的“更新项目状态”作为核心场景，复用既有 job 轮询并保留旧可信结果；
- Provider 显示“已配置”而不是“可用”，项目设置不复制全局 CRUD。

只有证据成立才进入 V4-C 的候选：

- Current State 的 compact current Chapter / active Thread summary；
- Project Library 的 compact 最近真实变化、覆盖和注意项，避免 N+1；
- 脱敏的项目 source binding summary；
- 经 Owner 批准的 Obsidian 只读 status adapter。

这些调整必须扩展既有 ProjectHistoryReadService、ProjectMemoryGatewayService 或现有 integration service，不新增 Project State、History、Memory、Agent Context 的第二套事实表。

## 17. 低保真 Wireframe

### 17.1 Project Library

    ┌ Rail ─────┬ 项目库 ────────────────────────────────┐
    │ 项目      │ 搜索项目                 [添加项目]    │
    │ 最近项目  ├────────────────────────────────────────┤
    │ 设置      │ 项目名        最近状态      注意       │
    │           │ 研究报告      结论待核查    1 项       │
    │           │ 演示文稿      已完成评审    无         │
    │           │ 软件项目      信息可能过期  2 项       │
    └───────────┴────────────────────────────────────────┘

### 17.2 Project Current State

    ┌ Rail ┬ Project Header / Switcher ──────────────────┐
    │      │ 当前状态 | 项目历程 | Agent 上下文 | 设置   │
    │      ├─────────────────────────────────────────────┤
    │      │ 当前能确认的状态              [更新项目状态]│
    │      │ 一段可读摘要 · 最近成功更新时间             │
    │      ├──────── 最近主要结果 ───────────────────────┤
    │      │ 结果一   结果二   最近变化                   │
    │      ├──────── 当前阶段 / 活跃主题 ────────────────┤
    │      │ 项目阶段摘要     演变主线摘要                 │
    │      └──────── 需要注意：Unknown / Conflict ──────┘

### 17.3 Project History Overview

    ┌ Header / Project Subnav ───────────────────────────┐
    │ 项目历程                         当前性 / 覆盖提示 │
    ├────────────────────────────────────────────────────┤
    │ 时间阶段轴：● 阶段一 ─ ● 阶段二 ─ ◉ 当前阶段     │
    ├────────────────────────────────────────────────────┤
    │ 项目阶段卡：摘要、主要成果、时间、变化记录数      │
    ├────────────────────────────────────────────────────┤
    │ 最近变化                                            │
    ├────────────────────────────────────────────────────┤
    │ 演变主线：跨项目阶段连接相关变化记录               │
    └────────────────────────────────────────────────────┘

### 17.4 Chapter Detail

    ┌ 返回历程  阶段标题                     时间范围    ┐
    │ 阶段摘要 / 主要成果                                │
    ├────────────────────────────────────────────────────┤
    │ Story 一：摘要与当前结果                    [打开] │
    │ Story 二：摘要与 Conflict                  [核查] │
    ├────────────────────────────────────────────────────┤
    │ Supporting / coverage / 修正记录              [展开]│
    └────────────────────────────────────────────────────┘

### 17.5 Story Detail + Engineering Drilldown

    ┌ Story 标题                         [修正展示]      ┐
    │ 此前状态 → 本次变化 → 当前结果                     │
    │ Unknown / Conflict 贴近对应段落                    │
    ├────────────────────────────────────────────────────┤
    │ 相关 Thread                 [查看工程详情]         │
    └───────────────────────────────┬────────────────────┘
                                    │ Evidence Drawer
                                    │ Commit / path
                                    │ full ID / authority
                                    │ currentness / diagnostics
                                    └────────────────────

### 17.6 Agent Context

    ┌ Agent 上下文                  [复制交接内容]        ┐
    │ 最近更新 · 适用范围 · 内容限制                     │
    ├────────────────────────────────────────────────────┤
    │ 当前状态                 已确认的重要事实           │
    │ 最近变化                 未解决冲突 / 未确认项     │
    ├────────────────────────────────────────────────────┤
    │ 建议进一步查看的材料                        [查看]  │
    │ 工程详情：package revision / Evidence refs   [展开] │
    └────────────────────────────────────────────────────┘

### 17.7 Project Settings / Integrations

    ┌ 项目设置与集成 ───────────────────────────────────┐
    │ 本地材料        已连接               [查看详情]    │
    │ Git / GitHub    未配置 / 待检查       [设置]       │
    │ Agent bridge    可读取 / 部分不可用   [诊断]       │
    │ Obsidian        投影与 Vault 配置      [设置]       │
    │ Provider        使用全局配置 / 待检查  [前往设置]  │
    └────────────────────────────────────────────────────┘

当已有真实投影能力时，Current State、History、Chapter 与 Story 可在内容上下文提供 Obsidian Note 入口；该入口不伪造实时同步状态。

## 18. V4-B 核心 prototype 与桌面 PoC 范围

V4-B 优先用四个真实核心 prototype 驱动视觉与组件基础：Project Library、Current State、Project History、Story Detail + Evidence Drawer；必要时再增加小型 Agent Context 样板。优先形成满足这些页面的最小组件集合，例如 AppFrame、ProjectSwitcher、ProjectSubNavigation、Button、StatusNotice、CurrentStateSummary、ChapterCard / Timeline、StoryCard / StateTransition、EvidenceDrawer、短标识/复制标识与 Loading/Empty primitives，具体数量不写死。

Semantic component inventory 不得机械转换成一次性 implementation checklist。只有真实页面需要时才增加新 primitive 或业务组件。

DESKTOP_SHELL_DECISION = DEFERRED。可删除 PoC 优先只比较 Electron 与 Tauri：在同一 Windows 环境、硬件、V3.10 runtime、Java Core、Next UI、transport security、页面、测试样本与测量方法下，对比冷启动、内存、CPU、包体、退出、孤儿进程、Java sidecar、125%/150% 缩放、键盘/焦点、titlebar、文件选择、拖放、原生菜单、安全、未来 installer/updater 边界和 CI 复杂度。WebView2 direct host 仅保留研究对照，不同时实现第三套完整 host。PoC PASS 不等于生产 shell 已选择。

全局 Needs Attention 暂不提升为一级导航。V4-B 可先在 Project Library 展示注意计数和“需要注意”筛选，再根据真实多项目使用决定是否需要 Attention Center。

## 19. Owner Review 与下一阶段边界

Owner 已认可：

1. Hybrid 项目中心与 Current State 默认第一屏；
2. Current State、History、Agent Context 的核心层级；
3. Evidence 按需下钻与旧入口渐进降级；
4. ProjectFlow semantic token、source-owned component 与成熟 headless primitive；
5. DESKTOP_SHELL_DECISION = DEFERRED。

本次 Owner Review 已据反馈修正 Capability 可选层级、Chapter / Thread 双轴、Agent Context 第一层产品化、Obsidian 配置与上下文入口、Current State 显式更新闭环、Onboarding 默认数据位置、全局/项目 Provider ownership、用户层命名、V4-B 页面驱动组件范围和 Electron/Tauri 双 PoC 优先级。Owner 仍需最终确认修订后的 V4.0-A；用户导航与 Chapter/Story 的最终用词留给 V4-B prototype 验证。

Owner 批准后，V4-B 直接基于本文探索视觉方向、真实 token 和核心 primitive 原型，不重新讨论页面集合。V4-A 不实现最终 GUI、不删除旧路由、不选择生产桌面壳、不创建 Tag/Release，也不自动进入 V4-B。

V4.0-A GUI FOUNDATION = READY_FOR_OWNER_REVIEW_UPDATED
