# ProjectFlow V4.0-E：语义质量、模型可靠性与关键技术债

状态：IN_PROGRESS。独立 stacked Draft；Owner Review = NOT_REVIEWED。验收事实持续追加至 `acceptance-evidence/v4.0-e/`，本报告不代表 Ready、merge、Tag 或 Release 授权。

## 1. Phase Goal

在不提高证据权威、不改 ProjectFact、不增加第二套引擎的前提下，让真实 Current Understanding 能完成、可恢复，并把项目变化说具体。保留 V4 午夜蓝主视觉与 Current → History → Story/Evidence → Workline 阅读链路。

## 2. GitHub / PR baseline

开始时远程 master 为 `1712841b77fd1e8146ce4ab6beaf404e5b1f7a53`。#24 仍为 Draft，Head `81730744a325ccc2391009b367ca4ba95aef6d14`，因此从该提交建立独立 worktree 与 `codex/v4.0-e-semantic-quality-debt-closure`。本任务在独立工作树实施，没有在用户原工作区写入。

## 3. V4.0-D blockers

两份 Current 均因 HTTP/2 CANCEL 失败；每份实际两次 transport 请求，Job 却显示零请求；用量未知。两轮 MODEL_REVIEW 均未通过，Desktop Shell Entry BLOCKED。历史失败、评审和既有验收文件全部保留，新证据只追加。

## 4. Technical Debt Ledger

完整清单见 `technical-debt-ledger.json`。本轮 P0 覆盖长请求、失败遥测、具体语义、时间来源、独立评审，以及真实第三项目揭示的“最终归纳失败却命中成功缓存”。项目级模型绑定属于 P2；Obsidian GUI、clone 引擎与大规模旧工具删除保持 DEFERRED；Archify 是未来增强。

## 5. HTTP/2 root-cause investigation

先用未修改的 V4.0-D Gateway 重跑 Corporation-Agent：本次成功，说明 CANCEL 不是必然复现；先前失败没有被抹去。基线成功任务又证明 Job 聚合只计最后阶段，无变化重跑还复读上次用量。

检查了 SDK 4.43.0 源码、有限 connect/read/write/call timeout、关闭 SDK retry 的配置、Gateway executor interrupt、Durable Job deadline/cancel 与只读轮询。没有证据证明 600 秒硬性客户端超时，也没有证据把 reset 归因于 relay、上游、SDK 或 Windows；origin 继续为 UNDETERMINED。

## 6. Reliability changes

有 reasoning 且请求超时超过五分钟的 Responses 请求使用官方 SDK SSE；模型、xhigh、900 秒单请求限制、完整输入、验证与两次 transport 上限不变。只消费 completed/incomplete/failed terminal response；不把 delta 当 JSON，不存 reasoning，缺失 terminal 明确失败。请求设置 `store(false)`。

原 Scout → bounded capability execution → conditional Final Synthesis 链保持 0/1/2 次逻辑调用。第一阶段仅保存已规范化且证据过滤后的内部 checkpoint，同源、同模型、同协议、同 effort 才可恢复。成功清除 checkpoint；旧可信结果不会被失败替换。GET 不返回内部 checkpoint。

## 7. Telemetry repair

Job 级 collector 在实际进入 adapter 时记录 attempt，跨线程显式传递；heartbeat 与 finally 都保存安全聚合。失败包装不再丢失次数，零变化为 NOT_CALLED / 0，未知用量为 null / UNKNOWN，部分用量另有 reported totals，不伪装成精确总量。每次尝试包括 task、protocol、effort、retry、完成/失败、耗时、规范化错误与 HTTP 状态，不含请求或响应正文。

## 8. Current Understanding results

ProjectFlow 与 Corporation-Agent 均已通过真实 Sol / Responses / xhigh Current，分别两次请求。最新无变化重跑分别为 176 毫秒与 43 毫秒；第三项目为 174 毫秒，三份均为零请求、NOT_CALLED。具体用量与阶段状态见 `real-model-usage.json`；History 的成功没有用于替代 Current 验收。

## 9. Semantic specificity changes

复用已有 Technical Atom、subject 和 changed-path inventory。区域仍是区域，只列有来源的文件对象示例；具体 subject 不借用同提交其他文件。提交范围的改动不再因含一个 `.env.example` 就误标为环境配置。通用标识翻译覆盖通知、沟通、附件、发票、支付等对象；没有项目名条件分支。

