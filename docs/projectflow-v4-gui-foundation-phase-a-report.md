ProjectFlow V4.0-A GUI Foundation 阶段报告

日期：2026-09-04

一、基线

2026-09-04 执行 fetch 后，origin/master 为 1712841b77fd1e8146ce4ab6beaf404e5b1f7a53，与本阶段输入基线一致。原 checkout 落后 158 个提交，且包含用户修改和未跟踪文件；为保护现有工作，未对其进行同步、清理或改写。已从最新 origin/master 建立干净工作树，并使用准确分支名 codex/v4.0-gui-foundation-ia。Draft PR #21 已按既定准确标题创建，仍为草稿，未合并。

二、实际工作

V4.0-A 已完成 GUI Foundation 与信息架构的范围冻结和决策归档。生产代码仅包含 AppShell 与 Dashboard 中 V3.9 到 V3.10 的标签修正；未删除既有 route，未改变后端语义契约，也未安装生产依赖。

真实 CI 额外暴露两条 Dogfood 测试对文件时间和分类措辞的脆弱依赖。当前仅在测试层进行确定性修复：固定 fixture 工作树的 mtime，并以 dominant family 与 label 的语义锚定断言。不修改已冻结的 V3.9 evidence。修复后的两条 focused Dogfood 均已通过，其中第二条已单独确认，第一条在组合运行中已通过。

同一轮 CI 的 OSV 锁文件门禁还发现六条当前依赖公告。最小处置保留 Spring Boot 3.5.15，只将同一补丁线的 Tomcat 受管版本覆写为 10.1.59；前端只刷新传递依赖锁，当前解析为 browserslist 4.28.8 与 postcss-selector-parser 6.1.4。package.json、Next、React、Tailwind 和 Autoprefixer 均未变更，也没有运行 npm audit fix 或引入新依赖。

依赖处置前的完整后端验证为 717 项、0 failure、0 error、11 条条件跳过，耗时 6 分 31 秒；ProjectHistoryDogfoodAcceptanceTest 3/3 通过。处置后再次完整验证为 717 项、0 failure、0 error、11 条条件跳过，耗时 6 分 54 秒；依赖树确认 tomcat-embed-core、tomcat-embed-el 与 tomcat-embed-websocket 均为 10.1.59。

前端 npm ci、production audit、lint、build、59/59 contracts 均通过。复用旧 `.e2e-data` 且缺少上一进程内凭据时，第一次普通 Playwright 重跑如实失败为 SECRET_NOT_FOUND；它证明本地测试数据不能跨进程复用 in-memory secretRef。重新创建全新隔离工作树、保证数据库与 in-memory store 同生命周期后，9/9 Playwright 通过。根 Start-ProjectFlow.bat 从工作树外以相对路径重建并启动成功，后端 health 与前端 login 均返回 200，退出后 3000/8080 无监听残留。

本机 Docker Desktop Linux daemon 不可连接，因此没有把本地 PostgreSQL profile 写成已运行。实现 Head 7b25c67e12cf0c2919949753da69003c23360c58 的 GitHub push/PR Quality runs 33871166836/33871171845 已通过，实际覆盖 backend/H2、PostgreSQL、Browser E2E、OSV 锁文件、前端质量、真实 V3.9 升级证明、Hermes、Obsidian 和敏感内容门禁；Windows portable push/PR runs 33871166456/33871171203 也已通过。

最终报告 Head 的重复 Windows PR run 33872093162 又暴露一处门禁时序上限：端口占用 fixture 仅保持 45 秒，而该 runner 的启动检测耗时约 44 秒，fixture 先释放后启动器成功，门禁因此正确报出“外部端口未被拒绝”；同 Head 的 push Windows run 33872089083 已通过。修复只把临时监听保持上限延长到 180 秒，`finally` 仍会在检测结束后立即停止监听，不改变产品运行时。该补丁的最终结果以 Draft PR 当前 checks 为准，不在提交前预写。

三、核心决策

产品信息架构采用 Hybrid 项目中心：全局项目库与全局设置配合项目工作区。进入项目后的第一屏为 Current State。History 保持 Overview、Chapter、Story、Thread、Raw Event、Evidence 的层级，不在首层暴露工程细节。

Agent Context 仅消费持久化且有界、脱敏的数据。Evidence 作为按需下钻层，完整 ID、提交、路径、原始事件与内部诊断不进入普通用户首层。

设计系统方向为 ProjectFlow 自有语义 token、源码拥有组件以及成熟无障碍 headless primitive。Radix 作为默认观察对象，React Aria 作为复杂 collection 的挑战者，Base 作为对照观察；最终依赖选择需由 V4-B 原型证据决定。DESKTOP_SHELL_DECISION = DEFERRED，暂不引入生产 Desktop Shell 依赖。

四、未解决事项

Owner 尚未审核 IA 与产品骨架，所以 READY_FOR_OWNER_REVIEW 不是 FINAL PASS。Tomcat 属性覆写虽处于同一 10.1.x 补丁线，未来升级 Spring Boot 时仍须复核是否可以移除；前端也必须继续保留全锁文件 OSV 门禁，不能只依赖 production-only npm audit。

Electron 与 Tauri 的选择尚无生产决策。若后续需要桌面壳，必须先基于现有 Java、Node、Next standalone 与 loopback runtime 完成可删除的 PoC，再决定是否进入产品实现。

Radix、React Aria 与 Base 的比较仍需针对真实复杂 collection、无障碍行为、源码拥有边界和可移除性提供 V4-B 原型证据。

五、Owner 审阅项

请确认 Hybrid 项目中心、Current State 首屏、History 信息层级、Agent Context persisted-only 边界和 Evidence 按需下钻策略。

请确认设计系统方向，以及在 V4.0-A 不引入生产依赖、将桌面壳决策延后至可删除 PoC 的限制。

请确认旧入口保留并仅作渐进降级的原则。Draft PR #21 在上述事项确认前保持草稿且不得合并。

六、V4-B 进入建议

建议仅在 Owner 确认上述产品边界后进入 V4-B。V4-B 应先完成聚焦原型，验证复杂 collection 的无障碍与源码拥有策略，并为桌面壳保留可删除的运行时 PoC；不得以本阶段结论直接扩张为最终视觉重建或生产 Desktop Shell。

状态：V4.0-A GUI FOUNDATION = READY_FOR_OWNER_REVIEW
