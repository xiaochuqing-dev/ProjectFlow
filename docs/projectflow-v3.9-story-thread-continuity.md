# ProjectFlow V3.9 Story 与 Evolution Thread 连续性

## 身份所有权

Story 与 Thread 身份继续由工程层拥有。Story 使用稳定 subject lineage 与首个稳定 Event 建立身份；Thread 使用 canonical subject 建立长期身份。模型不能创建旧 ID、Evidence、Event 或跨项目引用，也不能覆盖工程冲突。

## Story 判断

重建优先使用 subjectKey、Technical Atom lineage、直接 Evidence、文件/文档身份、relation refs、时间顺序、旧 Story event refs 和生命周期 transition。兼容且有确定性证据的新 Event 可追加到既有 Story；独立结果、长间隔或无法证明的连接默认生成新 Story 或 attention，不使用标题相似度或 Embedding 作为身份依据。

生命周期 CREATE → MODIFY → REMOVE → RESTORE 继续保留。rename/move 使用现有 alias/transition Evidence；rewrite 后无法证明的 linkage 不强连。

当前 deterministic audit 未发现必须新增模型 continuity-candidate 阶段的缺口，因此 V3.9 不增加该模型调用。`ambiguous*CandidateCount` 保持可观测；工程层拒绝的 relink candidate 进入 rejected diagnostics。

## Thread 判断

同一 canonical subject 的新 Story 安全追加到既有 Thread，不受影响 Thread ID 保持稳定。明显不同 subject 不并线；Supporting 变化只能作为已有主要成果的支撑，不自行制造长期主线。rewrite 失去可靠 lineage 时保留 UNKNOWN/attention。

用户对 Primary/Supporting、merge、split、reattach 等 presentation correction 继续作用于 corrected Thread view，但不改写 Raw Event、ProjectFact 或工程 Evidence。

增量重建会把保留的旧 Story 与受影响窗口的新 Story 合并。合并、分类和压缩完成后，系统只以每个 Supporting Story 的有效 `primaryStoryId` 重建双向角色图：Primary 的 `supportingChangeRefs` 必须由这些反向归属重新计算，旧快照中已经失效的前向引用不得带入新快照。目标不存在、自指或目标不再是 Primary 时，相关 Story 保守恢复为 Primary，不建立未经证明的支撑关系。

## Diagnostics 与 Gate

Diagnostics 输出 continued、unchanged、new、relinked、invalidated 的 Story/Thread ID 与计数，以及 ambiguous 与 rejected candidate 计数。冻结 Gate 要求未受影响 Story/Thread 身份稳定率 100%、unknown candidate ID 为 0、cross-project reference 为 0、false strong continuity attachment 为 0。
