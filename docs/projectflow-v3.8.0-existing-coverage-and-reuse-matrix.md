# ProjectFlow V3.8.0 现有覆盖与复用矩阵

更新日期：2026-08-02

## 结论

V3.8.0 的最小实现不是扩写 Capability Map，也不是重写 Timeline。应新增一个来源事件实体和一个可替换历程快照实体，复用现有 ProjectFact、Job、Model Gateway、Evidence、Gateway、Hermes 和 Obsidian 投影。

本阶段不引入新的第三方依赖。JDK、Jackson、现有 Git 固定执行边界、JPA、现有哈希和路径保护已经足够。研究对象只提供产品模式和验收反例。

## 覆盖矩阵

| 需求 | 当前覆盖 | 现有证据或入口 | V3.8.0 决策 | 不做什么 |
| --- | --- | --- | --- | --- |
| 强事实 | 完整 | ProjectFact、七种认知状态、Evidence 校验 | 保持唯一强事实源 | 不创建第二套 Fact |
| 完整来源事件 | 缺失 | Commit ref、Fact source、ChangeBatch 仅保存部分引用 | 新增 ProjectHistoryEvent，包含稳定来源身份、时间、类别、Evidence、关系和 stale 状态 | 不把所有事件升级为 Fact |
| 历史扫描 checkpoint | 已覆盖 | ProjectFactHistoryService，每批 25 Commit | 复用 checkpoint、取消、重试和活动任务唯一性 | 不一次读取完整仓库 |
| 一次 Commit 多变化 | 部分 | DevelopmentSegment 能做批内语义分组 | 先由工程生成候选原子，再由模型在已知事件内判断故事成员 | 不仅按 Commit message 分组 |
| 多 Commit 一变化 | 部分 | DevelopmentSegment source indexes | 将跨 Commit 候选聚为 Change Story | 不逐 Commit 调模型 |
| Merge 去重 | 部分 | Git 拓扑和 batch 首尾 revision | 保存 Merge 事件，但与分支事件建立重复或包含关系 | 不删除原始 Merge |
| Revert / reapply | 缺失统一表达 | Commit message、diff、Fact 可能零散存在 | 工程识别明确 Git 信号，模型只解释有 Evidence 的语义 | 不猜测撤销原因 |
| Rename / move | 部分 | changed path、结构索引 | 复用 Git rename/copy 信号和内容 identity，形成 RENAMED/MOVED 转换 | 不只依赖同一路径 |
| Split / merge / replace | 缺失 | 结构区域、文档和 Fact 可作辅助 | 组合路径、内容、引用、时间和有界语义判断 | 不用纯 embedding 相似度 |
| 固定时间 Timeline | 完整 | DAY/WEEK/MONTH/LIFECYCLE | 保持兼容视图 | 不把固定月摘要伪装成动态篇章 |
| 动态时间篇章 | 缺失 | Timeline 月份和 tag 可作边界候选 | 在历程快照中保存动态 chapter | 不自动宣称里程碑或成熟阶段 |
| 变化故事 | 缺失稳定读模型 | DevelopmentSegment、Fact、Timeline Theme | 新增快照中的 story 合同，含 before/change/after/unknowns | 不只复述文件名和 Commit |
| 演变链 | 部分 | Capability Evolution、Evolution Bridge | 新增通用 History Thread；Capability 仅作为可选映射 | 不把所有主体叫 Capability |
| 原始事件分页 | 缺失 | Fact 已有分页 | 为 ProjectHistoryEvent 提供 cursor/page、时间和来源筛选 | 不从快照 JSON 返回全部事件 |
| Evidence 下钻 | 已覆盖基础 | ProjectEvidenceTraceService、Fact trace | 复用并增加 event/story/thread 到 Evidence 的安全引用 | 不返回完整 diff、绝对路径或原始模型响应 |
| 当前项目理解 | 已覆盖 | ProjectUnderstandingSnapshot | 只作为当前状态和结构辅助 Evidence | 不让理解快照成为历史事实 |
| 历史重写 | 部分诊断 | project revision、Git history coverage | 比较 source fingerprint 和 ancestry；标记受影响事件/快照 stale 并重建 | 不静默沿用旧摘要 |
| 缓存 identity | 已覆盖模式 | Understanding fingerprint、Timeline source fingerprint | 使用 project revision、source fingerprint、strategy version、prompt version | 不以时间戳作为缓存身份 |
| 持久化任务 | 完整 | ProjectAnalysisJob、Runner、Scheduler | 新增 PROJECT_HISTORY_REFRESH 类型并复用状态机 | 不新增 workflow engine |
| 模型注册 | 完整 | ModelTaskType、ModelGatewayService | 新增专用 History 输出合同 | 不在业务 Service 直接发 HTTP |
| 模型输入安全 | 完整 | category-aware packing、secret redaction | 只发送相对安全引用和已知 event/evidence ID | 不发送凭证、绝对路径、完整仓库 |
| 失败保留 | 已覆盖模式 | Understanding/Timeline/Capability 上次成功保留 | 历程刷新失败保留上次成功快照并显示 degraded/stale | 不用失败结果覆盖成功结果 |
| Read API | 部分 | Timeline、Gateway GET | 新增 overview、chapters、stories、threads、events、evidence | GET 不扫描、不调用模型、不写事实 |
| Project Memory Gateway | 已覆盖门面 | snapshot、search、recent、timeline、capability、brief | 增加 history 查询与 brief 主轴，旧 DTO 兼容 | 不让消费者直连 Repository |
| Hermes | 已覆盖只读 Adapter | 13 个工具和 projectflow resource | 增加有界 history 工具和资源 | 不增加写工具或远程 MCP |
| Obsidian | 已覆盖非破坏性投影 | manifest、managed block、redirect、conflict、atomic write | 项目概览与项目历程成为主入口，增加官方 URI 和反向本地链接 | 不控制整个 Vault，不强依赖插件 |
| Capability 兼容 | 已覆盖 | ProjectCapability 与既有笔记 | 保留软件项目可选视图和旧链接 | 不作为所有项目的主轴 |
| 最终 GUI | 未覆盖且非目标 | 现有 Timeline 页面只作兼容 | 提供稳定 presentation contract 和可读验收产物 | 不进行全局导航或视觉重构 |
| PostgreSQL | CI 覆盖 | Testcontainers workflow | 新实体加入 H2 和 PostgreSQL 门禁 | 不以 H2 代替 PostgreSQL |
| 真实模型 | V3.7.5 两种 Provider 已合格 | GLM、DeepSeek freeze 与产品 E2E | 用冻结 History 任务重新做当前日期的专项资格测试 | 不沿用旧分数宣称新任务合格 |

