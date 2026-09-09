# ProjectFlow V4.0-C GUI 产品化与深层交互迁移

状态：实现与本地验收完成，Draft 交付阶段；不是 V4 正式发布。日期：2026-09-09。

## 目标与基线

保留 V4-B 午夜蓝三栏视觉，把真实 Evolution Thread 核心阅读与全局 Provider 日常管理迁入 V4 Workspace。复用既有历史、Evidence、Model Gateway、安全凭据和 Durable Job，不建立第二套引擎。

开始时实际核对 GitHub、fetch、本地 status 和工作树：

| 对象 | 实际状态 |
| --- | --- |
| 远程 master | V3.10 FINAL，`1712841b77fd1e8146ce4ab6beaf404e5b1f7a53` |
| PR #21 | OPEN / Draft，V4-A IA 合同，Head `d853e6f16d6a63562192eb51548f7b7949c2ffc9` |
| PR #22 | OPEN / Draft，V4-B GUI，Head `7d5f30eff3a05105b7f3f60e07e352d297301ed5` |
| 本轮分支 | `codex/v4.0-c-gui-productization`，由 PR #22 实际 Head 创建 |
| 原工作区 | 本地 master 有用户未提交内容；未在该工作区实施修改 |
| 交付策略 | 独立 worktree；stacked Draft PR 的 base 为 `codex/v4.0-b-gui-first-prototype` |

未合并 PR #21/#22，未重写历史，未创建 Tag/Release，未启动 Electron/Tauri。

## 已完成的用户流程

六个页面继续使用同一侧栏、顶部操作区、午夜蓝表面、山脉/山谷/星球原素材。Real Mode 的账户与页面标记使用“本地用户 / V4 工作区”，显式 Demo 才显示设计示例。

启动欢迎页的主入口已接到 V4 项目库；`/login` 保持既有本地模式跳转，兼容工作台保留独立链接。欢迎文字也改为当前状态、历程和证据交接，不再引导用户逐条确认正常事实。

### History / Evolution Thread

- `/workspace/history?project=<id>` 在“项目阶段”与“演变主线”之间切换，URL 保留项目、篇章、主线、目录页和主题搜索，可刷新或返回目录。
- Chapter 目录复用现有分页 API，每页 20 条；Thread 目录每页 12 条，支持服务端主题筛选，不再只消费 Overview 的少量篇章摘要。
- Thread 详情展示持久化主题、当前结果、真实关联 Story 时间范围、每页 12 条变化与相关 Chapter。没有根据标题推断完成度或成熟度。
- Story 在共享弹窗中展示 Before / Change / After、已有原因、后续结果、冲突与未知；用户可继续进入关联 Thread。
- Evidence 按来源事件渐进展开，事件每页 10 条；不再只读取 Story 的第一个来源。显示 currentness、validation、coverage、truncation、缺失事件与安全 deep link。
- STALE/DEGRADED 保留上次记录；conflict、unknown、coverage gap 分开呈现。读取失败可重试；缺失 Story 会说明当前主线不完整，不填补虚构内容。
- 相关 Chapter 来自实际 `storyRefs` 交集，跨页时显示“当前目录页”的边界，用户可翻页继续查找。没有新增关系推断。

Thread → Story → Evidence 的普通阅读不再依赖旧 History 页面。旧页面保留展示修正、完整来源与审计用途，原历史语义测试未被重写。

### Global Provider 管理

`/workspace/settings` 现在支持既有 Provider 的列表、创建、编辑、默认选择/取消、连接测试、确认删除。基本表单包含名称、服务地址、模型标识、类型与协议；高级设置包含 endpoint override、认证方式/Header/Query Key 名称、请求超时、Temperature、输出上限和能力三态覆盖。

普通编辑完整保留既有参数和 purpose tags。安全 Header 值不回显，默认省略 `safeHeaders` 保留旧值；只有显式选择替换时提交新集合。当前后端没有独立启用/禁用字段，UI 不把默认选择伪装成新的开关。

连接测试复用既有 Gateway 最小结构化任务。HTTP 200 但 `ok: false` 会显示失败；连接测试成功不代表长文本分析质量。保存/删除失败不会关闭成“成功”，原配置继续可见；默认 Provider 的删除保护沿用后端限制。

