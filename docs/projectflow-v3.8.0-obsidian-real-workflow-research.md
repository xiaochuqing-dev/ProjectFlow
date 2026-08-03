# ProjectFlow V3.8.0 Obsidian 真实工作流研究

研究日期：2026-08-03

## 结论

ProjectFlow 与 Obsidian 的正确关系是：

- ProjectFlow 负责来源归一化、强事实、项目历程、历史覆盖、冲突和 Evidence 追踪。
- Obsidian 负责长期阅读、个人注释、链接和用户自己的知识组织。
- 默认集成只依赖普通 Markdown 和 Obsidian 官方 URI。
- Advanced URI、Local REST API/MCP、Dataview 和 Bases 都是可选增强。
- ProjectFlow 不控制整个 Vault，不把第三方插件变成历程成立条件，也不通过 Obsidian Git 制造第二条事实链。

## 研究矩阵

| 对象 | 官方来源与 License | 维护状态 | 需要额外插件 | 核心问题和第一层 | 深链接/双向能力 | 高信号反馈或风险 | ProjectFlow 决策 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Obsidian 官方 URI | obsidianmd/obsidian-help；Obsidian 产品非开源，帮助仓库仅作官方文档参考 | 官方持续维护 | 否 | 跨应用打开 Vault、文件、标题或块 | open 支持 vault 名称/ID、file，相对文件可编码 #Heading 或 #^Block；支持 x-callback-url | URI 编码和 Linux 注册有平台差异；绝对 path 会暴露机器路径 | Level 0 默认，只使用 vault 标识和 managed relative file，不写绝对 path |
| Advanced URI | Vinzent03/obsidian-advanced-uri，MIT | 活跃，2026-07-26 有提交 | 是，需关闭 Restricted mode 并启用社区插件 | 打开 heading、block、line、workspace、bookmark，按 frontmatter UID 导航 | obsidian://adv-uri，支持 stable UID、heading、block、workspace、bookmark | 权限可扩展到写入、命令和插件控制；插件缺失时 URI 不工作 | Level 1 可选，仅生成导航 URI；不存在时自动退回官方 URI |
| Local REST API with MCP | coddingtonbear/obsidian-local-rest-api，MIT | 活跃，2026-08-03 有提交 | 是 | 通过本地 HTTPS REST/MCP 读写、搜索、patch、执行命令 | 127.0.0.1:27124，Bearer API key，支持 heading/block/frontmatter patch 和 ifMatch | 权限覆盖整个 Vault，包含删除、命令执行和 Agent 访问；API key 配置成本高 | Level 2 明确 opt-in、loopback、认证、最小权限；V3.8 核心不依赖，不授予 ProjectFlow 事实写权 |
| Obsidian Git | Vinzent03/obsidian-git，MIT | 活跃，2026-08-02 有提交 | 是 | 在 Vault 内 commit、pull、push、history、diff | 可打开 GitHub 文件和历史 | README 明确 mobile highly unstable；Issue #558 报告移动端冲突导致数据丢失；大 repo 有内存和性能限制 | 只作为用户自己的同步选择；ProjectFlow 投影不自动 commit/pull/push，不把其日志当强事实 |
| Dataview | blacksmithgu/obsidian-dataview，MIT | 最近提交 2025-11-17，维护节奏低于其他对象 | 是 | 将 frontmatter/inline fields 建索引并以 DQL/JS 查询 | 通过 query 动态汇总 ProjectFlow managed notes | DataviewJS 能改写/删除文件和联网；Issue #1280 报告 9k notes 时持续高 CPU，#1928 报告不足 700 notes 的全 Vault query 超过 100 秒 | Level 3 可选模板；默认不注入 DataviewJS，不让 Dataview 成为主索引 |
| Obsidian Bases | Obsidian 官方帮助；核心插件 | 官方持续维护 | 不需社区插件，但依赖支持 Bases 的 Obsidian 版本 | 基于 Markdown properties 创建 table/list/card/map 视图 | .base 文件或 Markdown code block，数据仍在本地 Markdown | 老版本或禁用核心插件时不可用；它是视图，不是事实系统 | Level 3 可选视图，普通 Markdown 仍是稳定底座 |

