# ProjectFlow V4.0-E：语义质量、模型可靠性与关键技术债

状态：LOCAL_ACCEPTANCE_PASS_CI_PENDING。独立 stacked Draft；Owner Review = NOT_REVIEWED。验收事实持续追加至 `acceptance-evidence/v4.0-e/`，本报告不代表 Ready、merge、Tag 或 Release 授权。

## 1. Phase Goal

在不提高证据权威、不改 ProjectFact、不增加第二套引擎的前提下，让真实 Current Understanding 能完成、可恢复，并把项目变化说具体。保留 V4 午夜蓝主视觉与 Current → History → Story/Evidence → Workline 阅读链路。

## 2. GitHub / PR baseline

开始时远程 master 为 `1712841b77fd1e8146ce4ab6beaf404e5b1f7a53`。#24 仍为 Draft，Head `81730744a325ccc2391009b367ca4ba95aef6d14`，因此从该提交建立独立 worktree 与 `codex/v4.0-e-semantic-quality-debt-closure`。本任务在独立工作树实施，没有在用户原工作区写入。

## 3. V4.0-D blockers

两份 Current 均因 HTTP/2 CANCEL 失败；每份实际两次 transport 请求，Job 却显示零请求；用量未知。两轮 MODEL_REVIEW 均未通过，Desktop Shell Entry BLOCKED。历史失败、评审和既有验收文件全部保留，新证据只追加。

## 4. Technical Debt Ledger

完整清单见 [Technical Debt Ledger](acceptance-evidence/v4.0-e/technical-debt-ledger.json)。本轮 P0 覆盖长请求、失败遥测、具体语义、时间来源、独立评审，以及真实第三项目揭示的“最终归纳失败却命中成功缓存”。项目级模型绑定属于 P2；Obsidian GUI、clone 引擎与大规模旧工具删除保持 DEFERRED；Archify 是未来增强。

## 5. HTTP/2 root-cause investigation

先用未修改的 V4.0-D Gateway 重跑 Corporation-Agent：本次成功，说明 CANCEL 不是必然复现；先前失败没有被抹去。基线成功任务又证明 Job 聚合只计最后阶段，无变化重跑还复读上次用量。

检查了 SDK 4.43.0 源码、有限 connect/read/write/call timeout、关闭 SDK retry 的配置、Gateway executor interrupt、Durable Job deadline/cancel 与只读轮询。没有证据证明 600 秒硬性客户端超时，也没有证据把 reset 归因于 relay、上游、SDK 或 Windows；origin 继续为 UNDETERMINED。

## 6. Reliability changes

有 reasoning 且请求超时超过五分钟的 Responses 请求使用官方 SDK SSE；模型、xhigh、900 秒单请求限制、完整输入、验证与两次 transport 上限不变。只消费 completed/incomplete/failed terminal response；不把 delta 当 JSON，不存 reasoning，缺失 terminal 明确失败。请求设置 `store(false)`。

原 Scout → bounded capability execution → conditional Final Synthesis 链保持 0/1/2 次逻辑调用。第一阶段仅保存已规范化且证据过滤后的内部 checkpoint，同源、同模型、同协议、同 effort 才可恢复。成功清除 checkpoint；旧可信结果不会被失败替换。GET 不返回内部 checkpoint。

## 7. Telemetry repair

Job 级 collector 在实际进入 adapter 时记录 attempt，跨线程显式传递；heartbeat 与 finally 都保存安全聚合。失败包装不再丢失次数，零变化为 NOT_CALLED / 0，未知用量为 null / UNKNOWN，部分用量另有 reported totals，不伪装成精确总量。每次尝试包括 task、protocol、effort、retry、完成/失败、耗时、规范化错误与 HTTP 状态，不含请求或响应正文。

## 8. Current Understanding results

ProjectFlow 与 Corporation-Agent 均已通过真实 Sol / Responses / xhigh Current，分别两次请求。最新功能源码 `349a6039` 上无变化重跑分别为 831 毫秒与 110 毫秒；第三项目为 17 毫秒，三份均为零请求、NOT_CALLED。重启及刷新前后完整 Current API 响应一致，旧零调用结果也继续保留。具体用量与阶段状态见 `real-model-usage.json`；History 的成功没有用于替代 Current 验收。

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

