# ProjectFlow V3.4.1 自动项目历程实施报告

## 1. 背景

V3.4.1 在 V3.4.0 自动 ProjectFact 长期记忆之上增加时间 read model，并先补齐 V3.4.0 报告中明确未执行的真实门禁。本文只记录实际实现和实测证据。

## 2. V3.4.0 遗留项

遗留项是完整后端回归、PostgreSQL Testcontainers、Playwright、当前真实 H2 与旧批次迁移、真实 history、桌面 BAT、敏感扫描和 GitHub Actions 核心门禁。

## 3. Gate 0 执行过程

先读取 V3.4.0 报告和现有协议，执行本地全套门禁、读取真实数据库、复制后验证幂等与 checkpoint、运行桌面入口并检查远程 CI；发现的失败均在原边界内修复后重跑。

## 4. Gate 0 发现的真实 Bug

初始 CI Run 29435440657 的 PostgreSQL 和浏览器任务失败。原因包括固定模型不识别 Timeline Schema、失败注入跨任务误消费、Playwright 硬编码机器 Maven 路径、共享固定模型状态并发干扰及旧 E2E 仍把自动事实当作人工沉淀。真实 H2 还暴露了启动迁移重复派发刷新、未知模型请求重启风险、旧可空模型列表、过长 PostgreSQL 幂等键，以及历史末端无差异提交永久停在 checkpoint 的问题。

## 5. Gate 0 修复

固定模型按任务返回正确 Schema，E2E 改为跨平台命令和单 worker；启动迁移合并刷新事件，持久化 job 使用新事务和有界键；未知模型请求只标记中断不自动重放；旧列表 null-safe。无差异历史提交现在只推进 checkpoint，不调用模型、不生成伪事实。

## 6. 完整 backend suite

状态：PASSED。2026-07-16 最终执行 284 tests，failures 0、errors 0、skipped 0，耗时 93.9 秒。

## 7. PostgreSQL Testcontainers

状态：PASSED。PostgreSQL 16.14 Testcontainers 的 ProjectFlowPostgresIT 2/2 通过；可选 RealDeepSeekIT 因没有独立安全测试 Key 为 SKIPPED 1。

## 8. Playwright Gate 0

状态：PASSED。最终 6/6，通过自动事实、批次继续分析、能力前置条件、任务取消/retry，以及 Timeline A-H；其中前四项覆盖 V3.4.0 主流程。

## 9. 真实 H2 安全验收方式

状态：PASSED。桌面链实际升级当前文件型 H2；涉及迁移重跑、Provider 和 history 的写入性验收均先复制数据库，在副本运行同一应用与真实 Git，避免破坏用户原库。

## 10. 真实 batch migration 核对表

状态：PASSED。以下是当前真实 H2 的 11 个旧批次，均为 FACTS_RECORDED_WITH_ATTENTION；“覆盖”是该批事实去重 commit 引用数。

| Batch | commits | files | segments | facts | attention | 覆盖 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| c50023e5 | 32 | 155 | 8 | 8 | 8 | 22 |
| 60b423cd | 20 | 97 | 3 | 3 | 3 | 15 |
| 55b59635 | 15 | 72 | 3 | 3 | 3 | 11 |
| 8ca119a9 | 13 | 62 | 5 | 5 | 5 | 9 |
| edf36875 | 11 | 62 | 5 | 5 | 5 | 8 |
| 5ff50930 | 7 | 45 | 4 | 4 | 4 | 6 |
| 188b36cb | 4 | 33 | 3 | 3 | 3 | 4 |
| dc9cc5b2 | 1 | 5 | 2 | 2 | 2 | 1 |
| b030cd92 | 30 | 124 | 3 | 3 | 3 | 24 |
| 96859f1b | 30 | 120 | 3 | 3 | 3 | 22 |
| 322ed50b | 30 | 110 | 3 | 3 | 3 | 24 |

