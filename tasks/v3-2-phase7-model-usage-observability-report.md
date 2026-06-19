# ProjectFlow V3.2 Phase 7 模型用量与质量可观测性报告

## 执行时间

- 日期：2026-06-19
- 范围：Phase 7 Token、质量和可观测性

## 本轮目标

让模型/模板输出调用具备可追踪记录，用户能在设置页按项目查看调用状态、Token 估算、耗时和基础质量提示。

## 已完成实现

### 后端

- 新增 `ModelUsageRecord` 持久化模型调用记录。
- 新增 `ModelUsageRecordRepository`，按项目读取最近调用记录。
- `AiOutputService.generate(...)` 在成果输出生成后写入调用记录。
- 新增 `GET /api/projects/{projectId}/model-usage-records`。
- 记录字段包括：
  - `operation`
  - `providerName`
  - `modelName`
  - `promptTokens`
  - `completionTokens`
  - `totalTokens`
  - `usageEstimated`
  - `latencyMs`
  - `status`
  - `errorType`
  - `errorMessage`
  - `qualityWarnings`
- 当前成果输出仍是本地模板或 mock provider 路径，因此 token 明确标记为估算，不冒充真实 provider usage。
- 增加基础质量检查：
  - 输出是否包含中文内容。
  - 有已确认变更时是否包含来源标注。
  - 是否疑似引用未确认候选事实。

### 前端

- `frontend/src/lib/api.ts` 新增 `ModelUsageRecord` 类型和查询 API。
- 设置页新增“模型调用记录”区域。
- 支持按项目选择调用记录。
- 展示最近调用记录的状态、provider、模型、token、耗时和质量提示。
- 展示当前项目的：
  - 今日 Token
  - 7 天 Token
  - 30 天 Token

## 验证结果

- 目标后端测试通过：

```powershell
& 'C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd' -q -Dtest=AiOutputControllerTest test
```

- 前端生产构建通过：

```powershell
npm.cmd run build
```

- 后端全量测试通过：

```powershell
& 'C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd' -q test
```

## 注意事项

- Maven 在当前 Windows/JDK 25 环境下仍输出 `Access is denied.` 和 JDK Unsafe/agent warning，但命令退出码为 0，测试实际通过。
- 本轮没有默认接入真实模型 provider 的 usage 回传；真实 usage 需要后续在实际模型调用适配层返回 token 后写入 `usageEstimated=false`。
- 当前质量检查是保守本地规则，不替代人工审查，也不把模型输出自动写入确认事实。

