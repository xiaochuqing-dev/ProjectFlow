# ADR：V3.7 Dynamic Project Profile

状态：Accepted

## 决策

从固定六个 Understanding Section 过渡为 `DynamicProjectProfile.sections[]`。每个 Section 有 id、type、title、summary、claims、evidence refs、confidence、epistemic status、display priority 和 applicability reason。

必须允许：

- 空目录或空白文本：0 Section。
- 文档型输入：Document Overview/Topics/Decisions，不生成代码架构。
- 小脚本：Purpose/Input/Output/Dependencies/Usage，不套多层架构。
- 代码项目：Current State/Technology/Structure/Engineering，加上模型证明适用的动态维度。
- 无历史：0 Timeline/Evolution。
- 未证明 Backend/Database：不显示对应 Section。

## 兼容

旧 identity、technology、structure、architecture、capabilities、engineeringState 字段保留，V3.7 从动态 Section 投影可匹配内容；旧 snapshot 仍由页面 fallback 读取。

## 非目标

不重做最终 UI，不固定新模板，不把 unavailable data 伪造成卡片，不让 Dynamic Profile 成为 Fact 来源。