使用冻结的 PR #24 只读 clone，Current 已成功。第五轮 History 耗时 2,749,896 毫秒，20 次实际请求；14 个窗口完成，4,312 个来源事件守恒，代表性覆盖为 0.6763。该轮一次 HTTP/2 reset 经有界重试恢复；已报告 297,306 tokens，总量 PARTIAL。随后无变化重跑零请求。但第五轮独立评审未通过，因此这些工程成功不计为最终语义验收。其 34 份文本、76 张截图与否定结论继续保留。

第六轮运行中确认了单个坏字段仍会抹去同条有效内容，遂通过正式取消入口停止旧逻辑任务。19 次真实请求中，18 次收到响应，最后一次 CLIENT_CANCELLED；已报告 337,959 tokens，最后一次用量未知。12 个 checkpoint 保留：11 个成功、1 个取消；成功项中 4 个无须升级，7 个曾替换措辞。最新字段恢复实现只重算受影响窗口和未完成尾部，未修改只读 clone。

字段修复后的首轮完成了 13/14 个窗口，复用 4 个成功窗口；21 次请求中 18 次收到响应、3 次传输失败，已报告 312,122 tokens，总量 PARTIAL。随后补跑复用全部 13 个成功窗口，但两次请求分别发生 IO_FAILURE 和 HTTP2_STREAM_RESET_CANCEL，用量 UNKNOWN。再一次重启后的补跑出现额外窗口请求，不能描述为只恢复一个窗口：该轮最终完成 12/14 个窗口，零缓存命中，27 次请求、4,875,764 毫秒，已报告 364,906 tokens，总量 PARTIAL。25 次收到响应、2 次传输失败；收到响应也不等于通过内容校验。对前后两个已关闭 H2 备份的只读比对确认：4,312 个事件 ID 不变，83 条指纹变化的来源内容全部相等（24 条 PR、59 条 Agent result）。原先 API 比对的 72 条只是下界。旧 JSON 对象字段顺序进入指纹，错误地使成功窗口失效。

兼容修复保留内容相等事件的旧指纹；聚合项目 HEAD 和数据库时间精度也不再造成伪更新。最新 `349a6039` 重启前后，三个 Current 及 ProjectFlow 338 条、Corporation 22 条 Story 的完整响应一致。三个 Current 与 Corporation History 的刷新均零请求；ProjectFlow 随后通过原刷新入口成功恢复：事件零新增、零更新、4,312 条复用；12 个成功窗口命中缓存，14/14 窗口完成且篇章无失败或待处理。剩余两窗口和篇章归纳完成，期间一次传输失败通过有界重试恢复，共 6 次请求、593,345 毫秒，已报告 60,167 tokens，总量 PARTIAL。最终无变化刷新耗时 19,638 毫秒、零请求，全部 338 条 Story 响应保持一致。

## 13. Corporation-Agent / third-project Dogfood

Corporation-Agent 使用冻结 `f80553c61aaf19b19e729d47739d6b8f35c1dffd`、17 commits、单 main 分支。较早的成功、失败、泛化措辞与零调用记录全部保留。最终字段恢复先保留一个成功窗口，再补齐另一个窗口和篇章：4 次真实 xhigh 请求、40,775 tokens、356,701 毫秒，全部 2 个窗口成功，22 条可读 Story。最新源码 `349a6039` 上的再次刷新为 1,371 毫秒、零请求，全部 Story 响应保持一致。独立 Sol/xhigh 完整检查 34 份 TXT、67 张 PNG，结论 PASS，无阻断项；部分标题、长来源列表与窄屏浮层有非阻断改进项。README 能力仍为声明，代码新增不等于上线。

第三项目为本地通用备考技能包的只读 clone。真实 Final Synthesis 失败后保留已验证 Scout，恢复只调用一次 Final Synthesis 并成功；最新无变化重跑为零请求。初始失败、状态误报和恢复分别留档，没有把材料包强制理解为前后端应用。

## 14. Sol / xhigh usage

有效验收使用现有 Windows User RELAY 变量，经 ProjectFlow DPAPI Provider credential path，模型为 Sol、协议为 Responses、effort 为 xhigh。一次任务执行器重启遗漏进程覆盖，产生了 6 次 high 请求；该轮被明确排除于验收，保留在 `failed-runs/restart-effort-deviation.json`，随后恢复显式 xhigh 并复跑。应用全局默认值没有为本轮改写。

当前用量账本包含 60 个实际 Job、252 次 transport 请求，其中包括明确排除验收的 6 次 high 请求和所有保留的失败/恢复轮次；零请求 Job 有 24 个。该账本不是账单，缺失的上游用量不能补齐。逐次实际用量、未知用量、延迟和重试见 `real-model-usage.json`。记录中的 effort 来自真实 attempt 遥测，不按预期硬填；无请求显示 NOT_CALLED。没有用账单或价格估算冒充实际费用。

