# ProjectFlow V3.8.0 验收报告

报告状态：最终验收通过（本文件随验收回填 PR #14 合并生效）

更新日期：2026-08-04

## 1. 最终版本

最终版本为 3.8.0。Backend、Frontend 和文档版本均已更新，功能已合并 master；本阶段不创建 Tag 或 Release。

## 2. 基线 master SHA

fd5ce827245f4fc4a20ecda15c63fc03313505ab。

## 3. 功能 PR

功能 PR 为 #13：`https://github.com/xiaochuqing-dev/ProjectFlow/pull/13`，已通过 required checks 并合并 master。

## 4. 功能 merge SHA

ea0cc126c17f36fea60af01f2dd060a94282d5c4。

## 5. 验收回填 PR

验收回填 PR 为 #14：`https://github.com/xiaochuqing-dev/ProjectFlow/pull/14`，只修改验收元数据。其 merge SHA 由 GitHub 合并时生成，并在最终回复记录；报告内容随该 PR 合并进入 master 后生效。

## 6. 最终 master SHA

当前已验证 master SHA 为 ea0cc126c17f36fea60af01f2dd060a94282d5c4。验收回填合并后的最终 master SHA 由 GitHub 生成，并在最终回复记录。

## 7. GitHub Actions

PR #13 已运行 required CI。提交 `077e218` 的 push run `30811074162` 与 pull_request run `30811227750` 保留了 acceptance manifest 使用 Windows 工作树 CRLF 哈希导致的首次失败。提交 `d3a935c` 的 push run `30811621732` 与 pull_request run `30811625114` 中，Frontend、PostgreSQL 16、Hermes、Obsidian、Sensitive Content 和 Browser E2E 均通过，`backend-unit-and-h2` 暴露同秒事件排序缺陷。修复提交 `ffe35de` 的 runs `30814897379`、`30814901364` 首次全绿；最终功能 head `dbeae33` 的 push run `30842484912` 与 pull_request run `30842488653` 全部通过 Backend/H2、PostgreSQL 16、Frontend、Browser E2E、Hermes、Obsidian 和 Sensitive Content。

独立 workflow_dispatch run `30832103333` 的全部作业成功，其中 `optional-real-provider` job `91748308607` 完成 GLM 38-run、17-case 产品链路和 Project History 合同验收。

功能 merge 后的 master run `30842827453` 首次只有 Browser E2E 失败，原因为 Playwright webServer 启动进程一次性退出；同一提交在功能 PR 两轮 Browser E2E 均已通过，其余 master jobs 也全部成功。保留该失败后仅重跑失败作业，attempt 2 的 Browser E2E 与整轮 required CI 成功。

## 8. PostgreSQL 16

本机 Docker Desktop 守护进程未运行，因此没有伪造本地 Testcontainers 结果。GitHub Actions runs `30842484912`、`30842488653`、`30832103333` 和 master run `30842827453` 的 `postgres-integration` 均通过。

## 9. 当前测试结果

- ProjectHistoryReconstructionTest：19/19 通过；连同 ProjectHistoryPromptBuilderTest 为 20/20。
- 同秒 Git 拓扑回归：两个原失败场景共同通过，新增/修改/删除/恢复场景额外连续重复 4 次通过。
- 三公开仓库有界验证：1/1 通过，覆盖三个固定公开项目。
- DeepSeek 与 GLM 项目历程真实模型合同：各 1/1 通过。
- GLM 冻结评测：38/38 成功，51 requests、501,188 Token、0 failure/timeout/schema/degradation；Critical Evidence Recall 0.9610、Evidence Precision 1.0000、Tool Precision/Recall 0.9792、Deep-read Sufficiency 0.8333、Dynamic View Recall 0.9412、Repeatability 0.9858、Unsupported Claim 0。Conflict Detection 0.6667 作为限制保留。
- GLM 产品链路：17/17 成功，33 logical / 33 physical requests、433,092 Token、3,246,152 ms，Invalid Evidence 0、Degraded 0。
- Backend H2 full suite：482 tests，0 failure，0 error，1 skipped；跳过项是显式 opt-in 的真实开源仓库 benchmark，不是功能失败。
- Frontend：TypeScript/lint 通过，contract 55/55 通过，Next.js 生产构建通过。
- Playwright：8/8 通过，运行真实前端、后端和固定模型服务。
- Hermes MCP：8/8 通过，19 tools；并发读取与故障恢复通过。
- Obsidian：21/21 通过。大样本为 5,000 facts、36 months、100 capabilities、1,000 evolutions、183 files；首次同步 424.1 ms，no-op 写入 0。
- 根启动器：`Start-ProjectFlow.bat` 的 CheckOnly、npm ci、production build、backend/frontend start、health 和 stop 全部通过；`logs/last-embedded-build.json` 已生成，3000/8080 无残留监听。
- 安全：提交内容密钥模式扫描和 `verify_v380_acceptance_evidence.py` 均通过。

