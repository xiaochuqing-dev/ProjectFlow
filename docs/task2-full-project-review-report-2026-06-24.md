# 任务二深度审查报告：ProjectFlow 前后端代码健康度

日期：2026-06-24

## 0. 审查结论

本轮第二任务只做审查与报告，不再改业务代码。已按前端、后端、路由、接口、安全边界、数据流、死代码/冗余、体积来源和验收证据逐项检查。

结论：当前没有发现 P0 级阻断问题，没有发现明显的 SQL 注入、XSS 直出、shell 拼接命令执行、任意文件路径拼接读写这类立即高危模式。主流程可以继续使用：项目导入/绑定、Git 扫描、证据包、结构化变更、采纳入档、项目画像、每日回顾、成果输出都能编译并通过已有测试。

但项目仍有 5 个必须认真处理的中长期风险：

1. V2/V3 兼容链路仍然叠在一起，`AiSuggestion`、`ProjectMaterial`、`ProjectChange`、`EvidenceBundle`、`ProjectAnalysisRecord` 同时暴露，会继续制造用户认知负担。
2. 后端认证依赖每个 controller 手动读取 `Authorization` 并调用 `AuthService.currentUser(...)`，当前基本覆盖，但新增接口存在漏接风险。
3. AI Provider API key 不回显到前端，但后端以普通文本字段保存，没有加密 at rest；本地单机可接受，团队/公网部署不够。
4. 本地项目路径和 `.projectflow` 写入已有宽路径保护，但未做 realpath/symlink 边界校验；Git 子进程也没有超时。
5. 前端“99% 体积”不是源码膨胀，而是 `node_modules`。源码很小，不能靠删业务代码解决；本轮已安全删除 `.next` 构建产物。

## 1. 审查范围与方法

已审查范围：

- 前端：`frontend/src/app`、`frontend/src/components`、`frontend/src/hooks`、`frontend/src/lib`、`frontend/tests`、`frontend/package.json`、`frontend/next.config.ts`。
- 后端：`backend/src/main/java/com/projectflow/controller`、`service`、`entity`、`repository`、`dto`、`config`、`security`、`support`、`backend/src/test/java`、`backend/pom.xml`、`application.yml`。
- 文档/配置：`PROJECT_CONTEXT.md`、`README.md`、`docs/api-design.md`、`.env.example`、`docker-compose.yml`、`.gitignore`。

审查动作：

- 统计前后端源码文件数、行数、最大文件。
- 列出全部前端 route 和全部后端 controller mapping。
- 扫描 deprecated API、未使用 API helper、低引用组件/工具函数。
- 扫描安全敏感模式：`dangerouslySetInnerHTML`、`eval`、`new Function`、`document.cookie`、raw SQL、shell 拼接、文件写入、路径 normalize、外部 URL、JWT、localStorage token、CSP、CORS。
- 对照既有计划书和旧审查文档，确认哪些旧问题已经修复、哪些仍存在。
- 复核验收结果：任务一改动后前端 build、前端静态测试、后端 Maven 测试均已通过。

## 2. 代码规模与路由/API 盘点

### 2.1 前端规模

`frontend/src` 当前约 58 个源码文件、9824 行。最大文件如下：

| 文件 | 行数 | 判断 |
| --- | ---: | --- |
| `frontend/src/lib/api.ts` | 1162 | 过大，类型和所有 endpoint 混在一起 |
| `frontend/src/app/dashboard/page.tsx` | 749 | 工作台职责过密 |
| `frontend/src/lib/project-insights.ts` | 642 | 规则推断集中，暂可接受 |
| `frontend/src/app/project-intelligence/page.tsx` | 555 | 项目画像主页面仍偏重 |
| `frontend/src/app/projects/[projectId]/files/page.tsx` | 434 | 文件理解页复杂但边界清楚 |
| `frontend/src/app/ai-review/page.tsx` | 411 | 成果输出页偏大 |
| `frontend/src/app/project-changes/[changeId]/page.tsx` | 408 | 详情页仍可继续拆证据区块 |

前端 route 共 24 个，构建期可识别：

