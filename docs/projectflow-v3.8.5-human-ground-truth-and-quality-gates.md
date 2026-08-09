# V3.8.5 人工 Ground Truth 与质量门禁

## 冻结材料

`backend/src/test/resources/projectflow-v385/history-ground-truth.json` 保存 calibration/holdout 的故事成员、Chapter 边界、正反标题、Before/Change/After、原因 Evidence、未知、冲突、Thread transition 和预期模型调用上限。Ground Truth 只用于测试和报告，不进入生产 Prompt、DTO、Snapshot 或 UI。

## 主要门禁

- 标题必须包含动作、对象和结果，禁止技术路径主导。
- Before/Change/After 完整率至少 0.90。
- Primary/Supporting F1 至少 0.80。
- Chapter 边界 precision 至少 0.80。
- 原因 Evidence precision 必须为 1.00；无 Evidence 的原因必须为空或 UNKNOWN。
- Thread 连续性至少 0.80。
- 人工可读性采用 1–5 分，4 分表示非工程用户读一遍即可说明结果，平均分门槛为 4.0。
- Invalid Evidence、跨项目引用、Raw Event 丢失、孤立 Supporting 或无 Evidence 强原因直接失败。
- Ground Truth 必须在模型运行前冻结，并保持 calibration 与 holdout 分离。

## 自动检查与真实结果

`ProjectHistoryV385GroundTruthTest` 校验 schema、case ID 唯一、split 不重叠、评测方法、Ground Truth 不泄漏到生产 Prompt、fallback 文案和 Primary/Supporting 指标边界。`ProjectHistoryV385QualityEvaluator` 只作为测试/报告工具，不进入产品数据。

workflow `31318477841` 的最终结果：GLM `glm-5.2` Responses/high 与 DeepSeek `deepseek-v4-flash` Chat/max 均通过 V3.8.5 19-case qualification。两者 Primary/Supporting F1=1.0、Chapter precision=0.9699、recall=0.9368、Before/Change/After=1.0、Reason Evidence=1.0、Thread=1.0、Conflict=1.0；Invalid Evidence、跨项目引用、unsupported strong fact、raw event loss、孤立 Supporting、无 Evidence reason、绝对路径/凭据泄漏、通用模板和技术泄漏计数均为 0。

最终真实场景两者均为 11/11，包含 Dogfood 与五类非代码。DeepSeek attempt 1 的 9/11 波动仍保留，相同 head 只重跑失败 job 后通过。

人工样本已冻结为 30 Story / 8 Chapter，但 reviewerCount=0、评分为空。自动指标不能替代人工阅读，因此最终质量仍为 BLOCKED / PENDING_HUMAN_REVIEW。