## 10. 独立调研对象

GitHub Activity、GitLab CE bulk push、Gitea、GitButler、Gource、OpenProject、release-please、Changesets、git-cliff、Changie、git-chglog、Obsidian 官方 URI、Advanced URI、Local REST API with MCP、Obsidian Git、Dataview 和 Bases。

提示词中的调研只作参考。本轮重新读取官方仓库、源码 blob 和公开 Issue，固定 SHA 与日期见两份 research 文档。

## 11. 采用的模式

- Overview → Chapter → Story → Thread → Raw Event → Evidence 渐进下钻。
- 展示聚合与原始事件库存分离。
- 动态时间边界、稳定主体、显式 transition、覆盖缺口和 unknown。
- 单次有界模型措辞改写，工程层拥有成员、时间、Evidence 和 authority。
- 官方 Obsidian URI 默认，Advanced URI、REST/MCP、Dataview/Bases 分级可选。

## 12. 拒绝的模式

- Commit 列表、文件树或动画图作为主界面。
- release note 替代全生命周期历史。
- 强制 Conventional Commits 或 Changesets。
- Git restore/undo、branch stack、看板、工时、团队管理。
- 复制 FSL/GPL 实现或引入新的 Git 引擎、parser、RAG、向量库、workflow engine、daemon。

## 13. 新增依赖

没有。仅修改版本号，复用现有依赖与边界。2026-08-03 的 `npm audit` 对当前依赖图报告 3 个 high：PostCSS 两个文件读取/路径问题和 Sharp/libvips 继承漏洞，并把 Next 作为受影响聚合项。注册表只给出不合理的 Next 9.3.3 major downgrade 自动修复，因此本阶段没有执行 `npm audit fix --force`；风险已进入 known-risks，后续应在官方兼容修复版本可用时单独升级并重跑全部前端门禁。

## 14. License 与 THIRD_PARTY_NOTICES

未复制第三方代码。THIRD_PARTY_NOTICES 已记录 V3.8.0 调研对象、采用模式和拒绝原因。最终 diff 与依赖锁定仍需在提交前复核。

## 15. 项目历程最终层级

Overview、动态 Chapter、Change Story、Evolution Thread、Raw Event、Evidence 六层。Capability 是可选关联，不是通用前提。

## 16. 大历史压缩

先把同一提交和同一稳定主体的重复来源事件组织为 Story，再按时间 gap、密度、Tag 和跨度形成 Chapter。Overview 只取代表性 Chapter；完整列表分页。原始事件不删除。

## 17. 新增、删除与恢复演变链

Transition 显式保留 CREATED、MODIFIED、REMOVED、RESTORED、REVERTED、REAPPLIED 等状态。同一稳定主体跨 Story 形成 Thread，测试已覆盖新增→修改→删除→恢复和拆分→合并→撤销→重新实现。

## 18. 避免文件名和 Commit message 主导

稳定路径键与 Conventional Commit 文案归一化分离。批量跨区域提交按 backend、frontend、docs 等稳定项目区域折叠；空白、fix、update 等泛化 Commit 不能独立主导 Story。Commit message 只作候选来源说明。

## 19. ProjectFlow dogfood

197 Commit、2,611 Source Event、536 Story、27 Chapter、392 Thread。V3.7 固定窗口的 Git 事件与跨来源 Story 统计口径不同，不解释为直接压缩比。非法 Evidence、跨项目引用和不支持强事实均为 0。

## 20. 其他真实项目

Kubernetes、MDN Content 和 Reveal.js 固定 SHA 验证通过。三个项目均因浅克隆诚实标记 DEGRADED/PARTIAL；Kubernetes 达到 20,000 Event 上限并公开披露未读取范围。

## 21. 真实 Provider

DeepSeek deepseek-v4-pro / OpenAI Chat Completions 通过固定 Prompt v2 合同：1 次请求、1,539 Token、13,510 ms。GLM glm-5.2 / OpenAI Responses 通过同一合同：1 次请求、4,537 Token、52,278 ms。两者的结构、安全和 Evidence 违规均为 0，跨 Provider 硬门禁已满足。

## 22. 关键安全计数

