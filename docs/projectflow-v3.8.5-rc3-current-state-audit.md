# ProjectFlow V3.8.5 RC3 当前状态审计

2026-08-23 superseding note: Final Chapter Closure is now the current engineering layer. It preserves this RC3 audit, replaces the stopped GLM slot with GPT 5.6 Luna, adds Qwen3.7 Plus beside DeepSeek V4 Flash, and freezes a separate unscored Chapter package. The controlling status remains `HUMAN_REVIEW_REQUIRED / NOT PASS`; see `projectflow-v3.8.5-final-closure-report.md`.

审计日期：2026-08-14。当前结论：PENDING_HUMAN_REVIEW_ROUND3 / NOT PASS。

## 基线

- GitHub 仓库为 `xiaochuqing-dev/ProjectFlow`，远端 `master` 为 `5cb5e49661206feb8f59885bea672c314c9374e8`。
- PR #15 为 OPEN、Draft、未合并，base 为 `master`，远端 head 分支为 `codex/v3.8.5-history-quality`。
- RC3 在独立工作树 `ProjectFlow-v385-rc2` 和本地分支 `codex/v3.8.5-rc3-truthfulness` 执行，通过同一远端 head 更新 PR #15。当前生产修复 head 为 `539dfc9802069dec40207179f65b873bf862872c`；Round 3 Provider 来源 head 为 `73d11250cddce3594d5ddb4ef54cd8c6d652dac7`。前序修复与失败证据继续保留。
- 主工作树、旧 V3.8.5 工作树和其他 worktree 含独立分支或用户状态，本轮没有改写、重置、删除或清理它们。
- RC3 启动前最新完整 required CI 为 run `31534591531`。新真实模型 run `31574016609` 的 backend/PostgreSQL 因其 head 尚无 Round 3 文件而按设计失败，不能冒充最终静态 CI；最终 Evidence head 必须重新取得七项 required job 全绿。

## Round 2 冻结状态

Round 2 正式结论为 `NEEDS_REVISION_NOT_APPROVED`。原 manifest 与 worksheet 不做手工修文，继续作为不可变失败证据：

- manifest raw SHA-256：`e1aca397b469c4d1e4e4b4f6bb856306b2b3340bcb5df97e80d71a286a247349`；canonical-LF：`b2841c74491d172919db4a37e723d6533ad99f77799e0191fd5d2a7bdb90e887`。
- worksheet raw SHA-256：`8e9c04bde787b6bb6c2528f96e5d296dcf66186f66290298cf18ca21f68d73e7`；canonical-LF：`44655c49ef0d21c58e7aef7df4e1295dba6e48a5ddff3039f4d42edb96824692`。

## 精确 P0

Round 2 的 GLM `glm-story-14`、Story `story-2bda5e730c55c66ee182` 把提交 `ae9fba1e60758252635695b797169dfde3c41e0a` 描述为“编写登录流程代码并形成实现”，并声称登录流程已有代码实现。该提交的主题是初始化 ProjectFlow skeleton；样本列出的 `next-env.d.ts`、`next.config.ts`、`package.json` 和 `postcss.config.mjs` 与登录实现没有直接 subject/action 关系。README、API 设计、认证配置、背景图和同提交的其他代码也不能拼成“登录已实现”。

## 根因

旧实现先把 Story 内 Technical Atom 的 subject、来源类别与代码强度扁平聚合，再判断整体状态。一个来源中的“登录”语义因此可以借用同 Story/Commit 中无关代码的 IMPLEMENTED 强度。旧 Validator 只验证总体主题和文件类别，没有保留“哪个 Atom 直接支持哪个 subject/action/state”的归属，所以模型只是把错误上限写成人话，并非唯一根因。

第二个 Provider-neutral 根因由 run `31574016609` 暴露：Chapter 初次输出未通过具体性校验后，共用的 `validationRepair` 固定要求模型使用 Story 阶段才存在的 `OUTPUT_TEMPLATE_JSON`；Chapter 原始输入只有 `CHAPTER_SYNTHESIS_JSON`。这会让本可修正的 Chapter 请求收到错误协议。RC3 在 `c4e020c` 中把 Chapter repair 单独绑定到原始 Chapter JSON 和仅含 chapterId/title/summary 的 schema，Chapter prompt 从 v5 升至 v6；阈值、Ground Truth 和事实边界均未改变。

