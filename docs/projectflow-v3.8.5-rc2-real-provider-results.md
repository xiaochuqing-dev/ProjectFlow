# ProjectFlow V3.8.5 RC2 真实 Provider 结果

最终自动化状态：PASS。人工可读性仍为 PENDING，因此这不是 V3.8.5 最终 PASS。

正式配置来自 workflow，不含凭据值：GLM 使用 `glm-5.2`、`https://ark.cn-beijing.volces.com/api/coding/v3`、`OPENAI_RESPONSES`、high；DeepSeek 使用 `deepseek-v4-flash`、`https://opencode.ai/zen/go/v1`、`OPENAI_CHAT_COMPLETIONS`、max。Key 只由 `PROJECTFLOW_REAL_MODEL_API_KEY` 与 `PROJECTFLOW_DEEPSEEK_API_KEY` Repository Secrets 注入。

最终 run 为 [`31318477841`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31318477841)，head `74ba013615932748b4a41077baf8f89af618a5d2`。

| 门禁 | GLM | DeepSeek Flash |
| --- | --- | --- |
| V3.8.0 schema/security | PASS；1 请求，5,131 token | PASS；1 请求，3,846 token；reasoning present |
| V3.7.5 38-run | 38/38；52 请求，521,726 token | 38/38；64 请求，663,829 token |
| Understanding E2E | 17/17 | 17/17 |
| V3.8.5 19-case | qualified=true；20 请求，97,269 token，863,220 ms | qualified=true；21 请求，121,540 token，473,875 ms |
| 19-case 失败/降级/拒绝/修复 | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 |
| 最终真实场景 | 11/11；68 请求，871,777 token，5,266,928 ms | 11/11；70 请求，962,976 token，2,158,891 ms |
| ProjectFlow Dogfood | PASS | PASS |
| 五类非代码 | 5/5 | 5/5 |
| 安全持久化 | Key/Prompt/raw/reasoning/绝对路径均 false | Key/Prompt/raw/reasoning/绝对路径均 false |

DeepSeek 场景 attempt 1 必须保留：9/11，54 个物理请求、826,943 token、1,794,354 ms；17-window 首轮为 15 succeeded、1 failed、1 pending，随后 correction 因 continuation fixture 不可用连带失败。相同代码、相同 Flash/max 配置只重跑失败 job 后 attempt 2 为 11/11；这被记录为真实模型输出波动，不倒推成 Provider 专属业务修复。

历史失败链同样保留：最初 GLM/DeepSeek 19-case qualification 均 FAIL；旧 DeepSeek 场景 10/11 且 Dogfood 角色引用失败；run `31264440534` 因 Secrets 缺失在请求前失败；run `31294942095` 中 GLM qualification FAIL/场景 8/11、DeepSeek Understanding 16/17；run `31303975027` 中 GLM 自动门禁通过、DeepSeek Understanding 16/17，失败为 reasoning 存在但可见 content 为空。当前修复保持 max 思考，并把唯一恢复限制在两次请求内；最终 run 的 DeepSeek Understanding 已为 17/17。

仓库只保存 `docs/acceptance-evidence/v3.8.5/real-model/` 下的归一化结果；不保存完整 Prompt、raw response、reasoning、Key、Authorization、机器绝对路径或私有项目内容。