### 凭据与 Demo 边界

- 沿用现有 API 与 Credential Store；没有新的 Secret API 或存储路径实现。
- 已保存 Key 不会返回给表单，密码框始终从空值开始。留空编辑保留；显式清除/替换或改变已保存凭据的请求目的地需要确认。
- Demo Provider 的创建、编辑、默认选择、测试和删除只改变当前页面内示例；输入值不保留为示例凭据，未发送真实业务请求。
- Demo → Real 读取失败时清除示例项目侧栏，显示真实错误；没有 fallback 到示例。
- 真实 E2E 只使用隔离临时项目与本地固定模型、合成凭据。Windows 下走实际 DPAPI，测试后删除本测试创建的 Provider。数据库与 config directory 同属每轮独立临时根，避免旧库悬空凭据引用。
- 未读取 `micuapikey`，未调用 GPT-5.6 Sol 或其他外部真实模型。真实模型/xhigh smoke 为 `NOT_RUN_NOT_NEEDED`，不能把固定模型测试称为真实 Provider 质量验收。

## 视觉、组件与交互

没有新增大型素材、UI 库或设计系统。拆出 source-owned `WorkspaceDialog`、`ProviderManager`、`HistoryReader`、`WorkspaceStoryDialog`，整合原页面内重复实现；Provider payload 规范化与历史 safe-link 检查共享于既有边界。

原生 `<dialog>` 提供焦点约束、Escape 与返回焦点；修复 React StrictMode 关闭/重开时队列事件误关弹窗。窄屏导航与 Evidence 抽屉将外部区域设为 inert、限制 Tab 并恢复触发器焦点。表单、详情、状态、分页按钮共享现有颜色与尺寸。

截图检查另修复 640px 固定导航仍显示关闭按钮的问题：抽屉状态与 CSS 的 620px 断点一致，放大窗口时退出 modal 并恢复外部可交互区域；保留对应浏览器断言。

浏览器用例覆盖 390、640、1024、1280×801 和 1600 宽度；长中文和无空格引用能换行，Story/Evidence 和高级表单不横向溢出。截图与视觉检查结论见 [Evidence 索引](acceptance-evidence/v4.0-c/README.md)。

## 仍保留的产品边界

| 能力 | 当前结果与保留原因 |
| --- | --- |
| 全局 Provider CRUD | 日常操作迁入 V4；旧 `/settings` 保留重复配置批量清理等兼容操作 |
| 项目级 Provider/Model/Policy | 领域模型没有可靠持久化绑定接口；项目设置显示全局默认与当前边界，没有新增 schema 或伪保存 |
| Obsidian | 仅有仓库内 validate/dry-run/status/sync CLI，没有可用的配置/状态/同步 REST API；V4 展示操作说明与“状态未读取” |
| 项目接入、ZIP、本地绑定 | 继续链接既有项目页，避免在本轮扩展为完整 Onboarding 重建 |
| 登录 | 保留既有入口与本地运行认证语义 |
| History 修正与完整审计 | 保留旧页面；普通 Thread/Story/Evidence 阅读已在 V4 内完成 |
| Desktop Shell | 未开始；不能把 Web 工作区实现称为桌面正式产品 |

Obsidian CORE 默认、opt-in、managed block 和模型零调用边界均保持不变。没有 watcher/daemon。

## 底层变更与安全补丁

没有生产后端 Java、Fact/History/Thread 算法、Flyway schema、Gateway 协议、Durable Job、安全凭据模型或 release workflow 修改。新增前端 API wrapper 仅调用已有 GET/Provider endpoint。后端构建配置仅更新下述安全补丁版本。