同一 run 的 DeepSeek Dogfood 场景又证明区域级归因仍有上限缺口：`project-area-frontend` 是宽泛工程区域，不是可验证的具体功能主体，但该区域内任意实现类文件仍能让整体 Claim 达到 IMPLEMENTED。于是精确 ae9f 样本虽已不再声称“登录实现”，底层“前端项目骨架”区域 Claim 仍被判 IMPLEMENTED，严格 P0 门禁继续失败。`92053e5` 对所有 `project-area-*` 宽泛主体设置 OBSERVED 上限；代码 Evidence 仍作为直接可观察变化保留，精确主体如 LoginExperience 仍可达到 IMPLEMENTED，因此不是隐藏措辞或模型/仓库特判。

第三个 Provider-neutral 文案根因由 run `31580355605` 暴露：GLM 的结构、Evidence、安全和窗口均通过，但两个 Holdout 的最佳 Primary Story 标题/摘要只写了动作与对象，没有明确结果。例如“编写成果导出功能的代码”加“涵盖代码创建与修改”不能回答形成了什么结果。DeepSeek 在同头没有该波动，说明事实边界已稳定，但提示词约束本身不足以保证所有 Provider 的首层标题质量。`539dfc9` 把冻结 Title AOR 语义加入生产 Validator；否则安全的弱标题不被包装成纯模型成功，而是保留已通过同一门禁的确定性标题/摘要，Story 标记为 `MODEL_VALIDATED_WITH_DETERMINISTIC_TITLE`，diagnostics 与安全工件记录累计回退数。Story prompt 升至 v12，旧窗口缓存随版本失效；阈值和 Ground Truth 未改。

## RC3 范围决定

RC3 采用最小 Provider-neutral 修复：Technical Atom 级 Claim attribution、direct/indirect Evidence、逐 Claim 状态上限、宽泛区域主体 OBSERVED ceiling、保守降级、correction 不提升事实、Title AOR 确定性保底及公开计数、Chapter 具体成果门禁、同一泛化提交中独立 Primary outcome 的确定性边界，以及 Story/Chapter 分阶段 repair 合同。没有新增数据库 schema、依赖、Provider 特判或新的模型入口；ProjectFact、Timeline、Capability、Evolution、Gateway、Hermes 与 Obsidian 的事实边界保持不变。

冻结 Ground Truth 自 RC2 head `46b91dcb9728ec6a33f86193e34a6b4c027bc909` 起没有修改，SHA-256 为 `ab7be7129130645000e9028031132c0b8e9362a7e6d1efb7b9d4abf0318d7d3f`。Title AOR、Chapter precision、Evidence 和安全阈值没有降低。

## 真实运行历史

