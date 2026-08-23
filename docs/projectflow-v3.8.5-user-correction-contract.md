# V3.8.5 用户修正合同

定位

用户修正是 `USER_DECLARED_PRESENTATION`，属于展示覆盖层，不是 ProjectFact、ProjectHistoryEvent、Evidence、Timeline、Capability 或 Evolution 的事实写入。

支持的声明

支持重命名 Story/Chapter、编辑摘要、合并/拆分 Story、设置 Primary/Supporting、重新挂接 Supporting、隐藏、置顶、声明 Chapter、恢复自动展示。所有修正保存目标类型、目标 ID、声明文本、来源 fingerprint、期望 presentation revision、状态和冲突说明。

一致性

创建修正前校验用户和项目所有权、目标存在、来源 fingerprint 和乐观 presentation revision。版本不一致时写入冲突审计并返回冲突，不静默覆盖。目标因重建或 rewrite 消失时保留修正记录并在 Story/Chapter 的 `correctionConflicts` 中说明。`RESTORE_AUTOMATIC` 只停用重叠的 active correction。

消费

History GET、Gateway、Agent Context、Hermes 和 Obsidian 通过同一 corrected read model 读取；刷新把 active correction revision 纳入 cache identity。Obsidian CORE 只投影主要成果以及有冲突、未知、置顶或声明覆盖的支撑项。

安全边界

用户声明不提升 epistemic status，不制造 Evidence，不改变 eventRefs 或事实时间。修正文本有界、脱敏、可审计，永不返回 Key、Authorization、完整 Prompt、raw response 或 reasoning。
