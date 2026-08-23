# ProjectFlow V3.8.5 Final Closure Report

日期：2026-08-23。

最终状态：`APPROVED_BY_PROJECT_OWNER`。`V3.8.5 FINAL ACCEPTANCE = PASS_BY_EXPLICIT_OWNER_OVERRIDE`。

2026-08-24 项目所有者明确批准最终 package 与 PR #15 merge，并明确豁免本轮量化人工评分。数值阈值没有得到证明，也没有被自动填充；独立追加证据、single-reviewer、scope 与 P0 依据限制见 `projectflow-v3.8.5-final-human-signoff.md`。Round 1/2/3 与 Final Chapter 冻结工件继续原样。

GitHub 正式关闭：PR #15 已以 merge commit `29c154eb618ca43edf58c631c14cc1d296e14f3f` 合入 master。合并后 required CI run `32652683003` 的 Backend/H2、PostgreSQL、Frontend、Browser、Hermes、Obsidian 与 sensitive-content 全绿；根启动器从同一干净 revision 完成构建、启动、健康检查和无残留退出。回填证据为 `acceptance-evidence/v3.8.5/final-acceptance-backfill.json`。

## 封板边界

本轮只关闭 Chapter Representativeness、三协议真实 Provider 兼容和最终可审计证据。ProjectFact 继续是唯一强事实来源；Raw Event、Technical Atom、Story 身份、Primary/Supporting、Claim State、Evidence、Timeline、Capability、Evolution 与用户修正均未被 Chapter 归纳改写。Round 1、Round 2 继续是 `NEEDS_REVISION_NOT_APPROVED`；Round 3 的 30 Story / 8 Chapter、空白人工字段和 canonical-LF 哈希保持不变。

起始时 PR #15 为 OPEN、Draft、未合并；该句只保留任务起点。项目所有者随后授权，PR 已 Ready 并合入 master，正在执行仅含关闭元数据的 acceptance backfill。Tag 与 Release 始终不执行；分支只在回填合并和最终 master 验证后清理。

## 根因与实现结果

原 Chapter 第一层标题偏向时间排序靠前的一个 Primary Story。大 Chapter 缺少代表成果簇、dominant/minor 角色、代表覆盖目标与“多中心应拆分”的工程判断；旧 Validator 只能证明文字与某个 Primary 有交集，不能证明标题代表整个阶段。这会产生“局部 Evidence 合法、阶段中心失真”的结果。

现实现先由确定性规则形成 Representative Cluster，计算 DOMINANT、CO_DOMINANT、MINOR、Claim ceiling 和有界代表覆盖；Supporting 只跟随 Primary owner，不增加阶段中心权重。存在强语义转折时保守拆分，否则保留 limitation。无模型或模型失败时，标题/摘要只来自 dominant/co-dominant outcome。模型只能润色已注册 cluster，必须原样返回 representedClusterIds；Validator 逐 cluster 检查覆盖、Claim ceiling、技术泄漏和 minor-title risk。

三协议兼容修复包括：Responses/Chat/Messages 的服务根与端点归一化、Qwen3.7 Plus Messages thinking budget、编码 JSON 容器与多候选选择、历史任务精确 Schema repair 模板、reasoning-only/截断归一化，以及 Schema repair 在 Max 思考下保留 Provider 输出上限。真实资格测试最多增加一次显式持久化刷新，只重试失败或 pending 窗口；成功 checkpoint 不重放，所有请求/Token、首次失败计数与是否恢复都进入脱敏工件。SDK HTTP 失败只暴露状态和严格白名单化的 type/code/param 标识，异常正文、SDK message 与请求内容不进入诊断。Story/Chapter Validator、Evidence allow-list 和强事实边界没有放宽。

## ProjectFlow Dogfood 对比

| 指标 | 起始基线 | 最终确定性结果 |
| --- | ---: | ---: |
| Commit | 任务起始真实历史 | 275 |
| Source Event | 任务起始真实历史 | 3,525 |
| Story | 任务起始真实历史 | 371 |
| Primary / Supporting | 任务起始真实历史 | 135 / 236 |
| Chapter / Thread | 任务起始真实历史 | 9 / 271 |
| 整体 Representative Primary Coverage | 0.6036 | 0.7783 |
| 最大 Chapter（按 Story 数）Representative Coverage | 0.3668 | 0.6507 |
| 最大 / 中位 Chapter Story | 未作最终封板指标 | 68 / 40.0 |
| 需要拆分 Chapter | 未作最终封板指标 | 0 |

