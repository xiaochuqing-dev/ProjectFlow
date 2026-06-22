# ProjectFlow 前端冗余与死代码审查报告

日期：2026-06-22  
范围：`frontend/src/` 全部页面、组件、lib 文件  
方法：逐文件读取 + 交叉比对导入/导出使用情况  
状态：只审查，未修改任何文件

---

## 一、死代码（定义但从未被使用）

### 1.1 api.ts 中未被任何页面导入的函数（10 个，占 16.7%）

| 函数 | 行号 | 对应后端端点 |
|------|------|-------------|
| `createTask` | 182 | POST /projects/:id/tasks |
| `updateTaskStatus` | 193 | PATCH /tasks/:id/status |
| `createTextMaterial` | 849 | POST /projects/:id/materials/text |
| `uploadProjectMaterialFile` | 880 | POST /projects/:id/materials/file |
| `uploadProjectZip` | 891 | POST /projects/:id/materials/zip |
| `analyzeProjectMaterial` | 904 | POST /project-materials/:id/analyze |
| `listProjectSnapshots` | 1102 | GET /projects/:id/snapshots |
| `updateWorkSession` | 1166 | PATCH /work-sessions/:id |
| `listProjectAgentSignatureFeedback` | 1199 | GET /projects/:id/agent-signature-feedback |
| `writeAgentTaskBrief` | 1233 | POST /projects/:id/agent-bridge/tasks/:id/brief |

说明：这些函数都是后端有对应端点、前端也写了封装，但没有任何页面在实际使用。删除后不影响任何功能，且减少 api.ts 约 120 行。

### 1.2 api.ts 中未被导入的类型（31 个，占 62%）

`ProjectStatus`、`ProjectPayload`、`TaskPriority`、`TaskPayload`、`DevLogCategory`、`DevLogPayload`、`AiProviderPayload`、`ProviderTestResult`、`MaterialSourceType`、`AiSuggestionType`、`AiSuggestionStatus`、`ProjectProfile`、`AnalyzeMaterialResult`、`ProjectImportAnalyzeResult`、`ProjectSnapshot`、`ProjectChangeKind`、`ProjectChangeImpactLevel`、`ProjectChangeSourceType`、`ProjectChangeStatus`、`ProjectFactSourceType`、`ProjectFileAnalysis`、`ProjectAnalysisJobStatus`、`ProjectAnalysisJobType`、`ProjectAnalysisRecordType`、`ApplySuggestionsResult`、`AgentBridgeWriteResult`、`AgentResultScanResult`、`AgentTaskBriefResult`、`EvidenceSource`、`AgentSignatureFeedback`、`ContextSyncResult`

说明：前端页面处理这些数据时只用内联字段解构，从未导入这些类型标注。

### 1.3 页面内的死函数

**tasks/page.tsx 第 678-682 行：`sourcePreview`**

```typescript
function sourcePreview(suggestion: AiSuggestion) {
  const sourceFile = typeof suggestion.payload.sourceFile === "string" ? suggestion.payload.sourceFile : "";
  const taskRef = typeof suggestion.payload.taskRef === "string" ? suggestion.payload.taskRef : "";
  return [sourceFile, taskRef].filter(Boolean).join(" · ") || "payload 可审查";
}
```

定义后从未被调用。纯死代码。

### 1.4 UI 组件库中的死组件（3 个）

| 组件 | 文件 | 行号 |
|------|------|------|
| `Stat` | primitives.tsx | 210 |
| `Field` | primitives.tsx | 235 |
| `LoadingBar` | toast.tsx | 30 |

三个组件在 `ui/index.ts` 中被 re-export，但没有任何页面导入它们。`LoadingBar` 尤其尴尬——4 个页面各自写了 `<div className="h-1 bg-slate-950" />` 内联样式，却没有用这个现成的组件。

### 1.5 其他死导出

- **project-insights.ts 第 60 行：`parseZipDirectoryTree`** — 对外导出但只有同文件内的 `projectZipPaths` 调用它，外部无引用。应改为内部函数。
- **toast.tsx 第 37 行：`export type { ReactNode }`** — 从 react 重导出 `ReactNode` 类型，但无任何文件从 toast.tsx 导入它。

---

## 二、跨页面重复代码（应抽取为共享模块）

### 2.1 项目选择下拉框（7 个页面各写一遍）

出现位置：tasks、dev-logs、ai-review、project-intelligence、settings、imports、projects

