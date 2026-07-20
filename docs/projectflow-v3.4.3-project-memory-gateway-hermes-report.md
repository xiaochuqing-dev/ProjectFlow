# ProjectFlow V3.4.3 Project Memory Gateway 与 Hermes MCP 实施报告

日期：2026-07-20

总体状态：PASSED

1. 主意图：PASSED。Facts、Timeline、Capabilities、Evolutions 已形成统一只读业务语义层；Hermes 通过该层即时查询，ProjectFlow 继续是唯一事实来源。

2. 不直接暴露全部内部 REST：PASSED。内部 API 包含生成、重试、兼容和页面 DTO，直接交给 Agent 会泄漏复杂度并增加误写风险；Gateway 只暴露九类稳定用户任务语义。

3. Project Memory Gateway：PASSED。新增 `ProjectMemoryGatewayService`、专用 DTO、Controller 和安全读取审计，不改 Fact/Timeline/Capability 核心写链。

4. Snapshot：PASSED。返回项目定位、事实覆盖、真实发生范围、最新期间、代表能力、近期演进、状态、新鲜度和 warnings；5000-fact P50/P95 为 50/57 ms，38 queries，16157 bytes。

5. Recent Changes：PASSED。按 `occurredTo`/`occurredFrom` 过滤排序，支持 attention、分页和 detail level；P50/P95 1/2 ms，6 queries，15939 bytes。

6. Unified Memory Search：PASSED。FACT、TIMELINE_PERIOD、TIMELINE_THEME、CAPABILITY、EVOLUTION 明确标识 SOURCE/DERIVED 与 matchedFields；P50/P95 47/55 ms，10 queries，12026 bytes。

7. Timeline Query：PASSED。支持 DAY/WEEK/MONTH/LIFECYCLE；失败摘要仍返回事实、确定性统计和 stale 上次成功内容。月 P50/P95 15/16 ms，14 queries，80258 bytes；lifecycle 18/23 ms，14 queries，17266 bytes。

8. Capability Query：PASSED。稳定 ID、aliases、确定性 maturity/reasons、形成/增强时间、证据统计、stale 与 merge redirect 可读；P50/P95 1/1 ms，3 queries，38780 bytes。

9. Evolution Query：PASSED。按发生时间和版本升序输出事实、期间和 merge 来源；P50/P95 1/2 ms，5 queries，8677 bytes。

10. Fact Trace：PASSED。返回有界 batch、commit、仓库相对文件、Agent result、evidence 与 capability 关系；P50/P95 1/7 ms，10 queries，734 bytes。

11. Project Brief：PASSED。2000-12000 字符硬预算，返回定位、生命周期、近期事实、能力、演进、历史期间、attention 和 coverage；P50/P95 35/55 ms，46 queries，1168 bytes。

12. 时间语义：PASSED。`occurredAt` 是发生时间，`recordedAt` 是持久化时间，`analyzedAt` 是分析时间，外部 `syncedAt` 是投影时间；Recent 和 Timeline 只用发生时间。

13. 7/17 → 8/20 回归：PASSED。测试事实发生于 2026-07-17、分析/记录于 2026-08-20，Recent July 与 Timeline 2026-07 均命中，August 不命中。旧 sediment 有 source batch 时改用 batch 事实窗口并立即分配 Timeline。

14. MCP transport：PASSED。采用 MCP stdio JSON-RPC，UTF-8 newline framing，stdout 仅协议；支持 initialize、ping、tools/list、tools/call。

15. local stdio：PASSED。仓库内 Python 3 标准库实现，无安装、无系统配置；`run-projectflow-mcp.ps1` 提供便携入口。

16. remote boundary：PASSED。V3.4.3 明确拒绝非 loopback URL；远程 transport/auth、Telegram 均未伪装实现。

17. tool list：PASSED。共 9 个：list_projects、get_project_snapshot、search_project_memory、get_recent_changes、get_project_timeline、list_project_capabilities、get_capability_evolution、trace_project_fact、get_project_brief。

18. tool descriptions：PASSED。每个工具说明适用问题、事实/派生语义和边界；真实 Hermes 成功发现 9 个工具。

19. compact output：PASSED。compact 默认，detailed 显式请求，brief/result bytes 均有硬限制。

20. pagination：PASSED。Fact/Search/Timeline/Capability/Evolution 均使用 zero-based page 和 1-100 size；大结果测试验证 page=2、size=7 透传与幂等。

