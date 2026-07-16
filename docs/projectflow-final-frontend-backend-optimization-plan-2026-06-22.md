# ProjectFlow Frontend and Backend Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 合并 DeepSeek、GLM 和 Codex 三轮审查结果，形成一份基于当前代码事实的最终前后端优化方案，重点减少死代码、重复代码、杂乱边界和安全风险，同时不破坏 ProjectFlow 的核心业务闭环。

**Architecture:** 先清理低风险死代码和误用，再补安全护栏，最后分阶段拆解过大的前后端核心文件。前端以共享 UI/Hook 收敛重复，后端以安全边界和业务服务拆分收敛复杂度。

**Tech Stack:** Frontend: Next.js App Router, React, TypeScript, Tailwind CSS. Backend: Spring Boot 3.5.x, Java 17, Spring Data JPA, JJWT, Maven.

---

## 1. 审查来源与合并规则

本方案综合以下三类结果：

| 来源 | 覆盖范围 | 采纳方式 |
| --- | --- | --- |
| `docs/DeepSeek前后端审查.md` | 前端死代码、重复 UI、后端死代码、事务误用、异常处理、分层问题 | 大部分采纳；对 `api.ts` 导出项按“未被页面使用”而非“编译死代码”处理 |
| `docs/GLM前端审查.md` | 前端静态安全、tsc 死代码、UI 重复模式、localStorage 风险 | 采纳；其“tsc 只报 4 处死代码”和 DeepSeek 不冲突 |
| Codex 本轮复核 | 当前代码行数、后端安全边界、`ProjectIntelligenceService` 大泥球、SSRF/JWT/zip/path guard | 作为最终优先级依据，补齐 DeepSeek/GLM 未覆盖的后端安全项 |

合并时采用以下规则：

1. 能被当前代码 `rg`/行数扫描验证的，列入正式优化项。
2. 与产品主链路有关的风险优先于纯代码洁癖。
3. `api.ts` exported 但未被页面 import 的函数不等同于 TypeScript unused local，先按“产品链路未使用导出”处理。
4. 旧材料/建议接口属于兼容层候选，不允许直接删除，必须先确认前端、测试和历史数据链路。
5. 本地路径、zip 上传、模型 URL 是 ProjectFlow 核心能力，不能为了安全直接砍掉，必须加 guard。

---

## 2. 当前代码事实

### 2.1 前端事实

| 文件 | 当前状态 | 结论 |
| --- | --- | --- |
| `frontend/src/app/dashboard/page.tsx` | 1548 行 | 工作台页仍是最大前端文件，应拆组件和 hook |
| `frontend/src/lib/api.ts` | 1120 行 | API 封装过大，且存在一批页面未使用导出 |
| `frontend/src/app/tasks/page.tsx` | 686 行 | 旧变更编辑和新详情页职责重叠 |
| `frontend/src/lib/project-insights.ts` | 642 行 | 规则集中，暂不优先拆，先保护测试 |
| `frontend/src/app/ai-review/page.tsx` | 484 行 | 来源展示和输出编辑职责混杂 |
| `frontend/src/app/project-intelligence/page.tsx` | 454 行 | 项目档案入口、编辑、分析记录入口混杂 |
| `frontend/src/app/dev-logs/page.tsx` | 419 行 | 每日回顾编辑与来源展示重复 |

已验证的前端问题：

- `frontend/src/app/tasks/page.tsx` 仍存在 `sourcePreview`。
- `frontend/src/lib/api.ts` 仍导出 `createTask`、`updateTaskStatus`、`createTextMaterial`、`uploadProjectMaterialFile`、`uploadProjectZip`、`analyzeProjectMaterial`、`listProjectSnapshots`、`updateWorkSession`、`listProjectAgentSignatureFeedback`、`writeAgentTaskBrief`。
- `frontend/src/components/ui/primitives.tsx` 仍导出 `Stat`、`Field`。
- `frontend/src/components/ui/toast.tsx` 仍导出 `LoadingBar`。
- `ai-review`、`dev-logs`、`tasks`、`project-intelligence` 仍有内联 toast。
- `dev-logs` 和 `ai-review` 仍各自定义 `SourceCardList`。
- `dev-logs` 和 `tasks` 仍各自定义不同签名的 `StatusPill`。
- 多页面仍手写 `readSession()` 和项目选择逻辑。