## 官方 URI 的现实能力

### 2026-08-03 独立复核

本轮重新读取官方仓库默认分支和公开 Issue，固定证据如下：

- Obsidian URI 官方帮助 blob SHA：a9780431a62f49f77a8d7daeda455de0960b651a。
- Advanced URI README blob SHA：49e6274d58787efea6bbc000cf921b06c6c1f696。
- Local REST API with MCP README blob SHA：2b406931b281cbd00324a2c3caf07cf32916b739。
- Obsidian Git README blob SHA：6cfe2401bee785f74c3347025ddf7b6c47be5c17。
- Dataview README blob SHA：4e365f3ae7d22df5c9aa9926c0737fe36755dd32。
- Obsidian Git Issue #558 仍开放，复现移动端冲突导致跨文件历史内容丢失。
- Dataview Issue #1280 和 #1928 均仍开放，分别记录约 9,000 notes 的持续高 CPU，以及不足 700 notes 的全 Vault 查询超过 100 秒。

官方帮助站页面若受浏览器权限限制，不绕过权限，改用同一官方 obsidian-help 仓库内容。

官方文档已确认：

- obsidian://open?vault=... 可以打开 Vault。
- file 可以使用 Vault 根目录下的相对路径。
- 编码后的 Note#Heading 可跳到标题。
- 编码后的 Note#^Block 可跳到块。
- vault 可以使用名称或本机 Vault ID。
- path 是绝对机器路径，会覆盖 vault 和 file。

因此零插件方案已足够支持 ProjectFlow → Obsidian 的核心跳转。V3.8.0 不应默认要求 Advanced URI。

默认 URI 规则：

1. 使用用户配置中的 Vault 名称或 Vault ID。
2. 使用 managed root 内相对 Markdown 路径。
3. 需要时把标题或块放在 file 参数内并正确 URI encode。
4. 不把绝对 path 持久化到 ProjectFact、History Event、Snapshot 或投影 frontmatter。
5. URI 生成失败时仍显示普通相对路径和可读 Markdown。

## Advanced URI 的适用边界

Advanced URI 能按 heading、block、line、workspace、bookmark 和 frontmatter UID 导航，也能执行写入、命令、搜索替换和插件操作。

ProjectFlow 只采用导航子集：

- 打开某个变化故事标题或 stable UID。
- 打开某个时间篇章。
- 可选打开预先配置的 workspace 或 bookmark。

拒绝默认采用：

- 写入、覆盖、搜索替换。
- 调用任意 Obsidian command。
- 启用、禁用或更新插件。
- 修改用户 frontmatter。

插件不存在时，生成器必须降级为官方 URI，投影和同步本身不能失败。

## Local REST API / MCP 的适用边界

该插件提供整个 Vault 的 CRUD、binary file、search、command 和 MCP；示例使用 127.0.0.1:27124 与 Authorization Bearer API key，并支持 ifMatch 乐观并发。

这是一条高权限自动化通道，不适合作为默认依赖。若未来启用：

- 必须由用户显式开启。
- 只能连接 loopback。
- Key 只能在本机安全配置中使用，不能进入数据库事实、Markdown、日志或 Git。
- 默认只允许 open/read/query，不允许 delete、command 和全 Vault write。
- 任何写入仍必须遵守 managed root、managed block 和冲突保护。
- 关闭插件或 ProjectFlow 时，普通 Markdown 和官方 URI 仍可用。

V3.8.0 可以记录兼容性合同和可选配置，不把 REST/MCP 变成正式写入主链。

## Dataview 与 Bases

Dataview 的 DQL 能安全查询 frontmatter，DataviewJS 则与其他插件同级，可改写、创建、删除文件或联网。公开 Issue 显示大 Vault 和全 Vault query 可能产生明显 CPU、冻结和百秒级延迟。

因此：

- CORE 投影不含 Dataview 依赖。
- 可选模板优先使用 DQL，不默认生成 DataviewJS。
- 查询必须限定 ProjectFlow managed root 和必要字段。
- Bases 可以作为更低脚本风险的可选核心插件视图。
- 无论 Dataview/Bases 是否存在，Overview、Chapter、Story 和 Thread Markdown 都必须直接可读。

