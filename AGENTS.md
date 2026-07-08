<!-- PROJECTFLOW V3.3.4 CONTEXT START -->
ProjectFlow 当前版本为 V3.3.4。后续 Agent 必须按“待整理变更 -> 开发推进段 -> 建议沉淀 -> 项目沉淀”理解产品，不要回到旧的“今日开发 / 项目资产字段”主线。

开始任务前请阅读 `.projectflow/AGENT_PROTOCOL.md`。完成开发任务后，按协议把结果写入 `.projectflow/agent-results/`。不要删除添加项目、zip 导入、本地项目绑定、模型配置、登录等核心入口。

开发推进段必须描述真实开发结果、用户或开发者可感知变化、验证情况和不确定项。禁止用 backend/frontend/docs/config 等目录名、提交数量或“开发推进”空话替代具体摘要。能力与成果页以结构化 Capability Card 为主，旧 `completedCapabilities` 仅作兼容档案。

V3.3.4 关键决策（后续 Agent 必须遵守）：
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
<!-- PROJECTFLOW V3.3.4 CONTEXT END -->

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