- run `31571883309`，head `98d3d4812b207c781594070ef8bac253590d43a2`：DeepSeek V3.8.0 出现 1 个 missing-reason-unknown，V3.8.5 Holdout 有 3 个 Title AOR 失败和 1 个 Chapter precision 失败；GLM 与两个场景 job 被取消。该失败和取消保持可追溯。
- run `31574016609`，head `4935884b12ad2be0ea4ab668687d7f0aa21134d4`：DeepSeek qualification 19/19 通过；GLM 的 19 个评分 case 全部通过且安全计数为 0，但 `cal-conflict-preservation` 与 `holdout-unrelated-commit` 各有一个 Chapter 在错误 repair 合同后进入安全 fallback，因此 qualification 严格判 false。两家 scenarios 均为 10/11，唯一失败都是 Dogfood 的 ae9f 区域 Claim 仍为 IMPLEMENTED。DeepSeek scenarios 为 64 次物理请求、1,030,298 tokens、2,453,365 ms 模型延迟、3 次 repair；GLM scenarios 为 53 次物理请求、808,194 tokens、3,575,463 ms 模型延迟、2 次 repair。两家安全持久化计数均为 0。
- run `31580355605`，head `92053e58b7ead35ffc84cc4db7eeea6bda76e17c`：DeepSeek qualification 19/19 通过，38 请求、158,776 tokens、670,331 ms、1 次成功 repair。GLM 41 请求、161,970 tokens、1,305,646 ms、5 次成功 repair、0 repair failure、0 安全持久化问题，但 `holdout-rename-move-split-merge` Title AOR 为 0.0000、`holdout-unrelated-commit` 为 0.3333，因此严格 qualification 失败。DeepSeek scenarios 为 10/11；Dogfood 与五类非代码通过，`schema-failure-isolation-and-retry` 因注入局部 schema failure 后独立窗口未继续而失败，55 请求、955,811 tokens、2,059,855 ms、3 次 repair。GLM scenarios 为 11/11，54 请求、842,640 tokens、3,287,611 ms、3 次 repair。两家安全持久化全 false；场景结果不得用于覆盖 qualification 失败。
- PR run `31583262597` 暴露 Round 2 不同 checkout 换行会得到不同 raw hash；内容本身未变化。`b9e9c2d` 强制 canonical-LF hash 不变，并把 raw bytes 限制在已记录的 LF/CRLF 两个值。后继 PR run `31584325448` 中 Round 2 contract 1/1 通过，backend 597 项只剩尚无 Round 3 文件这一项失败。
- run `31586433372` 使用 validation head `b9e9c2d`、affected scope 和双 Provider max。DeepSeek qualification 19/19 通过：39 请求、180,073 tokens、843,690 ms、3 次 repair、3 次公开确定性标题回退。GLM qualification 19/19 通过：43 请求、166,130 tokens、1,057,851 ms、7 次 repair、9 次公开确定性标题回退。两家 Title AOR/Chapter precision 均为 1.0、failed/pending window 与 repair failure 均为 0，安全持久化全 false。DeepSeek scenarios attempt 1 为 11/11：60 请求、1,023,867 tokens、2,510,634 ms、3 次 repair、129 次公开确定性标题回退，Dogfood P0 保持 OBSERVED，安全扫描为 0。GLM scenarios attempt 1 为 1/11：首个 non-code presentation 用 1 请求、10,012 tokens、66,455 ms 通过，之后调用不可用。仅重跑该 job 的 attempt 2 为 0/11、0 个成功请求；两个 attempt 的安全工件均未持久化 HTTP 状态，因此不能猜测外部原因，也不能相互覆盖。下一步只运行 correction 最小诊断，并仅输出 HTTP/传输/格式分类与请求数。
- correction-only 诊断 run `31592405476`，head `f3d520432a0be857cd21255051c796b28359fbfb`：两个 Story 窗口分别在两次有界请求后得到 `HTTP 429`，安全日志只包含场景、任务、状态码分类和请求数。由此排除本地 Schema/Prompt 解析为本次直接失败点，但 429 究竟是短期限流还是额度状态仍不能从正文推断。该 run 的 browser、frontend、Hermes、Obsidian、sensitive jobs 通过；backend/H2 与 PostgreSQL 只因 Round 3 清单尚未冻结而失败。
- 同一 correction-only run 的 job-only attempt 2 与更长冷却后的 attempt 3 都仍是两个窗口各 `HTTP 429`、每个窗口两次请求、0 个成功模型调用。重复结果排除单次网络抖动，但在不读取响应正文的边界内仍不能区分 Provider 的速率窗口与额度耗尽；完整 11 场景继续暂停，避免无意义消耗。
- 2026-08-14 correction 探针 run `31733370522` 在同一来源 head 上通过 GLM 资格与 correction 场景，确认 GLM 容量恢复。
- 正式 affected run `31733839404` 使用双 Provider max：GLM 与 DeepSeek qualification 均为 19/19，scenarios 均为 11/11。GLM scenarios 为 52 请求、798,608 tokens、1 次 repair；DeepSeek scenarios 为 57 请求、1,002,415 tokens、2 次 repair。两家 Dogfood 的旧 ae9f P0 均为 OBSERVED，非法 Evidence、跨项目引用和不受支持强事实均为 0，安全持久化字段全 false。
- Evidence head `49622f16aebf77e892c70a5b091f17c2b8ebaa6c` 的 push run `31740051324` 与 PR run `31740054761` 均成功，七项 required job 全绿。

## Round 3 冻结

六份同头真实 Provider 工件完成安全扫描与 canonical-LF 哈希绑定后，Round 3 已冻结为 30 Story / 8 Chapter，双 Provider 各 15/4。manifest 与 worksheet canonical-LF SHA-256 分别为 `f316b71a6bec24f7ba40c2da81ef210b101b3ca238c688793fa32d48be877c1b` 与 `4d57d7d1fa5bb975465db9be413f70cf943ca7c9c70d8174ba0d4dcdd7d85ca6`。reviewerCount 为 0，姓名、评分、布尔判断、备注和 PASS/FAIL 均为空；自动化不得替真人填写。

## 禁止提前执行

Round 3 真人字段尚未填写前，不得把 PR #15 转 Ready、合并、回填最终 master 元数据、创建 Tag/Release，或清理分支/worktree。自动化通过只允许把状态推进到 `PENDING_HUMAN_REVIEW_ROUND3`，不能写成 V3.8.5 PASS。
