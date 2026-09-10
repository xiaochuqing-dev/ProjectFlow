# ProjectFlow V4.0-D：真实项目理解、来源边界与 GUI 收敛

本阶段保持 Draft。工程实现、真实仓库运行、来源审计与可理解性分别记录，不能用自动化通过替代产品语义合格。Desktop Shell 入口为 BLOCKED，Owner Review 为 NOT_REVIEWED。最终运行数据以链接的 Evidence 为准。

## 1. Phase Goal

让首次进入的用户看到项目身份、有证据支持的变化、真实声明、未知和工作线。继续使用午夜蓝 Workspace；优先来源真实性、正常入口连续性与渐进阅读。不新建看板、Git 客户端、模型网关或桌面外壳。

## 2. GitHub / PR / branch 基线

执行前核对 master、原工作区、worktree 与 #21/#22/#23。master 为 `1712841b77fd1e8146ce4ab6beaf404e5b1f7a53`；#23 仍 Draft，Head 为 `703120998f2296b4e615cd785e651a351c5dc8c9`，依赖 #22。独立分支为 `codex/v4.0-d-real-project-understanding`，[Draft PR #24](https://github.com/xiaochuqing-dev/ProjectFlow/pull/24) 的 base 为 `codex/v4.0-c-gui-productization`。

保留原工作区未提交内容，没有 merge、force push、Tag、Release 或 Ready。实现节点为 `358b9c2`（主要功能）、`5564c54`（初次读取、近期分页、AUTO）、`a795c29`（失败理解恢复与显示边界）及后续验收修正。真实 ProjectFlow 输入冻结在 `358b9c2`；输入版本和实现版本分别记录。

## 3. Current State 事实 / 计划边界

真实页移除 Demo 百分比、虚构里程碑、泛化 Agent 鼓励和官方阶段感。README 身份摘录标为项目声明，并保留相对来源、行号、hash 和时间。材料语义说明消费已持久化 Understanding；“理解当前材料”显式创建既有 Job，离开页面后能恢复。

“直接证据支持的变化”要求直接 Evidence 与允许状态，同时说明文件变更不等于运行验收。宽泛的前后端骨架 OBSERVED 条目收窄为文件变化，不把新增文件说成首次建立整套应用。模型失败时展示本地观察，不把文件数量当成功的产品理解。

## 4. Unsupported Claim Gate

`workspace-claims.ts` 拒绝无来源计划、非 DECLARED 意图、推断百分比、无基准“符合预期”和鼓励性判断。未知允许缺省，冲突保持原身份。每项目抽取 27 条可见陈述，结果见 [claim audit](acceptance-evidence/v4.0-d/claim-audit.json)。

这是显示约束和样本审计，不是通用证明器。引用有效不等于措辞精确，不等于功能运行成功。高风险 intent/plan/merge/dependency 的来源门槛和历史语义质量分别评估。

## 5. History 信息架构

默认“项目变化概览”，再选择“按时间查看”或“按长期主题查看”。保留 Chapter、Story、Thread 成员和身份。近期排序在服务端分页前执行；旧默认顺序保持兼容。删除真实 History 顶部重复的混合 Current State 摘要，防止 UNKNOWN 被冠以“当前可确认结果”。Story → 来源事件 → Evidence 继续在 V4 内展开。

## 6. 新用户可理解性设计

六项任务分别是：辨认身份和成果、寻找近期主要变化、追溯结论、区分声明/归纳/未知、理解多分支、阅读单分支。首轮独立模型能辨认身份和工作线，但难以从“材料/文档/骨架”标题理解产品变化。这是实际质量问题，不由测试数量替代。

## 7. Branch Workline 模型

复用固定命令执行器与 History Job。`worklinesV1` 作为既有快照的可替换扩展，不新增表、不写 Fact。GET 仅做归属校验、筛选和分页。类别覆盖主线、开发、审查、依赖、历史、未活动与未知。

本地 Git 为基础，GitHub 提供 PR、Issue、检查状态增强。PR title/body 为 DECLARED；目录变化摘要为 INFERRED，不从 branch name 猜意图。精确 merged PR HEAD 可处理 squash；Git containment 可确认提交已包含；ahead 大于零不能单独判未合入。依赖只来自 open PR 的非默认 base/head。

有界上限：300 条分支、60 条详细工作线、100 条 PR、8 个 open PR 正文；每条 40 个文件、8 个提交样本，保留取消和超时。UI 每页 12 条，单分支去掉冗余筛选；截断和本地/GitHub 时间可见。

## 8. ProjectFlow 真实 Dogfood

冻结 clone 有 338 个提交、19 条分支；比提示词多出的分支是本任务授权创建的 D 分支。首次 History 因旧十分钟整体上限 EXPIRED，保留部分成果与失败窗口。AUTO 修复后增量续跑 SUCCEEDED：4,154 个来源事件、451 条 Story、317 条 Thread、11 个 Chapter；三次真实请求含一次验证恢复。不能据此宣称全部历史都经过模型。

