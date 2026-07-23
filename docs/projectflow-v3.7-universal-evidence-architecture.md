# ProjectFlow V3.7 Universal Evidence Architecture

## 目标

任意绑定目录先回答“真实有什么”，再决定“分析什么”。结果必须诚实、有界、可诊断，并与 ProjectFact 等长期层隔离。

## 生产流程

```text
Repository / Folder
  → RepositoryIntakeService
  → ProjectEvidenceDiscoveryService
  → Evidence Source Map
  → CompositeProjectStructureIndexer
  → HistoricalCoverageService
  → SemanticScoutService（符合条件时一次 Model Gateway 调用）
  → AdaptiveAnalysisPlanner + AnalysisToolRegistry
  → DynamicProjectProfileSynthesizer
  → ProjectUnderstandingSnapshot JSON
  → GET read-only UI
```

空目录、空白文本、无模型和无变化路径可在模型前结束。有内容文档和代码项目最多使用一次 Scout + Profile 综合请求。工程工具先产生可验证结果，模型只处理压缩候选。

## Evidence Source Map

Source ID 由相对 locator 的 SHA-256 前缀稳定生成。Map 记录 category、候选 source type、相对 locator、确定性角色、重要度、当前性、置信度、deep-read 状态、摘要和 evidence refs。响应不返回 content sample。

Discovery 对文档/config/manifest/CI/迁移/Agent result 等读取至多 8 KiB、1,600 字符和 16 个非空行；最多 500 候选、80 个 Scout 样本。敏感路径不进入详情，样本中 credential marker 行再次隐藏。generated/vendor/binary 默认跳过。

## Hybrid Intelligence

确定性层负责存在性、安全、路径、规模、Git、SCIP、图、coverage、cache 和工具可用性。模型负责 semantic role、shape hypothesis、适用维度、currentness/conflict 和 evidence-backed section。所有模型判断必须引用 allow-list evidence ID。

`AnalysisToolRegistry` 是模型意图与 provider 之间的信任边界。当前注册 FILESYSTEM、MANIFEST、SCIP、GIT_HISTORY、GIT_TAG、WORKTREE、DOC_READER、AGENT_RESULT 和未来 remote capability。未注册或不可用请求被丢弃。

## 数据与持久化

V3.7 不新增表。以下对象存入现有 replaceable snapshot JSON：

- EvidenceSourceMap
- SemanticScout
- AdaptiveAnalysisPlan
- DynamicProjectProfile
- HistoricalCoverage
- EvolutionPreview
- UnderstandingAnalysisMetrics

旧固定 section 作为兼容投影保留。旧 JSON 缺少新字段时仍可读取固定内容，下一次主动 refresh 因 `understanding-v3` 版本变化而安全重建。

## 安全和降级

- 模型失败：无旧快照时保存 deterministic V3.7 profile；有旧成功快照时保留并标 STALE。
- 无 Git：Historical Coverage 为 unavailable/limited document history，不生成 Timeline。
- 无 SCIP：使用 MANIFEST_FILESYSTEM，明确 precise relation 不可用。
- 未知 evidence ID：相关 shape/assessment/claim/conflict 丢弃并记录 limitation。
- 大仓库：file details、candidate、sample、structure、prompt、Git period 均有上限。
- GET：只读数据库，不扫描、不运行 Git、不调用模型、不写 Fact 或派生历史。

## Tool/provider 扩展

未来 provider 只能实现注册 capability，由工程层生成结果。SCIP producer、remote forge、Tree-sitter 和文档 reader 可以扩展，但不能让模型直接运行命令，也不能要求用户先整理目录。
