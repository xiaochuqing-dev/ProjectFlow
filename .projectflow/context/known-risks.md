# Known risks

- V4.0-A 只完成 GUI Foundation 与 IA 合同，未完成最终 GUI。READY_FOR_OWNER_REVIEW 不等于 FINAL PASS；Owner 尚需确认导航、项目第一屏、Current State / History / Agent Context 层级、旧入口降级和 Desktop Shell defer。
- DESKTOP_SHELL_DECISION = DEFERRED。Electron 更容易承载现有 Next standalone/loopback runtime，Tauri 官方 Next 路径偏静态导出；两者对 ProjectFlow 实际启动、包体、内存、缩放、安全、安装和 CI 的结果尚无同机 PoC，不能宣称任一方更轻或已选定。
- 现有 Current State 的 Chapter/Thread 主要以引用返回，Project Library 也没有 compact 的真实变化、覆盖和注意摘要。V4-B 应先使用现有字段；只有实际请求与性能证据成立时，V4-C 才可窄幅扩展既有 History/Gateway read model，禁止创建第二套 truth。
- Agent Context Package 后端已存在 persisted-only 读取，但前端尚无 typed client 和正式页面。Obsidian 目前只有显式 CLI，没有 Web REST status；V4 UI 不得假装已具备 Obsidian 连接/同步状态。
- 旧 Dashboard、Timeline、Project Intelligence、Sediment、Dev Logs、Tasks 和多套历史/记录入口仍保留。V4-A 只定义主导航降级与迁移目标，不删除 route；实际 redirect/remove 必须等 V4-C 能力吸收、deep link 和回归验收。
- 现有 shared primitives、Toast、手写 Dialog、focus、reduced-motion 与 ad-hoc Tailwind 尚未构成完整设计系统。V4-B 必须用真实 prototype 验证无障碍、长中文、900×600 内容视口候选和 Windows 125%/150% 缩放，不能把 V4-A 文档当实现证据。
- V4.0-A 的 CI 新发现 Spring Boot 3.5.15 仍管理 Tomcat 10.1.55，以及两个前端传递开发依赖处于已知漏洞版本。本阶段以 `tomcat.version` 10.1.59 和最小 lock-only 更新处置，未升级框架或增加依赖。未来升级 Spring Boot 时必须复核该属性是否仍必要；前端不能用 production-only npm audit 代替全锁文件 OSV 门禁。
- V3.10 的实现、exact V3.9 H2/PostgreSQL 升级补证、三 Provider secure smoke、最终功能 Head 门禁、PR #19 merge 和 merge 后 master Quality/Windows 已通过。正式 FINAL 与 V4.0 Entry 只在本事实回填 PR 自身 required CI/merge、最终 master 复验和任务临时资源清理完成后生效；任一后续 required gate 失败仍必须 BLOCKED。
- V3.10 不提供 Flyway DOWN migration；二进制回退前必须核对 schema 兼容性。外部 PostgreSQL 只强制显式备份确认，真实 `pg_dump` 的安全存放、恢复演练和保留策略仍属于运维者责任，不宣称为企业级备份。
- Windows Provider 凭据由 current-user DPAPI 绑定当前用户和机器；迁移用户或设备时需重新配置，不存在 plaintext fallback。非 Windows release 环境在没有可用 secure store 时会明确降级/阻断需凭据的操作。
- 最终 GUI、短 ID、正式中文产品标签、信息密度、安装引导和自动更新属于 V4.0；V3.10 只冻结 release foundation，未开始 V4 GUI。
- V3.9 已按 Owner 授权的自动化加独立语义复核模式关闭：功能 Head `f3c3adbd79206fc21a8a5209774a0b71ef47e185` 的三 Provider 独立盲评 12/12、Chapter 9/9、continuity 3/3，普通 CI、merge 后 master CI 与干净主线启动器均通过。原真人 Continuity worksheet 仍为 `NOT_REVIEWED`；不得把关闭模式误写为 HUMAN_REVIEW_PASS，也不得补造评分。V3.9 acceptance backfill、最终 master 验证和清理已完成，V3.10 从其 final master `dd5ee41b6afcbd7703fa0883dc115c11f4821447` 开始。
- Current Project State 是 corrected History 的派生展示，不是 ProjectFact。它能诚实显示 READY/STALE/DEGRADED/UNKNOWN，但不能证明“完成、成熟、已部署、生产可用”；消费者不得把 state revision 当作事实 Evidence。
- ProjectFlow 内部 Agent candidate、Correction、Fact ingestion 会写 continuity dirty revision，但不会自动刷新。外部 Git/文件变化没有 watcher，只能由下一次显式 source discovery 发现；不得宣称实时监控。
- 数据库 Agent candidate 单次最多采集 200 条，超过上限会标记 coverage incomplete。Agent 声明仍是 PROCESS_EVIDENCE；即使后续与 Git Evidence 相关，也不能因自报或模型共识自动提升为 Strong Fact。
- V3.9 曾由 Hibernate `ddl-auto=update` 管理 Snapshot dirty 列与 Correction membership refs。V3.10 release profile 现由 Flyway 唯一持有 schema，Hibernate 只做 `validate`；exact V3.9 H2/PostgreSQL 旧库门禁持续防止用删库规避兼容性。
- Additive Correction replay 只在有界旧 membership 完整且全部仍存在时成立。旧行无 refs、refs 截断或 rewrite 替换必须保持 conflict；保守 attention 可能多于理想自动 rebind，但优先避免错绑。

