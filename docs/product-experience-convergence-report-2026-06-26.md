# ProjectFlow 产品体验收敛 — 接续实施报告

> 日期：2026-06-26
> 代码基线：`b6483c7 feat: reshape projectflow asset workbench flow`
> 工作分支：`product-experience-convergence`（worktree）
> 新增提交：`b2a9d31`、`597e30a`
> 对应计划：`Downloads/ProjectFlow_最终版开工说明书_2026-06-25.txt` 及其评估改进建议

## 一、为什么 bat 启动后看不到变化

**根因：启动脚本指向的是 master 工作树，不是改动所在的 worktree。**

- `start-projectflow.bat` → 调用 `start-projectflow-embedded.ps1`
- 脚本第 8 行 `$root = Split-Path -Parent $MyInvocation.MyCommand.Path` 解析出 bat 所在目录
- bat 位于 `C:\Users\xiaochuqing\ZCodeProject\ProjectFlow`（master 分支，提交 `b6483c7`，**未含本轮改动**）
- 本轮所有改动提交在 worktree `C:\Users\xiaochuqing\.config\superpowers\worktrees\ProjectFlow\product-experience-convergence`（提交 `597e30a`）

当前 3000/8080 端口跑的是 master 旧版本，所以页面无变化。

**解决方式（任选其一，见文末「下一步」）：**
1. 把 worktree 的两个提交合并/检出到 master 工作树；
2. 或临时改 bat 指向 worktree 目录。

## 二、本轮完成范围

计划 P0–P6 中，上一轮已完成 P2、P3；本轮完成剩余 P1 收尾、P4、P5、P6，并补齐 P0 验收测试。

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| P0 | 验收测试（旧词扫描 + 入口层级 + 素材完整度） | ✅ 本轮补齐 |
| P1 | 清理用户可见旧命名 | ✅ 本轮收尾 |
| P2 | 项目理解页右侧入口收敛 | ✅ 上轮已完成（本轮随提交入库） |
| P3 | 能力清单升级为能力资产页 | ✅ 上轮已完成（本轮随提交入库） |
| P4 | 工作台 hasConfirmedAssets 主 CTA | ✅ 本轮 |
| P5 | 开发成果审查空状态降噪 | ✅ 本轮 |
| P6 | 每日回顾用途 + 成果输出素材完整度 | ✅ 本轮 |

## 三、具体改动（24 文件，+381 / -155）

### 提交 1 `b2a9d31 refactor: converge visible projectflow terminology`

**P1 旧词清理**（用户可见文本，保留路由参数/类型字段名/变量名）：

| 旧词 | 新词 | 涉及文件 |
| --- | --- | --- |
| 项目画像 | 项目理解 | project-analysis-records/[recordId]、analysis-records、changes、fact-sources、timeline、projects/[projectId]/files、settings、project-memory-display.ts |
| 项目档案 | 项目资产 | dev-logs、dev-logs/sources、daily-review-sources.ts、change-review-utils.ts、changes/page.tsx、ActivityFeed.tsx、timeline/page.tsx、project-changes/[changeId]/evidence、ai-review/page.tsx |
| 字段来源链 | 可信依据 | fact-sources/page.tsx |
| 档案变化 | 项目资产更新 | changes/page.tsx |
| 任务证据 | 开发证据 | dev-logs/page.tsx、daily-review-sources.ts |
| sourceId 裸露 | 收敛进高级信息 | fact-sources/page.tsx（移除 meta 裸露） |

**P2 项目理解页右侧入口收敛**（`project-intelligence/page.tsx`）：
- 6 个平级入口 → 2 主卡（能力与成果、待确认成果）+ 2 辅助链接（项目时间线、分析记录）
- 移除"时间线变化"和"可信依据"主入口

**P3 能力清单→能力与成果资产页**（`capabilities/page.tsx`）：
- 新增 `CapabilityAssetCard`：解决什么问题 / 为什么重要 / 已识别内容 / 来源证据（折叠）/ 可复用表达 + 复制按钮
- 本地规则 `buildCapabilityAssets` 从 ProjectMemory 生成能力资产

**P0 验收测试**：
- 新增 `product-language-convergence.test.mjs`：扫描旧词、入口层级、sourceId 裸露、素材完整度契约
- 修正 `long-term-record-navigation.test.mjs`：与入口降级冲突的断言改为反向断言

### 提交 2 `597e30a feat: clarify confirmed-asset guidance, review empty state and output readiness`

**P4 hasConfirmedAssets**（`project-flow-state.ts`）：
- 新增 `hasConfirmedAssets` 判断（completedCapabilities / technicalDecisions / developerLearnings / showcaseAssets / currentRisks 任一非空）
- 无待确认内容但已有确认资产时，主 CTA 从"刷新今日开发"改为"生成成果输出"（指向 /ai-review）

**P5 审查空状态降噪**：
- `ChangeReviewList.tsx`：空状态改操作型——"暂无待确认开发成果" + 主动作（回到工作台刷新今日开发）+ 次动作（查看已确认项目资产）+ 次级说明
- `ChangeReviewSidebar.tsx`：审查边界说明卡折叠为 `<details>查看审查说明</details>`

**P6 用途 + 素材完整度**：
- `dev-logs/page.tsx`：表单顶部加"保存后会成为成果输出和经验沉淀的来源；进入正式项目资产仍需在开发成果审查或项目资产页确认"
- `ai-review/page.tsx`：新增"输出素材完整度"卡——本地规则计分（项目资产/每日回顾/今日开发/项目时间线各 +1，0-1 低 / 2-3 中 / 4 高），列已连接素材与建议补充素材（测试结果/上线记录/风险决策），注明"是生成参考度而非项目质量评分"

## 四、验证结果

- 前端全量静态测试：13 个 mjs 全部 PASS
- `npm run build`：✓ Compiled successfully，全部路由正常生成
- 后端：未改动（符合计划约束）
- 工作树：干净

## 五、下一步（让 bat 跑到新版本）

当前 worktree 提交尚未进 master。建议：

**方案 A（推荐，合并到 master）：**
```bash
cd C:\Users\xiaochuqing\ZCodeProject\ProjectFlow
git merge product-experience-convergence
```
然后正常双击 `start-projectflow.bat`。

**方案 B（临时验证，不合并）：**
关闭当前 3000/8080 进程，手动在 worktree 目录启动：
```bash
cd C:\Users\xiaochuqing\.config\superpowers\worktrees\ProjectFlow\product-experience-convergence\frontend
npm.cmd run build
npm.cmd run start -- --hostname 127.0.0.1 --port 3000
```
（后端同理在 worktree 的 backend 目录启动。）
