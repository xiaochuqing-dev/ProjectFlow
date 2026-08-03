# ProjectFlow V3.8.0 项目历程展示合同

更新日期：2026-08-03

## 目标

用户第一眼看到“发生了什么、对象是什么、结果怎样”，而不是 SHA、文件列表、目录树、Evidence ID 或模型诊断。技术细节必须可逐层下钻，但不能占据第一层。

当前前端是只读开发者预览，用于验证稳定深链接和信息层次，不代表最终 GUI 视觉重建。

## 展示顺序

固定顺序为：

Overview → Chapter → Story → Thread → Raw Event → Evidence

Overview 和 Chapter 是时间阅读入口；Story 是主要变化阅读单元；Thread 是同一主体的纵向演变视图；Raw Event 和 Evidence 是审计层。

## Overview

Overview 第一屏必须包含：

- 快照状态和历史覆盖状态。
- 最早可确认状态。
- 当前可确认状态。
- 可确认时间范围。
- 代表性时间篇章。

随后展示最近变化，以及覆盖缺口、冲突、未知和最近刷新异常。大历史只取有代表性的最多 8 个 Chapter 进入 Overview；完整 Chapter 列表由分页 API 提供。

READY 表示来源覆盖完整且模型未失败。DEGRADED 表示结果仍可读，但来源不完整或模型失败。页面不得把 DEGRADED 隐藏成 READY。

## Chapter

Chapter 显示时间范围、标题、摘要、Story 数、Raw Event 数、authority 和 boundary signals。它不能出现“关键阶段、成熟里程碑、成功完成”等未经证据支持的表达。

Chapter 详情按时间展示所属 Story。每个 Story 卡片只显示动作、对象、结果、时间和证据数量，点击后进入 Story 详情。

## Story

Story 详情第一层包含：

- humanTitle，格式为动作 + 对象 + 结果。
- oneSentenceSummary。
- Before、Change、After 三列。
- 有证据支持时才显示 reason。
- 后续继续变化时显示 laterOutcome。
- Raw Event 数、Evidence 数、authority、summary status 和 coverage。

Story 下方展示所属 Thread 和来源事件。Raw Event 只展示安全来源标题、transition、source type、发生时间、Evidence 数和 rewrite state。绝对路径、完整 patch 和原始响应不展示。

Story 的 conflicts、unknowns 和 limitations 必须单独可见，不能为了简洁而删除。

## Thread

Thread 显示稳定主体、transition 序列、当前可确认结果和按时间排列的 Story。新增、修改、删除、恢复、撤销和重新实现必须保留为可读顺序，例如：

CREATED → MODIFIED → REMOVED → RESTORED

Thread 的 gap、conflict 和 unknown 必须保留。Capability ID 只在确有长期 Capability 关系时作为可选关联，不是 Thread 成立前提。

## Raw Event 与 Evidence

Raw Event 列表支持按来源、类别、transition、authority、epistemic status、rewrite state、主体、attention 和时间范围筛选。所有分页 size 最大为 100。

Evidence 详情只返回安全标签、类型、revision、currentness、epistemic status、coverage、limitations 和安全 deep link。不得返回 Key、Authorization、绝对路径、完整 diff、完整文档、Prompt、raw response 或 reasoning。

## API 合同

写入口只有：

- POST /api/projects/{projectId}/history/refresh

只读入口包括：

- GET /overview
- GET /chapters 与 /chapters/{chapterId}
- GET /stories 与 /stories/{storyId}
- GET /threads 与 /threads/{threadId}
- GET /events 与 /events/{eventId}
- GET /events/{eventId}/evidence
- GET /filters

所有 GET 只读数据库，不扫描文件、不运行 Git、不调用模型、不推进 cursor、不修改任何事实或派生层。

## 稳定深链接

前端深链接格式为：

- Overview：/projects/{projectId}/history
- Chapter：/projects/{projectId}/history?type=chapter&id={chapterId}
- Story：/projects/{projectId}/history?type=story&id={storyId}
- Thread：/projects/{projectId}/history?type=thread&id={threadId}

链接只包含稳定 project ID 和派生实体 ID，不包含凭证或绝对路径。实体不存在、跨项目或不属于当前用户时返回明确错误，不降级到其他项目内容。

## Gateway、Hermes 与 Obsidian

Project Memory Gateway 把持久化 history overview 作为统一只读语义的一部分。Hermes 新增的 history 工具只消费 Gateway/History API，不成为新事实源。

Obsidian CORE 投影生成项目概览、历程索引、Chapter、Story 和 Thread Markdown。ProjectFlow → Obsidian 默认使用官方 obsidian://open URI；检测到 Advanced URI 插件已安装并启用时可生成增强导航 URI。插件缺失时自动退回官方 URI。

Obsidian → ProjectFlow 使用稳定 project ID、entity type 和 entity ID 生成本地详情链接。Markdown 不保存 Token；ProjectFlow 未启动时，普通 Markdown 仍可独立阅读。

## 大历史展示

- 原始事件不因展示折叠而删除。
- 同一主体和同一提交的重复文件变化先折叠为 Story。
- Story 再按动态边界进入 Chapter，不固定按月切割。
- Overview 只展示代表性 Chapter；完整列表分页读取。
- 浅克隆、上限和来源失败必须在 Coverage 中可见。

## 明确不做

- 不把 Commit 列表、文件树或动画图作为主界面。
- 不把 release notes 当作完整项目历程。
- 不提供通用 Kanban、Todo、排期、工时或团队管理。
- 不在 V3.8.0 完成最终 GUI 视觉重构。
- 不提供 Git 工作区修改、restore、rebase、pull 或 push 操作。
