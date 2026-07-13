# Known risks

- V3.3.8.1 对旧批次采用读时默认值而非回填，因此“历史数据不完整”只能保守提示，缺失的原始模型诊断无法恢复。
- Dashboard Bootstrap 当前依赖 Hibernate 派生 latest/count 查询；数据量继续扩大后仍需结合真实数据库索引与慢查询观察，但不得改回全量历史加载。
- sessionStorage 可能被浏览器清理或禁用；这是允许的，F5 必须回退到数据库 Bootstrap，缓存不能成为事实来源。

- 外部 Provider 的真实 max output、JSON mode、reasoning 控制和兼容响应形态可能随模型版本变化；未知 Provider 只能安全退化，仍需真实验收。
- 本机配置模型名为 `deepseek-v4-pro`，本次真实调用可用，但它不是代码内置能力规则；后续 Provider 改名时需重新验收。
- 大输入首次真实返回采用未知集合包装并触发 Schema repair；目标集合递归适配后复验成功，但更多 Provider 私有包装仍可能需要新增无敏感值的 shape diagnostics。
- 项目仍依赖 Hibernate `ddl-auto=update`。V3.3.8 补齐旧 H2 job status enum、计时列和 nullable worktree flag 修复，但尚无完整版本化迁移工具。

- 本地 Docker Desktop 已在收尾验收中启动，PostgreSQL 16 Testcontainers 完整 workflow 通过；仍需以最终远程 CI 作为跨平台阻断证据。
- 项目仍依赖 Hibernate ddl-auto update，没有版本化迁移工具；V3.3.7 新字段保持可空并在加载时补安全默认值，但生产级迁移审计仍有限。
- Java HttpClient 不暴露底层连接池容量配置，当前通过最多 4 个模型请求的公平信号量和最多 4 个任务线程形成实际并发上限。
- 正在进行的单次同步 HTTP 请求不能可靠强制中断；取消会阻止之后的重试、阶段和正式写入，当前请求会等待返回或单次超时。
- 本地 Playwright 使用系统 Edge，CI 安装隔离 Chromium；两者都运行真实前后端进程，但浏览器二进制不同。
- 未完成真实 DeepSeek 联调，原因是执行环境没有安全可用的测试 Key。
- 固定兼容模型服务只证明结构化业务流程与失败分支，不证明真实 DeepSeek 的限流、长输出、网络延迟或 Provider 私有字段行为。
- 部分 Provider 会把主要推理放在 reasoning 字段而 content 为空；不得将其误报为普通空响应。
- 紧凑重试不能无限递归，也不能沿用原输出预算。
- 本地草稿不得通过 DTO 或兼容分支重新进入正式 ProjectChange。
- 能力分析失败不得覆盖上次成功卡片，也不得把待分析沉淀标为已处理。
- 新增字段必须兼容旧行；禁止要求用户删除数据库。
- 前端不得显示 API Key、原始模型响应、内部枚举或默认展开绝对路径。
- GitHub 刷新仍然只读，不执行 pull、merge 或 rebase。
