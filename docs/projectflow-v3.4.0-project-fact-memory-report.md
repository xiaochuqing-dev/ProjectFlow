# ProjectFlow V3.4.0 自动项目事实与项目记忆实施报告

## 1. 背景

V3.3.8.1 已能可靠分析开发变化，但分析后仍要求开发者逐条确认沉淀，形成额外机械审核。

## 2. 废除逐条确认主链

正常、证据充分的开发推进段现在直接写入 ProjectFact；人工确认不再是新事实主链的一部分。

## 3. 用户痛点

用户完成开发后只需点击“分析新变化”即可离开，ProjectFlow 自动保存事实并推进游标。

## 4. 新产品定位

ProjectFlow 自动维护项目从创建至今的长期记忆，把 Git、worktree 与 Agent result 原始证据整理为批次化项目事实。

## 5. V3.3.8.1 原主链

原流程在 DevelopmentSegment 后创建 ProjectChange，进入新建、合并、补证据、忽略的人工四选一处理。

## 6. V3.4.0 新主链

分析新变化 → DevelopmentSegment → ProjectFactIngestionService → ProjectFact → FactCursor → 项目记录与项目记忆。

## 7. 保持稳定的基础设施

没有重新设计 V3.3.7 后台任务基础设施、V3.3.8 ModelGateway、动态模型参数、Schema repair、截断恢复和 V3.3.8.1 Dashboard Bootstrap。

## 8. ProjectFact 数据模型

事实保存来源批次与推进段、标题与客观摘要、发生时间、commit/Agent/file/evidence 引用、质量、置信度、记录状态、关注原因和稳定指纹；commit 与 Agent result 另有规范化映射表。

## 9. DevelopmentSegment 与 ProjectFact 边界

DevelopmentSegment 是一次分析的过程产物；ProjectFact 是不可因后续分析而批量替换的长期事实。

## 10. ProjectFact ingestion

统一写入服务在同一事务内锁定批次、幂等写入 facts、更新批次统计并在成功后推进增量 FactCursor。

## 11. MODEL fact 规则

MODEL、有效证据、有效标题摘要且最终质量 PASS 的推进段自动记为 RECORDED。

## 12. partial model fact 规则

compact retry 或截断恢复本身不强制关注；最终条目边界、证据和质量完整时仍可自动记录。

## 13. LOCAL_RULE 与 Agent result 规则

LOCAL_RULE 有 Git/file 证据可自动记录；Agent result 绑定代码证据时可记录，只有 Agent result 或无有效证据时进入 NEEDS_ATTENTION。

## 14. NEEDS_ATTENTION

标题摘要缺失、无有效证据、仅 Agent result、质量非 PASS 或发生时间降级会记录明确原因；它不阻塞同批其他事实、批次完成和游标推进。

## 15. factFingerprint 与幂等

SHA-256 指纹由 project、batch、source segment 和排序后的 commit、Agent result、evidence 引用构成，不依赖标题相似度；数据库唯一约束与批次行锁共同保证并发幂等。

## 16. FactCursor

FactCursor 独立保存最后已成功记录事实的 commit、时间、分支和批次。

## 17. ProjectReviewCursor 兼容

无 FactCursor 时只用旧 ReviewCursor 初始化一次；后续增量边界只由 FactCursor 决定。

## 18. Cursor 自动推进

事实 flush、批次统计与 cursor 更新处于同一事务；attention 不阻塞推进，事实写入失败不会提前推进。

## 19. 旧 DevelopmentSegment migration

启动迁移直接把已有推进段幂等转换为事实，不重新调用模型；时间缺失时回退批次时间并标记关注。

## 20. 旧 ProjectSediment migration

有 source segment 的沉淀链接到对应事实；无 segment 但有证据的沉淀可生成 legacy fact；无证据数据不会伪装为普通事实。

## 21. 旧 ProjectChange 兼容

旧表、旧 API 和待处理数据保留，但新扫描不再创建 ProjectChange suggestion，也不再影响 FactCursor。

## 22. 项目记录页面

原沉淀处理页改为按长期月份分组的项目记录页，批次卡显示事实、关注、提交、文件和 Agent result 数量。

