# V3.8.5 真实项目与确定性验证

## 确定性验证

- `ProjectHistoryReconstructionTest` 覆盖 Git 创建/修改/删除/恢复/重命名、独立成果拆分、文档项目、敏感材料 metadata-only、rewrite、事件守恒、大历史、窗口重试和 cache。
- Ground Truth、用户修正、Window planner/checkpoint、最小模型合同、Provider-neutral Prompt 与语言策略测试通过。
- 后端全量 H2：557 项，0 失败，0 错误，6 个条件跳过；真实人工清单合同实际 PASS。
- Frontend contracts 58/58、Playwright 9/9、build/lint、Hermes 10/10、Obsidian 25/25 通过。
- GitHub head `74ba013` 的 push run `31317712835` 与 PR run `31317716057` required jobs 和 PostgreSQL 16 Testcontainers 通过。

## 真实 Provider 与 ProjectFlow Dogfood

- GLM `glm-5.2` Responses/high：Understanding 17/17、19-case qualified、真实场景 11/11、ProjectFlow Dogfood PASS。
- DeepSeek `deepseek-v4-flash` Chat/max：Understanding 17/17、19-case qualified；scenarios attempt 1 为 9/11，attempt 2 只重跑失败 job 后 11/11，ProjectFlow Dogfood PASS。
- 两者演示、研究、数据、品牌页和无 Git 版本均通过；Invalid Evidence、跨项目引用、unsupported strong fact、Raw Event 丢失和敏感持久化为 0/false。
- 旧 qualification FAIL、旧 DeepSeek Dogfood 10/11、Secrets 缺失、run `31294942095` 与 `31303975027` 失败均保留在 RC2 报告。

## 环境限制与结论

本机 Docker Desktop Linux engine 不可用；PostgreSQL 只引用 GitHub Testcontainers，没有用 H2 冒充。双 Provider 自动化证明事实守恒、协议、边界和真实场景；人工样本虽已冻结 30/8，但评分仍为空。最终质量门禁为 BLOCKED / PENDING_HUMAN_REVIEW。
