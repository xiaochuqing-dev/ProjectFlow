# ProjectFlow V3.4.2 事实原生能力地图实施报告

## 1. 背景

V3.4.2 在 ProjectFact 事实层和 Timeline 时间派生层之上，建立回答“从 0 至今的事实证明项目具备什么能力”的长期 Capability Map。

## 2. 旧能力系统真实状态

安全副本基线有 13 张旧卡片：8 CANDIDATE、5 IGNORED、0 CONFIRMED。旧入口基于最近输入和单次分析，依赖逐卡确认状态。

## 3. 最近 40 / 8 卡片模式退出原因

最近窗口和每次重建不能证明全历史覆盖、稳定身份、连续 Evolution、逐 fact 关系或非破坏性 merge，因此旧卡片退出主页面和正常主链。

## 4. 本轮范围

实现 ProjectCapability、Evolution、fact relation、coverage、attention、map state、全历史 bootstrap、增量刷新、API、主页面、详情页、旧数据兼容和完整门禁。

## 5. 未重构基础设施

V3.4.0 ProjectFact/FactCursor/history、V3.4.1 Timeline、V3.3.8 ModelGateway 和 V3.3.7 persistent job 仅增加必要调用方，没有重写核心。

## 6. 系统文件安全边界

PASSED。只修改仓库内代码、测试、文档、项目脚本可生成物和隔离数据库副本；未修改操作系统文件或全局机器配置。原始 H2 最终恢复为任务前 SHA-256 `3BC7AFBDBFD382E9343D5EE0073E9794552A1CD9FAED690C5C781B4A38B8B318`。

## 7. 42 条 attention 原始分布

22 条 PASS/LOCAL_RULE、8 条 PASS/MODEL、7 条 PASS/AGENT_RESULT 的唯一原因是发生时间回退；3 条 NEEDS_REVIEW 同时有质量原因，2 条 NEEDS_REVIEW 同时有可能重复原因。42 条 fingerprint 全部唯一。

## 8. reclassification 规则

仅 source segment、batch、commit 或 Agent result、affected file、evidence 完整，quality=PASS，且唯一 attention 原因是发生时间回退时转 RECORDED。质量不足、冲突、重复或证据不完整继续 attention。

## 9. before / after

PASSED。42 NEEDS_ATTENTION → 37 RECORDED + 5 NEEDS_ATTENTION；fact fingerprint、内容和 FactCursor 不变。隔离增量样本加入后副本为 39 RECORDED + 5 NEEDS_ATTENTION。

## 10. FAILED WEEK retry

PASSED。2026-W28 从 FAILED 34/0 恢复为 READY 34/34；2026-W29 从 FAILED 8/0 恢复为 READY 8/8，隔离增量样本后继续为 READY 10/10。

## 11. ProjectCapability 数据模型

稳定实体保存 system UUID、canonical name、aliases、问题、价值、产品区域、状态、确定性成熟度、来源统计、版本、表达、模型/job 引用和 merge redirect。

## 12. Capability Evolution

NEW_CAPABILITY、ENHANCE_CAPABILITY、ADD_EVIDENCE、MERGE_CAPABILITY 和 correction 形成不可重写事件，记录版本前后、来源 facts/batches/periods 和 operation fingerprint。

## 13. Capability Fact relation

唯一 capability/fact 关系区分 FORMATION、ENHANCEMENT、EVIDENCE，并指向来源 Evolution；详情查询直接连接 ProjectFact 和 batch/evidence 统计。

## 14. stable identity

身份哈希包含 project、problem、product areas 和 canonical meaning，不只依赖名称；数据库 UUID 只由系统生成。

## 15. aliases

能力名称变化时旧 canonical name 进入去重 aliases；merge 也把来源名称和 aliases 汇入目标。

## 16. non-destructive merge

来源能力保留为 MERGED，保留旧关系、Evolution 和 redirect；目标补充 aliases 和关系。任何不满足安全条件的 proposal 不执行。

