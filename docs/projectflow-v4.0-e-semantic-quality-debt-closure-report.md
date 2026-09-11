# ProjectFlow V4.0-E：语义质量、模型可靠性与关键技术债

状态：IN_PROGRESS。独立 stacked Draft；Owner Review = NOT_REVIEWED。验收事实持续追加至 `acceptance-evidence/v4.0-e/`，本报告不代表 Ready、merge、Tag 或 Release 授权。

## 1. Phase Goal

在不提高证据权威、不改 ProjectFact、不增加第二套引擎的前提下，让真实 Current Understanding 能完成、可恢复，并把项目变化说具体。保留 V4 午夜蓝主视觉与 Current → History → Story/Evidence → Workline 阅读链路。

## 2. GitHub / PR baseline

开始时远程 master 为 `1712841b77fd1e8146ce4ab6beaf404e5b1f7a53`。#24 仍为 Draft，Head `81730744a325ccc2391009b367ca4ba95aef6d14`，因此从该提交建立独立 worktree 与 `codex/v4.0-e-semantic-quality-debt-closure`。原工作区的已修改与未跟踪内容保持原样。

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

ProjectFlow 与 Corporation-Agent 均已通过真实 Sol / Responses / xhigh Current，分别两次请求。对应零变化重跑为 130 毫秒与 42 毫秒，均零模型。具体用量与阶段状态见 `real-model-usage.json`；History 的成功没有用于替代 Current 验收。

## 9. Semantic specificity changes

复用已有 Technical Atom、subject 和 changed-path inventory。区域仍是区域，只列有来源的文件对象示例；具体 subject 不借用同提交其他文件。提交范围的改动不再因含一个 `.env.example` 就误标为环境配置。通用标识翻译覆盖通知、沟通、附件、发票、支付等对象；没有项目名条件分支。

## 10. History quality

观察类代码变化明确写新增/修改对象，并保留“文件变化不等于运行验收”。精确声明、配置、实现、验证和冲突的原权威不变。词汇更具体不会把 broad owner 改成能力实体，也不会让样例文件名拆散既有 broad representation family。具体标题不再被更宽泛的模型改写覆盖。

## 11. Time provenance

Git 用提交时间；PR 区分 merged/closed/updated/created；Tag 用真实标签时间；Agent result 在可对应已提交文件时用 Git 入库时间，只代表过程记录。普通文件只知道 mtime/观察时间时标为“发生时间未知”。混合来源显式标 mixed，旧字段缺失为 unknown；不解析文件名来猜完成日期。

## 12. ProjectFlow Dogfood

使用冻结的 PR #24 只读 clone，Current 已成功。History、全部普通首层陈述、Thread、Story/Evidence、多分支与 stacked PR 的最终证据见可见文本、截图与审计文件；未完成项不作为 PASS。

## 13. Corporation-Agent / third-project Dogfood

Corporation-Agent 使用冻结 `f80553c61aaf19b19e729d47739d6b8f35c1dffd`、17 commits、单 main 分支。Current 与真实 History 已成功；README 能力仍为声明，代码新增不等于上线。另用本地通用备考技能包的只读 clone 验证非典型应用形态；它触发的真实 Final Synthesis 失败与恢复单独记录，不掩盖为完整成功。

## 14. Sol / xhigh usage

所有本轮真实请求使用现有 Windows User RELAY 变量，经 ProjectFlow DPAPI Provider credential path；未切模型、协议或 effort。逐次实际用量、未知用量、延迟和重试见 `real-model-usage.json`。没有用账单或价格估算冒充实际费用。

## 15. MODEL_REVIEW

最终独立 reviewer 使用 GPT-5.6 Sol / xhigh，仅获得普通用户可见 Current、History、Workline、Story/Evidence 文本和截图。不得获得代码、DTO、Ground Truth 或本报告中的审计答案。结果见 `model-review-summary.json`；Owner Review 独立保持 NOT_REVIEWED。

## 16. Unsupported claim gate

保持来源 ID 过滤、ownership、直接/间接来源、PR merged authority、计划和推断分离。无来源计划、里程碑、百分比与鼓励语不能回到 Current。实际审计计数见 `claim-audit.json`，不把规则测试等同于人工或模型评审。

