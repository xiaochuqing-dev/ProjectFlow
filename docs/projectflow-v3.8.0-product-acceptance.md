# ProjectFlow V3.8.0 产品验收

更新日期：2026-08-04

## 当前结论

项目历程核心实现、聚焦回归、三个公开仓库验证、DeepSeek 与 GLM 双真实 Provider、Backend H2、PostgreSQL 16、Frontend、Playwright、Hermes、Obsidian、启动器和安全扫描均已通过。功能 PR #13 和合并后 master required CI 已通过；本文件随仅含元数据的验收回填 PR #14 合并后构成最终批准。

## 产品能力验收

| 能力 | 当前实现 | 验收结论 |
| --- | --- | --- |
| 通用主轴 | History 为任意项目通用展示轴，Capability 可选 | 通过代码与文档检查 |
| 原始事件 | ProjectHistoryEvent 保留来源、时间、transition、authority、Evidence、rewrite state | 通过聚焦测试 |
| 可替换快照 | Overview、Chapter、Story、Thread 独立于 ProjectFact | 通过聚焦测试 |
| 显式刷新 | 仅 PROJECT_HISTORY_REFRESH Job 发现来源和调用模型 | 通过 Job 与 Controller 检查 |
| GET 只读 | Overview/Chapter/Story/Thread/Event/Evidence/Filter 不扫描、不调用模型 | 通过代码与 Gateway 测试 |
| 大历史有界 | 5,000 Commit、20,000 Event、分页、PARTIAL coverage | 通过 300+ Commit、1,000+ Event 和公开仓库验证 |
| 增量与缓存 | fingerprint cache、31 天 overlap、保留未受影响 Story | 通过聚焦测试 |
| Git 重写 | 移除来源标记 STALE/INVALIDATED，不删除 Evidence | 通过聚焦测试 |
| Transition | Created/Modified/Removed/Restored/Rename/Move/Split/Merge/Revert/Reapply | 通过冻结与专项测试 |
| 模型安全 | 单次有界措辞改写，非法 ID/Evidence/原因拒绝 | DeepSeek、GLM 与聚焦测试通过 |
| 前端层级 | Overview、Chapter、Story、Thread 稳定深链接 | 开发者预览已实现，最终 GUI 未实现 |
| Gateway/Hermes | 持久化 history 只读消费 | 8/8 通过，19 tools，并发读取与故障恢复通过 |
| Obsidian | Overview/History/Chapter/Story/Thread，官方 URI 默认、Advanced URI 可选 | 21/21 通过，真实临时 Vault、双向跳转、用户内容保留、冲突和 no-op 通过 |

## 冻结确定性验收

ProjectHistoryFrozenDatasetTest 把提示词要求的 24 种历史形状映射到现有可执行测试，包括：

- 5 Commit 小项目、300+ Commit 混乱项目、1,000+ 原始事件。
- 新增、修改、删除、恢复、替换、拆分、合并、重命名、移动、撤销和重新实现。
- 多 Commit 一件事、单 Commit 多件事、merge-heavy、Commit message 与 diff 不一致、空泛 message、中英混合。
- PR/Issue 原因、来源冲突、Agent Result process evidence、文档项目、无 Git、浅克隆、重写、路径重新绑定和敏感材料。

2026-08-03 最新聚焦回归：26 tests，0 failure，0 error，0 skipped，Maven 总耗时 162.4 秒。

## 可读验收产物

- ProjectFlow dogfood：27 Chapter、536 Story、392 Thread、2,611 Source Event。
- Synthetic product：3 Chapter、11 Story、3 Thread、34 Source Event。
- Synthetic evolution threads 覆盖 auth 和 export 的新增→修改→删除→恢复，以及 project-report 的拆分、合并、重命名、撤销和重新实现。
- 三个公开成熟项目均生成非空 Chapter、Story 和 Thread。

Dogfood 两个文件连续多次 SHA-256 一致，证明冻结输入下输出稳定。

## 关键安全门禁

已在 dogfood、公开仓库和真实模型产物中确认：

- Invalid Evidence：0。
- Cross-project reference：0。
- Unsupported strong fact/claim：0。
- 原始事件守恒：true。
- 模型原因无 Evidence：0。
- Key、Authorization、完整 Prompt、raw response、reasoning、绝对路径持久化：0。

提交内容密钥模式扫描和所有验收产物的敏感值、路径边界、文件长度与 SHA-256 校验已通过。七个正式产物由 `acceptance-freeze-manifest.json` 冻结；跨 Provider、PostgreSQL 16、GitHub required checks、功能 merge 和合并后 master 核验均已通过，最终状态随验收回填 PR 合并生效。

## 首次失败保留

