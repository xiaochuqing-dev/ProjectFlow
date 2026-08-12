# V3.8.5 Human Readability Round 2 Review

Status: NEEDS_REVISION_NOT_APPROVED. Round 2 remains frozen as failed acceptance evidence and must not be reused as an approval package.

The frozen set is `docs/acceptance-evidence/v3.8.5/human-review-round2-manifest.json` and `human-review-round2-worksheet.md`. It contains exactly 30 Story and 8 Chapter: GLM and DeepSeek each contribute 15 Story and 4 Chapter. Every human yes/no field, score, note and PASS/FAIL result is blank; reviewerCount remains 0 and modelSelfScoring is false.

Source mapping:

- GLM qualification/full scenarios: run `31523413972`.
- DeepSeek Flash qualification/full scenarios: run `31517037532`.
- Both affected correction samples: run `31532558352`.

Round 2 raw working-file SHA-256 values are manifest `e1aca397b469c4d1e4e4b4f6bb856306b2b3340bcb5df97e80d71a286a247349` and worksheet `8e9c04bde787b6bb6c2528f96e5d296dcf66186f66290298cf18ca21f68d73e7`. Canonical-LF values are manifest `b2841c74491d172919db4a37e723d6533ad99f77799e0191fd5d2a7bdb90e887` and worksheet `44655c49ef0d21c58e7aef7df4e1295dba6e48a5ddff3039f4d42edb96824692`.

Round 1 remains immutable and formally NEEDS_REVISION_NOT_APPROVED. Its canonical-LF hashes remain manifest `524391f2137a7b72d2920efbefaee1190177bbeb588594ef47fa9099d92554d9` and worksheet `dbe05a47548ba72cd2c379c05b987e5915e68c32bb935e5bbd3fcf173c420408`.

Automated checks found no indexed placeholder, credential pattern or private absolute path in the Round 2 worksheet. They do not replace human judgment. Round 2 nevertheless has a P0 truthfulness failure: the ProjectFlow skeleton commit `ae9fba1e...` was presented as “编写登录流程代码并形成实现”，并声称此前没有登录实现、本阶段加入了实现登录所需代码。其登录直接证据只有背景图；README、环境配置和同提交的其他代码/配置不能证明登录流程实现。该错误足以让 Round 2 直接判为 NEEDS_REVISION_NOT_APPROVED，不得用平均可读性分数抵消。

同一冻结包中还保留了需要关注的空泛 Chapter，例如“围绕项目基础建设推进阶段成果”以及“相关成果逐步形成并得到完善”。这些原始失败内容不做手工修文，RC3 通过新合同和 Round 3 新样本重新验收。

PR #15 must remain Draft. Only the user’s explicit review decision can permit readiness, merge, acceptance backfill or branch cleanup.
