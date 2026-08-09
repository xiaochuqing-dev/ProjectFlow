# ProjectFlow V3.8.5 当前状态审计

历史快照说明：本文件记录 RC2 接管前的初始审计与失败基线，以下 V4 Pro、qualification FAIL、10/11 和 NOT_RUN 不代表当前配置或最终自动化结果。2026-08-10 的当前状态以 `projectflow-v3.8.5-rc2-current-state-audit.md` 和 `projectflow-v3.8.5-acceptance-report.md` 为准；当前 DeepSeek 使用 `deepseek-v4-flash` / max。

审计基线

- 基线 master：`5cb5e49661206feb8f59885bea672c314c9374e8`
- 基线合并：PR #14 的 acceptance backfill merge；本地工作分支为 `codex/v3.8.5-history-quality`
- 基线产品版本：V3.8.0；本阶段实现将后端和前端版本提升为 V3.8.5
- 本阶段不创建 Tag、Release、watcher、daemon、Git 客户端或新的运行时依赖

RC2 接管前审计状态

- 确定性实现、H2、前端、Playwright、Hermes、Obsidian 和 GitHub required CI 已通过。
- GLM `glm-5.2` 与 DeepSeek `deepseek-v4-pro` 单请求合同通过，但两份 19-case qualification 均 FAIL；DeepSeek 真实场景为 10/11，ProjectFlow Dogfood 失败。
- GLM 真实场景、旧版 ProjectFlowRealModelEvalIT、ProjectUnderstandingRealModelIT 和人工可读性抽样未运行。
- 因此本阶段保持 BLOCKED，PR #15 继续 Draft，不合并 master。

已存在的可信底座

`ProjectHistoryEvent` 保存有界来源事件，`ProjectHistorySnapshot` 保存可替换的 Overview、Chapter、Story 和 Thread。`ProjectFact` 仍是唯一强事实来源；历史刷新不得改写 Fact、Timeline、Capability、Evidence 或既有 Evolution。只有显式刷新 Job 可以发现来源或调用 `PROJECT_HISTORY_SYNTHESIS`，所有 GET、Gateway、Hermes 和 Obsidian 读取均为持久化只读。

V3.8.0 的可读性缺口

基线 Dogfood 记录约 197 个 Commit、2,611 个 Source Event、536 个 Story、27 个 Chapter 和 392 个 Thread。Story 仍可能按类名、文件名或通用提交消息拆分，确定性 fallback 也会产生“相关变化”“形成初始结果”等技术化模板。普通用户无法从第一层判断完整工作成果、用户影响和后续演变。原有测试主要验证非空、守恒和 Evidence 合法，缺少冻结的人工作品级 Ground Truth。

当前真实生成路径

来源收集由 `ProjectHistorySourceCollector` 完成；`ProjectHistoryReconstructionService` 负责事件幂等写入、Technical Atom/Primary/Supporting 归并、Chapter/Story/Thread 重建和模型校验；`ProjectHistoryReadService` 只读 Snapshot 并叠加展示修正；`ProjectHistoryCorrectionService` 保存用户声明覆盖。模型入口仍只经 `ModelGatewayService`。

本阶段必须处理的技术债

- 让主变化和测试、文档、配置等 Supporting Change 分开，但保留可下钻 Evidence。
- 让 fallback 使用中文动作、对象、结果和 Before/Change/After，原因没有 Evidence 时保持 UNKNOWN。
- 把模型工作拆成有界、稳定、多窗口；窗口必须有 cache key、checkpoint、失败/取消/跳过状态和未处理范围诊断。
- 让用户修正是 `USER_DECLARED_PRESENTATION` 覆盖层，支持乐观版本冲突、回退和刷新后恢复。
- 让 Gateway、Agent Context、Hermes、Obsidian 与前端读取同一份修正后展示。

明确延后

最终 GUI、虚拟滚动、跨项目图视图、数据库迁移框架、远程 MCP、Agent Manager、Provider 排行榜、Tag、Release、自动 Obsidian watcher 和安全存储迁移均不属于本阶段。
