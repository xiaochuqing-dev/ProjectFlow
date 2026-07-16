# ProjectFlow V3.4.2 事实原生全生命周期能力地图

## 三层边界

旧 ProjectCapabilityCard 来自某次分析和最近输入，依赖候选、确认、忽略等人工状态，不能表达稳定身份、完整事实覆盖、连续版本或非破坏性合并，因此退出主链。ProjectFact 继续是唯一事实来源；Timeline 只解释事实何时发生；Capability Map 解释从 0 至今的事实证明项目具备什么长期能力。Timeline Theme 只属于一个时间段，不是 ProjectCapability。

## 长期能力与演进

ProjectCapability 保存系统生成的稳定 ID、规范名称、aliases、当前摘要、解决问题、长期价值、产品区域、状态、确定性成熟度、来源统计、版本、模型诊断引用和 merge redirect。stable identity 由 project、问题、产品区域和规范语义共同产生，不能只由名称决定；名称变化保留为 alias。

ProjectCapabilityEvolution 是不可重写的历史事件，记录 NEW_CAPABILITY、ENHANCE_CAPABILITY、ADD_EVIDENCE、MERGE_CAPABILITY 或纠正、版本前后、发生时间、来源 fact/batch/period 和分析 job。ProjectCapabilityFact 是能力到事实的规范化关系，区分 formation、enhancement 和 evidence，并指向来源 evolution。详情页可继续追到 batch、commit、file、Agent result 和 evidence。

merge 只在同项目、活动能力、问题和产品区域一致、且存在足够重叠事实时自动执行。来源能力标记 MERGED 并保留 redirect、aliases、旧关系和旧 evolution；关系复制到目标但不删除历史。高风险 merge 进入 ProjectCapabilityAttention。

## 成熟度

成熟度不接受模型分数。规则使用 fact、batch、commit、evidence、evolution、时间跨度和 attention：证据或批次不足为 FORMING；满足长期跨度和充分验证为 LONG_TERM_STABLE；多次演进且跨期为 CONTINUOUSLY_ENHANCED；其余为 FORMED。API 同时返回可读 maturityReason。

## 全历史 bootstrap 与增量刷新

首次 bootstrap 分页读取全部 ProjectFact，每块最多 120 条，并携带现有稳定能力上下文。每个输入 fact 必须恰好进入 operation.factIds、noCapabilityChangeFactIds 或 attentionFacts；未知、跨项目、重复、遗漏 ID，未知 capability ID，模型 maturity/reasoning 或未来规划字段都会使输出无效。系统生成数据库 UUID，模型只能为新能力给临时 key。

bootstrap 完成条件是 source fact count 与 coverage count 完全一致。5000 条事实需要 42 个有界 chunk，不在每个 chunk 重建完整图。增量刷新只读取缺少 coverage 或 fact updatedAt 已变化的输入，并可执行 NEW、ENHANCE、ADD_EVIDENCE、MERGE。规则校验后在一个持久化边界自动应用；用户不确认正常能力变化。

## 状态、幂等与失败保护

ProjectCapabilityMapState 记录 source fingerprint、dirtySince、source/covered/assigned/no-change/attention 数量、最新成功和最新尝试 job、错误及 generation version。fingerprint 包含全部 fact ID/updatedAt、生成版本和当前能力身份/merge 状态。等价活动 job 复用现有任务；取消、retry、队列和重启继续使用 V3.3.7 语义。模型调用在事实事务之外，GET 从不触发模型。

history backfill 运行时只累计 dirty，完成后触发一次完整覆盖刷新，不在每个 chunk 后重建全图。刷新失败时状态为 FAILED 或 READY_STALE；已有成功能力、关系、演进和 coverage 不被删除。retry 只用于异常恢复。

## 旧数据迁移

旧卡片保持原表、接口和兼容展示。只有 CONFIRMED 且 sourceRefs 能追到 sediment、segment 和 ProjectFact 的卡片可幂等建立 legacy-seeded ProjectCapability；没有事实的确认卡片进入 attention。CANDIDATE、NEEDS_CONFIRMATION 和 IGNORED 不迁移，不删除，也不成为事实。旧能力解读字段继续作为兼容表达，不替代长期能力事实关系。

## API 与 UI

API 提供 overview、稳定能力分页/筛选/搜索、详情、evolutions、facts、recent changes、attention 和 retry；所有入口通过 userId 与 projectId 校验所有权。主页面显示全历史覆盖、地图状态、成熟度、能力列表、近期变化、attention 和 stale 提示；旧卡片折叠在兼容区且没有旧分析/确认主操作。详情页显示当前版本、成熟度原因、merge redirect、演进和事实证据链。项目切换使用请求代次，慢响应不能让项目 A 闪回项目 B；次要读取失败保留已成功区块。

## 后续边界

Hermes 与 Obsidian 正式同步留到下一阶段。它们只能消费 ProjectFact、Timeline 和 ProjectCapability read models，不得成为事实来源，也不得修改历史 Evolution。
