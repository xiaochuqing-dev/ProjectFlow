# ProjectFlow V3.6 开源借鉴地图

研究日期：2026-07-23

原则：先使用标准、协议、Adapter 和成熟算法库。ProjectFlow 只实现结构来源编排、证据约束的功能/架构语义，以及结构和既有事实之间的演进桥。

## 决策

| 项目/技术 | 核心能力 | License | Java 17 / Windows / 打包影响 | V3.6 决策 |
| --- | --- | --- | --- | --- |
| SCIP | 跨语言 Index、Document、Symbol、Occurrence、Definition、Reference、Relationship | Apache-2.0 | `com.sourcegraph:scip-java-proto:0.12.3` 是纯 Java 协议消费包；语言 indexer 独立运行 | DIRECT_REUSE + ADAPTER_INTEGRATION |
| scip-java / scip-typescript / scip-python | 编译器级或语言服务级 SCIP 生产 | Apache-2.0 | 各自依赖 Maven/Gradle、Node 或 Python 环境；不适合作为 Java Core 强制运行时 | 外部生产，ProjectFlow 消费 `index.scip` |
| Tree-sitter | 容错、增量 CST、changed ranges、query/tag | MIT | 官方 Java 主线要求 JDK 22+；Kotlin/JNI 路径仍有 grammar 与多平台 native 打包成本 | ADAPTER_INTEGRATION，V3.6 不直接内置 |
| Aider Repo Map | Tree-sitter tags、definition/reference 图、PageRank、缓存、token budget 压缩 | Apache-2.0 | Python 实现不直接引入 Java Core | PATTERN_REUSE |
| JGraphT | PageRank、Label Propagation、连通性和成熟图算法 | EPL-2.0 或 LGPL-2.1 | `jgrapht-core:1.5.3` 纯 Java；无 native 包 | DIRECT_REUSE |
| GitNexus | graph-first、cluster、execution flow、change impact | PolyForm Noncommercial 1.0.0 | 商业使用受限，不能复制或打包 | REFERENCE_ONLY |
| RepoAgent | AST 对象层级、全局结构、增量文档 | Apache-2.0 | Python 且偏逐对象文档生成，模型调用粒度不符合本阶段 | PATTERN_REUSE |
| Sourcegraph Code Intelligence | precise SCIP 与 search fallback 分层、索引按 revision 生产和消费 | SCIP Apache-2.0；产品另有许可 | 验证“精确索引可选，fallback 永远可用”的 provider chain | PATTERN_REUSE |
| PyDriller | 有界选择 commit、tag、日期和文件历史，复用 Git diff | Apache-2.0 | Python runtime；ProjectFlow 已有固定参数 Git CLI | PATTERN_REUSE，不增加 Python 依赖 |
| CodeQL | 编译数据库与可查询程序关系 | MIT CLI / 查询库按组件许可 | 构建成本和语言数据库体积高，目标偏安全分析 | REJECT 作为默认结构 provider |
| Semgrep | 成熟语法/语义规则匹配 | LGPL-2.1 | 适合规则扫描，不是跨语言 definition/reference 索引 | REFERENCE_ONLY |
| LSP | 编辑器请求协议、definition/reference 动态查询 | 标准；实现各自许可 | 需要长期语言服务进程和 workspace 生命周期 | REJECT 作为 V3.6 持久结构格式 |
| Rekal / PROJECTMEM | immutable history、replaceable index、stale/supersede、search-first | Apache-2.0 / MIT | 不直接依赖 | PATTERN_REUSE |

## 核心源码与工程结论

SCIP：

- 协议仓库和 `scip.proto`：https://github.com/scip-code/scip
- 官方 indexer 列表与 schema 说明：https://sourcegraph.com/docs/code-navigation/writing-an-indexer
- Java indexer 设计与构建入口：https://github.com/sourcegraph/scip-java
- Maven 协议包：https://central.sonatype.com/artifact/com.sourcegraph/scip-java-proto/0.12.3

结论：ProjectFlow 不定义新的跨语言 Symbol/Occurrence 标准。V3.6 直接解析官方 protobuf，保留原始 symbol identity，并归一成产品级 bounded read model。Index 生产失败或不存在时退回 manifest/filesystem。

Tree-sitter：

- 核心增量解析：https://github.com/tree-sitter/tree-sitter
- 官方绑定要求：https://tree-sitter.github.io/tree-sitter/
- Kotlin binding：https://github.com/tree-sitter/kotlin-tree-sitter

结论：Tree-sitter 的 changed ranges 和 tags 适合后续增量 syntax provider，但 Java 17、grammar 版本和 Windows/macOS/Linux native 打包仍需独立 PoC。V3.6 不复制 grammar、不手写 AST 规则，也不让 Tree-sitter 缺失阻断产品。

Aider Repo Map：

- 核心实现：https://github.com/Aider-AI/aider/blob/main/aider/repomap.py
- 语言 tags 要求：https://aider.chat/docs/languages.html

结论：复用“raw graph → PageRank → token budget map”的思想。V3.6 使用 JGraphT 对 SCIP 文件依赖图排序，只把高价值节点、区域摘要和 evidence ID 交给模型。

GitNexus：

- 架构入口：https://github.com/nxpatterns/gitnexus
- 许可证：https://github.com/nxpatterns/gitnexus/blob/main/LICENSE

结论：只借鉴 graph-first、cluster 和 change impact，禁止复制 PolyForm Noncommercial 代码。

RepoAgent：

- 仓库与对象层级：https://github.com/OpenBMB/RepoAgent

结论：借鉴结构先于模型和分层综合，不采用逐对象文档生成产品方向。

软件演进：

- PyDriller 有界 commit 选择：https://pydriller.readthedocs.io/en/latest/repository.html
- Git 原生命令继续由已有 `FixedCommandExecutor` 执行。

结论：V3.6 最小桥只采样已有 Fact 指向的真实 commit 及其 parent，不逐 commit、逐 revision 重建全仓结构。完整 milestone/change-point reconstruction 留给 V3.7。

## 明确取消自研

1. 不自研 Parser、grammar 或多语言 AST 规则。
2. 不自创 Symbol、Occurrence、Definition、Reference 协议。
3. 不自写 PageRank、社区发现或图基础设施。
4. 不自研 Git 或完整历史挖掘框架。
5. 不逐文件、逐 Symbol、逐 Commit 调模型。
6. 不复制 GitNexus 受限代码。
7. 不创建第二套 Job、Provider HTTP、重试或取消系统。
8. 不增加 watcher、daemon、Agent recorder 或 Desktop runtime。

## 版本、升级和安全

生产依赖固定：

- `com.sourcegraph:scip-java-proto:0.12.3`
- `org.jgrapht:jgrapht-core:1.5.3`

升级策略：分别跟随 SCIP schema/indexer compatibility 和 JGraphT 稳定版；升级必须用真实 `.scip` fixture、旧索引兼容、Windows 构建和性能门禁验证。

安全边界：只读取绑定项目根目录内固定候选 `index.scip`；限制 index 文件字节数、Document、Symbol、Occurrence、Relation、Cluster 和 evidence 数量；不持久化 SCIP metadata 中的绝对 project root、源码全文或文档全文。