### 2.2 后端事实

| 文件 | 当前状态 | 结论 |
| --- | --- | --- |
| `backend/src/main/java/com/projectflow/service/ProjectIntelligenceService.java` | 2282 行 | 后端最大复杂度来源，必须分阶段拆 |
| `backend/src/main/java/com/projectflow/service/ProjectAgentBridgeService.java` | 707 行 | 文件系统协议、扫描、解析、落库混在一起 |
| `backend/src/main/java/com/projectflow/dto/V2ProjectDtos.java` | 406 行 | DTO 聚合过大 |
| `backend/src/main/java/com/projectflow/service/WorkSessionScanService.java` | 388 行 | Git 扫描可用，但路径 guard 应统一 |
| `backend/src/main/java/com/projectflow/service/AiOutputService.java` | 382 行 | 输出模板继续膨胀风险高 |
| `backend/src/main/java/com/projectflow/controller/ProjectIntelligenceController.java` | 301 行 | 材料、画像、分析、建议、变更、档案接口都在一个 Controller |

已验证的后端问题：

- `ImportRecord.rawMarkdown` 有字段和 getter，但响应 DTO 不返回，业务侧未读。
- `AiSuggestionRepository.findByProjectIdAndStatusOrderByCreatedAtDesc` 未被 service 调用。
- `ProjectContextSyncService.sync()` 是 `@Transactional(readOnly = true)`，但执行 `Files.writeString`。
- `AiProviderService.test()` 在 read-only 事务内做最长 12 秒 HTTP 调用。
- `WorkSessionScanService.parseInstant()` 和 `MarkdownImportService.parseDate()` catch `RuntimeException` 过宽。
- `ProjectAnalysisJobRunner.execute()` 失败时只保存简短错误，没有日志堆栈。
- `EvidenceBundleService.toResponse()` 和 `EvidenceDraftChangeService.draftChange()` 在运行期重复调用 schema repair。
- `WorkSession`、`EvidenceBundle`、`AgentSignatureFeedback` 实体依赖 `V2ProjectDtos` 响应 DTO。
- `AiOutputService` provider 字段硬编码 `"mock-provider"`。
- 所有 Controller 大量手写 `@RequestHeader Authorization`，没有集中式 JWT Filter。
- `application.yml` 默认 JWT secret 是公开占位值。
- AI Provider `baseUrl` 被拼入 `URI.create(provider.getBaseUrl() + "/chat/completions")`，需要 SSRF guard。
- 上传默认上限为 512MB，zip 扫描缺少总读取预算。
- 多个 service 各自实现 `Path.of(projectPath).toAbsolutePath().normalize()`。

---

## 3. 最终优先级

### P0：立即可做的低风险清理

目标：删除或收敛确定性死代码，不改变主流程。

- [ ] 删除 `frontend/src/app/tasks/page.tsx` 的 `sourcePreview`。
- [ ] 删除或重新接线 `frontend/src/app/tasks/page.tsx` 中未使用的旧变更保存逻辑：`savingChange`、`handleSaveChange`、旧 `updateProjectChange` 列表页保存入口。
- [ ] 将 `frontend/src/lib/api.ts` 中 10 个未被页面使用的导出先标记为 deprecated；确认无测试和未来页面依赖后再删除。
- [ ] 处理 `frontend/src/components/ui/primitives.tsx` 的 `Stat`、`Field`：优先在页面中使用；确认不需要再删除。
- [ ] 处理 `frontend/src/components/ui/toast.tsx` 的 `LoadingBar`：优先替换各页面内联 loading bar；如果不采用则删除。
- [ ] 将 `frontend/src/lib/project-insights.ts` 的 `parseZipDirectoryTree` 改为非导出内部函数。
- [ ] 删除 `frontend/src/components/ui/toast.tsx` 的无用 `ReactNode` re-export。
- [ ] 删除 `backend/src/main/java/com/projectflow/repository/AiSuggestionRepository.java` 的未用查询方法。
- [ ] 明确 `ImportRecord.rawMarkdown` 用途：如果不展示原始导入内容则删除字段；如果未来要溯源则加入响应和详情页。
- [ ] 移除 `EvidenceBundleService` / `EvidenceDraftChangeService` 运行期重复 schema repair，仅保留启动修复或迁移脚本。

