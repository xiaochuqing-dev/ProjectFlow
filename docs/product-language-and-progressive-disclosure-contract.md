# ProjectFlow 产品语言与渐进披露合同

状态：V3.8.5 收口时冻结产品合同。V3.9 的新增用户可见语义必须遵守；最终 GUI、具体视觉层级与交互实现留到 V4.0。

## 用户层语言

- 使用正式、自然、简洁的中文产品语言，不使用网络口语、夸张宣传语或幼稚化表达。
- 用户第一层表达项目故事、当前状态和可确认结果，不默认暴露内部 enum、Representative Cluster、weight、Primary/Supporting 统计、Claim state、Evidence ID 或完整 hash。
- 内部 `Before / Change / After` 在用户层显示为“此前状态 / 本次变化 / 当前结果”，或使用经过产品审核的等价正式中文。
- Git、Commit、PR、CI、API、Token、SHA、HTTP、JSON 等行业通用术语保留，不强行生造中文译名。

## 工程层语言

- 工程详情与审计层保留完整 Hash、Commit SHA、Evidence ID、Claim state、authority、epistemic status、cluster diagnostics 和来源关系。
- 工程术语可以使用行业通用英文，但仍应给出清晰、准确的中文解释。
- 工程字段通过下钻提供，不能在普通用户第一屏成组堆放。

## ID 与 Hash 展示

- 数据层始终保存和传递完整真实值。
- GUI 默认使用短 ID；6 至 8 位只是 V4.0 的设计候选，本合同不锁死具体长度。
- 完整值必须支持复制、展开或进入工程详情查看。
- 短显示只用于呈现，绝不能替代完整 ID 参与业务逻辑、关联、校验或审计。

## 渐进披露层级

1. 用户故事与当前状态。
2. 主要成果，以及必要的 Supporting、Unknown 与 Conflict。
3. 工程详情。
4. 完整 Evidence、Hash 与 diagnostics。

任何层级压缩都不能删除事实、Evidence、Unknown、Conflict 或审计入口。V3.9 只落实新增语义和必要低风险文案，不提前进行最终 GUI 重构。
