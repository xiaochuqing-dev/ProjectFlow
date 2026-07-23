# ProjectFlow V3.6 最终验收报告

验收时间：2026-07-23 至 2026-07-24

## A. V3.6 最终完成了什么

完成 Structure Index V2、官方 SCIP protobuf Adapter、JGraphT PageRank/Label Propagation、压缩证据语义归纳、provider diagnostics、最小 Evolution Bridge、只读分页 API 和聚焦页面展示。版本统一升级为 3.6.0。

## B. 直接复用的 V3.5 核心

复用 Repository Intake、分类与规模、ProjectStructureIndexer SPI、MANIFEST_FILESYSTEM fallback、持久化结构索引、ProjectUnderstandingSnapshot、dirty set、CURRENT/STALE、coverage/unknowns、无变化 cache、Durable Job 和 Model Gateway V2。Fact、Timeline、Capability、Memory Gateway、Hermes 与 Obsidian 边界不变。

## C. 结构理解提升程度

从文件、manifest 和候选入口提升为可选的跨语言 Symbol、Definition、Reference、Occurrence、代码引用关系、重要节点与关系区域。没有 `index.scip` 时仍是 fallback，并明确显示 Symbol coverage 为 0；不夸大为全语言 AST 或 Call Graph。

## D. Tree-sitter 决策

结论：ADAPTER + DEFERRED。

官方 Java 主线当前要求 JDK 22+，而 ProjectFlow 基线为 Java 17；grammar 版本、Windows/macOS/Linux native 打包和供应链边界也未完成。V3.6 不直接内置、不复制 grammar，未来只作为可替换 syntax/changed-range provider。

## E. SCIP 决策

结论：DIRECT PROTOCOL REUSE + ADAPTER。

生产依赖固定为 `com.sourcegraph:scip-java-proto:0.12.3`。ProjectFlow 只消费项目根目录安全候选 `index.scip`，语言 index 生产由 scip-java、scip-typescript、scip-python 等官方工具负责。不存在、超限或解析失败时回退。

官方 scip-java 0.12.3 发布资产已核验 SHA-256 并在 JDK 17 启动。Windows 真实生成因上游 CLI 调用 `mvn` 而机器仅提供 `mvn.cmd`，在编译前失败；没有把临时 shim 变成产品轮子。

## F. 真正影响架构的开源项目

- SCIP：跨语言结构协议与 revision index 边界。
- Aider Repo Map：definition/reference 图、PageRank 和 token-budget map。
- JGraphT：直接复用 PageRank 与 Label Propagation。
- Sourcegraph Code Intelligence：precise provider 与 search/fallback 分层。
- PyDriller：有界 revision/commit 选择思想。
- RepoAgent：结构先于语义模型。
- GitNexus：只借鉴 graph-first、cluster 和 impact；许可证限制下不复制代码。

完整 License 和决策见 borrowing map 与 THIRD_PARTY_NOTICES。新增四个固定 JAR 约 2.30 MiB，均为纯 Java，无 native 安装。

## G. 明确取消自研的轮子

不自研 Parser、grammar、跨语言 Symbol Protocol、PageRank、社区发现、Git、全历史挖掘框架、第二套 Job/HTTP/重试系统，也不逐文件、逐 Symbol、逐 Commit 调模型。

## H. Structure Index V2

V2 保留文件、模块、manifest、entry、engineering signals、evidence、coverage 和 delta，并增加 symbols、definitions、references、importantNodes、functionalAreas、providerDiagnostics 和 metrics。它记录 source revision、content hash、index version 和 bounded currentness，是可重建派生智能，不是事实源。

## I. 结构对象识别

- Symbol：保留 SCIP opaque identity，生成本地 bounded ID。
- Definition / Reference：使用 SCIP Occurrence role 与 range。
- Dependency：reference file 指向 definition file 的有向关系。
- Entry：复用 V3.5 manifest/filesystem 候选入口。
- Functional Area：SCIP 关系图经 JGraphT Label Propagation 形成成员，PageRank 选择 key symbols。
- Evolution affected area：优先 Functional Area；精确区域缺失时只降级为明确标注的 MANIFEST_FILESYSTEM 结构模块，不把它叫业务功能。

