# ProjectFlow V3.8.5 RC3 当前状态审计

审计时间：2026-08-12（Asia/Shanghai）

## 基线

- GitHub 仓库：xiaochuqing-dev/ProjectFlow。
- 最新 master：`5cb5e49661206feb8f59885bea672c314c9374e8`。
- PR #15：OPEN、Draft、未合并；base 为上述 master；head 分支为 `codex/v3.8.5-history-quality`。
- 审计时 PR head：`46b91dcb9728ec6a33f86193e34a6b4c027bc909`。
- 最新 PR required CI：run `31534591531`，七个 required job 均成功。正常 PR workflow 中真实 Provider job 按条件跳过。
- 历史 workflow_dispatch run `31532558352` 整体失败；其中 GLM 与 DeepSeek 的 qualification/scenario job 成功，但 backend 与 PostgreSQL job 因当时缺少 Round 2 artifacts 失败。不得把该 run 整体描述为成功。

## 工作区选择

- 主工作区和旧 V3.8.5 工作树存在用户未提交内容，本轮不触碰。
- RC3 使用干净工作树 `ProjectFlow-v385-rc2`，其起点与 PR head 完全一致。
- 后续只更新 PR #15 的既有 head 分支，不新建 PR，不合并，不创建 Tag 或 Release。

## Round 2 冻结状态

- 原始 Round 2 manifest 和 worksheet 保留为不可变失败证据，不允许改写。
- manifest 原始字节 SHA-256：`e1aca397b469c4d1e4e4b4f6bb856306b2b3340bcb5df97e80d71a286a247349`。
- worksheet 原始字节 SHA-256：`8e9c04bde787b6bb6c2528f96e5d296dcf66186f66290298cf18ca21f68d73e7`。
- 既有规范化 LF hash 仍保留在原验收记录中；RC3 另以原始字节 hash 做防篡改回归。
- Round 2 正式结论必须从模糊的待审核状态修正为 `NEEDS_REVISION_NOT_APPROVED`，但只更新 review 文档，不修改冻结 artifacts。

## 精确 P0

Round 2 把提交 `ae9fba1e...` 叙述为“编写登录流程代码并形成实现”，并称“此前代码中还没有登录流程的实现”“这一阶段加入了实现登录流程所需的代码”。该提交的真实主题是初始化 ProjectFlow 骨架；与登录直接相关的文件证据只有登录背景图，JWT/Auth/login 的文字主要来自环境配置、README 或规划材料。`next-env.d.ts`、`next.config.ts`、`package.json`、`postcss.config.mjs` 等同提交文件不能证明登录流程已经实现。

## 可执行复现与根因

在当前生产代码中，以 Round 2 的登录背景图和四个前端配置文件构造 Evidence Profile：

1. Human Subject Label 因 `login-background.png` 得到“登录流程”。
2. Narrative Entailment Validator 在 Story 全局路径中发现任意代码扩展名，即把该主题判成 `IMPLEMENTED`。
3. 现有 login/auth 特判只要求主题或任一路径出现登录词；背景图满足主题锚定，无关代码文件满足 implementation anchor，因此特判没有阻止跨证据拼接。

根因是 Story 级扁平 Evidence 聚合丢失了“哪一个 Technical Atom 直接支持哪一个 subject/action/state”的结构关系。同 Commit、同 Story 或 Supporting 关系被错误当成可提升状态的直接证据。模型只是把错误的确定性上限写成了人话，并非唯一根因。

## 范围决策

RC3 将在现有 Reconstruction、Narrative Entailment、Prompt、Correction 和 Chapter 边界内完成以下最小闭环：

- 引入 provider-neutral 的 claim-level attribution：subject、action、state、outcome、直接与间接证据、来源权威、支持等级和降级原因。
- 状态提升只允许使用与同一主体和动作直接相连的 Technical Atom；同 Commit 和 Supporting Evidence 只提供上下文，不能提升 Primary Claim。
- 对 planned/configured/implemented/verified/removed/restored/unknown/conflicted 建立确定性上限，并让模型输出与 fallback 共用该上限。
- correction 只改变展示，不得把原状态提升为更强事实。
- Chapter 必须复述至少一个 Primary Story 的具体成果；空泛“围绕、推进、完善、建设”不能单独通过。
- 冻结十类反例、原 ProjectFlow P0 回归、Round 2 hash 回归、Round 3 30 Story/8 Chapter 人工验收包。

不新增数据库 schema，不改 ProjectFact/Timeline/Capability/Evolution 事实边界，不修改 Round 2 artifacts，不引入新依赖，不做 Stage B 合并或验收回填。