## 23. 批次详情页面

详情分页展示事实摘要并按需加载完整证据；正常事实没有四选一下拉框或确认按钮。

## 24. 项目记忆页面

主区域展示事实总量、commit coverage、最早/最近事实、最近事实和历史补齐状态；旧沉淀与旧 ProjectMemory 放入兼容区域。

## 25. 历史事实重建

新增 PROJECT_FACT_HISTORY_REBUILD 任务，独立于增量扫描自动处理未覆盖 Git 历史。

## 26. 历史 chunk 策略

每个 chunk 最多 25 commits，oldest-to-newest；已由事实覆盖的 commit 在模型调用前跳过。Git 输出改为并行排空，避免 100+ commit 输出填满管道后超时。

## 27. HistoryState 与 checkpoint

持久化 head snapshot、总量、覆盖量、剩余量、最后 commit、chunk 数、最后 batch 和错误摘要。

## 28. 自动触发策略

增量事实成功后发布轻量事件；有模型时创建后台任务，无模型时仅记录 WAITING_FOR_MODEL，GET 接口不会触发付费调用。

## 29. 重启、取消与 retry

复用现有持久化 job、预算和取消检查；运行状态不明的历史任务恢复为 PAUSED，已完成 facts 与 checkpoint 保留。

## 30. 新事实读取 API

新增 facts 分页/详情、fact-memory-overview、fact-history-state、项目记录批次列表/详情 API，均执行用户与项目归属校验。

## 31. 自动化测试

收尾实际通过 20 个定向后端测试：ProjectFactMemory 6、HistoryService 1、H2 upgrade 2、DataReadReliability 4、WorkSessionScan 7。后端 test-compile 通过；完整 backend test suite 因用户要求立即收尾未重新执行。

## 32. Playwright

测试场景已改造为项目记录、自动事实和旧沉淀兼容语义，但本次收尾未执行 Playwright，状态为 NOT_RUN。

## 33. 1000 / 5000 facts 性能

H2 定向测试实测：1000 facts 首屏 109 ms/3 queries；5000 facts 首屏 16 ms/3 queries；5000 facts overview 37 ms/4 queries；100 batches 列表 14 ms/3 queries；50 facts 批次详情 13 ms/4 queries。

## 34. H2 升级结果

临时文件型 H2 的 V3.3.8.1 batch/segment/sediment fixture 原地升级通过，事实迁移重复执行不重复；当前用户真实 H2 与桌面启动未在本次紧急收尾执行。

## 35. PostgreSQL 结果

PostgreSQL Testcontainers 用例已更新为自动事实与旧沉淀兼容链，但本次收尾未执行，状态为 NOT_RUN，需以 GitHub Actions 为准。

## 36. 前端 build

24/24 contract tests、TypeScript noEmit 和 Next.js 16.2.7 production build 均通过。

## 37. CI Run

提交前无可记录的本次 CI Run；推送后由 GitHub Actions 执行，报告不预写通过。

## 38. 真实 DeepSeek

未重新执行 V3.3.8 全入口压力矩阵，因为本轮没有重新设计 ModelGateway；固定模型只用于业务契约，不代表真实 DeepSeek。

## 39. 已知风险

本次未完成 Playwright、PostgreSQL、本机真实 H2、桌面 Start-ProjectFlow.bat、完整后端回归和敏感内容扫描；ddl-auto=update 仍不是版本化迁移体系。

## 40. 下一阶段建议

先补齐上述门禁并观察真实历史回填，再基于 ProjectFact 实施 timeline 与 fact-native capability map；本阶段不提前实现 Hermes/Obsidian 正式同步。

## 41. 关键文件

关键实现位于 ProjectFact 实体与仓库、ProjectFactIngestionService、ProjectFactHistoryService、ProjectFactMigrationService、ProjectFactController、WorkSessionScanService，以及项目记录/项目记忆页面。

## 42. 最终 commit SHA

本报告随 V3.4.0 收尾提交发布；准确 SHA 以 GitHub master 最新提交为准，避免在提交内容中制造自引用 SHA。

## 43. 报告链接

仓库内路径：`docs/projectflow-v3.4.0-project-fact-memory-report.md`。