## 15. MODEL_REVIEW

最终两个真实项目均由独立 GPT-5.6 Sol / xhigh 复核并获得 PASS，无阻断项。ProjectFlow reviewer 完整读取 34/34 TXT、逐张查看 77/77 PNG；Corporation reviewer 完整读取 34/34 TXT、查看 67/67 PNG。输入覆盖普通用户默认最近八条、前五条 Story/Evidence 下钻、Current、时间视图、Thread、Workline，以及五档屏宽，没有挑选最优条目。reviewer 未获得代码、DTO、Ground Truth、审计答案或预期结论。

Corporation 的 27 份文本与原冻结评审相同；另 7 份 Workline 只更新观察时间，同一独立 reviewer 对 7 TXT / 9 PNG 的补充复核仍为 PASS。完整输入、字节/规范化换行哈希和采集时刻见 visual-manifest.json，重启内容保留证明单独记录。

旧 V4.0-D 两轮与 E 多轮 NEEDS_REVISION 全部保留。ProjectFlow 最终仍有 1 项 MEDIUM、4 项 LOW 非阻断建议：时间篇章名称偏泛、长导航形成空白、内部状态/脱敏占位符直出、未知项重复、来源列表阅读成本。Corporation 保留 3 项非阻断建议。这些没有被写成零缺陷或独立运行验收；Owner Review 始终为 NOT_REVIEWED。

## 16. Unsupported claim gate

保持来源 ID、ownership、直接/间接来源、PR merged authority 与计划/推断分离。最终 Agent 来源审计覆盖两项目默认近期 16 条 Story，核对 69 组 Git 来源/时间、20 条工作线及 11 条 Current 陈述，来源不匹配与高风险无来源主张均为 0。全部可读 Story 为 ProjectFlow 338 条、Corporation 22 条，持久化诊断中的无效来源、跨项目引用与 unsupported claim/strong fact 均为 0。

计数范围与不可逐路径核实的脱敏条目在 claim-audit.json 明确列出；模型评审与该来源审计相互独立。没有据此声称项目内每项功能运行或部署验收通过。

## 17. Semantic usefulness gate

标题/摘要须保留可支持的 action/object/result；有具体来源时禁止退化为材料、结构之类宽泛词。来源本身不足时允许保守摘要。跨项目测试包含发票、支付、库存与未知文档；真实第三项目独立检验可迁移性。

## 18. GUI refinements

Current 先展示用途与有来源说明，其余盘点可展开。每条声明/推断保留独立 badge 与来源。时间标签进入列表及 Evidence；降级最终归纳明确可见。三栏、山脉、星球、午夜蓝、V4 routes 与真实/示例隔离保持。

## 19. CI debt

#24 最新 Quality 与 Windows 均成功。当前已使用新版且 SHA 固定的 Actions；检查的日志/注释未发现待解决 runtime/setup-java deprecation，因此没有为还债盲目改工作流。最终本分支 CI 单独记录。

## 20. Backend / PostgreSQL / Windows

功能源码 `349a6039e68ab5ad0126a2dd1dca3dca1029e7f7` 的后端/H2 全套 782 项：771 通过、11 项按既有 opt-in 边界跳过。PostgreSQL 16 必需集成 7 项通过、零跳过；exact V3.9 H2/PostgreSQL 旧应用建库和受控升级证明 2 项通过、零跳过，耗时 48.984 秒。前端类型检查、73 项契约、生产构建及 38 项 Playwright 均通过；浏览器全套耗时 3.0 分钟，使用真实前后端与固定兼容模型，不代替真实 Sol 验收。

Hermes 10 项、Obsidian 27 项通过。Obsidian 的 5,000 facts、36 个月、100 capabilities、1,000 evolutions 规模产生 183 个文件，首轮 806.6 毫秒、184 次写入，无变化同步零写入。

