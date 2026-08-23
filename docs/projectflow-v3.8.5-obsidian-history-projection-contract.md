# V3.8.5 Obsidian 项目历程投影合同

Obsidian 仍是 Project Memory Gateway 的长期阅读投影，不是事实源、数据库镜像或修正存储。同步只读取 Gateway 返回的 corrected history，不调用模型、不读取仓库、不写回 ProjectFact、Event、Timeline、Capability 或 Evolution。

密度策略

- CORE：总览、篇章、主要 Story、必要演变链和有冲突/未知/置顶/用户声明的 Supporting Story；大历史不默认一 Story 一文件。
- EXTENDED：在 CORE 基础上增加重要 Supporting Story 和更多演变链。
- FULL：用户显式选择后投影全部 Story/Thread 和工程详情。

每个 Note 使用稳定 entity ID、source version、content hash、projection version 和 managed block。用户 frontmatter、managed block 外内容、移动或重命名的受管 Note 必须保留；重复身份、路径逃逸、symlink/junction、非法文件名和写入中断进入 conflict 或安全失败。无变化时零写入。

历史修正

Overview 显示 presentation revision 和 active correction 摘要；Story Note 使用修正后的标题、摘要、role、隐藏/置顶、冲突和自动版本。恢复自动展示只停用覆盖层，不删除事实证据。官方 URI 是基线，Advanced URI 仅在可用时增强，失败必须安全降级。