历史文档通过固定参数的 Git 差异读取补充内容证据：最多 48 个文档差异、单次 16,000 字符、总计 256,000 字符、20 秒总期限。只留下最多两条加入、一条移除的短文本及版本前后值；原始 patch、完整文档和凭据不持久化。敏感、符号链接、二进制或超限输入仍按原边界降级。已采集的不可变提交注释复用，不因最新抽样窗口移走而抹除旧证据或破坏无变化缓存。

模型仍接收工程层确定的主体、时间和 Evidence；候选保留来源类别多样性，同时优先近期变化，并先提供最近的对应文本。使用说明、报告和验收记录中的内容只支持“文档补充了什么”，不证明功能或验收通过。单个依赖清单或数据结构文件不再被称为整个项目骨架。

提交者和开发助手的有界声明保留具体行为、验证范围及未完成项，包括英文原文或带斜杠的描述。模型必须用中文转述并标清作者；这只证明来源做过该声明。纯过程记录保持 UNKNOWN 实现状态、空直接证据与 PROCESS_DECLARATION 支持类型，不自动写入 ProjectFact。

## 10. History quality

观察类代码变化明确写新增/修改对象，并保留“文件变化不等于运行验收”。同一提交的总览与细分范围通过 Primary/Supporting 关系阅读，相关范围可继续下钻；原始事件和双向关系完整保留。篇章切分保持完整父子组，并只复用兼容的分组与代表性计划。词汇更具体不会把 broad owner 改成能力实体，也不会让样例文件名拆散既有 broad representation family。

仅一条记录或宽泛文件区域的既有 Thread 保留稳定身份，标为记录上下文；V4 在分页前筛选跨多条记录的精确主体。原工程接口、历史连续性与消费边界保持兼容。代表性覆盖不足时可作有界的时间阅读切分，原 0.60 门槛未降低；它不代表成熟阶段。

真实复验又定位到整窗恢复丢失语义的问题：一条措辞被拒绝后，原恢复模板会把同窗已合格文字全部改回通用草稿。现在仅在 Story-only 输出的 ID、结构和 Evidence 完整合法时，保留逐条通过同一 Validator 的文字，为被拒绝条目使用工程草稿，仍执行原有一次恢复与完整复验。非法 ID、Evidence、未知字段或混入篇章仍拒绝整窗；恢复计数和保留/替换数量写入原 checkpoint 的安全诊断，原模型内容不进入诊断。

## 11. Time provenance

Git 用提交时间；PR 区分 merged/closed/updated/created；Tag 用真实标签时间；Agent result 在可对应已提交文件时用 Git 入库时间，只代表过程记录。普通文件只知道 mtime/观察时间时标为“发生时间未知”。混合来源显式标 mixed，旧字段缺失为 unknown；不解析文件名来猜完成日期。

## 12. ProjectFlow Dogfood

使用冻结的 PR #24 只读 clone，Current 已成功。最终 History 耗时 2,749,896 毫秒，20 次实际请求；14 个窗口全部完成，篇章无失败或待处理项，4,312 个来源事件守恒，代表性覆盖为 0.6763，原 0.60 门槛未降低。第 10 次请求发生 HTTP/2 reset，第 11 次有界重试成功；总用量为 PARTIAL，已报告 297,306 tokens 不代表完整总量。随后无变化重跑耗时 20,799 毫秒、零请求。最终 34 份文本与 76 张截图已冻结，独立评审正在进行。

## 13. Corporation-Agent / third-project Dogfood

Corporation-Agent 使用冻结 `f80553c61aaf19b19e729d47739d6b8f35c1dffd`、17 commits、单 main 分支。较早的 xhigh History 恢复成功与无变化零调用记录全部保留。文档内容复验有一次 Job 成功、4 次实际请求、耗时 519,167 毫秒，但整窗恢复抹去了具体措辞，因此明确不计为语义验收。保留合格措辞后的真实复验成功：6 次实际 xhigh 请求、耗时 932,182 毫秒；无变化重跑 736 毫秒、零请求。默认最近五条已显示文档中的版本变化、协作方式和检查记录内容，独立语义复审仍在进行。README 能力仍为声明，代码新增不等于上线。

第三项目为本地通用备考技能包的只读 clone。真实 Final Synthesis 失败后保留已验证 Scout，恢复只调用一次 Final Synthesis 并成功；最新无变化重跑为零请求。初始失败、状态误报和恢复分别留档，没有把材料包强制理解为前后端应用。

