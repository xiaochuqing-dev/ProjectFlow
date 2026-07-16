# ProjectFlow V3.4.1 自动项目历程

## 产品定义

项目历程是 ProjectFact 之上的时间 read model。它回答项目在某日、某个 ISO 周、某月以及完整生命周期内实际发生了什么，不创建新事实，也不承载下一步、路线图、优先级或未来规划。用户不保存、不确认派生摘要。

ProjectFact 是唯一事实来源。摘要、主题和主题关系都是可删除重建的 derived data；刷新失败不会修改事实或确定性统计。

## 时间归属

`factEventAt` 优先使用 `occurredTo`，为空时使用 `occurredFrom`。写入事实和旧数据迁移时一次性保存 eventAt、dayKey、ISO weekKey 与 monthKey。默认时区是运行机器时区，可用 `projectflow.timeline.zone` 或 `PROJECTFLOW_TIMELINE_ZONE` 固定。自动化测试显式使用固定 ZoneId，避免机器时区改变边界结果。

DAY 使用 `YYYY-MM-DD`，直接分页展示事实，不调用模型。WEEK 使用 ISO week-based year 的 `YYYY-Www`。MONTH 使用 `YYYY-MM`。LIFECYCLE 的固定 key 是 `ALL`。

## 确定性统计

事实数、批次数、去重 commit、文件、Agent result、需要关注数以及最早/最晚时间全部由数据库 group/count/min/max 查询产生。摘要不得创造或覆盖统计。周期列表和详情都分页，主题数量与主题事实数量使用批量聚合查询，禁止按主题 N+1。

## 派生摘要与主题

WEEK、MONTH 和 LIFECYCLE 使用统一 ModelGateway 与登记的 Timeline ModelTaskType。模型输入是紧凑 ProjectFact DTO，不包含 Key、Authorization、reasoning 原文、完整 prompt 的持久化副本或原始响应。

周期输出包含 `periodSummary`、`themes[].title/summary/factIds` 与 `ungroupedFactIds`。每个允许的 fact ID 必须且只能首次归入一个主题或未分组集合；未知 ID、跨项目 ID、遗漏覆盖、非法结构或规划字段使本次输出失败。重复 ID 按首次归属确定，覆盖数始终显式保存。

Timeline Theme 只属于一个摘要周期。`ProjectTimelineThemeFact` 把主题追溯到 ProjectFact，事实再追溯到批次与证据。Timeline Theme 不等同于长期 Project Capability。

## 大周期与完整生命周期

单周期超过 120 facts 时按 120 条 bounded chunk 生成局部摘要，再用 chunk ID 做一次综合。综合结果展开回全部事实关系，因此 230 facts 会使用 3 次模型调用并验证 230/230 覆盖，recent-N 截断不能冒充完整周期。

LIFECYCLE 不把全部事实一次塞给模型。它读取月度事实版本、确定性月统计和已有月摘要，按 monthKey 生成阶段，再把阶段月集合展开为完整 fact membership。生命周期覆盖仍以所有事实 ID 校验。

## 自动刷新与失败保护

事实成功提交后发布 after-commit 事件，只标记受影响的 WEEK、MONTH 和 LIFECYCLE。摘要用排序后的事实 ID 与 updatedAt 生成 fingerprint；相同内容不会重复刷新。持久化 `PROJECT_TIMELINE_REFRESH` job 复用既有有界执行器、预算、取消、retry 和重启语义。GET API 只读数据库，绝不触发模型。

没有默认 Provider 时状态为 `WAITING_FOR_MODEL`，事实与统计仍可读；配置 Provider 后等待项自动重新排队。生成在事实事务之外执行，成功时短事务原子替换主题关系。失败或取消保留旧 READY 内容并标记 stale；retry 只用于异常恢复。

历史 backfill 运行时先累计 dirty 周期，不启动摘要生成。每个 bounded chunk 只扩展事实与 dirty 集合，历史完成后统一恢复队列，避免每个 chunk 重建全部摘要。checkpoint、重启和已覆盖 commit 规则继续由 V3.4.0 history 主链负责。

## API 与兼容边界

Timeline API 提供 overview、周期列表、周期详情、主题事实、lifecycle 和 retry。每个入口都校验 userId 与 projectId 所有权，周期 key、themeId 与分页参数在服务边界验证。

前端 `/timeline` 默认按月，支持按日、按周、按月和全部；项目切换立即清除旧状态，并用请求代次阻止慢响应覆盖新项目。`/dev-logs` 继续作为 Daily Review 旧链接兼容，不再是主导航时间视图。

V3.4.1 不实现完整生命周期能力地图，也不实现 Hermes 或 Obsidian 正式同步。它们只能在后续阶段消费同一 ProjectFact 记忆层。