## Obsidian Git 冲突与同步循环

Obsidian Git README 明确移动端实现非常不稳定，受 isomorphic-git、内存、merge strategy 和仓库大小限制。Issue #558 提供了跨 Mac/iOS 冲突后用户内容丢失的复现。

ProjectFlow 必须保持：

- 不自动控制 Obsidian Git。
- 不在投影后自动 commit、pull 或 push。
- 投影写入继续使用 manifest、content hash、临时文件和原子替换。
- ProjectFlow managed note 的变化不重新导入为外部项目强事实。
- 如果 Vault 本身也是项目 Git 仓库，Discovery 识别 projectflow_managed metadata 和 managed root，避免“投影 → Git 事件 → 新 Fact → 再投影”的循环。
- 冲突时保留用户文件，记录 conflict，不用远端或生成内容静默覆盖。

## ProjectFlow → Obsidian

最低可用流程：

1. ProjectFlow History API 返回目标实体的 stable ID、managed relative path 和可用标题。
2. 投影生成项目概览、篇章、变化故事和演变链 Markdown。
3. ProjectFlow 生成官方 obsidian://open URI。
4. 若用户启用 Advanced URI，再生成增强 URI。
5. 若 Vault 未注册、改名或移动，页面显示配置失效并允许用户重新选择；事实层不变化。

## Obsidian → ProjectFlow

每个 managed note 生成不含 Token 的本地详情链接，使用稳定 project ID 和 entity ID，不使用机器绝对路径。建议合同：

- projectflow_project_id
- entity_type
- entity_id
- projectflow_local_url

当 ProjectFlow 未启动时：

- 链接可能暂时无法打开。
- Markdown 的标题、摘要、Evidence 数量、unknown 和来源说明仍完整可读。
- 用户笔记不依赖在线回调才能成立。

ProjectFlow 本地 URL 必须只携带稳定标识，认证继续由本地应用会话处理，不把 Bearer token 放入 Markdown。

## Vault 迁移与旧投影兼容

- Vault 路径、名称和 Vault ID 属于本地集成配置，不属于事实。
- 每次 validate/sync 重新验证 Vault 与 managed root。
- stable entity metadata 优先于文件路径。
- 用户移动或改名 managed note 时，现有 discovery 和 manifest reconciliation 继续识别。
- 旧“项目能力”笔记保留，软件项目继续可读。
- 新主入口增加“项目概览”和“项目历程”，不删除旧 Capability 笔记。
- 迁移只新增或更新 ProjectFlow managed block，不删除用户文件，不重排用户目录。
- manifest 损坏时从 Gateway 和 managed note 恢复，不清空 Vault。

## 最终分级

| Level | 默认性 | V3.8.0 结论 |
| --- | --- | --- |
| 0 普通 Markdown + 官方 URI | 默认 | 必须实现并验收 |
| 1 Advanced URI | 可选 | 实现生成与自动降级，不成为同步依赖 |
| 2 Local REST API / MCP | 高级 opt-in | 只形成安全合同和兼容入口；不作为核心写链 |
| 3 Dataview / Bases | 可选视图 | 可提供安全模板；普通 Markdown 始终可读 |

## 主要来源

- https://github.com/obsidianmd/obsidian-help/blob/master/en/Extending%20Obsidian/Obsidian%20URI.md
- https://github.com/obsidianmd/obsidian-help/blob/master/en/Bases/Introduction%20to%20Bases.md
- https://github.com/Vinzent03/obsidian-advanced-uri
- https://github.com/coddingtonbear/obsidian-local-rest-api
- https://github.com/Vinzent03/obsidian-git
- https://github.com/Vinzent03/obsidian-git/issues/340
- https://github.com/Vinzent03/obsidian-git/issues/558
- https://github.com/blacksmithgu/obsidian-dataview
- https://github.com/blacksmithgu/obsidian-dataview/issues/1280
- https://github.com/blacksmithgu/obsidian-dataview/issues/1928
