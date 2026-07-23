# ADR：V3.7 Semantic Scout

状态：Accepted

## 决策

复用 `PROJECT_UNDERSTANDING_SNAPSHOT` Model Gateway task，执行一次组合的 Semantic Scout + Dynamic Profile 响应，不新增模型客户端或逐来源请求。

输入只包含 intake 摘要、相对 locator、至多 80 个 redacted bounded sample、结构压缩、Git/coverage 摘要和 evidence ID。输出包含 shape hypotheses、source assessments、applicable dimensions、registered tool requests、unknowns、conflicts/currentness warnings 和 dynamic sections。

## 验证

Shape、assessment、claim、conflict 和 warning 必须绑定 allow-list evidence ID。Unknown ID 被过滤。模型不能改变 source 存在性、结构关系、工具 availability 或 Historical Coverage。

## 原因

纯 filename 规则会漏掉 `fuck-this-bug.md` 等高价值材料；全仓模型扫描又会泄漏材料并线性消耗 token。一个有界 Scout 能提供语义价值，同时保持 V3.6 的单请求、缓存和失败恢复边界。

## 折中

本阶段不是迭代 Agent loop。工具结果主要在 Scout 前由确定性 provider 收集，模型建议经过 registry 形成可解释计划；需要二次 targeted retrieval 的复杂场景留给后续版本。这样避免新增通用 Agent runtime 和多请求成本。
