# ProjectFlow V3.8.0 验收报告

报告状态：本地门禁通过，外部门禁待完成

更新日期：2026-08-03

## 1. 最终版本

目标版本为 3.8.0。Backend、Frontend 和文档版本已更新，但功能尚未合并 master，因此当前不是最终发布状态。

## 2. 基线 master SHA

fd5ce827245f4fc4a20ecda15c63fc03313505ab。

## 3. 功能 PR

尚未创建。

## 4. 功能 merge SHA

尚未产生。

## 5. 验收回填 PR

尚未创建；是否需要取决于功能 PR 合并后是否要回填 PR、merge SHA 和 CI run ID。

## 6. 最终 master SHA

尚未产生。

## 7. GitHub Actions

V3.8.0 required CI 尚未运行。不得用本地 H2 结果代替 PostgreSQL 16 required check。

## 8. PostgreSQL 16

本机 Docker Desktop 守护进程未运行，因此没有伪造本地 Testcontainers 结果。PostgreSQL 16 必须由本轮 GitHub Actions 的 required `postgres-integration` 验证。

## 9. 当前测试结果

- Project History 聚焦回归：26/26 通过。
- 三公开仓库有界验证：1/1 通过，覆盖三个固定公开项目。
- DeepSeek 项目历程真实模型合同：1/1 通过。
- Backend H2 full suite：483 tests，0 failure，0 error，1 skipped；跳过项是显式 opt-in 的真实开源仓库 benchmark，不是功能失败。
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

DeepSeek deepseek-v4-pro 通过固定 Prompt v2 合同：1 次请求、1,539 Token、13,510 ms，所有结构、安全和 Evidence 违规为 0。第二真实 Provider 当前未配置，因此跨 Provider 硬门禁尚未满足。

## 22. 关键安全计数

当前正式产物中 Unsupported Claim、Invalid Evidence、Cross-project reference、Reason without Evidence 均为 0，eventConservation=true。提交内容密钥模式扫描与验收产物路径、敏感字段、长度和 SHA-256 校验均通过。

安全冻结清单为 `docs/acceptance-evidence/v3.8.0/acceptance-freeze-manifest.json`，当前 SHA-256 为 `4A37B6F528CF1DB35A55A558CF9FD6A99560B5C1E3CDCC31E11C3488AA6F1652`。清单冻结六个正式产物，同时明确第二 Provider、PostgreSQL required CI、GitHub checks 和 merge 仍未完成。

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

第二真实 Provider、PostgreSQL 16 required CI、GitHub PR/CI、可能的验收回填、最终 master 核验、分支与 worktree 清理。本地确定性、前后端、Playwright、Hermes、Obsidian、安全和根启动器门禁已完成。

## 27. 为什么仍不进入最终 GUI

V3.8.0 的目标是先冻结事实边界、History 语义、读 API、持久化、跨消费者合同和真实验收。当前页面只验证层级与深链接；最终 GUI 需要在稳定语义和真实用户阅读反馈之后单独设计。

## 28. Tag

NO。

## 29. Release

NO。

## 30. 开发分支清理

尚未执行；功能未合并前不能删除当前开发分支。

## 31. master clean

尚未进入最终核验阶段。不会覆盖主工作区中用户已有修改。

## 32. 残留 worktree

当前 V3.8.0 独立 worktree 仍存在，待合并和回填完成后删除。

## 33. V3.9 进入条件

V3.8.0 必须先完成全部本地与 required CI 门禁、真实跨 Provider 资格或经用户明确调整该门禁、功能与回填合并、最终 acceptance freeze、master clean、分支和 worktree 清理。之后才可基于真实用户对 Chapter/Story/Thread 可读性、浅历史续扫和 Obsidian 工作流的反馈决定 V3.9 范围。