1. 批量区域专项测试首次因测试临时目录未创建失败，先修正 fixture。
2. fixture 修正后真实产生 21 个 Story，并出现 guide-01、guide-02 文件名标题。根因是 docs 同时被 Conventional Commit 归一化和稳定路径归一化清除；新增 normalizeStableKey 后改为 backend、frontend、docs 区域 Story。
3. History 聚焦全量首次因 Dogfood Git dubious ownership 失败，其余 24 项通过。修复为每个准确根目录传入 git -c safe.directory，不修改全局配置。
4. 浅克隆专项首次只在 limitations 披露缺口，gaps 未披露。修复后 Coverage gaps 明确说明浅克隆不能代表完整历史。
5. 公开仓库首次断言 READY，实际 DEGRADED。修正为诚实的浅历史 DEGRADED/PARTIAL。
6. 公开仓库第二次把可达 Commit 总数与有界来源事件数直接比较。修正为读取 5,000 Commit 和 20,000 Event 产品边界，并要求未读取范围公开披露。
7. Frontend 首轮完整合同门禁为 54/55。旧 V3.3 文档合同只接受中文“唯一强事实来源”，而当前 PROJECT_CONTEXT 使用等价英文 “only strong-fact source”；合同改为接受两种明确等价表述后 55/55 通过。
8. 根启动器首次 CheckOnly 在隔离 worktree 中触发 Git dubious ownership。启动器改为只给当前仓库调用传入 `git -c safe.directory`，不修改全局配置；随后 build/start/health/stop 通过。
9. 隔离 worktree 首次生产构建因 `node_modules` 指向 worktree 外部而被 Turbopack 拒绝；改用 worktree 内独立 `npm ci`。备用 junction 一度被 TypeScript 扫描，移出 frontend 后最终生产构建通过。该环境失败未被描述为代码通过。
10. PR #13 首轮 `sensitive-content` 因 freeze manifest 使用 Windows 工作树 CRLF 字节计算四个 JSON 的长度和 SHA-256 而失败。产物语义未变化；清单与校验器改为读取 Git index/blob 的规范提交字节，消除 Windows/Linux 换行差异，并保留首轮 CI 失败证据。
11. GLM run `30816468130` 的 Provider Probe 和 38-run 通过，但产品链路为 15/17：large-middle 暴露大型源码 Content Map 丢失，conflicting-final-docs 暴露错误类别断言；History 因脚本顺序未执行。修复后真实聚焦复验通过。
12. GLM run `30830424132` 与 `30831241801` 的旧 reasonWithoutEvidenceCount=1 混合了硬 Evidence 违规和漏写 UNKNOWN。拆分诊断后，非空原因无 Evidence 继续硬拒绝，空原因漏写 UNKNOWN 由工程层补齐；最终运行两项均为 0。
13. 功能 merge 后 master run `30842827453` 首次只有 Browser E2E 因 Playwright webServer 启动进程一次性退出而失败；同一提交的功能 PR 两轮 Browser E2E 和其余 master jobs 均通过。保留失败证据后只重跑失败作业，attempt 2 成功，没有用代码改动掩盖环境波动。

这些失败均保留在验收记录中，没有通过降低安全阈值、伪造 PASS 或删除不利证据处理。

## 非目标验收

确认未新增：

- 最终 GUI 视觉重建。
- 通用看板、Todo、排期、工时或团队管理。
- Git 客户端、GitHub/GitLab 替代、工作区 restore 或 rebase。
- Obsidian 编辑器或 Vault 管理器。
- Agent 管理器、Provider 排行、Token 仪表盘。
- 通用 RAG、向量数据库、parser、SCIP producer、daemon、watcher、Tag 或 Release。

## 依赖与 License

V3.8.0 未新增运行时或测试依赖，未复制第三方源码。实现复用现有 Spring/JPA/Jackson、Model Gateway、Durable Job、Project Memory Gateway、Hermes 和 Obsidian Projection 边界。

`npm audit` 当前报告 3 个 high，涉及 PostCSS 与 Sharp/libvips，并聚合影响 Next。自动建议是破坏性的 Next 9.3.3 downgrade，本阶段未执行 `--force`；该依赖风险已显式保留，等待官方兼容修复后单独升级与完整回归。

GitButler、Gource、OpenProject 等仅作为产品模式研究。FSL-1.1-MIT 的竞争限制和 GPL 范围使直接复制不合适；其他 release/changelog 工具也不解决 ProjectFlow 的强事实与任意项目历史问题。THIRD_PARTY_NOTICES 已更新。

## 最终批准条件

只有以下条件全部完成后才能把本文件结论更新为最终通过：

- Backend H2 full suite。
- PostgreSQL 16 Testcontainers required CI。
- Frontend lint、contract、production build。
- Playwright、Hermes、Obsidian 真实临时 Vault。
- 安全扫描、Prompt parity、根启动器 build/start/health/stop。
- 至少第二种真实 Provider，或用户明确调整该硬门禁。
- 功能 PR 和可能的验收回填 PR 均通过 required CI 并合并。
- master clean，开发分支、回填分支和 worktree 全部删除。

Backend、PostgreSQL 16、Frontend、Playwright、Hermes、Obsidian、安全、根启动器、双真实 Provider、功能合并和 master required checks 已满足。验收回填 PR #14 只修改元数据，并在自身 required checks 通过后合并；随后删除 V3.8.0 分支/worktree，最终验收完成。
