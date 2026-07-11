# ProjectFlow V3.3.7 正式收尾报告

## 1. 收尾背景
V3.3.7 已有后台任务与分层测试基础，但 retry 仍可绕过活动任务唯一性，浏览器、PostgreSQL 和 H2 验收未覆盖完整产品闭环与真实旧库升级。

## 2. 收尾前发现的问题
- retry 使用 force 语义跳过活动任务检查。
- Playwright 只有 1 条任务烟雾测试。
- PostgreSQL 只验证实体持久化。
- H2 只验证当前 schema 中字段为 null。
- 真实旧库升级暴露 optimistic version 空值首次 flush 失败。

## 3. retry 幂等漏洞
旧失败任务 A 在等价任务 B 已运行时仍可能创建 C，导致重复队列、模型费用和结果覆盖风险。

## 4. retry 最终语义
完成历史是否复用与活动唯一性彻底分离。所有入口永远先复用等价活动 job；仅在不存在活动任务时创建新 retry，并保存来源关系。

## 5. 并发 retry 结果
H2 和 PostgreSQL 均验证 10 路并发 retry 只生成 1 个活动 job，队列提交一次；QUEUED、RUNNING、CANCEL_REQUESTED 均可复用。

## 6. Playwright 原覆盖
原 1 条测试覆盖项目创建、刷新、切页、绑定、10 路重复扫描、取消和状态恢复。

## 7. 新增核心业务 E2E
Playwright 现有 4 条独立测试，临时项目和 Git 仓库测试后清理，失败保留 screenshot/trace。

## 8. 项目分析批次 E2E
固定 Git 提交和工作区变化经固定兼容模型生成正式分析批次；刷新、切页后批次和来源标签仍存在。

## 9. 沉淀处理闭环 E2E
逐条确认正式建议，验证 processed/pending 变化、新沉淀可见且进入待能力分析；受控模型失败产生的本地事实草稿保持 formal=0，不会批量确认。

## 10. 能力分析闭环 E2E
已确认沉淀生成能力候选并记录 analysis job/sourceRefs；刷新和切页不丢状态。随后受控失败单独展示，上一次成功卡片保留。

## 11. PostgreSQL 原范围
原测试只持久化项目、Provider、Job、沉淀、能力卡和取消状态。

## 12. PostgreSQL workflow integration
PostgreSQL 16.14 下真实执行 Spring service、repository 和 transaction：扫描、模型归并、正式建议、沉淀确认、能力分析、卡片确认、关系重读、失败保留、并发 retry 和取消。2 项通过。

## 13. H2 原兼容范围
原测试只把当前 schema 的可靠性字段设为 null 后重新加载。

## 14. 旧版本升级测试
新增文件数据库重启测试，模拟移除全部 V3.3.7 Job 字段后由当前应用更新 schema。项目、Provider、历史任务、沉淀、已确认能力和候选均保持完整。

## 15. 后端完整测试
174 项通过。

## 16. 前端构建
TypeScript、18 项契约测试、production build 全部通过。

## 17. Playwright
4/4 通过，约 43 秒。

## 18. PostgreSQL Testcontainers
2/2 通过，真实 PostgreSQL 16.14。

## 19. H2 升级
1 条文件库升级集成测试及既有兼容测试通过。

## 20. CI Run
最终远程 CI Run 在推送后回填。

## 21. 真实 DeepSeek
SKIPPED：无安全测试 Key。未影响任务幂等、数据库、浏览器业务闭环和结构化结果持久化结论；真实限流、长响应、网络和 Provider 私有字段仍需后续低预算验收。

## 22. 未解决风险
`ddl-auto=update` 缺少版本化迁移审计；同步 HTTP 当前请求不能强制中断；多实例 worker lease 未实现。

## 23. 后续建议
下一阶段单独评估 Flyway baseline、数据库活动任务唯一约束和真实 DeepSeek 低预算验收，不提前实现 V3.3.8。

## 24. 关键文件
- `backend/src/main/java/com/projectflow/service/ProjectAnalysisJobService.java`
- `backend/src/main/java/com/projectflow/entity/ProjectAnalysisJob.java`
- `backend/src/test/java/com/projectflow/ProjectAnalysisJobRetryIdempotencyTest.java`
- `backend/src/test/java/com/projectflow/ProjectFlowPostgresIT.java`
- `backend/src/test/java/com/projectflow/ProjectFlowH2UpgradeIntegrationTest.java`
- `frontend/e2e/core-workflow.spec.ts`
- `frontend/e2e/fixed-model-server.mjs`
- `.github/workflows/quality-gates.yml`

## 25. 最终 Commit SHA
首次正式推送后回填。

## 26. 报告链接
- [原 V3.3.7 实施报告](projectflow-v3.3.7-implementation-report.md)
- [V3.3.7 正式收尾报告](projectflow-v3.3.7-finalization-report.md)
