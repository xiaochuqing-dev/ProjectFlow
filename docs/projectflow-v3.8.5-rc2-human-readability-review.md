# ProjectFlow V3.8.5 RC2 人工可读性复核

状态：PENDING_HUMAN_REVIEW。自动化真实模型门禁和 Round 2 冻结已完成，但真实人工评分尚未发生。

Round 2 文件为 `human-review-round2-manifest.json` 与 `human-review-round2-worksheet.md`，固定 30 Story 和 8 Chapter，GLM 与 DeepSeek 各 15 Story/4 Chapter。完整基线来自 runs `31523413972` 与 `31517037532`；纠正样本来自受影响 run `31532558352`。所有人工评分、备注和结论为空，reviewerCount=0，modelSelfScoring=false。

Story 需判断第一眼理解、Before/Change/After 自然度与非重复、Evidence 支撑、planned/implemented、declared/verified、技术/路径泄漏和原因猜测。Chapter 需判断时间阶段、中心成果、是否像项目阶段、raw subject、截断 slug、Supporting、统计口吻和 Evidence。4 分仅表示普通用户读一遍后能大致转述；自动 evaluator 不替代人工。

自动观察中需重点复核“项目材料/相关记录”类泛化标题、只描述移除动作的标题、把配置 Supporting 写得像实现，以及 ProjectFlow 的宽泛阶段标题。任何低分必须保留。PR #15 在用户明确决定前保持 Draft。
