# ProjectFlow V3.8.5 RC 代码质量审计与修复记录

状态：本轮代码审计、修复和固定兼容回归已完成；真实 GLM/DeepSeek 合同已通过但双 Provider 资格失败，DeepSeek Dogfood 10/11，独立人工抽样和最终合并未完成，结论为 BLOCKED。

## 审计范围

本轮审计针对 Project History 的窗口续跑、模型输出合同、Primary/Supporting 角色图、用户修正后的 corrected read model、非代码材料泛化和 ProjectFlow 自身 Dogfood。ProjectFact、Raw Event、Evidence 和已有事实没有被展示层重建覆盖。

## 实际发现与修复

1. 验收执行器用 `stories(..., true, ...)` 读取 Supporting。该参数表示 `attentionOnly`，生产读取还会隐藏 Supporting，导致执行器拿到单向 Primary 引用并错误报告角色图失败。执行器现在读取快照经 correction overlay 后的完整 Story 集合；默认用户列表仍隐藏 Supporting。
2. fixed compatibility server 在没有原因 Evidence 时只写“仍需核对”，没有明确披露 UNKNOWN。Prompt v4 合同测试因此首次失败 1 项；fixture 改为明确写出“变化原因未知”，合同随后通过。
3. 展示不变量此前分散在重建与 correction 服务。新增 `ProjectHistoryPresentationInvariantValidator`，统一角色合法性、环、孤立 Supporting、双向关系、唯一归属和 corrected view 的 Chapter/Thread 覆盖与计数校验。已合并归档 Story 不参与活动角色图，但其原始 ID、事件和 Evidence 仍保留。
4. ProjectFlow Dogfood 曾因读取口径错误失败；修正后的最新固定兼容工件 `v385-real-scenarios-openai_chat_completions/history-real-scenarios.json` 为 11/11，通过 60 个物理请求。较早的 `fixed-v385-scenarios` 工件不是当前结论。真实 DeepSeek 重新运行后为 10/11，失败原因是 Primary/Supporting history references inconsistent；输出中仍保留低质量标题和英文对象，没有用 ProjectFlow 专属规则掩盖。

## 复现与验证

- `ProjectHistoryV385RealScenarioEvaluatorTest`：最新固定兼容模型工件 11/11 PASS，60 个物理请求，12,000 个固定 usage token；覆盖 5 类非代码、17 窗口、服务实例重启、全局 cache、Chapter 二阶段、局部 correction 失效、schema failure、取消恢复和 prompt overflow split。
- `ProjectHistoryRealModelIT`：固定兼容 Prompt v4 合同 PASS。首次失败已保留在本地 Surefire 记录，原因是 UNKNOWN 文案不满足合同，不是生产解析器放宽门禁。
- `ProjectHistoryReconstructionTest`：31/31 PASS。
- `ProjectHistoryCorrectionServiceTest`：修正后 14/14 PASS；首次抽取时 merge 归档 Story 被过严纳入活动角色图，已按原 corrected view 语义修复。
- 后端全量、PostgreSQL、前端生产构建、Playwright、Hermes 和 Obsidian 的本轮最终计数以 `evidence-index.md` 和最终验收报告为准；运行中的门禁未完成前不写 PASS。

## 未完成与限制

- GLM `glm-5.2` 与 DeepSeek Chat 的合同请求已在隔离进程完成，但 19-case qualification 均 FAIL；不能把合同 PASS 当成质量 PASS。
- DeepSeek 真实场景 11 个中 10 个通过，ProjectFlow Dogfood 因 Primary/Supporting 引用不一致失败；GLM 真实场景未执行。
- 尚未完成至少 30 个 Story、8 个 Chapter 的独立人工评分，因此可读性平均分为 NOT_RUN。
- PR #15 必须保持 Draft；真实 Provider、人工门禁和所需报告完成前不合并、不创建 Tag/Release、不清理分支。
