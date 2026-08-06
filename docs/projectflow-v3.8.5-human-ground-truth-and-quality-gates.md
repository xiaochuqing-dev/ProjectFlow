# V3.8.5 人工 Ground Truth 与质量门禁

冻结材料

`backend/src/test/resources/projectflow-v385/history-ground-truth.json` 保存 calibration/holdout 的故事成员、Chapter 边界、正反标题、Before/Change/After、原因 Evidence、未知、冲突、Thread transition 和预期模型调用上限。Ground Truth 只用于测试和报告，不进入生产 Prompt、DTO、Snapshot 或 UI。

主要门禁

- 标题必须包含动作、对象和结果，禁止技术路径主导。
- Before/Change/After 完整率至少 0.90。
- Primary/Supporting F1 至少 0.80。
- Chapter 边界 precision 至少 0.80。
- 原因 Evidence precision 必须为 1.00；无 Evidence 的原因必须为空或 UNKNOWN。
- Thread 连续性至少 0.80。
- 人工可读性采用 1–5 分，4 分表示非工程用户读一遍即可说明结果。
- Ground Truth 必须在模型运行前冻结，并保持 calibration 与 holdout 分离。

实现的自动检查

`ProjectHistoryV385GroundTruthTest` 校验 schema、case ID 唯一、split 不重叠、评测方法存在、Ground Truth 不泄漏到生产 Prompt、fallback 文案可读，以及 Primary/Supporting 指标计算边界。`ProjectHistoryV385QualityEvaluator` 只作为测试/报告工具，不进入产品数据。

质量状态

本地确定性 Ground Truth 契约已通过；本次收口没有伪造真实 Provider 的人工质量分数。真实模型和人工抽样仍须在具备安全运行环境后单独记录。