验收：

- `cd frontend && npm.cmd run build`
- `cd frontend && npx.cmd tsc --noEmit --noUnusedLocals --noUnusedParameters`
- `cd backend && mvn -q test`

### P1：安全护栏

目标：开源后不会因为默认配置或用户输入边界造成高风险。

- [ ] 增加 JWT secret 启动校验。
  - 非 test profile 下，如果 `JWT_SECRET` 未设置或等于 `replace-with-at-least-32-random-bytes`，启动失败。
  - 开发模式如需自动生成内存密钥，必须明确日志提示“重启失效，不可生产使用”。
- [ ] 增加 AI Provider URL guard。
  - 只允许 `https`。
  - 本地开发显式允许 `http://localhost` 和 `http://127.0.0.1`。
  - 默认拒绝 metadata / loopback / private network IP 段，除非显式 local-only 配置。
  - `AiProviderService.test()` 和模型调用共用同一套 URL 校验。
- [ ] 提取 `LocalProjectPathGuard`。
  - 统一处理空路径、根目录、系统目录、用户主目录、路径不存在、是否必须 `.git`。
  - 替换 `ProjectAgentBridgeService`、`ProjectContextSyncService`、`WorkSessionScanService`、`ProjectIntelligenceService` 中的重复路径解析。
- [ ] 增加 zip 上传预算。
  - 下调默认上传上限到 64MB 或 100MB。
  - 在 `scanZip` 中累计读取字节数，超过预算停止并返回用户可理解错误。
  - 显式检查 `MultipartFile.getSize()`。
- [ ] 前端 `readSession()` 增加 JWT exp 检查。
  - token 过期时清理 localStorage 并跳转登录。
- [ ] 增加 CSP。
  - 在 Next headers 配置基础 CSP，至少限制 `default-src 'self'`，根据 API 与 dev server 补必要例外。

验收：

- 未设置 JWT_SECRET 的非测试启动失败。
- Provider URL 指向 `http://169.254.169.254`、`http://127.0.0.1` 在非本地允许模式下被拒绝。
- 过大 zip 返回明确错误，不造成长时间卡死。
- 本地路径 guard 对根目录、Windows、Program Files、普通项目路径返回一致结果。

### P2：前端共享层收敛

目标：减少页面重复，避免继续出现“按钮像按钮但不可点击”“项目切换后局部不刷新”等回归。

- [ ] 建立 `useProjectSelection`。
  - 统一 `readSession`、`listProjects`、`resolveSelectedProjectId`、`rememberSelectedProjectId`。
  - 统一 query projectId 优先、localStorage 兜底、项目删除后的回退。
  - 替换 `tasks`、`dev-logs`、`ai-review`、`project-intelligence`、`settings`、`imports`、长期资源页的重复项目选择逻辑。
- [ ] 建立 `useAutoDismissNotice`。
  - 替换 6 个页面重复的 error/notice 定时清理逻辑。
- [ ] 全面使用共享 `Toast`。
  - 替换 `ai-review`、`dev-logs`、`project-intelligence`、`tasks` 的内联 toast。
- [ ] 全面使用 `ProjectContextBar`。
  - 替换 6 个页面内联项目选择器。
- [ ] 全面使用 `PageContainer`。
  - 替换长期资源页和详情页重复 wrapper。