- `/dashboard`
- `/tasks`
- `/project-intelligence`
- `/project-intelligence/capabilities`
- `/project-intelligence/timeline`
- `/project-intelligence/fact-sources`
- `/project-intelligence/changes`
- `/project-intelligence/analysis-records`
- `/project-analysis-records/[recordId]`
- `/project-changes/[changeId]`
- `/project-changes/[changeId]/evidence`
- `/dev-logs`
- `/dev-logs/sources`
- `/dev-logs/sources/[sourceId]`
- `/ai-review`
- `/projects`
- `/projects/[projectId]`
- `/projects/[projectId]/files`
- `/imports`
- `/settings`
- `/login`
- `/register`
- `/work-sessions/[sessionId]`
- `/`

未发现构建期坏路由。任务一新增的能力清单页和每日来源详情页已经进入 route table。

### 2.2 后端规模

`backend/src/main/java` 当前约 117 个源码文件、10562 行。后端 controller 共 14 个，request mapping 共 68 个：

| Controller | Mapping 数 | 行数 |
| --- | ---: | ---: |
| `WorkSessionScanController.java` | 9 | 126 |
| `ProjectAnalysisController.java` | 8 | 108 |
| `ProjectIntelligenceController.java` | 7 | 104 |
| `AiProviderController.java` | 5 | 69 |
| `ProjectChangeController.java` | 5 | 69 |
| `ProjectController.java` | 5 | 68 |
| `ProjectMaterialController.java` | 5 | 78 |
| `AiOutputController.java` | 4 | 61 |
| `ProjectMemoryController.java` | 4 | 63 |
| `TaskController.java` | 4 | 63 |
| `AuthController.java` | 3 | 34 |
| `DevLogController.java` | 3 | 52 |
| `MarkdownImportController.java` | 3 | 54 |
| `ProjectAgentBridgeController.java` | 3 | 55 |

最大 service：

| Service | 行数 | 判断 |
| --- | ---: | --- |
| `ProjectAgentBridgeService.java` | 688 | 写协议、扫结果、生成材料/建议/变更，职责偏宽 |
| `ProjectIntelligenceService.java` | 661 | zip 导入、legacy suggestion、snapshot/evolution 混合 |
| `ProjectAnalysisService.java` | 624 | 本地规则与模型分析都在一个类中 |
| `ProjectZipScanService.java` | 463 | zip 扫描规则集中，安全边界较多 |
| `WorkSessionScanService.java` | 433 | Git 扫描、归因、冲突信号集中 |
| `AiOutputService.java` | 375 | 名称像 AI，实际主要是本地模板输出 |
| `ProjectMemoryService.java` | 311 | 项目档案写入和来源记录集中 |

## 3. 路由与接口健康

健康点：

- 前端所有 route 可被 Next build 识别。
- 后端 controller 接口整体覆盖当前主流程。
- 前端 API client 统一处理 `{ data, message }` 和错误响应。
- 大多数项目资源在 service 层通过 `projectRepository.findByIdAndUserId(...)` 或等价逻辑做 owner 校验。
- 文件分析接口会先确认请求 path 存在于导入目录树中，再做分析，避免任意 path 查询。
- zip 上传前端有 512MB 快速拦截，后端也有上传/读取预算。

需要修正的接口/文档不一致：

- `docs/api-design.md` 仍写有 `DELETE /tasks/{taskId}`、`DELETE /dev-logs/{logId}`、`POST /ai-outputs/{outputId}/regenerate`，当前后端没有实现。
- 后端有 `GET /tasks/{taskId}`、`GET /dev-logs/{logId}`、`GET /ai-outputs/{outputId}`，但前端主线目前并未统一暴露这些 detail helper 或详情页。
- `ProjectIntelligenceController`、`ProjectMaterialController`、`ProjectAnalysisController` 保留多组 deprecated V2 接口，前端仍部分使用，不能直接删除。

建议：先更新 `docs/api-design.md`，把未实现接口标为“旧规划未落地/当前不提供”，避免后续按过期文档继续扩散错误流程。

## 4. 产品数据流审查

### 4.1 V3.2 主链路

当前主链路基本成立：

```text
导入/绑定项目 -> 扫描 Git/Agent Result -> EvidenceBundle -> ProjectChange -> 用户采纳 -> ProjectMemory + ProjectFactSource + ProjectEvolutionRecord -> 每日回顾/成果输出引用
```

任务一修正后：

