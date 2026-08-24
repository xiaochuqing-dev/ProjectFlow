# ProjectFlow V3.9 产品验收

当前结论：`TECHNICAL_ACCEPTANCE_PASS / HUMAN_REVIEW_REQUIRED`。V3.9 不是 Final PASS，PR #17 保持 Draft；不得合并、进入 V3.10、创建 Tag 或 Release。

## 已证明的产品行为

- 无变化 refresh 为 0 模型请求，并保持 Story、Thread、Chapter、Current Project State、Context Package 与 Obsidian 语义修订稳定。
- 小 delta 形成有界 Continuity Delta，只续接或重算受影响范围；未受影响 Story、Thread 与历史 Chapter 工程身份保持稳定。
- rename、delete/restore 与 rewrite 保留 Raw Event 账本；无法证明的连续关系保持 UNKNOWN、new Story、attention 或 Correction conflict。
- Correction 只有在旧成员完整且仍属于同一目标时做安全 additive replay，不能静默丢失或错误重绑。
- Current Project State 从持久化 corrected history 派生，GET/Gateway/Hermes/Obsidian 均不扫描、不调用模型，也不产生 ProjectFact。
- Agent Context Package v2 纳入 Current State revision；相关变化更新 package revision，无变化保持稳定。
- Obsidian 只更新受影响 managed notes，保留用户内容，后续 no-op 为 0 write。
- T0–T7 ProjectFlow 连续 Dogfood 已覆盖 no-op、连续主题、独立主题、Correction、后续变化、rename/rewrite 与 HTTP 503 resume。

## 工程证据

| 门禁 | 结果 |
| --- | --- |
| 生产/eval Head | `eb38c78fe70d3cf9280e716f7fc906d8729b15b1` |
| 冻结 Ground Truth | 15 Calibration + 15 Holdout，30/30 均绑定真实可执行测试 |
| Backend/H2 | 648 项，0 failure、0 error、7 conditional skip |
| PostgreSQL 16 | 本机 Testcontainers 5/5；同头 GitHub PostgreSQL job PASS |
| Frontend | lint、58/58 contracts、Next.js production build PASS |
| Playwright | 9/9 PASS |
| Hermes | 10/10 PASS，21 个只读工具 |
| Obsidian | 26/26 PASS；无需安装 Obsidian 桌面应用 |
| 根启动器 | V3.9.0 production build、H2/backend/frontend 双端健康、正常退出与端口释放 PASS |
| 普通 CI | push `32666198144`、PR `32666201528` 均 SUCCESS |
| 三 Provider | run `32666372066`：qualification 57/57、Chapter 27/27、continuity 9/9 |
| 敏感内容 | Repository 扫描与九个 Provider 工件安全校验 PASS |
| 真人 Continuity review | `NOT_REVIEWED`，12 个场景字段保持空白 |

## 保留边界

Run `32659635453` 的 Qwen Chapter 8/9 失败已作为独立证据保留，不被后续成功覆盖。真人 Continuity review、Draft→Ready、PR 合并、master CI、acceptance backfill 与分支清理仍未执行。因此不得宣布 Project Continuity、Incremental History Maintenance、Agent Context Continuity 或 V3.9 Final Acceptance 为 Final PASS，也不得批准 V3.10 ENTRY。