21. read-only permissions：PASSED。全部工具标注 readOnly=true、destructive=false、idempotent=true、openWorld=false，无 create/update/delete/merge/shell 工具。

22. auth/scope：PASSED。Controller 统一解析当前用户，所有 project/fact/capability 读取同时校验 userId/projectId；跨项目返回安全 not-found。可选 token 只在进程环境转发。

23. audit：PASSED。记录 operation、数量、耗时、状态、caller hash、query length/hash、entity/filter；项目删除同步删除 audit。审计失败不改变项目记忆。

24. privacy：PASSED。不返回/持久化 diff、绝对路径、fingerprint、完整 query/caller、Key、Authorization、prompt、raw response 或 reasoning。

25. failure handling：PASSED。backend unavailable、timeout、HTTP/ownership、invalid JSON、oversize 与 remote disabled 有机器可读 code/retryable/status，不泄漏 token。

26. restart：PASSED。连续重启 stdio process 均恢复 9 工具和相同数据；6 路并发读取 max_active=6，全部成功。

27. performance：PASSED。规模为 5000 facts/36 months/100 capabilities/1000 evolutions/10000 relations。MCP startup+discovery 142.3 ms，单次 tool call 146.0 ms，6 并发 265.0 ms；Gateway 各入口 P50/P95、query、bytes 见第 4-11 项，无默认一事实一输出对象问题。

28. real Hermes/MCP E2E：PASSED。Hermes Agent v0.18.2 在隔离 `HERMES_HOME` 注册 repository-local server，`hermes mcp test` 172 ms 连接并发现 9 工具。两轮真实 deepseek-v4-pro one-shot 均 completed=true/failed=false；配置、Key、session 数据均只在 ignored target/process 中，未提交。

29. sample questions：PASSED。真实提问覆盖“2026 年 7 月发生什么”“FactCursor 为什么形成”“是否已有 Obsidian 正式同步”。Hermes 只调用 ProjectFlow 工具并标注工具名。

30. answer quality：PASSED。7 月回答按 occurredAt 排序并列出 v3.4.0/1/2；FactCursor 回答引用 fact ID、实体/Repository 代码证据和 cursor 成功边界；Obsidian 搜索为 0 时明确“尚无正式同步”，没有臆测能力或把派生摘要当事实。

31. security scan：PASSED。本地 cached source 扫描和 GitHub sensitive-content 均无真实 Key、Bearer、Hermes credential、Vault/user 绝对路径、prompt/response/reasoning、真实 DB/Vault/MCP secret。文档仅占位符。

32. tests：PASSED。完整 backend 302/302；Gateway 5/5；5000 规模性能 1/1；frontend contracts 44/44；TypeScript、production build；Playwright 7/7；MCP 5/5。Windows 测试发现后台 Git handle 导致临时目录 EPERM，测试工具仅对 Windows EPERM/EBUSY 延后清理，Linux 严格删除，完整重跑通过。

33. H2：PASSED。完整 H2、文件型升级链和当前真实 H2 byte-identical safe copy 均启动成功。真实库 68 facts、18 active capabilities；原始 `projectflow.mv.db` 启动前后 SHA-256 均为 `489D059BB2B9D7BCB8133BF47E5FE593F9A39994B35A8C502FB2E43829436E0D`。

34. PostgreSQL：PASSED。Windows 本地因 Docker daemon 不可用在容器初始化前 BLOCKED，未改系统配置；GitHub Actions `postgres-integration` 在 Linux Docker/PostgreSQL 16 Testcontainers 中 PASSED。

35. CI：PASSED。最终 V3.4.3 commit `05e133ef31366a5b222d031bc43dddce4a0804ac` 的 run `29728864679` 全绿：backend/H2、PostgreSQL、frontend、browser、Hermes MCP、sensitive；optional real DeepSeek 按设计 SKIPPED。URL：https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/29728864679

36. known risks：Gateway search 是有界字段候选，不是全文搜索引擎；remote MCP/auth 尚未实现；audit 仍依赖 Hibernate ddl-auto；Hermes 最终措辞依赖外部宿主模型，但其输入有界可追溯；本阶段没有 Obsidian 投影。

37. final commit：实现提交 `31f0d2867e2eddea01a9cab7ae761fe356f80959`；契约收尾提交 `05e133ef31366a5b222d031bc43dddce4a0804ac`。二者已推送 master，后者 CI PASSED。

38. report path：`docs/projectflow-v3.4.3-project-memory-gateway-hermes-report.md`。
