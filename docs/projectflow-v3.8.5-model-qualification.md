# V3.8.5 模型资格与运行边界

模型调用只经 `ModelGatewayService`，任务类型为 `PROJECT_HISTORY_SYNTHESIS`。Provider 协议、超时、取消、重试、usage、finish reason 和 schema 诊断由 Gateway 归一化；业务层不拼接 Provider 请求，也不按模型名硬编码语义答案。

每个语义窗口最多一次有界请求，最多执行 16 个窗口。无历史、无合格 Story、无 Provider 或无变化时可以是 0 次。Prompt 容量不足、模型失败、取消、无效 JSON、未知 ID、Evidence 越界或不支持的主张都必须保留确定性结果，并在 checkpoint 和 Snapshot diagnostics 中披露。

缓存只恢复 validated presentation JSON，要求 Story ID 和 Chapter ID 集合与窗口精确一致。任何 `FAILED`、`CANCELLED`、`RUNNING` 或 `SKIPPED` checkpoint 都会阻止全局无变化缓存命中；重试通过 `beginAttempt` 保留审计行并清理旧错误。

真实 Provider 状态

本次本地收口未执行外部真实 Provider 请求，也未把用户凭据写入仓库、命令、日志或报告。GLM 与 DeepSeek 的真实 calibration、holdout、非代码项目和双模型产品链应在隔离 CI/安全密钥环境中执行；未执行部分保持 BLOCKED/NOT_RUN，不用 Mock 冒充通过。