- [ ] 合并 `SourceCardList`。
  - 创建 `frontend/src/components/sources/SourceCardList.tsx`。
  - `ai-review` 和 `dev-logs` 共用。
- [ ] 合并 `StatusPill`。
  - 优先改为共享 `Badge`，不要继续新增本地副本。
- [ ] 合并 `firstMeaningfulText` / `firstUsefulLine`。
  - 创建 `frontend/src/lib/text-summary.ts`，提供 `firstUsefulLine`。
- [ ] `work-sessions/[sessionId]` 改用共享 `Card` / `Stat`。

验收：

- `ProjectContextBar` 是项目选择的唯一 UI 入口组件。
- 页面内不再出现重复的 fixed bottom toast。
- `SourceCardList` 只有一个实现。
- `StatusPill` 本地副本删除。
- `npm.cmd run build` 通过。

### P3：前端大页面拆分

目标：降低工作台和任务页维护风险。

- [ ] 拆 `frontend/src/app/dashboard/page.tsx`。
  - `components/dashboard/ProjectAccessCard.tsx`
  - `components/dashboard/EvidenceFlowPanel.tsx`
  - `components/dashboard/ActivityFeed.tsx`
  - `components/dashboard/ArchitectureQuickEntry.tsx`
  - `components/dashboard/FlowGuideDialog.tsx`
  - `hooks/useDashboardWorkspace.ts`
- [ ] 拆 `frontend/src/app/tasks/page.tsx`。
  - 列表页只保留候选列表、筛选、批量操作、进入详情。
  - 结构化变更完整编辑只保留在 `/project-changes/[changeId]`。
  - `Payload JSON` 只允许作为折叠的高级调试区，默认不出现在主流程。
- [ ] 拆 `ai-review` 来源面板和输出编辑器。
  - 输出生成页只负责选择输出类型、生成、复制/下载。
  - 来源材料卡片复用共享来源组件。
- [ ] 拆 `project-intelligence` 项目档案入口。
  - 档案入口、手动修正、分析记录入口分离为组件。

验收：

- `dashboard/page.tsx` 控制在 800 行以内。
- `tasks/page.tsx` 控制在 450 行以内。
- 用户主链路不变：导入项目 -> 看到画像 -> 绑定路径 -> 开发一天 -> 回来看变化 -> 审查 -> 生成输出。

### P4：后端服务边界拆分

目标：拆掉 `ProjectIntelligenceService` 大泥球，同时不重写业务。

- [ ] 抽 `ProjectZipScanService`。
  - 接管 `scanZip`、`readSafeZipText`、`sanitizeIndexedContent`、`shouldSkipZipEntry`、`buildProjectProfile` 相关 zip 规则。
  - 同时承接 zip 上传预算和敏感内容脱敏测试。
- [ ] 抽 `ModelGatewayService`。
  - 接管 `HttpClient`、重试、URL guard、模型 JSON 提取、错误脱敏。
  - `AiProviderService.test()` 和 `ProjectIntelligenceService` 共用。
- [ ] 抽 `ProjectMemoryService`。
  - 接管 `initialMemory`、`updateMemory`、`applyAcceptedChangeToMemory`、`recordFactSource`。
  - 为事实来源历史事件预留扩展点。
- [ ] 抽 `ProjectChangeReviewService`。
  - 接管 `listChanges`、`getChange`、`updateChange`、`acceptChange`、`ignoreChange`。
  - 旧 Suggestion 兼容调用集中在这里。
- [ ] 抽 `ProjectAnalysisRecordService`。
  - 接管分析记录列表、详情、删除。
- [ ] 抽 `ProjectMaterialService`。
  - 接管文本材料、文件材料、材料详情。
- [ ] `ProjectIntelligenceController` 按业务拆 Controller。
  - `ProjectMaterialController`
  - `ProjectAnalysisController`
  - `ProjectChangeController`
  - `ProjectMemoryController`

验收：

- `ProjectIntelligenceService.java` 控制在 800 行以内。
- 每个新服务有明确单一职责。
- 旧端点不破坏，先保持 API 兼容。
- 后端全量测试通过。

