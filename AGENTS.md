<!-- PROJECTFLOW V3.3.7 CONTEXT START -->
ProjectFlow 当前版本为 V3.3.7。后续 Agent 必须按“待整理变更 -> 开发推进段 -> 批次化沉淀处理 -> 项目沉淀 -> 能力分析”理解产品，不要回到旧的“今日开发 / 项目资产字段”主线。

开始任务前请阅读 `.projectflow/AGENT_PROTOCOL.md`。完成开发任务后，按协议把结果写入 `.projectflow/agent-results/`。不要删除添加项目、zip 导入、本地项目绑定、模型配置、登录等核心入口。

开发推进段必须描述真实开发结果、用户或开发者可感知变化、验证情况和不确定项。禁止用 backend/frontend/docs/config 等目录名、提交数量或“开发推进”空话替代具体摘要。能力与成果页以结构化 Capability Card 为主，旧 `completedCapabilities` 仅作兼容档案。

V3.3.7 关键决策（后续 Agent 必须遵守）：
- 长分析只能通过持久化 Job 执行；重复输入复用活动 job，不得重复调用模型或重复正式写入。
- retry、重新分析、页面重试和恢复入口都不得绕过活动任务唯一性；retry 只允许忽略已完成历史，不能强制创建第二个等价活动 job，并须保留来源 job 关系。
- 取消必须在外部调用、紧凑重试和持久化前检查；取消后不得新增正式结果，已确认沉淀、能力卡片和旧成功结果必须保留。
- QUEUED、RUNNING、CANCEL_REQUESTED、CANCELLED、INTERRUPTED/RETRYABLE、EXPIRED、REJECTED、FAILED 必须保持不同语义和人话提示。
- 线程池、队列、模型 HTTP 并发、请求次数、总耗时和 token 都必须有上限；401/403、取消、配置错误和保存失败不得盲目重试。
- 服务重启只自动恢复尚未外部调用的排队任务；模型请求状态未知时禁止自动重发，避免重复计费。
- 不得把 Mock、固定响应或静态契约描述为真实 PostgreSQL、真实浏览器或真实 DeepSeek 联调。无安全 Key 时真实模型测试必须标记 SKIPPED。
- 测试分层必须分别说明 H2/单元、PostgreSQL 16 Testcontainers、真实前后端 Playwright、固定兼容模型服务和可选真实 DeepSeek 的证据边界。
- 任务 API 必须同时校验 userId 与 projectId 归属，不返回 Key、Authorization、reasoning 原文、请求体、原始响应或未脱敏绝对路径。
- 开发完成至少运行后端测试、H2 兼容、前端生产构建和 Playwright；PostgreSQL Testcontainers 在 Docker/CI 环境运行并作为阻断门禁。
- V3.3.7 收尾报告必须记录测试数量、核心 E2E 范围、PostgreSQL workflow、H2 旧库升级、CI Run、真实 DeepSeek 状态、关键文件和提交 SHA。

V3.3.6 关键决策（后续 Agent 必须遵守）：
- 工作台只显示分析批次摘要；沉淀处理中心按时间和批次组织，并默认逐条处理正式建议。
- 本地事实草稿和 Agent result 草稿不得自动生成正式沉淀建议；来源、质量和推荐强度必须明确显示。
- 确认项目沉淀后必须记录来源批次、涉及文件和待能力分析状态；能力分析以已确认项目沉淀为输入并记录输入快照。
- 能力分析失败不得消耗待分析状态或覆盖上次成功卡片；成功持久化后才更新沉淀参与状态。
- Git、文件、GitHub、Agent result 和模型等外部耗时调用不得放在方法级长事务中。

V3.3.5 关键决策（仍然有效）：
- 模型链路必须区分请求、响应、截断、JSON 解析、目标结构识别、证据绑定和持久化阶段。诊断保留 finish reason、token usage、实际 Max Tokens/Temperature、超时、Provider/model、紧凑重试与部分恢复结果，不显示 Key 或原始响应。
- 模型疑似截断时执行一次紧凑重试；截断根数组中已经完整的条目可以保留并标记警告，不能笼统归为“格式无效”。
- DisplayContentSanitizer 只负责规范化，不再在持久化前截断。列表使用展示层预览，详情展示完整内容；旧省略号数据标记后引导重新分析，不假装恢复。
- 沉淀确认使用“系统推荐 + 后果预览 + 明确结果”，普通用户不需要理解内部枚举；确认后反馈目标、证据数、文件数、摘要变化与查看入口。
- 能力卡片关联分析 job。页面区分当前成功批次、最近失败和历史；失败不替换上次成功候选，已确认能力始终保留，旧版无 job 卡片标为来源未知。
- Provider 支持测试、编辑、唯一默认、删除保护和用户确认后的重复清理。Key 留空保留，只有显式勾选才清除；新模型任务只使用明确默认项。

