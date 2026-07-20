# ProjectFlow V3.4.4 Obsidian Projection / Sync 实施报告

实施日期：2026-07-20

1. 主意图：PASSED。ProjectFlow 将最有长期阅读、关联和复用价值的项目记忆投影到 Obsidian，同时保持 ProjectFlow 为唯一事实来源。

2. 为什么不是数据库镜像：PASSED。CORE 文件量随月份和长期能力增长，不随 Fact 数量一比一增长；不导出表、job、diff、源码、Agent 原文或模型诊断。

3. Projection architecture：PASSED。仓库内 Python CLI 执行 Gateway collect → deterministic desired notes → sync plan → managed-block/manifest conflict check → atomic execution。

4. Gateway reuse：PASSED。Projection 只调用 V3.4.3 Project Memory Gateway 的 Snapshot、Timeline、Capability 和 Evolution read models，没有新建 Repository 读取或第二套业务拼装。

5. default CORE profile：PASSED。默认输出 Overview、月度 Timeline、长期 Capability、月度 Fact Index 和三个索引；EXTENDED/FULL_FACTS 必须显式选择。

6. Overview note：PASSED。包含项目定位、事实/历史覆盖、真实变化范围、生命周期摘要、主要能力、最近事实/演进、attention 和导航。

7. Timeline notes：PASSED。按月展示既有 summary、deterministic stats、themes、事实、能力变化与 history warning，只按 eventAt/occurredAt 归属。

8. Capability notes：PASSED。包含稳定 ID、别名、问题/价值、确定性成熟度、时间、版本/计数、chronological evolutions、代表事实、Fact Trace 和可复用表达；内部枚举已中文化。

9. Fact index：PASSED。CORE 以月文件集中展示时间、标题、摘要、状态、稳定 Fact ID、batch、相关能力和追溯引用。

10. high-value Fact notes policy：PASSED。EXTENDED 只选择 evolution 引用或 NEEDS_ATTENTION Fact；FULL_FACTS 才为全部 Fact 建独立 Note。

11. frontmatter：PASSED。所有 Note 含 managed/project/entity/source version/content hash/generated/source updated/projection version；Timeline 与 Capability 补充专属字段。

12. managed root：PASSED。仅接受现有 Vault 下的安全相对专用目录；未寻找或触碰用户真实 Vault。

13. managed block：PASSED。只替换 `PROJECTFLOW:BEGIN/END` 之间内容，新 Note 预留“我的笔记”。

14. user content preservation：PASSED。测试证明未知用户 frontmatter 与 managed block 外笔记在来源更新后保持内容完整。

15. conflict policy：PASSED。managed hash、markers、entity/project identity 和重复实体异常均拒绝覆盖；manifest 与 side file 记录冲突，用户恢复原内容后冲突可清除。

16. path safety：PASSED。managed root/Note 逐层 containment 校验，禁止绝对路径、`..`、root escape 和管理范围外写入。

17. filename safety：PASSED。NFKC、非法字符、尾随点/空格、Windows 设备名与 32 位稳定实体 slug 共同处理 Unicode、大小写及同前缀碰撞。

18. symlink / traversal：PASSED。Windows junction 实测被拒绝；Linux CI symlink 分支通过；traversal 测试通过。

19. manifest：PASSED。managed root 内原子维护 project/profile/entity/path/version/hash/generation/redirect/conflict，更新前备份；不作为事实源。

20. atomic writes：PASSED。temp → flush/fsync/close → `os.replace`；模拟磁盘错误不留下目标文件或临时残片。

21. incremental sync：PASSED。只更新 body/hash 实际变化或链接受路径影响的 Note，不删除目录、不全量重建。

22. dry-run：PASSED。真实 CLI/Gateway dry-run 返回完整 plan，managed root 不创建、写入为 0。

23. sync plan：PASSED。CREATED、UPDATED、UNCHANGED、REDIRECTED、ARCHIVED、CONFLICT、ERROR 均有确定性计数与 item reason。

24. no-op：PASSED。fixture、5000-fact 规模测试和当前项目安全副本最终同步均为 0 writes / 0 bytes。

