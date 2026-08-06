# ProjectFlow V3.8.5 验收报告

报告状态：实现完成；本地确定性门禁与真实前后端产品 E2E 已通过，外部数据库和真实 Provider 门禁仍未闭环。

更新日期：2026-08-06

## 范围

本阶段把项目历程提升为中文优先、成果导向的渐进阅读轴，保留 Raw Event 和 Evidence 为完整来源库存，增加 Technical Atom、Primary/Supporting Change、Story、Thread、Chapter、Technical Detail 下钻、有界多窗口模型措辞、cache/checkpoint 恢复，以及可审计、可逆的 `USER_DECLARED_PRESENTATION` 用户修正。修正只覆盖展示层，不改变 ProjectFact、原始事件或 Evidence。

## 基线与版本

- 基线 master：`5cb5e49661206feb8f59885bea672c314c9374e8`
- 本地分支：`codex/v3.8.5-history-quality`
- 工作树版本：后端/前端 `3.8.5`
- 本地工作树包含未提交修改；未执行远程同步、提交、推送、PR 或合并
- No Tag、No Release

## 本地验证

| 门禁 | 实际结果 |
| --- | --- |
| Maven 全量测试（H2） | PASS，496 项，0 失败，0 错误，1 跳过（可选 benchmark） |
| V3.8.5 历程与修正测试 | PASS，Ground Truth、Correction、Window、Prompt、Reconstruction 相关类均通过 |
| Frontend production build | PASS，Next.js 16.2.11 |
| Frontend TypeScript | PASS，`npm.cmd run lint` |
| Frontend contracts | PASS，55/55 |
| Playwright 浏览器 E2E | PASS，8/8；真实前端、嵌入后端和固定模型服务 |
| Hermes MCP | PASS，9/9 |
| Obsidian projection | PASS，21/21；5,000 facts、36 months、100 capabilities、1,000 evolutions 压力样本通过 |
| `Start-ProjectFlow.bat -CheckOnly` | PASS；识别 3.8.5 和本地修改，未执行远程写入 |

关键命令和可复核输出见 `docs/acceptance-evidence/v3.8.5/evidence-index.md`。

## 外部门禁

- PostgreSQL 16 Testcontainers：BLOCKED。本机 `docker info` 无法连接 `dockerDesktopLinuxEngine`，没有用 H2 冒充 PostgreSQL 结果。
- GLM `glm-5.2`、DeepSeek 真实 Provider、calibration/holdout、非代码项目：NOT_RUN。本轮没有使用或持久化用户提供的凭据，也没有把固定模型测试描述为真实 Provider 质量结论。
- GitHub PR、CI、推送和合并：NOT_RUN。

## 安全检查

对 Git 已跟踪和工作树未跟踪的 856 个文本文件执行 UTF-8 敏感值扫描：真实 token/Bearer 命中为 0。绝对路径检测命中 2 个测试夹具，它们只用于验证脱敏行为，未进入验收报告、Agent Result、API 或模型上下文。验收产物和本轮 Agent Result 不包含 API Key、完整 Authorization、完整 Prompt、raw response、reasoning、私有项目内容或机器绝对路径。

## 结论与剩余项

V3.8.5 的实现和本地产品链路已完成，失败、取消、跳过、未处理范围、Evidence 引用和用户修正均保留可诊断状态。最终外部验收仍需在具备 Docker 和安全 Provider 凭据的隔离环境补跑 PostgreSQL、双真实 Provider、calibration/holdout 与非代码项目；这些项目完成前不把本报告标为最终质量 PASS。
