# ProjectFlow V3.5 Open-Source Borrowing Map

研究日期：2026-07-23

研究原则：优先复用协议、标准、现有进程能力和成熟外部工具；受限许可证只研究思想；不为 V3.5 自研语言 Parser、跨语言 Symbol Protocol、Git 实现或 Agent Session Recorder。

## 决策表

| 项目/技术 | 它解决的问题 | 成熟做法 | ProjectFlow 对应问题 | 直接复用? | 参考思想? | Adapter? | License | 风险 | 决策 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| JDK NIO + 现有 FixedCommandExecutor + Git CLI | 跨平台文件遍历、受控外部命令、Git 精确信息 | FileVisitor、超时、固定参数、有限输出 | Intake、hash、Git HEAD/状态/提交数 | 是 | 是 | 否 | JDK / GPLv2+Classpath；Git GPLv2 | 目录权限、超大目录、symlink | DIRECT_REUSE |
| scc | 快速语言识别、LOC、二进制和代码统计 | 单二进制、JSON 输出、Windows/Linux/macOS、被 CodeQL/Qodana 等采用 | 比手写行计数更准确的可选结构指标 | 系统存在时直接调用 | 是 | 是 | MIT | 当前机器未安装；不能作为正确性前置条件 | ADAPTER_INTEGRATION |
| GitHub Linguist | 语言、generated、vendored、binary 分类 | GitHub.com 生产使用、可配置属性 | 更完整的语言与噪声识别 | 否 | 是 | 可选 | MIT | Ruby/native 依赖和 Desktop 打包成本高 | REFERENCE_ONLY |
| Aider Repo Map | 大仓库压缩和模型上下文选择 | Tree-sitter tags、引用图、PageRank、mtime/cache、map token budget | 不逐文件调用模型，只提交高价值结构 | 不复制 Python 实现 | 是 | 否 | Apache-2.0 | Python/runtime 与现有 Java Core 不一致；PageRank 需可靠 symbol graph | PATTERN_REUSE |
| Tree-sitter | 多语言、容错、增量语法树 | Grammar + query/tag、增量 parse、广泛编辑器采用 | symbols、entry points、relations | V3.5 不直接嵌入 | 是 | 是 | MIT | 官方 Java 0.26.x 要求 JRE 23，ProjectFlow 当前 Java 17；grammar/native 打包成本 | ADAPTER_INTEGRATION |
| SCIP | 跨语言 definition/reference/occurrence 协议 | Protobuf schema，多语言 indexer，独立生产与消费 index | 避免自创 Symbol/Reference 模型 | 本阶段不运行 indexer | 是 | 是 | Apache-2.0 | 各语言 indexer 安装、构建语义和大 index 体积 | ADAPTER_INTEGRATION |
| GitNexus | 本地代码知识图、cluster、execution flow、MCP | Tree-sitter → 关系图 → cluster/flow → 按需查询 | 不依赖目录名的功能区域和图优先理解 | 否 | 是 | 否 | PolyForm Noncommercial 1.0.0 | 商业/竞品使用受限，不能复制或捆绑 | REFERENCE_ONLY |
| RepoAgent | AST 结构上的分层仓库解释与文档 | object hierarchy、调用关系、增量文档 | 结构先于 LLM、层级综合 | 否 | 是 | 否 | Apache-2.0 | Python、偏文档生成、逐对象模型策略不适合 Token 目标 | PATTERN_REUSE |
| Entire CLI | Agent session、checkpoint、commit 关联 | hooks、独立 checkpoint branch、worktree 和并发会话支持 | 增强未来开发意图证据 | 不内置 recorder | 是 | 是 | MIT | transcript 隐私和 secret redaction 只能 best-effort | ADAPTER_INTEGRATION |
| Rekal | append-only 意图账本与本地可重建索引 | data.db truth / index.db intelligence、Git orphan transport、search-first/drill-down/silence gate | 事实与可重建结构智能分离、渐进上下文 | 否 | 是 | 未来证据 Adapter | Apache-2.0 | 当前 README 要求 macOS/Linux，Windows 不是可靠前置能力 | PATTERN_REUSE |
| PROJECTMEM | 事件溯源、stale、supersede、watcher | append-only typed events、当前解释与历史分离 | 历史事实不能因 currentness 被覆盖 | 否 | 是 | 否 | MIT | 项目年轻；“Memory + Judgment”定位不适合照搬 | PATTERN_REUSE |
| GitButler | Desktop GUI/CLI 共用 Core | Tauri shell，Rust Core 同时供 GUI 和 CLI 使用 | 未来 GUI 不能成为业务核心 | 否 | 是 | 否 | Fair Source，延时转 MIT | non-compete 限制，不复制代码 | REFERENCE_ONLY |
| Tauri | 系统 WebView、小体积 shell、native capability | Rust core、command/IPC、sidecar、系统 WebView | 未来 Desktop shell | 否 | 是 | 未来 sidecar | Apache-2.0 / MIT | Java/JRE sidecar 生命周期、更新、签名、localhost 安全仍需 PoC | REFERENCE_ONLY |
| Electron | Chromium/Node 桌面进程模型 | main/renderer/preload、contextIsolation、sandbox、IPC | 复用 React/Next.js 的另一条 Desktop 路径 | 否 | 是 | 未来 sidecar | MIT | 包体和内存较大，安全边界要求严格 | REFERENCE_ONLY |