## 11. facts 数量

当前真实 H2 为 42 个 ProjectFact；文件型旧库自动升级与重复启动后数量保持 42。

## 12. attention 数量

当前真实 H2 为 42 个 NEEDS_ATTENTION。这是旧证据质量的保守迁移结果，不阻塞批次完成、事实读取或 Timeline。

## 13. duplicate 数量

当前真实 H2 的 fact 总数与 distinct fingerprint 都是 42，duplicate 为 0；history 副本结束后为 46/46，仍为 0。

## 14. commit coverage

当前真实 H2 有 56 个去重 ProjectFact commit 引用。批次间可能引用同一 commit，因此不能把上表逐行覆盖数直接相加冒充全局覆盖。

## 15. real history backfill

状态：PASSED。基于当前真实 H2 安全副本和 ProjectFlow 真实 Git 运行，观察到总历史 133 commits，三个成功 bounded chunk 后到达 132/133，最后无差异提交修复后完成 133/133。

## 16. checkpoint / restart

状态：PASSED。现场 checkpoint 曾为 completedChunkCount 3、remaining 1；retry 后 requestCount 0、completedChunkCount 4、remaining 0。再次重启仍为 COMPLETED 133/133，facts 保持 46、distinct fingerprint 46。

## 17. Desktop BAT

状态：PASSED。最终实际执行根目录 Start-ProjectFlow.bat，保护有本地改动的工作树，完成 V3.4.1 前端构建、Spring Boot/H2、3000/8080 就绪与登录入口；证据 Build ID 为 JKUJ0KVv8PmiP4Nb2QH1I。

## 18. sensitive scan

状态：PASSED。检查明显 API Key、Authorization 值、真实 H2、reasoning/raw response/full prompt、用户绝对路径、临时 Git fixture 和 Playwright trace；发现并脱敏 10 处历史文档机器路径，未发现 trace 或密钥值。

## 19. Gate 0 CI

状态：FAILED。基线 commit 4bff544 的 Run 29435440657 中 backend、frontend、sensitive 成功，postgres、browser 失败，optional-real-deepseek 为 SKIPPED；失败已在本地修复并通过，V3.4.1 推送后的核心 CI 当前为 NOT_RUN。

## 20. V3.4.1 产品目标

用户打开项目历程即可读取已经发生的事实、确定性统计、自动阶段摘要与可追溯主题，不需要保存、确认或决定下一步。

## 21. Timeline 定义

Timeline 回答项目在某日、ISO 周、月份和完整生命周期实际发生了什么。它不等于日报、工时、任务计划或路线图。

## 22. factEventAt

primary assignment 使用 occurredTo，缺失时回退 occurredFrom。同一事实只属于一个 primary period；详情保留 occurredFrom/occurredTo 完整范围，生命周期不重复计数。

## 23. timezone

TimelinePeriodResolver 集中使用 ZoneId.systemDefault，可由 projectflow.timeline.zone 或 PROJECTFLOW_TIMELINE_ZONE 覆盖；API 返回 timelineZone，测试显式注入固定时区。

## 24. Timeline Read Model

ProjectFact 是唯一事实真相。Timeline summary、theme 和关系是可删除重建的派生数据，不能反向覆盖、删除或批量替换事实。

## 25. deterministic stats

事实、批次、去重 commit、文件、Agent result、关注项和时间范围均由数据库 count/group/min/max 聚合；模型不提供事实统计。

## 26. ProjectTimelineSummary

持久化 granularity、period key、source fingerprint、source/covered fact count、状态、摘要、成功版本与失败诊断；刷新期间可继续读取上次 READY 内容。

## 27. ProjectTimelineTheme

Theme 是某个 summary 内的期间局部演进主题，可替换、可重建，不是 Project Capability。

## 28. Theme → Fact

ProjectTimelineThemeFact 保存主题到 ProjectFact 的关系；Fact 继续追溯 batch、commit、Agent result、文件和 evidence。