## 14. Sol / xhigh usage

有效验收使用现有 Windows User RELAY 变量，经 ProjectFlow DPAPI Provider credential path，模型为 Sol、协议为 Responses、effort 为 xhigh。一次任务执行器重启遗漏进程覆盖，产生了 6 次 high 请求；该轮被明确排除于验收，保留在 `failed-runs/restart-effort-deviation.json`，随后恢复显式 xhigh 并复跑。应用全局默认值没有为本轮改写。

逐次实际用量、未知用量、延迟和重试见 `real-model-usage.json`。记录中的 effort 来自真实 attempt 遥测，不按预期硬填；无请求显示 NOT_CALLED。没有用账单或价格估算冒充实际费用。

## 15. MODEL_REVIEW

独立 reviewer 使用 GPT-5.6 Sol / xhigh，仅获得普通用户可见 Current、History、Workline、Story/Evidence 文本和截图。不得获得代码、DTO、Ground Truth 或本报告中的审计答案。第四轮 Corporation-Agent 为 NEEDS_REVISION：34 份 TXT、67 张 PNG 均已检查，但默认近期变化主要描述文件载体，未解释内容。完整输入与结论保存在 `failed-runs/sol-review-round4/`。

第五轮 Corporation-Agent 为 PASS：再次完整检查 34 份 TXT、67 张 PNG，无阻断项。唯一 LOW 项为 Story 2 局部来源卡仍使用“完善项目骨架”这类范围偏宽的措辞；主 Story 已说明只能确认文件变化，不能确认运行结果。该项保留为非阻断债务，不隐去评审限制。ProjectFlow 第五轮完整检查 34 份 TXT 与 76 张 PNG，结论仍为 NEEDS_REVISION：来源声明中已有的具体行为、验证范围和未完成项被第一层通用措辞抹去。已追加保留作者身份的声明上下文与验证规则，正在定向、全量与真实复验；旧结论不改写。Owner Review 独立保持 NOT_REVIEWED。

## 16. Unsupported claim gate

保持来源 ID 过滤、ownership、直接/间接来源、PR merged authority、计划和推断分离。无来源计划、里程碑、百分比与鼓励语不能回到 Current。实际审计计数见 `claim-audit.json`，不把规则测试等同于人工或模型评审。

## 17. Semantic usefulness gate

标题/摘要须保留可支持的 action/object/result；有具体来源时禁止退化为材料、结构之类宽泛词。来源本身不足时允许保守摘要。跨项目测试包含发票、支付、库存与未知文档；真实第三项目独立检验可迁移性。

## 18. GUI refinements

Current 先展示用途与有来源说明，其余盘点可展开。每条声明/推断保留独立 badge 与来源。时间标签进入列表及 Evidence；降级最终归纳明确可见。三栏、山脉、星球、午夜蓝、V4 routes 与真实/示例隔离保持。

## 19. CI debt

#24 最新 Quality 与 Windows 均成功。当前已使用新版且 SHA 固定的 Actions；检查的日志/注释未发现待解决 runtime/setup-java deprecation，因此没有为还债盲目改工作流。最终本分支 CI 单独记录。

## 20. Backend / PostgreSQL / Windows

源码 `80c5402231df6e11a400d684ee68de68e1f78491` 的后端/H2 全套 780 项：769 通过、11 项按既有 opt-in 边界跳过。PostgreSQL 16 集成 7 项通过且无跳过；exact V3.9 final 应用创建的 H2 和 PostgreSQL 16 旧库升级证明 2 项通过、零跳过，耗时 43.628 秒。前端类型检查、73 项契约、生产构建与 38 项 Playwright 全通过；Playwright 使用真实前后端、Next 开发服务器和固定兼容模型，生产 UI 则单独构建并连接真实分析库，不能把固定模型当作 Sol。

Hermes 10 项、Obsidian 27 项通过；5,000 facts 规模的 Obsidian 无变化同步为零写入。较早源码 `3baf330afed17e903967d20d1b81a83ccadfec60` 的 Windows 便携包和根启动证据已追加保留在 prior-verification；最新代码的便携包、根启动、OSV 与分支 CI 继续复验，不以旧证明冒充本轮结果。

## 21. Failures / recovery