## 关键研究证据

1. Aider 的 RepoMap 使用 Tree-sitter tags、缓存和 PageRank，并显式设置 map token budget：
   https://github.com/Aider-AI/aider
   https://github.com/Aider-AI/aider/blob/main/aider/repomap.py
2. Tree-sitter 是增量 Parser；官方 Java binding 当前主线要求 JDK/JRE 23，仓库同时列出 JDK 17+ 的 Kotlin binding 等替代：
   https://github.com/tree-sitter/tree-sitter
   https://github.com/tree-sitter/java-tree-sitter
3. SCIP 提供 language-agnostic Protobuf，并已有 Java、TypeScript、Python、C/C++、Rust、.NET 等 indexer：
   https://github.com/sourcegraph/scip
4. GitNexus 使用本地 Tree-sitter/知识图/cluster/flow，但当前代码许可证是 PolyForm Noncommercial 1.0.0：
   https://github.com/nxpatterns/gitnexus
   https://github.com/nxpatterns/gitnexus/blob/main/LICENSE
5. RepoAgent 使用 AST 和全局结构生成仓库文档，许可证 Apache-2.0：
   https://github.com/OpenBMB/RepoAgent
6. Entire 将 Agent session/checkpoint 放在独立 Git branch，并支持多 Agent/worktree，许可证 MIT：
   https://github.com/entireio/cli
7. Rekal 将 append-only data.db 与可重建 index.db 分离，并用 search-first/drill-down 控制上下文：
   https://github.com/rekal-dev/rekal-cli
8. PROJECTMEM 使用 append-only event log、stale 和 supersede 语义，许可证 MIT：
   https://github.com/riponcm/projectmem
9. GitButler 的 Desktop GUI 与 CLI 使用同一 Rust backend，但许可证为带 non-compete 的 Fair Source：
   https://github.com/gitbutlerapp/gitbutler
10. Tauri 与 Electron 官方架构分别强调系统 WebView/sidecar 和 main-renderer/preload 隔离：
    https://github.com/tauri-apps/tauri/blob/dev/ARCHITECTURE.md
    https://www.electronjs.org/docs/latest/tutorial/process-model
    https://www.electronjs.org/docs/latest/tutorial/security
11. scc 是 MIT 单二进制代码统计器，提供 JSON 和跨平台预编译版本：
    https://github.com/boyter/scc

## 取消自研的轮子

1. 不自研多语言 Parser：只定义 ProjectStructureIndexer，Tree-sitter 以后通过兼容 Java binding 或 sidecar 接入。
2. 不自创跨语言 Symbol/Occurrence 协议：未来优先消费 SCIP。
3. 不自研 Git：继续使用固定参数的 Git CLI。
4. 不自研完整 Agent recorder：未来消费 Entire/Rekal/Agent 原生结果。
5. 不把每个文件交给 LLM：使用结构压缩、硬预算和一次顶层语义调用。
6. 不复制 GitNexus/GitButler 代码：许可证决定仅研究架构。
7. 不引入第二套 Job、重试、取消或模型 HTTP 层：复用 ProjectFlow V3.4.5。

## V3.5 自己实现的不可替代部分

ProjectFlow 自己只实现“目录证据、结构来源、既有事实资产与 Model Gateway 之间的可信编排”：

1. 统一 Intake 和输入分类。
2. 可替换 ProjectStructureIndex read model。
3. 依据目录规模、Git、结构覆盖率和 Provider 可用性生成 Adaptive Analysis Plan。
4. 把 Observed、Inferred、Explained、coverage、confidence、unknowns 和 currentness 组合成 ProjectUnderstandingSnapshot。
5. 保证无模型仍有确定性结果、失败不覆盖旧快照、无变化尽量零模型调用。
