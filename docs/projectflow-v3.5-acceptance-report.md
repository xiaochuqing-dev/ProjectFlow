# ProjectFlow V3.5 Acceptance Report

验收日期：2026-07-23

验收基线：master，HEAD 6e408c2146a4a41c2e47e9a47654c280c93cb2c1，包含未提交的 V3.5 工作树。未创建提交，未运行远程 CI。

## A. 最终解决的问题

V3.5 新增了从任意已绑定本地目录开始的通用项目理解闭环：

本地目录 → 有界 Intake → 输入/规模分类 → 可替换结构索引 → 自适应计划 → 可选一次有界模型综合 → 证据校验 → 持久化当前理解 → API 与信任校准页面。

它不要求 Git 或模型才能给出确定性结果，不把模型推断写成 ProjectFact，也不让 GET 请求扫描文件或调用模型。

## B. 不同输入的处理

| 输入 | 实际策略 |
| --- | --- |
| 空目录 | 识别 EMPTY，保存“无可分析内容”，0 模型请求 |
| 非代码目录 | 识别 UNKNOWN_NON_CODE，只报告库存、manifest 和未知边界，0 模型请求 |
| 无 Git 代码项目 | 建立 current structure/understanding，明确 historicalMode=UNAVAILABLE_NO_GIT |
| 小项目 | 完整有界结构摘要；有 Provider 时最多一个顶层语义阶段 |
| 中项目 | 模块/manifest/入口聚合后再做语义阶段，不逐文件调用模型 |
| 大项目 | hierarchical 计划，只保留高价值结构证据，固定文件/证据/Prompt/Job 预算 |
| Monorepo | 读取 workspace/module 声明和顶层聚合，保留多个模块，不揉成单个项目 |
| 无变化重跑 | 快速库存 fingerprint 命中后复用当前快照，0 模型请求 |
| 有变化重跑 | 记录 added/modified/removed/unchanged dirty set；首版 provider 确定性重建有界摘要 |

## C. 直接复用的成熟能力

1. JDK NIO FileVisitor：有界目录遍历和不跟随 symlink。
2. 现有 FixedCommandExecutor 与 Git CLI：HEAD、branch、commit count、worktree、submodule。
3. 现有 Durable Job：持久化、活动任务复用、取消、retry、恢复、时间/请求/token 预算。
4. 现有 Model Gateway V2：Provider 协议、官方 SDK、重试、结构化恢复和脱敏。
5. 可选 scc Adapter：机器已有 scc 时消费 JSON；未安装时使用内建确定性降级，不增加全局依赖。

本阶段没有捆绑新的第三方代码，因此不新增 NOTICE；前端现有依赖升级到 Next.js 16.2.11、Playwright 1.55.1 和 PostCSS 8.5.10。

## D. 借鉴但未复制的成熟方案

Aider 提供结构压缩、图排序和 token budget 模式；Tree-sitter 提供容错增量语法树方向；SCIP 提供跨语言 definition/reference 协议；GitNexus、RepoAgent 提供结构先于语义的分层模式；Rekal、PROJECTMEM 提供事实与可重建索引分离、stale/supersede 思路；Entire 提供未来 Agent session Adapter 方向；GitButler、Tauri、Electron 用于 Core/Desktop 边界判断。

GitNexus 和 GitButler 因许可证限制仅作参考，没有复制代码。

## E. 取消自研的轮子与节省成本

1. 取消自研多语言 Parser，避免 grammar、错误恢复、native 打包和长期语言维护成本。
2. 取消自创跨语言 Symbol Protocol，未来优先消费 SCIP。
3. 取消自研 Git 实现，继续使用固定参数 Git CLI。
4. 取消完整 Agent recorder，未来通过 Adapter 消费成熟会话/结果。
5. 取消逐文件 LLM 摘要，避免请求数随文件数线性增长。
6. 取消第二套 Job、重试、取消和模型 HTTP 层，复用 V3.4.5。
7. 取消为了 Desktop 重写 Java Core，避免事实、Provider、任务和数据库迁移风险。

## F. ProjectFlow 自己新增的独特能力

新增价值是把目录证据、可替换结构来源、既有事实资产和 Model Gateway 组合成可信编排：

1. Repository Intake、输入分类与规模策略。
2. ProjectStructureIndexer SPI 和 MANIFEST_FILESYSTEM provider。
3. content hash、库存缓存、dirty set 和结构覆盖率。
4. Adaptive Analysis Plan。
5. Observed/Inferred/Explained、coverage、unknowns、CURRENT/STALE 统一快照。
6. 失败保护、所有权校验、持久化 Job/API 和信任校准页面。

## G. LLM 使用边界

LLM 只在代码项目、Provider 可用且库存变化时，接收压缩后的相对路径、manifest、模块、入口、工程信号和证据编号，执行最多一个顶层语义阶段。

文件计数、LOC、语言、Git、hash、dirty set、module/workspace、coverage、时间排序和 evidence linking 不使用 LLM。空目录、非代码、无模型和无变化重跑均为 0 模型请求。

## H. Token 与资源上限

默认扫描最多 250,000 个文件；结构详情最多 5,000 个文件；单文件读取最多 8 MiB；总读取最多 512 MiB；结构 evidence 最多 700 条；模型压缩 Prompt 最多 48,000 字符。

项目理解 Job 上限为 3 次网关请求、40,000 总 token、10 分钟。正常业务计划最多一次语义请求；额外次数只留给网关受控结构化恢复。所有阶段支持既有取消检查。

