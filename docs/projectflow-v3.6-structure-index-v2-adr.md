# ADR：ProjectStructureIndex V2

状态：Accepted for V3.6

## 决策

保留 `ProjectStructureIndexer` 作为唯一业务 SPI，生产实现采用组合 provider：

1. `MANIFEST_FILESYSTEM` 永远先运行，提供文件、manifest、workspace、基础入口和 fallback。
2. 绑定目录存在安全、大小合规的 `index.scip` 时，`SCIP` Adapter 读取官方 protobuf并增强 Symbol、Definition、Reference、代码依赖和 provider diagnostics。
3. SCIP 不存在、过期、超限或解析失败时返回降级 diagnostics 和 unknowns，不使项目理解失败。
4. Tree-sitter 在 V3.6 保持独立 provider 边界，不直接内置。

## V2 read model

V2 在 V1 文件、模块、入口、关系、evidence、coverage 和 dirty set 上增加：

- symbols
- definitions
- references
- important nodes
- functional areas
- provider diagnostics
- structure metrics

SCIP symbol identity 保留为 opaque 值；ProjectFlow 只生成本地 bounded ID 供 API 引用，不重新定义跨语言符号标准。

## 图与功能区域

SCIP occurrence 的 definition file 和 reference file 形成文件级有向依赖图。JGraphT PageRank 计算 important nodes；JGraphT Label Propagation 在无向视图上形成候选区域。

Functional Area 的边界来自代码关系，不来自目录名。确定性 label 只使用该 cluster 的高排名 symbol display name 和语言；模型可以在 evidence 约束下给出用户可读语义名称，但不能改变成员或编造关系。

## 有界规则

默认上限：

- SCIP index：256 MiB
- Document：20,000
- Symbol：50,000
- Definition：50,000
- Reference：100,000
- Relation：100,000
- Functional Area：100
- 每区域成员文件：200
- 模型结构摘要字符：继续服从 48,000 全局上限

达到上限时：

- diagnostics 标记 TRUNCATED
- coverage 降低
- unsupported/unknowns 明确可见
- 不把部分索引描述为完整成功

## 缓存与增量

库存无变化继续直接命中 V3.6 cache，SCIP 不重复读取，模型请求为 0。

有变化时 V3.6 仍以文件 dirty set 驱动一次有界组合索引。SCIP index 的生产是否增量由外部 indexer 决定；ProjectFlow 不伪称自己实现了编译器级增量索引。changed symbols、relations 和 impacted areas 由前后持久化 V2 read model 的稳定 ID 差异计算，为 V3.7 增量引擎提供边界。

## 被拒绝方案

直接 Tree-sitter Java：官方主线 JDK 要求与 Java 17 不对齐，且 grammar/native 多平台打包风险未解决。

自写语言规则：重复发明 Parser 和 Symbol Protocol，违反 V3.6 目标。

LSP 常驻：引入语言服务器生命周期和 daemon 方向，不符合手动刷新与 GUI/Core 同生命周期。

只用目录聚类：会把文件夹名称误当语义，不能满足功能区域可信度。