最终确定性 Dogfood 为 0 模型调用并命中 cache。invalid/cross-project Evidence、unsupported claim、technical leak、Chapter overlap、orphan Supporting、reason without Evidence 和 user-declared mutation 均为 0。规范化 JSON 为 51,740 bytes，raw / canonical-LF SHA-256 分别为 `dbf17ea4fc14d22f5f96dfff8dca6ad2a2c1a9a0b2bc0e70390ae65934fc9ec8` / `2b271cdebd4c3d1d42d9d70c689b1e4d29acba4a5dd4c44777865440a138c7b9`。

## 真实 Provider 诚实记录

所有配置均为 Max 思考：GPT 5.6 Luna `gpt-5.6-luna` / `OPENAI_RESPONSES`，DeepSeek V4 Flash `deepseek-v4-flash` / `OPENAI_CHAT_COMPLETIONS`，Qwen3.7 Plus `qwen3.7-plus` / `ANTHROPIC_MESSAGES`。没有调用替代 Qwen 模型。

DeepSeek 在兼容加固期间保留了多次失败证据：runs `32418200565`、`32420366955`、`32422127617`、`32423927654`、`32425495346`、`32426991772` 分别暴露了编码容器、Schema 模板、候选选择、reasoning-only 与修复预算问题；没有删除或改写这些失败。run `32428741982` 因误用冗余 full 范围被操作员取消，不作为质量结论。

定向 affected run `32429355953` 的 DeepSeek qualification 最终 PASS：57 个物理请求、172,809 Token、716,546 ms 模型/评测耗时；2 个 case 首轮出现未完成信号，执行 2 次显式失败窗口刷新后全部恢复。首次 repair failure 为 1，最终 unresolved、model degraded、rejected output、repair failure 均为 0；安全持久化标志全部为 false。该记录证明恢复发生过，不把首次失败隐藏成一次成功。

源码头 `f108dc6` 的定向 run `32435709820` 进一步暴露了非历史最小兼容探针的传输形态缺口：19-case DeepSeek qualification 工件本身 PASS，64 个请求、198,725 Token，2 个失败窗口重试后全部恢复；但独立 ProjectFlow compatibility probe 的字符串化 JSON 在两次请求后仍被判缺少 `summary`，资格 job 因此 FAIL。场景 job 在该源码头被 `7e36bf3` 替代后 force-cancelled，未作为质量结论。生产适配随后只解码 JSON-shaped wrapper 并重新执行注册 Schema，不映射语义别名或补造字段。

本次恢复后的真实尝试也完整保留：DeepSeek run `32597054302` 的场景 job 失败，修复后的 run `32598786904` 通过 qualification 19/19 与 scenarios 9/9；三模型诊断 run `32600022743` 中 DeepSeek 通过，但 Luna、Qwen qualification 失败，因此整 run 仍为失败。Luna 定向 runs `32603831405`、`32604655890` 继续暴露最小探针形态问题，后者在诊断完成后取消场景，未作为质量结论。修复后的 Luna run `32605122482` 通过 19/19 与 9/9；Qwen run `32605872507` 通过 19/19 与 9/9，并关闭了 Story 润色后 Chapter grounding 不一致问题。后续成功没有覆盖这些失败和取消记录。

最终同头三模型 run：`32609107531`，来源代码头 `e1b67f28428e73f39fc23aa6f85961155a20ffd8`。

| Provider | Qualification | Chapter scenarios | 请求 / Token / 恢复 |
| --- | --- | --- | --- |
| GPT 5.6 Luna | PASS 19/19；42 请求 / 123,198 Token | PASS 9/9；56 请求 / 338,147 Token | 4 repair；0 retry；安全计数 0 |
| DeepSeek V4 Flash | PASS 19/19；42 请求 / 188,051 Token | PASS 9/9；55 请求 / 412,738 Token | 3 repair；0 retry；安全计数 0 |
| Qwen3.7 Plus | PASS 19/19；42 请求 / 177,380 Token | PASS 9/9；60 请求 / 421,525 Token | 4 repair；0 retry；安全计数 0 |

