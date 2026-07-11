# Confirmed V3.3.7 decisions

- 同用户、项目、任务类型和输入指纹只允许一个活动任务，后端通过项目行锁保证并发幂等。
- retry 只能忽略已完成历史，不能绕过活动任务唯一性；新 retry 持久化来源任务和原因。
- 排队和运行任务可取消；模型调用和正式保存前必须重新读取持久化取消状态。
- 执行器、队列、模型 HTTP 并发、总请求次数、总耗时和 token 均使用保守上限。
- 重启时只重新排队从未开始的 QUEUED；模型请求可能已发送的任务标为 INTERRUPTED，禁止自动重复收费。
- PostgreSQL Testcontainers、H2 兼容、生产构建和 Playwright 是独立质量层；真实 DeepSeek 只在显式开关与安全 Key 同时存在时运行。

- 空 content 不等于普通空响应；若 finish reason 为 length、用量接近上限或存在 reasoning 内容，则按疑似截断处理。
- 紧凑重试最多一次，输出预算 2000 tokens，完整调用总次数最多 3 次。
- 正式建议只来自有证据绑定的 MODEL 开发推进段；LOCAL_RULE 和 AGENT_RESULT 仅保留为本地草稿。
- 沉淀处理以分析批次为入口，批次列表展示摘要，详情页逐条处理正式建议。
- 推荐强度分为强、中、仅供参考、不推荐；强推荐必须同时满足证据和目标相似度条件。
- 能力分析输入改为已确认项目沉淀；失败不改变沉淀的待分析状态。
- 外部 Git、GitHub、模型调用均不放在数据库长事务中。
- 新增数据库字段使用可空或安全默认值，兼容 H2 与 PostgreSQL 既有数据。
- optimistic version 列使用数据库默认值 0 兼容旧行；Java 新实体仍以 null version 进入 persist，避免误判为 detached。
