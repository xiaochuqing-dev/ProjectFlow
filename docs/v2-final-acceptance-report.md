# ProjectFlow V2 Final Acceptance Report

## 当前 V2 状态

V2 已经进入可以做完整人工验收的阶段。

当前主线已经打通：

1. 导入完整项目 ZIP，生成项目档案和待确认建议。
2. 在项目管理页填写真实项目文件夹路径。
3. 写入 `.projectflow/agent-protocol.md` 和项目上下文文件。
4. ProjectFlow 会把真实项目文件夹路径保存进项目记忆，下次选择项目时自动回填。
5. 开发者可以直接在 agent 里描述需求，不强制先让 ProjectFlow 出任务书。
6. 如果用户选择已有任务，也可以写入任务级 brief。
7. agent 完工后按固定格式写入 result。
8. ProjectFlow 扫描 `.projectflow`，生成待确认建议。
9. 用户确认后再写入任务、项目记忆、开发日志和演进记录。

## 本轮完成内容

### Agent Bridge

已完成：

- `.projectflow/agent-protocol.md` 写入。
- `.projectflow/context/*` 项目上下文写入。
- 本地项目路径保存到 ProjectFlow 项目记忆。
- 重新进入项目管理页时自动回填已保存路径。
- `.projectflow/tasks/<task-id>/brief.md` 写入。
- `.projectflow/tasks/<task-id>/result.md` 预置。
- `.projectflow/tasks/<task-id>/status.json` 写入。
- `.projectflow/inbox/*-agent-result.md` 扫描。
- `.projectflow/tasks/*/result.md` 扫描。
- 已处理 result 文件会生成 `.processed` 标记，避免重复导入。

### TaskId 绑定

已完成：

- 如果 agent result 里的 `TaskId` 是真实 ProjectFlow 任务 UUID，扫描时会识别并绑定。
- 待确认建议 payload 会带上 `taskId` 和 `taskTitle`。
- 绑定不等于自动完成任务，任务状态仍然必须由用户确认后更新。

### Result 诊断

已完成：

- 非 `# ProjectFlow Agent Result` 格式的文件不会被导入。
- ProjectFlow 会返回 warnings。
- 前端会显示扫描 warnings，方便用户修正 agent 输出格式。

### 安全边界

已完成：

- 后端会拒绝过宽的系统级路径，例如磁盘根目录。
- 系统目录如 Windows、Program Files 也被视为不适合作为项目路径。
- API key 创建接口不会把密钥原文返回给前端。
- ZIP 导入会排除 `.env`、`.git`、`node_modules`、构建产物和二进制文件。
- agent result 不会直接修改真实任务状态，只生成待确认建议。

## 如何启动

在项目根目录运行：

```powershell
.\start-projectflow.ps1
```

如果脚本不可用，可以分别启动：

```powershell
cd backend
C:\Users\Administrator\Desktop\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
```

```powershell
cd frontend
npm.cmd run dev
```

默认地址：

```text
Frontend: http://localhost:3000
Backend:  http://localhost:8080
```

## 完整人工测试流程

### 1. 登录或注册

进入前端页面后注册一个测试账号。

### 2. 进入项目管理

进入：

```text
/dashboard
```

确认首页优先显示：

- 项目选择。
- 项目文件夹路径。
- 本次需求。
- 写入协议。
- 扫描更新。
- 导入 ZIP。
- 项目档案。
- 任务队列。
- 待确认建议。

### 3. 导入完整项目 ZIP

上传一个真实项目 ZIP。

预期：

- 如果没有选择项目，会按 ZIP / 文件夹名自动创建项目。
- 项目档案会显示技术栈、目录结构、README、测试、启动脚本、部署配置等信息。
- 待确认建议会出现，但不会自动创建任务。

### 4. 采纳初始建议

选择待确认建议并点击采纳。

预期：

- 项目记忆更新。
- 任务候选被写入任务列表。
- 开发日志和演进记录生成。
- 任务队列能看到新任务。

### 5. 写入 ProjectFlow 协议

在顶部输入真实项目文件夹路径，例如：

```text
C:\Users\Administrator\Documents\Codex\SomeProject
```

点击“写入协议”。

预期项目文件夹下出现：

```text
.projectflow/
  agent-protocol.md
  context/
    project-profile.md
    requirements.md
    confirmed-decisions.md
    known-risks.md
    update-history.md
  inbox/
  tasks/
```

同时，ProjectFlow 会把该绝对路径保存到项目记忆。以后重新进入项目管理页或切换回该项目时，路径输入框会自动回填，不需要再次手动填写。

### 6. 可选：写入任务 brief

如果任务队列里已有任务，点击任务行的“写入 brief”。

预期出现：

```text
.projectflow/tasks/<task-id>/brief.md
.projectflow/tasks/<task-id>/result.md
.projectflow/tasks/<task-id>/status.json
```

### 7. 在 agent 里工作

开发者可以直接在 agent 里说需求。

开始前让 agent 读取：

```text
.projectflow/agent-protocol.md
```

如果是任务级工作，让 agent 读取：

```text
.projectflow/tasks/<task-id>/brief.md
```

### 8. 写回 agent result

让 agent 完工后写入：

```text
.projectflow/inbox/20260606-1800-agent-result.md
```

或者：

```text
.projectflow/tasks/<task-id>/result.md
```

格式：

