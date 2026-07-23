# ProjectFlow V3.6 产品人工验收

验收时间：2026-07-23 至 2026-07-24

## 结论

V3.6 达到“安全、诚实、可交付的最小深结构与演进桥”标准，但不把外部精确索引未生成的仓库描述为已完成深度理解。

有 `index.scip` 时，Structure Index V2 能消费官方 SCIP Symbol、Definition、Reference 和 Occurrence，形成关系图、重要节点和 Functional Area。没有 `index.scip` 时，产品稳定回退到 MANIFEST_FILESYSTEM，清楚展示 coverage、unsupported 和 unknowns，仍可形成基于真实 Git、ProjectFact 和结构模块的最小 Evolution Bridge。

## 验收对象

| 对象 | Revision | 用途 |
| --- | --- | --- |
| ProjectFlow | `7c92c484546e43c7a5e9351611f57f8691aba989` 加本轮工作树 | 自身产品页面、Java/React 混合项目、中型仓库 |
| Spring Petclinic | `f182358d02e4a68e52bdbabf55ca7800288511e7` | Java 单体、真实 Git 历史与演进桥 |
| Flask | `36e4a824f340fdee7ed50937ba8e7f6bc7d17f81` | Python 小项目 |
| JUnit Framework | `76824f254752504ee08a11d8b0b3254e197cc17e` | Java 大型多模块项目 |
| React | `28cd4bb08f1b66808bede284fca978cc9b065154` | TypeScript/React 超大仓库 |
| VS Code | `998b93211cff607c69f1a2fa0b9ce1014eb8d858` | 超过 1M LOC 的大型仓库 |

真实仓库均为临时浅克隆或本地 ProjectFlow 工作树，不进入提交。

## ProjectFlow 自身页面人工检查

通过本地 H2、真实前后端和固定兼容模型运行 `/project-intelligence/understanding`。固定模型仅验证产品契约，不是现实 Provider。

实际页面显示：

- V3.6.0、structure-v2、understanding-v2 版本一致。
- 632 个文件、450 个源码文件、66,239 估算 LOC，分类为 MEDIUM。
- 没有 `index.scip` 时结构来源明确为 MANIFEST_FILESYSTEM。
- 5 条模型推断均绑定证据；4 个 unknown 明确说明 Symbol、Definition、Reference 和运行时调用关系不可用。
- Architecture 在缺少代码关系时保持未知，没有用 frontend/backend 目录名伪造架构。
- 演进桥为空时明确说明缺少真实 Git、Fact 与结构区域的共同证据，不为填充页面编造历史。
- 页面遵循 Summary First：用途、技术、结构、架构、能力和工程状态先展示，证据与 unknowns 后置。

人工判断：

- 项目用途：仅达到“大体正确”。固定模型给出本地工程工作流的概括，但不具备真实 Provider 的细粒度语义质量。
- 技术栈：确定性语言和 manifest 信号可信；页面固定模型摘要偏泛。
- 核心模块：fallback 能显示模块边界，但不是精确代码关系。
- Architecture：诚实保持未知，优于按目录猜测。
- Functional Areas：没有 SCIP 时为不可用，不伪造。
- Core Capabilities：只有证据绑定的推断；不写入 ProjectFact。
- Observed / Inferred / Unknown：标签清楚。
- 信任感：失败和未知表达可信；“真正理解代码关系”的体验需要仓库提供有效 SCIP 索引。

## 真实仓库回放

