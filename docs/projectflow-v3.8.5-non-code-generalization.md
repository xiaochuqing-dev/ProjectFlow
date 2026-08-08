# ProjectFlow V3.8.5 非代码项目泛化记录

状态：BLOCKED。历史 DeepSeek 五类非代码材料通过结构与安全检查，GLM 未运行；RC2 新双 Provider 场景因 Secrets 缺失未运行，且人工可读性仍为 0/0，因此不能宣称最终泛化质量 PASS。

RC2 的非代码规则保持 Provider-neutral：模型不再生成 role 或工程结构，确定性语言层根据材料类型生成安全 fallback，不把演示、研究、数据、品牌页或无 Git 版本强行描述成后端、Controller、数据库或发布能力。`NonCodeLanguageRegressionTest` 已通过。

## 场景结果

| 场景 | DeepSeek 真实场景 | 固定兼容回归 | 当前性 | 结构安全 |
| --- | --- | --- | --- | --- |
| 演示材料 / Reveal.js 类结构 | PASS | PASS | CURRENT_STATE_ONLY | PASS |
| 文档、论文、研究报告 | PASS | PASS | CURRENT_STATE_ONLY | PASS |
| 数据分析 CSV/JSON | PASS | PASS | CURRENT_STATE_ONLY | PASS |
| 单页品牌展示 | PASS | PASS | CURRENT_STATE_ONLY | PASS |
| 无 Git、只有版本材料 | PASS | PASS | CURRENT_STATE_ONLY | PASS |

DeepSeek 工件共 11 个场景、83 个物理请求，其中上述五类各 1 个窗口调用，均为 `MODEL_VALIDATED`，Raw Event 守恒、Invalid Evidence、跨项目引用和未处理窗口均为 0。无 Git 场景返回 `CURRENT_STATE_ONLY`，没有伪造历史成熟度。GLM 真实场景没有执行；固定兼容模型只证明流程边界。

## 真实 Provider 输出片段

以下是 DeepSeek 场景工件中保存的安全标题摘要，不是 raw response：

- 演示材料：整理季度审查文档形成清晰演示叙事。
- 研究报告：补全研究报告文档明确结论。
- 数据分析：更新 results 数据结果，解释关键指标。
- 品牌页：调整网站页面突出品牌核心信息。
- 无 Git 版本：记录交付版本形成可核对当前版本。

这些标题说明模型没有把五类材料统一改写成“后端/Controller/能力”，但仍有 `results`、`analysis` 等英文对象和“可核对版本”重复措辞。它们需要人工抽样，不应仅凭结构门禁视为自然语言通过。

## 消费者边界

非代码 Story 仍使用同一 corrected read model；Evidence 只保留安全相对引用。Gateway、Agent Context、Hermes 和 Obsidian 不因缺少源码或 Git 而切换为软件专属语义，也不会由 GET 触发模型调用。真实 DeepSeek Dogfood 的 ProjectFlow 场景因 Primary/Supporting 引用不一致失败，说明非代码通过不能覆盖整体 Provider 资格失败。

## 未完成

- GLM `glm-5.2` 非代码场景：NOT_RUN。
- 真实非代码项目的独立人工评分：NOT_RUN。
- 真实 Provider 资格未通过，最终状态为 BLOCKED。
