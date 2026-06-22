# ProjectFlow 前端审查报告

**审查日期**：2026-06-22
**审查范围**：`frontend/src/` 全部 37 个 `.ts/.tsx` 文件
**审查方法**：源码通读 + `tsc --noUnusedLocals --noUnusedParameters` 静态分析 + 模式 grep 验证
**审查基线**：commit `eb9f788`（含靛蓝精炼风地基 + 工作台重构 + zip 导入/分析按钮修复）

---

## 总体评价

| 维度 | 评级 | 说明 |
|---|---|---|
| **安全** | 良好 | 零 XSS 向量、零硬编码密钥、认证一致、apiKey 处理规范。主要缺口是纵深防御层面（localStorage 存 JWT、无 CSP）。 |
| **冗余** | 中等 | 经 tsc 验证仅 4 处确定性死代码（全在 tasks 页）。但有大量"本地重复组件"和"未迁移到共享库的旧模式"，属技术债而非 bug。 |
| **架构** | 良好 | lib 层（auth/api/project-selection）抽象干净，localStorage 只在 2 个 lib 文件集中处理。 |

**关键结论**：没有发现 critical 级别的安全漏洞或功能性 bug。最值得处理的是 tasks 页的 4 处死代码，以及将重复的本地组件迁移到已有的 `components/ui/` 共享库。

---

## 第一部分：冗余代码

### 1.1 确定性死代码（高确定性，tsc 验证）

全部集中在 `app/tasks/page.tsx`，是页面重构后遗留的孤儿：

| # | 位置 | 类型 | 说明 |
|---|---|---|---|
| 1 | `app/tasks/page.tsx:109` | 未使用 state | `const [savingChange, setSavingChange]` —— setter 从未被调用，state 值从未读取 |
| 2 | `app/tasks/page.tsx:210` | 未使用函数 | `async function handleSaveChange` —— 定义了完整的表单提交逻辑，但 JSX 中无任何调用点 |
| 3 | `app/tasks/page.tsx:323` | 未使用函数 | `async function handleIgnore` —— 忽略候选建议的 handler，但按钮已改用 `handleIgnoreChange` |
| 4 | `app/tasks/page.tsx:678` | 未使用函数 | `function sourcePreview(suggestion)` —— 计算 payload 预览文本，但渲染处已删除 |

**验证方式**：`npx tsc --noEmit --noUnusedLocals` 对全量 37 个文件运行，仅这 4 处报 TS6133。其余文件零死代码。

**严重度**：高（应删除）。这 4 项合计约 40 行未执行代码，含一个完整的异步 handler，会误导后续维护者以为该功能仍接线。

### 1.2 本地重复组件（中确定性，应迁移到共享库）

第一阶段已建立 `components/ui/` 共享库（Card/Button/Badge/Stat/EmptyState/Toast/ProjectContextBar/PageContainer），但**只有 dashboard 完成了迁移**。以下页面仍保留功能等价的本地副本：

| 本地组件 | 位置 | 对应共享组件 | 严重度 |
|---|---|---|---|
| `MiniFact` | `dashboard/page.tsx:1597` | `Stat`（components/ui） | 中 |
| `SourcePanel` | `dev-logs/page.tsx:302` | `Card` + `SectionHeader` | 中 |
| `StatusPill`（无 tone） | `dev-logs/page.tsx:383` | `Badge` | 中 |
| `StatusPill`（带 tone） | `tasks/page.tsx:660` | `Badge` | 中 |
| `FlowStep` | `tasks/page.tsx:669` | （无直接对应，可内联或新建） | 低 |
| `Metric` | `work-sessions/[sessionId]/page.tsx:173` | `Stat` | 中 |

**注意**：`StatusPill` 在 dev-logs 和 tasks 有**两个签名不同的副本**（dev-logs 版本无 `tone` 参数），这正是当初建立共享 `Badge` 的原因。

**严重度**：中。功能正常，但违反 AGENTS.md 的 ponytail 冗余控制规则，且两套 `StatusPill` 签名不一致是潜在维护陷阱。

### 1.3 未迁移的旧 UI 模式（中等，技术债）

#### Toast 重复（8 处内联）
以下 4 个文件仍逐字复制 error/notice 两行 toast，未使用共享 `Toast` 组件：
- `app/ai-review/page.tsx:337-338`
- `app/dev-logs/page.tsx:294-295`
- `app/project-intelligence/page.tsx:373-374`
- `app/tasks/page.tsx:653-654`

每处 2 行 × 4 文件 = 8 行重复，外加 `fixed bottom-5 left-1/2 z-50 -translate-x-1/2 ... shadow-panel` 的完整 className。

#### 项目选择器重复（6 个页面内联）
以下页面仍内联 `<select> + rememberSelectedProjectId + projects.map` 模式，未使用共享 `ProjectContextBar`：
- `app/ai-review/page.tsx`
- `app/dev-logs/page.tsx`
- `app/imports/page.tsx`
- `app/project-intelligence/page.tsx`
- `app/settings/page.tsx`
- `app/tasks/page.tsx`

#### shadow-panel 旧样式（约 60 处跨 20 文件）
新设计系统提供 `shadow-card`，但除 dashboard 外的所有页面仍用旧的 `shadow-panel`。这是**预期的过渡态**（第一阶段只迁移了地基 + dashboard），属计划内技术债。

### 1.4 console / 调试残留

**全量扫描 `console.log|debug|warn|error|info`：零匹配。** 生产代码干净，无调试残留。

### 1.5 未使用依赖

`package.json` 依赖：`next`、`react`、`react-dom`、`lucide-react`（4 个运行时依赖）。全部在源码中被广泛使用。devDependencies 均为构建工具链，无冗余。

---

## 第二部分：安全审查

