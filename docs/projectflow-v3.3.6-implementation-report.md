# ProjectFlow V3.3.6 实施报告

## 1. 任务背景
在 V3.3.5 模型可靠性基础上，修复真实截断误判，并把大量建议列表重构为可持续处理的沉淀闭环。

## 2. V3.3.5.1 稳定性问题
空 content 会先触发普通空响应异常，导致 finish_reason=length、token 耗尽和 reasoning 输出无法进入紧凑重试。

## 3. V3.3.6 工作流问题
本地草稿会被包装为正式建议；工作台和沉淀页列表过长；确认沉淀与能力形成之间缺少状态闭环。

## 4. 最终完成范围
完成模型网关修复、统一诊断、长事务拆分、正式建议边界、沉淀处理中心、时间档案、能力闭环、测试与文档升级。

## 5. 未完成范围
未执行真实 DeepSeek 和 PostgreSQL 运行验证，原因分别为无可用 Key、Docker 服务未运行；均未伪造结果。

## 6. 空 content + length 根因和处理
响应正文判空早于截断判断。现改为先综合 finish reason、completion tokens 和 reasoning 字段；疑似预算耗尽时触发一次 2000 tokens 的紧凑重试。

## 7. reasoning 字段处理原则
识别 reasoning_content、reasoning、analysis；只记录是否存在及长度，不保存、不返回原文。

## 8. Max Tokens、Temperature、重试和总预算
诊断保留真实生效参数；紧凑重试降低输出预算且不再叠加传输重试；单次结构化任务总请求不超过 3 次。

## 9. 全模型入口诊断
项目分析、文件分析、能力解释和能力卡片统一返回请求次数、用量来源、token、耗时、Provider/model 和失败阶段摘要。

## 10. 长事务拆分
扫描、项目分析、文件分析、能力解释和 Agent result 扫描不再以方法级事务包裹外部调用；持久化仍使用短事务边界。

## 11. 降级边界
模型成功产生正式结果；部分恢复只保留完整条目并带警告；模型失败只产生本地事实草稿，不冒充模型结果。

## 12. 本地草稿与正式建议
只有 generationMode=MODEL 且有证据的推进段可生成 ProjectChange；LOCAL_RULE 与 AGENT_RESULT 只保留在批次详情的草稿区。

## 13. 沉淀处理中心流程
用户从批次列表进入详情，逐条查看正式建议，可上一条、下一条、跳过、稍后、创建、合并、仅补证据或忽略。

## 14. 批次和时间管理
批次按今天、昨天、本周、更早分组；工作台只显示批次摘要；项目沉淀按能力状态和时间归档。

## 15. 推荐策略
推荐分为强、中、仅供参考、不推荐。强推荐必须同时具备模型来源、充分证据和可靠目标相似度，本地草稿不显示强推荐。

## 16. 确认反馈闭环
反馈明确写入目标、证据数、涉及文件数、摘要变化、能力待分析状态和详情入口；不再把涉及文件误称新增文件。

## 17. 沉淀与能力分析关系
新确认沉淀进入 PENDING_ANALYSIS。能力分析只消费已确认沉淀，成功后标记形成能力或已分析未形成能力，失败保持待分析且不替换旧成功卡片。

## 18. 数据模型和迁移
ProjectChange 增加来源批次、内容来源、质量和推荐强度；ProjectSediment 增加文件、批次、质量、能力状态及最近分析 job。字段使用可空或安全默认值，旧数据无需清库。

## 19. H2 兼容验证
后端完整测试使用 H2 2.3.232 启动并完成实体建表、查询、写入及工作流验证，165 项全部通过。

## 20. PostgreSQL 兼容验证
完成 JPA 字段和兼容策略静态检查；Docker Desktop 服务未运行，未执行真实 PostgreSQL 启动验证。

## 21. 自动化测试
后端 165 项通过；前端 18 项静态契约检查通过，覆盖 V3.3.6 批次、逐条处理和草稿分离。

## 22. 前端构建
Next.js 16.2.7 生产构建通过，TypeScript 通过，21 个静态页面生成完成，新增两个沉淀处理路由。

## 23. 后端构建
Maven test 构建成功，ProjectFlow 版本为 3.3.6。

## 24. 真实 DeepSeek 联调
环境变量中未发现 DeepSeek、ProjectFlow 模型或 OpenAI Key，因此未发起真实付费调用。网关场景通过模拟响应测试覆盖。

## 25. 验收记录
自动化记录覆盖空正文截断、reasoning 存在、低预算重试、请求上限、本地草稿边界、批次统计、确认反馈、能力输入和旧结果保留；未生成界面截图。

## 26. 已知风险
不同 Provider 的私有推理字段仍可能扩展；真实 PostgreSQL 与真实 DeepSeek 需在具备安全环境后补做。旧版无批次数据只能标记来源未知。

## 27. 后续建议
在隔离测试账号中补做三类真实 DeepSeek 分析，并在 CI 增加 PostgreSQL Testcontainers 兼容任务。

## 28. 关键文件
ModelGatewayService.java、ProjectSedimentService.java、ProjectCapabilityService.java、V33WorkflowDtos.java、sediment-review 页面、model-analysis.md、sediment-workflow.md、capability-analysis.md。

## 29. 最终 commit SHA
实现提交 SHA 在交付提交完成后由最终回复给出；本报告与实现同批提交。

## 30. 报告链接
仓库内路径：docs/projectflow-v3.3.6-implementation-report.md
