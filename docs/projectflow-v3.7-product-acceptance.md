# ProjectFlow V3.7 产品验收

验收日期：2026-07-24

结论：CONDITIONAL PASS。Open-world discovery、零模型边界、动态 Section、工具校验和历史诚实性已形成最小生产闭环；真实 Provider 语义质量、自动 SCIP producer 和完整 milestone evolution 仍未完成。

## 极端输入

| 输入 | 证据 | 结果 |
| --- | --- | --- |
| 空目录 | `ProjectUnderstandingServiceTest` | classification=EMPTY，0 model，0 dynamic section，history unavailable |
| 空 TXT | 同上 | `SKIPPED_NO_SUBSTANTIVE_EVIDENCE`，shape=EMPTY_CONTENT，0 model |
| 有内容 TXT/奇怪命名 Markdown | `fuck-this-bug.md` 自动化 | 进入 UNKNOWN_DOCUMENT candidate 和 bounded Scout；不生成代码结构 |
| 小脚本 | Planner deterministic boundary | source <=2、LOC <=500 时 shape=SCRIPT_OR_SMALL_CODE，只默认 Purpose/Input/Output/Dependencies/Usage |
| 纯前端 | React 真实仓库 | Monorepo/源码/文档/manifest 被发现；没有模型时不硬断言 Backend/Database |
| 纯后端 | Spring Petclinic 真实仓库 | Java/测试/构建/部署证据可用；fallback 不冒充 precise call graph |
| Desktop | VS Code 真实仓库 | HUGE repository，动态形态需模型；规则不把它固定解释为 Web |
| Fullstack | ProjectFlow 自身 | 多技术、Git、Agent/docs/CI 来源进入统一 Source Map |
| Monorepo | JUnit、React、VS Code | workspace/scale 触发 hierarchical plan，Scout 来源保持 80 上限 |
| 无 Git | `HistoricalCoverageServiceTest` | 当前理解可用，Historical Coverage=UNAVAILABLE，Evolution=CURRENT_STATE_ONLY |
| 3 commits | 真实临时 Git 测试 | SHORT_GIT_HISTORY / EARLY_PROJECT，不生成成熟阶段 |
| 长历史 | Git metadata bounded strategy | 最多 5,000 commit period sample、15 milestone candidates，不逐 commit LLM |
| README 过时/冲突 | Scout schema/validation | 模型可输出 evidence-bound conflict/currentness warning；无模型时保持 UNKNOWN，不盲信 |
| 无模型 | 所有真实性能仓库 | deterministic Source Map/Profile 可用，modelRequest=0 |
| 模型失败 | `ProjectUnderstandingServiceTest` | 新项目保存 deterministic 结果；已有成功快照保留并标 STALE |
| 无变化 | 同上 | inventory cache hit，0 model，不重建 Evolution |

## Token 与性能

以下为本机真实首次扫描，使用现有开源 checkout；均无安全模型 Key，因此 model/token 为 0。`scoutEvidence` 和 `deepRead` 上限为 80。

| 档位 / 仓库 | files | LOC | docs | discovered | candidates | scout/deep | skipped | total ms | structure coverage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| small / Spring Petclinic | 130 | 4,450 | 4 | 131 | 55 | 55 | 75 | 1,857 | 0.771 |
| medium / ProjectFlow | 649 | 68,657 | 155 | 650 | 252 | 79 | 397 | 3,598 | 0.787 |
| large / JUnit | 2,326 | 235,265 | 117 | 2,327 | 1,212 | 80 | 1,114 | 17,180 | 0.799 |
| huge / React | 7,274 | 833,665 | 139 | 7,275 | 4,035 | 80 | 3,239 | 68,512 | 0.799 |
| >=1M / VS Code | 16,344 | 3,550,729 | 195 | 16,345 | 2,668 | 80 | 13,676 | 134,367 | 0.793 |

React/VS Code 首次扫描在当前 Windows 文件系统明显偏慢，属于真实风险。repeat inventory fingerprint 分别为 166 ms 和 389 ms，因此无变化路径仍适合快速 0-model cache。

## 人工判断

- 未预设不存在的后端、数据库或 Timeline：通过。
- 奇怪命名文档进入候选：通过自动化。
- README 不被当作绝对事实：通过设计与 evidence validation；真实模型质量未验收。
- 分析维度按输入变化：通过 read model/UI；Frontend/Backend 细分需要真实模型验证。
- 模型请求和 token 有界：通过。
- 未知与历史限制可见：通过。
- 页面信息价值：最小验证通过，尚未做最终 UI。
- 用户无需整理目录：通过基本案例；PDF/Office 深读尚未支持。
- 100k/1M LOC：有界完成，但首次扫描性能仍需后续优化。

## 真实 Provider

环境中的 OPENAI_API_KEY、ANTHROPIC_API_KEY、DEEPSEEK_API_KEY 均不存在。真实语义验收：SKIPPED。固定/Mock 未冒充质量通过。

## SCIP producer

官方 producer、许可证和 runtime 已重新核验。现有 consumer/fallback 继续生产可用；本阶段没有在任意用户项目中静默安装 Node/Python/JVM 或执行 build，因此一键 producer 为 DEFERRED，不报告完成。
