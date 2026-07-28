# ProjectFlow V3.7.3 Long-running Analysis ADR

状态：Accepted

## 决策

`AnalysisTimePolicy` 明确分离五种时间语义：

- Connection Timeout：默认 10 秒，范围 1–60 秒，只约束 DNS/TLS/建连。
- Provider Request Timeout：默认 240 秒，尊重 Provider 或显式测试配置，可显著更长。
- Overall Analysis Deadline：`AUTO`、`FINITE`、`UNLIMITED`。V3.7.3 的 AUTO 与 UNLIMITED 都不会仅因总运行时间长而主动终止；FINITE 以秒为单位忠实使用大于零的用户值，未传值时默认 600 秒。
- Retry Boundary：transport retry 最多一次；SDK 自带 retry 关闭。
- Cancellation：模型等待期间每 250ms 检查取消、总体 deadline，并更新 heartbeat。

`QUALITY_FIRST` 是唯一当前质量模式。时间和 Token 不隐式绑定，不因等待变长而减少 Evidence、跳过必要深读或跳过满足 High-value Evidence Gate 的 Final Synthesis。

## Durable Job 与恢复

现有 `ProjectAnalysisJob` 保存当前阶段、阶段提示、heartbeat、已消耗请求/Token 和 runtime policy。任务启动时绑定总体 deadline；AUTO/UNLIMITED 不产生截止时间。服务重启后，尚未开始外部调用的任务可安全恢复；模型请求状态不确定的任务进入明确可重试/需确认状态，不自动重复计费。

旧任务的有限时长字段继续按 FINITE 兼容读取。显式取消优先于持久化后续正式结果。Provider、Schema 或普通请求超时造成的 Final Synthesis 失败仍保留 Stage 1、已校验 Tool Evidence 和当前降级档案。

## API

`POST /api/projects/{projectId}/understanding/refresh` 可选接收：

```json
{
  "deadlineMode": "AUTO | FINITE | UNLIMITED",
  "maxAnalysisDurationSeconds": 1800,
  "qualityMode": "QUALITY_FIRST"
}
```

响应增加 `analysisDeadlineMode`、`qualityMode` 和 `overallDeadlineEnabled`。不传 body 保持兼容并使用 AUTO。

## 拒绝方案

- 把 45 秒替换成 120/180 秒：仍是隐藏短上限。
- UNLIMITED 同时取消网络 timeout/retry：坏连接无法有界释放。
- 新建通用 workflow engine：现有 Durable Job 已能承载阶段、取消与恢复。
