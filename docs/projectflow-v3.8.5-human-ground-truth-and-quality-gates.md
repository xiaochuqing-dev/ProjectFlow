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

本地确定性 Ground Truth 契约和生产输出比较已通过。GLM 与 DeepSeek 真实 19-case 工件均保留失败窗口、降级窗口和拒绝主张：GLM 20 请求/103,268 token/16 个降级窗口，DeepSeek 20 请求/79,702 token/14 个降级窗口；两者 qualification 均 FAIL。DeepSeek 真实场景 10/11，ProjectFlow Dogfood 的 Primary/Supporting 引用不一致；GLM 真实场景未运行。人工可读性抽样仍为 0 Story/0 Chapter，因此不能把结构指标或模型自评写成最终人工质量 PASS。