- V3.8.5 已由项目所有者明确批准并豁免本轮量化人工评分，结论只能写 `PASS_BY_EXPLICIT_OWNER_OVERRIDE`。Story/Chapter 三项平均分和核心维度最低分仍未提供，原定量阈值没有得到证明；最终只有一名评审人，批准消息没有重确认 30 Story/12 Chapter item count，P0=0 只表示没有新报告。这些限制必须在最终报告和 backfill 中持续披露。
- PR #15 merge 后 master run `32652683003` 与 V3.9 runs `32659635453`/`32666372066` 均提示 Node 20 action runtime 和 `setup-java@v4` 弃用；当前 runner 强制使用 Node 24 后任务仍通过。本轮不夹带无关 workflow 升级，后续应单独升级 actions/setup-java 并跑完整 CI。
- V3.8.5 Human Readability Round 1 与 Round 2 均为 NEEDS_REVISION_NOT_APPROVED，原 30 Story/8 Chapter 文件和哈希保持冻结。Final Chapter 自动门禁不能提供真人评分；任一 P0 truthfulness failure 都直接阻止 PASS。

- V3.8.5 RC3 的旧失败链必须保留：run `31574016609` 暴露 Chapter repair 误用 Story schema 与宽泛主体错误提升，run `31580355605` 暴露标题缺少明确结果，run `31592405476` 记录 GLM HTTP 429。外部容量后来恢复；Final Chapter 同头 run `32609107531` 已由 Luna、DeepSeek、Qwen 完成 19/19 与 9/9，旧 429 不再是当前阻断。Provider 输出仍有随机性，后续相关合同变化必须按影响范围重验。
- V3.8.5 用户修正是可审计、可逆的展示覆盖，不改变 ProjectFact、原始事件或 Evidence；跨窗口的模型措辞仍受窗口上限、Provider 兼容性和未处理范围诊断约束。
- 2026-08-08 的只读 `npm audit` 报告 4 个 high、0 critical，涉及直接依赖 Next/PostCSS 与传递依赖 nanoid/sharp；本轮未运行 `audit fix`，避免在 RC2 夹带未评估的依赖升级，正式发布前仍需单独处置并回归。
- 2026-08-03 的 `npm audit` 对当前前端依赖图报告 3 个 high，涉及 PostCSS 的 source map 文件读取/路径问题与 Sharp/libvips 继承漏洞，并聚合影响 Next。注册表当前自动修复建议是破坏性的 Next 9.3.3 downgrade，不能用 `npm audit fix --force` 代替兼容性判断；应等待或选择官方兼容修复版本，升级后重跑 lint、contracts、production build、Playwright 和根启动器。
- V3.8.5 的 Story/Chapter 标题仍以确定性主体名为基础，真实模型只在有默认 Provider 且窗口合格时做一次有界措辞增强；无模型或模型失败时可读性保守，但成员和 Evidence 不受影响。
- Git `--all` 会覆盖本地可达分支，不等同于远端全部协作历史；浅克隆、force-push、已删除远端分支和未授权 GitHub 元数据会形成明确覆盖缺口。
- 当前增量策略使用受影响时间窗口与 31 天 overlap，能重建常见新增和 rewrite 范围，但超长跨年语义链仍依赖稳定 subject/Evidence；不会用模型相似度强行连接。
- Source event 和 snapshot schema 在 V3.8/V3.9 历史版本中由 Hibernate `ddl-auto=update` 管理；V3.10 release 已收入 Flyway V1 baseline，并以 exact V3.9 H2/PostgreSQL 验证禁止删库规避。
- 最小 `/history` 页面只验证信息层次和深链接，不是最终 GUI。完整 Hash/Evidence 下钻、短 ID、正式中文标签、信息密度、筛选、跨篇章可视化和完整可访问性属于 V4.0 GUI/Productization 债务；V3.9 只遵守产品语言合同并实现必要低风险文案，不抢跑最终视觉重构。
- Obsidian Advanced URI、Local REST/MCP、Dataview/Bases 均为可选增强。零插件官方 URI 只能稳定打开 Vault/文件，标题或块级定位需要插件能力并必须安全降级。
- DeepSeek 与 GLM 专项结果只适用于当日模型、协议、Prompt v2 和冻结输入，不是任意项目准确率承诺。GLM 38-run 的 Conflict Detection 为 0.6667、Deep-read Sufficiency 为 0.8333，仍需把冲突和未读范围作为显式限制保留，不能因总门禁通过而隐藏。
- V3.8.5 真实 qualification 工件保留 12 个 UNSUPPORTED_CLAIM 拒绝和 24 个失败/未处理窗口（两 Provider 均如此）；聚合安全指标为零违规不等于质量资格通过。

