# ProjectFlow V3.8.5 RC2 当前状态审计

审计更新日期：2026-08-10。对象为 Draft PR #15；基线 master 为 `5cb5e49661206feb8f59885bea672c314c9374e8`，当前自动化验收代码 head 为 `74ba013615932748b4a41077baf8f89af618a5d2`。

接管时的失败事实全部保留：最初 GLM/DeepSeek 19-case qualification FAIL；旧 DeepSeek 真实场景 10/11 且 Dogfood 角色引用不一致；GLM 完整场景 NOT_RUN；人工 0/0；旧 Obsidian CI failure；workflow `31264440534` 因 Secrets 缺失在请求前失败。

实际修复保持 Provider-neutral：模型 schema 只负责 Story/Chapter 措辞与有 Evidence 的 reason；role、Primary/Supporting、Chapter membership、before/change/after、冲突、生命周期与 Evidence 归属由工程层唯一维护。reasoning-only 空 content 只允许一次同输入恢复，第二次失败即停止；DeepSeek 仍为 Flash/max。真实模型 qualification 与 scenarios 分成两个 job，避免 GLM 单 job 接近 GitHub-hosted 6 小时硬上限，但没有拆除任何 gate。

本地与确定性结果：后端 557 项通过、0 失败、0 错误、6 个条件跳过；人工清单合同已从 skip 转为实际 PASS。前端契约 58/58、Playwright 9/9、生产构建、Hermes 10/10、Obsidian 25/25、敏感扫描和根启动脚本均通过。head `74ba013` 的 push run `31317712835` 与 PR run `31317716057` 通过。

真实结果：workflow `31318477841` 的 GLM `glm-5.2` Responses/high 与 DeepSeek `deepseek-v4-flash` Chat/max 均通过 V3.8.0、V3.7.5 38-run、Understanding 17/17 和 V3.8.5 19-case；GLM 场景 11/11。DeepSeek attempt 1 为 9/11，保留 1 failed/1 pending 的 17-window 波动；相同 head 只重跑失败 job后 attempt 2 为 11/11。两者 Dogfood、五类非代码与安全计数最终通过。

当前唯一产品门禁阻断是人工可读性：30 Story / 8 Chapter 已冻结，但 reviewerCount=0、评分为空。PR 必须保持 Draft；人工通过前不合并、不 backfill、不清理分支/worktree。

依赖风险：只读 npm audit 为 4 high、0 critical；本轮没有执行可能改变依赖图的 audit fix。