- 「已完成能力」不再在项目画像主页面铺开路径。
- 能力点进入独立能力页。
- 每日回顾来源从 4 个重复按钮收敛为 1 个总入口，来源卡可继续点详情。

### 4.2 仍存在的数据流歧义

1. `AiSuggestion` 与 `ProjectChange` 双审查体系仍并存。
   - `/tasks` 仍有「兼容候选建议」区域。
   - `/dashboard`、`/project-intelligence` 仍读取 `listAiSuggestions`。
   - 这不会立刻坏，但会让用户不清楚到底应该采纳“旧建议”还是“结构化变更”。

2. 「字段来源链」当前更像“字段最新来源”，不是完整历史链。
   - `ProjectFactSourceRepository` 使用 `findByProjectIdAndFieldKey(...)`。
   - `ProjectMemoryService.recordFactSource(...)` 找到同一 field 后直接 update。
   - 这意味着每个字段保留最近来源，不保留每次改写历史。
   - 真正历史链目前在 `ProjectEvolutionRecord` 和 `ProjectChange` 中。
   - 建议 UI 文案改为「字段来源」或后续新增 `ProjectFactSourceHistory`，不要让用户误以为这里能看到完整来源链。

3. `AiOutputService` 仍容易造成命名误解。
   - `AiOutputService.generate(...)` 写入 provider 为 `local-template`。
   - 它生成的是本地模板 Markdown，并记录估算 token。
   - 这对本地 fallback 有价值，但 UI/文档必须明确“本地模板输出”，否则用户会误以为已调用模型生成。

## 5. 安全审查

### 5.1 未发现的高危模式

未发现以下直接高危模式：

- `dangerouslySetInnerHTML`
- `eval(...)`
- `new Function(...)`
- 直接 `document.cookie`
- 前端手写 `innerHTML`/`outerHTML` 注入
- 业务层拼接 shell 命令
- 用户输入拼接 SQL

后端业务查询主要使用 Spring Data Repository。唯一 `JdbcTemplate` 使用点是 `ProjectChangeSchemaRepairService`，SQL 来源是固定字符串和 enum 值，仅用于 H2 schema repair，不属于用户输入 SQL 注入面。

### 5.2 已有安全控制

- 密码使用 BCrypt。
- JWT 使用 JJWT `parseSignedClaims(...)` 验签，不是只 decode。
- JWT secret 默认占位时生成内存开发密钥并 warning；显式短 secret 会拒绝启动。
- CORS 默认只允许 `localhost:3000`、`127.0.0.1:3000`，且 `allowCredentials(false)`。
- 前端 CSP 设置了 `default-src 'self'`、`frame-ancestors 'none'`、`base-uri 'self'`、`form-action 'self'`。
- AI Provider URL 只允许 HTTPS；本地 HTTP 仅允许 localhost；阻断私有网段、loopback、metadata IP 字面量。
- zip 扫描跳过 `.git`、`node_modules`、`.next`、`target`、`dist`、`build`、日志、二进制、`.env` 等噪音/敏感路径。
- zip 内容会脱敏 key/secret/password/token、Authorization bearer、private key。
- Git 命令使用 `ProcessBuilder(List<String>)` 参数数组，不经 shell 拼接。

### 5.3 风险项

#### P1：AI Provider API key 明文存储

证据：

- `AiProvider.apiKey` 是普通 `text` 字段。
- `AiProviderService.create/update` 保存 `blankToNull(request.apiKey())`。
- 响应 DTO 只返回 `apiKeyConfigured`，不回显 key，这是正确的。

判断：

- 本地单机工具可接受。
- 如果进入团队共享或公网部署，应该加密 at rest，或者接入 OS credential store / secret manager。

#### P1：认证逻辑分散在 controller

证据：

- 多数 controller 方法都显式接收 `@RequestHeader(value = "Authorization", required = false)`。
- 每个方法再调用 `authService.currentUser(...)`。

判断：

- 当前覆盖面基本健康。
- 但新增接口时容易漏认证或漏 owner check。
- 建议后续引入 Spring Security filter / argument resolver / method security，把“当前用户”集中注入。

#### P1：AI Provider SSRF 防护未解析 DNS

证据：

