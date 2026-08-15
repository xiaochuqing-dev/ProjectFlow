# ProjectFlow V3.8.5 Final Chapter Closure 起始审计

审计日期：2026-08-15。审计结论：Chapter Representativeness 缺口真实存在；V3.8.5 仍为 PENDING_HUMAN_REVIEW / NOT PASS。

## GitHub 与本地基线

- Repository：`xiaochuqing-dev/ProjectFlow`，默认分支 `master`。
- PR #15 为 OPEN、Draft、未合并、MERGEABLE；Head 为 `fcb19a15acfb942286476679a95648ee9db7c0a9`，来源分支为 `codex/v3.8.5-history-quality`。
- 最新 PR 质量工作流为 `31740425461`，结论 success，Head 与 PR Head 一致。backend/H2、PostgreSQL、browser、frontend、Hermes、Obsidian、sensitive-content 七个 job 全部成功；GitHub 当前未配置 branch-protection required checks，因此不得把“工作流成功”误写成“已配置 required checks”。
- 主工作树 `master` 落后 `origin/master` 52 个提交，且存在用户未提交修改与未跟踪 Agent Result。本轮不切换、不暂存、不覆盖该工作树。
- 本轮从现有干净隔离工作树的 `fcb19a1` 新建 `codex/v3.8.5-final-chapter-representativeness`，只在该分支实施。

## 冻结证据状态

- Round 1 与 Round 2 继续保持 NEEDS_REVISION_NOT_APPROVED。
- Round 3 继续保持 30 Story / 8 Chapter、双 Provider 各 15/4；`reviewerCount=0`，人工字段为空。原 manifest、worksheet 和来源工件不得修改或补写 PASS。
- 正式 affected Provider run `31733839404` 的自动资格结果继续作为历史基线：GLM 与 DeepSeek qualification 19/19、scenarios 11/11；该自动结果不等于真人 Chapter 代表性通过。
- PR 在最终人工 Gate 前继续保持 Draft，不执行 Ready、merge、acceptance backfill、Tag、Release 或历史分支清理。

## 当前实现与缺口

- Chapter 初始边界主要由时间间隔、Primary 数量、事件密度、跨度和 Tag 决定；除同一 Commit 的独立主体外，没有通用的阶段成果簇一致性判断。
- 确定性标题从按时间排列的前六个 Primary Story 中只取前一项；摘要最多取前两项。大量 Supporting 虽不会直接进入标题，但“第一个 minor Primary”仍可劫持阶段中心。
- 大 Chapter 二阶段 Prompt 均匀抽样 Primary Story 摘要，没有代表成果簇、dominant/co-dominant/minor 角色、代表覆盖率、允许 Claim State 或必须覆盖的 cluster ID。
- Chapter Validator 目前只证明标题/摘要与任一 Primary Story 有具体文字重合，不能证明它覆盖 dominant cluster，也不能识别“只代表 minor Story”的合法但失真标题。
- 当前 ProjectFlow Dogfood 首章有 57 个 Story。GLM 标题为“建立项目使用说明”，DeepSeek 标题为“补充环境配置示例”；两者都可能被局部 Evidence 支持，但都不能代表整个大型阶段。这是本轮根因证据。

## 实施边界

本轮只修 Chapter grouping、representation plan、确定性 wording、Chapter Prompt/Validator、诊断与验收工件。ProjectFact、Technical Atom、Claim Attribution、Story schema、Story v12 Truthfulness、Evidence 绑定、Provider Gateway 与 correction 事实边界保持不变。实现不引入聚类依赖、Embedding、向量数据库、新模型或新持久化表。
