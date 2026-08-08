# ProjectFlow V3.8.5 RC2 定向复用决策

本轮没有出现需要引入新框架或复制外部实现的重要设计问题，因此未新增依赖，也未用泛化研究替代直接修复。

复用内容：继续使用现有 ModelGatewayService 和 capability 边界承载两种协议；复用 corrected presentation view 作为所有消费者唯一展示来源；复用现有 checkpoint/cache，而不是新增工作流引擎；复用 Gateway 分页和 revision，Obsidian 只补充一致性校验；前端复用现有 details 渐进展开组件。

未采用内容：Provider 专属 Prompt、模型名条件分支、自研模型编排、第二套 History DTO、Obsidian 私自重建角色关系、为通过测试放松 Evidence。新增实现限于最小 schema 常量、直接 DTO 字段和小型校验。
