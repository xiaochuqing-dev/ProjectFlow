# ProjectFlow V3.9 连续 Dogfood 报告

当前状态：DETERMINISTIC PASS；真实三 Provider 结果单独验收；真人 Gate 尚未执行。

执行证据：`docs/acceptance-evidence/v3.9/dogfood-sequence.json`

## 固定来源

T0 使用 V3.8.5 final master `ab29b1ff0f842c029b5cf121bd584bd40fcf74b2`，T1 使用 V3.9 合同冻结提交 `3ba06c26a977e70a6fa276ae5108a98b7e8638ad`，T2 使用 V3.9 核心实现提交 `cc1970370865094caf02a7bb0e621c1a8055af2b`。T3–T7 在隔离临时 Git 仓库中按固定时间创建可审计 continuation、correction、rename/rewrite 和 Provider failure 输入，不修改工作仓库。

## T0–T7 结果

| 步骤 | 输入与结果 | 关键证据 |
| --- | --- | --- |
| T0 | V3.8.5 快照后 no-change | delta 0、Model request 0、9 个 Chapter 全部复用，State 与 Context revision 均稳定 |
| T1 | V3.9 冻结文档与 Agent Result | 11 个新增事件、1 个 Agent Result ref、217 个 Story 复用，State/Context 同步更新 |
| T2 | 同一连续性主题加入核心实现 | delta 59、217 个 Story 复用、7 个 Thread 续接、3 个 Chapter 复用 |
| T3 | 独立导出审计主题 | 新建 1 个 Story 与 1 个 Thread，没有强制并入旧主题 |
| T4 | 用户重命名修正 | source delta 0，Correction 保持 ACTIVE，仅 presentation revision 更新，事实历史不变 |
| T5 | 同一主题继续变化 | 保持同一 Story ID，成员只追加一次，Correction 安全 replay 且无静默丢失 |
| T6 | rename 后 history rewrite | 2 个旧事件 INVALIDATED，Raw Event ledger 不下降，无静默错误重绑 |
| T7 | 第二个 Story window 注入 HTTP 503 | 首轮 2 个成功、1 个失败；一次 resume 后 3/3 成功且 2 个成功窗口复用，最终 no-change 为 0 请求/cache hit |

## 安全与边界

8 个步骤均满足 event conservation，Raw Event ledger 从 3569 单调增长到 3718。记录中的 Invalid Evidence、跨项目引用、Unsupported Strong Fact、Correction 静默丢失和错误目标重绑均为 0。工件不保存 API Key、Prompt、raw response、reasoning 或机器绝对路径。

本序列使用真实 ProjectFlow Git 历史和生产重建链，但 Provider failure 为内存内确定性故障注入；它不能代替三 Provider 的真实网络验收。Obsidian mutation class 由同一 continuity diagnostics 推导，并由独立 Python projector 测试验证真实零写入和受影响块更新。

## 发现并关闭的问题

首次执行暴露两项连续性缺陷：保留旧 Primary 时会遗留过期 `supportingChangeRefs`，以及同源失败重试会丢失首次 affectedFrom 并改变窗口 identity。实现现会在合并后规范化双向角色图，并在存在未完成 checkpoint 时恢复上次 reconstruction affectedFrom。修复后的完整 T0–T7 序列通过。
