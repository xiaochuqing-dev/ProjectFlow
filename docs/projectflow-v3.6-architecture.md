# ProjectFlow V3.6 Deep Structural Intelligence 与 Evolution Bridge 架构

## 产品语义

ProjectFlow 理解项目今天是什么样，并基于真实工程证据逐步重建它是如何演进到今天的。

V3.6 保持本地优先、用户主动刷新、GUI/Core 同生命周期的方向，不实现后台常驻或 Desktop 正式迁移。

## Pipeline

```text
Bound Repository
  → bounded Repository Intake
  → MANIFEST_FILESYSTEM fallback
  → optional SCIP adapter
  → Structure Index V2
  → JGraphT importance + functional clusters
  → bounded semantic synthesis through Model Gateway
  → ProjectUnderstandingSnapshot
  → Fact/Git/Structure matching
  → Evolution Bridge
```

## Provider chain

`CompositeProjectStructureIndexer` 是生产 SPI 实现。它先建立 fallback，再尝试安全读取项目根目录中的 `index.scip`。高级 provider 的失败只降低 coverage，并通过 diagnostics、unknowns 和 unsupported areas 可见。

Tree-sitter 作为未来 syntax provider：它适合 changed ranges 和 tag extraction，但 V3.6 不绑定官方 JDK 22+ Java runtime，也不捆绑多语言 native grammar。

SCIP 作为 V3.6 precise structure provider：ProjectFlow 使用官方 protobuf消费已有 index，不承担每种语言的编译和索引生产。

## Structure Index V2

V2 是 derived/rebuildable intelligence：

- 文件、语言、module/workspace 和 manifest
- SCIP Symbol identity
- Definition / Reference occurrence
- file dependency 与 symbol relationship
- PageRank important nodes
- relation-driven functional areas
- entry points
- coverage、unsupported areas 和 provider diagnostics
- source revision、hash、index version、currentness 和 dirty set

V2 不是事实源，不能创建或修改 ProjectFact。

## 模型边界

确定性层负责：

- index 解析
- definition/reference 判定
- graph edge
- PageRank
- cluster membership
- coverage
- evidence allow-list

模型只负责：

- 对高价值结构区域进行用户可读命名
- 综合当前架构和能力语义
- 标注无法解释的 unknowns

模型看不到全部 Symbol、源码全文、绝对路径或无限图。它只接收 bounded important nodes、functional area 摘要和 evidence ID；所有调用继续通过 Model Gateway V2。

## Evolution Bridge

Bridge 连接：

`before Git revision → ProjectFact meaningful change → after Git revision → affected functional area → evidence`

Bridge 是派生层，不修改 Fact、Timeline、Capability 或既有 Evolution。它优先映射代码关系 Functional Area；精确区域缺失时可映射明确标注的结构模块。无真实 commit/parent、Fact 或 changed-file 证据时保持空，不生成“看起来合理”的历史。

## 数据层边界

- ProjectUnderstandingSnapshot：当前项目是什么
- ProjectStructureIndex：当前结构的可重建智能
- ProjectFact：真实发生过的重要事实
- Timeline：事实的时间视图
- ProjectCapability：长期能力
- ProjectCapabilityEvolution：长期能力版本事件
- Evolution Bridge：结构状态与历史事实/证据的连接

## API

- `POST /api/projects/{projectId}/understanding/refresh`
- `GET /api/projects/{projectId}/understanding`
- `GET /api/projects/{projectId}/structure-index`
- `GET /api/projects/{projectId}/evolution-bridges`

GET 只读取持久化结果，不扫描、运行 Git、调用模型或修改派生层。

## 生命周期

未来 Desktop：

`GUI 启动 → Java Core 启动 → 展示持久化旧结果 → 用户主动刷新 → 分析 → GUI 关闭 → Core 退出`

V3.6 不增加 watcher、daemon、system tray、开机启动、自动分析、Tauri/Electron 正式迁移、Installer 或 Auto Update。
