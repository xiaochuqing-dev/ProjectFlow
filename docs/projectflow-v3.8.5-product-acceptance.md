# V3.8.5 产品验收清单

## 已通过

- ProjectFact、ProjectHistoryEvent、Evidence 和 rewrite 状态未被历史重建或展示修正覆盖。
- Primary/Supporting 由工程层唯一构图；中文 fallback、Commit 用户摘要、工程详情下钻、用户修正、多窗口 checkpoint 和失败/取消/未处理诊断已实现。
- RC2 本地后端 H2 546 项、frontend contracts 58/58、build/lint、Playwright 9/9、Hermes 10/10、Obsidian 25/25 通过。
- RC2 本地 PostgreSQL 16 Testcontainers 5/5 通过；当前 GitHub required CI 仍在运行，不能沿用旧 head 结果。
- GLM Responses 与 DeepSeek Chat 的单请求协议/安全合同通过。

## 阻断或未运行

- 历史 GLM 19-case qualification FAIL；历史 DeepSeek 19-case qualification FAIL。
- DeepSeek 真实场景 10/11，ProjectFlow Dogfood 因 Primary/Supporting 引用不一致失败；GLM 真实场景未运行。
- RC2 新双 Provider workflow 因 GitHub Secrets 缺失在请求前失败，真实结果为 NOT_RUN。
- 五类 DeepSeek 非代码场景通过，但不是双 Provider 最终泛化门禁。
- 人工可读性抽样 0 Story/0 Chapter，NOT_RUN。
- 前端目前只提供标题、摘要、隐藏、置顶、恢复等基础修正 UI；高级合并/拆分/角色/章节声明仍以 API/消费者预览为主，不宣称普通用户全量 UI 闭环。

## GitHub 结论

PR #15 仍为 Draft。未执行 Ready for Review、merge master、Tag、Release、分支删除或 worktree 清理。真实 Provider 和人工门禁未通过前，不得把产品候选标为最终 PASS。
