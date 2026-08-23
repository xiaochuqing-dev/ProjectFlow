# ProjectFlow V3.8.5 RC2 定向复用决策

本轮没有引入新框架或复制外部实现。继续复用 ModelGatewayService 的协议 adapter、现有 checkpoint/cache、corrected presentation view、Gateway 分页/revision、Obsidian 投影边界和前端 details 渐进展开。

OpenCode Go 官方文档明确列出 DeepSeek V4 Flash 的模型 ID `deepseek-v4-flash` 与 Chat Completions endpoint `https://opencode.ai/zen/go/v1/chat/completions`，因此 workflow 使用 base URL `https://opencode.ai/zen/go/v1`、Chat Completions 和 Flash；没有切换到 V4 Pro。来源：https://opencode.ai/docs/go/

DeepSeek V4 Flash 官方 encoding 文档说明 thinking 模式把 reasoning 与最终 response 分开，并提醒解析器只处理格式正确输出、生产环境需要额外错误处理；同一文档定义 `reasoning_effort=max` 的最大思考前缀。RC2 因而复用现有 Gateway 边界增加“reasoning 后必须有可见 JSON”的一次恢复，不读取、不复用、不保存 reasoning，也不增加第三次请求。来源：https://huggingface.co/deepseek-ai/DeepSeek-V4-Flash/blob/main/encoding/README.md

上一轮 GLM 单 job 实测 5 小时 45 分，接近 GitHub-hosted 每 job 6 小时上限。workflow 只把同一组真实门禁拆为 qualification 与 scenarios 两个依赖 job，保持配置、顺序、工件和失败语义不变。来源：https://docs.github.com/en/actions/reference/limits

未采用：Provider 专属 Prompt、模型名条件分支、自研模型编排、第二套 History DTO、Obsidian 私自重建关系、放松 Evidence/Strong Fact、静默增加模型重试。DeepSeek attempt 1 的 17-window 波动通过同 SHA 的失败 job 重跑验证，没有伪装成首次成功。