每一处都是同样的结构：select → projects.map → 判断 selectedProjectId → onChange 调用 rememberSelectedProjectId。只有 dashboard 通过 `ProjectContextBar` 做了封装，其余 6 页各写一遍，约 60 行重复 JSX。

### 2.2 `refreshProjectContext` 模式（5 个页面）

dashboard、tasks、dev-logs、ai-review、project-intelligence 各有一个同名 async 函数，结构完全一致：readSession → 判空 → Promise.all(多个 API) → set 各状态 → catch。差异仅在于调用了哪些 API。可抽取为通用数据加载 hook。

### 2.3 error/notice 自动消失逻辑（6 个页面）

出现位置：dashboard、tasks、dev-logs、ai-review、project-intelligence、settings

```typescript
useEffect(() => {
  if (!notice && !error) return;
  const timeout = window.setTimeout(() => { setNotice(""); setError(""); }, 4200);
  return () => window.clearTimeout(timeout);
}, [error, notice]);
```

6 个文件完全相同。应抽取为 `useAutoDismiss(error, notice, ms?)` hook。

### 2.4 error/notice 内联 Toast（4 个页面）

出现位置：tasks（653-654）、dev-logs（294-295）、ai-review（337-338）、project-intelligence（373-374）

4 个页面用一模一样的 `<div>` 展示 error/notice：`fixed bottom-5 left-1/2 z-50 -translate-x-1/2`，仅颜色不同。dashboard 已迁移到共享 `<Toast>` 组件，这 4 个页面没跟上。

### 2.5 文本提取函数（3 个页面各写一遍）

- dashboard/page.tsx 第 1586 行：`firstMeaningfulText`
- dev-logs/page.tsx 第 452 行：`firstUsefulLine`
- ai-review/page.tsx 第 453 行：`firstUsefulLine`

都是"从文本中提取第一句有实际内容的行"，逻辑几乎相同。应合并为一个共享工具函数。

### 2.6 同名但不同接口的组件

**`StatusPill`：**
- tasks/page.tsx 第 660 行：label、value、tone 三个 props
- dev-logs/page.tsx 第 383 行：label、value 两个 props，无 tone

**`SourceCardList`：**
- dev-logs/page.tsx 第 364 行
- ai-review/page.tsx 第 374 行

两个版本骨架一致，但 props 和渲染逻辑略有不同。应合并。

### 2.7 work-sessions 页面重写了 Card 组件

work-sessions/[sessionId]/page.tsx 第 162 行自己定义了 `Card`，而 `@/components/ui` 已有功能相同的共享 `<Card>`。应改用共享组件。

---

## 三、用了原生写法没迁移到共享组件

### 3.1 PageContainer 没被 4 个页面使用

`layout.tsx` 第 10 行 JSDoc 明确写了"替代此前各页重复的 `min-h-[calc(100vh-4rem)] bg-surface p-6 md:p-8`"。但以下 4 页仍然内联：

- project-intelligence/timeline/page.tsx
- project-intelligence/changes/page.tsx
- dev-logs/sources/page.tsx
- project-intelligence/analysis-records/page.tsx

### 3.2 LoadingBar 组件零使用

`toast.tsx` 定义了 `<LoadingBar>`，但无人使用。4 个页面各自写 `<div className="h-1 bg-slate-950" />`——与 LoadingBar 的功能完全一致（仅颜色不同：bg-slate-950 vs bg-brand）。

---

## 四、AuthPanel.tsx 内部重复

登录表单的 3 个输入框（用户名/邮箱/密码）是同一套 JSX 结构重复 3 次（第 61-71、77-87、91-103 行）：

```tsx
<span className="flex items-center gap-3 rounded-2xl border border-blue-200/20 bg-white/9 px-4 py-4 text-base text-white shadow-inner shadow-blue-950/30">
  <Icon className="h-5 w-5 text-cyan-200" />
  <input ... />
</span>
```

区别只在图标、type、placeholder。约 30 行 JSX 重复。应提取为内部 `InputField` 组件。

---

## 五、不一致问题

- **AuthPanel/AuthPageShell 颜色体系**：使用硬编码 hex 色值（`#07152d`、`#2f7cff`、`rgba(...)`），而其余页面已迁移到 Tailwind 语义色（`surface`、`elevated`、`brand`、`muted`）。登录页和其他页面是两个设计体系。
- **AppShell 导航激活判断**：第 63 行用 `pathname.includes("/files")` 判断项目画像是否激活，是脆弱的字符串匹配。后续路由增多会导致误判。
- **测试文件风格不统一**：`.mjs` 和 `.ts` 混用。部分 `.mjs` 测试自己写 assert 函数而非用 `node:assert/strict`。所有测试都是静态正则匹配，无组件渲染测试或 API mock 测试。

