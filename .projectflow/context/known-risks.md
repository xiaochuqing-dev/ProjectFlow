# Known risks

- V3.4.3 仅支持 loopback stdio MCP；远程传输、远程身份、Telegram 与正式 Hermes 配置分发未实现，不能把本地验收描述为远程能力。
- Gateway audit 仍由 Hibernate `ddl-auto=update` 建表，没有 Flyway；必须持续保留当前 H2 安全副本、旧库升级和 PostgreSQL 门禁。
- Unified Search 是有界数据库候选与字段匹配，不是全文搜索引擎；超大项目需要按类型、时间和分页收窄，不能取消边界全量返回。
- Hermes 外部模型回答质量依赖宿主模型，但 ProjectFlow 只负责提供有界、可追溯、明确 SOURCE/DERIVED 的数据；不得持久化 Hermes 问题或答案为事实。
- 真实 Hermes 配置和 Provider Key 只能用于进程内隔离验收，不得复制或提交。
- V3.4.3 尚未实现 Obsidian 投影；查询无事实时必须明确无正式同步，不得推测。

- V3.4.2 能力表仍通过 Hibernate `ddl-auto=update` 升级，尚无 Flyway 版本；必须保留文件型 H2 安全副本和 PostgreSQL 16 Testcontainers 门禁，禁止删库规避升级问题。
- Provider 可能返回 Schema 漂移、遗漏 fact 或高风险 merge；严格校验会拒绝本次刷新并保留旧 READY，用户看到的是 stale/attention 而不是部分覆盖冒充成功。
- 旧能力卡片只有 CONFIRMED 且 sourceRefs 可追到 ProjectFact 时才迁移；无来源旧卡片继续留在兼容区，不能伪造事实关系。
- 42 条旧 attention 的确定性重分类只移除“发生时间回退”这一单一异常；质量问题、可能重复等其他原因继续保留 attention。
- 全历史 bootstrap 会增加首次模型调用和写入成本；120-fact chunk、job 请求/token/时长预算和 history 完成后统一刷新限制规模。
- Hermes 与 Obsidian 尚无正式同步协议；下一阶段只能消费 Facts、Timeline 与 Capabilities，不能成为新的事实源。
- 禁止通过修改系统文件或全局机器配置解决环境问题；只能使用仓库内和进程内方案。

- V3.4.1 Timeline 派生表仍通过 Hibernate `ddl-auto=update` 升级，尚无 Flyway 版本；必须保留复制库、当前真实 H2 与 PostgreSQL Testcontainers 验收，禁止以删库处理升级问题。
- Timeline summary 依赖外部 Provider，可能受网络、限流、模型 Schema 漂移和长响应影响；事实与确定性统计必须始终可读，旧 READY 必须保留，诊断不得保存 prompt、raw response 或 reasoning。
- 历史迁移与 backfill 可能短时间产生大量 dirty periods；启动迁移必须抑制逐批摘要任务，history chunk 只累计 dirty，完成后再用有界持久化队列处理。

- ProjectFact、FactCursor 和 HistoryState 仍依赖 Hibernate `ddl-auto=update`，尚无正式 Flyway 迁移版本；必须持续保留文件型 H2 旧库升级与 PostgreSQL Testcontainers 门禁，升级失败时不得删除用户数据库。
- 旧 DevelopmentSegment 和 ProjectSediment 的历史证据可能不完整；迁移只能幂等生成有证据事实，不能恢复不存在的模型诊断或伪造强事实。
- 历史 Git 可能存在 rewritten、orphan、浅克隆或无法定位时间的提交；应降级为可诊断 NEEDS_ATTENTION，不能阻塞已成功 chunk 或普通增量扫描。
- Fact fingerprint 绑定来源和证据可避免同一分析重复写入，但不同历史来源对同一客观事件的跨来源重复仍需保守保留或标记关注，不能用标题相似度破坏性合并。
- 长期事实量会持续增长；项目记录和记忆 read model 必须分页并使用 count/min/max/latest/projection，禁止 Java 全量装载和 batch→facts N+1。
- 旧能力分析仍依赖 ProjectSediment 输入；迁移到完整 fact-native 生命周期能力地图属于后续阶段，V3.4.0 不应伪装为已经完成。
- Hermes、Obsidian 和完整事实原生生命周期能力地图尚无稳定正式同步或生成协议；Timeline Theme 不得被当作长期能力。
- V3.3.8.1 对旧批次采用读时默认值而非回填，因此“历史数据不完整”只能保守提示，缺失的原始模型诊断无法恢复。
- Dashboard Bootstrap 当前依赖 Hibernate 派生 latest/count 查询；数据量继续扩大后仍需结合真实数据库索引与慢查询观察，但不得改回全量历史加载。
- sessionStorage 可能被浏览器清理或禁用；这是允许的，F5 必须回退到数据库 Bootstrap，缓存不能成为事实来源。

- 外部 Provider 的真实 max output、JSON mode、reasoning 控制和兼容响应形态可能随模型版本变化；未知 Provider 只能安全退化，仍需真实验收。
- 本机配置模型名为 `deepseek-v4-pro`，本次真实调用可用，但它不是代码内置能力规则；后续 Provider 改名时需重新验收。
- 大输入首次真实返回采用未知集合包装并触发 Schema repair；目标集合递归适配后复验成功，但更多 Provider 私有包装仍可能需要新增无敏感值的 shape diagnostics。
- 项目仍依赖 Hibernate `ddl-auto=update`。V3.3.8 补齐旧 H2 job status enum、计时列和 nullable worktree flag 修复，但尚无完整版本化迁移工具。

- 项目仍依赖 Hibernate ddl-auto update，没有版本化迁移工具；V3.3.7 新字段保持可空并在加载时补安全默认值，但生产级迁移审计仍有限。
- Java HttpClient 不暴露底层连接池容量配置，当前通过最多 4 个模型请求的公平信号量和最多 4 个任务线程形成实际并发上限。
- 正在进行的单次同步 HTTP 请求不能可靠强制中断；取消会阻止之后的重试、阶段和正式写入，当前请求会等待返回或单次超时。
- 本地 Playwright 使用系统 Edge，CI 安装隔离 Chromium；两者都运行真实前后端进程，但浏览器二进制不同。
- V3.4.1 Timeline 已用当前已配置 Provider 完成 WEEK、MONTH 与 LIFECYCLE 小样本验收；本轮没有独立安全测试 Key，因此未重复 V3.3.8 全入口真实 DeepSeek 压力矩阵。
- 固定兼容模型服务只证明结构化业务流程与失败分支，不证明真实 DeepSeek 的限流、长输出、网络延迟或 Provider 私有字段行为。
- 部分 Provider 会把主要推理放在 reasoning 字段而 content 为空；不得将其误报为普通空响应。
- 紧凑重试不能无限递归，也不能沿用原输出预算。
- 新扫描不得通过 DTO 或兼容分支重新进入人工 ProjectChange 主链；旧 ProjectChange 仍须可读。
- 能力分析失败不得覆盖上次成功卡片，也不得把待分析沉淀标为已处理。
- 新增字段必须兼容旧行；禁止要求用户删除数据库。
- 前端不得显示 API Key、原始模型响应、内部枚举或默认展开绝对路径。
- GitHub 刷新仍然只读，不执行 pull、merge 或 rebase。
