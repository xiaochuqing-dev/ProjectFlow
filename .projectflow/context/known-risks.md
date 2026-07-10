# Known risks

- 不要重新在 DisplayContentSanitizer 中加入持久化前长度截断；预览长度必须留在前端展示层。
- 不要把 finish reason 缺失直接视为正常结束；completion tokens 接近实际上限或 JSON 根结构未闭合时仍应识别为疑似截断。
- 紧凑重试最多一次，且必须缩小返回结构；不能原样无限重试或让轮询临时失败污染正式任务状态。
- 不要让失败的能力分析删除或替换上次成功候选；能力卡片无 analysisJobId 时只能标为旧版来源未知。
- Provider 编辑时空 Key 不能覆盖旧 Key；删除默认 Provider 前必须先选择替代项，重复清理不得自动删除。
- 新增字段保持 nullable/包装类型以兼容 H2 和 PostgreSQL 的既有行；不得要求用户重建数据库。
- 不要重新要求模型复制 UUID、提交哈希或内部 evidenceRefs；新增结构化入口应复用 S 编号映射和 ModelOutputAdapter。
- 不要在外部模型调用期间持有数据库事务，也不要在新候选生成成功前删除旧候选。
- 模型原始返回只用于后端诊断；主界面不得展示原始 JSON、异常栈或内部阶段枚举。
- Do not remove project creation, zip import, or local-path binding.
- Do not make GitHub PR/CI or GitHub CLI the primary workflow.
- Do not let GitHub CLI failures block local change analysis.
- Do not write unsupported AI inference into confirmed sediment.
- Do not show full diffs, payloads, source IDs, or absolute paths by default.
- Do not return to a “今日开发” business boundary or revive deprecated Agent-entry wording.
- Do not accept directory names, counts, or generic “development progress” wording as a completed segment.
- Do not silently present local fallback as successful model analysis.
- Do not let confirmation of one capability candidate change the status of sibling candidates.
- Do not let the quality gate discard a whole batch of model results when only some segments have quality issues — retain and tag them instead.
- Do not surface English commit messages as user-visible titles/summaries/capability names; keep English only in evidence details.
- Do not make GitHub a hard dependency or auto pull/merge/rebase; refresh is read-only.
- Do not fabricate low-quality local-template results as if they were full model analysis when a model is not configured.
- Do not show raw internal enums (CALL_FAILED / LOCAL_RULE / CONNECTED / local_ahead etc.) to users; always translate via status-labels.ts.
- Do not mark evidenceGap true solely because GitHub did not participate; base it on real evidence conditions.
- Do not let the capability analysis page lose its running task on refresh or navigation; it must be a recoverable async job.
- Do not let "打开登录终端" execute anything other than the fixed whitelisted command; never accept arbitrary commands from the frontend.
- Do not revive the "增强本地摘要" wording; use "本地事实摘要" and split failure reasons into plain Chinese.