---

## 六、无问题的部分

- 无 `console.log` 或调试语句残留
- 无注释掉的代码块
- 所有 `useState` 变量均被读取（无声明后未使用的状态）
- 所有 import 的库函数均被使用（无 unused import）
- `globals.css` 中无未使用的样式
- `tailwind.config.ts` 中主题色均在代码中有引用

---

## 七、修复建议优先级

### P1（明显冗余和死代码，清理后立即减重）

| 项目 | 预估减重 |
|------|---------|
| api.ts 删除 10 个死函数 | ~120 行 |
| api.ts 清理 31 个未用类型 | ~180 行 |
| tasks/page.tsx 删除 `sourcePreview` | ~5 行 |
| project-insights.ts `parseZipDirectoryTree` 改内部函数 | 规范 |
| ui 组件删除 `Stat`、`Field`、`LoadingBar` 或让它们被使用 | ~70 行 |
| toast.tsx 删除 `export type { ReactNode }` | 1 行 |

### P2（跨页面重复，抽取共享模块）

| 项目 | 影响范围 |
|------|---------|
| 抽取共享 `useAutoDismiss` hook | 6 个文件各减 6 行 |
| 4 页面迁移到 `<Toast>` 组件 | 消除 4 处重复内联样式 |
| 4 页面迁移到 `<PageContainer>` 组件 | 消除 4 处重复 wrapper |
| 6 页面迁移到 `<ProjectContextBar>` | 消除 6 处重复下拉框 |
| 合并 `firstMeaningfulText` 为共享工具函数 | 3 个文件各减 5 行 |
| 合并 `StatusPill` 为一个组件 | 接口统一 |
| 合并 `SourceCardList` 为一个组件 | 消除重复 |
| work-sessions 页面改用共享 `<Card>` | 消除重新实现 |

### P3（改善一致性，非紧急）

| 项目 | 说明 |
|------|------|
| AuthPanel 提取 `InputField` 子组件 | 减 30 行 JSX 重复 |
| AuthPanel/AuthPageShell 颜色迁移到 Tailwind 语义色 | 设计统一 |
| AppShell 导航判断改为精确匹配 | 防止后续路由增多误判 |
| 测试文件风格统一 | `.mjs` → `.ts` |

---

## 八、统计数据

| 指标 | 数值 |
|------|------|
| 审查文件数 | 35+ |
| 死函数（api.ts） | 10 / 60（16.7%） |
| 死类型（api.ts） | 31 / ~50（62%） |
| 死组件（ui/） | 3 / 12（25%） |
| 页面内死函数 | 1 |
| 跨页面重复模式 | 8 种 |
| 未迁移到共享组件的页面 | 4 个 |
| 需抽取的共享 hook | 2 个 |
| P0 问题 | 0 |
| P1 问题 | 6 项 |
| P2 问题 | 8 项 |
| P3 问题 | 4 项 |

---

## 九、后端审查

日期：2026-06-22  
范围：`backend/src/main/java/com/projectflow/` 全部 controller、service、entity、repository、dto  
方法：逐文件读取 + 交叉比对调用链
状态：只审查，未修改任何文件

### 9.1 死代码

后端情况比前端好——没有死控制器方法，没有死服务公共方法，没有死注入依赖。

**entity/ImportRecord.java 第 35 行：`rawMarkdown` 字段只写不读**

该字段在构造时赋值，有 getter，但没有任何 service 或 controller 调用 `getRawMarkdown()`。配套的 `ImportRecordResponse` DTO 也刻意不包含这个字段。数据存入数据库但从未读出展示。可删除或改为 transient。

**repository/AiSuggestionRepository.java 第 14 行：死查询方法**

```java
List<AiSuggestion> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, AiSuggestionStatus status)
```
定义但从未被任何 service 调用。整个项目只用 `findByProjectIdOrderByCreatedAtDesc()`。

### 9.2 跨 Service 重复工具方法

以下方法在多个 service 中各写一份，逻辑完全相同：

