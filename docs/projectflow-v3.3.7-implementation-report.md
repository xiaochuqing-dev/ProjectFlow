# ProjectFlow V3.3.7 实施报告

## 1. 背景与目标
建立真实可重复验收，以及可取消、幂等、有界、可解释恢复的后台分析任务。

## 2. 实际完成范围
完成持久化状态、预算、取消检查点、重启恢复、活动任务唯一性、retry 追溯、核心业务 E2E、PostgreSQL workflow、H2 旧库升级和 CI 分层。

## 3. 未完成范围
未进入 V3.3.8、增量能力分析、语义去重、推荐升级、Flyway 全量改造或多实例 lease。真实 DeepSeek 无安全测试 Key，未执行。

## 4. 测试分层
单元/H2 验证逻辑和旧库升级；PostgreSQL 16 Testcontainers 验证真实 service/repository/transaction workflow；Playwright 启动真实前后端和固定兼容模型服务；真实 DeepSeek 仅显式启用。

## 5. PostgreSQL Testcontainers
2 条测试通过。除基础持久化外，完整覆盖 Git 扫描、模型开发推进段、正式建议、沉淀确认、待能力分析、能力候选、卡片确认、失败保留、10 路并发 retry 和取消不写结果。

## 6. H2 旧数据升级
新增文件库重启升级测试：先写入项目、Provider、失败/成功 Job、已确认沉淀、已确认能力和未确认候选，再移除 V3.3.7 Job 字段，以当前应用 `ddl-auto=update` 重启。数据数量、内容、状态和关系保持完整，retry/cancel 可直接使用。

## 7. Playwright 核心流程
4 条测试全部通过：分析批次生成；沉淀处理与本地草稿隔离；沉淀到能力分析及失败保留旧成功；取消与 retry 复用等价活动任务。每条覆盖刷新或切页，使用隔离项目和临时 Git 仓库。

## 8. CI 工作流
核心 job 为 backend/H2、PostgreSQL、frontend、browser E2E 和 sensitive-content；任何失败都会阻断。optional-real-deepseek 仅在手动开关和安全 Key 同时存在时运行。

## 9. 任务状态模型
保持 QUEUED、RUNNING、CANCEL_REQUESTED、CANCELLED、SUCCEEDED、SUCCEEDED_WITH_WARNINGS、FAILED、INTERRUPTED、RETRYABLE、EXPIRED、REJECTED 的独立语义。

## 10. 幂等策略
项目行悲观锁串行化同项目创建。用户、项目、任务类型和输入指纹相同的活动任务只返回同一 jobId。

## 11. retry 最终语义
retry 可以忽略已完成历史，但不能绕过活动任务唯一性。已有 QUEUED/RUNNING/CANCEL_REQUESTED 时直接复用；无活动任务才创建新行，并记录 `retriedFromJobId` 和 `retryReason`。成功任务 retry 返回冲突。

## 12. 并发和队列
10 路并发 retry 只创建 1 个新活动任务；模型调用和队列提交只发生一次。不同输入、项目和任务类型保持隔离。

## 13. 取消实现
排队任务直接取消；运行任务进入 CANCEL_REQUESTED。外部调用、紧凑重试和正式持久化前检查数据库状态。

## 14. 安全检查点
取消后不再发起新模型请求或正式写入；失败的能力分析不覆盖上次成功卡片；已确认内容保留。

## 15. 请求、时间和 token
每个任务默认最多 3 次模型请求、10 分钟、60,000 token；线程池、队列和模型 HTTP 并发都有上限。

## 16. 服务重启恢复
只自动恢复未外部调用的 QUEUED。模型请求状态未知时标记 INTERRUPTED，避免自动重复收费。

## 17. H2 optimistic version 修复
升级测试发现旧表新增 nullable `version` 后，旧行首次 flush 会失败。最终以数据库默认值 0 添加 version；Java 新实体仍保持 null 直到 persist，兼顾旧行升级和 Spring Data 新实体识别。

## 18. 前端任务体验
刷新和离开页面后可恢复任务状态；能力页区分当前成功结果和最近失败；能力卡详情修复无效嵌套 `dt/dd`。

## 19. API 与安全
cancel/retry 继续校验 userId 和 projectId 归属。响应新增 retry 来源信息，不暴露 Key、Authorization、reasoning、请求体或模型原始响应。

## 20. 自动化测试数量
后端/H2 174 项通过；PostgreSQL workflow 2 项通过；前端契约 18 项通过；Playwright 4 项通过。RealDeepSeekIT 1 项因无开关/Key明确 skipped。

## 21. 前端构建
TypeScript、18 项契约测试和 Next.js 16.2.7 production build 通过，生成 21 个静态页面。

## 22. 后端构建
Maven 完整测试通过，Spring Boot 3.5.14，版本 3.3.7。

## 23. PostgreSQL 真实结果
本机 Docker Desktop 已启动，Testcontainers 真实启动 PostgreSQL 16.14，2 条测试通过；没有使用 H2 替代。

## 24. H2 升级结果
旧结构文件库无需删除即可升级；旧成功、失败、沉淀和能力数据未被重写或错误归入新批次；新 retry/cancel 正常。

## 25. Playwright 结果
本机系统 Edge 启动真实隔离前后端，4 项通过，用时约 43 秒。失败时保留 screenshot/trace，CI 另保留视频。

## 26. 真实 DeepSeek
SKIPPED：执行环境没有安全测试 Key，未发起付费调用。固定响应仅证明业务和结构化契约，不证明真实 Provider 的限流、长输出和私有字段。

## 27. CI 信息
GitHub Actions Run [29154153436](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/29154153436) 全部核心 job 通过：backend/H2、PostgreSQL、frontend、browser E2E、sensitive-content 均为 success；optional-real-deepseek 未手动启用，状态为 skipped。

## 28. 已知风险
仍使用 Hibernate `ddl-auto=update`，不是正式版本化迁移系统；单次同步 HTTP 调用只能等待返回或超时；本地 Edge 与 CI Chromium 二进制不同；多实例 worker lease 不在本版本范围。

## 29. 后续建议
后续独立阶段可建立 Flyway baseline 和数据库级活动任务唯一约束；具备安全测试 Key 时手动运行低预算真实 DeepSeek 验收。

## 30. 关键文件
`ProjectAnalysisJob.java`、`ProjectAnalysisJobService.java`、`ProjectAnalysisJobRetryIdempotencyTest.java`、`ProjectFlowPostgresIT.java`、`ProjectFlowH2UpgradeIntegrationTest.java`、`core-workflow.spec.ts`、`fixed-model-server.mjs`、`quality-gates.yml`。

## 31. Commit SHA
正式实现提交：`29f83b052ff68235e714a8051d01a798e102d54d`。

## 32. 报告链接
仓库路径：[projectflow-v3.3.7-implementation-report.md](projectflow-v3.3.7-implementation-report.md)。
