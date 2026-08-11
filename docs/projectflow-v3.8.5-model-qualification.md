# V3.8.5 模型资格与运行边界

当前状态：双 Provider 自动资格和受影响复验完成；最终产品状态仍为 PENDING_HUMAN_REVIEW。

正式配置：GLM `glm-5.2`、Ark Coding v3、`OPENAI_RESPONSES`、high；DeepSeek `deepseek-v4-flash`、OpenCode Go `/v1`、`OPENAI_CHAT_COMPLETIONS`、max。Key 只由 Repository Secrets 注入，V4 Pro 不在当前配置中。

模型调用只经 `ModelGatewayService`。工程层拥有 ID、角色、Chapter 成员、Before/Change/After 语义、Evidence 和 Strong Fact 门禁；模型只生成最小措辞合同。失败、取消、修复、窗口和缓存语义保持 Provider-neutral。

完整基线产生于编号占位符缺陷被发现之前；它们保持原样，只复用未受影响的 qualification、Dogfood 与非代码证据：

| 门禁 | GLM run 31523413972 | DeepSeek Flash run 31517037532 |
| --- | --- | --- |
| 19-case qualification | PASS；40 请求，156,848 token | PASS；35 请求，133,653 token |
| validation repair / failure | 6 / 0 | 1 / 0 |
| model degraded / failed-pending / rejected | 0 / 0 / 0 | 0 / 0 / 0 |
| full scenarios | 11/11；52 请求，884,266 token | 11/11；59 请求，945,342 token |
| ProjectFlow Dogfood / 五类非代码 | PASS / 5/5 | PASS / 5/5 |

受影响纠正复验 run `31532558352`：GLM 与 DeepSeek 均 1/1 PASS、3 次真实 Story 请求、64 Story、2 窗口、单窗口失效、最终 cache hit、编号占位符泄漏 0、repair 0。该范围明确复用完整资格、Dogfood 和非代码基线，不伪装为又一次完整 11 场景运行。

真实波动继续保留：run `31468663795` 的 DeepSeek 场景为 9/11；run `31517037532` 的较早 GLM 资格失败。固定兼容测试只证明执行器，不替代真实 Provider 资格。