- V3.10 新写入的 Provider API Key 不再保存到数据库；数据库只保留 opaque `secretRef`，Windows release 使用 current-user DPAPI。自定义 safe Header 仍必须通过 credential-like 名称拒绝规则，不得被当作另一条凭据通道。
- 固定本地 relay 覆盖协议、SDK 和恢复契约，不代表真实 OpenAI、Anthropic 或 DeepSeek 的输出质量、限流与私有扩展。
- Endpoint override 只支持保留标准协议尾路径的非标准前缀；完全自定义非标准协议不在 V3.4.5。
- 早期迁移 facts 存在泛化摘要，部分 capabilities 合并了多个关注点；只能通过显式 reconciliation/evolution 改善，不能静默改事实。

- Obsidian V3.4.4 是手动 one-shot CLI，不包含 watcher、后台定时器或正式前端配置；自动同步留给后续受控设计。
- Projection manifest 是可重建状态而非事实源；用户复制同一受管实体形成多个 Note 时必须进入 conflict，由用户消除重复，不能自动猜测。
- CORE 的月度 Fact Index 会随单月事实量增长；当前有总输入、分页和响应边界，超大项目仍应观察单月文件可读性，不能退化为默认一 Fact 一文件。
- 原子替换依赖本地文件系统语义；只支持现有本地 Vault 与 loopback ProjectFlow，网络盘、远程 Vault 和跨设备原子性未承诺。
- FULL_FACTS 会显式生成大量文件，只适合用户主动选择；默认始终是 CORE。

- V3.4.3 仅支持 loopback stdio MCP；远程传输、远程身份、Telegram 与正式 Hermes 配置分发未实现，不能把本地验收描述为远程能力。
- Gateway audit 在 V3.4.3 历史版本中由 Hibernate 建表；V3.10 release 已由 Flyway 管理，H2 备份、真实旧库升级和 PostgreSQL 门禁继续作为安全证据。
- Unified Search 是有界数据库候选与字段匹配，不是全文搜索引擎；超大项目需要按类型、时间和分页收窄，不能取消边界全量返回。
- Hermes 外部模型回答质量依赖宿主模型，但 ProjectFlow 只负责提供有界、可追溯、明确 SOURCE/DERIVED 的数据；不得持久化 Hermes 问题或答案为事实。
- 真实 Hermes 配置和 Provider Key 只能用于进程内隔离验收，不得复制或提交。

- V3.4.2 能力表的历史创建方式是 Hibernate `ddl-auto=update`；V3.10 release 已将其纳入 Flyway V1 baseline，并继续用 H2 安全副本和 PostgreSQL 16 Testcontainers 防止删库规避。
- Provider 可能返回 Schema 漂移、遗漏 fact 或高风险 merge；严格校验会拒绝本次刷新并保留旧 READY，用户看到的是 stale/attention 而不是部分覆盖冒充成功。
- 旧能力卡片只有 CONFIRMED 且 sourceRefs 可追到 ProjectFact 时才迁移；无来源旧卡片继续留在兼容区，不能伪造事实关系。
- 42 条旧 attention 的确定性重分类只移除“发生时间回退”这一单一异常；质量问题、可能重复等其他原因继续保留 attention。
- 全历史 bootstrap 会增加首次模型调用和写入成本；120-fact chunk、job 请求/token/时长预算和 history 完成后统一刷新限制规模。
- Hermes 与 Obsidian 都只能消费 Gateway，不能成为新的事实源；远程接入与外部写回仍无正式协议。
- 禁止通过修改系统文件或全局机器配置解决环境问题；只能使用仓库内和进程内方案。

- V3.4.1 Timeline 派生表的历史创建方式是 Hibernate `ddl-auto=update`；V3.10 release 已将其纳入 Flyway V1 baseline，并保留真实 H2/PostgreSQL 旧库验收与不删库规则。
- Timeline summary 依赖外部 Provider，可能受网络、限流、模型 Schema 漂移和长响应影响；事实与确定性统计必须始终可读，旧 READY 必须保留，诊断不得保存 prompt、raw response 或 reasoning。
- 历史迁移与 backfill 可能短时间产生大量 dirty periods；启动迁移必须抑制逐批摘要任务，history chunk 只累计 dirty，完成后再用有界持久化队列处理。

- ProjectFact、FactCursor 和 HistoryState 的历史 schema 现已收入 V3.10 Flyway V1 baseline；仍必须持续保留文件型 H2 旧库升级与 PostgreSQL Testcontainers 门禁，升级失败时不得删除用户数据库。
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
- 当前 V3.8.5 RC3 重跑使用 `glm-5.2` Responses/max 与 `deepseek-v4-flash` Chat/max。该配置只存在于验收 Provider profile，业务校验、fallback 与修复合同保持 Provider-neutral。
- 大输入首次真实返回采用未知集合包装并触发 Schema repair；目标集合递归适配后复验成功，但更多 Provider 私有包装仍可能需要新增无敏感值的 shape diagnostics。
- V3.3.8 历史版本依赖 Hibernate `ddl-auto=update`；V3.10 release 已改为 Flyway 版本化迁移和 Hibernate `validate`，该历史风险不再是当前阻断。

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
