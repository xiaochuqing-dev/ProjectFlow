# ProjectFlow V3.9 复用与研究决策

日期：2026-08-24

No new external architecture research was required.

源码审计表明，V3.8.5 已有稳定事件身份、差异 upsert、31 天增量 overlap、持久化 semantic window checkpoint、租约与恢复、修正覆盖层、Agent Context v2 和 Obsidian manifest projector。V3.9 的问题是把这些机制连接成持续闭环，而不是缺少外部框架。

决定如下：

1. Continuity Delta 从现有 `PersistedEvents` 与前一成功快照派生，不引入 event-sourcing 依赖。
2. 增量篇章复用现有 Chapter Representation Planner 和 checkpoint，不引入新 projector。
3. 修正持久性扩展现有 correction entity，保存有界成员引用；不能证明安全时保持冲突，不引入模糊匹配库。
4. Current Project State 与 Agent Context 继续读取同一持久化 corrected view，不引入向量数据库或第二事实表。
5. Obsidian 继续使用现有逐文件 hash、manifest 和原子写；不安装插件、不增加 daemon/watcher。
6. Story/Thread continuity 由稳定 subject/event identity 决定。模型若参与，只能在现有 bounded known IDs 中改善可替换措辞，不能建立强连接。

如果后续确定性测试证明现有 checkpoint 无法保证新篇章尾部的原子替换，才重新开启针对 materialized-view checkpoint/replay 的一手资料研究；当前没有该阻塞。

