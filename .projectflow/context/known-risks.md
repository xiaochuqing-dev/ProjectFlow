# Known risks

- 2026-08-03 的 `npm audit` 对当前前端依赖图报告 3 个 high，涉及 PostCSS 的 source map 文件读取/路径问题与 Sharp/libvips 继承漏洞，并聚合影响 Next。注册表当前自动修复建议是破坏性的 Next 9.3.3 downgrade，不能用 `npm audit fix --force` 代替兼容性判断；应等待或选择官方兼容修复版本，升级后重跑 lint、contracts、production build、Playwright 和根启动器。
- V3.8.0 的 Story/Chapter 标题仍以确定性主体名为基础，真实模型只在有默认 Provider 且窗口合格时做一次有界措辞增强；无模型或模型失败时可读性保守，但成员和 Evidence 不受影响。
- Git `--all` 会覆盖本地可达分支，不等同于远端全部协作历史；浅克隆、force-push、已删除远端分支和未授权 GitHub 元数据会形成明确覆盖缺口。
- 当前增量策略使用受影响时间窗口与 31 天 overlap，能重建常见新增和 rewrite 范围，但超长跨年语义链仍依赖稳定 subject/Evidence；不会用模型相似度强行连接。
- Source event 和 snapshot schema 仍由 Hibernate `ddl-auto=update` 管理，没有 Flyway/Liquibase。必须继续用旧 H2 升级与 PostgreSQL 16 CI 验证，禁止删库规避。
- 最小 `/history` 页面只验证信息层次和深链接，不是最终 GUI；筛选、虚拟滚动、跨篇章可视化和完整可访问性设计仍是 V3.9 进入条件。
- Obsidian Advanced URI、Local REST/MCP、Dataview/Bases 均为可选增强。零插件官方 URI 只能稳定打开 Vault/文件，标题或块级定位需要插件能力并必须安全降级。
- DeepSeek 与 GLM 专项结果只适用于当日模型、协议、Prompt v2 和冻结输入，不是任意项目准确率承诺。GLM 38-run 的 Conflict Detection 为 0.6667、Deep-read Sufficiency 为 0.8333，仍需把冲突和未读范围作为显式限制保留，不能因总门禁通过而隐藏。

- API Key 与自定义 Header 值当前仍保存在应用数据库，只适合本地兼容；桌面产品化前需迁移到 OS secure store。
- 固定本地 relay 覆盖协议、SDK 和恢复契约，不代表真实 OpenAI、Anthropic 或 DeepSeek 的输出质量、限流与私有扩展。
- Endpoint override 只支持保留标准协议尾路径的非标准前缀；完全自定义非标准协议不在 V3.4.5。
- 早期迁移 facts 存在泛化摘要，部分 capabilities 合并了多个关注点；只能通过显式 reconciliation/evolution 改善，不能静默改事实。

- Obsidian V3.4.4 是手动 one-shot CLI，不包含 watcher、后台定时器或正式前端配置；自动同步留给后续受控设计。
- Projection manifest 是可重建状态而非事实源；用户复制同一受管实体形成多个 Note 时必须进入 conflict，由用户消除重复，不能自动猜测。
- CORE 的月度 Fact Index 会随单月事实量增长；当前有总输入、分页和响应边界，超大项目仍应观察单月文件可读性，不能退化为默认一 Fact 一文件。
- 原子替换依赖本地文件系统语义；只支持现有本地 Vault 与 loopback ProjectFlow，网络盘、远程 Vault 和跨设备原子性未承诺。
- FULL_FACTS 会显式生成大量文件，只适合用户主动选择；默认始终是 CORE。

- V3.4.3 仅支持 loopback stdio MCP；远程传输、远程身份、Telegram 与正式 Hermes 配置分发未实现，不能把本地验收描述为远程能力。
- Gateway audit 仍由 Hibernate `ddl-auto=update` 建表，没有 Flyway；必须持续保留当前 H2 安全副本、旧库升级和 PostgreSQL 门禁。
- Unified Search 是有界数据库候选与字段匹配，不是全文搜索引擎；超大项目需要按类型、时间和分页收窄，不能取消边界全量返回。
- Hermes 外部模型回答质量依赖宿主模型，但 ProjectFlow 只负责提供有界、可追溯、明确 SOURCE/DERIVED 的数据；不得持久化 Hermes 问题或答案为事实。
- 真实 Hermes 配置和 Provider Key 只能用于进程内隔离验收，不得复制或提交。

- V3.4.2 能力表仍通过 Hibernate `ddl-auto=update` 升级，尚无 Flyway 版本；必须保留文件型 H2 安全副本和 PostgreSQL 16 Testcontainers 门禁，禁止删库规避升级问题。
- Provider 可能返回 Schema 漂移、遗漏 fact 或高风险 merge；严格校验会拒绝本次刷新并保留旧 READY，用户看到的是 stale/attention 而不是部分覆盖冒充成功。
- 旧能力卡片只有 CONFIRMED 且 sourceRefs 可追到 ProjectFact 时才迁移；无来源旧卡片继续留在兼容区，不能伪造事实关系。
- 42 条旧 attention 的确定性重分类只移除“发生时间回退”这一单一异常；质量问题、可能重复等其他原因继续保留 attention。
- 全历史 bootstrap 会增加首次模型调用和写入成本；120-fact chunk、job 请求/token/时长预算和 history 完成后统一刷新限制规模。
- Hermes 与 Obsidian 都只能消费 Gateway，不能成为新的事实源；远程接入与外部写回仍无正式协议。
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
- V3.7.5 兼容模型验收使用 `deepseek-v4-flash`、显式 JSON Mode 与 high reasoning。该能力来自 Provider 配置而非模型名特判；Provider 行为或模型标识变化时仍需重新验收。
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
