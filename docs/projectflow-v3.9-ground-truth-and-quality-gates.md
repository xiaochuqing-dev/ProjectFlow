# ProjectFlow V3.9 Ground Truth 与质量门

冻结日期：2026-08-24

机器可读清单：`docs/acceptance-evidence/v3.9/continuity-ground-truth.json`

可执行绑定：`docs/acceptance-evidence/v3.9/continuity-ground-truth-execution-map.json`

本清单在实现前冻结。Calibration 与 Holdout 使用不同 case ID；预期答案、阈值与 case identity 不得进入 production Prompt。后续只能通过 append-only amendment 增加说明，不能为了让实现通过而改写原预期。

## Calibration

覆盖 no-change、既有 Story 续接、既有 Thread 新 Story、独立 Story、相干 Chapter 吸收、长间隔新 Chapter、异质结果边界、rename/move、delete/restore、force-push、多 Commit 单结果、单 Commit 多结果、Supporting-only、修正保留、rewrite 使修正目标失效。

## Frozen Holdout

覆盖 Agent Result 先于 Git Evidence、后续 Evidence 校正且不自我提升、no-Git 文档、PPT、数据分析、Provider fallback/retry、Context revision、Obsidian no-op/局部更新、跨项目隔离、大项目小 delta、历史 Chapter 稳定和 stale/degraded Current State。

## 硬门

- no-change model request count = 0
- unaffected Story ID stability = 100%
- unaffected Thread ID stability = 100%
- unaffected Chapter engineering identity stability = 100%
- Raw Event loss = 0
- Invalid Evidence = 0
- Cross-project reference = 0
- Unsupported Strong Fact = 0
- planned→implemented、configured→deployed、implemented→verified 错误提升 = 0
- silent correction loss = 0
- silent correction wrong-target rebind = 0
- frozen critical cases false strong continuity attachment = 0
- unknown candidate ID = 0
- unrelated model window rerun = 0
- successful checkpoint replay = 0
- Context Package stale-current mismatch = 0
- Obsidian user content loss = 0
- Obsidian no-change mutation = 0
- secret leak = 0
- absolute private machine path leak = 0

语义不确定时必须选择 UNKNOWN、new Story 或 attention，不得建立未经证据支持的强连续关系。

## 验证层

1. 纯确定性单元/集成测试验证 Delta、身份、篇章尾部、修正、Current State、Context revision、并发与投影。
2. H2、PostgreSQL 16、Frontend、Playwright、Hermes、Obsidian、root launcher、敏感内容和 V3.8.5 regression 全部通过。
3. 三 Provider calibration、冻结 Holdout 与真实产品场景使用同一 production semantic contract；Provider 差异只允许出现在 transport/capability。
4. ProjectFlow T0–T7 多轮 dogfood 逐轮记录 delta、复用/变化范围、模型窗口、revision、修正和投影 mutation。
5. 最终 10–15 个连续性场景由真人检查；模型自评不能替代该门。

冻结清单保持不可变；执行绑定把 30 个 case ID 逐条连接到 Maven 或 Python 测试，并由 `ProjectHistoryV39GroundTruthExecutionMapTest` 校验 ID、split、源码和测试方法均真实存在。标准后端与 Obsidian 门会实际运行这些测试；映射文件及 case ID 不进入 production Prompt。
