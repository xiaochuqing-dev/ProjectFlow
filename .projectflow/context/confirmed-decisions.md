# Confirmed V3.3.8.1 decisions

- 分析结果的事实来源是数据库；sessionStorage 仅用于快速恢复；Dashboard 使用轻量 Bootstrap Read Model 校准核心状态。
- session-only 弱响应不得清空已有 batch 或非空 segments；数据库权威响应才可明确清空。
- Dashboard snapshot 使用 projectId 隔离和 schemaVersion 2，旧单 key 只做一次兼容迁移。
- Bootstrap 只读持久化项目、memory、最新 job/batch/segments/sessions、待处理统计、分析摘要和 Provider 可用性；禁止 Git、GitHub CLI、模型和文件扫描。
- 历史 batch/change/segment 缺失字段在读取边界保守降级；不做破坏性回填，单条坏数据不得拖垮列表。
- 批次列表使用固定批量查询，不允许恢复逐批次 changes/segments 的 N+1。
- 开源 Windows 快速启动入口使用仓库相对路径，首次或 package-lock 变化时安装前端依赖，每次重建当前工作树；它不修改 Git 历史，并记录可核对的版本、提交、本地修改标记和前端 Build ID。
- V3.3.7 后台任务和 V3.3.8 模型可靠性边界保持不变。

- 6 个真实模型入口统一登记并通过网关，Provider 测试不再自行发送 HTTP。
- Temperature 配置值不再被全局 0.3 封顶；不支持时省略。任务建议值只用于策略与诊断，不冒充用户配置。
- Max Tokens 按任务、输入规模、输出结构、reasoning 行为和 Provider ceiling 动态计算；取消固定 4000/2000。
- Schema mismatch 进入一次定向重编码；截断和 reasoning 耗尽分别提高预算恢复。所有恢复共享任务请求/token/耗时上限。
- diagnostics 不保存 Key、Authorization、reasoning 原文、完整 prompt 或原始响应。

- 同用户、项目、任务类型和输入指纹只允许一个活动任务，后端通过项目行锁保证并发幂等。
- retry 只能忽略已完成历史，不能绕过活动任务唯一性；新 retry 持久化来源任务和原因。
- 排队和运行任务可取消；模型调用和正式保存前必须重新读取持久化取消状态。
- 执行器、队列、模型 HTTP 并发、总请求次数、总耗时和 token 均使用保守上限。
- 重启时只重新排队从未开始的 QUEUED；模型请求可能已发送的任务标为 INTERRUPTED，禁止自动重复收费。
- PostgreSQL Testcontainers、H2 兼容、生产构建和 Playwright 是独立质量层；真实 DeepSeek 只在显式开关与安全 Key 同时存在时运行。

- 空 content 不等于普通空响应；若 finish reason 为 length、用量接近上限或存在 reasoning 内容，则按疑似截断处理。
- 输出恢复最多一次；V3.3.8 已用分型动态预算取代固定 2000 tokens。
- 正式建议只来自有证据绑定的 MODEL 开发推进段；LOCAL_RULE 和 AGENT_RESULT 仅保留为本地草稿。
- 沉淀处理以分析批次为入口，批次列表展示摘要，详情页逐条处理正式建议。
- 推荐强度分为强、中、仅供参考、不推荐；强推荐必须同时满足证据和目标相似度条件。
- 能力分析输入改为已确认项目沉淀；失败不改变沉淀的待分析状态。
- 外部 Git、GitHub、模型调用均不放在数据库长事务中。
- 新增数据库字段使用可空或安全默认值，兼容 H2 与 PostgreSQL 既有数据。
- optimistic version 列使用数据库默认值 0 兼容旧行；Java 新实体仍以 null version 进入 persist，避免误判为 detached。