现有生产依赖门禁发现 Next 16.3.2 的 Windows/AVIF critical 与 Sharp 0.35.3 的 high 公告。按官方公告定向更新 Next 16.3.4、Sharp 0.35.4 及对应 native packages；保留 React 与其他依赖，未使用 `audit fix --force`。更新后生产审计为 0 漏洞。参考：[Next Windows 公告](https://github.com/vercel/next.js/security/advisories/GHSA-p293-qw3h-jr36)、[Next AVIF 公告](https://github.com/vercel/next.js/security/advisories/GHSA-2xp9-vwfh-vxw4)、[Sharp 0.35.4](https://github.com/lovell/sharp/releases/tag/v0.35.4)。

第一次 GitHub required OSV 检查在 `f65bcc8` 检出继承的 `netty-handler 4.1.136.Final` 两项公告（OSV 计为 1 critical / 1 medium）。核对 Netty 官方公告后，仅将既有 `netty.version` 更新为 `4.1.137.Final`，不改变应用 API 或协议语义。此修复由 required dependency gate 的实际失败触发，不能在 GUI 层消除；后端、PostgreSQL、Gateway、Windows 启动和 CI 随补丁复验。参考：[Netty SNI 公告](https://github.com/netty/netty/security/advisories/GHSA-c4c3-7fpv-j4q5)、[Netty 握手重组公告](https://github.com/netty/netty/security/advisories/GHSA-fccg-mwvh-qqg4)。

## 验证记录

当前验证状态随实际执行更新；最终机器可读结果保存在 `acceptance-evidence/v4.0-c/verification.json`。

| 检查 | 当前观察结果 |
| --- | --- |
| TypeScript / lint | PASS；项目 `lint` 命令为 `tsc --noEmit` |
| Frontend contracts | PASS，63/63 |
| Production build | PASS，Next 16.3.4；最后入口/断点修正后重新构建 |
| Production GUI | 独立生产 GUI 19/19 PASS；最后入口/断点修正后随完整生产 E2E 再验证 |
| 完整前后端 E2E | dev 31/31 PASS；最终生产模式 32/32 PASS，包含最终入口/断点回归 |
| Backend/H2 full suite | Netty 4.1.137 补丁后复验 PASS，717 项、0 failure、0 error、11 项条件跳过；Dogfood 3/3 |
| PostgreSQL 16 / migration | Netty 补丁后复验 PASS，实际运行 7/7（6 项业务/并发约束与 1 项 Flyway）；Failsafe 共 13 项，6 项可选外部评测未启用 |
| Hermes | PASS，10/10 |
| Obsidian | PASS，27/27；5000 Fact no-op 为 0 writes |
| 安全验收产物校验 | PASS，`V380_ACCEPTANCE_EVIDENCE_OK` |
| 生产依赖审计 | PASS，Next 16.3.4 / Sharp 0.35.4，0 vulnerabilities |
| Windows 根启动器 | PASS；从父目录相对调用、重建、真实 V4 入口、正常退出、3000/8080 无监听 |
| GitHub required CI | 首次除 Netty OSV 外全部通过，补丁后待复验；见 ci.json |
| 外部真实模型 | NOT_RUN_NOT_NEEDED，未读取真实 Key 或发起付费调用 |

### 失败与修复记录

1. 首轮生产 GUI 16 项中 14 项通过。两处旧断言暴露 Story dialog 名称与 Key label 关联不一致；恢复故事标题的可访问名称，为密码输入显式关联帮助文本，没有删除断言。
2. 首轮完整 dev E2E 28 项中 22 项通过。5 项弹窗相关失败来自 StrictMode 重复初始化；修复原生 close 事件后定向 10 条 History/GUI 用例全部通过。
3. 本地固定模型服务未实现 Provider 最小 `summary` 响应，造成真实 CRUD 测试的连接检查失败。界面实际正确显示了“结构不兼容”；补齐固定服务的现有协议响应，保留 `ok` 和页面成功断言。
4. 后续 31 项完整测试出现 8 项旧流程失败、23 项通过：先前临时数据库引用旧 config directory 的凭据。改为每次执行同根隔离 DB/secure store，未改安全存储规则或屏蔽测试。
5. 依赖审计发现继承的 1 critical / 1 high，定向官方补丁后恢复 0 漏洞。
6. 人工检查生成截图时发现 640px 导航关闭按钮多余；修复断点一致性，并验证从 390px 抽屉放大到 640px 后不残留 modal/inert。
7. 首次 GitHub OSV job 因 Netty 两项继承漏洞失败；保留 [失败 job](https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/34327290670/job/102387368517)，定向更新官方 `4.1.137.Final`，未修改门禁、忽略公告或跳过扫描。

Backend/H2 的 11 项条件跳过包含 8 项显式外部语义评测、2 项 exact V3.9 旧版本补证、1 项可选 intake benchmark。本轮本地未重复构建旧版本应用，exact V3.9 H2/PostgreSQL 升级由 required CI 独立门禁验证。凭据相关本地回归包括 AiProviderCredentialSecurity 12/12、Credential Migration 4/4、Credential Store 2/2、Windows DPAPI 1/1、Gateway credential boundary 1/1；安全与 H2/Flyway 适用回归均随全量执行。

失败细节只保留归因与计数；不提交包含绝对临时路径、请求体或合成凭据的原始 Playwright trace。Next dev 的 CSP 调试提示不被当作生产行为，也未因此放宽 CSP。

## Windows 与 CI 交付

从工作区父目录相对调用 `Start-ProjectFlow.bat -NoBrowser`，实际执行依赖校验、Next 16.3.4 重建、Java 8080 与前端 3000 启动。`logs/last-embedded-build.json` 记录当前工作树有本地修改，readyAt 为 `2026-09-09T15:53:46.1709395+08:00`。浏览器验证 `/login` 的既有本地跳转、欢迎页主入口、真实空项目库与 Provider 空 Key 表单；无页面错误、无写请求、无 Demo fallback。按 Enter 正常退出，进程返回 0，再次确认 3000/8080 无监听，没有强制终止。此次只证明有本地修改时重建当前树，未把未执行的干净工作区远程同步路径记为本轮通过。

Netty 补丁后的根启动器复验也已通过，readyAt 为 `2026-09-09T16:16:55.4200491+08:00`。再次确认真实 V4 入口、空 Key 表单、零写入、正常退出和端口释放。生成 JAR 中七个 Netty 模块均为 `4.1.137.Final`；两次启动来源分别保存在 Windows Evidence 中。

本轮已推送自己的分支并创建依赖 PR #22 的 [Draft PR #23](https://github.com/xiaochuqing-dev/ProjectFlow/pull/23)。首次 Quality 除 Netty OSV 外全部通过（浏览器 32/32，exact V3.9 H2/PostgreSQL 证明通过），Windows portable 两次运行均通过。Netty 补丁后 required CI 将继续复验；各次实际 Run 保存在 `acceptance-evidence/v4.0-c/ci.json`。

## 未完成项、设计债与下一阶段

本轮核心目标是迁移 Thread 与全局 Provider；项目级绑定、Obsidian GUI 同步、完整 Onboarding、修正/审计 GUI 迁移和 Desktop Shell 明确保留。长详情沿用后端已有有界快照，前端分页控制渲染；未来若遇到真实超大详情需求，再评估服务端详情分页，不在本轮增加第二套索引。

下一步先由 Owner 体验 V4 连续工作路径并决定 Draft 的依赖顺序。可以单独研究 Desktop Shell 如何复用同一 Java Core/API，但本轮没有验证壳、安装器、自动更新或桌面发布条件，不能据此宣称可正式桌面发布。

## 关键文件与证据

- `frontend/src/components/workspace/HistoryReader.tsx`、`WorkspaceStoryDialog.tsx`：Thread/Story/Evidence 阅读。
- `frontend/src/components/workspace/ProviderManager.tsx`、`WorkspaceDialog.tsx`：全局 Provider 与共享弹窗。
- `frontend/src/lib/provider-settings.ts`、`api.ts`、`project-history.ts`：既有 payload/API/safe-link 复用。
- `frontend/e2e/workspace-productization.spec.ts`、`workspace-provider.spec.ts`、`project-history.spec.ts`：UI 与实际 Java 链路。
- [Acceptance Evidence](acceptance-evidence/v4.0-c/README.md)：截图、测试摘要、安全 smoke、Windows 和 CI。
- [Agent Result](../.projectflow/agent-results/20260909-v40c-gui-productization/result.json) 已追加，保留验证边界，不覆盖历史结果。
