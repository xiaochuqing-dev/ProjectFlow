# ProjectFlow V3.7.3 Prompt Calibration

## V3.7.2 失败信号

同一 GLM 的 V3.7.2 38-run 有 19 次 timeout；有效输出的 Tool recall 为 0.1667、Dynamic View recall 为 0.0941、Conflict Detection 为 0.1111、Repeatability 为 0.4130。八个生产链案例仅 2 个完成。该结果只用于定位，不修改 Ground Truth、公式或门槛。

## V6/V7/V8/V9/V10 校准

- 用三步固定协议替代松散建议：先评估全部 Evidence，再说明信息缺口和 capability intent，最后生成受证据支持的适用视图。
- 工具请求改为对象，必须包含 capability、information gap、expected evidence value、target evidence IDs 和现有证据不足理由。
- 在 Prompt 中提供工程计算的 eligible capability/view 集合；模型负责语义选择，工程负责后置验证。
- 明确原子、多标签且稳定的 shape vocabulary，并兼容别名 normalization。
- 具体代码形态优先：已有 FRONTEND、BACKEND、DESKTOP 等受证据支持的标签时，不再重复堆叠泛化 CODE_PROJECT；生产链断言验证具体主形态，不要求冗余标签。
- 给常见项目形态建立稳定的核心 View 映射，模型仍须按 Evidence 自己选择适用 View；工程注册表只做 eligibility、别名 normalization 和后置拒绝，不补造模型未选择的 View。
- 多个独立 information gap 必须分别请求全部适用能力。Planner 只接受模型给出的完整结构化请求，registry 拒绝不可用或危险能力；工程可用性不会自动升级成“值得调用”。
- 明确 discovery 候选与 `tool:` Evidence 的成熟度差异，并增加 View/Tool 跨字段一致性：模型选择依赖、架构、文档当前性、冲突或历史维度时，必须自行给出对应的完整请求，否则删除该维度或保留 UNKNOWN。
- Conflict 使用稳定对象与性质描述；Final 以 Stage 1 Section type 为基线，只有新增 Tool Evidence 改变适用性时才增删。
- Section 使用 `OBSERVED | INFERRED | UNKNOWN`；Claim 使用 `CURRENT_STATE | HISTORICAL_EVENT | POSSIBLY_STALE | PROCESS_EVIDENCE | PROCESS_METADATA | USER_ASSERTION | ENGINEERING_OBSERVATION | INFERRED | UNKNOWN`，防止过程材料和历史描述被提升为当前事实。
- Final Synthesis 只使用 Stage 1 和新增 Tool Evidence 收口，不重新自由探索。
- 增加短 self-check，要求引用、工具、视图、冲突和当前性边界完整；不要求或保存思维链。

## 校准纪律

每次 Prompt 变化先跑 fixture/parity/leak/Schema/eligibility 静态测试，再跑八个重点案例 2 次，最后才允许原始 38-run。正式评测仍使用原始 Ground Truth 和原门槛。真实结果记录在 model evaluation 与 acceptance report，不在本文预写。

Repeatability 不比较逐字相同的自然语言，也不因模型额外给出受支持的补充内容而扣分；它比较 Ground Truth 标注的关键 Shape、Evidence、Tool、View、Conflict 决策在重复运行中的一致性。must-not、unsupported claim、precision 和 recall 继续由独立门禁约束，不能用稳定性指标替代准确性。Stage 2 evidence gain 同时识别 Final 新引用的已校验 `tool:` Evidence，因为 Scout 选择待深读 source 并不等于已经获得 Provider 正文。两项修正都不改变 Ground Truth 或门槛，只纠正旧公式把合理改写、受支持的补充内容和真实深读增益误报为不稳定或零增益的问题。

一次 v5 高风险小批曾由 Planner 补齐客观可用 Tool/View，虽然得到高指标，但越过“模型判断值不值得调用”的职责边界，因此该结果作废。移除全部语义补齐后，同一八类输入 8/8 完成、0 timeout、Evidence Recall 0.9333、Dynamic View Recall 1.0000、Stage 2 Evidence Gain 1.0000，但 Tool Recall 真实降为 0.6667。v6 只根据这些跨案例漏报加强共享 Prompt 的 discovery/tool 成熟度和 View/Tool 一致性，不恢复工程补齐、不修改 Ground Truth 或门槛。

v6 八类复测为 8/8 完成、0 timeout、Evidence Recall 0.9333、Tool Recall 0.8333、Dynamic View Recall 1.0000、Stage 2 Evidence Gain 1.0000。为避免 0.80 门槛附近的随机漏评，v7 要求模型对每个 eligible capability 显式输出一次 REQUEST 或 SKIP；REQUEST 同时携带完整信息缺口契约。生产与 Eval 共用的归一化只合并模型显式 REQUEST 的重复编码，并选择字段更完整的一项；SKIP 不会被工程层升级为请求，仍没有 Tool/View 语义补齐。

v7 的 38-run 达到全部数值门槛，但生产大案例暴露 capabilityDecisions 与 toolRequests 重复编码会扩大长 JSON，并在 32k Provider 上限内连续截断。v8 保留逐项 REQUEST/SKIP 和全部 Evidence/View/Claim 决策，只删除重复 Tool 数组并限制自然语言字段长度；兼容层仍可读取旧 toolRequests、根层 Scout 和只含 capabilityDecisions 的输出。该调整不减少输入 Evidence、深读或 Final 资格。

v8 的直接 Eval 已能完成 ProjectFlow fixture，但真实大仓库 refresh 仍在 32k 输出上限截断。审计发现 Discovery 虽已按类别与模块选择候选，Prompt 上下文却重复携带每项长样本、模块、重要节点、Functional Area 和 Structure Evidence；完整 JSON packer 最终只能保留数组前缀。v9 不删除入选 Evidence：默认 Scout 候选由 80 收紧为与输出契约一致的 40，全部保留 ID/category/relative locator/短摘要，仅最多 8 个跨类别来源携带 240 字符样本；结构按 kind、顶层模块和优先引用去重为代表项。模型只评估真正改变结论、深读、跳过、当前性或冲突判断的最多 20 个来源，不逐项复述目录。

v9 的 8 类高风险复测为 8/8 完成、0 timeout、Evidence Recall 0.9333、Dynamic View Recall 0.9375、Stage 2 Evidence Gain 1.0000、无多余或不可用工具，但 Tool Recall 为 0.7500。唯一漏项来自“当前仓库结构 + 历史 roadmap”组合：模型只选择 DOC_READER，把 manifest、提交周期和 Tag anchor 三个独立 gap 一并 SKIP。v10 明确四类来源解决不同问题，一个工具不能替代另一个；仍由模型输出独立完整 REQUEST，工程层不补齐。