## J. Functional Area 如何避免目录名

区域成员只由 definition/reference 图聚类形成；目录名不参与 cluster membership。确定性标签保持证据化，模型只能在成员和 evidence allow-list 内做用户可读语义归纳，不能修改关系。

## K. LLM 只负责什么

只负责高价值区域的用户可读解释、当前架构/能力语义与 unknowns。Parser/Indexer 可确定的 Symbol、关系、ranking、cluster、coverage 和 bridge 均不调用模型。

## L. Token 限制

模型输入限制 48,000 字符；important nodes 最多 50，Functional Areas 最多 100，每区 member path 最多 20、key symbol 最多 10、evidence 最多 12，key symbols 最多 400，最终 evidence 最多 700。空目录、非代码、无模型、未变化和真实性能 benchmark 都是 0 模型/0 token。所有模型调用继续由 Model Gateway 动态预算、取消和恢复控制。

## M. Evolution Bridge 完成程度

已实现最小生产桥：

真实 Git parent → 已有 ProjectFact meaningful change → 真实 commit → changed paths → Functional Area 或显式结构模块 → evidence。

每次刷新最多检查最近 200 Fact、创建 20 条桥、读取 500 个 changed paths；fingerprint 防重复。GET 只读数据库。未实现逐 revision 深结构重建、milestone 选择或完整历史引擎。

## N. 真实 before → change → after

真实 Spring Petclinic 回放成功：

- before：`e0db9b184e028d41bcb626f3cbf03a942f67e104`
- after：`f182358d02e4a68e52bdbabf55ca7800288511e7`
- 正常扫描生成 2 条 ProjectFact
- bridge 使用其中 1 条 Fact、1 个真实 changed file、`结构模块 src` 和 8 个 evidence refs
- before 无持久化深结构快照，因此状态为 INFERRED，未冒充 OBSERVED

独立单元测试还用真实临时 Git commit 验证前后 structure revision 均对齐时产生 OBSERVED，并验证重复 rebuild 不新增。

## O. Current Understanding 与 Fact 边界

保持。Structure Index 和 Understanding Snapshot 都是 replaceable current interpretation；Bridge 是 derived link。三者均不创建、删除或改写 ProjectFact、Timeline、Capability 或已有 Evolution。

## P. 大项目性能

所有结果为真实仓库首次有界 MANIFEST_FILESYSTEM fallback；仓库没有 `index.scip`，所以 Symbol/Definition/Reference/Cluster 为 0，模型请求和 token 为 0。

| 档位 / 仓库 | files | LOC | relation | index time | repeat fingerprint | coverage | truncated |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| small / Spring Petclinic | 130 | 4,450 | 7 | 2,530 ms | 19 ms | 0.771 | false |
| medium / ProjectFlow | 632 | 66,242 | 10 | 1,007 ms | 28 ms | 0.787 | false |
| large / JUnit Framework | 2,326 | 235,265 | 25 | 16,893 ms | 79 ms | 0.799 | false |
| huge / React | 7,274 | 833,665 | 9 | 2,014 ms | 197 ms | 0.799 | false |
| >1M / VS Code | 16,344 | 3,550,729 | 14 | 2,950 ms | 497 ms | 0.793 | false |

JUnit 首次浅克隆含延迟对象读取，因此耗时明显高于其他仓库。fallback index size 为 0（无外部二进制 index）；memory peak 未可靠测量，记录为 -1。SCIP fixture 记录 3 Symbol、3 Definition、2 Reference 和非空 cluster。

## Q. 增量结果

V3.6 已持久化 added/changed/deleted/unchanged dirty set，并让 Bridge 只处理 dirty paths。无变化通过快速 fingerprint 直接 cache hit。存在变化时当前仍做一次有界组合重建，`incrementalUpdateMs=-1` 明确表示没有宣称编译器级增量。真正 changed-symbol/impacted-area 增量留给 V3.7。

