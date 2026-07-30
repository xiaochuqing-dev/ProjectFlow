# ProjectFlow V3.7.4 Current-state Audit

审计日期：2026-07-29

基线：GitHub 默认分支为 master，origin/master 为 5c7fb75cae5f23d933d352634a6c97a0c2f6aece，最近合并 PR 为 #8，仓库版本为 V3.7.3。原工作区落后 16 个提交且存在 3 个已修改文件和 1 组未跟踪 Agent result；V3.7.4 在独立 worktree 中从最新 master 开始，未改动这些用户工作。

## 已有可复用能力

- Repository Intake 已有文件数、语言、manifest、Git、敏感路径、generated/vendor/binary 过滤和有界 I/O 诊断。
- Evidence Discovery 已有来源类别、模块多样性、重复压缩、Evidence ID、短样本缓存和 Source Map。
- Semantic Scout、Planner、Capability Registry、Provider、High-value Gate 与 Final Synthesis 已形成 0/1/2 模型调用边界。
- ProjectFact、Timeline、Capability Map、Project Memory Gateway、Hermes 和 Obsidian 已有持久化、所有权和只读消费链。
- Model Gateway 已支持 OPENAI_RESPONSES、OPENAI_CHAT_COMPLETIONS 和 ANTHROPIC_MESSAGES，并有取消、超时、重试、usage、结构化恢复和脱敏边界。

## 已确认缺口

1. DOC_READER 只从文件开头读取；Evidence Discovery 的正文样本也只读取 head，无法证明 8 万行文件中部和尾部事实。
2. 当前没有文件级 Content Map、带行号/字节范围/哈希的 HEAD、MIDDLE、TAIL 采样，也没有未读范围披露。
3. ProjectFact 只有 RECORDED/NEEDS_ATTENTION，没有正式的 OBSERVED、VERIFIED、DECLARED、INFERRED、CONFLICTED、UNKNOWN、PROCESS_EVIDENCE 契约。
4. 现有事实接纳只校验 Evidence 前缀、Agent-only 和质量状态；没有针对历史原因、废弃、技术债和推断升级的专门 Guard。
5. Hermes 可列项目并读取单项目事实、Timeline、Capability 和 brief，但不能跨项目搜索，也不能读取 Evidence、Unknown、Conflict 或版本化 Context Package。
6. Agent 没有正式候选断言写入边界；现有只读 Gateway 不能表达“可提交候选但不能直接写强事实”。
7. list_projects 返回基本项目元数据，不包含最新理解 revision、历史范围、Evidence coverage 和 Unknown/Conflict 计数。
8. GitHub 只读 Evidence 仍是已注册但不可用的未来 Adapter；本阶段不扩张为 GitHub Manager。

## 风险与保留边界

- 不重写 ProjectFact、Timeline、Capability、Evolution 或旧事实；新增字段必须兼容旧库，默认值保守。
- 不把 Agent Result、模型共识、README 或文件名升级为强事实。
- 不新增 parser 平台、SCIP producer、Lucene、向量数据库、通用 RAG、workflow、watcher、GUI、Tag 或 Release。
- 大文件完整正文、绝对路径、prompt、raw response、reasoning 和凭据不得持久化。
- GET understanding/structure/evolution 和 Project Memory reads 继续只读，不触发扫描或模型。

