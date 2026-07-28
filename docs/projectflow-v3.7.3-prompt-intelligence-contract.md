# ProjectFlow V3.7.3 Prompt Intelligence Contract

Contract version：`project-understanding-prompt-contract-v1`

Scout prompt：`semantic-scout-v10`

Final prompt：`final-synthesis-v5`

Compatibility profile：`multi-provider-json-v1`

## 单一构建入口

生产 Semantic Scout、生产 Final Synthesis 和 direct Eval 共用 `ProjectUnderstandingPromptBuilder`。Builder 只接收项目 Evidence context、允许的 evidence IDs、eligible capabilities/views 和已校验 Tool Evidence；类型层不接受 case ID、Ground Truth、期望标签、评分或门槛。

固定 fixture hash 测试保护 Prompt 版本。Parity 测试证明修改 Ground Truth 标签不会改变 Prompt。Prompt、raw response 和 reasoning 不进入持久化或报告。

Scout 对 eligible capability 集合逐项输出 REQUEST 或 SKIP，不重复输出等价 Tool 数组。工程层兼容合并模型显式 REQUEST 的 `capabilityDecisions`/旧 `toolRequests` 编码，并保留结构字段更完整的一项；SKIP 不会被补成请求，完整性、Evidence allow-list、registry eligibility 和固定 Provider 边界继续后置校验。

Scout 输入中的 documents 是完整的入选来源短目录：每项保留 Evidence ID、category、relative locator 和摘要；仅最多 8 个跨类别来源携带 240 字符正文样本。结构表示按 kind、顶层模块和高价值引用去重后保留代表项。模型不逐项复述目录，只评估会改变结论、需要深读、应跳过或存在当前性/冲突风险的最多 20 个关键来源。

Eligible capabilities 是独立决策，不是互斥菜单。当前工程结构、文档正文、提交周期和 Tag milestone 分别由 MANIFEST、DOC_READER、GIT_HISTORY、GIT_TAG 校验；仓库材料同时存在当前结构与历史 roadmap 时，一个工具不得替代另一个独立信息缺口。

## 产品宪法

模型是 ProjectFlow 的证据分析组件，不是通用代码助手、项目经理或需求生成器。它只能根据提供的 Evidence：

1. 判断项目形态、Evidence 语义角色和重要性；
2. 陈述信息缺口并在 eligible 集合内请求能力；
3. 输出证据引用完整、可校验的 Dynamic Profile；
4. 保留 Unknown、Conflict、Currentness 和限制。

禁止用常识补全不存在的架构、后端、数据库、历史或演进，禁止输出下一步规划和 Chain-of-Thought。

## Evidence importance

语义重要性只使用 `HIGH | MEDIUM | LOW | UNKNOWN`，并必须同时给出：

- `semanticRole`
- `reason`
- `informationGap`
- `affectedDimensions`
- `shouldDeepRead`
- `confidence`

判断依据是材料能否定义项目、支持或否定能力、解决 Unknown、揭示冲突/过时风险、提供独特信息、影响适用视图，以及不读取可能造成的遗漏或错误。文件名、扩展名、README 身份、目录或工程采样分数不能直接决定语义重要性。

## 输出自检

结构化结果必须确认：引用只来自 allow-list；工具与视图只来自 eligible 集合；所有声明带 epistemic status；事实、推断、解释和用户主张不混同；没有未来规划、命令、绝对路径、凭证或自由探索。
