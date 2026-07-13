# ProjectFlow V3.3.8.1 数据读取可靠性实施报告

## 1. 背景、现象与范围

用户真实 H2 中，沉淀处理中心请求 500；离开工作台再返回后，已完成的 8 个开发推进段会短暂消失约 10～20 秒。本轮只修旧数据读取、工作台恢复和必要读模型，不重做 V3.3.7 的任务取消/retry/队列/预算，也不改 V3.3.8 的 ModelGateway、Provider capability、动态 temperature/max_tokens、reasoning、JSON/Schema/截断恢复或真实 DeepSeek 路径。

## 2. 最初假设、真实复现与根因

最初假设是历史 `ChangeBatch.modelStatus` 空值触发 `startsWith` NPE，以及 `workSessionListResult` 的弱结果覆盖完整扫描结果。真实用户库复现确认：`GET /api/projects/283e5477-e47b-417c-8a93-c101db0bc7cd/sediment-review-batches` 返回 500，堆栈为 `NullPointerException`，位置是 `ProjectSedimentService.reviewBatchResponse`；失败批次 `322ed50b-d47e-4656-a3a2-29d86e4c7d02` 的 modelStatus 等诊断字段为空。工作台复现确认，普通刷新构造 `batch=null, segments=[]` 并覆盖完整结果；稍后 `useProjectAnalysisJobs` 才从成功 job 恢复 8 个 segments。最终根因与两项假设一致，但字段审计显示旧 change/segment 还有多组空值，不能只修一行。

## 3. 旧数据兼容与是否回填

ChangeBatch 的 modelStatus、modelProvider、segmentationMode、fallbackReason、analysisScope、githubStatus、remoteRelation、scanFingerprint、四个计时、status 和时间读取全部 null-safe。ProjectChange 的 sourceBatchId、developmentSegmentId 保持可空，contentSource、qualityStatus、recommendationStrength、suggestedAction、evidenceRefs、status、标题和摘要安全降级。DevelopmentSegment 的 generationMode、qualityStatus、fallbackReason、batch 关系、集合、confidence 和 status 安全降级。

未做数据回填。缺失的原始诊断无法可靠恢复，读时默认更安全，也不会覆盖正确历史值。modelStatus 缺失的批次显示“历史数据不完整”；单条旧记录不会拖垮列表，真实 11 批次均可见。

## 4. N+1 与批次读取

原实现每个 batch 各查 changes 和 segments，确认存在 N+1。现改为所有 batches 一次读取、changes 一次批量读取、segments 一次批量读取并按 batchId 聚合。50 批次测试为 16～25ms、4 条 prepared statements，查询数不随批次数线性增长。真实用户库 11 批次首次 140ms，预热后 16～24ms。

## 5. 工作台合并与按项目快照

数据库中的 job、batch、segment、change 和 sediment 是事实来源；React state 是运行态；sessionStorage 只是快速缓存。sessions 可以独立更新，弱结果缺少 batch 时保留现有 batch，segments 为空时保留现有非空 segments；只有数据库 Bootstrap 的权威结果可明确清空。成功 WORK_SESSION_SCAN 的完整 result 会立即同时写入页面状态和所选项目快照，项目 ID 不匹配的扫描结果被拒绝。

快照键为 `projectflow:dashboardSnapshot:{projectId}`，schemaVersion 为 2，并记录 capturedAt、latestScanJobId、latestBatchId、latestBatchUpdatedAt 和 pendingSedimentReviewCount。默认五分钟新鲜度只用于诊断；旧快照仍可先显示并后台校准。旧单 key 按匹配项目惰性迁移；退出清全部，删除项目只清该项目。

## 6. Dashboard Bootstrap Read Model

新增 `GET /api/projects/{projectId}/dashboard-bootstrap`。返回已校验归属的项目摘要、memory/本地路径、最新成功 WORK_SESSION_SCAN job 投影、最新持久化 batch、该批 segments、最多 20 条 work sessions、待处理正式建议数、最新项目分析投影和 Provider 可用性。它只执行数据库 latest/count/有界列表查询，不运行 Git、GitHub CLI、模型、文件系统扫描或文本分析，也不返回 Key、prompt、原始响应或 reasoning。