### P5：后端分层和事务修复

目标：减少隐性运行时风险。

- [ ] 修复 `ProjectContextSyncService.sync()` 的事务标注。
  - 如果只读 DB 后写文件，则不要标 `readOnly = true`，或拆成只读查询 + 文件写入。
- [ ] 修复 `AiProviderService.test()` 的事务边界。
  - 只在事务内读取 provider，HTTP 调用在事务外执行。
- [ ] `ProjectAnalysisJobService.startProjectAnalysis/startFileAnalysis` 增加事务边界或确保 save 后异步执行可见。
- [ ] `ProjectAnalysisJobRunner` 失败时写日志堆栈。
  - 用户响应仍使用脱敏短消息。
  - 服务端日志记录 exception stack。
- [ ] `parseInstant` 只 catch `DateTimeParseException`。
- [ ] `parseDate` 只 catch `DateTimeParseException`。
- [ ] 实体不再依赖 DTO。
  - 移除 `WorkSession.toResponse()`、`EvidenceBundle.toResponse()`、`AgentSignatureFeedback.toResponse()` 中对 `V2ProjectDtos` 的依赖。
  - 转换逻辑移到 service 或 mapper。

验收：

- 异步分析失败时日志能定位具体堆栈。
- 无 `readOnly=true` 方法执行外部 HTTP 或文件写入。
- entity 包不 import dto 包。

### P6：旧链路收敛和产品语义统一

目标：让核心业务链路清晰，不再同时维护 V2 Suggestion 和 V3 Evidence/Change 两套入口。

- [ ] 明确旧材料/建议接口生命周期。
  - `createTextMaterial`
  - `createFileMaterial`
  - `createZipMaterial`
  - `analyzeMaterial`
  - `updateSuggestion`
  - `ignoreSuggestion`
  - `applySuggestions`
- [ ] 对仍需兼容的接口加 `@Deprecated` 和注释。
- [ ] 前端主流程不再暴露旧 Suggestion JSON 编辑。
- [ ] `AiOutputService` provider 字段改为真实来源。
  - 如果只是模板渲染，provider 应写 `local-template`。
  - 如果接入模型，记录真实 provider name 和 usage。
- [ ] 统一后端错误语言。
  - API 错误 message 面向用户可以中文。
  - code 保持英文稳定。
  - 同一模块不混用中英文 message 风格。
- [ ] 将 `ProjectFactSource` 当前值和历史事件分离。
  - 当前值用于项目档案页面。
  - 历史事件用于成长时间线、字段来源链、输出追溯。

验收：

- 用户不再看到或需要编辑 raw JSON payload。
- 采纳变更后，项目档案、事实来源、成长记录、输出来源链都有可追溯记录。
- 旧接口保留期间有明确注释和测试保护。

---

## 4. 不采纳或暂缓项

| 项 | 处理 |
| --- | --- |
| 一次性删除所有 `api.ts` 未导入类型 | 暂缓。类型可能作为 API client 公共类型使用，先拆 api 模块，再删除真正无用类型 |
| 直接删除 `Stat` / `Field` / `LoadingBar` | 暂缓。优先让页面使用共享组件，确实不用再删 |
| 立即重写 `ProjectIntelligenceService` | 不采纳。必须按 zip/model/memory/change 分阶段拆 |
| 立即把 JWT 从 localStorage 改 httpOnly cookie | 暂缓。先加 exp 检查和 CSP；cookie 需要后端 CSRF 方案配套 |
| 删除本地路径能力 | 不采纳。本地路径是 ProjectFlow 核心能力，应加 guard，不应移除 |
| 删除旧 Suggestion 接口 | 暂缓。先标记兼容期，确认历史数据和内部调用 |

---

## 5. 推荐执行顺序

### Round 1：死代码和误用清理

优先级最高，因为风险低、收益直接。