## I. 证据、覆盖率和质量

工程工具直接证明的 claim 标为 OBSERVED；模型综合标为 INFERRED；V3.5 不自动生成因果型 EXPLAINED。未知 evidence ID 会被过滤，不能替换确定性段落。

Snapshot 显示 intakeCoverage、structureCoverage、observed/inferred/evidence-bound claim 数、unsupported areas、unknowns、semantic status、cache hit、分析时间和 CURRENT/STALE。没有制造无依据健康分。

## J. 与长期事实模型的关系

ProjectUnderstandingSnapshot 表示“当前项目是什么”，允许替换。

ProjectFact 继续表示真实发生过的重要工程事实；Timeline 是事实的时间派生层；ProjectCapability 是事实证明的长期能力；Evolution 表示长期实体变化。V3.5 不迁移、删除或改写历史事实，无 Git 时不编造历史。

## K. Desktop 准备

Intake、结构索引和 Understanding 均位于 Java Service，Controller 只是 delivery adapter；持久化 Job 可在浏览器关闭时运行；Web、未来 Desktop、CLI、Hermes 和后台引擎可消费同一 Core/API。

V3.5 故意没有实现 Tauri/Electron shell、JRE sidecar 打包、IPC、安装器、托盘、自动更新或后台 daemon。两条 Desktop 路径需经过 Windows 打包与安全 PoC 后再选择。

## L. 已知限制与风险

1. MANIFEST_FILESYSTEM 只提供 manifest、文件和模块级结构；没有 AST、symbol、import/call/reference graph，相关 coverage 明确为 0 或 unsupported。
2. dirty set 已落地，但当前 provider 在变化时仍重建有界结构摘要；细粒度解析缓存等待 Tree-sitter/SCIP Adapter。
3. content hash 使用相对路径、大小和 mtime，适合快速复用，但极端的同大小同时间内容替换可能漏检；后续可对 dirty candidate 增加内容摘要。
4. Windows 上的真实大型仓库扫描时间受磁盘缓存、Defender 和 partial-clone blob hydration 影响明显。
5. scc 未安装，因此本次真实基准使用 BUILTIN_EXTENSION_SCAN。
6. npm audit 在最新稳定 Next.js 16.2.11 内部固定的 PostCSS 8.4.31 和 Sharp 0.34.5 上仍报告 1 个 moderate、2 个 high 上游告警；没有兼容的 Next.js 稳定升级，未强制覆盖框架内部依赖。
7. 数据库仍依赖 Hibernate ddl-auto update；PostgreSQL 容器验收因本机 Docker daemon 不可用而跳过。
8. 没有安全真实 Provider Key，因此真实 Provider 质量验收跳过；固定模型只验证协议和证据绑定。

## M. 实际验收结果

| 层级 | 命令/场景 | 结果 |
| --- | --- | --- |
| Backend 全量 | backend: mvn test | 319 tests，0 failures，0 errors，1 skipped；跳过项是需显式真实仓库路径的 benchmark |
| Backend V3.5 聚焦 | RepositoryIntakeServiceTest + ProjectUnderstandingServiceTest | 9 tests 通过，覆盖 EMPTY、NON_CODE、无 Git、Monorepo、规模阈值、无模型、缓存、dirty set、模型失败保护、所有权和无效证据 |
| Frontend 类型 | frontend: npm run lint | 通过 |
| Frontend contracts | frontend: npm run test:contracts | 47/47 通过 |
| Frontend production | frontend: npm run build | Next.js 16.2.11 构建成功，项目理解路由已生成 |
| E2E | frontend: npm run test:e2e | Chromium 8/8 通过；含持久化项目理解、页面刷新恢复、固定模型证据绑定和旧 Fact/Timeline/Capability 主流程 |
| H2/启动 | Start-ProjectFlow.bat -NoBrowser | 当前脏工作树完成 npm ci、生产构建、H2 旧数据升级、前后端健康检查；证据写入 logs/last-embedded-build.json |
| 大仓库 | Spring Framework 浅克隆 checkout | 11,314 文件，9,659 源文件，1,536,497 LOC，32 模块，214 evidence，coverage 0.799，未截断 |
| 大仓库时间 | intake + manifest index | 观测到 9.417 秒和 74.960 秒；快速重复 fingerprint 为 233–260 ms，表明 Windows 文件缓存/安全扫描存在高方差 |
| 大仓库模型成本 | benchmark | 0 requests，0 input tokens，0 output tokens；benchmark 只验证确定性结构层 |
| PostgreSQL 16 | Testcontainers | SKIPPED：Docker Desktop daemon 不可用 |
| 真实 Provider | OpenAI/Anthropic/Relay | SKIPPED：没有读取或使用安全 Key |
| 固定模型 | Playwright local fixed model | 通过；仅作为自动化契约，不描述为真实 Provider 验收 |
| npm audit | npm audit --json | 直接 Playwright 与 Next.js 本体告警已通过升级消除；仍有 3 个 Next.js 内部依赖上游告警 |

真实仓库是浅克隆，Windows checkout 有 2 个超长路径未落盘；因此文件数不是上游仓库的跨平台绝对总数。首次 partial-clone 网络 hydration 约 96 秒，不计为稳定本地扫描时间。

## N. 版本与 CI

分支：master

基线 HEAD：6e408c2146a4a41c2e47e9a47654c280c93cb2c1

工作树：包含 V3.5 未提交变更和用户原有启动可靠性改动。

提交：未创建，用户未要求提交。

远程 CI：未运行。