## 29. DAY

DAY key 为 YYYY-MM-DD，直接分页读取 facts 与确定性统计，默认不创建模型任务。

## 30. WEEK

WEEK key 使用 ISO week-based year 的 YYYY-Www，范围是周一到周日；全部周期 facts 进入自动阶段摘要。

## 31. MONTH

MONTH key 为 YYYY-MM，是前端默认视图；展示统计、摘要、主题和主题事实入口，全部当月 facts 必须被覆盖。

## 32. LIFECYCLE

LIFECYCLE key 为 ALL，按月份统计与月摘要分层综合，阶段 monthKeys 最终展开回完整事实关系，不把全历史一次塞给模型。

## 33. Timeline model task

新增 period 与 lifecycle ModelTaskType，实际调用继续统一经过 ModelGateway、Provider capability、动态参数、请求/token/时限预算和安全诊断。

## 34. output schema

Period 输出 periodSummary、themes(title/summary/factIds) 与 ungroupedFactIds；Lifecycle 输出 periodSummary、stages(title/summary/monthKeys) 与 ungroupedMonthKeys。

## 35. coverage validation

未知、跨项目、非法或遗漏 ID 会使生成失败。重复 ID 只接受首次归属；只有 coveredFactCount 等于 sourceFactCount 才能成为 READY。

## 36. 230 facts coverage

状态：PASSED。单周期上限为 120 facts，230 facts 使用 2 个 chunk 加 1 次综合，共 3 次固定模型请求，验证 230/230，最早事实未丢失。

## 37. dirty / fingerprint

排序后的 fact ID 与 updatedAt 形成 source fingerprint。相同 fingerprint 不重算；变化时标 DIRTY，同时保留上次成功摘要供读。

## 38. auto refresh

持久化 PROJECT_TIMELINE_REFRESH job 合并 WEEK、MONTH、LIFECYCLE scope；同一有效输入复用活动 job，Provider 配置后恢复 WAITING_FOR_MODEL。

## 39. after-commit event

ProjectFact 事务提交成功后才发布事件；监听器在事务外标 dirty 和排队，事实写入不等待模型网络调用。

## 40. history backfill interaction

history chunk 期间只累计受影响周期；每个 chunk 不重建整个 lifecycle，历史完成后再恢复队列。无差异提交只推进独立 checkpoint。

## 41. job idempotence

同用户、项目、PROJECT_TIMELINE_REFRESH 和输入 fingerprint 只允许一个活动任务；幂等键保持在 PostgreSQL 字段上限内。

## 42. cancel / retry / restart

沿用持久化 job 的取消、来源 retry、预算与恢复语义。没有外部请求的排队任务可恢复；模型请求状态未知时标记中断，禁止自动重放和重复计费。

## 43. old READY preservation

生成先写新版本，成功后短事务替换主题；失败、取消或中断只更新状态/诊断，上次 READY 摘要和 facts 始终可读。

## 44. Timeline API

已实现 overview、period list/detail、theme facts、lifecycle 与 retry。每个入口同时校验 userId、projectId、period key、theme ownership 与分页边界。

## 45. 项目历程页面

主导航“每日回顾”已替换为“项目历程”；/timeline 默认月视图，可切日、周、月、全部，显示历史覆盖、stale/waiting/failed 状态并进入主题 facts。

## 46. DevLog compatibility

DevLog entity、API、/dev-logs 与旧输出路径保留；Timeline 不创建“保存为当天记录”、下一步建议或摘要确认入口。

## 47. backend tests

状态：PASSED。完整 H2 suite 284/284；Timeline 定向覆盖时间边界、Schema/安全、读模型、230 facts、job/失败保护、H2 升级与性能。

## 48. frontend contracts

状态：PASSED。39/39；其中 Timeline 专项 15 项，覆盖 DTO、标签、导航、兼容、项目切换、失败保留和无 daily/规划动作。

