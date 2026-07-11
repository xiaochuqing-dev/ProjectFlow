# ProjectFlow V3.3.8 模型可靠性实施与验收报告

## 1. 背景

V3.3.7 已有持久化 Job、取消、幂等、预算和基础输出恢复，但结构化 Temperature 全局封顶 0.3、复杂任务固定 4000、恢复固定 2000、Provider 测试绕过网关，以及按第一个 JSON/数组解析，仍可能制造可避免失败。

## 2. V3.3.7 后的真实问题

源码确认 6 个真实模型入口，其中 5 个业务入口使用网关，Provider 连接测试自行发送 HTTP。大输入真实 DeepSeek 首轮还复现了合法 JSON 采用未知集合包装、Schema repair 后仍无法识别的问题。旧 H2 副本同时暴露 job status enum、change batch 计时列和 nullable worktree flag 的启动兼容缺口。

## 3. 模型入口注册表

| 入口 | 前端/API | Job | Service/网关任务 | 输入与 Schema | 新参数/恢复 |
| --- | --- | --- | --- | --- | --- |
| Provider 连接测试 | 设置页；POST /api/ai-providers/{id}/test | 无 | AiProviderService；PROVIDER_CONNECTION_TEST | 小输入；ok 对象 | 动态 256；统一 diagnostics |
| 分析新变化 | 工作台；POST /api/projects/{id}/scan/jobs | WORK_SESSION_SCAN | WorkSessionScanService -> ModelSegmentEnricher；DEVELOPMENT_SEGMENT_MERGE | Git/worktree/GitHub/Agent result；segments | 任务+输入动态；取消；本地事实草稿 fallback |
| 整体项目分析 | 项目分析；POST /api/projects/{id}/analysis/run | PROJECT | ProjectAnalysisService；PROJECT_ANALYSIS | 项目材料；summary/architecture 等 | 动态预算；本地规则 fallback |
| 单文件分析 | 文件页；POST /api/projects/{id}/files/analyze | FILE | ProjectAnalysisService；FILE_ANALYSIS | 路径/可见片段；role/summary 等 | 动态预算；敏感路径不发模型 |
| 能力解读 | 能力入口；POST /api/projects/{id}/capabilities/interpret | CAPABILITY_INTERPRET | ProjectMemoryService；CAPABILITY_INTERPRETATION | 项目档案+能力事实；6 字段对象 | 小任务预算；候选不自动确认 |
| 分析项目能力 | 能力与成果；POST /api/projects/{id}/capabilities/analyze/jobs | CAPABILITY_CARD_ANALYSIS | ProjectCapabilityService；PROJECT_CAPABILITY_ANALYSIS | 已确认沉淀；capabilities | 动态预算；失败保留旧卡片 |

所有入口默认超时基线 240 秒；reasoning capability 建议 300 秒。普通调用最多一次可重试 transport，恢复调用不叠加 transport retry；持久化 Job 继续限制总请求、token 和耗时。

## 4. Temperature 旧策略

旧网关统一执行 min(configured, 0.3)，Provider 测试另行固定 0。

## 5. Temperature 新策略

Provider capability 决定是否发送。支持时使用用户配置值，不做全局封顶；任务推荐值独立记录。不支持时省略。diagnostics 记录 configured、recommended、effective、sent 和 reason。

## 6. Max Tokens 旧策略

项目/文件/推进段/能力分析固定 4000，能力解读固定 1200，截断恢复固定 2000，并与 Provider 值直接取 min。

## 7. 动态输出预算

每个任务定义基础预算和有意义上界，再按 prompt 大小增加；reasoning 模型提高可见输出预留，最后只受 Provider capability ceiling 约束。真实套娃推进段最终申请 10148，不再被 4000 卡死。

## 8. Reasoning 策略

能力档案识别 reasoning model 和字段名。reasoning 原文不保存，只记录存在、长度和是否疑似耗尽共享预算。空 content + reasoning/length 进入 EMPTY_AFTER_REASONING_RETRY，并提高预算，不再压缩为固定 2000。

## 9. Provider Capability

已实现 DeepSeek chat、DeepSeek reasoning、OpenAI-compatible standard、custom standard 档案，表达 Temperature、JSON mode、structured output、reasoning、字段名、输出 ceiling、reasoning control、streaming、response shape 和建议 timeout。未知 Provider 安全退化，不发送私有参数。

## 10. 输出适配流程

Provider 响应 -> content/reasoning/finish reason/usage -> 截断分类 -> balanced 多 JSON 候选 -> trailing comma 修复 -> 目标 Schema 评分 -> 任意外层容器中的目标集合定位 -> camelCase/snake_case alias -> Schema match -> 业务证据校验。

## 11. Schema Mismatch

JSON 语法失败与 Schema mismatch 分开。合法 JSON 未匹配目标时，只把已有结果和最小目标 Schema交给模型执行一次定向重编码，不重新分析事实。失败码为 SCHEMA_REPAIR_FAILED，旧结果或本地事实草稿保留。

## 12. Retry 类型

- TRANSPORT_RETRY：仅 429/5xx/网络瞬态，最多一次。
- TRUNCATION_RETRY：首次输出截断，预算提高 50%。
- EMPTY_AFTER_REASONING_RETRY：reasoning 疑似耗尽共享预算，预算提高到至少首次两倍。
- SCHEMA_REPAIR_RETRY：按目标结构和已有内容大小计算重编码预算。

## 13. 统一 Diagnostics

