# Phase 3: PostgreSQL 与 H2 升级验证
Parent PRD: [V3.3.7 正式收尾](../prd-v3-3-7-finalization.md)
Status: Complete
Last Updated: 2026-07-11

## Objective
证明真实 PostgreSQL workflow 和从最小 V3.3.6 H2 schema/data 到当前应用的升级行为。

## Implementation Checklist
- [x] PostgreSQL 测试走真实 service/repository/transaction workflow。
- [x] 覆盖任务状态分支、取消不落正式结果、retry 幂等和并发唯一性。
- [x] 覆盖沉淀确认、待能力分析、能力候选/确认与关系重读。
- [x] 用 V3.3.6-like 文件库建立旧结构，再以当前应用 update schema。
- [x] 验证旧项目、Provider、任务、沉淀、已确认和未确认能力卡片保持完整，并可使用 cancel/retry。

## Validation Checklist
- [ ] `mvn -q test`
- [ ] `mvn -Ppostgres-it verify`
- [ ] Docker 不可用时如实记录，CI 不允许静默跳过。

## Exit Criteria
- [ ] PostgreSQL workflow 与 H2 升级验收满足提示文件清单。