25. rename：PASSED。Capability rename 及用户 move 通过实体元数据保留既有 Note 路径；只有受链接变化影响的索引更新。

26. merge redirect：PASSED。旧 Capability Note、稳定 ID 与历史保留，写入 merged state/target/link；manifest 记录 redirect。

27. interrupted recovery：PASSED。注入中断后只有完整原子 Note、无半文件；重跑恢复完整 projection。

28. manifest recovery：PASSED。破损 JSON 被检测并由 Gateway + Note 元数据重建，已有 10 个 Note 全部零重写。

29. 7/17 → 8/20 time test：PASSED。7 月 17 日发生、8 月 20 日记录/分析的 Fact 只进入 2026-07 Timeline/Fact Index，不创建 2026-08。

30. 5000 facts CORE performance：PASSED。36 months、100 capabilities、1000 evolutions、Gateway 既有 10000 relations 规模下生成 176 个 Markdown；最终测量 first sync 539.4 ms、177 writes、3040389 bytes，no-op 0 writes，未生成 5000 文件。

31. current ProjectFlow safe-copy Vault output：PASSED。当前 H2 安全副本含 68 facts、18 active capabilities；最终代码在受控空 Vault 生成 26 个 CORE Note，first sync 960 ms/27 writes/140095 bytes，no-op 779 ms/0 writes，只含 2026-06 与 2026-07。人工核对 Overview、2026-07、一个 Capability、Evolution trace 与 Fact Index。

32. human-readable quality：PASSED。人工检查可从 Overview 理解范围和主要能力，从 July 阅读主题/事实，从 Capability 追踪月度与 Fact；修复了演进内部枚举和“来源事实”多余量词。

33. no model re-generation：PASSED。Projection 代码无 ModelGateway/Provider/模型 HTTP；CLI、Gateway E2E、profiles 与 no-op 全程只读既有 read models。

34. security：PASSED。repository staged diff 与 git sensitive scan 未发现 Key、Bearer、真实 Vault、用户绝对路径、raw prompt/response/reasoning 或临时 fixture；只接受 loopback backend。

35. tests：PASSED。Obsidian 18/18、Hermes MCP 5/5、backend/H2 302/302、frontend contracts 44/44、Playwright 7/7；TypeScript、production build、py_compile 和 diff check 通过。

36. H2：PASSED。完整 H2/旧库升级测试在 302 项后端套件通过；当前库安全副本成功启动。Desktop BAT 打开 H2 后产生文件字节变化，进程退出后从预先 byte-identical 备份恢复；原始 SHA-256 最终仍为 `489D059BB2B9D7BCB8133BF47E5FE593F9A39994B35A8C502FB2E43829436E0D`。

37. PostgreSQL：PASSED（CI）；本地 BLOCKED。Windows Docker daemon 未运行，未修改系统配置；CI `postgres-integration` 在 PostgreSQL 16 Testcontainers 中通过。

38. CI：PASSED。实现提交 `daa814e2313c57b924d89167d5f6d2144c11c93f` 的 run `29731785408` 全绿：backend/H2、PostgreSQL、Obsidian、Hermes、frontend、browser、sensitive；optional real DeepSeek 按触发条件 SKIPPED。URL：https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/29731785408

39. known risks：CORE 单月 Fact Index 仍会随极端单月事实数增大；网络盘/远程 Vault 原子语义未承诺；FULL_FACTS 可显式产生大量文件；duplicate entity conflict 需要用户消除；自动 watcher/前端配置未实现；CI action 有 Node 20 deprecation warning，npm audit 报告 2 moderate/2 high 既有依赖风险。

40. final commit：PASSED。V3.4.4 implementation commit 为 `daa814e2313c57b924d89167d5f6d2144c11c93f`，已推送 GitHub master；本报告与 Agent result 由后续 release-evidence commit 保存。

41. report path：PASSED。`docs/projectflow-v3.4.4-obsidian-projection-sync-report.md`。

42. next stage：PASSED。下一阶段明确为 backend business / logic consolidation；完成后再进行 full frontend rebuild。
