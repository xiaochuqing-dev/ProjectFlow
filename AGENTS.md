<!-- PROJECTFLOW V3.3.2 CONTEXT START -->
ProjectFlow 当前版本为 V3.3.2。后续 Agent 必须按“待整理变更 → 开发推进段 → 建议沉淀 → 项目沉淀”理解产品，不要回到旧的“今日开发 / 项目资产字段”主线。

开始任务前请阅读 `.projectflow/AGENT_PROTOCOL.md`。完成开发任务后，按协议把结果写入 `.projectflow/agent-results/`。不要删除添加项目、zip 导入、本地项目绑定、模型配置、登录等核心入口。

开发推进段必须描述真实开发结果、用户或开发者可感知变化、验证情况和不确定项。禁止用 backend/frontend/docs/config 等目录名、提交数量或“开发推进”空话替代具体摘要。能力与成果页以结构化 Capability Card 为主，旧 `completedCapabilities` 仅作兼容档案。
<!-- PROJECTFLOW V3.3.2 CONTEXT END -->

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
