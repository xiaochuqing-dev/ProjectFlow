# ProjectFlow V3.8.5 验收报告

报告状态：PENDING_HUMAN_REVIEW / NOT PASS。更新日期：2026-08-12。

V3.8.5 RC2 保持 ProjectFact 为唯一强事实来源。工程层拥有 Raw Event、Technical Atom、Primary/Supporting、Chapter membership、Before/Change/After、Evidence 和纠正覆盖；模型只改写有界措辞与有 Evidence 的原因。Frontend、Gateway、Agent、Hermes 与 Obsidian 读取相同 corrected view。

自动化证据：

- 本地 backend/H2：579 项，0 失败，0 错误，5 个条件跳过。
- 真实 GLM 完整基线 run `31523413972`：19-case qualified，11/11 scenarios，ProjectFlow Dogfood 与五类非代码通过。
- 真实 DeepSeek Flash 完整基线 run `31517037532`：19-case qualified，11/11 scenarios，ProjectFlow Dogfood 与五类非代码通过。
- 首次 Round 2 候选暴露编号占位符后被拒绝。code head `aee0160` 的受影响 run `31532558352` 中，两家均 1/1 PASS、3 次真实 Story 请求、64 Story、2 窗口、只失效纠正目标窗口、最终 cache hit、泄漏 0、repair 0。
- run `31532558352` 的 frontend、Playwright、sensitive-content、Hermes 与 Obsidian 通过；该 run 的 backend/PostgreSQL 因当时尚无最终 Round 2 文件而失败，因此不作为最终静态 CI 权威，evidence commit 以自身 PR checks 为准。

Round 2 已冻结为 30 Story/8 Chapter，GLM 与 DeepSeek 各 15/4。canonical-LF manifest SHA-256 为 `b2841c74491d172919db4a37e723d6533ad99f77799e0191fd5d2a7bdb90e887`，worksheet SHA-256 为 `44655c49ef0d21c58e7aef7df4e1295dba6e48a5ddff3039f4d42edb96824692`。reviewerCount=0，所有人工字段为空，modelSelfScoring=false。

Round 1 仍为 NEEDS_REVISION_NOT_APPROVED，原文件和哈希保持不变。run `31468663795` 的 DeepSeek 9/11、run `31517037532` 的较早 GLM 资格失败及其他历史失败继续保留；后续成功不覆盖首次事实。

安全证据只包含规范化输出，不包含完整 Prompt、raw response、reasoning、Key、Authorization、私有绝对路径或私有项目内容。当前没有 Provider 专属业务特判，也没有降低 Evidence 或 Strong Fact 门禁。

最终阻断是用户对 Round 2 的真实人工审核，以及 evidence commit 的全部 GitHub required checks。此前 PR #15 保持 Draft，不合并、不 backfill、不创建 Tag/Release、不删除分支或 worktree。
