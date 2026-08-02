# ProjectFlow V3.8.0 开源项目历史与可读变化研究

研究日期：2026-08-02

## 研究方法

本研究独立核对官方文档、公开仓库源码、许可证、最近提交和高信号 Issue。部分产品官网页面被浏览器安全策略阻止后，没有重试或绕过，改用官方文档仓库、官方源码镜像和公开 Issue。

研究目标不是寻找一个可整包搬入 ProjectFlow 的历史组件，而是回答：

- 用户第一眼应看到什么。
- 大量事件如何折叠、筛选和下钻。
- 如何保留原始事件同时避免信息过载。
- 新增、删除、恢复、撤销和重做如何表达。
- 发布说明聚合与完整项目历程有什么边界差异。
- 哪些实现可复用，哪些只能借鉴产品模式。

## 总体结论

1. 成熟产品普遍采用“概览或上下文 → 分组活动 → 原始事件 → diff/详情”的渐进式下钻。
2. GitHub 和 GitLab 证明来源、用户、时间和活动类型筛选是基础能力；GitLab 的 bulk push 证明必须在不删除原始来源的前提下压缩刷屏事件。
3. GitButler 的快照与 Undo Timeline 证明删除、恢复和重做不能压平成一条线，但其讨论也直接暴露了相似快照刷屏、恢复点语义不清和“恢复不是撤销”的理解成本。
4. Gource 的时间缩放适合视觉概览，不适合承担 ProjectFlow 的主要信息架构和证据阅读。
5. OpenProject 把 Activity 放在工作包上下文内，并为长活动使用 paginator；ProjectFlow 应借鉴上下文和分页，不应变成任务管理器。
6. release-please、Changesets、git-cliff 和 Changie 证明多 Commit 聚合成可读说明有效，但它们依赖提交约定、人工 changeset 或发布边界，不能代替全生命周期证据重建。
7. Commit message 只能是候选 Evidence。PR 正文、Issue、文件状态、测试、Agent Result 和 ProjectFact 必须互补；冲突不能由模型静默裁决。
8. 没有研究对象能直接满足 ProjectFlow 的强事实、任意项目类型、Evidence 追踪和失败保留要求。本阶段不复制第三方代码，也不新增依赖。

## 研究矩阵 A：产品与信息层次

| 对象 | 官方来源 | License | 维护状态 | 核心用户问题 | 默认第一层信息 | 时间分组 | 折叠、下钻与筛选 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GitHub Activity View | github/docs：Using the activity view | GitHub 产品非开源；文档仓库 CC-BY-4.0 | 官方文档持续维护 | 看清 push、merge、force-push、分支创建和删除 | 谁在何时对哪个分支做了什么 | 按活动发生时间 | 按分支、用户、时间、活动类型筛选；Compare changes 下钻 |
| GitLab CE Activity / Events | GitLab 官方源码与 push_event_activities_limit 文档 | CE 为 MIT Expat；文档与商标另有规则 | 活跃 | 大 push 不应刷满活动流 | 单个 push/merge/branch 事件或 bulk push 摘要 | 事件时间 | 默认超过 3 个 branch/tag ref 时合并为一个 bulk push；API 保留 ref_count，但单 ref 细节为空 |
| Gitea | go-gitea/gitea | MIT | 活跃，2026-08-02 有提交 | 自托管仓库中浏览 Commit、Graph、Compare、Release | Commit、分支或 Release 原始信息 | Commit 时间和 ref | 分页、分支选择、compare 和单 Commit 下钻 |
| GitButler | gitbutlerapp/gitbutler | FSL-1.1-MIT | 活跃，2026-08-01 有提交 | 在复杂 Git 操作中理解当前 stack、操作历史和可恢复点 | 可操作 stack、snapshot、operation | 操作时间和 snapshot | Undo Timeline、snapshot diff、恢复点；可灰显或折叠不在当前状态的区间 |
| Gource | acaudwell/Gource | GPL-3.0 | 维护中，2026-03-06 有提交 | 快速看到仓库随时间增长和人员/文件活动 | 动态可视化时间轴 | 连续时间播放 | 缩放、跳转、跟随用户或文件；详情阅读能力弱 |
| OpenProject | opf/openproject | GPL-3.0 | 活跃，2026-08-01 有提交 | 在工作包上下文中查看状态、评论和关联活动 | 当前工作包及其 Activity | 活动时间 | 类型/上下文过滤、分页；源码存在 activities paginator |
| release-please | googleapis/release-please | Apache-2.0 | 活跃，2026-07-31 有提交 | 从约定提交生成可审阅 Release PR 和 changelog | 待发布版本和按类型分组的变更 | 两个 release 边界之间 | Release PR 下钻到 PR/Commit；用户可编辑 Release PR |
| Changesets | changesets/changesets | MIT | 活跃，2026-07-31 有提交 | 在 Monorepo 中显式声明包、版本影响和用户可读摘要 | 待发布 changeset 和受影响包 | release/batch | changeset 文件聚合，人工选择包和 bump；PR 中可修正 |
| git-cliff | orhun/git-cliff | MIT 或 Apache-2.0 | 活跃，2026-07-27 有提交 | 高度可配置地从 Git 生成 changelog | 按配置分组的提交说明 | Tag/release range | parser、filter、group、template；可链接 Commit/PR |
| Changie | miniscruff/changie | MIT | 活跃，2026-07-20 有提交 | 让每次变化先形成独立 fragment，再批量发布 | 变化 fragment 的 kind、component、body | batch/version | fragment 聚合为版本 changelog；人工编辑 fragment |
| git-chglog | git-chglog/git-chglog | MIT | 仓库已 archived；最近提交 2025-03-28 | 用模板和提交约定生成 changelog | 按类型和版本分组的 Commit | Tag/release range | 配置模板和筛选；归档状态不适合作新依赖 |

