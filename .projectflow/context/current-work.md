# Current work

ProjectFlow V3.3.8.1 正在收尾数据读取可靠性修复：真实用户 H2 旧批次的 modelStatus 为空曾导致沉淀列表 500；工作台普通 work session 刷新曾用 batch=null、segments=[] 覆盖完整分析结果。现已完成旧 batch/change/segment 全字段 null-safe、批次列表固定批量查询、按项目 snapshot、弱数据合并、数据库 Dashboard Bootstrap Read Model、次要接口错误隔离和对应 H2/前端/Playwright/PostgreSQL 回归。V3.3.7 后台任务与 V3.3.8 模型链路未改造。
