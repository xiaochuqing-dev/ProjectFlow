# ProjectFlow V3.9 Obsidian 增量投影

## 复用边界

V3.9 继续使用现有 Obsidian CLI、managed block、per-file content hash、manifest、原子替换和冲突保护，不重新实现 sync engine，不安装 watcher/daemon，不自动运行 Git。

## Current State 输入

Gateway dataset 增加 persisted Current Project State。Project Overview 与 History Index 的 managed block 使用 confirmed state、currentness 和 state revision；Story、Thread、Chapter 仍使用同一 corrected history presentation revision。

仅 Current State 变化时，确定性计划只更新 Project Overview 和 History Index 两个必要索引。Story notes 与其他不相关 managed files 保持原 hash；再次同步相同 dataset 为 0 write。

## 安全与冲突

用户 frontmatter、managed block 以外内容和手写笔记继续保留。identity、marker、hash、路径或 presentation revision 冲突不会猜测或覆盖。manifest 只是投影 checkpoint，不是 ProjectFact 或 History 来源；Project History source discovery 继续排除 ProjectFlow 管理投影，避免反馈循环。

CORE 维持有界阅读集，FULL/audit 仍需显式选择。Obsidian 未安装不影响 CLI 合同测试或 ProjectFlow 核心能力；只有真实桌面联调明确阻塞时才需要安装。

## 验证 Gate

测试必须覆盖首次同步、Current-State-only 两文件更新、Story note 稳定、用户内容保留、冲突保护和后续 no-op 0 write。Obsidian user content loss 与 no-change mutation 均必须为 0。