当前正式产物中 Unsupported Claim、Invalid Evidence、Cross-project reference、Reason without Evidence 均为 0，eventConservation=true。提交内容密钥模式扫描与验收产物路径、敏感字段、长度和 SHA-256 校验均通过。

安全冻结清单为 `docs/acceptance-evidence/v3.8.0/acceptance-freeze-manifest.json`。清单冻结七个正式 Git blob 的长度和 SHA-256，避免 Windows CRLF 与 Linux LF 造成平台相关哈希；清单记录跨 Provider、PostgreSQL required CI、功能 merge 和 master checks 已通过，最终状态将在验收回填 PR 合并时生效。

PR #13 首轮 run `30811227750` 的 `sensitive-content` 因最初清单冻结 Windows 工作树 CRLF 字节而失败；四个 JSON 在 Git 提交后转为 LF，导致长度和 SHA-256 不同。修复后校验器直接读取 Git index/blob 的规范字节，未修改验收产物语义或降低安全门槛。

PR #13 run `30811621732` 和 `30811625114` 的 `backend-unit-and-h2` 又暴露同秒事件排序缺陷：来源事件的 stable key 包含随机 projectId，不能充当历史次序；复杂 Merge fixture 也证明 SHA 字典序不能替代 Git parent 拓扑。修复保留原始 occurredAt、Event、transition 和 Evidence，只稳定工程分组次序，没有降低 Story/Thread Ground Truth。

GLM run `30816468130` 保留了两项真实产品链路失败：large-middle 未生成 Content Map，conflicting-final-docs 使用错误文档类别断言；History 又因脚本顺序未执行。修复大型源码采样、类别断言和 CI 工件顺序后，最终 run `30832103333` 全部通过。run `30830424132` 与 `30831241801` 的旧 reasonWithoutEvidenceCount=1 也被保留；拆分硬 Evidence 违规与 missing UNKNOWN 后，最终两项均为 0。

## 23. Obsidian 分级结论

- Level 0：普通 Markdown + 官方 URI，默认且必须可用。
- Level 1：Advanced URI，只使用导航子集，插件缺失自动降级。
- Level 2：Local REST API/MCP，高权限 opt-in，不进入核心事实写链。
- Level 3：Dataview/Bases，可选视图，普通 Markdown 始终直接可读。

## 24. 双向跳转

ProjectFlow → Obsidian 使用 vault 标识和 managed relative path。Obsidian → ProjectFlow 使用 project ID、entity type 和 entity ID，不携带 Token 或绝对路径。真实临时 Vault 已验证稳定前端路由、官方 URI、Advanced URI 缺失降级、用户移动后的 URI 重建和远程/带凭证 URL 拒绝。

## 25. 旧 Vault 兼容

旧 Capability Note 保留。迁移只更新 ProjectFlow managed block；用户 frontmatter 和 block 外内容保留。稳定 entity metadata 优先于路径，manifest、redirect、archive 和 conflict 机制继续有效。

## 26. 当前未完成

产品实现、双真实 Provider、本地门禁、PostgreSQL 16、功能 PR 和合并后 master CI 均已完成。PR #14 只承载最终元数据，并由 required checks 保护；其合并后执行 V3.8.0 分支和 worktree 清理，不存在遗留产品实现项。

## 27. 为什么仍不进入最终 GUI

V3.8.0 的目标是先冻结事实边界、History 语义、读 API、持久化、跨消费者合同和真实验收。当前页面只验证层级与深链接；最终 GUI 需要在稳定语义和真实用户阅读反馈之后单独设计。

## 28. Tag

NO。

## 29. Release

NO。

## 30. 开发分支清理

功能分支与验收回填分支在 PR #14 合并后删除；删除结果由最终核验和最终回复记录。

## 31. master clean

远端 master 的功能合并态 ea0cc126 已在独立 clean worktree 核验。原主工作区存在用户预先已有的修改和未跟踪结果，未被覆盖、暂存、清理或混入本阶段提交，因此不把该用户工作区伪报为 clean。

## 32. 残留 worktree

两个 V3.8.0 worktree 在 PR #14 合并后删除。其他早已存在、与 V3.8.0 无关的 worktree 不在本阶段授权范围内，保持不动。

## 33. V3.9 进入条件

V3.9 只能在验收回填合并、V3.8.0 分支/worktree 清理和最终远端 master 核验完成后进入。随后应基于真实用户对 Chapter/Story/Thread 可读性、浅历史续扫和 Obsidian 工作流的反馈决定范围，不自动扩张为最终 GUI 或通用项目管理工具。