- [ ] 清理前端 tasks 死代码。
- [ ] 处理未使用 UI 组件。
- [ ] 清理后端死 repository 方法。
- [ ] 修复 schema repair 重复调用。
- [ ] 修复事务误用和异常 catch 过宽。
- [ ] 跑前后端测试。

### Round 2：安全护栏

开源前必须完成。

- [ ] JWT secret 校验。
- [ ] Provider URL SSRF guard。
- [ ] zip 上传预算。
- [ ] LocalProjectPathGuard。
- [ ] 前端 token exp 和 CSP。
- [ ] 增加安全边界测试。

### Round 3：前端共享层收敛

先统一小组件和 hook，再拆大页面。

- [ ] `useProjectSelection`
- [ ] `useAutoDismissNotice`
- [ ] `Toast`
- [ ] `ProjectContextBar`
- [ ] `PageContainer`
- [ ] `SourceCardList`
- [ ] `Badge` / `StatusPill`

### Round 4：工作台和任务页拆分

只拆结构，不改产品行为。

- [ ] 拆 dashboard 组件。
- [ ] 拆 tasks 页面职责。
- [ ] 保持当前 UI 布局和主链路。
- [ ] 做 route smoke。

### Round 5：后端大服务拆分

每次只拆一个领域。

- [ ] `ProjectZipScanService`
- [ ] `ModelGatewayService`
- [ ] `ProjectMemoryService`
- [ ] `ProjectChangeReviewService`
- [ ] `ProjectAnalysisRecordService`
- [ ] `ProjectMaterialService`

### Round 6：旧链路收敛和长期记录

这是产品竞争力层面的优化。

- [ ] 废弃旧 Suggestion 主流程。
- [ ] 建立事实来源历史事件。
- [ ] 输出生成使用确认来源链。
- [ ] 成长记录按时间线和详情页长期展示。

---

## 6. 总体验收标准

功能验收：

1. 新用户不看文档，可以完成：导入项目 -> 看到画像 -> 绑定路径 -> 开发一天 -> 回来看变化 -> 审查 -> 生成输出。
2. 项目切换后，画像入口、架构入口、活动流、长期档案页全部刷新到当前项目。
3. 生成证据包 -> 生成候选变更 -> 进入详情审查 -> 采纳 -> 写入项目档案，全链路可用。
4. 每日回顾和成果输出能说明使用了哪些确认来源。

前端验收：

1. `dashboard/page.tsx` 小于 800 行。
2. `tasks/page.tsx` 小于 450 行。
3. 不再有本地重复 `StatusPill` / `SourceCardList`。
4. 不再有重复内联 toast。
5. 项目选择逻辑只有一套共享 hook。
6. `npm.cmd run build` 通过。
7. `npx.cmd tsc --noEmit --noUnusedLocals --noUnusedParameters` 通过。

后端验收：

1. `ProjectIntelligenceService.java` 小于 800 行。
2. entity 包不依赖 dto 包。
3. 未设置安全 JWT_SECRET 时，非测试环境不能启动。
4. Provider URL 不能请求内网/metadata 地址。
5. zip 上传有明确大小限制和总读取预算。
6. 本地路径 guard 行为一致。
7. `mvn.cmd -q test` 通过。

安全验收：

1. SQL 查询不拼接用户输入。
2. Git 命令仍使用 `ProcessBuilder` 参数数组，不走 shell 字符串。
3. 本地路径只在 guard 通过后用于文件系统/Git 操作。
4. 模型 URL 通过 allowlist/denylist 校验后才能请求。
5. token 过期后前端不会继续展示登录态。

---

## 7. 最终判断

DeepSeek 的主要价值是找到了更多“产品链路未使用导出”和后端事务/分层问题；GLM 的主要价值是确认前端没有明显 XSS、console、硬编码密钥，并用 tsc 证明了 tasks 页的确定性死代码；Codex 复核补齐了后端开源安全风险和大服务拆分边界。

最终应该先做小而确定的清理和安全护栏，再拆大文件。不要先重写 UI，也不要先大规模重构后端服务，否则会再次引入之前反复出现的顽固回归。