- `AiProviderUrlGuard` 阻断的是 IP literal 私网/metadata/loopback。
- 对普通域名没有解析后判断最终 IP。

判断：

- 本地应用风险较低。
- 团队/公网部署时，攻击者可配置解析到私网的域名，造成 SSRF 绕过。

#### P2：前端 token 存在 `localStorage`

证据：

- `frontend/src/lib/auth.ts` 保存 `projectflow_access_token` 到 `window.localStorage`。

判断：

- 当前没有发现 XSS sink，且项目 local-first，短期可接受。
- 一旦进入非本地环境，XSS 后 token 可被读取。建议改为 httpOnly SameSite cookie 或短 access token + refresh/session 机制。

#### P2：本地项目路径缺少 realpath/symlink 边界校验

证据：

- `LocalProjectPathGuard` 使用 `Path.of(...).toAbsolutePath().normalize()`。
- `ProjectAgentBridgeService` 和 `ProjectContextSyncService` 在 `projectRoot.resolve(".projectflow")` 下写文件。

判断：

- 已阻断 root、home、Windows、Program Files 等过宽路径。
- 但未对 `toRealPath()`、symlink、junction 做边界确认。后续应在写入前确认最终真实路径仍在用户绑定项目内。

#### P2：Git 子进程没有超时

证据：

- `WorkSessionScanService.runGit(...)` 使用 `process.waitFor()`，没有 timeout。

判断：

- 无 shell 注入风险。
- 但异常 Git hook、损坏仓库或慢网络文件系统可能阻塞扫描线程。建议改为 `waitFor(timeout, TimeUnit.SECONDS)`，超时后 destroy 并返回 warning。

#### P3：CSP 允许 `'unsafe-inline'`

证据：

- `frontend/next.config.ts` 的 `script-src` 和 `style-src` 包含 `'unsafe-inline'`。

判断：

- Next/Tailwind 本地开发方便。
- 生产部署应分 dev/prod 策略逐步收紧。

## 6. 前端冗余、死代码与可维护性

### 6.1 `api.ts` 过大

`frontend/src/lib/api.ts` 1162 行，同时包含：

- 通用请求封装
- 所有 domain type
- 所有 endpoint function
- legacy/deprecated API
- model/provider/project/work-session/project-change 等多条业务线

它不是当前 bug，但会增加后续修改成本。建议后续按 domain 拆：

- `api/core.ts`
- `api/projects.ts`
- `api/project-memory.ts`
- `api/project-changes.ts`
- `api/analysis.ts`
- `api/agent-bridge.ts`
- `api/legacy-materials.ts`
- `api/ai-provider.ts`

拆分时不要一次性重排所有页面，先从新增功能开始迁出。

### 6.2 未被前端源码引用的 API helper

静态引用扫描显示以下 `api.ts` 导出在前端源码中没有页面引用：

- `analyzeProjectMaterial`
- `createTask`
- `createTextMaterial`
- `ignoreAiSuggestion`
- `listProjectAgentSignatureFeedback`
- `listProjectSnapshots`
- `updateTaskStatus`
- `updateWorkSession`
- `uploadProjectMaterialFile`
- `uploadProjectZip`
- `writeAgentTaskBrief`

判断：

- 这些大多是 legacy/兼容 API。
- 不建议马上删除，因为后端测试、历史数据、文档和未来兼容入口仍可能依赖。
- 建议先把它们迁入 `api/legacy-*`，再观察是否仍需要 UI。

### 6.3 低引用工具

扫描发现 `frontend/src/lib/project-memory-display.ts` 的 `capabilityCountLabel` 当前只有定义引用，没有页面使用。它是任务一改动时留下的候选清理点。第二任务要求不改代码，因此仅记录，不在本轮删除。

### 6.4 重复 helper

前端存在重复或相似 helper 名称：

- `changeMemoryTargets`
- `compactPath`
- `InfoLine`
- `sourceLabel`
- `toPayload`

这不是功能 bug，但说明详情页和通用组件之间还有局部重复。后续只在改对应页面时顺手收敛，不建议为此做大重构。

### 6.5 页面复杂度

最需要控制的页面：