19 条工作线独立核对通过，包括 master、V4 A/B/C/D、旧 V3.x、fix 与无 PR 分支。#23→#22、#24→#23 来自真实 base/head。冻结 clone 与远端 D Head 不同，UI 显示来源差异。

## 9. Corporation-Agent 真实 Dogfood

输入冻结为 `f80553c61aaf19b19e729d47739d6b8f35c1dffd`，17 个提交、main 一条分支。首次 History 成功：521 个事件、34 条 Story、26 条 Thread、12 个 Chapter；一次 Sol 请求改写 16 条 Story，其余是确定性措辞。无变化重跑零模型。

main 的 HEAD、包含关系、活动时间和样本可核对；没有制造 PR、Review 或依赖。README 的企业沟通、附件、通知能力保留为声明。本轮未运行 Corporation-Agent 自身业务测试，不能升级为已验证能力。私有位置与 URL 不进入交付。

## 10. GPT-5.6 Sol / xhigh 真实使用

用户明确授权 Windows 用户级 RELAY 变量代替 micu 变量。生产配置为 `gpt-5.6-sol`、`OPENAI_RESPONSES`、xhigh，仍经过 Model Gateway 和安全凭据存储。temperature 未发送；输出上限 32,768 不是消耗目标。单请求 300 秒在实际超时后改为允许的 900 秒，没有降级模型或 effort。

真实规模、usage、完成类型和恢复见 [model usage](acceptance-evidence/v4.0-d/real-model-usage.json)。早期 Understanding 超时与 HTTP 500 保留于 failed-runs。失败快照曾被 no-change 缓存吞掉重试，已修复。修复后的两份材料理解各执行两次 transport request，分别在 1,205,276 / 1,207,037 ms 后失败，底层为 HTTP/2 `StreamResetException: CANCEL`，取消发起方及服务端原因未证实。Job 包装丢失规范化诊断而报告零计数，不能解释为零调用或零费用；安全异常计数证明每份两次，Token usage 未知。两份仍保留 MODEL_FAILED / STALE 本地观察，见 [project manifest](acceptance-evidence/v4.0-d/real-project-manifest.json)。

## 11. Claim Sampling

每项目 27 条，共 54 条，覆盖声明、计划缺省、Story、主线、合入、PR 意图、依赖和时间边界。声明核对原文、行号和原始换行下的 SHA-256；Story 核对持久化事件和 Git；工作线由独立读取交叉验证。本轮没有成功新增材料语义陈述，文件计数只记为本地观察。来源检查不冒充运行验收。

## 12. Model Blind Review

独立 reviewer 只看用户文本和截图，不读代码、DTO、Ground Truth 或已有评分。配置为 gpt-5.6-sol / xhigh，类型 MODEL_REVIEW。首轮 NEEDS_REVISION 已保留。第二轮确认来源展开、声明/归纳/未知区分、19 分支依赖链和单分支密度已改善，但 Current 具体能力和 History 有意义变化仍不足，结论继续 NEEDS_REVISION。复审后定向处理窄屏证据按钮遮挡和来源时间说明，不把这两项显示修复宣称为语义复审通过。详见 [model review](acceptance-evidence/v4.0-d/model-review-summary.json)。没有 HUMAN_REVIEW、Owner 评分或虚构签字。

## 13. Legacy GUI Convergence

| 入口 | 结果 |
| --- | --- |
| `/projects` | 默认 V4 项目库；`compat=1` 为显式工程兼容 |
| `/projects/[id]` | 默认 V4 Current；兼容模式保留 |
| 旧项目 History | 默认 V4 History，保留 Story/Thread 深链；审计显式进入 |
| `/settings` | 默认 V4 全局设置；旧设置需兼容参数 |
| 新建、本地、GitHub、ZIP、文本 | V4 Intake，复用已有 API |
| login / welcome | 普通按钮进入 V4；工程按钮单独标识 |
| dashboard、imports、files、旧记录/分析详情 | 工程兼容工具，不作为默认引导 |
| Provider / Agent 交接 | 保留 V4-C 已有流程 |

## 14. `/projects` 处理结果

真实根启动器上的 `/projects` 已进入午夜蓝项目库。卡片显示来源、成功读取时间、数据状态、刷新/注意/读取错误，没有模型进度。最终故障截图又暴露列表 503 时同时显示空库引导的问题；已阻止把读取失败展示为零项目空库，错误保留明确重试入口。旧产品标记改为工程兼容说明，Draft 没有写成正式 Release。

## 15. Intake 结果

五种入口统一视觉；创建失败保留输入，后续绑定/导入失败重试复用已创建项目。复用现有绑定、ZIP、文本和 GitHub API，读取走 Durable Job。缺少历史或计划保持未知。材料语义复用 Understanding，不新建 intake 引擎。

## 16. GUI 微调与 Agent 视觉审查

保留午夜蓝、山脉、星球、三栏和青蓝强调。处理长分支名、分页、窄屏、来源标签、错误、过期、未知和冲突；搜索复用深色控件，工作线弹窗保留正文边距。窄屏证据按钮单独占据底部空间，不覆盖正文滚动区。真实项目覆盖 390、640、1024、1280×801、1600，异常注入明确标为合成状态。Agent 视觉记录见 [visual manifest](acceptance-evidence/v4.0-d/visual-manifest.json)，与模型审查分开。

