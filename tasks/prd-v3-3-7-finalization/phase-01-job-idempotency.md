# Phase 1: 任务 retry 幂等
Parent PRD: [V3.3.7 正式收尾](../prd-v3-3-7-finalization.md)
Status: Complete
Last Updated: 2026-07-11

## Objective
拆分“忽略历史成功结果”和“活动任务唯一性”，让所有创建和 retry 入口统一复用活动 job。

## Phase Discovery Gate
- [x] 检查 `ProjectAnalysisJobService`、repository、entity、controller。
- [x] 检查现有 service tests 和 PostgreSQL 测试。
- [x] 确认项目悲观锁可序列化同项目并发创建。

## Implementation Checklist
- [x] 移除 retry 绕过活动任务检查的 force 语义。
- [x] 记录 retry 来源 job 和原因并通过响应可追溯。
- [x] 覆盖活动 RUNNING/QUEUED/CANCEL_REQUESTED、无活动、成功冲突和输入隔离。
- [x] 覆盖 10 路并发 retry 只创建一个活动 job。
- [x] 验证模型执行次数和队列提交次数不因复用增加。

## Validation Checklist
- [x] 定向与完整 Maven 测试通过。
- [x] 相关 controller/ownership 回归通过。
- [x] 安全与竞态复核完成。

## Exit Criteria
- [x] retry 永不绕过活动任务唯一性。
- [x] 追溯字段安全持久化和读取。