V3.3.4 仍有效的关键决策：
- 模型失败提示人话化：删除所有用户可见的“增强本地摘要”说法，统一改为“本地事实摘要”。按原因拆分：未配置 / 调用失败 / 返回格式无效 / 证据引用无效，用户一眼看懂模型有没有参与、为什么没参与、当前结果是什么来源。
- 本地事实摘要也必须中文化：用户可见主内容（title / plainSummary / mainChanges / userVisibleValue / 能力卡片名）禁止直接复读英文 commit message。英文原文只能出现在证据细节里。无法可靠转写的英文标题标为“根据提交记录整理的变更”。
- GitHub 接入入口前移到“项目接入”区域（本地路径 / 模型 / GitHub 同属项目接入状态），不再只藏在待整理变更卡片里。
- GitHub 小白接入向导：未登录时提供“打开登录终端 / 复制登录命令 / 重新检查”；未安装时提供“查看安装说明 / 重新检查”。“打开登录终端”只执行固定白名单命令 `gh auth login --web --clipboard`，不接受前端传入任意命令，不读取/展示/保存 token。
- GitHub 刷新只读取远程提交信息，不会修改本地代码（不会 pull、merge、rebase）。UI 必须明确说明这一点。
- 分析口径不直接暴露内部枚举（CALL_FAILED / LOCAL_RULE / CONNECTED / local_ahead 等），统一翻译成中文人话。前端使用 `frontend/src/lib/status-labels.ts` 共享映射。
- evidenceGap 不再因为 GitHub 未参与就默认 true。证据缺口基于真实条件判断（只有 Agent result 无代码 / 代码变化无解释证据 / 远程领先未同步 / 本地远程分叉 / 只有未提交变化无解释等），并记录 evidenceGapReason。
- 能力分析改为可恢复异步任务（CAPABILITY_CARD_ANALYSIS job type）。点击“分析项目能力”创建 job，后端异步执行并推进阶段（LOAD_EVIDENCE / MODEL_CAPABILITY_ANALYSIS / PERSIST_CAPABILITY_CARDS / SUCCEEDED / FAILED）；前端轮询，刷新/离开页面后回来能恢复任务状态；完成后重新拉取能力卡片。重新分析只替换未确认候选，已确认能力保留。

V3.3.3 仍有效的关键决策：
- 分析新变化必须显示阶段进度（stage / stageMessage / 已等待时间 / 输入规模）。
- 模型结果保留优先，质量门槛改为标记器（PASS / NEEDS_REVIEW / NEEDS_CHINESE_REWRITE / NEEDS_EVIDENCE / PARTIAL_EVIDENCE / LOW_CONFIDENCE），不再整批丢弃模型结果。只有模型完全不可用（未配置 / 调用失败 / 未返回 / 无法解析 JSON / 证据完全不可用）才回退本地规则。
- 多来源证据（本地 Git / 工作区 diff / GitHub / Agent result / 扫描范围）要整理成分析输入快照交给模型，模型基于证据灵活判断真实开发状态，不写死优先级。
- 需要模型理解的入口（分析新变化、分析项目能力）必须有模型配置前置检查；未配置模型时不生成低质量本地模板结果，明确提示去配置模型。
- 规则负责证据事实，模型负责灵活理解，用户负责最终确认。
<!-- PROJECTFLOW V3.3.7 CONTEXT END -->

# ProjectFlow Local Rules

For substantial ProjectFlow work, read `PROJECT_CONTEXT.md` first, then inspect only the task-relevant docs and source files.

Keep changes aligned with the current direction: ProjectFlow is a developer workbench for project understanding, agent result review, project profile maintenance, daily review, and asset output. Do not drift it toward a generic Kanban/admin app.

## Ponytail-Inspired Redundancy Control

Reference: `DietrichGebert/ponytail` on GitHub. Use this as an instruction pattern, not as a project dependency.

Before adding code, stop at the first rule that works:

1. Does this need to exist at all? If not, skip it.
2. Can existing project code, standard library, browser/native platform, or installed dependency do it? Reuse that.
3. Can the change be one focused line or one focused helper? Prefer that over new abstractions.
4. Only then write the smallest implementation that satisfies the current requirement.

For ProjectFlow specifically:

- Prefer deleting or consolidating duplicated cards, DTO mappings, API wrappers, and page-local helpers before adding new ones.
- Do not create new services, components, hooks, entities, or dependencies unless existing boundaries cannot cleanly handle the requirement.
- Keep detailed information in focused detail pages; do not duplicate the same summary across dashboard, project profile, tasks, and outputs.
- Mark intentional shortcuts with a `ponytail:` comment only when there is a real ceiling and a clear upgrade path.
- This rule never overrides security, ownership checks, trust-boundary validation, data-loss prevention, accessibility, model-failure fallback, or explicit user requirements.
- Non-trivial new logic still needs the smallest useful test or runnable check.