| 仓库 | 文件 / 源码 | LOC | 结果与人工判断 |
| --- | ---: | ---: | --- |
| Spring Petclinic | 130 / 56 | 4,450 | 正确识别为 SMALL。人工验收发现并修复了仅因 `settings.gradle` 存在就误判 Monorepo 的旧规则；现在必须存在 include/includeBuild。 |
| Flask | 236 / 86 | 18,372 | SMALL，Python 扩展和 manifest 信号可用；无 SCIP 时精确关系明确 unsupported。 |
| ProjectFlow | 632 / 450 | 66,242 | MEDIUM，Java + TypeScript/React 当前结构可组合盘点；无 SCIP 时不声称跨层调用关系。 |
| JUnit Framework | 2,326 / 1,929 | 235,265 | LARGE 规模数据实际完成有界扫描；真实 Gradle include 保留 workspace 边界。 |
| React | 7,274 / 4,625 | 833,665 | HUGE，完成有界扫描且没有逐文件/逐 Symbol 模型调用。 |
| VS Code | 16,344 / 11,505 | 3,550,729 | HUGE，未截断；初次结构扫描约 2.95 秒，fingerprint 约 0.50 秒。 |

这些仓库均未自带 `index.scip`，因此 Symbol、Definition、Reference、Functional Area 数量为 0，coverage 约 0.77 至 0.80。报告不把 fallback 结果描述为精确深结构成功。

## 精确 SCIP 契约人工复核

自动化 PoC 用官方 `com.sourcegraph.Scip` protobuf 构建 Java + TypeScript 混合索引，包含 Java App、Java Service 和 TypeScript Screen：

- 3 个 Symbol
- 3 个 Definition
- 2 个 Reference
- 至少 1 条 REFERENCES 关系
- PageRank important nodes 非空
- Label Propagation Functional Areas 非空
- invalid protobuf 正确回退且 symbol coverage 为 0

官方 scip-java 0.12.3 发布资产 SHA-256 与发布页一致，JDK 17 可启动。真实 ProjectFlow Java 索引生产在 Windows 上因 scip-java 硬调用无扩展名 `mvn`、机器只提供 `mvn.cmd` 而在编译前失败。V3.6 没有为绕过该限制把自制 Maven/SCIP launcher 加入产品；该限制作为已知风险保留。

## 真实 Evolution Bridge

Spring Petclinic 临时克隆补足真实父提交后，使用正常 ProjectFlow 流程：

`e0db9b184e028d41bcb626f3cbf03a942f67e104`

→ 固定兼容模型辅助的“分析新变化”生成 2 条 ProjectFact

→ meaningful change 对应真实提交 `f182358d02e4a68e52bdbabf55ca7800288511e7`

→ `git diff-tree` 验证 1 个真实变更文件

→ 映射到 `结构模块 src`

→ 生成 1 条 Evolution Bridge，关联 1 个 Fact 和 8 个证据引用。

before revision 没有持久化深结构快照，所以桥正确标为 INFERRED，而不是 OBSERVED。重复构建的幂等性由 fingerprint 测试验证。

## 14 项产品判断

1. 用途基本准确：条件通过；固定模型只证明契约。
2. 技术栈准确：通过确定性语言/manifest 验证。
3. 核心模块准确：fallback 可用，精确关系依赖 SCIP。
4. Architecture 符合实现：无精确关系时保持未知。
5. Functional Areas 有意义：官方 SCIP fixture 通过；真实仓库因无索引未验证语义质量。
6. Core Capabilities 有证据：证据引用经过 allow-list。
7. Observed 清楚：通过。
8. Inferred 清楚：通过。
9. Unknown 清楚：通过。
10. Git 历史可解释：Spring Petclinic 真实桥通过。
11. 无明显文件名当语义：通过；模块 fallback 明确标为结构模块，不冒充功能语义。
12. 无模型幻觉进入事实：通过；未知 evidence 被过滤，Current Understanding 不写 Fact。
13. 无低价值 Git/GitHub 对象堆叠：通过；页面只展示紧凑 before/change/after。
14. “真的理解项目”的信任感：精确 provider 可用时具备基础；fallback 状态下产品诚实但深度有限。

最终产品验收：CONDITIONAL PASS。V3.6 的安全边界、深结构消费链、产品 read model 和真实演进桥已闭环；外部 SCIP 索引的一键生产与真实 Provider 语义质量仍是后续验收项。