```markdown
# ProjectFlow Agent Result

ProjectId: <project-id-or-name>
TaskId: <task-uuid-if-known>
Status: ready_for_review

## Summary
本轮完成了什么。

## Changed Files
- path/to/file

## Task Updates
- <task-id>: ready_for_review
- New: 后续任务

## Decisions
- 需要用户确认的决策

## Risks
- 风险或未完成项

## Dev Log
本轮开发过程记录。
```

### 9. 扫描更新

回到 ProjectFlow，点击“扫描更新”。

预期：

- 合法 result 会变成待确认建议。
- 真实任务 UUID 会显示任务引用。
- 非法 result 不会导入，会显示 warning。
- 真实任务状态不会自动改变。

### 10. 采纳建议

选择建议并采纳。

预期：

- 开发日志被创建。
- 项目记忆被更新。
- 风险和决策被沉淀。
- 新任务建议进入任务队列。
- 演进记录更新。

## 自动化验证结果

本轮需要通过：

```powershell
cd backend
C:\Users\Administrator\Desktop\apache-maven-3.9.9\bin\mvn.cmd -q test
```

```powershell
cd frontend
npm.cmd run build
```

安全相关覆盖点：

- API key 不泄漏。
- ZIP 导入过滤 `.env`。
- `.projectflow` 拒绝磁盘根目录等过宽路径。
- 无效 agent result 不导入，只返回 warning。
- agent result 只生成待确认建议，不直接改任务状态。

### 本轮实测结果

已在 2026-06-06 跑完：

- `backend`: `C:\Users\Administrator\Desktop\apache-maven-3.9.9\bin\mvn.cmd -q test` 通过，退出码 0。
- `frontend`: `npm.cmd run build` 通过，退出码 0。
- 启动冒烟：后端用 `SPRING_PROFILES_ACTIVE=test` 和 `APP_PORT=18080` 启动，`GET http://127.0.0.1:18080/api/health` 返回 200，内容为 `{"service":"projectflow-api","status":"UP"}`。
- 启动冒烟：前端用 `npm.cmd run dev -- --hostname 127.0.0.1 --port 3100` 启动，`GET http://127.0.0.1:3100/login` 返回 200，页面标题为 `ProjectFlow`。
- 冒烟测试结束后已确认测试端口 `18080` 和 `3100` 没有残留监听。

静态安全检查结果：

- 未发现真实 API key、私钥块或生产密钥落入源码。
- `sk-test-secret-value`、`DATABASE_PASSWORD=secret` 只存在于测试用例中，用于验证 API key 不回显和 ZIP `.env` 过滤。
- `application.yml` 里仍有本地默认占位值 `change-me-local` 和 JWT 占位密钥，适合本地启动，不适合生产部署；生产部署必须通过环境变量覆盖。
- `.projectflow` 文件写入入口已经限制过宽路径，例如磁盘根目录、Windows、Program Files。
- `.projectflow` 写入、扫描更新、写入任务 brief 时都会把规范化后的项目路径同步到项目记忆。
- agent result 导入只接受包含 `# ProjectFlow Agent Result` 的文件；非法结果只返回 warning，不写入建议。

环境噪声：

- Maven / Java 25 在本机输出了 Jansi、Unsafe、Mockito 动态 agent 等警告。
- 日志里出现一条 Windows `Access is denied` 噪声，但测试、构建和健康检查均未失败。
- Codex 当前 PowerShell 环境存在 `Path` / `PATH` 重复键，直接 `Start-Process` 会报错；本轮冒烟时已在当前进程内临时规范化环境变量后启动，不影响项目代码。

## 当前限制

- Web 版仍然需要手动输入项目文件夹路径。
- 浏览器无法稳定提供真实本地绝对路径选择。
- 还没有桌面端文件监听。
- 还没有把待确认建议做成完整代码 review 风格的 diff 面板。
- 还没有对 agent result 做更复杂的冲突合并。

这些限制不阻塞 V2 完整人工测试。

## V3 展望

V3 应该围绕“更自然、更自动、更接近开发工具”推进。

建议方向：

1. 桌面端或本地 helper。
   - 解决文件夹选择、文件监听、路径权限和本地服务启动。
   - 推荐优先评估 Tauri，不建议一开始就上 Electron。
   - 理性成本判断：做成桌面端不是重写产品，但也不是一两天的小改。当前前端可以复用，后端接口和 `.projectflow` 协议也可以复用；主要新增成本在本地文件权限、打包安装、自动更新、日志诊断、Windows/macOS 差异和后台服务管理。低成本路线是先做 Tauri 本地 helper，只负责选文件夹、监听 `.projectflow`、启动/连接本地服务；不要一开始把整个系统重做成大型桌面客户端。

2. 自动扫描 agent 更新。
   - 监听 `.projectflow/inbox/` 和 `.projectflow/tasks/*/result.md`。
   - 发现新 result 后自动提示用户确认。

3. Review 式确认面板。
   - 把 agent result 拆成事实、推断、风险、决策、任务变化。
   - 用户可以逐条接受、修改或拒绝。

4. MCP / Agent API。
   - 让 agent 不只读写文件，也能通过协议查询 ProjectFlow 当前上下文。
   - 仍然保持“agent 提交建议，用户确认事实”的边界。

5. 项目长期记忆质量提升。
   - 区分 confirmed / inferred / rejected。
   - 让下一轮 brief 只引用可信上下文。

6. 多 agent 会话管理。
   - 记录哪个 agent、哪次会话、哪批文件、哪条 result。
   - 支持冲突提示，而不是直接合并。

V3 不应该变成更大的管理后台。它应该继续保持开发工具取向，让开发者主要在 agent 里工作，ProjectFlow 负责项目事实、追踪和确认。