- `dashboard/page.tsx`：749 行，状态和动作非常多，是前端风险最高页面。
- `project-intelligence/page.tsx`：555 行，主页面同时负责表单、入口卡、分析触发、右侧归档入口。
- `projects/[projectId]/files/page.tsx`：434 行，文件筛选、分析、展示都在一页。
- `ai-review/page.tsx`：411 行，输出生成、来源 metric、编辑导出都在一页。

建议：

- 优先拆 dashboard 的 action handler 和 project context loader。
- 保持“详情进入详情页”的方向，不再把长文本塞回主页面。
- 不要为了拆而拆；只有当页面继续新增功能时再拆。

### 6.6 交互问题

仍建议修的 UX 点：

- 多个详情页使用 `router.back()`，外部直达时返回路径不稳定；应提供确定性 fallback。
- 删除项目有确认，删除分析记录没有二次确认。
- 每日来源详情页当前通过列表 API 重建来源项，没有独立后端 source detail API；短期可用，长期可以考虑持久化来源索引。

### 6.7 测试盲区

前端现有 10 个 `.mjs` 测试都是静态/正则式检查，没有 React 渲染测试或 Playwright 浏览器冒烟测试。它们能防止关键文案/入口缺失，但不能证明：

- 按钮真实可点。
- query 参数切换无状态错乱。
- 卡片响应式布局没有溢出。
- 删除/确认/加载状态在浏览器中正确。

建议后续补 3 条 Playwright 冒烟：

1. 项目画像 -> 能力清单 -> 返回。
2. 每日回顾 -> 查看全部来源 -> 来源详情。
3. 变更审查 -> 详情 -> 采纳后项目画像入口数量变化。

## 7. 后端冗余、死代码与可维护性

### 7.1 V2 兼容桶仍偏大

`V2ProjectDtos.java` 仍是 400+ 行聚合 DTO，覆盖 material、analysis、suggestion、memory、agent bridge、work session、evidence bundle、project change、fact source 等多条线。

判断：

- 这是历史演进留下的兼容桶。
- 不是立即 bug。
- 后续清理应按业务线迁出，而不是一次性重命名。

### 7.2 大 service 职责过宽

建议优先拆分顺序：

1. `ProjectIntelligenceService`
   - 拆出 legacy suggestion adapter。
   - 保留 zip import/profile/evolution 主职责。

2. `ProjectAgentBridgeService`
   - 拆出 `.projectflow` 文件写入。
   - 拆出 agent result parser。
   - 拆出 result -> suggestion/change 的转换。

3. `ProjectAnalysisService`
   - 拆出 local rule analyzer。
   - 拆出 model result mapper。
   - 保留 orchestration。

4. `ProjectService.delete(...)`
   - 当前手动调用 17 个 repository `deleteByProjectId`。
   - 建议后续收敛为 project deletion policy/service，或者用明确的 cascade 策略。

### 7.3 分析 job 可能存在并发重复创建

证据：

- `ProjectAnalysisJobService.startProjectAnalysis(...)` 先查 active job，再不存在时 save。
- 当前没有看到数据库唯一约束或锁。

判断：

- 前端 loading/disabled 可降低概率。
- 但双击、并发请求或多 tab 仍可能创建重复 active job。
- 后续可用唯一约束、乐观锁、或数据库级状态锁解决。

### 7.4 H2 schema repair 是技术债

`ProjectChangeSchemaRepairService` 在 `ApplicationReadyEvent` 时对 H2 enum 做修复。它不是注入风险，但属于“运行时补 schema”。如果项目后续稳定，应迁移到正式 migration 工具或明确注释为本地兼容修复。

### 7.5 旧问题已修复或已缓解

旧审查中提到的部分问题已不再成立：

- 实体层没有再直接依赖 `V2ProjectDtos` 响应 DTO。
- `ProjectContextSyncService.sync()` 当前是 `@Transactional`，不是旧报告里的 `readOnly = true`。
- `AiProviderService.test()` 当前没有放在 read-only transaction 内。
- `AiOutputService` provider 已从旧的 `mock-provider` 改成 `local-template`，但命名误解仍需继续处理。

## 8. 前端体积审查

当前体积来源：

| 区域 | 大小 |
| --- | ---: |
| `frontend` | 376.54MB |
| `frontend/node_modules` | 374.6MB |
| `frontend/.next` | 0MB |
| `frontend/src` | 0.41MB |
| `frontend/tests` | 0.03MB |
| `backend` | 8.51MB |
| `backend/target` | 7.94MB |
| `docs` | 0.12MB |

