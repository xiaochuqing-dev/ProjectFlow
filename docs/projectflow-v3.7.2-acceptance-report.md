# ProjectFlow V3.7.2 Acceptance Report

Report date: 2026-07-26

Scope notice: 本指标仅代表本阶段人工标注代表性测试集，不构成对任意项目的通用准确率承诺。

A. 当前 master 版本？

开发基线为 V3.7.1，origin/master SHA `86632ff13db3b23b41856575c32accf6d758399b`。本分支目标版本为 V3.7.2；合并后信息见 AS。

B. V3.7.2 完成什么？

完成 ProjectFlow 专用测试内 Eval Harness、18-case Ground Truth、真实 Model Gateway pilot、v3 语义校准、可审计 High-value Evidence Gate、Final Synthesis 当前结果降级、完整工具 cache identity、内部指标产品隔离、三类薄 Adapter Contract、文档与版本更新。

C. 使用了哪个真实 Provider / Model？

DeepSeek，OPENAI_CHAT_COMPLETIONS，deepseek-v4-pro。

D. Key 如何安全注入？

本地评测只从工作树内临时复制的 H2 Provider 数据库按只读连接加载；CI 只从 GitHub Actions secret 环境变量注入。均只在测试进程内使用。

E. Key 是否进入 Git/日志/报告？

NO。源码和未跟踪源码扫描未发现 Key/Authorization 模式，评测工件不记录 Key、完整 Prompt、raw response 或 reasoning。

F. 测试集有哪些？

empty、blank、奇怪重要文档、小脚本、frontend、backend、desktop、fullstack、monorepo、no Git、短/长 Git、stale README、README/source conflict、Agent Result、token usage、ProjectFlow、large repository，共 18 case；重要 case 三次重复。

G. Ground Truth 如何定义？

人工定义多标签 shape、must-find evidence、must-not claim、期望/禁止工具、期望/禁止视图、unknown、conflict、history mode 和 deep-read target。模型 Prompt 不读取期望答案。

H. 幻觉率如何定义？

使用 Unsupported Claim Rate：缺少 evidence refs 且非 UNKNOWN/INFERRED、人工判定无支持或违反 must-not claim 的声明数除以声明总数。它只属于该测试集。

I. Critical Evidence Recall？

成功的校准前 pilot 为 0.8831；最终 v3 因 Provider HTTP 402 未得到可用 aggregate，不能写最终 PASS。

J. Evidence Precision？

pilot 为 1.0000；最终 v3 未得到可用 aggregate。

K. Shape Accuracy？

pilot F1 为 0.8148，exact accuracy 为 0.7368；未达最终完整质量结论。

L. Tool Selection？

pilot precision 0.7619、recall 0.6667、unnecessary rate 0.2381，未达到 0.80/0.15 门槛。

M. Dynamic View Applicability？

pilot precision 0.6951、recall 0.6706，未达到 recall 0.90 门槛。

N. Conflict Detection？

pilot 为 1.0000，stale README、README/source conflict 和 historical roadmap currentness 均识别。

O. Repeatability？

pilot 为 0.5708，低于 0.80 门槛。

P. Second-stage Gain？

pilot evidence gain 0.0444、view gain 0.1333、unsupported reduction 0。存在可测增益，但旧 Final Schema 带来额外修复请求；独立 Schema 已修正，待 v3 重验。

Q. Token / Cost / Latency？

pilot 67 次物理请求、160256 total tokens、平均 38460 ms。旧工件未分拆 stage token，当前 Harness 已补齐 Stage 1/Tool chars/Stage 2 字段。没有可靠日期价格，cost 为 UNAVAILABLE，不伪造金额。

R. 发布门槛？

failure <= 0.05、Critical Evidence Recall >= 0.85、Unsupported Claim <= 0.05、critical must-not violation = 0、Tool recall >= 0.80、unnecessary tool <= 0.15、Dynamic View recall >= 0.90、repeatability >= 0.80、deep-read Stage 2 有正增益、empty/blank 0 model、逻辑 0/1/2、降级 100%、secret leak 0。

S. 是否通过？

NO。工程安全边界和固定回归通过，但 pilot 的 Tool/Dynamic View/Repeatability 未达门槛，最终 v3 真实批次又被唯一 Provider 的 HTTP 402 阻断。不得进入 V3.8 质量放行。

T. 哪些 case 失败过？

pilot 中 small-script、no-git 未找出关键 evidence；fullstack 两次空结果且一次复合 shape；Agent Result 一次缺关键 evidence且输出不稳定；ProjectFlow 未稳定识别 frontend；多个 case 有额外工具。最终真实尝试中除 empty/blank 外的 36 次调用均因 HTTP 402 失败。

U. Prompt 如何修改？

v2 增加 Agent Result、PROCESS_METADATA、current/history、conflict/unknown 边界；v3 再要求原子多标签 shape、逐 evidence assessment、非空逃避禁止、稳定大写维度、信息缺口驱动工具、保守 deep read、小输出上限和独立 Final Synthesis Schema。

V. 是否针对测试集硬编码？

NO。生产 Prompt 不含 case ID/期望答案；真实 Harness 从 bounded context 提取 allowed evidence IDs，以模型实际 deep-read 选择决定实验 Stage 2。Ground Truth 只在测试资源和计分器中。

W. High-value Evidence Gate 如何工作？

只有预算允许且注册 Provider 产生经过验证、非短文本、非重复、非 Stage 1 已知、非 clean metadata 的新深读内容、历史锚点、工作树变化或 conflict/currentness 证据时触发。决定保存 trigger/skipped reason 和 evidence IDs。

X. Final Synthesis 失败是否保留 Stage 1？

