# ProjectFlow V3.8.5 RC2 最终验收

当前结论：PENDING_HUMAN_REVIEW / NOT PASS。PR #15 继续保持 Draft，不创建 Tag 或 Release。

真实模型配置为 GLM `glm-5.2` / `OPENAI_RESPONSES` / high，以及 DeepSeek `deepseek-v4-flash` / `OPENAI_CHAT_COMPLETIONS` / max；V4 Pro 未使用。GLM 完整基线来自 run `31523413972`，DeepSeek Flash 完整基线来自 run `31517037532`，两者资格与 11/11 场景均通过，包含 ProjectFlow Dogfood 和五类非代码材料。

首次 Round 2 候选暴露中文编号占位符后，没有继续封板。Provider-neutral 修复在 code head `aee0160cf1d4cf11224055548107098fd12e6de1` 上由 run `31532558352` 复验：两家均 1/1 PASS、3 次真实 Story 请求、64 Story、2 窗口、只失效纠正目标窗口、最终 cache hit、泄漏计数 0、validation repair 0。

本地后端/H2 全量为 579 项、0 失败、0 错误、5 个条件跳过。Round 2 已冻结 30 Story / 8 Chapter，双 Provider 各 15/4；清单和复核表的人工字段全部空白，不能由模型或本 Agent 代填。

必须保留的波动包括 run `31468663795` 的 DeepSeek 场景 9/11，以及 run `31517037532` 中较早的 GLM 资格失败。后续成功不覆盖这些事实。没有为某一模型写业务特判，也没有降低 Evidence、Strong Fact、ID、角色图或安全门禁。

只有真实人工复核明确通过，且最终 GitHub required checks 全绿，才可退出 Draft、合并 PR #15、验证 master、完成 acceptance backfill 和安全清理。此前不得宣布 V3.8.5 PASS 或进入新阶段。
