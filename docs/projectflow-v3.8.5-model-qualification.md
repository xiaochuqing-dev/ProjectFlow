# V3.8.5 模型资格与运行边界

模型调用只经 `ModelGatewayService`，任务类型为 `PROJECT_HISTORY_SYNTHESIS`。Provider 协议、超时、取消、重试、usage、finish reason 和 schema 诊断由 Gateway 归一化；业务层不拼接 Provider 请求，也不按模型名硬编码语义答案。

RC2 统一采用最小输出合同：Story 只允许 ID、标题、摘要、有 Evidence 的原因和 Unknown；Chapter 只允许 ID、标题、摘要。角色关系、Chapter 成员和状态三段由工程层提供，GLM 与 DeepSeek 不存在专属 Prompt 或 validator 分支。

每个语义窗口最多一次有界请求，单次最多执行 16 个未完成窗口。窗口缓存要求 source fingerprint、strategy/Prompt 版本、window identity、presentation revision 和 Story/Chapter 集合精确一致；失败、取消、运行中、跳过或未处理尾部都会阻止全局 cache hit。Prompt 超限会拆分为可继续的子窗口，局部窗口失败不会阻断独立窗口，系统性 Provider 错误仍可停止剩余调用并保留诊断。

## 固定兼容结果

最新固定兼容场景工件 `v385-real-scenarios-openai_chat_completions/history-real-scenarios.json` 为 11/11 PASS、60 个物理请求、12,000 token。它证明 continuation、restart/cache、schema failure、取消、overflow、correction 和五类非代码边界，不证明真实 Provider 质量。

## 真实合同

- GLM `glm-5.2` Responses：1 请求，4,850 token，41,659 ms，schema/security PASS。
- DeepSeek `deepseek-v4-pro` Chat Completions：1 请求，4,271 token，81,987 ms，schema/security PASS。

## 真实资格

- GLM 19-case：20 请求、103,268 token、616,966 ms；16 个降级窗口、24 个失败/未处理窗口、12 个 UNSUPPORTED_CLAIM 拒绝；qualification FAIL。
- DeepSeek 19-case：20 请求、79,702 token、1,002,070 ms；14 个降级窗口、24 个失败/未处理窗口、12 个 UNSUPPORTED_CLAIM 拒绝；qualification FAIL。
- DeepSeek 11 场景：10/11 PASS；ProjectFlow Dogfood 因 Primary/Supporting history references inconsistent 失败。
- GLM 真实场景、旧版 `ProjectFlowRealModelEvalIT`、`ProjectUnderstandingRealModelIT`：NOT_RUN。

真实资格工件的安全聚合指标均为零违规，但资格仍因失败/降级/拒绝窗口不通过。未把合同 PASS、固定模型 PASS 或安全指标 PASS 写成 Provider qualification PASS。最终状态为 BLOCKED。

RC2 workflow 31264440534 在凭据前置检查处失败，两项 Secret 均未配置；真实步骤未发请求。RC2 新资格结果保持 NOT_RUN，不能用历史合同 PASS 覆盖。
