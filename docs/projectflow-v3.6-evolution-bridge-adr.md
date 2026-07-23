# ADR：V3.6 Evolution Bridge

状态：Accepted for V3.6 minimum bridge

## 问题

当前 `ProjectUnderstandingSnapshot` 说明项目现在是什么，`ProjectFact` 说明真实发生过什么，`ProjectCapabilityEvolution` 说明长期能力版本变化，但没有一个结构化 read model 把当前代码区域、真实 commit、Fact 和 before/after revision 连起来。

## 决策

增加最小、幂等、派生的 `ProjectEvolutionBridge`：

- occurredAt
- before/after revision
- before/after structure version
- meaningful change
- affected structural area
- epistemic status 与 confidence
- source Fact IDs
- source commit refs
- structure evidence refs
- deterministic fingerprint

Bridge 不是 Fact，不写回 Timeline 或 Capability。它只引用已有事实和 Git 证据，允许随重建策略升级而替换或增加新版本。

## 生成策略

1. 只在用户主动 refresh 且结构确有变化时生成。
2. 从同项目已有 `ProjectFact` 查找真实 commit；Fact 有 affected files 时先与 dirty files 相交，没有时只允许由该 Fact 的真实 commit diff 补足。
3. 校验 Fact 的 commit 确实存在于绑定仓库。
4. 用真实 commit parent 作为 before revision、commit 作为 after revision。
5. 用 `git diff-tree --name-status` 产生有界 changed-file evidence。
6. 将 changed files 与 Structure V2 functional area 成员相交；精确区域缺失时只降级为明确标注的 manifest/filesystem 结构模块，不冒充业务功能。
7. 幂等 fingerprint 防止 retry、重复 refresh 或 cache hit 重复事件。

没有 Git、没有匹配 Fact、commit 不存在或 before parent 不可用时不编造 bridge。API 明确返回空结果或 diagnostics。

## 历史重建边界

V3.6 不逐 commit checkout、index 或调用模型。首次导入只对已有 Fact 指向的真实 commit 做有限采样。完整策略留给 V3.7：

`last analyzed revision → current revision → milestone/delta selection → incremental structure → meaningful evolution`

候选 milestone 可复用 tag、Fact density、Capability Evolution、large diff 和 release boundary；仍需硬时间、revision 和 token budget。

## 读取与所有权

`GET /api/projects/{projectId}/evolution-bridges` 只读持久化结果，不运行 Git、索引器或模型。所有访问先校验 userId/projectId 所有权。响应不返回 diff 正文、绝对路径、prompt、raw response、reasoning、Key 或 Authorization。