### 2.1 XSS 与代码注入 —— ✅ 无风险

- **零** `dangerouslySetInnerHTML` 使用
- **零** `eval()` / `new Function()` / `.innerHTML` 赋值
- 所有用户内容通过 React JSX 渲染（自动转义）
- Markdown 导入预览也是纯文本展示

**结论**：当前无 XSS 向量。

### 2.2 密钥与令牌处理 —— ✅ 良好

- **零硬编码密钥**：全量 grep `apiKey|secret|password`，无任何字面量密钥
- **apiKey 输入规范**（`settings/page.tsx:204`）：`type="password"`，placeholder 明确"只保存到后端，不回显"
- **前端只持有布尔值**：`AiProvider.apiKeyConfigured: boolean`，明文 key 从不进入前端 state 或 localStorage
- **令牌不进日志**：全量 grep console 无任何 token 输出

### 2.3 认证一致性 —— ✅ 良好

- **所有受保护页面都调用 `readSession()`**：经 grep 验证，dashboard/tasks/dev-logs/project-intelligence/ai-review/settings/imports/projects 等全部页面均在 useEffect 或 handler 开头校验 session
- **AppShell 守卫**：`AppShell.tsx:31` 在 `readSession()` 返回 null 时 `router.replace("/login")`
- **登录/注册隔离**：`/login`、`/register` 使用独立的 `AuthPanel`/`AuthPageShell`，不经过 AppShell

### 2.4 API 层 —— ✅ 良好

- **API base**：`api.ts:3` `process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api"`，env 优先，fallback 合理
- **错误处理不泄露内部细节**：`api.ts:64-66` 错误信息来自后端 `payload.error.message`，不暴露堆栈；网络错误用友好中文提示
- **所有受保护请求带 Bearer token**：`api.ts` 的 `authedJson` 包装统一注入 `Authorization: Bearer ${token}`

### 2.5 需关注的安全改进项（纵深防御）

| # | 问题 | 位置 | 严重度 | 说明 |
|---|---|---|---|---|
| S1 | JWT 存 localStorage | `auth.ts:16-17` | 中 | localStorage 可被 XSS 读取。当前无 XSS 向量故风险可控，但理想方案是 httpOnly cookie + CSRF token。这是 SPA 常见权衡，非紧急。 |
| S2 | `readSession()` 不校验 token 过期 | `auth.ts:20-36` | 中 | 只检查 token 存在与 JSON 合法，不解析 JWT exp。过期 token 会持续被视为登录态，直到 API 返回 401。建议：解析 exp 字段，过期则 clearSession。 |
| S3 | projectPath 无客户端校验 | `dashboard/page.tsx:325,347,369` 等 | 中 | 仅 `.trim()` 非空检查，不校验路径合法性（如 `..` 越界、绝对路径）。**权威防护在后端 ownership/路径检查**，前端校验仅为体验层。建议加基础格式提示。 |
| S4 | 无 Content Security Policy | `app/layout.tsx` | 低 | 无 CSP meta 或 header。Next.js 可在 `next.config.ts` 配置 headers。本地工具优先级低，但上线前应补。 |
| S5 | zip 上传仅靠 `accept=".zip"` | `dashboard/page.tsx` 导入表单 | 低 | `accept` 只是浏览器提示，不阻止用户选其他文件。后端应做 MIME/魔数校验（属后端职责，此处仅提示）。 |

**说明**：S1-S5 均为纵深防御改进，**无一项是当前可利用的漏洞**。本地优先工具的安全模型下，这些是"上线前应处理"而非"立即修复"。

### 2.6 localStorage 使用清单

集中且克制，仅两个 lib 文件操作 localStorage：
- `lib/auth.ts`：`projectflow_access_token`（JWT）、`projectflow_user`（用户名/邮箱/ID，非敏感）
- `lib/project-selection.ts`：`projectflow_selected_project`（上次选中的项目 ID）

无敏感数据（密码、apiKey、项目源码内容）进入 localStorage。

---

## 第三部分：建议处理优先级

### 立即处理（高确定性、低风险）
1. **删除 tasks/page.tsx 的 4 处死代码**（1.1 节）—— 纯删除，无行为变化，约 40 行。

### 短期处理（技术债收敛）
2. **将 6 个本地重复组件迁移到 components/ui/**（1.2 节）—— 消除两套 StatusPill 签名不一致的陷阱。
3. **4 个页面用共享 Toast 替换内联**（1.3 节）—— 每页删 2 行。
4. **6 个页面用 ProjectContextBar 替换内联选择器**（1.3 节）—— 统一项目切换交互。

### 中期处理（第二阶段，计划内）
5. **剩余页面从 shadow-panel 迁移到 shadow-card/Card**（1.3 节）—— 这是第一阶段确立的渐进策略。

### 上线前处理（安全纵深）
6. **`readSession()` 加 JWT exp 校验**（S2）—— 10 行改动，明显改善过期 token 体验。
7. **补 Content Security Policy**（S4）—— next.config.ts 配置 headers。
8. **评估 JWT 迁移到 httpOnly cookie**（S1）—— 架构级调整，需后端配合。

---

## 审查方法说明

- **冗余**：`npx tsc --noEmit --noUnusedLocals --noUnusedParameters`（全量 37 文件，权威）+ grep 模式匹配（重复组件、console、shadow-panel）
- **安全**：分类 grep（XSS/密钥/认证/注入）+ 关键文件通读（auth.ts、api.ts、settings 页、dashboard）
- **未覆盖**：后端、依赖漏洞库（npm audit）、运行时行为、可访问性（a11y）。这些超出本次前端静态审查范围。

---

*本报告由代码审查生成，描述的是 commit `eb9f788` 时的代码状态。后续改动可能使部分发现失效。*
