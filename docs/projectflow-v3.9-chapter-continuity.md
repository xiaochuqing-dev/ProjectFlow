# ProjectFlow V3.9 增量 Chapter 维护合同

## 复用原则

V3.9 继续使用 V3.8.5 Final Chapter Representation Planner，不改变冻结的 Chapter、Primary/Supporting 或 Evidence 语义。变化只发生在 Chapter 维护范围，而不是新建一套 planner。

## 受影响尾部

重建先取得上一成功快照的 Story 与 Chapter。一个旧 Chapter 只有在其全部 Story refs 都属于 overlap 之前已保留、仍有效的 Story 时，才能原样复用。第一个受影响 Chapter 起的尾部由现有确定性分章逻辑重算；来源完全未变化的非 force 路径复用全部旧 Chapter。

这样可避免全局密度预算因为末尾一个新 Commit 改变旧边界，也不会因为小增量重命名大型历史 Chapter。新 Story 可进入当前/latest Chapter；时间长间隔或异质主要结果仍可按既有边界规则开启新 Chapter。

## 模型与 Correction

只有受影响尾部的有界 representation plan 可以进入现有 Chapter synthesis checkpoint。未受影响 Chapter 不重放模型调用。用户声明的 Chapter/title 是 corrected presentation overlay；自动逻辑不能覆盖，restore automatic 后才恢复自动展示。

## Diagnostics 与 Gate

Diagnostics 输出 `affectedChapterIds`、`reusedChapterIds`、`recomputedChapterIds`、`newChapterIds`、对应计数和 `chapterRepresentationRevision`。未受影响范围的 Chapter ID、工程 membership 与 corrected presentation 必须 100% 稳定；successful checkpoint replay 和 unrelated model window rerun 必须为 0。

