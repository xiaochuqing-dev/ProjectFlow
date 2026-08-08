# ProjectFlow V3.8.5 人工可读性抽样记录

状态：BLOCKED / NOT_RUN。历史真实工件有部分候选，但 RC2 双 Provider 新工件尚未产生，也没有冻结并完成独立人工评分。

## 抽样规则

总计至少冻结 30 个 Story 和 8 个 Chapter，并分层覆盖两个真实 Provider、ProjectFlow、五类非代码项目、短历史、大历史、generic Commit、add/modify/remove/restore、merge/split、conflict、unknown reason、Supporting 和用户修正。固定 ID、来源工件和 presentation revision 后才允许评分。

每项 1–5 分：一眼是否知道改了什么、是否理解前后状态、是否出现工程术语、是否像完整成果、是否愿意继续下钻、Evidence 是否可达、是否编造原因、是否过度泛化。4 分表示非工程用户读一遍后能用自己的话说明改变和结果；平均分门槛为 4.0。Invalid Evidence、跨项目引用、Raw Event 丢失、孤立 Supporting 或无 Evidence 原因直接失败。

## 已观察到但未评分的候选

DeepSeek 非代码场景包括“整理季度审查文档形成清晰演示叙事”“补全研究报告文档明确结论”“更新 results 数据结果，解释关键指标”“调整网站页面突出品牌核心信息”和“记录交付版本形成可核对当前版本”。大历史场景还出现 `outcome00000` 等合成对象名；ProjectFlow Dogfood 没有可接受的完整候选，因为 Primary/Supporting 引用不一致。

这些候选只能进入人工池，不能用模型自评、固定 fixture 分数或结构指标代替人工评分。

## 当前结果

Story 抽样数量：0。Chapter 抽样数量：0。人工平均分：NOT_RUN。独立第二评审者：无记录。RC2 真实 Provider 尚未运行且人工门禁未运行，PR #15 必须保持 Draft。