## 17. Demo / Real Isolation

Demo 的进度和叙述仅是已标识示例。真实读取失败保留有效结果或显示错误，不代入示例。Demo Provider 交互不发真实写请求。真实仓库与合成异常截图分目录标注。

## 18. Tests

前端 contracts 当前 69 项。初次本地完整浏览器 37 项中 34 通过，3 项仍断言已移除的 hero 混合摘要；修正为核对持久化变化、恢复、拒绝后保留，受影响套件 18 项全部通过，随后 CI 完整生产前端/Java E2E 37 项通过。一次误用默认 8080 生产构建运行 18037 隔离测试已终止，不能计通过；真实 Provider 默认配置确认未被改动。

`2c14bf1` CI backend/H2 为 739 项、12 skipped、0 failure/error，即 727 项执行，186 个 suite。Understanding 缓存修复定向 13 项通过。最终数量与门禁见 [verification](acceptance-evidence/v4.0-d/verification.json) 和 CI Evidence；Mock、固定模型、真实模型与浏览器范围分别标注。

## 19. PostgreSQL / Windows

必需 CI 实际运行 PG16 Testcontainers、Flyway、真实 V3.9 H2/PG 升级、凭据、Hermes、Obsidian 和 Windows portable。PG 常规集成 6 项、Flyway 1 项执行；条件真实模型 skips 不算 Provider 通过。

从工作区外执行根 `Start-ProjectFlow.bat -NoBrowser`，相对路径构建当前工作树，使用隔离数据和配置。验证前后端、login、welcome、项目库、真实项目、Current、History、Thread/Evidence、Workline、Settings。Enter 退出和端口释放见 [launcher](acceptance-evidence/v4.0-d/windows-launcher.json)。既有 runtime 版本号保留，不构成正式 V4 Release。

## 20. CI

`5564c54` 首轮通过，`a795c29` 因旧浏览器断言失败且保留记录。`2c14bf1` 的 Quality PR Run `34438810299` 与 Windows Run `34438809883` 成功；push/PR 共 20 项检查成功，对应 10 类必需 job，4 项 optional real-provider 跳过。后续定向显示修复的根构建与截图、最终交付 Head 的 PR 检查分别核验。详见 [CI](acceptance-evidence/v4.0-d/ci.json)，前一 Head 成功不套用到后续更改，SKIPPED 不写为 PASS。

## 21. Evidence

[索引](acceptance-evidence/v4.0-d/README.md) 包含 verification、真实输入、claim/branch audit、真实模型 telemetry、盲审、CI、Windows、视觉 manifest 和截图。不包含完整 Prompt、raw response、reasoning、凭据、私有 URL 或机器绝对路径；临时 clone 与日志留在忽略目录。

## 22. Failures / Fixes

修复空 History JSON 首次读取 500、分页后排序错误、History 隐含十分钟上限、失败 Understanding 缓存、任务完成 effect 丢弃结果、旧普通路由、混合确认/未知摘要、骨架首次创建过度措辞、单分支密度与搜索样式。全部沿用已有业务边界。

Provider 超时、HTTP 500 和历史验证恢复均为真实记录，不改写成成功。泛化历史措辞是更深的语义质量问题，不能仅靠界面改名解决。

## 23. Unfinished

Owner Review 未进行。历史中较多条目仍不能说明具体用户功能变化；旧 Agent 文件缺少可靠发生时间时，观察时间与开发时间仍需区分，History 现在明确说明来源时间的限制。既有 wording 合同仅允许模型改写已验证语义包，本轮不为更好看绕过 Evidence/Claim 限制。两份当前材料理解均失败，尚未形成成功的当前能力语义结果；Job 对包装异常的调用与 usage 诊断保留也不完整。

## 24. Risks

四个 README/roadmap 文件不构成全面计划搜索；其他历史计划可能未展示。工作线受远端权限、分页、上限影响；来源时间不是监听。无 PR 用途仍较粗。两个真实仓库不覆盖所有 squash/rebase/cherry-pick 拓扑，相关边界使用合成契约。模型成功计数不等于全部历史或功能已核验。

## 25. Owner Manual Review Guide

由根入口打开两个真实项目，回答身份与实际确认范围；在变化概览找三项有意义变化并展开来源；比较时间和长期主题；核对 #24/#23 依赖、Draft 与 Review 区别及 main 的简洁性；确认无来源计划与过期/失败提示。Owner 字段保持 NOT_REVIEWED，不代填评分，也不为提交 Draft 额外增加批准节点。

## 26. Desktop Shell Entry Assessment

`DESKTOP_SHELL_ENTRY = BLOCKED`。

主要 GUI 路径与工作线可以真实阅读，但独立审查发现历史语义具体程度不足，阶段成功标准尚未全部满足。不能用构建和引用检查宣称 Project Understanding 完全成型。不集成 Electron/Tauri，不自动 Ready 或合并。
