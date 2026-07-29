# ADR: Multi-agent Shared Project History

状态：Accepted for V3.7.4。

Project Memory Gateway 继续作为业务语义门面。多项目能力增加在该边界之上，不让 Agent 直接访问 Repository。

读能力包括授权项目目录、跨项目事实搜索、单项目 Evidence、强事实/声明/推断/冲突/未知分层、版本化 Context Package 和既有历史/Timeline/Capability 查询。每个结果都携带 projectId、实体或 Evidence ID、状态、currentness 和来源。

写能力单独进入 Agent candidate endpoint。Agent 可提交 assertion、evidence link、correction、conflict 或 review request；服务只创建 PENDING_ENGINEERING_VALIDATION 候选，不能创建、修改或 reclassify ProjectFact。

所有 REST 和 MCP 请求复用登录 userId 与项目所有权。未授权项目返回 not found；跨项目搜索先获得授权项目 ID 集，查询不能跨出该集合。绝对本地路径、凭据、prompt、raw response 和 reasoning 不返回。

