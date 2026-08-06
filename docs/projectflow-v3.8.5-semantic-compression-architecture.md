# V3.8.5 多层语义压缩架构

数据路径

`Raw Source Event → Technical Change Atom → Supporting Change / Primary Change → Change Story → Chapter → Evolution Thread`

Raw Event 是保留来源身份、时间、transition、authority、epistemic status、Evidence 和 rewrite state 的库存。Technical Atom 是一个 Commit 内的有界技术变化。Primary Change 表示用户或项目结果；Supporting Change 保留测试、文档、配置和验证的支撑关系。Story 组合一个完整成果，Chapter 按真实时间和成果边界组织，Thread 连接同一对象的创建、修改、移除、恢复、替换、拆分、合并、撤销和重做。

确定性职责

工程代码决定事件成员、时间顺序、transition、Primary/Supporting 角色候选、Chapter 边界、Thread 连续性和 Evidence 归属。它不使用固定 Story 数硬裁剪，也不因压缩删除原始事件。技术名词和 Commit 细节只在工程详情中保留。

模型职责

模型只接收已允许的 Story/Chapter ID 和安全压缩上下文，只能改写中文标题、摘要和展示 role。未知 ID、成员、时间、Evidence、原因、成熟度和成功判断都会被拒绝。模型失败、取消、格式无效或容量不足时保留确定性结果并标记降级。

窗口执行

`ProjectHistoryWindowPlanner` 使用最多 32 个 Story 或 360 个 Event 的稳定窗口，执行上限为 16 个窗口。`planAll` 用于披露总范围，`plan` 用于有界执行。cache key 包含 source fingerprint、strategy、Prompt、窗口身份和 presentation revision；成功缓存必须精确覆盖 Story ID 和 Chapter ID 集合。失败、取消、运行中和跳过 checkpoint 会阻止全局缓存命中。
