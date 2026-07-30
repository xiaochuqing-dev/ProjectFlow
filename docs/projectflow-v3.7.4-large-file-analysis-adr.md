# ADR: Large-file Content Map and Range Evidence

状态：Accepted for V3.7.4。

## 决策

使用 Java 17 标准库流式扫描文本，复用现有 SensitiveContentRedactor 和 DOC_READER Provider。Content Map 记录 encoding、text/binary、line/byte count、source hash、heading/section、词法 symbol、TODO/FIXME、decision/deprecation/conflict/currentness marker、重复区域与 partial coverage。

每个 Range Evidence 包含 kind、start/end line、start/end byte、source hash、truncated 和脱敏正文。默认产生 HEAD、MIDDLE、TAIL，并按预算补 heading/marker/changed/query 范围。相同 source hash/range 不重复发送。

## 边界

- Content Map 不等于语义事实；词法 symbol 不等于 SCIP Definition/Reference。
- 完整大文件、完整 Agent Result 和 raw model response 不持久化。
- 超过读取或输出上限时保留 unread ranges、limitations 和 next useful read。
- 生成器创建 8 万行 fixture，仓库不提交巨型原件。

## 拒绝方案

不引入 Lucene、向量数据库、通用 RAG、编辑器 Piece Tree、语言 grammar 包或 SCIP producer。它们解决的问题超过本阶段的只读有界 Evidence 定位。

