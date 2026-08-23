# ProjectFlow V3.8.5 RC2 当前状态审计

审计更新日期：2026-08-12。对象为 Draft PR #15，当前真实复验代码 head 为 `aee0160cf1d4cf11224055548107098fd12e6de1`。

接管时和中途失败事实继续保留：最初双 Provider qualification FAIL、旧 DeepSeek 10/11 与 Dogfood 失败、Secrets 缺失 run、run `31468663795` 的 DeepSeek 9/11、run `31517037532` 的较早 GLM 资格失败，以及 Round 1 NEEDS_REVISION_NOT_APPROVED。

当前实现保持 Provider-neutral。工程层唯一维护角色、Chapter membership、Before/Change/After 语义、生命周期、冲突与 Evidence；模型只负责最小措辞和有 Evidence 的 reason。中文编号占位符在第一层归一化并由 entailment/evaluator 拒绝，内部稳定 subject 继续用于窗口规划。

确定性结果：本地 backend/H2 579 项通过，0 失败，0 错误，5 个条件跳过。Round 1 canonical hashes 保持不变；Round 2 manifest 合同实际通过。run `31532558352` 的 frontend、Playwright、sensitive-content、Hermes 与 Obsidian 通过。

真实结果：GLM run `31523413972` 与 DeepSeek Flash run `31517037532` 的资格、11/11 场景、Dogfood 和五类非代码通过。受影响 run `31532558352` 中两家均 1/1 PASS，64 Story、2 窗口、单窗口纠正、cache hit、泄漏 0。该 run 的 backend/PostgreSQL 失败只说明当时 head 尚无最终 Round 2 文件；最终静态结论以 evidence commit 自身 PR checks 为准。

Round 2 已冻结 30 Story/8 Chapter，双 Provider 各 15/4，人工字段全空。当前产品门禁仍是 PENDING_HUMAN_REVIEW；PR 不合并、不 backfill、不 Tag/Release、不清理分支或 worktree。既有 npm audit 4 high、0 critical 未在 RC2 中静默处理。