记录 entrypoint/task、Provider/model/capability、输入与 prompt 大小、参数来源与决定、timeout、请求次数、usage、finish reason、content/reasoning 安全统计、截断、repair、Schema、partial、恢复条目、retry type、failure stage/code 和 latency。不保存 Key、Authorization、完整 prompt、原始响应、reasoning 原文或未脱敏源码。

## 14. 自动化固定模型测试

新增 Provider capability、动态参数、Temperature 省略、多候选 JSON、错误首数组、未知外层、snake_case、Schema repair 成功/失败、reasoning 空输出、动态恢复预算和 H2 旧库修复覆盖。最终数量见第 21 节。

## 15. 真实 DeepSeek 环境

环境变量没有 Key；本机嵌入式 ProjectFlow 检测到一个默认 DeepSeek Provider。验收通过应用实际 API 和数据库隔离副本完成，未读取、输出、记录或提交 Key。Provider/model 为 DeepSeek / deepseek-v4-pro。

## 16. 真实 DeepSeek 全入口矩阵

| 入口/等级 | 输入规模 | Temp 配置/实际 | 有效 Max | prompt/completion/total | finish | reasoning/content | retry/repair/partial | 状态 | 人工质量 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Provider 测试/小 | ok JSON | 0.2/0.2 | 256 | 未持久化 | stop | 否/是 | NONE/否/否 | 成功 | 可直接使用 |
| 整体项目/中 | ProjectFlow zip 材料 | 0.2/0.2 | 6466 | 5401/3081/8482 | stop | 否/是 | NONE/否/否 | 成功 | 可直接使用 |
| 文件/中大 | 当前 ModelGateway 等 4 文件 zip | 0.2/0.2 | 3487 | 1534/1360/2894 | stop | 否/是 | NONE/否/否 | 成功 | 轻微修改，片段边界已如实提示 |
| 能力解读/小 | V3.3.8 能力事实 | 0.2/0.2 | 1838 | 271/1220/1491 | stop | 否/是 | NONE/否/否 | 成功 | 可直接使用 |
| 推进段/大 | 30 提交/148 文件/15 Agent result | 0.2/0.2 | 10148 | 11551/8023/19574 | stop | 否/是 | NONE/是/否 | 8 条，需复核警告 | 轻微修改 |
| 项目能力/大 | 8 条已确认沉淀 | 0.2/0.2 | 7165 | 792/4109/4901 | stop | 否/是 | NONE/否/否 | 7 张卡片 | 轻微修改 |

## 17. ProjectFlow 套娃压力测试

隔离项目绑定 ProjectFlow 当前工作区，输入覆盖多提交、多文件、未提交变化、15 份 Agent result、长 prompt、推进段、8 条沉淀和能力分析。最终推进段使用 10148 输出预算产出 8 条，能力分析产出 7 张卡片；均未 fallback。

## 18. 历史故障路径

自动化等价复现 finish_reason=length、completion near limit、reasoning 存在、content 为空。现在必定进入 EMPTY_AFTER_REASONING_RETRY，diagnostics 显示 retry attempted/succeeded；不存在“检测截断但未恢复”的分支。

## 19. 结果质量抽样

项目分析与能力解读可直接使用；大文件分析因导入片段截断需要轻微补充；推进段和能力卡片内容与真实文件/沉淀一致，但质量标记要求用户复核。未发现把 HTTP 200 当作唯一质量标准的情况。

## 20. 未解决 Provider 风险

Provider 宕机、网络、429/5xx、Key/权限、模型版本改名和未登记私有响应形态不可由 ProjectFlow 消除。deepseek-v4-pro 本次可用，但未来能力变化仍需重新验收。无通用 reasoning 控制参数时不会擅自发送。

## 21. 后端/H2

`mvn test`：194 项通过，0 失败。`ProjectFlowH2UpgradeIntegrationTest,V33PersistenceTest` 专项通过。旧 H2 隔离副本真实触发并修复 job status enum、change batch 计时列和 nullable worktree flag。

## 22. PostgreSQL 16

`mvn -Ppostgres-it verify`：PostgreSQL 16 Testcontainers 2 项通过；同一 Failsafe 执行中 RealDeepSeekIT 因未设置环境变量 1 项跳过。真实 DeepSeek 证据来自第 16 节实际应用 API，不以 H2 或固定模型代替。

## 23. 前端构建

TypeScript lint 通过，18 项前端契约通过，Next.js 生产构建通过。

## 24. Playwright

4 条 Chromium E2E 全部通过：模型批次、沉淀确认与本地草稿隔离、沉淀到能力且失败保留旧结果、刷新/取消/retry 幂等。固定兼容模型服务不冒充真实 DeepSeek。

## 25. CI Run

本地 CI 等价命令全部通过；远程 GitHub Actions 待推送后更新。

## 26. 关键文件

ModelTaskType、ModelCapabilityRegistry、ModelRequestPolicy、ModelGatewayService、ModelOutputAdapter、ModelFailureClassifier、AiProviderService、ProjectChangeSchemaRepairService、设置页、诊断 UI、RealDeepSeekIT。

## 27. 最终 Commit SHA

待提交后更新。

## 28. 报告链接

docs/projectflow-v3.3.8-model-reliability-report.md

## 29. 结论

ProjectFlow 不承诺外部模型永不失败；V3.3.8 的完成标准是自身不再用统一低级参数、脆弱 JSON 选择、Schema 混淆或错误 retry 制造可避免失败。真实大输入复现并修复了一个此前自动化未覆盖的集合包装偏离，证明恢复链路和验收方法可发现真实问题。