## 复用的现有代码边界

- ProjectAnalysisJob 及 Runner：活动任务唯一性、阶段、取消、重试、恢复、heartbeat。
- ModelGatewayService 和 ModelTaskType：协议中立模型调用、参数能力、结构化输出、失败分类。
- ProjectFactHistoryService：有界历史 chunk、checkpoint、重启恢复。
- Git 固定命令执行和 WorkSession 证据：Commit、父子关系、changed paths、rename/copy 信号。
- ProjectFact 和 ProjectEvidenceTraceService：强事实与安全 Evidence 下钻。
- ProjectUnderstandingSnapshot 和 Structure Index：当前状态、结构区域和 Historical Coverage 辅助。
- ProjectMemoryGatewayService：所有权校验和稳定只读门面。
- Hermes stdio Adapter：本地只读 Agent 消费。
- ObsidianProjection：managed root、frontmatter/用户内容保留、manifest、redirect、冲突和原子写入。
- JDK MessageDigest、Jackson 和现有 JSON 字段模式：稳定指纹和有界派生快照。

## 不引入新依赖的理由

研究对象没有提供一个同时满足 ProjectFlow 强事实、通用项目类型、Evidence 追踪、失败保留和非破坏性投影边界的可直接依赖组件。

- GitButler 是 FSL-1.1-MIT，且产品目标是 Git 工作区操作，不适合复制。
- Gource 和 OpenProject 为 GPL-3.0，且功能范围远大于本阶段需要。
- release-please、Changesets、git-cliff、Changie 解决发布说明，不解决完整生命周期证据。
- Obsidian 插件只能是可选外部通道，不能成为 ProjectFlow 核心依赖。
- 已有 JDK、Jackson、JPA 和 Git 执行边界能完成事件归一化、指纹、读模型和投影，不需要新增数据库、图引擎、向量库、parser 或 workflow engine。

## 实现前自审

- 没有修改生产代码。
- 没有复制第三方代码。
- 没有新增依赖。
- 没有改变 ProjectFact 权威。
- 没有把 Capability 继续作为通用主轴。
- 已明确 GET 只读、历史重写 stale、模型已知 ID 和 Obsidian 非破坏性边界。