## 17. Semantic usefulness gate

标题/摘要须保留可支持的 action/object/result；有具体来源时禁止退化为材料、结构之类宽泛词。来源本身不足时允许保守摘要。跨项目测试包含发票、支付、库存与未知文档；真实第三项目独立检验可迁移性。

## 18. GUI refinements

Current 先展示用途与有来源说明，其余盘点可展开。每条声明/推断保留独立 badge 与来源。时间标签进入列表及 Evidence；降级最终归纳明确可见。三栏、山脉、星球、午夜蓝、V4 routes 与真实/示例隔离保持。

## 19. CI debt

#24 最新 Quality 与 Windows 均成功。当前已使用新版且 SHA 固定的 Actions；检查的日志/注释未发现待解决 runtime/setup-java deprecation，因此没有为还债盲目改工作流。最终本分支 CI 单独记录。

## 20. Backend / PostgreSQL / Windows

运行实际计数见 `verification.json`。根启动器已重建当前工作树，后端健康与前端均 HTTP 200，并保存 `logs/last-embedded-build.json`。exact V3.9 final 应用创建的 H2 与 PostgreSQL 16 旧库升级测试通过，不能与从当前 V1 建库混淆。Windows portable、OSV 与最终 CI 仍按独立证据验收。

## 21. Failures / recovery

保留所有 V4.0-D 失败。V4.0-E 记录了早期测试回归、旧快照断言、篇章覆盖下降、第三项目实际 HTTP 失败，以及 History 的实际 HTTP/2 reset 与有界成功重试。发生/观察时间变化导致篇章数量可变，连续性测试改比安全不变量并独立验证请求唯一性，不降低事件守恒、来源、覆盖率或零变化门槛。

## 22. Remaining debt

relay/上游 reset 的最终来源仍未证明；SSE 与阶段恢复降低影响，不保证网络永不失败。普通文件没有真实发生时间就保持未知。项目级 Provider 绑定、Obsidian GUI、clone、legacy 工程工具和未来 Archify 按 Ledger 分期处理。

## 23. Owner manual review guide

1. 在本阶段工作树启动 `Start-ProjectFlow.bat`。
2. 打开 ProjectFlow，阅读 Current。
3. 查看最近 3–5 个真实变化。
4. 展开 Story 与 Evidence，确认来源和时间。
5. 查看多分支 Workline 与 Draft 依赖。
6. 切到 Corporation-Agent，比较单分支展示。
7. 确认没有来源的计划和进度没有被补写。
8. 判断第一次使用能否理解项目及判断依据。

完整十步路径与本地验收实例入口在最终交付说明中给出。Agent 不填写 HUMAN_PASS。

## 24. STACK_CONSOLIDATION_READINESS

#22 基于 master，#23 基于 #22，#24 基于 #23，E 基于 #24。正确顺序为 #22 → #23 → #24 → E，逐步重新定位 base 并验证 master；本轮不执行合并。#21 的 backend、Actions 和普通 AppShell 基础已被后续包含，但四份 A 阶段 IA 文档和两份 Agent Result 不在 #22 树中，不能声称完全吸收。其审计/合同应在合并整理时显式保留或归档，不直接把 #21 全栈并入新 UI。

合并后 master 要重跑 Quality、PostgreSQL、exact legacy、Windows、敏感扫描和启动器。若需补最终合并 SHA/CI，仅追加 facts-only evidence backfill，不重写 ProjectFact。分支只有被确认合入、永久 Evidence 可达且 Owner 授权清理后才可删除。V4 A–E 的来源、失败、评审和升级证据永久保留。

## 25. DESKTOP_SHELL_TECHNICAL_ENTRY

当前为待验收状态，不能提前标 READY_FOR_POC。只有两份 Current、真实可靠性、独立 MODEL_REVIEW PASS、无高风险无来源结论、具体语义、诚实时间和 Windows/CI 全部通过才更新；Owner Approval 独立。

## 26. Future Archify direction

满足技术门槛后优先 V4.0-F Electron vs Tauri PoC，继续复用 Java Core/API。桌面壳稳定后再评估 Archify / Visual Understanding；本轮不引依赖、Visual IR、桌面壳、watcher 或新引擎。
