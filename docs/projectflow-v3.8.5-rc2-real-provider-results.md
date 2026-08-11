# ProjectFlow V3.8.5 RC2 真实 Provider 结果

自动真实模型证据已完成；人工可读性仍为 PENDING，因此不是 V3.8.5 最终 PASS。

正式配置不含凭据值：GLM 使用 `glm-5.2`、Ark Coding v3、`OPENAI_RESPONSES`、high；DeepSeek 使用 `deepseek-v4-flash`、OpenCode Go `/v1`、`OPENAI_CHAT_COMPLETIONS`、max。Key 只由两项 GitHub Repository Secrets 注入。

| 证据 | GLM | DeepSeek Flash |
| --- | --- | --- |
| 完整资格 | run `31523413972`，40 请求，156,848 token，repair 6/0 | run `31517037532`，35 请求，133,653 token，repair 1/0 |
| 完整场景 | 11/11，52 请求，884,266 token | 11/11，59 请求，945,342 token |
| Dogfood / 非代码 | PASS / 5/5 | PASS / 5/5 |
| 受影响纠正复验 | run `31532558352`，1/1，3 请求，68,769 token | run `31532558352`，1/1，3 请求，59,386 token |
| 受影响复验安全结果 | 64 Story、2 窗口、单窗口失效、cache hit、泄漏 0 | 64 Story、2 窗口、单窗口失效、cache hit、泄漏 0 |

所有正式工件只保存规范化结果，安全字段显示 Key、Prompt、raw response、reasoning 和机器绝对路径未持久化。两份受影响工件 SHA-256 分别为 GLM `0aff9ec06f28c73fd7a445d5e6385d4300bcbcb7b5584e1b4113b292a044e97a`、DeepSeek `97cb5bc097ad86cd5a78271af2e752d04e5be6487ad4ae9571baad2bc158869d`。

历史失败不删除：run `31468663795` 的 DeepSeek 场景为 9/11，run `31517037532` 的较早 GLM 资格失败。当前修复没有模型专属业务分支，也没有降低 Evidence、Strong Fact、ID、角色图或安全门禁。
