# ProjectFlow V3.8.5 RC2 最终验收

当前结论：BLOCKED，仅剩真实人工可读性门禁以及通过后的 GitHub 合并、master 复核、acceptance backfill 和安全清理。PR #15 继续保持 Draft；不创建 Tag 或 Release。

当前代码 head 为 `74ba013615932748b4a41077baf8f89af618a5d2`。该 head 的 push run `31317712835` 与 PR run `31317716057` 全部 required jobs 通过。后端最新全量为 557 项、0 失败、0 错误、6 个条件跳过；根启动脚本已从当前工作树完成 Next.js 生产构建、Java 17 后端启动和前后端就绪检查。

真实 workflow [`31318477841`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31318477841) 使用 GLM `glm-5.2` / `OPENAI_RESPONSES` / high，以及 DeepSeek `deepseek-v4-flash` / `OPENAI_CHAT_COMPLETIONS` / max。两者 provider probe、V3.8.0 合同、V3.7.5 38-run、Understanding 17/17、V3.8.5 19-case qualification 均通过；最终场景均为 11/11，包含 ProjectFlow Dogfood 与五类非代码材料，安全持久化计数全为 false/0。

必须保留的波动：DeepSeek 场景 attempt 1 为 9/11；17-window 首轮留下 1 failed、1 pending，correction 因前置 fixture 不可用连带失败。相同 head、相同 Flash/max 配置只重跑失败 job 后 attempt 2 为 11/11。没有为模型写业务特判，也没有降低 Evidence、Strong Fact、ID 或角色图门禁。

已从最终归一化工件冻结 30 Story / 8 Chapter，双 Provider 各 15/4，清单和复核表位于 `docs/acceptance-evidence/v3.8.5/`。当前状态为 `PENDING_HUMAN_REVIEW`，评分全部空白，不能由模型或本 Agent 代填。

只有真实人工复核达到既定平均分 4.0 且没有直接失败项，才可更新报告、退出 Draft、合并 PR #15、验证 master CI、完成 acceptance backfill 和清理。此前不能输出 PROJECT HISTORY HUMAN-READABLE QUALITY = PASS、V3.8.5 FINAL ACCEPTANCE = PASS 或 V3.9 ENTRY = APPROVED。

非阻断风险：只读 npm audit 仍为 4 high、0 critical；RC2 没有静默执行依赖升级。
