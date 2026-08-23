# ProjectFlow V3.8.5 RC 代码质量审计与修复记录

状态：本文是 RC2 代码审计历史记录。RC2 本地确定性回归和双真实 Provider 自动化门禁曾完成，但 Round 2 后续因 truthfulness P0 正式判为 `NEEDS_REVISION_NOT_APPROVED`。RC3 的真实失败、修复和 Round 3 状态以 RC3 验收报告为准；PR #15 继续 Draft，结论为 `PENDING_HUMAN_REVIEW_ROUND3 / NOT PASS`。

## 审计范围

本轮审计针对 Project History 的窗口续跑、模型输出合同、Primary/Supporting 角色图、用户修正后的 corrected read model、非代码材料泛化和 ProjectFlow 自身 Dogfood。ProjectFact、Raw Event、Evidence 和已有事实没有被展示层重建覆盖。

## 实际发现与修复

1. 验收执行器用 `stories(..., true, ...)` 读取 Supporting。该参数表示 `attentionOnly`，生产读取还会隐藏 Supporting，导致执行器拿到单向 Primary 引用并错误报告角色图失败。执行器现在读取快照经 correction overlay 后的完整 Story 集合；默认用户列表仍隐藏 Supporting。
2. fixed compatibility server 在没有原因 Evidence 时只写“仍需核对”，没有明确披露 UNKNOWN。Prompt v4 合同测试因此首次失败 1 项；fixture 改为明确写出“变化原因未知”，合同随后通过。
3. 展示不变量此前分散在重建与 correction 服务。新增 `ProjectHistoryPresentationInvariantValidator`，统一角色合法性、环、孤立 Supporting、双向关系、唯一归属和 corrected view 的 Chapter/Thread 覆盖与计数校验。已合并归档 Story 不参与活动角色图，但其原始 ID、事件和 Evidence 仍保留。
4. ProjectFlow Dogfood 曾因读取口径和旧模型职责边界失败；固定兼容工件 11/11 只证明执行器。workflow `31318477841` 的 GLM 与 DeepSeek Flash 最终 Dogfood 均 PASS；DeepSeek attempt 1 的失败发生在 17-window 而非 Dogfood，相同 head 的失败 job 重跑后 11/11。没有用 ProjectFlow 专属规则掩盖输出。
5. RC2 继续追踪后确认模型旧 schema 本身仍允许窗口级输出改写 role、primaryStoryId、supportingChangeRefs 和 Chapter storyRefs。该职责已从模型合同删除，`ProjectHistoryModelOutputContract` 只允许措辞字段，工程层完整角色图由 337 Story 回归固化。
6. PostgreSQL profile 首次重跑暴露取消测试依赖并发完成顺序；实际仍为一个 SUCCEEDED、一个 CANCELLED。测试改为核对状态集合、保留成功 checkpoint ID，并证明 retry 只执行取消与未启动窗口，随后 PostgreSQL profile 通过。
7. DeepSeek max reasoning 在 run `31303975027` 的 small-script 出现两次 reasoning 存在、可见 content 为空。统一 Prompt 现在要求结束 reasoning 后输出完整可见 JSON；只允许一次同输入恢复，第二次仍空即 `REASONING_EXHAUSTED_OUTPUT`，不得第三次调用。当前 run Understanding 17/17。
8. 上一轮 GLM 单 job 用时 5 小时 45 分，接近 GitHub-hosted 6 小时限制。workflow 只把相同真实门禁拆成 qualification 与 scenarios 两个依赖 job，没有减少任何测试或改变 Provider 配置。

## 复现与验证

- `ProjectHistoryV385RealScenarioEvaluatorTest`：最新固定兼容模型工件 11/11 PASS，60 个物理请求，12,000 个固定 usage token；覆盖 5 类非代码、17 窗口、服务实例重启、全局 cache、Chapter 二阶段、局部 correction 失效、schema failure、取消恢复和 prompt overflow split。
- `ProjectHistoryRealModelIT`：固定兼容 Prompt v4 合同 PASS。首次失败已保留在本地 Surefire 记录，原因是 UNKNOWN 文案不满足合同，不是生产解析器放宽门禁。
- `ProjectHistoryReconstructionTest`：包含最小模型合同、reasoning-only 恢复和并发取消确定性回归，随 H2 全量 557 项 PASS。
- `ProjectHistoryCorrectionServiceTest`：修正后 14/14 PASS；首次抽取时 merge 归档 Story 被过严纳入活动角色图，已按原 corrected view 语义修复。
- `HumanReviewSampleManifestTest`：真实 30 Story / 8 Chapter 清单存在后实际 PASS，校验 Run URL、相对工件路径、双 Provider、分层覆盖和安全标志。
- workflow `31318477841`：GLM 与 DeepSeek Flash 的 V3.8.0、V3.7.5 38-run、Understanding 17/17、V3.8.5 qualification 与最终 scenarios 11/11。DeepSeek attempt 1 9/11 作为失败证据保留。
- 后端、PostgreSQL、前端生产构建、Playwright、Hermes 和 Obsidian 的最终计数以 `evidence-index.md` 和最终验收报告为准。

## 未完成与限制

- 历史 qualification FAIL、旧 DeepSeek 10/11、run `31294942095`、run `31303975027` 和当前 attempt 1 失败都不能被最终成功覆盖；报告必须同时保留。
- 30 Story / 8 Chapter 已冻结，但真实人工评分尚未完成，可读性平均分为 NOT_RUN。
- PR #15 必须保持 Draft；人工门禁和所需报告完成前不合并、不创建 Tag/Release、不清理分支。