六份最终 JSON 都经过 Provider/profile、qualification、scenarioScope、场景 PASS、安全标志、疑似凭据、Prompt/raw response/reasoning 字段和机器绝对路径检查，并以 canonical-LF SHA-256 绑定到 Final Chapter manifest。

## 验证

- 本地受影响模型/协议/历史套件：100 项，0 failure、0 error、1 条件跳过。
- 有界恢复资格合同：3 项，0 failure、0 error；真实 Provider wrapper 在无 Key 本地环境按合同跳过。
- 当前 ProjectFlow 确定性 Dogfood：2 项通过。
- 启动：生产代码来源头 `746f12b`，测试夹具证据头 `e1b67f2`；Next.js 16.2.11 与 Spring Boot/H2 就绪，Build ID `n8TKaHfKQejeExyRej5uv`，readyAt `2026-08-23T07:51:39.4687986+08:00`；退出后 3000/8080 监听为 0。此前首次运行同时暴露并修复了 bundled PowerShell 缺少 `Get-FileHash` 的仓库脚本兼容问题。
- 来源头静态 push run `32437875886` 与 PR run `32437875179` 全绿。
- 最终同头 run `32609107531` 的 backend/H2 为 634 项、0 failure、0 error、7 条件跳过；PostgreSQL 工件为 11 项、0 failure、0 error、6 条件跳过。frontend、browser、Hermes、Obsidian、sensitive-content 与三模型资格/场景 jobs 全部成功。
- 最终证据头：`d5ddb3f20193a2330ed69fc156240a4ead5293c4`；push run `32612757299` 与 PR run `32612759225` 的 backend/H2、PostgreSQL、frontend、browser、Hermes、Obsidian、sensitive-content 全部成功。

## Final Chapter 人工门禁

最终工件冻结 12 个 Chapter，三 Provider 各 4 个，覆盖 ProjectFlow 大型长期历史、large-coherent、large-heterogeneous、representation boundary、minor-first、supporting-heavy、short-coherent、user-declared、deterministic fallback、presentation、research report 与 data analysis。Round 3 Story 只比较展示变化，Truth/Evidence semantic hash 必须 30/30 不变。

冻结 worksheet 人工状态仍为 `NOT_RUN`：`reviewerCount=0`，姓名、是/否、1-5 分、PASS/FAIL 与备注全部为空。独立 final sign-off 记录 `reviewerCount=1` 与项目所有者明确批准，不改写冻结 worksheet。

2026-08-24 PHASE A0 复核确认 PR Head、base master、最终 Provider run、冻结哈希与 required CI 均未变化，冻结合同测试 2/2 通过。交接材料记录用户基于最终 worksheet 展示给出约 8.5/10 的总体判断，认为整体信息、个人理解、标题与摘要已基本可接受，未报告新的 Truthfulness P0。该判断没有覆盖冻结 Gate 所需的精确 1-5 平均分、完整审核范围与 merge 批准，因此不能自动转写为 PASS。

同一反馈把主要剩余问题定位为 GUI/Presentation：完整 Hash、Commit 与 Evidence ID 默认过长；Representative Cluster、weight、Primary/Supporting 与内部 state 不应在用户第一层堆叠；Before/Change/After 等混排标签应采用正式中文产品语言。工程层继续保留 Git、Commit、PR、CI、API、Token、SHA、JSON、HTTP 等专业术语。权威合同见 `product-language-and-progressive-disclosure-contract.md`，最终 GUI 实现延后到 V4.0，不反向阻断 V3.8.5 的语义验收。

## 最终决策与风险

工程自动门禁完成后曾停在 `HUMAN_REVIEW_REQUIRED / NOT PASS`。2026-08-24 项目所有者明确改变本次收口要求：不再等待量化评分并批准继续，因此当前按 `PASS_BY_EXPLICIT_OWNER_OVERRIDE` 执行 Ready、merge、master CI、acceptance backfill 与 V3.9 进入。不得把该 override 改写为原数值 Gate 已通过。

保留风险：Provider 输出仍有随机性，DeepSeek 已实际触发并通过有界重试；最终人工评审计划只有一名评审人，必须披露 single-reviewer limitation；API Key 的本地数据库存储仍是桌面产品化前需迁移到 OS SecretStore 的既有风险。V3.9 可考虑把代表性反馈转为新的人工评测集，但不在 V3.8.5 反向改写冻结事实或验收结论。