YES。Provider、timeout、schema、cancel 和 interruption 均保留 Stage 1、已校验 Tool Evidence、Source Map 和当前 Dynamic Profile，状态为 FAILED_DEGRADED。

Y. Tool cache key 是否修正？

YES。包含 source/content/structure revision、canonical capabilities、deep-read targets、Provider version、执行预算、模型预算、策略版本和 Source Map 签名。V3.7.2 不虚构持久 cache。

Z. Eval metrics 是否进入产品？

NO。Harness 只在 test source/target artifact；生产 API、snapshot、database、frontend 与 Dynamic Profile 没有 accuracy/hallucination/benchmark score。

AA. Integration Boundary 如何锁定？

外部能力只能产出绑定项目、来源、revision、时间角色、currentness、confidence、evidence refs 和脱敏摘要的 Envelope；validator 拒绝 raw payload、绝对路径、缺 project binding 和重复 fingerprint。Adapter 结果不能直接写 ProjectFact。

AB. 三类 Adapter 是什么？

EvidenceSourceAdapter、IntelligenceProviderAdapter、ProjectionAdapter。

AC. 是否做 Adapter PoC？

NO。V3.7.2 只锁 Contract/Envelope/Validator，避免在质量门槛未通过时扩大外部集成。

AD. 是否学习 CC Switch 集成作风？

YES。采用薄接入、单一配置真相、成熟能力优先复用和不复制业务的作风。

AE. 是否复制 CC Switch 业务？

NO。

AF. 开源调研采用了什么？

采用 OpenAI eval/structured-output 的数据集与实验思想、OpenTelemetry GenAI 的版本化脱敏观测边界、MCP/GitHub/VS Code 的薄 Adapter 边界和 CC Switch 的集成作风。License 与采用/拒绝记录见开源调研。

AG. 哪些轮子拒绝自研？

通用 Eval 平台、Agent Manager、parser/grammar、SCIP producer、Git/GitHub SDK、secret scanner、RAG/vector store、workflow/daemon、Desktop shell、telemetry agent 和模型排行榜。

AH. Backend tests？

Surefire 365 项，0 failure、0 error、1 个无 Provider 条件跳过；定向 Eval/Gate/Degradation/Adapter tests 全部通过。

AI. PostgreSQL？

PostgreSQL 16 Testcontainers 3/3 通过；Failsafe 另有两个真实模型测试因无 CI Key 条件跳过。

AJ. Frontend / Playwright？

contracts 50/50、TypeScript/lint、production build、Playwright 8/8 全部通过。

AK. Hermes / Obsidian？

Hermes 5 项通过；Obsidian 18 项通过。

AL. Product Acceptance？

安全与边界 PASS；真实模型最终质量 NOT PASSED。详见 Product Acceptance 与 Model Evaluation。

AM. Known risks？

唯一真实 Provider 当前 HTTP 402；v3 无最终真实 aggregate；pilot 稳定性/工具/视图未达门槛；本地 Provider Key 仍是数据库兼容存储；前端依赖审计报告 3 个既有 high severity；Stage token 新字段待下一次真实批次填充。

## 2026-07-27 final funded-provider addendum

The previous DeepSeek 402 history remains valid historical evidence. A new funded GLM `glm-5.2` Provider using `OPENAI_RESPONSES` passed the focused probe and completed the full 38-run evaluation.

Final aggregate: 19 failures, failure rate 0.5000, Critical Evidence Recall 0.3636, Evidence Precision 1.0000, Unsupported Claim Rate 0.0000, Shape F1/exact 0.4691/0.1842, Tool precision/recall 0.2222/0.1667, unnecessary-tool rate 0.7778, Dynamic View precision/recall 0.1026/0.0941, Conflict Detection 0.1111, Repeatability 0.4130 and no positive Stage 2 gain. Total usage was 62742 tokens across 45 bounded physical requests.

The eight-case real `ProjectUnderstandingService.refresh()` acceptance passed strange document and small script. The strange-document flow actually executed `DOC_READER`, used real Tool Evidence, completed Final Synthesis with valid Evidence IDs and persisted/read back the snapshot. Frontend, backend, fullstack, no Git, Agent Result and ProjectFlow itself timed out in Stage 1.

Updated decision: `V3.7.2 REAL MODEL QUALITY GATE = NOT PASSED`; `V3.8 ENTRY = BLOCKED`. No Prompt, Ground Truth, metric formula or threshold changed. No Tag or Release was created. Full final questions and GitHub backfill are maintained in `docs/projectflow-v3.7.2-final-revalidation.md`.

AN. 下一阶段建议？

先补充/轮换可用测试额度，在相同 Provider/model/prompt/Ground Truth 上重跑 v3，满足全部门槛后再决定 V3.8 Evidence-backed Evolution Reconstruction；不先做 Adapter PoC 或新产品面。

AO. implementation SHA？

`d7e044023383b968bcd632671ad208ce314be16d`。

AP. documentation SHA？

`b08fd393b9cf5363a6577fd4d8e95dcaf0d077be`。

AQ. PR number？

PR #5。

AR. PR merge SHA？

`13f59169a66342d7ebc152bd3de9257792fbf017`。

AS. final master SHA？

最终功能 master SHA 为 `13f59169a66342d7ebc152bd3de9257792fbf017`；本报告回填提交是仅文档后继，不改变功能验收基线。

AT. final CI run IDs/status？

PR head push `30201675174` PASS；PR `30201676234` PASS；最终功能 master `30201767848` PASS。blocking jobs 全部通过，optional-real-deepseek 因未配置 CI Secret 按条件跳过。

AU. 是否创建 Tag？

NO。

AV. 是否创建 Release？

NO。
