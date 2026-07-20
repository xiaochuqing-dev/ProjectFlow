# V3.4.4 requirements

1. Obsidian 必须复用 Project Memory Gateway，保持 ProjectFact 唯一事实来源与 occurredAt/eventAt 时间语义。
2. 默认 CORE 生成 Overview、月度 Timeline、长期 Capability、月度 Fact Index 和导航索引，不默认一 Fact 一文件。
3. 每个 Note 使用稳定 frontmatter；同步由 entity/version/hash/projection version/manifest 驱动，UNCHANGED 零写入。
4. 只允许写现有 Vault 下的专用 managed root 和 managed block，用户内容必须保留；冲突不得静默覆盖。
5. 路径遍历、symlink/junction、Windows 保留名、非法字符、Unicode、大小写碰撞必须安全处理，文件与 manifest 原子写入。
6. CLI 提供 validate、dry-run、status、sync；默认不调用模型，不新增前端或全局配置。
7. rename 保持稳定链接，merge 保留旧 Note、历史与 redirect；中断和 manifest 损坏可恢复。
8. 使用 5000 facts/36 months/100 capabilities/1000 evolutions 与当前 H2 安全副本验收，完整真实状态写入 V3.4.4 report。

# V3.4.3 requirements

1. 建立统一只读 Project Memory Gateway，覆盖 Snapshot、Recent、Search、Timeline、Capabilities、Evolution、Fact Trace 和 Brief。
2. ProjectFact 保持唯一事实来源；所有派生层、稳定 ID、证据和时间语义必须明确。
3. Recent 和 Timeline 按 occurredAt；7 月 17 日发生、8 月 20 日分析仍属于 7 月。
4. 所有读取校验 userId/projectId，compact 默认、分页/硬上限，GET 不触发模型。
5. 审计不保存完整 query、caller、凭证或内部敏感内容。
6. Hermes 只通过 repository-local stdio MCP 读取 loopback backend，工具数量克制且全部只读。
7. 前端冻结，不新增正式集成页面、一级导航或视觉重构。
8. H2/PostgreSQL/当前安全副本保持兼容，不清库，不修改系统或全局机器配置。

# V3.3.6 requirements

1. 空正文结合 finish reason、token 用量和 reasoning 字段识别疑似截断，并只进行一次低预算紧凑重试。
2. 统一记录请求次数、用量来源、实际参数、耗时及各阶段失败诊断，不展示 Key 和原始响应。
3. 外部模型调用期间不得持有数据库长事务。
4. 本地规则和 Agent result 只能形成本地草稿，不能自动生成正式沉淀建议。
5. 沉淀处理中心按批次和时间组织，正式建议逐条处理，本地草稿单独展示。
6. 已确认沉淀保存来源批次、涉及文件、内容来源、质量状态及能力形成状态。
7. 能力分析只消费已确认沉淀，成功后回写形成能力或已分析未形成能力；失败时保留待分析状态。
8. 新字段保持 H2 和 PostgreSQL 既有数据兼容，不要求重建数据库。

# Still-effective requirements

保留 V3.3.5 的模型诊断、完整正文、能力 job 与 Provider 安全规则；保留 V3.3.4 的中文提示、只读 GitHub 和证据缺口规则；保留 V3.3.3 的用户确认边界及核心项目接入入口。