便携包源提交 `268172023810119ffa18b4bcbde193e2ce0e37e0` 与功能源码仅有文档和证据差异；两次 bundled-runtime 启动、DPAPI、manifest、KNOWN_CURRENT 备份恢复和端口释放均通过，运行 PATH 不含 Maven/npm/Git。根启动器在独立提交 `b74b7b8c035b125d18e065d69d9b7cf95ea12777` 添加了可选端口；参数范围、端口不可相同、显式端口预检、发布脚本契约与前端 73 项契约通过。根 Start-ProjectFlow.bat 已带 `-BackendPort 18041 -FrontendPort 13041` 从不同工作目录重建并运行当前工作树，保留三个真实项目、两份最终 History 和 DPAPI 配置；Agent 浏览器再次检查已保存 Current、默认近期变化与工作线。旧阶段数据先备份，停库后才复制。启动证明在 logs/last-embedded-build.json 及 windows-launcher.json，Root 与便携验证范围分开。

本分支最终 required CI 与 OSV 仍待 GitHub 实际运行后追加。

## 21. Failures / recovery

保留所有 V4.0-D 失败。V4.0-E 记录了旧快照断言、篇章覆盖下降、第三项目实际 HTTP 失败，以及 History 的实际 HTTP/2 reset 与有界成功重试。后续还发现旧篇章复用不适配新的父子组、普通动词“修改”被质量门槛误拒绝、单记录浏览器夹具不再符合长期主题定义，以及执行器重启遗漏 effort 覆盖；逐项修复或纠正并追加复验。

首次最终全套测试有一项无变化缓存断言失败；保持输入稳定后，未修改该断言或代码的完整重跑通过。精确瞬态原因未证实，失败仍留档。不降低事件守恒、来源、覆盖率、请求唯一性或无变化零调用门槛。

第五轮声明改动的全套回归出现三个失败：Prompt 期望版本过旧、声明回退漏掉可确认的记录结果、证据文件较多时 Git 元数据页截断。修复后八项定向检查通过，包含真实历史连续性三项；完整读取 351/351 提交，覆盖率 0.6875。分页只缩小读取范围，单条命令仍限 100,000 字符，单提交文件预算由 500 调整为 1,000，总事件仍限 20,000；更大提交继续明确 INCOMPLETE，不丢弃来源来伪造完整。全套复验已通过；对应的最终真实模型与独立复核结果见本报告第 12–15 节。

第六轮 Corporation 独立 Sol/xhigh 复核为 PASS，完整保留 34 份 TXT、67 张 PNG 和结论。Agent 另发现单条 Story 的坏字段会抹掉有效摘要/变化内容，因此继续修复，不能把该轮 PASS 直接转用为新结果的验收。修复复用同一完整校验器，最多尝试八组本地字段，仍只有一次模型修复；旧缓存仅重试确实替换过 Story 的窗口。五项定向检查、780 项后端全套、7 项 PostgreSQL、2 项 exact 旧库升级和 38 项浏览器回归已通过，对应的最终真实输出与独立复核已另行记录。

最新又用确定性测试复现了普通标题回退误丢有效摘要：安全但没有动作的标题导致两个字段一并被替换。首个较宽修复触发缺少结果的旧回归，已保留该失败并收紧条件：被保留字段须独立通过既有语义有用性门槛，组合仍通过完整权威校验。四项定向和 781 项全套通过；没有额外模型调用，也没有全局清空有效缓存。

真实重启发现的指纹顺序问题另有一个先红后绿的定向回归：全部来源字段相同则不更新事件、不替换旧指纹；coverage 值实际变化仍更新。新测试使用合成旧指纹，只证明兼容逻辑，不冒充真实重启或 Sol 验收。

追加的持久化回归又先后复现两种伪变化：不可变来源沿用较早项目 HEAD，以及 100 纳秒文件时间被 TIMESTAMP(6) 舍入后重新读取。修复后两项定向、782 项后端全套、7 项 PostgreSQL 与 2 项 exact 旧库升级检查通过；真实重启及零请求证明见 `restart-checkpoint-retention.json`，两个先红后绿的结果保留在 `failed-runs/restart-persisted-identity.json`。

根启动器验收还保留了三类本地执行失败：cmd 相对路径分隔符错误、非交互终端关闭输入造成服务退出，以及短暂 8080 端口占用。修正执行方式、等待端口可独占绑定后再验收；没有停止无关进程或改操作系统设置；确认 8080 属于现有容器服务后，仅为启动器添加可选端口，默认端口不变。

## 22. Remaining debt

relay/上游 reset 的最终来源仍未证明；SSE 与阶段恢复降低影响，不保证网络永不失败。普通文件没有真实发生时间就保持未知。项目级 Provider 绑定、Obsidian GUI、clone、legacy 工程工具和未来 Archify 按 Ledger 分期处理。

