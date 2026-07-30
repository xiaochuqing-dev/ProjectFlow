# ProjectFlow V3.7.4 Acceptance Report

本报告以实际结果为准。当前实现可交付，但最终泛化 Gate 失败；不允许进入 V3.8，不创建 Tag 或 Release。

A. latest master SHA：`d0766403c25d382cd6cdd38e332ebb6c811022bc`（V3.7.4 功能合并 master；本报告回填是后继）。
B. functional baseline SHA：`5c7fb75cae5f23d933d352634a6c97a0c2f6aece`。
C. version：`3.7.4`。
D. PR number：PR #9。
E. implementation SHA：`23d419ef453417f1ab2f0b653b2560b0987755ae`；冻结提交 `dd87476f479f7909830250d012800f48511c0da8`；Linux 快照可移植性修复 `d1c1c8c72897c923ded2ff5bbb39455768da0efc`。
F. documentation SHA：`ebb63b1d3c6132e0e702c51c3d7b59a815ca6ea1`。
G. merge SHA：PR #9 merge `d0766403c25d382cd6cdd38e332ebb6c811022bc`。
H. final master SHA：最终功能 master 为 `d0766403c25d382cd6cdd38e332ebb6c811022bc`；本报告回填是仅版本标识/证据后继。
I. final CI run IDs/status：PR 最终 head push `30504177687` PASS；pull_request `30504179642` PASS；功能 master `30504320753` PASS。初始 Run `30503945116`/`30503947001` 因测试 fixture 的 CRLF/LF 快照差异失败，`d1c1c8c` 仅在测试输入边界统一 LF 后三组最终 Run 均通过。optional-real-provider 因 CI 无真实 Key 按条件跳过。
J. Model A provider/model/protocol：Volcano Ark Coding / `glm-5.2` / `OPENAI_RESPONSES`。
K. Model B provider/model/protocol：DeepSeek official / `deepseek-v4-pro` / `OPENAI_CHAT_COMPLETIONS`。
L. API keys handling：通过遮蔽输入仅注入测试进程，退出后清理；未持久化、未记录、未提交。
M. Prompt versions：Strong Fact Contract v2；Semantic Scout v11；Final Synthesis v6。
N. Ground Truth hashes：Calibration `2034C83BAE71B6A3D7CBEF415FD5A69D74B81BA75C4406913E9BF597A8111D58`；Holdout `00F9F32C4D4E8C1184021F36558F9226C54DAA863A2558436974C94E624D23A5`。
O. Strong Fact Contract：Evidence 绑定、状态分离、currentness/conflict/unknown、Agent candidate 和 promotion guard 已实现。
P. Fact statuses：OBSERVED、VERIFIED、DECLARED、INFERRED、CONFLICTED、UNKNOWN。
Q. inference promotion violations：0。
R. declared promotion violations：0。
S. Agent Result promotion violations：0。
T. fake historical reason count：0。
U. fake deprecation count：0。
V. fake technical-debt count：0。
W. invalid Evidence count：0（正式 Calibration、Holdout、Regression、GLM 产品 E2E）。
X. weird-name recall：确定性夹具与两模型正式集均发现关键内容；无文件名特判。
Y. normal-project critical recall：GLM Holdout 0.9091；DeepSeek Holdout 0.8182，低于 0.90 门槛。
Z. large-file middle recall：确定性 Content Map PASS；冻结集关键中部 Evidence 可发现。
AA. large-file tail recall：确定性 Content Map PASS；GLM 找到尾部修订但 Final 降级；DeepSeek 未满足完整深读门禁。
AB. cross-chunk precision：引用合并测试 PASS；正式 Evidence Precision 为 GLM 1.0000、DeepSeek 0.9000。
AC. conflict detection：GLM Holdout 1.0000；DeepSeek 0.0000，门禁失败。
AD. unknown preservation：确定性测试与 unknowable-reason Holdout 保留 UNKNOWN。
AE. unread range disclosure：PASS，Content Map 返回 partial coverage、limitations 与 unread ranges。
AF. deep-read sufficiency：GLM Holdout 1.0000；DeepSeek 0.6667，未达标。
AG. multi-project list：PASS，Agent 可列出全部授权项目。
AH. project history read：PASS，授权项目历史可分页读取。
AI. cross-project isolation：PASS，无 Evidence 串线。
AJ. unauthorized access：PASS，未授权读取不返回数据。
AK. candidate-write boundary：PASS，Agent 不能直接写 OBSERVED/VERIFIED 强事实。
AL. Context Package：PASS，包含来源、revision、状态分区、预算、覆盖和限制。
AM. MCP/API：Hermes 6/6，发现 13 个只读工具；跨项目 REST/API 所有权检查通过。
AN. Calibration Set：两模型各 21 个正式用例；GLM 与 DeepSeek 均 21/21、0 降级。
AO. Holdout Set：8 个独立冻结用例，每个模型首轮且唯一正式运行一次。
AP. Holdout result：NO PASS。GLM 1 次可见降级；DeepSeek Critical Recall 0.8182、Deep-read 0.6667、Conflict 0。
AQ. V3.7.3 38-run result：GLM 38/38，Critical Recall 0.9610，Unsupported 0，非法引用 0；DeepSeek 核心 10/10 作为补充。
AR. V3.7.3 E2E result：原始 8 项由 GLM 在 V3.7.4 最终配置重跑为 8/8；初始 32k 结果 7/8 已保留。
AS. Model A product E2E：8/8 产品检查，16 次逻辑调用、23 次物理请求、353,252 Token、总延迟 3,170,874 ms；no-git 有 1 次可见 Final fallback。
AT. Model B product E2E：未完成。Holdout 后官方账户返回 HTTP 402，6 个核心用例 0/6；最小 Provider probe 同样确认 402，未结果导向重试。
AU. long-running/cancel：长任务心跳、取消、有限请求边界和唯一恢复测试通过；GLM 最长单用例 964,466 ms。
AV. persistence/readback：GLM E2E 8/8 snapshot readback；跨 Agent/模型读取同一持久化状态测试通过。
AW. Backend/H2：432 tests，failures 0，errors 0，skipped 1，BUILD SUCCESS。
AX. PostgreSQL：本地 Docker Desktop daemon 不可用；PR 两个最终 Run 与功能 master Run 的 PostgreSQL 16 Testcontainers 均 PASS。
AY. Frontend：lint PASS；contract tests 50/50；Next production build PASS。
AZ. Playwright：8/8 PASS。
BA. Hermes：6/6 PASS，13 tools；startup 161.6 ms，concurrent 298.4 ms，tool 150.9 ms。
BB. Obsidian：18/18 PASS；5,000 facts / 36 months / 100 capabilities / 1,000 evolutions；first sync 450.1 ms，177 writes，no-op 0 writes。
BC. secret scan：本地提交前为 0；PR 最终 head 和功能 master 的 sensitive-content 均 PASS。
BD. local archive path：用户本机 `ProjectFlow-Acceptance-Archive/V3.7.4`；绝对路径不提交。
BE. archive hash：`hashes.sha256` 覆盖 42 个归档文件，其 SHA-256 为 `E9FA44B6B45BFA9F9A18ABC8ED86A91422D8725B4EA25157ED976868B99993CE`。
BF. GitHub evidence index：`docs/acceptance-evidence/v3.7.4-evidence-index.md`。
BG. known risks：DeepSeek Holdout 未达标；DeepSeek 账户 HTTP 402；GLM Schema/reasoning 长尾与 fallback；本地 Docker 不可用但 GitHub PostgreSQL 已通过；npm audit 报告既有 3 个 high；GitHub 提示 actions/checkout@v4、setup-python@v5 的 Node 20 runtime 已弃用并暂由 Node 24 强制运行。
BH. unfinished items：双模型 Holdout 共同达标与 DeepSeek 产品 E2E 未完成，二者阻断 V3.8；不通过伪重跑掩盖。
BI. Foundation Generalization Gate：`PROJECT UNDERSTANDING FOUNDATION = NOT STABLE`；`STRONG FACT SAFETY GATE = NOT PASSED`；`REAL PROJECT GENERALIZATION GATE = NOT PASSED`。
BJ. V3.8 entry decision：`V3.8 EVOLUTION RECONSTRUCTION = BLOCKED`。
BK. Tag：NO。
BL. Release：NO。
