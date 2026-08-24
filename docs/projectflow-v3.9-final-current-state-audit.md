# ProjectFlow V3.9 最终当前状态审计

审计时间：2026-08-25（Asia/Shanghai）

## 结论

PHASE A0 的 GitHub 与本地重新审计完成。远端事实与正式执行任务书的参考状态一致：V3.9 PR #17 仍为 Draft、OPEN、MERGEABLE/CLEAN，Head 为 `9a01e497b0b0d7ebcaf0b73e64aa6daa161a4450`，base master 为 `ab29b1ff0f842c029b5cf121bd584bd40fcf74b2`。V3.9 生产与评测源码 Head 仍为 `eb38c78fe70d3cf9280e716f7fc906d8729b15b1`。

普通 push/PR CI runs `32684305540` 与 `32684307889` 均为 SUCCESS。受保护真实 Provider affected run `32666372066` 仍为 SUCCESS；首次 Qwen Chapter 8/9 的失败 run `32659635453` 仍保留，没有被后续成功证据覆盖。

## 本地状态与保护措施

原 master 工作树落后远端 121 个提交，并包含用户未提交内容：`frontend/playwright.config.ts`、`start-projectflow-embedded.ps1`、`start-projectflow.ps1`，以及三组未跟踪 Agent Result。未执行 stash、reset、checkout 覆盖或清理。

本轮从远端 V3.9 Head 建立独立 worktree 和跟踪分支 `codex/v3.9-project-continuity-closure`。该 worktree 创建时为 clean，未发现合并冲突。仓库另有历史 worktree；本轮不删除或改写它们。

## Provider Secret 与验收状态

Repository Secrets 列表包含 `PROJECTFLOW_DEEPSEEK_API_KEY` 和 `PROJECTFLOW_REAL_MODEL_API_KEY`。GitHub 只公开名称与更新时间，不能据此证明值当前有效；有效性必须由受保护真实 Provider workflow 实际请求确认。密钥值未读取、未写入仓库、文档、日志或命令参数。

现有 V3.9 状态仍为 `TECHNICAL_ACCEPTANCE_PASS / HUMAN_REVIEW_REQUIRED`，12-scenario worksheet 保持 `NOT_REVIEWED`。本轮项目 Owner 已直接要求按正式任务书执行到底，并允许用自动化、独立语义审查和 Owner policy 替代逐项手填；该决定需要新增 append-only policy evidence，不能反向改写原 worksheet，也不能伪造人工评分或 `HUMAN_REVIEW_PASS`。

## 与任务书参考状态的差异

远端 PR、Head、base、普通 CI、受保护 Provider run 和 Secret 名称均无实质差异。新增本地事实是原 master 工作树存在未提交修改且显著落后，因此后续开发只能在隔离 worktree 进行。

## 当前阻断审计

正式任务书指出的两个 V3.9 收口风险在当前 Head 仍存在：Dirty Revision token 由 project、reason 和 affected revision 的确定性 hash 生成，同一目标重复写入可能形成相同 token；Current Project State 先按 pinned 排序，再选择第一条非空 afterState，旧 pinned Story 可能改变“当前”时间语义。两项均须在 Ready/merge 前关闭。

## 本轮关闭结果（未提交工作树）

两项 P0 已在隔离工作树关闭。Dirty Revision 改为持久化单调 generation，并在 snapshot 行锁事务内生成 `g<generation>` token；normal persist 与 cache-hit 都只 acknowledge 刷新开始时实际观察到的 revision。H2 并发、create/revert/reapply、normal/cache-hit 竞态和旧 schema 缺列升级均有自动化覆盖；PostgreSQL 16 并发覆盖已加入 Testcontainers 门禁，但本机 Docker Desktop 未运行，须由远端 CI 提供运行证据。

Current Project State 改为只由可见 Primary Story 的真实发生时间决定，pinned、Supporting、隐藏 Story 和 Chapter 选择都不能改写当前时间语义。Overview、Gateway snapshot/brief、Agent Context narrative、Frontend 专用 Current State 读取和 Obsidian 最近发生排序已对齐，并覆盖 Primary 空结果、Supporting-only、同时间与空时间字段等回归。

Owner 免除逐项手填后，原 worksheet 继续保持 `NOT_REVIEWED`，没有伪造人工评分。新增盲化独立语义复核：输入在 Provider 调用前校验 exact 12 个 ID、字段、大小、答案标签和敏感边界；三 Provider 各使用一次有界逻辑调用，经专用 ModelTaskType 和现有 Gateway/adapter 执行；工件必须明确 `COMPLETE`、12/12 judgement、预算合规且不含 prompt、raw response、reasoning、凭据、request ID、base URL 或绝对路径。

本地实测：V3.9 定向后端 93 项通过（1 个真实 Provider 环境性 skip）；全量 H2 664 项通过（8 个环境性 skip）；独立复核加固定向矩阵 27 项通过（2 个真实 Provider 环境性 skip）；Frontend lint、生产 build、59 个 contracts、Playwright 9/9、Hermes 10/10、Obsidian 27/27、敏感内容扫描和根启动器双端健康检查全部通过。`logs/last-embedded-build.json` 已生成，启动后 3000/8080 端口均已释放。
