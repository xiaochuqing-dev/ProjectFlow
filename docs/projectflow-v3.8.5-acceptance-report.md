# ProjectFlow V3.8.5 验收报告

报告状态：BLOCKED / PENDING_HUMAN_REVIEW。代码、确定性 CI 和双真实 Provider 自动化门禁已通过；30 Story / 8 Chapter 已冻结但尚未由真实人工评分，因此 PR #15 保持 Draft，不能合并 master。

更新日期：2026-08-10

## 范围与边界

V3.8.5 将 Project History 作为通用阅读轴，保留 Raw Event 与 Evidence 完整来源库存，增加 Technical Atom、Primary/Supporting Change、Story、Thread、Chapter、工程详情下钻、有界多窗口措辞、cache/checkpoint 和可审计可逆的 USER_DECLARED_PRESENTATION 展示修正。修正不改写 ProjectFact、原始事件或 Evidence。

RC2 将 role、primaryStoryId、supportingChangeRefs、Chapter storyRefs、before/change/after、冲突、生命周期和 Evidence 归属收回工程层；模型只返回 Story/Chapter 措辞与有 Evidence 的 reason。Frontend、Gateway、Agent、Hermes 与 Obsidian 读取同一个 corrected view 和 presentationRevision。

## GitHub 状态

- 基线 master：`5cb5e49661206feb8f59885bea672c314c9374e8`
- 自动化验收代码 head：`74ba013615932748b4a41077baf8f89af618a5d2`
- PR #15：Open、Draft，目标 `master`，尚未合并
- head required CI：push run `31317712835`、PR run `31317716057`，全部通过
- 真实 workflow：[`31318477841`](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/31318477841)，attempt 2 success
- No merge、No Tag、No Release、No acceptance backfill、No branch/worktree cleanup

## 确定性与产品门禁

| 门禁 | 实际结果 |
| --- | --- |
| Maven 全量测试（H2） | PASS，557 项，0 失败，0 错误，6 个条件跳过 |
| HumanReviewSampleManifestTest | PASS；真实 30/8 清单存在且安全字段通过 |
| 根启动脚本 | PASS；Next.js 16.2.11 生产构建、Java 17 后端及 3000/8080 就绪 |
| Frontend contracts / Playwright | PASS，58/58 与 9/9 |
| Hermes / Obsidian | PASS，10/10 与 25/25 |
| PostgreSQL 16 Testcontainers | GitHub required CI PASS；本机 Docker 不可用，未用 H2 冒充 |
| 敏感内容与归一化工件 | PASS；12 份正式模型文件中密钥样式与机器绝对路径均为 0 |
| npm audit | 4 high、0 critical；未执行自动依赖修复 |

## 最终真实 Provider 资格

正式配置：GLM `glm-5.2`、Ark Coding base URL、`OPENAI_RESPONSES`、high；DeepSeek `deepseek-v4-flash`、OpenCode Go `/v1` base URL、`OPENAI_CHAT_COMPLETIONS`、max。API Key 只由 Repository Secrets 注入，仓库和报告不记录值。

| 门禁 | GLM | DeepSeek Flash |
| --- | --- | --- |
| V3.8.0 协议/schema | PASS；1 请求，5,131 token | PASS；1 请求，3,846 token |
| V3.7.5 38-run | 38/38；52 请求，521,726 token | 38/38；64 请求，663,829 token |
| Understanding E2E | 17/17 | 17/17 |
| V3.8.5 19-case | qualified；20 请求，97,269 token | qualified；21 请求，121,540 token |
| 模型降级 / 失败窗口 / 拒绝输出 / 修复失败 | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 |
| 自动可读性指标 | 5.0 | 5.0 |
| 安全持久化 | Key/Prompt/raw/reasoning/绝对路径全 false | Key/Prompt/raw/reasoning/绝对路径全 false |

## 真实场景、Dogfood 与非代码

GLM 最终为 11/11，68 个物理请求、871,777 token、模型耗时 5,266,928 ms、4 次统一 validation repair。DeepSeek attempt 2 最终为 11/11，70 个物理请求、962,976 token、模型耗时 2,158,891 ms、2 次统一 validation repair。两者 ProjectFlow Dogfood、演示材料、研究报告、数据分析、品牌页、无 Git 版本、17-window continuation/restart/cache、correction、schema failure、取消恢复和 Prompt overflow 均通过。

DeepSeek attempt 1 为 9/11：17-window 首轮 15 succeeded、1 failed、1 pending，correction 因前置 continuation fixture 不可用连带失败；Dogfood 与五类非代码仍通过。该失败没有被删除；相同 head、相同 Flash/max 配置只重跑失败 job 后通过。未增加 Provider 分支、第三次语义请求或 Evidence/Strong Fact 放宽。

## 历史失败链

- 初始工件：GLM 与当时的 DeepSeek V4 Pro 19-case qualification 均 FAIL；旧 DeepSeek 场景 10/11，Dogfood 角色引用不一致；GLM 场景 NOT_RUN。V4 Pro 只作为历史失败事实保留，不是当前配置。
- run `31264440534`：两项 Repository Secrets 尚未配置，在真实请求前失败；没有请求或计费。
- run `31294942095`：GLM qualification FAIL、场景 8/11；DeepSeek Flash qualification 和场景通过，但 Understanding 16/17（stale-readme）。
- run `31303975027`：GLM 自动门禁通过；DeepSeek qualification/场景通过，但 Understanding 16/17（small-script 两次只有 reasoning、可见 content 为空）。
- run `31318477841` attempt 1：当前代码下 DeepSeek qualification 通过，场景 9/11；attempt 2 只重跑失败 job 后 11/11。

## 人工可读性

最终工件已按固定分层规则冻结 30 Story / 8 Chapter，GLM 与 DeepSeek 各 15/4。清单状态为 `PENDING_HUMAN_REVIEW`，reviewerCount=0，所有评分为空，modelSelfScoring=false。人工门槛为平均分 4.0，且 Invalid Evidence、跨项目引用、Raw Event 丢失、孤立 Supporting 或无 Evidence 强原因不得出现。

自动 evaluator 的 5.0 只说明冻结 Ground Truth 维度，不替代真实人工阅读。当前人工结果仍是 0/30 Story、0/8 Chapter、平均分 NOT_RUN。

## 安全与最终结论

正式证据仅保存归一化输出，不保存完整 Prompt、raw response、reasoning、Key、Authorization、私有项目内容或机器绝对路径。首次失败、低质量候选和 single-reviewer limitation 均不得删除。

V3.8.5 自动化质量门禁已通过，但人工可读性尚未通过，因此最终状态仍是 BLOCKED。完成真实人工评分并达到门槛后，才可退出 Draft、合并 PR #15、验证 master CI、完成 acceptance backfill 和安全清理。
