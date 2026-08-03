# ProjectFlow V3.8.0 真实项目验证

验证日期：2026-08-03

## 验证原则

真实项目验证只使用公开仓库、固定 Commit 和安全验收产物。不会提交用户私有项目内容、机器绝对路径、模型原始响应或凭证。

ProjectFlow 自身用于完整历史 dogfood；三个外部公开仓库使用 blob:none、sparse worktree、固定 SHA、浅克隆和只读临时目录。外部仓库验证不调用模型。

## ProjectFlow dogfood

冻结基线：fd5ce827245f4fc4a20ecda15c63fc03313505ab。

结果：

| 指标 | 数值 |
| --- | ---: |
| 基线 Commit | 197 |
| 来源事件 | 2,611 |
| Chapter | 27 |
| Story | 536 |
| Thread | 392 |
| V3.7.x 固定窗口 Commit 事件 | 33 |
| V3.7.x 固定窗口 Git 原始事件 | 525 |
| 同窗口跨来源 Story | 186 |

V3.7.x 的 Git 事件只统计固定窗口内 GIT 来源；Story 包含同窗口内 Git 与冻结当前材料元数据。两组数字来源范围不同，不能解释为 525 个 Git 事件压缩成 186 个 Story。

安全合同：eventConservation=true，invalidEvidenceRefCount=0，crossProjectRefCount=0，unsupportedStrongFactCount=0。

确定性结果连续多次一致：

- projectflow-v375-dogfood.json：2D07376082D3CE4AC8E8E0BDA1F44AF596D87D880B475BBB796EFECDD37642B0
- projectflow-v375-dogfood.md：25E330A7F15ADDC29E1C9CD532A43D2E980DFB639F722A0C0DF9A9ECF29372DE

## 三个公开成熟项目

| 项目形态 | 固定仓库与 SHA | 浅克隆深度 | 可达 Commit | 实际读取 Commit | 来源事件 | Story | Chapter | Thread |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 大型代码仓库 | kubernetes/kubernetes 0e5f0f9374ca822d0a5619088d4a00f335b8bafd | 120 | 92,183 | 2,999 | 20,000 | 4,615 | 236 | 3,396 |
| 文档与知识仓库 | mdn/content f35f247e16286c4e0b1c88fba3d8ce01683c189b | 100 | 100 | 100 | 1,758 | 453 | 23 | 430 |
| 前端与创意展示仓库 | hakimel/reveal.js a3b940695648aa1c5b0680bc9a5b905cf43020e5 | 100 | 196 | 196 | 2,039 | 366 | 23 | 217 |

三个快照均为 DEGRADED，Coverage complete=false、currentness=PARTIAL。原因是测试有意使用浅克隆，浅克隆窗口不能代表完整项目历史。Kubernetes 的 merge-heavy 浅历史还触发 20,000 来源事件安全上限；产品明确报告未完整读取，没有把部分窗口伪装成完整历史。

三个项目均满足：

- eventConservation=true。
- invalidEvidenceRefCount=0。
- crossProjectRefCount=0。
- unsupportedStrongFactCount=0。
- Story、Chapter 和 Thread 均非空。
- 模型调用为 0。

公开仓库产物 SHA-256：82BBE0813AAE7AA2E3CD224E2D62DD516F75B136209CB6552D310F6009D08E94。

## 有界性验证

产品诊断显式返回：

- gitCommitReadLimit=5,000。
- sourceEventLimit=20,000。
- reachableGitCommitCount。
- readGitCommitCount。

Kubernetes 在 92,183 个可达 Commit 中只读取 2,999 个就达到事件上限。这不是事件丢失：eventConservation 只对进入本轮有界快照的 20,000 个来源事件负责；未读取范围通过 gaps 和 limitations 公开披露。

## 首次失败及修复证据

1. 首次公开仓库验证期望 READY，实际为 DEGRADED。根因是验收断言忽略了浅克隆的诚实覆盖语义；修正为 DEGRADED + PARTIAL。
2. 第二次验证错误要求 sourceEventCount 大于所有可达 Commit。Kubernetes 的浅历史包含 92,183 个可达 Commit，而产品按设计限制 20,000 个事件。修正为读取产品诊断的提交和事件上限，并要求超限时报告未完整读取。
3. 修正后，三仓测试 1/1 通过，测试本体耗时 213.6 秒，Maven 总耗时 229.9 秒。

失败没有通过降低安全门槛或修改 Ground Truth 隐藏，而是改正错误的验收口径。

## 当前限制

- 外部仓库是固定浅窗口，不能代表完整历史或所有分支。
- 公开产物中的标题样本用于检查可读性，不等于人工逐条质量评分。
- Kubernetes 达到事件上限，完整历史需要后续有界续扫机制；V3.8.0 不启动 daemon 或后台常驻扫描。
- 此验证证明有界性、事件守恒、层级非空和安全引用，不宣称任意项目的通用准确率。
- 真实模型资格另见 projectflow-v3.8.0-model-qualification.md。
