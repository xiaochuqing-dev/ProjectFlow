# ProjectFlow V3.8.5 Final Chapter Representativeness Contract

状态：工程合同已实现；最终质量仍为 HUMAN REVIEW REQUIRED / NOT PASS。

## 产品边界

Chapter 只汇总已经通过 Story、Claim State 与 Evidence 校验的展示结果。它不新增 ProjectFact，不改变 Raw Event、Story 身份、Primary/Supporting 归属、Claim State 或用户修正。Round 1、Round 2、Round 3 工件保持冻结。

## Representation Plan

每个自动 Chapter 先按 Primary Story 的稳定主体、语义族与时间范围形成 Representative Cluster。Supporting Story 跟随其 Primary owner，只记录支撑数量，不增加 cluster 权重。

Cluster 权重由 Primary 数量的平方根、有界活跃天数与月份、以及可解释性共同组成。Project material、merge/pull-request 元数据、project-area 和带原始技术 token 的标签会被降权，不能仅靠提交或文件数量压过具体成果；非代码文档、演示、报告和数据结果仍按具体成果参与。

Cluster 角色为 DOMINANT、CO_DOMINANT 或 MINOR。最多选择 4 个代表簇，目标覆盖 0.72 的加权 Primary outcome value；同分时使用固定时间与 ID 顺序。计划保存精确 requiredRepresentativeClusterIds、dominantClusterIds、claim ceiling、unknown、conflict 和稳定 fingerprint。

## Chapter 边界

既有时间、Tag、密度和独立结果边界继续有效。若一个自动 Chapter 存在多个没有共同主体、语义或邻近共同区域的 major cluster，规划器只在强的按时间排列语义转折点拆分，并记录 REPRESENTATION_BOUNDARY。拆分最多递归两层，每侧至少保留 4 个 Primary；所有 Story 完整且互斥，Supporting 跟随 owner。没有可信边界时保守保留，并显示覆盖与 limitation，不制造空泛 umbrella。

## 确定性与模型边界

无模型、模型失败、Schema/cluster ID 错误、repair 失败或 Provider 不可用时，确定性标题只能来自 dominant/co-dominant outcome，摘要覆盖选中的代表 outcome。项目材料元数据只能表述为当前可确认的材料变化，原始路径、next、target、类名和哈希不得进入第一层。

模型只润色有界 Representation Plan。第二阶段必须原样返回 representedClusterIds；模型不能改变成员、cluster、权重、状态、Evidence 或 unknown/conflict。Prompt 和 cache key 均绑定 plan version 与 fingerprint。

Validator 要求标题至少命中一个 dominant/co-dominant cluster，摘要逐一覆盖所有 required cluster，并对每个匹配分句执行 Claim State ceiling 和第一层技术泄漏校验。不合格输出只允许一次定向 repair；仍不合格时保留已验证的确定性 Chapter。

## Correction 兼容

用户声明或修正继续作为只读展示覆盖层。自动重建不静默改写用户 Chapter，自动身份或成员变化通过既有 correction conflict 暴露；userDeclaredChapterMutationCount 必须为 0。

## 诊断与 Gate

至少记录 chapterCount、primaryStoryCount、supportingStoryCount、representativeClusterCount、dominantClusterCount、selectedRepresentativeClusterCount、representativePrimaryCoverage、largest/median/large Chapter、chaptersNeedingSplit、deterministic/model Chapter 数、minor-title risk、technical leak、unsupported claim、overlap、orphan Supporting、reason without Evidence 和 user-declared mutation。

自动 Gate 要求 invalid/cross-project Evidence、Raw Event loss、Story overlap、orphan Supporting、technical leak、unsupported claim、minor-title risk 与 silent correction loss 均为 0。自动 Gate 不等于人工 PASS；最终仍需 8–12 个双 Provider Chapter 与 Round 3 Story/变化子集由真人评分。
