# ProjectFlow V3.9 Final Continuity Closure Report

记录时间：2026-08-25（Asia/Shanghai）

## 当前结论

本地最终实现与技术回归已通过，状态为 `LOCAL_FINAL_GATES_PASS / REMOTE_GATES_PENDING`。V3.9 尚未在本文件中声明最终关闭：PR #17 仍为 Draft，三 Provider 独立语义复核、同头普通 CI、merge、master CI、根启动器复验、acceptance backfill 和 cleanup 必须按顺序完成后再追加最终事实。

原 `human-continuity-review-worksheet.json` 保持 `NOT_REVIEWED` 且无字段被填写，规范化 SHA-256 仍为 `75f9f18836d87c77db0d8a89fd3541348d2b48f28673e68ffa57e96ff000f9df`。Owner 已通过 append-only policy 免除逐场手填，但本轮不声明 `HUMAN_REVIEW_PASS`，也不伪造评分或 reviewer judgement。

## 起点与隔离

远端 V3.9 PR #17 的起始 Head 为 `9a01e497b0b0d7ebcaf0b73e64aa6daa161a4450`，base master 为 `ab29b1ff0f842c029b5cf121bd584bd40fcf74b2`。原本地 master 落后且包含用户未提交修改，本轮只在隔离 worktree 的 `codex/v3.9-project-continuity-closure` 分支工作，没有 stash、reset、覆盖或清理原工作树。

## 实际关闭的风险

Dirty Revision 改为 snapshot 行锁内推进的项目级单调 generation。相同目标的 create、revert、reapply 会得到不同 revision；normal persist 与 cache-hit 只确认刷新开始时观察到的 marker，新并发写入不会被旧刷新清除。旧 H2 行的空 generation 从零安全起步，PostgreSQL 16 并发用例已加入远端门禁。

Current Project State 现在只由可见 Primary Story 按 `occurredTo`、`occurredFrom` 与稳定 ID 排序决定。旧 pinned Story、Supporting、隐藏 Story 和 Chapter presentation 不能劫持当前语义；Primary 缺少可确认结果时会返回明确限制，不回退到 Supporting。Overview、Gateway/Brief、Agent Context、Frontend 专用 Current State 读取与 Obsidian 最近发生顺序已对齐。

## 独立语义复核

新增原 12 个 continuity 场景的有界盲评包。任何 Provider 调用前都会校验 exact ID、字段、大小、答案标签、敏感内容与原 worksheet 状态。每个 Provider 只执行一次逻辑调用，物理请求、单请求输出、completion 和 total Token 均有固定上限。

真实复核工件必须为 `COMPLETE`、12/12，并通过严格字段集合、JSON 类型、固定预算、诊断、安全布尔和敏感内容门禁。模型共识只作为验收诊断，不升级 Strong Fact；`no`、`uncertain`、真实性风险或旧历史意外变化都必须保留给主 Agent 复核。

## 本地实测事实

- 后端全量 H2：668 tests，0 failure，0 error，9 个环境性 skip，耗时 5 分 44 秒。
- 独立语义复核合同：21 tests，0 failure，0 error，2 个真实 Provider 环境性 skip。
- 冻结 Ground Truth、T0–T7、Current State parity、Context revision、Correction、Dirty race、H2 旧库升级均在本轮后端门禁中通过。
- Frontend lint、生产 build、59 个 contracts 与 Playwright 9/9 通过。
- Hermes 10/10、Obsidian 27/27 通过。
- 敏感内容扫描未发现长 `sk-` 标记或新增 Authorization/API Key/Bearer 值；`git diff --check` 通过。
- 根 `Start-ProjectFlow.bat -NoBrowser` 从表示兼容修复后的最终未提交工作树完成 Frontend build、Backend/Frontend readiness，生成 build ID `T-GIr3SSltwRh96vQrC-y`；脚本退出后 3000/8080 监听数为 0。

本机 Docker Engine 未运行，因此新增 PostgreSQL 16 并发门禁尚无本地执行事实，必须由普通远端 CI 验证。真实 Provider 本地门禁按无安全配置正常跳过，不能描述为真实模型通过。

## 保留的失败与边界

历史真实 Provider run `32659635453` 的 Qwen Chapter 8/9 失败继续保留；后续成功不会覆盖该事实。Frontend 本地依赖审计仍报告 4 个 high 漏洞，该项属于 V3.10 dependency/supply-chain security 范围，不在 V3.9 临时升级依赖。

首次最终收口受保护 run `32778908166` 在 Head `83d6f2b4a72d91c883aac843c980212d8f34d285` 上保留了新的 Luna 失败：Chapter affected 与 continuity 工件已生成，但独立盲评在两个 16,384-token 请求后仍只有 reasoning、没有可见 JSON，工件状态为 `FAILED`、0/12，failure code 为 `REASONING_EXHAUSTED_OUTPUT`，失败工件 SHA-256 为 `722701739361e0428ecf9ad80b0121ad2274e746d67605e8f0c7ff0f0c58f3d7`。该失败没有被删除或改写。

根因是 evaluator 自行把 workflow 已配置的 65,536 quality-first 上限压低为 16,384，而不是 Provider Key、输入包或 production continuity 失败。修复保持 `max` reasoning，不降低质量档次；单请求恢复为 Provider 已配置的 65,536 有界上限，最多一次语义恢复，对应 aggregate completion/total 上限为 131,072/160,000。修复后的定向合同 27 tests 通过，2 个真实 Provider 环境性 skip；真实复验仍待新 Head 完成。

第二次受保护 run `32783687630` 在 Head `236bf279dafab4bf4460876797e97f954f40fa7f` 上证明预算问题已关闭：Luna 单次请求正常 `COMPLETED`，未截断，31,515 completion tokens、35,435 total tokens，且 gateway schemaMatched=true。但返回的 `attachmentSemanticallySupported` 表示未通过 evaluator 只接收字符串枚举的解析，工件以 `REVIEW_FIELD_INVALID_ATTACHMENTSEMANTICALLYSUPPORTED` 失败，0/12，SHA-256 `5a8013a36c8c7c5308ddffb5d6b2f36b3631e23600551a2fc96336ac5a8a6785`。Raw response 按安全合同未持久化，因此不伪造其具体原始值。后续兼容修复只把 judgement 字段的 JSON boolean 规范化为语义等价 `yes`/`no`，仍拒绝数字、对象、数组、未知标签和 boolean confidence，同时在盲评指令中明确要求带引号的字符串。

表示兼容修复后的 evaluator 合同 21 tests 通过，0 failure/error，2 个真实 Provider 环境性 skip；覆盖 judgement boolean、canonical 字符串大小写与空格、null、数字、数组、对象、未知标签、二值字段 uncertain、boolean confidence、非字符串 rationale 和 prompt quoted-string 合同。

V3.9 不创建 Tag 或 GitHub Release，不提前实现 V4 GUI。只有远端三 Provider、PR CI、merge/master、backfill 与 cleanup 全部完成并形成 append-only 证据后，才能把最终状态更新为 `PASS_BY_OWNER_APPROVED_AUTOMATED_AND_INDEPENDENT_SEMANTIC_REVIEW`。
