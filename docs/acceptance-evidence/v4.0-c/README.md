# V4.0-C Acceptance Evidence

状态：本地验收 PASS；GitHub 执行状态见 [ci.json](ci.json)。所有截图均来自本轮实际浏览器运行；不包含真实用户项目或真实服务凭据。

## 可复核记录

- [verification.json](verification.json)：测试计数、条件跳过、依赖审计与保留的失败记录。
- [provider-smoke.json](provider-smoke.json)：真实 Java API、临时合成 Provider、本地固定模型和 Windows DPAPI；不是外部模型质量验收。
- [windows-launcher.json](windows-launcher.json)：从工作区外相对调用根启动器、重建、欢迎页进入 V4、正常退出与端口检查。
- [visual-manifest.json](visual-manifest.json)：29 张截图的实际像素尺寸、SHA-256、数据来源和 Agent 视觉检查结论。
- [阶段报告](../../projectflow-v4.0-c-gui-productization-report.md)：实现、边界、失败修复与交付说明。

## 六页与深层阅读

项目库、当前状态、项目设置、交接和响应式 Provider 表单使用显式 Demo；History、Provider 管理、长 Thread 与状态用例使用合成测试 DTO。实际 Java 路径另列于末节。截图中的示例不能证明真实模型输出质量。

| 内容 | 截图 |
| --- | --- |
| 项目库 | [projects-desktop.png](projects-desktop.png) |
| 当前状态 | [current-desktop.png](current-desktop.png) |
| 时间篇章 | [history-chapter.png](history-chapter.png) |
| 主题目录 | [history-thread-list.png](history-thread-list.png) |
| 主线详情 | [thread-detail.png](thread-detail.png) |
| Story 与 Evidence | [story-evidence.png](story-evidence.png) |
| Provider 列表与凭据状态 | [provider-list.png](provider-list.png) |
| Provider 编辑 | [provider-edit.png](provider-edit.png) |
| 连接失败 | [provider-connection-error.png](provider-connection-error.png) |
| 保存失败 | [provider-save-error.png](provider-save-error.png) |
| 项目设置 | [project-settings-desktop.png](project-settings-desktop.png) |
| 交接 | [handoff-desktop.png](handoff-desktop.png) |
| 旧成功状态与过期提示 | [current-live-stale.png](current-live-stale.png) |
| 服务读取失败 | [current-service-error.png](current-service-error.png) |

## 响应式与长内容

| 宽度 | Provider 高级表单 | 长 Thread 阅读 |
| --- | --- | --- |
| 390 | [表单](provider-form-390.png) | [主线](thread-reading-390.png) |
| 640 | [表单](provider-form-640.png) | [主线](thread-reading-640.png) |
| 1024 | [表单](provider-form-1024.png) | [主线](thread-reading-1024.png) |
| 1280 | [表单](provider-form-1280.png) | [主线](thread-reading-1280.png) |
| 1600 | [表单](provider-form-1600.png) | [主线](thread-reading-1600.png) |

Story/Evidence 的 [390px](story-evidence-390.png) 与 [1600px](story-evidence-1600.png) 另验长文本与缺失边界。主视觉基准和 1280px 宽度矩阵采用 801px 高，其他矩阵采用 900px 高；部分默认用例采用 1280×720，具体尺寸以 manifest 为准。没有把不同尺寸称为像素级对比。

Agent 已逐组检查可读性、截断、横向溢出、表单与详情布局、错误反馈和原有素材一致性。截图检查发现的 640px 多余导航关闭按钮已修复，并补充从 390px 放大到 640px 的自动回归。Tab、Escape、焦点恢复、inert 和 Demo 零写入由 Playwright 断言验证；静态图片本身不证明交互行为。

## 实际 Java 与 Windows 路径

- [real-backend-thread-story-evidence.png](real-backend-thread-story-evidence.png)：隔离临时项目已持久化 History；V4 Thread → Story → Evidence 只读闭环，非 Demo。
- [real-backend-provider-credential.png](real-backend-provider-credential.png)：隔离 Provider 经创建、空 Key 编辑与重新读取后保留已配置状态，输入不回显。
- [windows-workspace-real.png](windows-workspace-real.png)：根启动器实际重建并运行后的真实空项目库；未回退到示例。

可提交材料只保留合成数据截图、相对路径、状态、计数和摘要。原始 trace、含临时绝对路径的日志、数据库、secure-store 文件及运行目录不属于本 Evidence。
