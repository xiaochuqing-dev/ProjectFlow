# ProjectFlow V3.3.5 实施与验收报告

## 结论

V3.3.5 已按提示词的合理方向完成实现。重点不是增加更多模型开关，而是让模型失败、截断、部分恢复、沉淀确认和能力分析都能被用户理解、追溯和安全恢复。前端生产构建通过，后端全量 161 项测试也已在权限恢复后通过。

## 本次方向校正

1. 模型不存在“无限增大 Max Tokens”方案。Provider 上限限制为 256 到 200000，任务仍以各自策略上限为准，并记录实际使用值。
2. 疑似截断不再直接归为“返回格式无效”。系统执行一次紧凑重试；若根数组已有完整对象，则保留这些对象并显示警告。
3. “取消默认 Provider”不再暗中使用最近更新的非默认 Provider。所有新模型任务只使用明确默认项；历史多默认数据读取时归一为唯一默认项。
4. 旧的省略号数据不能被伪装为完整内容。系统只能识别为历史截断并提示重新分析，不能凭空恢复丢失文本。

## 已实现内容

### 模型可靠性与可观测性

- 模型调用诊断记录 Provider、模型、finish reason、输入/输出/总 token、Provider 与任务 Max Tokens、实际 Max Tokens、Provider 与实际 Temperature、超时、延迟、传输重试、紧凑重试、JSON 修复、截断与部分恢复数量。
- 诊断不向前端暴露 API Key 或原始模型响应。
- 区分空响应、疑似截断、JSON 无法解析、目标结构无法识别、证据绑定失败和持久化失败，并使用中文提示。
- 展示层可以裁剪预览，持久化层不再按标题、摘要或变更内容长度截断。

### 建议沉淀

- 建议列表和详情显示系统推荐动作与推荐理由。
- 确认前可预览写入目标、已有卡片、证据、文件、摘要变化和对下一次能力分析的影响。
- 确认后返回实际执行结果，包括写入目标、证据数、文件数、摘要是否更新及直接查看沉淀的入口。
- 旧版结尾为省略号的沉淀显示历史截断提示和重新分析入口。

### 能力卡片与异步任务

- 能力卡片保存来源分析任务 ID。
- 能力页按当前成功批次、最近失败和历史批次展示；失败任务不会覆盖上一次成功候选，已确认卡片始终保留。
- 最近失败任务可以查看人话化原因、实际模型诊断和确认关闭；旧版无任务来源的卡片明确标为来源未知。

### Provider 管理

- 支持编辑 Provider、测试配置、唯一默认项、删除保护和重复项的用户确认清理。
- 编辑时 Key 留空保留旧值，只有显式勾选才清除 Key。
- 删除当前默认项时要求先设定替代默认项；删除唯一默认项仍允许，使用户可以完全关闭模型功能。
- 新模型任务只选择明确默认且已配置 Key 的非 MOCK Provider。

### 版本、兼容和文档

- 后端 Maven、前端 package 和页面可见标识统一为 V3.3.5。
- 新持久化字段采用可空或带安全默认值的字段，使用既有 `ddl-auto: update`，不要求清库或删除旧数据。
- 已更新 README、架构、数据模型、项目上下文、决策、风险和需求记录。

## 关键实现文件

- `backend/src/main/java/com/projectflow/service/ModelGatewayService.java`
- `backend/src/main/java/com/projectflow/service/ModelOutputAdapter.java`
- `backend/src/main/java/com/projectflow/service/AiProviderService.java`
- `backend/src/main/java/com/projectflow/service/ProjectSedimentService.java`
- `backend/src/main/java/com/projectflow/service/ProjectCapabilityService.java`
- `backend/src/main/java/com/projectflow/service/ProjectAnalysisJobRunner.java`
- `frontend/src/app/settings/page.tsx`
- `frontend/src/app/project-changes/[changeId]/page.tsx`
- `frontend/src/app/project-intelligence/capabilities/page.tsx`

## 验收结果

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 修改前后端基线测试 | 通过 | 修改前 `mvn.cmd -q test` 已通过。 |
| 新增核心链路定向测试 | 通过 | 覆盖模型重试、截断恢复、Provider、沉淀、能力任务和兼容 DTO。 |
| 全量 Maven 测试 | 通过 | 权限恢复后执行 `mvn.cmd -q -Dmaven.repo.local=%USERPROFILE%\\.m2\\repository test`，161 项测试全部通过。两项旧断言已按 V3.3.5 语义修正。 |
| 前端生产构建 | 通过 | `npm.cmd run build` 通过，TypeScript 与 20 个页面生成均通过。 |
| 差异格式检查 | 通过 | `git diff --check` 通过。 |
| 默认 Provider 静态审查 | 通过 | 四个模型入口均筛选 `defaultEnabled`、非 MOCK 和已配置 Key。 |
| H2 兼容性 | 通过 | 全量测试使用 H2，并完成实体建表、持久化和控制器测试。 |
| PostgreSQL 配置兼容性 | 静态通过 | 实体字段与既有 PostgreSQL 配置兼容；Docker 配置校验受沙箱拒绝读取 Docker 用户配置影响，未启动实际容器。 |
| 真实 DeepSeek 调用 | 未执行 | 执行环境未配置 `DEEPSEEK_API_KEY`，未发送外部或付费请求。 |

## 风险与后续验收

1. 在配置了可测试 DeepSeek Provider 的隔离项目中，执行一次真实调用，确认 finish reason、token、延迟和紧凑重试诊断落库与页面展示正确；不要在报告或日志中记录 Key。
2. 使用真实 PostgreSQL 副本验证旧数据升级，重点检查历史多个默认 Provider、旧省略号内容、旧能力卡片和旧分析任务。

## 提交与交付状态

- 任务开始前的存档提交：`f35648f chore: archive before ProjectFlow v3.3.5 implementation`。
- 本次实现已提交：`61173fe feat: implement ProjectFlow v3.3.5 reliability`。
- 本报告会以单独的记录修订提交保存，并与实现提交一起推送到 `origin/master`。
- 结构化 Agent 结果已写入 `.projectflow/agent-results/20260711-v335-reliability/result.json`。