tracked 源码体积：

| 区域 | 文件数 | 大小 |
| --- | ---: | ---: |
| 前端 tracked 文件 | 73 | 约 1.86MB |
| 后端 tracked 文件 | 132 | 约 0.57MB |

结论：

- 前端 99% 不是业务源码，而是 `node_modules`。
- `.next` 是可再生构建产物，本轮已删除。
- 不能在不影响本地 dev/build 的情况下删除 `node_modules`；删了就必须重新 `npm install`。
- 前端依赖很少：Next、React、React DOM、lucide-react、Tailwind、TypeScript、类型包；没有明显可删的大型业务依赖。

可选优化：

1. 继续保留 `node_modules`，保持本地开发可用。
2. 如果必须压目录体积，删除 `frontend/node_modules`，但之后需要重新安装依赖。
3. 中长期改 pnpm 全局 store/硬链接模式，减少多项目重复依赖体积。
4. ProjectFlow 自己做项目体积统计时，默认排除 `node_modules`、`.next`、`target`、`dist`、`build`、`.git`，这比物理删除更符合产品语义。

## 9. 不建议现在删除的内容

不要直接删除：

- deprecated suggestion/material API：前端仍有页面使用，后端测试和历史数据也覆盖。
- `ProjectMaterial`：文件理解页和 zip 导入仍需要材料记录。
- `ProjectSnapshot`：当前不是主 UI，但仍是历史兼容数据。
- `Mock Provider` fallback：能让未配置模型时仍显示可用状态；可以改文案，但不应突然删除。
- `node_modules`：除非接受重装成本。

可以后续迁移/隐藏：

- V2 suggestion UI。
- 未被引用的 API helper。
- `capabilityCountLabel`。
- `V2ProjectDtos` 中非主流程 DTO。

## 10. 优先级建议

### P0

当前没有发现必须立即修复的阻断性 bug 或直接高危漏洞。

### P1

1. 更新 `docs/api-design.md`，修正未实现 endpoint。
2. 把 `/tasks` 中 V2 suggestion 区域继续降级或隐藏，主线只保留 `ProjectChange`。
3. 为 AI Provider key 做 at-rest 加密方案，至少在非本地部署前完成。
4. 引入集中认证机制，减少 controller 手动认证重复。
5. 给 AI Provider URL guard 增加 DNS 解析后私网/metadata 校验。

### P2

1. 本地路径 guard 增加 `toRealPath()` 和 symlink/junction 边界校验。
2. `WorkSessionScanService.runGit(...)` 增加超时。
3. 为分析 job 增加并发去重约束。
4. 给删除分析记录增加确认。
5. 补 Playwright 冒烟测试覆盖能力清单、每日来源详情、变更采纳链路。
6. 把 `api.ts` 按业务域逐步拆分。

### P3

1. 收紧生产 CSP，去掉或限制 `'unsafe-inline'`。
2. 迁移 `V2ProjectDtos` 到按业务域划分的 DTO。
3. 抽出 dashboard/project-intelligence 的 project-scoped resource loader。
4. 将 `AiOutputService`/UI 文案改成“本地模板输出”或接入真实模型生成。

## 11. 验收与验证

任务一代码改动后的验证已完成：

- `frontend`: `npm.cmd run build` 通过。
- `frontend`: 全部 `tests/*.mjs` 通过。
- `backend`: `C:\Users\Administrator\Desktop\apache-maven-3.9.9\bin\mvn.cmd -q test` 通过。

任务二阶段没有继续改业务代码，只更新本报告。报告中的代码事实来自当前仓库扫描。

## 12. 最终判断

ProjectFlow 当前最需要的不是继续堆功能，而是把 V3.2 主线从历史兼容层里彻底凸显出来：用户应该只看到“项目变化 -> 审查确认 -> 项目档案 -> 每日回顾/成果输出”的主路径，旧 suggestion/material/snapshot 只作为兼容或追溯存在。

体积问题已经明确：前端目录占比巨大主要由 `node_modules` 造成，不是源码过大。本轮已经删除可再生 `.next`；剩余体积不建议继续物理清理，除非接受重新安装依赖。
