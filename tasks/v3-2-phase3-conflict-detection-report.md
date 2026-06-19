# ProjectFlow V3.2 Phase 3 执行报告：文件级冲突识别最小闭环

日期：2026-06-19
范围：Phase 3 第一段最小闭环，识别多个 Evidence Bundle 覆盖同一文件的客观冲突。

## 本轮已完成

- 新增 `ChangeConflictService`。
- 新增 DTO：`ChangeConflictResponse`。
- 新增接口：`GET /api/projects/{projectId}/change-conflicts`。
- 冲突识别逻辑：
  - 读取当前项目的 Evidence Bundle。
  - 按文件路径聚合证据包。
  - 如果同一文件出现在多个不同 Work Session 的 Evidence Bundle 中，则生成 `FILE_OVERLAP` 冲突。
  - 冲突状态为 `PENDING`。
  - 冲突严重度当前为 `MEDIUM`。
- 首页侧栏新增“冲突待审查”摘要卡片。
- 前端新增 `ChangeConflict` 类型和 `listProjectChangeConflicts()`。
- 生成 Evidence Bundle 后会刷新冲突摘要。

## 关键边界

- 本轮只识别客观文件重叠，不判断哪个 Agent 正确。
- 冲突来源是 Evidence Bundle，不读取用户主目录或全局 Agent 日志。
- 冲突只是待审查信号，不会自动写入确认事实。
- 没有把连续修改误判为已确认问题，只提示用户判断“连续修改还是冲突”。

## 现实取舍

- 当前冲突为派生结果，尚未持久化为独立队列表。
- 暂未支持忽略、接受、关闭冲突。
- 暂未识别模块级、任务级、风险级、项目档案字段级冲突。
- 暂未实现多 Agent 同时修改同一模块的高级语义判断。

## 验证结果

- 先写红灯测试：`WorkSessionScanControllerTest` 初次运行失败，因为 `/api/projects/{projectId}/change-conflicts` 不存在。
- 实现后，`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q -Dtest=WorkSessionScanControllerTest test` 通过。
- 后端全量测试通过：`C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd -q test`。
- 前端构建通过：`npm.cmd run build`。

## 下一步

1. 从 Evidence Bundle 生成候选变更，先用本地规则生成保守中文候选。
2. 候选变更展示 Evidence Bundle、客观证据、Agent Claim 和归因置信度。
3. 后续把冲突持久化，并支持用户忽略或确认。
