# ProjectFlow V3.9 验收报告

报告日期：2026-08-24

基线 master：`ab29b1ff0f842c029b5cf121bd584bd40fcf74b2`

生产/eval 源码 Head：`eb38c78fe70d3cf9280e716f7fc906d8729b15b1`

Draft PR：`#17`

当前总状态：`TECHNICAL_ACCEPTANCE_PASS / HUMAN_REVIEW_REQUIRED`。V3.9 不是 Final PASS。

## 实现结论

V3.9 已在原有 ProjectHistory source-event upsert、31 天 overlap、Window Planner/Checkpoint、Correction overlay、Context Package v2、Gateway/Hermes/Obsidian 链上完成持续闭环，没有建立第二套 History、Fact、增量或投影引擎。新增内容包括有界 Continuity Delta、未受影响 Story/Thread/Chapter 身份稳定、Correction 安全续接、持久化 Current Project State、Context revision 联动、内部 dirty revision，以及 Obsidian Current-State-only 增量更新。

## 确定性、Dogfood 与工程门

实现前冻结 30 个 Continuity cases，execution map 已逐条绑定真实 Maven/Python 测试。完整 Backend/H2、PostgreSQL 16、前端、Playwright、Hermes、Obsidian、敏感内容和根启动器通过；同头 push/PR runs `32666198144`/`32666201528` 为 SUCCESS。T0–T7 证明 no-op 0 请求、稳定身份、主题续接/分离、Correction、rewrite Event conservation 和失败 checkpoint 恢复。

## 真实 Provider 与失败保留

首次 run `32659635453` 保留为失败：Qwen Chapter regression 为 8/9，`projectflow-current-history-dogfood` 有 1 个 unsupported Claim。根因是 Story enhancement 后的 Chapter 回填只验证任一 Primary 的叙事锚定，没有验证当前代表簇计划；一个泛化回填又替换了已选中的公开成果措辞。修复后新增两个精确回归，并通过原 ProjectFlow Dogfood 路径。

受影响重验 run `32666372066` 为 SUCCESS。GPT 5.6 Luna Responses/max、DeepSeek V4 Flash Chat/max 和 Qwen3.7 Plus Messages/max 均通过 qualification 19/19、Chapter regression 9/9 与 V3.9 continuity 3/3。Qualification 合计 57/57，Chapter 合计 27/27，continuity 合计 9/9；三种协议的最终 no-change 都是 0 模型请求。

## 人工门与发布边界

真人 worksheet 冻结 12 个 Continuity 场景，当前 `reviewer=null`、`status=NOT_REVIEWED`，所有判断与评分字段均为 null。用户要求暂时不处理人工评分，因此不由 Agent 或模型代填，也不把自动测试等同于人工 PASS。PR #17 保持 Draft；未完成的 Ready、merge、master CI、acceptance backfill、最终 master 验证和分支清理不会被预填。没有 Tag，没有 Release，不批准 V3.10 ENTRY。

## 本机环境记录

首次本机 PostgreSQL profile 在 Testcontainers 初始化阶段因 Docker engine 未启动而失败；engine 启动后，同一源码以 PostgreSQL 16.14 完成 5/5，没有用代码改动掩盖环境失败。首次本机 Playwright 也曾因该进程 PATH 不包含已安装 Maven bin 而无法启动后端；向该次进程补入 D 盘已安装 Maven 后 9/9 通过，没有修改产品或测试。
