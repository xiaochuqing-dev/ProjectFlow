# V3.8.5 真实项目与确定性验证

## RC3 当前验证

- ProjectFlow 当前历史 run `31574016609` 在两家 Provider 上都复现同一确定性 P0：ae9f 的宽泛 `project-area-frontend` Story 被提升为 IMPLEMENTED。两家均为 10/11 scenarios，证明问题不依赖模型措辞。
- head `92053e58` 给宽泛区域主体设置 OBSERVED 上限，并保留精确主体的 IMPLEMENTED 能力。119 项 affected ProjectHistory/Provider-neutral 回归为 0 失败/错误、4 条件跳过；全部 19 个冻结 Ground Truth case 通过。
- 根启动器从 `539dfc9` 当前工作树完成 Next 16.2.11 生产构建、Spring Boot/H2 启动和双端就绪验证，Build ID 为 `XXi5IxRJDs40sepsAtxeK`；正常退出后 3000/8080 无监听残留，证据写入 `logs/last-embedded-build.json`。
- 当前生产 head `539dfc9` 新增 Story v12 结果门禁、确定性标题/摘要保留和公开回退计数。run `31586433372` 的双 Provider qualification 与 DeepSeek 11/11 scenarios 已通过；GLM scenarios attempt 1 为 1/11、隔离重跑为 0/11，且安全工件未保留 HTTP 状态。完成最小安全诊断并取得 GLM 11/11 前，不得把该 run 写成完整 PASS。

## 确定性验证

- `ProjectHistoryReconstructionTest` 覆盖 Git 创建/修改/删除/恢复/重命名、独立成果拆分、文档项目、敏感材料 metadata-only、rewrite、事件守恒、大历史、窗口重试和 cache。
- Ground Truth、用户修正、Window planner/checkpoint、最小模型合同、Provider-neutral Prompt 与语言策略测试通过。
- 当前本地后端/H2：602 项，0 失败，0 错误，11 个条件跳过；只显式排除本机 Docker 不可用的 `ProjectFlowPostgresIT` 与尚未生成 Round 3 文件的 `ProjectHistoryHumanReviewRound3ManifestTest`。Round 3 清单合同未被冒充为已通过。
- 受影响 ProjectHistory/Provider-neutral 回归：119 项，0 失败，0 错误，4 个条件跳过；全部 19 个冻结 Ground Truth case 通过。

## 保留的 RC2 静态基线

- RC2 曾记录后端/H2 557 项、Frontend contracts 58/58、Playwright 9/9、Hermes 10/10、Obsidian 25/25 通过；这些计数只属于当时版本。
- GitHub head `74ba013` 的 push run `31317712835` 与 PR run `31317716057` required jobs 和 PostgreSQL 16 Testcontainers 通过；RC3 最终 Evidence head 仍必须重新取得七项 required job 全绿。

## 保留的 RC2 真实 Provider 与 ProjectFlow Dogfood 基线

- GLM `glm-5.2` Responses/high：Understanding 17/17、19-case qualified、真实场景 11/11、ProjectFlow Dogfood PASS。
- DeepSeek `deepseek-v4-flash` Chat/max：Understanding 17/17、19-case qualified；scenarios attempt 1 为 9/11，attempt 2 只重跑失败 job 后 11/11，ProjectFlow Dogfood PASS。
- 两者演示、研究、数据、品牌页和无 Git 版本均通过；Invalid Evidence、跨项目引用、unsupported strong fact、Raw Event 丢失和敏感持久化为 0/false。
- 旧 qualification FAIL、旧 DeepSeek Dogfood 10/11、Secrets 缺失、run `31294942095` 与 `31303975027` 失败均保留在 RC2 报告。

## 环境限制与结论

本机 Docker Desktop Linux engine 不可用；PostgreSQL 只引用 GitHub Testcontainers，没有用 H2 冒充。Round 1 和 Round 2 的 30 Story/8 Chapter 包均已冻结且结论为 `NEEDS_REVISION_NOT_APPROVED`；新的 Round 3 包尚未冻结、评分为空。当前质量门禁为 `PENDING_HUMAN_REVIEW_ROUND3 / NOT PASS`。