## 17. maturity rules

FORMING、FORMED、CONTINUOUSLY_ENHANCED、LONG_TERM_STABLE 由 fact/batch/commit/evidence/evolution 数、跨度和 attention 确定，返回 maturityReason；模型 maturity 字段被拒绝。

## 18. bootstrap architecture

按时间分页读取全部 ProjectFact，以现有稳定能力、Timeline context 和 compact facts 调用统一网关；模型在事实事务之外，规则在持久化前完成全量校验。

## 19. bootstrap full coverage

PASSED。真实安全副本最终覆盖 42/42，固定模型和 H2 测试也要求每个 fact 恰好分类一次。

## 20. chunk strategy

每块最多 120 facts；42=1 块、230=2 块、5000=42 块。chunk 只积累 operation，不在每块后重建完整图；Provider 遗漏 fact 时只允许一次有界 coverage repair，第二次仍不完整即失败。

## 21. initial capability count

PASSED。当前配置真实 Provider 在 42 条 ProjectFlow facts 上形成 8 个中文、项目特定能力；28 条进入能力、14 条 no-change、0 capability attention。

## 22. incremental architecture

通过 coverage 缺失或 source fact updatedAt 变化选择增量输入，携带已有 stable IDs，成功后只追加/增强必要能力并刷新 fingerprint。

## 23. operation protocol

NEW、ENHANCE、ADD_EVIDENCE、MERGE 是内部 JSON 协议；用户不选择 operation。未知 ID、重复/遗漏分类、规划/reasoning/maturity 字段均无效。

## 24. automatic apply

正常 proposal 通过规则校验后原子应用，无逐卡确认；失败不写部分结果。

## 25. attention rules

无证据冲突、无法分类、旧确认卡无事实来源和高风险 merge 进入 capability attention；普通 no-change 不进入 attention。

## 26. dirty / fingerprint

fingerprint 包含全部 fact ID/updatedAt、generation version 和 capability identity/merge state；state 记录 source/covered/assigned/no-change/attention、dirty 和成功/尝试时间。

## 27. job idempotence

等价 scope 复用活动 job；source fingerprint 与 operation fingerprint 防止重复调用和重复 Evolution。

## 28. cancel / retry / restart

沿用持久化 job 的取消检查点、显式 retry 和未知计费状态不自动重放规则。history backfill 期间只累计 dirty，完成后统一刷新。

## 29. failure preservation

PASSED。固定模型故障测试确认已有 READY 变为 READY_STALE，旧能力、关系和 Evolution 完整保留；无旧结果时为 FAILED。

## 30. legacy confirmed migration

0 张真实 CONFIRMED 卡片需要迁移。实现仅在 sourceRefs 可追到 owned ProjectFact 时幂等 seed；无事实来源的确认卡片进入 attention。

## 31. candidate / ignored compatibility

PASSED。8 CANDIDATE 和 5 IGNORED 不迁移、不删除，折叠显示在兼容区且不再提供旧主操作。

## 32. capability interpretation compatibility

旧 README/resume/interview 表达仍可读取和迁移到 legacy-seeded capability，但不替代 ProjectFact 关系。

## 33. API

新增 overview、稳定能力 list/filter/search、detail、evolutions、facts、changes、attention、retry；所有入口校验 userId/projectId ownership，旧 API 保留。

## 34. capability map page

主页面展示完整覆盖、状态、成熟度、稳定能力、近期变化、attention、retry 和 stale 提示；旧卡片仅在兼容折叠区。

## 35. detail page

展示当前版本、成熟度原因、aliases、merge redirect、Evolution 和 ProjectFact→batch→evidence 追溯。

## 36. recent changes

按 occurredAt 分页读取 Evolution，不含下一步、路线图或未发生能力。

## 37. attention UI

只显示异常事实或 merge 风险；普通事实和能力变化无需确认。

