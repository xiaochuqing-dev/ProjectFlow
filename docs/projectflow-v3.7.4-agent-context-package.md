# ProjectFlow V3.7.4 Agent Context Package

Context Package 版本：projectflow-agent-context-v1。

包由持久化 ProjectFact、ProjectUnderstandingSnapshot、Evidence Source Map 和 Agent candidate 层生成，不触发扫描或模型。

字段包括 project identity、source revision、generatedAt、current strong facts、declared material、inferred candidates、conflicts、unknowns、key Evidence、latest verified changes、historical coverage、limitations 和 provenance。每一项保留 projectId 与 fact/evidence/candidate ID。

包有字符、事实数、Evidence 数和候选数硬上限；超限时标记 truncated 与 limitations。它不是自由 Prompt 摘要，也不能把声明、推断、冲突或 Agent Result合并成强事实。

