# ProjectFlow V3.4.2 context

ProjectFlow 的核心定位是“自动维护项目从创建至今的长期记忆”。主流程为“分析新变化 → DevelopmentSegment → 自动 ProjectFact → 项目记录 / 项目记忆 → 自动项目历程 → 全生命周期能力地图”。正常证据充分事实和能力演进自动记录，只有证据冲突、严重不足、事实边界不完整或无法安全去重等异常进入 attention。

DevelopmentSegment 属于单次分析，ProjectFact 属于长期事实。事实不按标题相似度主题合并，指纹优先绑定来源和证据；FactCursor 只在事实成功持久化后推进。历史 backfill 使用独立 cursor/checkpoint、bounded chunk 和 oldest-to-newest 顺序，已覆盖 commit 不重复模型分析。

分析结果的事实来源是数据库；sessionStorage 仅用于按项目快速恢复；Dashboard 使用轻量 Bootstrap Read Model 校准核心状态。弱 work session 数据不得覆盖完整 batch/segments，旧持久化字段必须 null-safe，单条不完整历史记录不能拖垮整个列表。Bootstrap 不能执行 Git、GitHub CLI、文件扫描或模型调用。

ProjectFact 是 Timeline 唯一事实来源。Timeline 按 factEventAt、统一时区和 DAY/WEEK/MONTH/LIFECYCLE 组织确定性统计；周、月和全周期摘要是全覆盖、可重建的派生数据，不包含下一步规划。Timeline Theme 只属于一个期间，不是能力。用户不保存或确认摘要，GET 不触发模型，失败刷新保留上次成功内容。

ProjectCapability 由全部 ProjectFact 分块 bootstrap，并按未覆盖或已变化 fact 增量刷新。每次 NEW、ENHANCE、ADD_EVIDENCE 或 MERGE 都写入 Evolution 和 fact relation；稳定身份不只看名称，成熟度由确定性指标解释，merge 非破坏性，失败保留旧 READY。Timeline Theme 仍不是 Capability。

ProjectChange、ProjectSediment、ProjectReviewCursor、旧 ProjectMemory、DevLog 和 ProjectCapabilityCard 仅作兼容，不再是新扫描、主时间视图或能力主链。Hermes 和 Obsidian 正式同步属于后续阶段。

所有模型入口必须使用 `ModelTaskType` 和统一网关。参数由 Provider/model capability、任务类型、输入规模和输出结构共同决定；不支持的字段不发送。JSON 语法、截断、Schema mismatch、reasoning 耗尽、证据拒绝和 Provider 故障保持不同语义与恢复路径。

V3.4.2 不重新设计 V3.4.0 Fact、V3.4.1 Timeline、V3.3.7 job infrastructure 或 V3.3.8 model gateway。真实 Provider、固定模型自动化和人工质量抽样必须分开记录。任何诊断都不得保存 Key、Authorization、reasoning 原文、完整 prompt、原始响应或未脱敏源码。禁止修改操作系统文件或全局机器配置。