## 研究矩阵 B：反馈、关联、可借鉴与拒绝

| 对象 | 事件关联和深链接 | 高信号正面模式 | 高信号抱怨或限制 | ProjectFlow 可借鉴 | ProjectFlow 不应照搬 | 代码/依赖复用与风险 |
| --- | --- | --- | --- | --- | --- | --- |
| GitHub Activity View | 活动关联 commit、用户、branch；Compare changes 深链 | 四类筛选直接、原始变化可精确下钻 | 仍是原始 Git 活动流，不能生成跨 Commit before/after 故事 | 来源、时间、状态筛选；每项都有原始 compare | 不把 SHA、文件统计放在第一层 | 只借鉴交互；不复制 GitHub 产品代码 |
| GitLab CE | push、merge、branch、ref_count 和 Events API | bulk push 明确以性能和防 spam 为目标 | bulk event 会丢失单 ref 详情，外部系统若要全量必须拆 push | 聚合展示与原始事件库存分离；聚合不能删除原始记录 | 不用聚合摘要代替来源事件 | MIT 源码可读，但本阶段无必要复制；现有 Java 边界可实现 |
| Gitea | Commit、branch、compare、release URL | 熟悉、直接、低学习成本 | 仍以 Git 对象为中心，不覆盖非 Git 项目 | 原始层分页、compare 深链 | 不做第二个 Git Web UI | MIT，但整合会把产品带向 Git 客户端；不复用 |
| GitButler | snapshot、operation、restore_from、snapshot diff | Issue #3726 强调 glance-able、可恢复 checkpoint 和完整状态 | 同一类 snapshot 会刷屏；restore 是 time travel 不是简单 undo；恢复前后点容易误读；大 Monorepo 有性能反馈 | 显式 REMOVED、RESTORED、REVERTED、REAPPLIED；保留被跳过区间并可展开 | 不实现工作区写操作、branch stack 或 snapshot 恢复 | FSL-1.1-MIT 有非竞争限制；只研究模式，零代码复制 |
| Gource | 用户、文件、目录和时间形成视觉关系 | 时间缩放和活动密度一眼可见 | 视觉演示难以精确阅读原因、冲突和 Evidence；可访问性不足 | 活动密度可作为篇章边界候选 | 不以动画图代替读模型 | GPL-3.0；不复用 |
| OpenProject | Activity 绑定工作包和 GitHub integration | 活动始终带业务上下文 | 长历史必须分页；完整模式会把 ProjectFlow 带向任务和团队管理 | 故事必须带项目主体和上下文；读取要分页 | 不做看板、工时、人员和排期 | GPL-3.0；只借鉴分页和上下文 |
| release-please | Conventional Commit、PR number、Release PR | 自动聚合后仍保留可审阅 PR | 不规范 Commit、squash 和非发布历史会降低质量 | 机器候选 + 人可修正；引用 PR/Issue | 不把 release 当全生命周期篇章 | Apache-2.0，但无必要引入 Node 依赖 |
| Changesets | changeset 显式绑定 package、bump 和摘要 | Issue #862 指出显式 package 关联正是 Conventional Commit 缺失的信息 | 要求贡献者提前写 fragment，自动化和 adoption 有成本 | 优先显式用户/PR 声明，保留人工语义 | 不要求所有项目采用 changeset 流程 | MIT；模式可借鉴，格式不作为核心依赖 |
| git-cliff | Commit parser、group、template、remote links | 可配置性强，适合多仓库 release note | 配置复杂，仍受 Commit message 和约定质量限制 | 噪声过滤、确定性 grouping、模板化输出 | 不让正则分类成为语义事实 | 双许可友好，但引入 Rust binary 无必要 |
| Changie | fragment 绑定 kind/component/body | 变化在发生时记录，比事后猜测准确 | 增加贡献流程，缺 fragment 的旧历史仍无法恢复 | Agent Result/PR 明确声明可提升 reason Evidence | 不强制改造外部项目流程 | MIT；不引入 CLI |
| git-chglog | Tag、Commit、模板链接 | 简单、成熟的 release-range 模式 | 已归档，仍依赖规范 Commit | 仅作为 release 级对照样本 | 不作新依赖或历史引擎 | MIT 但维护结束，拒绝依赖 |