## 38. 5000 facts / 100 capabilities performance

PASSED。5000 facts、100 capabilities、1000 evolutions、10000 relations：overview/list/detail/facts/changes P95 分别 20/7/9/4/4ms，查询数 13/4/7/4/3。Timeline 既有门禁另含 300 themes 和单月 230 facts；500 incremental 分为 5 块。

## 39. backend tests

PASSED。最终完整 Maven 套件 297/297 通过，包含 coverage-repair、失败保留、H2 升级、ownership、read model 与性能门禁。

## 40. PostgreSQL

PASSED。PostgreSQL 16 Testcontainers 3/3 通过，包含真实 capability/evolution/relation/coverage/state 持久化和查询；可选 RealDeepSeekIT 1 项因未提供独立测试 Key 为 SKIPPED。

## 41. H2 upgrade

PASSED。文件型旧库升级 fixture 2/2；当前真实 H2 的安全副本完成 schema、42→37/5 重分类、WEEK retry、bootstrap 和 incremental。原库最终字节哈希与任务前一致。

## 42. frontend contracts

PASSED。最终前端契约 44/44 通过。

## 43. Playwright

PASSED。最终完整 7/7 通过，覆盖事实生成、重复分析、能力地图 bootstrap/incremental/失败保护/详情/项目隔离、任务取消/retry 和 Timeline。

## 44. TypeScript

PASSED。最终 `tsc --noEmit` 通过。

## 45. production build

PASSED。最终 Next.js 生产构建通过并生成能力列表和动态详情路由。

## 46. Desktop BAT

PASSED。根目录 `Start-ProjectFlow.bat -NoBrowser` 在本地修改受保护时完成依赖校验、生产构建及 3000/8080 就绪验证；证据版本 3.4.2、Build ID `NklpE8y1f9TlDzAMQDngk`。进程已停止，启动写入后的原始 H2 已恢复为任务前字节哈希。

## 47. sensitive scan

PASSED。未发现 Key、Bearer 凭证、raw prompt、raw response、reasoning 原文或新增个人绝对路径；API 中只存在既有认证头结构和运行时 token 变量。

## 48. real Provider bootstrap

PASSED。安全副本首次严格校验拒绝遗漏 38 facts 的响应；一次 coverage repair 后 2 requests、38713 tokens、282895ms，形成 8 capabilities，42/42 coverage。能力为中文项目语义，无未来建议、泛化模板或 Theme 复制；只持久化 reasoning presence/length 等安全 diagnostics。

## 49. real Provider incremental

PASSED。在隔离副本新增 2 条明确标记的验收 facts：已有 capability `40cbd5b6-aeb8-4cd3-9924-cfc6776aad5b` 保持 ID，version/evolution 1→2；另 1 条为 NO_CAPABILITY_CHANGE。1 request、3609 tokens、34860ms，最终 44/44 coverage、29 assigned、15 no-change。

## 50. merge safety

PASSED。固定模型自动化确认问题和区域不一致的相似名称 merge 进入 attention 且不删除能力；真实用户副本未执行破坏性 merge。

## 51. GitHub Actions

NOT_RUN。

## 52. known risks

能力表仍依赖 `ddl-auto=update`；外部 Provider 仍可能超时或 Schema 漂移；coverage repair 只允许一次且会增加 token。失败保护确保事实和旧 READY 可读。

## 53. next stage

Hermes 与 Obsidian 正式同步，且只能消费 Facts、Timeline 和 Capabilities，不能成为事实源或重写 Evolution。

## 54. key files

核心文件包括 `ProjectCapabilityMapService`、`ProjectCapabilityQueryService`、能力实体/仓库、`ProjectCapabilityController`、能力主页面与详情页、固定模型 E2E 和本报告。

## 55. final commit SHA

NOT_RUN。

## 56. report path

`docs/projectflow-v3.4.2-fact-native-capability-map-report.md`