## 49. Playwright

状态：PASSED。6/6，耗时 57.5 秒；Timeline A-H 包含边界、自动摘要、追溯、切换、失败保护、兼容入口和 history 期间可读。

## 50. 5000 facts / 36 months performance

状态：PASSED。5000 facts、100 batches、36 months、300 themes、最大月 230 facts：overview P95 1 ms/7 queries，period list P95 2 ms/6，detail P95 6 ms/11，lifecycle P95 21 ms/15；查询数不随主题逐条增长。

## 51. H2 final result

状态：PASSED。旧库原地升级、迁移重跑、Timeline assignment、摘要状态、history checkpoint/restart 与 duplicate 检查均完成；当前 facts 42、attention 42、duplicates 0。

## 52. PostgreSQL final result

状态：PASSED。PostgreSQL 16.14 Testcontainers 2/2，验证 Timeline 表、唯一约束、事实事务、派生摘要及旧兼容流程。

## 53. production build

状态：PASSED。TypeScript noEmit 通过，Next.js 16.2.7 生产构建通过，静态生成 22 页并包含 /timeline 与 /dev-logs。

## 54. Desktop BAT final result

状态：PASSED。最终代码工作树实际启动成功，版本 3.4.1、Build ID JKUJ0KVv8PmiP4Nb2QH1I，健康检查和登录页均就绪。

## 55. sensitive scan

状态：PASSED。最终扫描未发现明显密钥、Bearer 值、用户绝对路径、完整任务提示词或 Playwright trace；真实/复制 H2、测试 fixture 和报告证据均未进入提交范围。

## 56. GitHub Actions final Run

状态：NOT_RUN。V3.4.1 尚未推送；推送后必须等待 backend-unit-and-h2、postgres-integration、frontend-quality、browser-e2e、sensitive-content 全部成功，再把 Run 与 job 结果写回本节。

## 57. real Timeline Provider validation

状态：PASSED。当前已配置 Provider 的隔离副本实测 WEEK、MONTH、LIFECYCLE 小样本均生成中文、无规划内容、1/1 完整覆盖；当前真实 H2 的 MONTH 与 LIFECYCLE 为 READY 42/42。另有两个 WEEK 在真实 Provider 错误/中断后保持 FAILED 且 facts 可读，证明失败隔离；未重复全入口 DeepSeek 压力矩阵。

## 58. 未重构的 V3.4.0 / V3.3.8 基础设施

ProjectFact、FactCursor、bounded history 和 DevelopmentSegment 边界保持；V3.3.7 job executor 与 V3.3.8 ModelGateway 未重写，只增加 Timeline task、预算、调用方和已证明必要的可靠性修复。

## 59. known risks

Hibernate ddl-auto=update 仍不是版本化迁移；外部 Provider 仍可能限流、漂移或中断；两个真实 WEEK 摘要需用户需要时 retry。Timeline Theme 不是完整生命周期能力地图。

## 60. next stage

后续可在 ProjectFact 与 Timeline 证据基础上设计 fact-native 生命周期能力地图，再独立评估 Hermes/Obsidian 正式同步；本轮未提前实现。

## 61. key files

关键实现包括 ProjectFact、Timeline 实体与 repositories、TimelinePeriodResolver、ProjectTimelineService、ProjectTimelineSummaryService、ProjectTimelineJobScheduler、ProjectFactIngestionService、ProjectFactHistoryService、ProjectTimelineController、frontend/src/app/timeline/page.tsx、frontend/src/lib/project-timeline.ts 与 docs/project-timeline.md。

## 62. final commit SHA

状态：NOT_RUN。代码提交尚未创建；准确实现 SHA 在首次推送后写回，避免在同一提交中制造自引用值。

## 63. report path

docs/projectflow-v3.4.1-automatic-project-timeline-report.md
