# ProjectFlow V3.2 Phase 6 执行报告：输出草稿引用已确认变更

日期：2026-06-19
范围：Phase 6 第一段最小闭环，让周报、项目总结、README 和简历素材引用已确认事实，而不是只套模板。

## 本轮已完成

- `AiOutputService` 注入 `ProjectChangeRepository`。
- 输出生成时读取当前项目的 `ACCEPTED` ProjectChange。
- 周报新增“已确认变更”章节。
- 项目总结新增“已确认变更”章节。
- README 段落新增 `Confirmed changes`。
- 简历要点新增已确认变更数量说明。
- 输出内容标明来源类型，例如 `EVIDENCE_BUNDLE` 或 `USER_MANUAL`。

## 关键边界

- 只引用 `ProjectChangeStatus.ACCEPTED`。
- 不引用 `PENDING`、`EDITED`、`IGNORED` 等未确认候选。
- 不调用模型，不把生成文本写成确认事实。
- 没有已确认变更时，明确显示“暂无已确认变更”。

## 现实取舍

- 暂未按受众生成明显不同语气。
- 暂未显示 Evidence Bundle 的完整引用链。
- 暂未保存输出草稿版本历史。
- 暂未实现输出质量检查。

## 验证结果

- 先写红灯测试：`AiOutputControllerTest` 初次运行失败，因为周报内容不包含“已确认变更”。
- 实现后，`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q -Dtest=AiOutputControllerTest test` 通过。
- 后端全量测试通过：`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q test`。
- 前端构建通过：`npm.cmd run build`。

## 下一步

1. 记录输出生成的 ModelUsageRecord。
2. 区分真实 usage 和估算 usage。
3. 在设置页展示模型调用统计和失败原因。
