# ProjectFlow V3.7 Open Source Borrowing Map

调研日期：2026-07-24。活跃度来自当日 GitHub repository metadata；许可证以仓库 LICENSE 和官方条款为准。本阶段没有复制候选项目代码，也没有新增依赖。

| 项目 | 分类 | 重合层 / 采用点 | License / 活跃度 | Runtime、Windows、Java 17、Desktop 影响 | Token / 大仓库 / 本地优先 | Producer、setup、daemon | 决策 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Sourcegraph SCIP / scip-java / scip-typescript | DIRECT_REUSE + ADAPTER_INTEGRATION | 继续复用 SCIP 协议、官方 Java protobuf、revision/provider/fallback 边界 | Apache-2.0；2026-07 活跃 | Go/Node/JVM producer；Windows 可用性依构建工具；consumer 兼容 Java 17；producer 会增加打包体积 | 精确关系可压缩模型上下文；本地优先 | 成熟 producer；需要语言 runtime/build setup；无 daemon 必需 | 保留 V3.6 consumer。自动 producer 仍需独立安全安装/副作用设计，本轮不静默调用 |
| JGraphT | DIRECT_REUSE | PageRank 与 Label Propagation | EPL-2.0/LGPL-2.1；现有锁定依赖 | 纯 Java，Java 17/Windows 友好 | 图排序降低 token；适合有界大图 | 无手工 setup/daemon | 继续直接复用，不写图算法 |
| Aider Repo Map | PATTERN_REUSE | tags、definition/reference 图、PageRank、token budget map、cache | Apache-2.0；2026-05 活跃，约 47k stars | Python/Tree-sitter；直接嵌入会引入 Python/native grammar | 大仓库压缩成熟、本地优先 | 需要 Python/grammar，无 daemon | 只复用“结构先行 + ranking + budget”模式；现有 SCIP/JGraphT 已覆盖核心 |
| Tree-sitter | ADAPTER_INTEGRATION（DEFERRED） | changed ranges、syntax/tag、增量解析 | MIT；2026-07 活跃，约 26k stars | C/Rust/Wasm，多 grammar/native 打包；Java 17 不是主路径 | 本地、低 token、适合增量 | grammar/provider 成熟度不一，需要打包；无 daemon | 不自研 grammar；等统一 provider 与跨平台打包 PoC |
| PyDriller | PATTERN_REUSE | 有界 Git mining、commit selection、modified-file metadata | Apache-2.0；v2.10，2026-07 活跃 | Python/GitPython；Windows 可用；引入第二 runtime | 大历史筛选有效；本地优先 | pip setup，无 daemon | ProjectFlow 已有安全 Git CLI，复用 milestone/window 思想，不引入 runtime |
| CodeBoarding | PATTERN_REUSE | Static Analysis → LLM → component/architecture、增量更新 | MIT；2026-07 活跃，约 2.3k stars | Python 3.12/3.13、Node、LSP binaries；Desktop 包装重 | 分层结构有利 token；本地 CLI | setup 会下载 LSP/Node；无常驻必需 | 复用专业分析器与语义综合分层，不复制 LSP/runtime |
| Litho / deepwiki-rs | PATTERN_REUSE | preprocessing、research、外部文档、多阶段 synthesis | MIT；2026-07 活跃，约 1.3k stars | Rust binary；Windows 需额外验证；非 Java 17 内嵌 | 可分阶段压缩，但产品偏 Wiki | Cargo/二进制 setup；无 daemon | 复用 Evidence → specialized research → synthesis，不转向 Wiki 生成 |
| DeepWiki-Open | REFERENCE_ONLY | Repo 自动组织、RAG、wiki navigation | MIT；2026-07 活跃，约 17k stars | Python + Next/Docker/embedding storage，包装重 | RAG 可处理大材料但引入 embedding/token/缓存成本 | 需要模型/embedding/runtime，通常服务进程 | ProjectFlow 当前不需要通用 RAG/向量库，拒绝直接集成 |
| RepoAgent | REFERENCE_ONLY | AST hierarchy、关系、Git 增量文档 | Apache-2.0；最后主要活动 2024-12 | Python，当前重点 Python 项目 | 逐对象文档 token 成本高 | pip/pre-commit setup，无 daemon | 只确认 structure-first/incremental 模式；拒绝逐对象文档方向 |
| GitNexus | REJECT（代码）/ PATTERN_REUSE（思想） | graph-first、cluster、impact、execution flow、token-efficient context | PolyForm Noncommercial 1.0.0；2026-07 活跃 | Node、Tree-sitter、LadybugDB；CLI/bridge 增加运行时与存储 | 本地 graph 有价值，大仓库 CLI 模式 | npm setup，可运行 bridge/server | 许可证与产品边界不适合复制；只借鉴 graph-first 与 cluster 思想 |
| Semgrep | REFERENCE_ONLY | 成熟多语言规则/static analysis provider | LGPL-2.1；2026-07 活跃，约 16k stars | OCaml/Python binary；Windows 有已知边界；Java 17 外部工具 | 本地扫描，规则可有界；大型 targeting 有成本 | 安装 CLI；无 daemon 必需 | 未来安全/规则 provider 候选，不作为通用理解引擎 |
| CodeQL | REJECT（通用内置） | 精确 database/query、安全分析 | 查询库 MIT，但 CLI 有独立使用条款；2026-07 活跃 | 大型独立 CLI/database；Windows 可用；包装重 | 强但 index/time/storage 成本高 | 需下载 bundle/setup，无 daemon | 许可证/体积/用途不适合任意本地项目默认分析 |
| Glean / LSP | REFERENCE_ONLY | 跨语言索引、语义查询、provider boundary | 多组件/协议各异 | 通常需要 server/indexer/runtime，Desktop 影响大 | 大规模成熟但超出轻量本地边界 | 常需 setup 或服务 | 只比较能力边界，不实现协议或 runtime |
| CodeScene / evolution research | PATTERN_REUSE | hotspots、churn、temporal coupling、change risk、architecture evolution | 商业产品/研究思想，代码不可复制 | 外部产品/runtime，不纳入 Java 包装 | 适合筛选里程碑，避免逐 commit LLM | 可能需要服务/setup | 只复用公开研究方法，V3.7 Evolution Preview 先做有界窗口策略 |

## 最终借用结果

本轮代码继续直接复用 SCIP、JGraphT、Git CLI、Model Gateway 和 Durable Job。新代码只实现 ProjectFlow 特有的 Evidence Source Map、Semantic Scout validation、capability registry、Adaptive Plan、Dynamic Profile 与 Historical Coverage。

明确不造：多语言 Parser/grammar、SCIP producer、Git、PageRank、全文引擎、向量库、通用 RAG、LSP、Agent runtime、workflow engine、daemon 和 session recorder。

参考仓库：

- https://github.com/scip-code/scip
- https://github.com/scip-code/scip-java
- https://github.com/sourcegraph/scip-typescript
- https://github.com/Aider-AI/aider
- https://github.com/tree-sitter/tree-sitter
- https://github.com/ishepard/pydriller
- https://github.com/CodeBoarding/CodeBoarding
- https://github.com/sopaco/deepwiki-rs
- https://github.com/AsyncFuncAI/deepwiki-open
- https://github.com/OpenBMB/RepoAgent
- https://github.com/nxpatterns/gitnexus
- https://github.com/semgrep/semgrep
- https://github.com/github/codeql
