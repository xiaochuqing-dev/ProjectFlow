# ProjectFlow V3.3.7 实施报告

## 1. 背景与目标
建立真实可重复验收、CI 阻断门禁，以及可取消、幂等、有界、可解释恢复的后台分析任务。

## 2. 实际完成范围
完成任务状态与诊断扩展、并发幂等、队列限制、取消检查点、请求/时间/token 预算、重启恢复、前端取消和重跑体验、PostgreSQL/H2/Playwright/CI 测试层及版本文档。

## 3. 未完成范围
未进入增量能力分析、语义去重、推荐算法或全面 UI 改造。未完成真实 DeepSeek 联调，原因是执行环境没有安全可用的测试 Key。

## 4. 测试分层
单元/H2 验证逻辑与兼容；Testcontainers 验证 PostgreSQL；Playwright 启动真实前后端；DeepSeek 仅显式启用。

## 5. PostgreSQL Testcontainers
新增 PostgreSQL 16 容器测试，覆盖项目、Provider、Job、已确认沉淀和能力卡片持久化及取消状态。容器由测试生命周期自动清理。

## 6. H2 旧数据升级
新增旧 Job 缺少可靠性字段的兼容测试；字段保持数据库可空，加载时补 2 次尝试、3 次请求、10 分钟、60,000 token 等安全默认值，无需清库。

## 7. Playwright 核心流程
真实创建项目、刷新、切页、绑定本地仓库、10 次并发相同扫描、单 jobId、取消、requestCount 停止增长、刷新后仍显示取消状态。

## 8. CI 工作流
GitHub Actions 分为后端/H2、PostgreSQL、前端、Playwright、敏感内容和可选真实 DeepSeek。核心 job 失败会阻断。

## 9. 任务状态模型
支持 QUEUED、RUNNING、CANCEL_REQUESTED、CANCELLED、SUCCEEDED、SUCCEEDED_WITH_WARNINGS、FAILED、INTERRUPTED、RETRYABLE、EXPIRED、REJECTED。

## 10. 幂等策略
SHA-256 输入指纹结合用户、项目和任务类型；创建时锁定项目行。相同活动输入返回同一 jobId，E2E 的 10 次并发请求已验证。

## 11. 并发和队列
默认 2 核心线程、4 最大线程、16 队列、20 全局活动任务、4 个并发模型 HTTP 请求；队列满记录 REJECTED，未发起模型请求。

## 12. 取消实现
排队任务直接取消；运行任务先标记 CANCEL_REQUESTED。Git、GitHub、模型前后和正式持久化前均读取数据库状态。

## 13. 安全检查点
取消后不再发起新模型请求、紧凑重试或正式写入；能力候选替换与沉淀建议创建前均检查。旧成功结果和已确认内容保留。

## 14. 总请求、总时间和 token
每个任务默认最多 3 次模型请求、10 分钟、60,000 token；诊断返回累计请求、输入/输出/总 token 和已耗时。

## 15. 重试策略
模型网关只重试瞬时网络、可恢复 5xx、限流和受限紧凑重试。401/403、取消、配置错误、保存失败和预算耗尽不重试。

## 16. 服务重启恢复
未开始的 QUEUED 自动重新排队；模型前中断标为 RETRYABLE；模型可能已发送标为 INTERRUPTED，需用户明确重跑，防止重复计费。

## 17. 心跳和租约
阶段推进与模型用量记录更新 heartbeatAt；当前为单机语义，version 提供乐观并发边界，未宣称完成多实例租约调度。

## 18. 前端任务体验
排队显示前方任务数和未产生费用；运行可取消；中断、过期、拒绝和失败可重新运行；轮询失败指数退避，页面隐藏降低频率，返回立即刷新。

## 19. API 与安全
新增 cancel/retry API，沿用 userId 和项目归属校验。响应不包含 Key、Authorization、reasoning 原文、请求体或模型原始响应。

## 20. 自动化测试数量
后端 168 项通过；前端契约 18 项通过；Playwright 1 项通过；PostgreSQL 为独立 failsafe 测试。

## 21. 前端构建
Next.js 16.2.7 生产构建和 TypeScript 通过，生成 21 个静态页面。

## 22. 后端构建
Maven test 通过，Spring Boot 3.5.14，版本 3.3.7。

## 23. PostgreSQL 真实测试结果
本机已真实执行 Testcontainers 命令，但 Docker Desktop 未启动，测试明确失败为“Could not find a valid Docker environment”，未使用 H2 冒充。远程 CI Docker 结果见本报告后续 CI 信息。

## 24. H2 升级结果
旧行安全默认值测试与完整 H2 套件通过；已确认数据逻辑未改变。项目仍使用 ddl-auto update，缺少 Flyway 是已知风险。

## 25. Playwright 结果
本机系统 Edge 启动真实隔离前后端，1 项通过，用时约 15 秒；失败配置保留截图、trace，CI 另保留视频。

## 26. 真实 DeepSeek
未完成真实 DeepSeek 联调，原因是执行环境没有安全可用的测试 Key。Mock 和固定响应未描述为真实联调。

## 27. CI 信息
工作流文件：`.github/workflows/quality-gates.yml`。推送后的远程运行链接在最终交付或后续报告提交中记录。

## 28. 已知风险
ddl-auto 缺少版本化审计；同步 HTTP 当前请求只能等待返回或超时；本地 Edge 与 CI Chromium 二进制不同；多实例 worker lease 尚未实现。

## 29. 后续建议
在 CI 稳定后引入 Flyway 基线和数据库唯一活动任务约束；具备安全测试 Key 时手动运行低预算 DeepSeek workflow。

## 30. 关键文件
ProjectAnalysisJob.java、ProjectAnalysisJobService.java、ProjectAnalysisJobRunner.java、AsyncTaskConfig.java、quality-gates.yml、ProjectFlowPostgresIT.java、core-workflow.spec.ts、playwright.config.ts。

## 31. Commit SHA
核心实现提交：`6fbba3f`。

## 32. 报告链接
仓库路径：`docs/projectflow-v3.3.7-implementation-report.md`。
