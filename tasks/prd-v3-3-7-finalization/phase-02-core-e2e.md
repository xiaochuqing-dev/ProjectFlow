# Phase 2: 核心业务 Playwright
Parent PRD: [V3.3.7 正式收尾](../prd-v3-3-7-finalization.md)
Status: Complete
Last Updated: 2026-07-11

## Objective
将浏览器验收从单一任务烟雾扩展为分析批次、沉淀闭环、能力分析和任务可靠性。

## Phase Discovery Gate
- [ ] 复核 dashboard、sediment-review、capabilities 页面 DOM 和 API。
- [ ] 复核固定模型响应所需 JSON 契约。
- [ ] 复核测试 repo 和 H2 数据清理。

## Implementation Checklist
- [x] 增加固定 OpenAI-compatible 测试服务并明确非真实 DeepSeek。
- [x] 增加分析新变化与批次生成测试。
- [x] 增加沉淀确认闭环测试。
- [x] 增加沉淀到能力分析及失败保留旧成功结果测试。
- [x] 扩展任务取消/retry 幂等测试。

## Validation Checklist
- [ ] `npm run test:e2e`
- [ ] 每条测试独立、可重复、覆盖刷新和切页。
- [ ] 失败证据保留，成功后临时数据清理。

## Exit Criteria
- [ ] 至少 3 条核心 Playwright 测试全部通过。
