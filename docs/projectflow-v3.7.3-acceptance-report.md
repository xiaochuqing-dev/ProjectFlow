# ProjectFlow V3.7.3 Acceptance Report

本文件记录实现、真实模型、本地验收、GitHub PR、merge、最终功能 master 和 CI 的完成证据。

A. 当前 master version？V3.7.3。
B. 当前 master SHA？最终功能 master 为 `5ac2f34809251850ace69c01e7488f5e9ca8642f`；本报告回填是仅文档后继。
C. V3.7.3 完成什么？完成长任务时间语义、多协议 Provider runtime、共享 Prompt Builder、Evidence 语义护栏、Capability/View eligibility、结构化恢复、真实评测和产品边界。
D. 哪些 V3.7.2 问题被修复？修复短总时限、连接/请求/总体超时混用、长请求不可取消/无心跳、Eval/Production Prompt 漂移、工程预判 Evidence 重要性、工具/视图边界不足和截断后结构丢失。
E. 45 秒问题如何处理？移除其作为总体分析截止时间的语义；总体、连接和单次 Provider 请求分别管理。
F. 是否仍有固定短硬上限？NO。AUTO/UNLIMITED 没有主动 overall deadline。
G. Connection Timeout？默认 10 秒，配置值限制在 1–60 秒。
H. Provider Request Timeout？有限且可按 Provider 覆盖；基础默认 240 秒，reasoning 档案默认 300 秒，真实压力验收使用显式长请求窗口。
I. Overall Analysis Deadline？AUTO/UNLIMITED 无主动截止时间；FINITE 使用用户显式秒数。
J. UNLIMITED 语义？只取消总体截止时间，不取消连接/请求超时、取消、并发、Token 和有界重试。
K. Cancellation？Provider 等待期间 250 ms 轮询取消与 deadline，并更新持久化心跳。
L. Checkpoint/Resume？持久化阶段、心跳、重试来源和策略均保留；重启前尚未调用模型可安全恢复，模型状态未知时不自动重发。
M. 是否隐藏降质？NO。只支持 QUALITY_FIRST，Job policy 明确记录 hiddenQualityDowngrade=false。
N. Prompt Builder 是否共用？YES。生产 Scout、生产 Final 和 Eval 共用 ProjectUnderstandingPromptBuilder。
O. Ground Truth 是否泄漏？NO。Builder 不接受 Ground Truth；泄漏测试通过；blob hash 未改变。
P. Prompt Contract version？project-understanding-prompt-contract-v1；Scout v10；Final v5。
Q. Evidence Importance 如何定义？Evidence 能实质支持、限制、纠正项目形态、能力、冲突、未知、风险或有证据演进时才重要，不按文件名、类型、大小或新旧打分。
R. 工程系统是否只负责事实和分类？YES。另负责安全采样、eligibility、allow-list、执行和引用校验。
S. 模型是否负责语义重要性？YES。模型负责 importance、information gap、适用 view、冲突和当前性判断。
T. Eligible Capability？Registry 根据真实可用性产生；模型必须逐项 REQUEST/SKIP；执行只接受完整、已注册、固定参数请求。
U. Eligible View？AnalysisViewRegistry 根据代码、材料、历史和结构事实限定集合，模型在集合内选择，输出再次验证。
V. Structured Output normalization？支持 Responses、Chat Completions、Anthropic 的统一输出；轻量修复、一次定向 Schema repair 和仅保留已闭合字段的 Scout 截断恢复。
W. Transport/Semantic metrics 是否拆分？YES。可靠性与 conditional semantic quality 分组报告。
X. Provider/model/protocol？Volcano Ark GLM real eval / glm-5.2 / OPENAI_RESPONSES。
Y. Provider READY？YES。最终真实 Provider probe 通过；未记录 Key、请求体或原始响应。
Z. 完整运行次数？38。
AA. Timeout？0。
AB. Failure rate？0.0000。
AC. Conditional Semantic Quality？38 个有效结构化 run 全部进入语义统计。
AD. Critical Evidence Recall？0.9610。
AE. Evidence Precision？0.8409。
AF. Unsupported Claim Rate？0.0000。
AG. Shape？F1 0.7912，Exact Accuracy 0.7895。
AH. Tool precision/recall？1.0000 / 0.8750。
AI. Unnecessary Tool Rate？0.0000。
AJ. Dynamic View？Recall 0.9529，Precision 0.4241。
AK. Conflict Detection？0.6667。
AL. Repeatability？0.9680。
AM. Stage 2 Gain？Evidence 1.0000，View 0.0476，Unsupported Claim Reduction 0。
AN. Token？正式 38-run：input 135,203，output 399,999，total 535,202；8-case E2E 另计 291,900。
AO. Latency average/P95？145,919 ms / 337,617 ms。
AP. 8 个端到端结果？8/8 通过；详见 projectflow-v3.7.3-end-to-end-model-acceptance.md。
AQ. ProjectUnderstandingService.refresh()？8 个样本均调用真实生产 refresh 链并完成持久化/readback。
AR. Real Capability Provider？YES。实际执行 MANIFEST、GIT_HISTORY、GIT_TAG、DOC_READER、AGENT_RESULT 等注册 Provider。
AS. Real Tool Evidence？YES。端到端不是固定 Tool Evidence 注入。
AT. Final Evidence refs？8-case 共 57 个，非法引用 0。
AU. Secret leak？0。密钥只存在于受控测试进程环境，进程已关闭；仓库 secret pattern scan 为 0。
AV. Internal metrics product boundary？通过。内部评测字段未进入生产 DTO、API、snapshot、数据库或 UI。
AW. Backend/H2？403 tests，failures/errors 0，skipped 1。
AX. PostgreSQL？本机 Docker daemon 不可用；GitHub PostgreSQL 16 Testcontainers 在 PR push、PR 和最终功能 master CI 中全部通过。
AY. Frontend？TypeScript/lint 通过，contracts 50/50，Next.js production build 通过。
AZ. Playwright？8/8 通过。
BA. Hermes？5/5 通过。
BB. Obsidian？18/18 通过。
BC. Product Acceptance？23/23 已人工回答；全部阻断 Gate 通过。
BD. Known risks？Dynamic View precision 0.4241、Deep Read target accuracy 0.2241、Conflict Detection 0.6667 仍可提升；真实 Provider 延迟和 Token 较高；现有 npm lock 审计报告 3 个高危依赖；本地未运行 Docker。
BE. 是否允许进入 V3.8？YES。V3.7.3 MULTI-PROVIDER QUALITY GATE = PASS；GLM QUALITY QUALIFIED = YES；V3.8 ENTRY = APPROVED。
BF. implementation SHA？主实现 `ed6cb724fb6acd3d28b4b7d27057b5e0a21f4b76`；合并前自审契约修正 `9ca5e759b56b1c1310dceb0f426a78893614608d`。
BG. documentation SHA？`962a1d1f5de33182f28f01a0d18527fc26d21ea0`。
BH. PR number？PR #7。
BI. PR merge SHA？`5ac2f34809251850ace69c01e7488f5e9ca8642f`。
BJ. final master SHA？最终功能 master SHA 为 `5ac2f34809251850ace69c01e7488f5e9ca8642f`；本报告回填提交是仅文档后继，不改变功能验收基线。
BK. final CI run IDs/status？最终 PR head push `30384903989` PASS；PR `30384906887` PASS；最终功能 master `30385223914` attempt 2 PASS。master attempt 1 的 browser-e2e runner 在测试前 `npm ci` 外部安装阶段无输出挂起，主动取消后换 runner 重跑，`npm ci` 和 Playwright 均通过；其他 blocking jobs 在两个 attempt 中保持通过。optional-real-provider 因 CI 未配置真实 Key 按既定条件跳过。
BL. 是否创建 Tag？NO。
BM. 是否创建 Release？NO。
