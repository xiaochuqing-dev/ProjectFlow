# ProjectFlow 产品语言与渐进披露合同

V4.0-E 补充：有来源时说清行动与具体对象，不能以“材料更新”“前后端结构”代替可读变化；来源不足时保留保守说明。宽泛文件集合只列文件对象示例，不因命名变细而提升成已验收能力。Current 的用途、能力、声明和推断逐条保留来源与分类；额外盘点可展开。日期必须明确是发生、提交、过程记录或观察时间，未知不能由文件名补造。Workline 的 Draft、Review、依赖、合入状态仍各自依赖真实证据。

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
# V4.0-D 阅读补充

普通入口使用 Current → 变化概览 → 按时间/按长期主题 → Story → Evidence；开发工作线说明并行工作与真实 PR 依赖。界面中“已确认结果”“项目明确声明”“系统归纳”“未知/冲突”不能互换。无来源计划与百分比保持空；PR 标题只代表声明，commit count 不表示进度，ahead 不表示尚未合入。当前材料理解继续使用既有有界分析入口，源码与 README 提供的声明分别披露来源，不能自动提升为项目事实。