| 方法 | 出现位置 | 出现次数 |
|------|---------|---------|
| `findOwnedProject` | TaskService、DevLogService、MarkdownImportService、AiOutputService、ProjectAgentBridgeService、ProjectIntelligenceService、ProjectAnalysisJobService | 7 次 |
| `resolveProjectRoot` | ProjectAgentBridgeService、ProjectContextSyncService、WorkSessionScanService | 3 次 |
| `defaultText` | ProjectAgentBridgeService、ProjectIntelligenceService、ProjectContextSyncService | 3 次 |
| `truncate` | ProjectAgentBridgeService、ProjectIntelligenceService | 2 次 |
| `escapeJson` | AiProviderService、ProjectAgentBridgeService | 2 次 |
| `writePayload` / `readPayload` | ProjectAgentBridgeService、ProjectIntelligenceService | 2 次 |
| `estimateTokens` | AiOutputService、ProjectAnalysisJobRunner | 2 次 |
| `initialMemory` | ProjectAgentBridgeService、ProjectIntelligenceService | 2 次 |
| `qualityWarnings` | AiOutputService、ProjectAnalysisJobRunner（相似但不完全相同） | 2 次 |

7 个 service 各自写了一遍 `projectRepository.findByIdAndUserId(...).orElseThrow(...)`——这是后端最严重的重复。应抽取为共享工具类。

### 9.3 @Transactional 误用

**ProjectContextSyncService.sync()：`@Transactional(readOnly = true)` 但写了文件**

该方法读取 DB 后调用 `Files.writeString()` 写入 `.projectflow/context/projectflow-context.md`。`readOnly = true` 在语义上和实际操作矛盾，某些 JDBC 驱动会强制拦截。

**AiProviderService.test()：HTTP 调用在事务内**

该方法用 `@Transactional(readOnly = true)` 包裹了一个最长 12 秒的 `HttpClient.send()` 外部 API 调用。DB 连接在整个 HTTP 往返期间被占用。应将 HTTP 调用移出事务。

### 9.4 异常处理问题

**ProjectAnalysisJobRunner.execute()：异常栈完全丢失**

catch 块只保存了 `exception.getClass().getSimpleName()` 和 `exception.getMessage()`，没有记录堆栈也没有打日志。异步任务失败后调试极难定位。

**WorkSessionScanService.parseInstant()：catch 了 RuntimeException**

应 catch `DateTimeParseException`。当前写法如果代码里有 NPE 也会被吞掉返回 `Instant.now()`。

**MarkdownImportService.parseDate()：同样 catch RuntimeException 过宽**

同上。

### 9.5 架构问题

**ProjectService.delete() 是上帝方法**

删除项目时对 17 个不同的 Repository 逐一调用 `deleteByProjectId()`。每新增一个实体都得改这个方法。应该用 JPA 级联或至少用反射/注册机制管理。

**EvidenceBundleService 在每次读取时调 schema repair**

`toResponse()` 方法（每次 GET 请求都会触发）里调了 `projectChangeSchemaRepairService.ensureEvidenceBundleSourceTypeAllowed()`。这个 repair 在 `ApplicationReadyEvent` 启动时已经执行过一次，运行时重复调用是多余的。`EvidenceDraftChangeService` 也有同样的问题。

**实体依赖 DTO（反向依赖）**

`WorkSession.java`、`EvidenceBundle.java`、`AgentSignatureFeedback.java` 的 `toResponse()` 方法直接 import `V2ProjectDtos` 的响应类型。持久化层依赖了 API 响应层，违反分层原则。应把 `toResponse()` 的逻辑移到 service 层。

### 9.6 硬编码与不一致

**AiOutputService 的 provider 字段永远写 "mock-provider"**

所有 AI 输出生成的 `provider` 字段都硬编码为 `"mock-provider"`。实际上根本没调模型，只是模板渲染。

**ProjectAnalysisJobService 的错误信息是中文，其余全是英文**

`"分析任务不存在"`、`"项目不存在"` — 而其他 15 个 service 错误信息全是英文。中英混杂。

**ProjectAnalysisJobService.startProjectAnalysis() 缺 @Transactional**

先 `jobRepository.save()` 再 `jobRunner.execute()`（异步），没有事务包裹。save 可能还没刷到数据库，异步线程就查不到了。

### 9.7 后端统计

| 指标 | 数值 |
|------|------|
| 审查文件数 | 60+ |
| 死字段 | 1（ImportRecord.rawMarkdown） |
| 死查询方法 | 1（AiSuggestionRepository） |
| 死控制器方法 | 0 |
| 死 service 公共方法 | 0 |
| 重复工具方法 | 9 种，跨 2-7 个文件 |
| @Transactional 误用 | 2 处 |
| 异常被吞 | 3 处 |
| 架构问题 | 4 处 |