另记录两项实际 P1：不同 read model 读取时刻和时区的提示可以更清楚；旧通用 Job DTO 的 `maxRequestCount=1`、`maxTotalTokens=120000` 不能表达多窗口 History 的聚合预算。History 实际按每轮最多 16 个 Story 窗口、4 个篇章窗口及各自 Gateway 请求/时间/token 上限执行，正常 V4 页面不展示这两个旧字段。后续若暴露桌面聚合预算控制，须先统一该契约，不能把旧字段宣传成当前全历程上限。

第三个材料项目的项目库仍显示等待首次读取，而 Current 材料理解已成功、History/Workline 尚未刷新；该库卡片的范围提示可更清楚，作为非阻断 P2 留档。

## 23. Owner manual review guide

本阶段入口位于独立工作树，三个只读样本及已保存分析库随该工作树保留。

1. 本机默认 8080 已由其他服务使用；在本阶段工作树运行 `Start-ProjectFlow.bat -BackendPort 18041 -FrontendPort 13041`，或直接打开当前运行的 `http://127.0.0.1:13041/workspace/projects`。
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

GitHub 复核时 #21–#24 均为开放 Draft，master 仍为 `1712841b77fd1e8146ce4ab6beaf404e5b1f7a53`。B⊂C⊂D⊂E 已由 Git ancestry 验证；E 本地完整验收已完成，正在创建 Draft 并等待实际 CI。

| PR | base branch | actual head |
| --- | --- | --- |
| #21 | master | `d853e6f16d6a63562192eb51548f7b7949c2ffc9` |
| #22 | master | `7d5f30eff3a05105b7f3f60e07e352d297301ed5` |
| #23 | codex/v4.0-b-gui-first-prototype | `703120998f2296b4e615cd785e651a351c5dc8c9` |
| #24 | codex/v4.0-c-gui-productization | `81730744a325ccc2391009b367ca4ba95aef6d14` |
| V4.0-E | codex/v4.0-d-real-project-understanding | 语义源码 `349a6039e68ab5ad0126a2dd1dca3dca1029e7f7`；启动器源码 `b74b7b8c035b125d18e065d69d9b7cf95ea12777`；交付提交与 PR 待记录 |

正确顺序为 #22 → #23 → #24 → E，逐步重新定位 base 并验证 master；本轮不执行合并。#21 的 Windows 端口等待改动已经包含，Tomcat 安全基线被后续版本承接，Dogfood 时间夹具被后续实现承接，AppShell 旧版本标题改为显式工程兼容入口。不能笼统称 #21 全部已吸收：以下四份文档和两份 Agent Result 不在 E 树中，须在后续合并整理时显式保留或归档。

- `docs/projectflow-v4-design-system-and-desktop-foundation-research.md`
- `docs/projectflow-v4-gui-foundation-current-state-audit.md`
- `docs/projectflow-v4-gui-foundation-phase-a-report.md`
- `docs/projectflow-v4-gui-foundation-product-contract.md`
- `.projectflow/agent-results/20260904-2015-v40-gui-foundation-ia/result.json`
- `.projectflow/agent-results/20260905-025101-v40-gui-foundation-owner-review-revision/result.json`

显式 legacy dashboard 仍有 V3.9 eyebrow，#21 的 V3.10 字面标签没有被吸收。该旧页面不属于正常 V4 路由；关闭 #21 前须明确其处理决定，不直接把旧 UI 栈合进 V4。

合并后 master 要重跑 Quality、PostgreSQL、exact legacy、Windows、敏感扫描和启动器。若需补最终合并 SHA/CI，仅追加 facts-only evidence backfill，不重写 ProjectFact。分支只有被确认合入、永久 Evidence 可达且 Owner 授权清理后才可删除。V4 A–E 的来源、失败、评审和升级证据永久保留。

## 25. DESKTOP_SHELL_TECHNICAL_ENTRY

当前为 BLOCKED，唯一剩余技术交付门槛是本分支 required CI / OSV 的实际成功记录。两份真实 Current、最终 History 恢复及无变化零调用、独立 MODEL_REVIEW PASS、来源/时间审计与本地 Windows/浏览器验证已经完成。CI 未完成前不标 READY_FOR_POC；Owner Review 仍为 NOT_REVIEWED，与技术入口分开。

## 26. Future Archify direction

满足技术门槛后优先 V4.0-F Electron vs Tauri PoC，继续复用 Java Core/API。桌面壳稳定后再评估 Archify / Visual Understanding；本轮不引依赖、Visual IR、桌面壳、watcher 或新引擎。