保留所有 V4.0-D 失败。V4.0-E 记录了旧快照断言、篇章覆盖下降、第三项目实际 HTTP 失败，以及 History 的实际 HTTP/2 reset 与有界成功重试。后续还发现旧篇章复用不适配新的父子组、普通动词“修改”被质量门槛误拒绝、单记录浏览器夹具不再符合长期主题定义，以及执行器重启遗漏 effort 覆盖；逐项修复或纠正并追加复验。

首次最终全套测试有一项无变化缓存断言失败；保持输入稳定后，未修改该断言或代码的完整重跑通过。精确瞬态原因未证实，失败仍留档。不降低事件守恒、来源、覆盖率、请求唯一性或无变化零调用门槛。

第五轮声明改动的全套回归出现三个失败：Prompt 期望版本过旧、声明回退漏掉可确认的记录结果、证据文件较多时 Git 元数据页截断。修复后八项定向检查通过，包含真实历史连续性三项；完整读取 351/351 提交，覆盖率 0.6875。分页只缩小读取范围，单条命令仍限 100,000 字符，单提交文件预算由 500 调整为 1,000，总事件仍限 20,000；更大提交继续明确 INCOMPLETE，不丢弃来源来伪造完整。全套复验已通过；新的真实模型和独立评审仍在执行。

第六轮 Corporation 独立 Sol/xhigh 复核为 PASS，完整保留 34 份 TXT、67 张 PNG 和结论。Agent 另发现单条 Story 的坏字段会抹掉有效摘要/变化内容，因此继续修复，不能把该轮 PASS 直接转用为新结果的验收。修复复用同一完整校验器，最多尝试八组本地字段，仍只有一次模型修复；旧缓存仅重试确实替换过 Story 的窗口。五项定向检查、780 项后端全套、7 项 PostgreSQL、2 项 exact 旧库升级和 38 项浏览器回归已通过，新的真实输出与独立复核待完成。

## 22. Remaining debt

relay/上游 reset 的最终来源仍未证明；SSE 与阶段恢复降低影响，不保证网络永不失败。普通文件没有真实发生时间就保持未知。项目级 Provider 绑定、Obsidian GUI、clone、legacy 工程工具和未来 Archify 按 Ledger 分期处理。

## 23. Owner manual review guide

本阶段入口位于独立工作树，三个只读样本及已保存分析库随该工作树保留。

1. 在本阶段工作树启动 `Start-ProjectFlow.bat`。
2. 在项目库打开 ProjectFlow。
3. 阅读 Current，区分材料声明、当前归纳与未知。
4. 查看最近 3–5 个真实变化。
5. 展开 Story 与 Evidence，确认关联范围、来源和时间。
6. 查看多分支 Workline 与 Draft 依赖。
7. 切到 Corporation-Agent。
8. 比较其单 branch 展示。
9. 确认没有来源的计划和进度没有被补写。
10. 判断第一次使用能否理解项目及判断依据。

Agent 不填写 HUMAN_PASS；Owner 的判断独立于工程与模型评审。

## 24. STACK_CONSOLIDATION_READINESS

#22 基于 master，#23 基于 #22，#24 基于 #23，E 基于 #24。正确顺序为 #22 → #23 → #24 → E，逐步重新定位 base 并验证 master；本轮不执行合并。#21 的 backend、Actions 和普通 AppShell 基础已被后续包含，但四份 A 阶段 IA 文档和两份 Agent Result 不在 #22 树中，不能声称完全吸收。其审计/合同应在合并整理时显式保留或归档，不直接把 #21 全栈并入新 UI。

合并后 master 要重跑 Quality、PostgreSQL、exact legacy、Windows、敏感扫描和启动器。若需补最终合并 SHA/CI，仅追加 facts-only evidence backfill，不重写 ProjectFact。分支只有被确认合入、永久 Evidence 可达且 Owner 授权清理后才可删除。V4 A–E 的来源、失败、评审和升级证据永久保留。

## 25. DESKTOP_SHELL_TECHNICAL_ENTRY

当前为待验收状态，不能提前标 READY_FOR_POC。只有两份 Current、真实可靠性、独立 MODEL_REVIEW PASS、无高风险无来源结论、具体语义、诚实时间和 Windows/CI 全部通过才更新；Owner Approval 独立。

## 26. Future Archify direction

满足技术门槛后优先 V4.0-F Electron vs Tauri PoC，继续复用 Java Core/API。桌面壳稳定后再评估 Archify / Visual Understanding；本轮不引依赖、Visual IR、桌面壳、watcher 或新引擎。
