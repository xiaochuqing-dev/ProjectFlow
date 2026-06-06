# ProjectFlow V2 Agent Bridge Implementation Status

## 本轮做到哪了

本轮已经把 ProjectFlow 和 agent 的基础交互从“规划文档”推进到可运行的第一版闭环。

已完成：

- ProjectFlow 可以在真实项目文件夹下写入 `.projectflow` 工作目录。
- 已生成项目级协议文件 `.projectflow/agent-protocol.md`。
- 已生成项目上下文文件：
  - `.projectflow/context/project-profile.md`
  - `.projectflow/context/requirements.md`
  - `.projectflow/context/confirmed-decisions.md`
  - `.projectflow/context/known-risks.md`
  - `.projectflow/context/update-history.md`
- 开发者不一定要先通过 ProjectFlow 生成任务说明，可以直接在 agent 里描述需求。
- agent 只需要读取 `.projectflow/agent-protocol.md`，完工后按协议把结果写到 `.projectflow/inbox/` 或任务目录的 `result.md`。
- ProjectFlow 可以扫描 agent 写回的 result 文件，并转成待确认建议。
- 扫描结果不会直接改真实任务状态，仍然需要用户确认。
- ProjectFlow 现在也可以为某个已有任务生成可选的任务级 brief。
- 任务级 brief 会写入：
  - `.projectflow/tasks/<task-id>/brief.md`
  - `.projectflow/tasks/<task-id>/result.md`
  - `.projectflow/tasks/<task-id>/status.json`
- 项目管理页顶部已经增加紧凑操作条：
  - 项目文件夹路径
  - 本次需求
  - 写入协议
  - 扫描更新
  - 复制全局 agent 规则
- 项目管理页已经增加紧凑任务队列，任务行可以直接写入 brief。
- 待确认建议会显示任务引用和来源文件，方便用户判断 agent 结果来自哪里。
- 独立“项目档案”主导航已经移除，项目档案回到“项目管理”内部展示。
- 后端已增加针对协议写入和 agent 结果扫描的测试。

## 当前实现方式

### 1. 写入协议

接口：

```text
POST /api/projects/{projectId}/agent-bridge/protocol
```

输入：

```json
{
  "projectPath": "C:\\path\\to\\real-project",
  "requirements": "本次需求或限制"
}
```

行为：

- 校验项目属于当前用户。
- 校验项目文件夹存在。
- 创建 `.projectflow` 目录。
- 写入 agent 协议和项目上下文。
- 返回全局 agent 规则，方便用户放到 Codex、Cursor、Claude Code 等工具的全局记忆里。

### 2. 扫描 agent 更新

接口：

```text
POST /api/projects/{projectId}/agent-bridge/scan
```

ProjectFlow 会读取：

```text
.projectflow/inbox/agent-result.md
.projectflow/inbox/*-agent-result.md
.projectflow/tasks/*/result.md
```

然后生成待确认建议：

- 开发日志建议。
- 项目记忆更新建议。
- 新任务或后续任务建议。
- 技术决策建议。
- 风险建议。

处理过的 result 文件会写入 `.processed` 标记，避免重复导入。

### 3. 任务级 brief

接口：

```text
POST /api/projects/{projectId}/agent-bridge/tasks/{taskId}/brief
```

用途：

- 这是可选路径。
- 用户已经在 ProjectFlow 里确认某个任务时，可以生成更明确的 agent 工作说明。
- 开发者如果想直接在 agent 里提需求，也可以完全不走这个路径。

生成文件：

```text
.projectflow/tasks/<task-id>/brief.md
.projectflow/tasks/<task-id>/result.md
.projectflow/tasks/<task-id>/status.json
```

## 现在还没做的部分

- 还没有做文件夹选择器，目前项目路径需要用户手动输入。
- 还没有把 `.projectflow` 写入能力扩展到 ZIP 导入后的自动推荐路径。
- 还没有做用户最终使用视角的完整端到端流程测试。

## 下一步建议

### 1. 改进 result 解析

下一步可以把解析结果拆得更细：

- 哪些是已完成事实。
- 哪些是 agent 推断。
- 哪些是风险。
- 哪些是新任务。
- 哪些需要用户补充需求。

这样 ProjectFlow 的确认面板会更像代码 review，而不是简单日志列表。

### 2. 强化项目管理页布局

继续减少大卡片，改成更像开发工具的结构：

- 顶部操作条。
- 左侧项目和资料。
- 中间任务队列与待确认建议。
- 右侧项目档案、风险、决策。

空内容区域尽量使用长条输入、表格行、紧凑面板，不再堆大卡片。

### 3. 加入文件夹选择体验

浏览器不能直接可靠拿到本地绝对路径，所以后续可以考虑：

- 继续手动输入路径作为最低成本方案。
- 后端提供最近项目路径记录。
- 桌面壳或本地 helper 后续再做文件夹选择。

当前阶段不建议为了文件夹选择引入复杂桌面端能力。

### 4. 最后做完整流程测试

等任务级 brief 和确认面板稳定后，再做完整测试：

1. 导入项目 ZIP。
2. 写入 `.projectflow` 协议。
3. 在 agent 中直接提需求。
4. agent 写回 result。
5. ProjectFlow 扫描更新。
6. 用户确认建议。
7. 任务、项目档案、开发日志同步更新。

## 当前结论

现在已经打通了最重要的底座：开发者可以继续以 agent 为主要工作入口，ProjectFlow 负责协议、识别、记录和确认。

下一步不要急着做完整测试，应该先把任务级 brief 和确认面板完善到真正好用，再做完整流程验收。

## 如果以后发展成桌面端，代价大不大

理性判断：中等成本，不是现在最该优先做的事。

桌面端的主要收益：

- 可以真正选择本地项目文件夹，不需要用户手动输入路径。
- 可以更稳定地读写 `.projectflow`。
- 可以监听 agent result 文件变化，自动提示“发现新更新”。
- 可以更像开发工具，而不是普通网页后台。

但代价也比较明确：

- 需要引入 Tauri、Electron 或类似桌面壳。
- 需要处理 Windows/macOS 路径权限、签名、更新、安装包。
- 需要重新设计本地文件访问的安全边界。
- 前后端启动方式会变复杂。
- 测试矩阵会扩大，尤其是文件权限和路径行为。

如果只是为了当前阶段“让 ProjectFlow 和 agent 交互起来”，桌面端不是必需。当前 Web + 手动路径输入 + `.projectflow` 协议已经能验证核心产品价值。

更合理的路线是：

1. 先把 Web 版核心闭环做顺。
2. 确认用户确实高频使用本地项目文件夹桥接。
3. 再考虑 Tauri 桌面端。

如果未来做桌面端，Tauri 比 Electron 更适合这个产品：包体更小，更像本地工具，适合只补“文件夹选择、文件监听、启动本地服务”这些能力。Electron 生态更成熟，但包体和资源占用更大。