页面先恢复项目快照，再立即用 Bootstrap 校准；materials、suggestions、history、tasks、bundles、conflicts、outputs、Providers 和 GitHub 独立渐进加载。次要接口失败保留核心分析结果，只显示局部失败和重试入口；projectId 与分 scope request guard 防止 A 的迟到响应覆盖 B。

## 7. 错误与产品验收

后端 AppException message 正常透传，无法解析的 500 使用“沉淀批次读取失败，请查看本地服务日志后重试。”。已有批次时刷新失败继续展示并标记降级。

真实用户 H2 验收：工作台显示最新批次、32 提交、155 文件和 8 个开发推进段；沉淀列表显示最新批次及“历史数据不完整”旧批次；详情有 8 条正式建议；实际确认一条后生成项目沉淀 `b4883b47-d3d6-42d6-9beb-8f6edcaf80a0`，进度从 0/8 变为 1/8，返回列表统计为 processed=1、pending=7。未删除或清库。

## 8. 性能实测

- 有 snapshot 返回工作台：核心批次可见 268ms，Bootstrap 校准同轮 268ms，无“暂无待整理变更”闪烁。
- 清空 sessionStorage 后 F5：数据库 Bootstrap 恢复核心批次和 segments 为 262ms。
- Bootstrap 服务 MockMvc：42ms、7456 bytes；真实用户 H2 首次 131ms，预热中位约 96ms。
- 50 批次列表：16～25ms、4 条 SQL；真实 11 批次预热 16～24ms。

## 9. 自动化、数据库与构建结果

新增 7 个直接回归用例：后端 3 个覆盖旧数据列表/详情、50 批次查询数和 Bootstrap；前端 4 个覆盖弱响应合并、项目快照隔离、旧 key 迁移和 freshness。现有 Playwright 流程扩展了返回工作台、无缓存 F5、A/B 切换、GitHub 失败隔离、沉淀列表/详情/确认/统计。

- 后端/H2：197 项通过，含文件 H2 旧库升级。
- PostgreSQL 16 Testcontainers：2 项通过，Bootstrap 读取加入真实扫描/沉淀/能力闭环。
- 前端：TypeScript 通过，22 项契约测试通过，Next.js production build 通过。
- Playwright：4 项通过，使用真实前后端与明确标识的固定兼容模型；不冒充真实 DeepSeek。
- 敏感内容扫描：通过。

## 10. CI、提交与已知剩余问题

远程 GitHub Actions：Run 29253336055，最终 attempt 3 核心作业全部通过。attempt 1 的 frontend-quality 与 browser-e2e 均在 Set up job 阶段遇到 GitHub `Service Unavailable / Failed to resolve action download info`，没有执行项目代码；attempt 2 的 frontend-quality 通过，browser-e2e runner 在 Playwright 依赖安装停滞 15 分钟后被取消；attempt 3 更换 runner 后依赖安装 30 秒、4 项核心浏览器流程全部通过。optional-real-deepseek 因未提供安全 Key 按规则 SKIPPED。实现提交 SHA：`aa360d57d2f1abe805f7f8efaa38982ec68a2635`。

已知剩余问题：缺失的历史模型诊断不可恢复，只能保守标记；项目仍使用 Hibernate ddl-auto=update；sessionStorage 可被浏览器清理，但已由数据库 Bootstrap 兜底。后续只需观察真实大库的索引和慢查询，不进入 V3.3.9，也不提前实现增量能力分析、向量库、Flyway 全量体系或多实例 Worker。

## 11. 关键文件与报告链接

关键文件：`ProjectSedimentService`、三个兼容实体、批量查询 repositories、`DashboardBootstrapService`、`ProjectController`、`dashboard/page.tsx`、`dashboard-snapshot.ts`、`api.ts`、`DataReadReliabilityTest`、`core-workflow.spec.ts`。

报告：`docs/projectflow-v3.3.8.1-data-read-reliability-report.md`
