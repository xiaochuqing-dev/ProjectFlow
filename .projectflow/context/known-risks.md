# Known risks

- 部分 Provider 会把主要推理放在 reasoning 字段而 content 为空；不得将其误报为普通空响应。
- 紧凑重试不能无限递归，也不能沿用原输出预算。
- 本地草稿不得通过 DTO 或兼容分支重新进入正式 ProjectChange。
- 能力分析失败不得覆盖上次成功卡片，也不得把待分析沉淀标为已处理。
- 新增字段必须兼容旧行；禁止要求用户删除数据库。
- 前端不得显示 API Key、原始模型响应、内部枚举或默认展开绝对路径。
- GitHub 刷新仍然只读，不执行 pull、merge 或 rebase。
