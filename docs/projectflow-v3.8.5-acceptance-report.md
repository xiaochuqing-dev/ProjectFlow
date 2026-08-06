# ProjectFlow V3.8.5 验收报告

报告状态：BLOCKED。代码实现、确定性门禁、产品链路和 required CI 已通过；真实 Provider 资格未通过，因此 PR #15 必须保持 Draft，不能合并 master。

更新日期：2026-08-07

## 范围

V3.8.5 将 Project History 作为通用阅读轴，保留 Raw Event 与 Evidence 完整来源库存，增加 Technical Atom、Primary/Supporting Change、Story、Thread、Chapter、工程详情下钻、有界多窗口措辞、cache/checkpoint 和可审计可逆的 USER_DECLARED_PRESENTATION 展示修正。修正不改写 ProjectFact、原始事件或 Evidence。

## GitHub 状态

- 基线 master：`5cb5e49661206feb8f59885bea672c314c9374e8`
- PR #15：Open、Draft，目标 `master`，尚未合并
- 本轮真实资格执行基准 head：`52e990496ffad6cb63213722fa1c410220324b83`
- 当前 PR head、required CI、merge SHA、Tag 和 Release 必须以本轮报告提交后的 GitHub 状态复核；本报告不把未来提交写成已完成
- No merge、No Tag、No Release、No branch/worktree cleanup

## 本轮审计与修复

本轮复现并修复了窗口续跑/缓存完整性、Prompt 超限拆分、局部窗口失败隔离、Primary/Supporting 角色图、修正冲突审计事务、Split/Merge 展示不变量和中文优先 fallback 的问题。首次失败与修复后的固定兼容结果记录在 `docs/projectflow-v3.8.5-rc-code-audit-and-fix-report.md`；固定执行器只证明确定性流程，不证明真实模型质量。

## 确定性与产品门禁

| 门禁 | 实际结果 |
| --- | --- |
| Maven 全量测试（H2） | PASS，496 项，0 失败，0 错误，1 跳过（可选 benchmark） |
| V3.8.5 历程、Ground Truth、修正、Window、Prompt、重建测试 | PASS；固定生产输出比较与安全不变量通过 |
| Frontend production build / lint | PASS；Next.js 16.2.11 |
| Frontend contracts | PASS，55/55 |
| Playwright 浏览器 E2E | PASS，8/8；真实前端、嵌入后端和固定兼容模型服务 |
| Hermes MCP | PASS，9/9 |
| Obsidian projection | PASS，21/21；含大投影压力样本 |
| `Start-ProjectFlow.bat -CheckOnly` | PASS；识别版本 3.8.5 和本地修改 |
| GitHub required CI | PASS；push run `31069320457`、PR run `31069362971` |
| PostgreSQL 16 Testcontainers | PASS；两轮 GitHub `postgres-integration` 独立通过 |
| 本机 Docker/PostgreSQL 复核 | BLOCKED；Docker Desktop Linux engine 不可用，未用 H2 冒充 PostgreSQL |

## 真实 Provider 合同与资格

合同测试只验证协议、解析和安全边界，不能替代资格测试：

- GLM：`glm-5.2`、`OPENAI_RESPONSES`，1 请求，4,850 token，41,659 ms，schema/security 合同 PASS。
- DeepSeek：`deepseek-v4-pro`、`OPENAI_CHAT_COMPLETIONS`，1 请求，4,271 token，81,987 ms，schema/security 合同 PASS。

冻结 19-case 资格结果均为 FAIL：

| Provider | 请求 / token / 耗时 | 降级窗口 | 失败或未处理窗口 | UNSUPPORTED_CLAIM 拒绝 | 资格 |
| --- | ---: | ---: | ---: | ---: | --- |
| GLM `glm-5.2` | 20 / 103,268 / 616,966 ms | 16 | 24 | 12 | FAIL |
| DeepSeek `deepseek-v4-pro` | 20 / 79,702 / 1,002,070 ms | 14 | 24 | 12 | FAIL |

两份资格工件的聚合安全指标均为零违规（Invalid Evidence、跨项目引用、Raw Event 丢失、孤立 Supporting、无 Evidence 原因、绝对路径/凭据泄漏等），但窗口失败、降级和模型主张拒绝仍使 Provider qualification 失败，不能用安全指标掩盖质量失败。

DeepSeek V3.8.5 真实场景工件共 11 个场景，10 个通过、1 个失败；83 个物理请求、1,079,860 token、模型耗时 5,512,516 ms。五类非代码、17 窗口 continuation/cache/restart、schema failure、取消恢复、Prompt overflow 和 correction 均通过；`projectflow-current-history-dogfood` 因“Primary and supporting history references are inconsistent”失败。GLM 真实场景未执行。旧版 `ProjectFlowRealModelEvalIT` 和 `ProjectUnderstandingRealModelIT` 本轮未执行。

## 非代码与人工可读性

DeepSeek 真实场景中的演示材料、研究报告、数据分析、品牌页和无 Git 版本材料为 5/5 PASS，但这只是场景执行与结构安全证据；GLM 对应场景未运行，且没有独立人工评分。固定兼容输出仍保留“整理……形成可阅读结果”等低质量候选，不能当作自然语言通过。

人工抽样：0 个 Story、0 个 Chapter、平均分 NOT_RUN。尚未完成至少 30 个 Story 与 8 个 Chapter 的独立人工复核，人工门禁为 BLOCKED。

## 安全与证据边界

本轮不持久化用户凭据、Authorization、完整 Prompt、raw response、reasoning、私有项目内容或机器绝对路径。当前扫描覆盖 871 个文本文件，token-like/Bearer 命中均为 0；7 个绝对路径匹配分布在 4 个脱敏/敏感内容测试夹具中，不是验收产物或模型上下文泄漏。`git diff --check` 退出码为 0。

## 最终结论

V3.8.5 当前是可交付候选，不是最终质量通过版本。双真实 Provider 资格失败、GLM 真实场景未运行、旧真实评测入口未运行、人工可读性抽样未运行，因此保持 BLOCKED：PR #15 保持 Draft，不退出 Draft，不合并 master，不创建 Tag/Release，不删除分支或 worktree。