## 关键研究证据

### GitHub Activity

官方文档明确说明 Activity View 展示 pushes、merges、force pushes 和 branch changes，并可按 branch、user、time 和 activity type 筛选。每项可以通过 Compare changes 查看精确变化。

ProjectFlow 采用相同的“筛选 + 精确下钻”原则，但第一层改为变化故事，不直接复制原始 Git 活动流。

### GitLab bulk push

GitLab 官方文档说明，默认 push_event_activities_limit 为 3。一次 push 影响超过 3 个 branch/tag ref 时，Activity Feed 只显示一条 bulk push。Events API 返回 commit_count=0 和 ref_count，单 ref 的 commit_from、commit_to、ref、commit_title 为空。

这证明“展示压缩”和“原始事件保存”必须分层。ProjectFlow 可以折叠同一次操作产生的重复事件，但自己的原始事件层不能像 bulk API 一样失去每个来源事件。

### GitButler Undo Timeline

GitButler Issue #3726 把 snapshot 定义为可恢复完整项目状态，并讨论 restore_from、灰显不在当前状态的区间和 snapshot diff。讨论同时列出相似 snapshot spam、恢复到操作前还是操作后不清楚、恢复动作自身又产生新 snapshot 等 UX 难点。

ProjectFlow 只借鉴“显式操作类型 + 当前状态路径 + 被跳过历史仍可展开”，不复制恢复功能，也不把 ProjectFlow 做成 Git 工作区客户端。

### Release 说明工具

release-please、git-cliff 和 git-chglog 的共同前提是可用的 Commit/PR 约定和明确 release range。Changesets 和 Changie通过人工 fragment 补足 Commit message 的语义不足。

ProjectFlow 面对的是旧历史、非代码项目和不规范材料，因此必须：

- 把 Commit message 视为候选而非真相。
- 优先使用 PR、Issue、文档、Agent Result、测试和文件状态的互证。
- 原因无明确 Evidence 时保持 UNKNOWN。
- 允许用户未来修正派生故事，但不改写 ProjectFact 或原始事件。

## 最终采用的产品模式

- 总览、篇章、故事、演变链、原始事件、Evidence 六层下钻。
- 来源、时间、权威、主体、冲突和 unknown 筛选。
- 展示层聚合与原始事件库存分离。
- 大 push、大 merge 和格式化噪声折叠，但可展开。
- 删除、恢复、撤销和重做作为显式 transition。
- 模型只解释有界候选和已知 ID，工程层负责拓扑、时间、路径、hash 和引用。
- 用户第一层看到动作、对象和结果；SHA、文件、行数和 Evidence ID 只在详情出现。

## 明确拒绝的模式

- 以 Commit 列表或文件树作为主界面。
- 固定按月切割所有历史。
- 用动态图替代可读文本和 Evidence。
- 把 release notes 当作项目全生命周期。
- 要求所有外部项目采用 Conventional Commits 或 Changesets。
- 把 restore/undo 写操作引入 ProjectFlow。
- 复制 FSL、GPL 产品实现。
- 引入新的 Git 引擎、parser、图数据库、向量库或工作流引擎。

## 主要来源

- https://docs.github.com/repositories/viewing-activity-and-data-for-your-repository/using-the-activity-view-to-see-changes-to-a-repository
- https://github.com/gitlabhq/gitlabhq/blob/master/doc/administration/settings/push_event_activities_limit.md
- https://github.com/gitlabhq/gitlabhq/blob/master/app/services/bulk_push_event_payload_service.rb
- https://github.com/go-gitea/gitea
- https://github.com/gitbutlerapp/gitbutler
- https://github.com/gitbutlerapp/gitbutler/issues/3726
- https://github.com/gitbutlerapp/gitbutler/issues/3235
- https://github.com/acaudwell/Gource
- https://github.com/opf/openproject
- https://github.com/opf/openproject/blob/dev/app/services/work_packages/activities_tab/paginator.rb
- https://github.com/googleapis/release-please
- https://github.com/changesets/changesets
- https://github.com/changesets/changesets/issues/862
- https://github.com/changesets/changesets/issues/647
- https://github.com/orhun/git-cliff
- https://github.com/miniscruff/changie
- https://github.com/git-chglog/git-chglog
