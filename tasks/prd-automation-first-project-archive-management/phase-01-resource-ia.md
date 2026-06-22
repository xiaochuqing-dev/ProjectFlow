# 阶段 1：资源管理信息架构与数据字段统一

## 目标

把当前分散的长期页面统一成“可管理资源”的信息架构，先明确每条记录至少要有哪些字段，避免后续页面继续各自堆列表。

## 范围

涉及页面：

- `/project-intelligence/timeline`
- `/project-intelligence/fact-sources`
- `/project-intelligence/changes`
- `/project-intelligence/analysis-records`
- `/dev-logs/sources`
- `/project-changes/[changeId]`

涉及首页区域：

- 今日变化闭环
- 最近活动
- 项目档案入口

## 统一资源字段

每条可管理资源至少需要：

- `id`
- `projectId`
- `title`
- `summary`
- `type`
- `status`
- `source`
- `createdAt`
- `updatedAt`
- `eventDate`
- `confidence`
- `primaryAction`
- `detailHref`

如果后端暂时没有完整字段，前端允许用已有数据派生，但必须集中处理，不在每个页面重复拼接。

## 页面模式

所有长期资源页采用同一种骨架：

1. 顶部说明：一句话说明这个页面管理什么。
2. 筛选区：月份、类型、状态、来源、关键词。
3. 时间分组：月 -> 日。
4. 资源长条卡片：日期、标题、摘要、状态、来源、关键数量。
5. 详情跳转：点击卡片进入详情，或展开详情抽屉。

## 交付物

- 资源字段映射表。
- 页面改造清单。
- 重复数据来源清单。
- 每个页面的主动作定义。

## 验收

- 所有长期页都能说清楚“列表看什么、点进去看什么、从哪里返回”。
- 每个长期页的列表项都具备日期、状态、摘要和详情入口。
- 不再出现只显示大段原文、没有日期和详情层级的资源列表。
