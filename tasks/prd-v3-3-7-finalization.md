# PRD: ProjectFlow V3.3.7 正式收尾

## Document Status
- Status: Complete
- File Mode: Split
- Current Phase: Complete
- Active Phase File: [Phase 5](./prd-v3-3-7-finalization/phase-05-release-evidence.md)
- Context File: [context.md](./prd-v3-3-7-finalization/context.md)
- Last Updated: 2026-07-11
- PRD File: `tasks/prd-v3-3-7-finalization.md`

## Problem
V3.3.7 已具备持久化任务和分层测试基础，但 retry 可绕过等价活动任务检查，浏览器、PostgreSQL 与 H2 验收仍不足以证明核心业务闭环和旧库升级行为。

## Goals
- G-1: 所有入口都复用同用户、项目、任务类型和输入指纹相同的活动任务。
- G-2: 用独立 Playwright 场景证明分析批次、沉淀确认、能力分析和取消/retry。
- G-3: 用真实 PostgreSQL 16 workflow test 和更接近 V3.3.6 的 H2 升级测试证明持久化兼容。
- G-4: 完成本地与远程质量门禁、文档、报告、提交和推送。

## Non-Goals
- NG-1: 不实现 V3.3.8、增量能力分析、语义去重、推荐升级、Flyway 全量改造或多实例 lease。

## Success Criteria
- SC-1: retry 和 10 路并发 retry 均只得到一个等价活动 job。
- SC-2: Playwright 至少 3 条独立核心测试，覆盖刷新、切页和持久状态。
- SC-3: PostgreSQL workflow integration 与 H2 旧库升级测试通过。
- SC-4: 后端、前端、Playwright、敏感内容和远程 GitHub Actions 核心任务全部通过；真实 DeepSeek 无 Key 时明确 SKIPPED。

## Requirements
### Functional Requirements
- FR-1: retry 不复用已完成结果，但不得绕过活动任务唯一性，并保留来源 job 关系。
- FR-2: 已成功任务 retry 返回冲突；不同指纹、项目和类型仍可分别创建。
- FR-3: 分析批次、正式建议、本地草稿、沉淀确认和能力输入关系均可在刷新后读取。
- FR-4: 取消后不增加模型请求、不进入正式持久化。

### Non-Functional Requirements
- NFR-1: 不输出或提交 Key、Authorization、reasoning 原文、模型原始响应和测试临时数据。
- NFR-2: 保持现有服务边界，优先最小字段、查询和测试补丁。
- NFR-3: Mock/固定响应只描述为 E2E 测试模型，不冒充真实 DeepSeek。

## Discovery Summary
- Reviewed: 任务 service/repository/entity、现有 3 个任务测试、Playwright 配置和单一 spec、PostgreSQL/H2 测试、CI workflow、V3.3.7 文档。
- Current system: 项目行悲观锁可串行化同项目创建；正常创建检查活动任务，retry 使用 `force` 跳过检查；本地 HTTP Provider 已被 SSRF 守卫允许用于测试。
- Validation surface: Maven unit/H2、Failsafe Testcontainers、Next lint/build/contracts、Playwright 真实前后端、GitHub Actions。
- Design implication: 复用现有项目锁和活动查询；测试模型作为 Playwright 第三个 webServer；不引入新业务 API。

## Assumptions
- A-1: 当前 `master` 是用户要求的实现基线，工作树初始干净。
- A-2: 允许按提示提交、推送并检查 GitHub Actions。
- A-3: 无安全 DeepSeek Key 时不执行真实付费调用。

## Phase Index
| Phase | Status | Objective | Validation Focus | File |
|---|---|---|---|---|
| 1. 任务幂等 | Complete | 修复 retry 唯一性和追溯 | 单元/并发测试 | [phase-01](./prd-v3-3-7-finalization/phase-01-job-idempotency.md) |
| 2. 核心 E2E | Complete | 覆盖三条业务闭环及任务可靠性 | Playwright | [phase-02](./prd-v3-3-7-finalization/phase-02-core-e2e.md) |
| 3. 数据库升级 | Complete | 强化 PostgreSQL workflow 与 H2 旧库升级 | Testcontainers/H2 | [phase-03](./prd-v3-3-7-finalization/phase-03-database-verification.md) |
| 4. 质量门禁 | Complete | 完整本地验证并修复失败 | 全量命令 | [phase-04](./prd-v3-3-7-finalization/phase-04-quality-gates.md) |
| 5. 发布证据 | Complete | 同步文档、报告、Git 与 CI | 文档/远程 CI | [phase-05](./prd-v3-3-7-finalization/phase-05-release-evidence.md) |

## Final Multi-Pass Review After All Phases
- [x] Requirements and acceptance criteria covered.
- [x] Cross-phase workflow and state transitions verified.
- [x] Simplicity, duplication, security, privacy and performance reviewed.
- [x] Temporary files and sensitive content removed.
- [x] Documentation, reports, commit SHA and CI evidence updated.
- [x] PRD closed as Complete.

## Change Log
- 2026-07-11: 基于正式收尾提示和当前 master 建立执行 PRD。
- 2026-07-11: 实现、本地门禁、master 推送和远程 CI 全部完成。