## R. 无变化是否 0 模型

是。自动化测试验证第二次 refresh cache hit，ModelGateway 从未调用，且不重复生成 Evolution。

## S. 测试

- Maven 最终完整 H2 套件：323 tests，0 failure，0 error，1 条条件 benchmark skipped。
- 新增核心测试：官方 SCIP Java+TypeScript fixture、invalid SCIP fallback、真实 Git bridge、bridge idempotency、Gradle 单体/Monorepo 边界、unchanged cache、dirty set、model failure stale preservation。
- 前端 TypeScript：通过。
- 前端契约：47/47。
- Next.js 生产构建：通过，23 个页面生成。
- Playwright 真实前后端：8/8，通过持久化理解、事实、能力、时间线、取消/retry 和项目隔离。
- PostgreSQL 本机：Docker daemon 不可用，未伪造本地通过。

最终门禁已在版本对齐为 backend/frontend 3.6.0、structure-v2、understanding-v2 后执行，避免因版本断言不一致重复运行。根目录 `Start-ProjectFlow.bat -NoBrowser` 已从当前工作树完成依赖校验、Next.js 生产构建、Java 17 后端编译、H2 旧库启动和前后端健康检查；`logs/last-embedded-build.json` 记录 version=3.6.0、frontendBuildId=`ltZE9q00FUsCYcac9ngkw`、hasLocalChanges=true 和 readyAt=`2026-07-24T00:17:32.1432396+08:00`。页面人工验收和 Spring Petclinic 演进回放也已在隔离端口完成。

## T. 产品人工验收

结果为 CONDITIONAL PASS，详见 `projectflow-v3.6-product-acceptance.md`。页面的 trust calibration、unknowns、fallback 和真实演进桥可信；没有 SCIP 的仓库不会产生虚假深结构。真实 provider 语义质量和官方 indexer 一键生产仍未完成。

## U. 真实 Provider

SKIPPED。环境没有可安全使用的 DEEPSEEK、OPENAI 或 ANTHROPIC Key。固定兼容模型只用于自动化与产品契约，未描述为真实 Provider。

## V. PostgreSQL CI

本地 Docker daemon 不可用，因此本地 Testcontainers SKIPPED。V3.6 GitHub Actions Run 30024524557 已通过真实 PostgreSQL Testcontainers 集成测试；同一 Run 的后端/H2、前端、Playwright、Hermes、Obsidian 和敏感内容门禁也全部成功。可选真实 DeepSeek 因未配置安全 Key 按设计跳过。

## W. 已知风险

1. V3.6 消费现有 `index.scip`，尚未提供跨语言 indexer 安装/调用编排。
2. scip-java 0.12.3 在当前 Windows Maven 环境有 `mvn`/`mvn.cmd` 启动兼容问题。
3. SCIP index freshness 由外部 producer 负责；V3.6 尚未强制验证 index 对应 revision。
4. 大型有效 SCIP index 的真实 index size、heap peak 和解析耗时尚未在本机测得。
5. Functional Area 的真实业务命名质量尚未用现实 Provider 验收。
6. 有变化时仍做有界全量组合重建，不是 symbol-level incremental。
7. 首次历史只生成有限 bridge，不重建每个 revision 的深结构。

## X. 下一阶段建议

V3.7 聚焦 Incremental Refresh & Evolution Engine：绑定 producer revision、last analyzed revision → current revision、changed symbol/relation、impacted area、milestone 选择、可恢复 checkpoint 和真实大索引性能。不要扩张 Desktop、daemon 或全新 GUI。

## Y. Commit / branch / CI

- 基线 SHA：`7c92c484546e43c7a5e9351611f57f8691aba989`
- branch：`master`
- V3.6 implementation SHA：`23212ee20217d56f576b25989fb1c7396d47db95`
- V3.6 CI run：`30024524557`，SUCCESS
- 未创建分支、PR 或 release
