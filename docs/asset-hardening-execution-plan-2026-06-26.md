# ProjectFlow 落地执行书：项目资产变真、变实、变硬

> 生成日期：2026-06-26
> 代码基线：`a52f17e merge: product experience convergence (P1-P6)`（GitHub master）
> 基准文档：`Downloads/ProjectFlow_开工说明书_资产变真变实变硬_2026-06-26_审查建议.txt`
> 工作目录：`C:\Users\xiaochuqing\ZCodeProject\ProjectFlow`

---

## 一、目标

不扩张成果输出。只做一件事：**让"项目资产"本身足够真实、具体、可信、可复用。**

用户心智从"这里列了几个能力，都是系统模板生成的，我还得自己判断"变成"这里把我的项目能力讲清楚了，每条都有证据、用途和可复用表达"。

主线：开发过程 → 待确认成果 → 项目资产 → 能力资产 → 可信依据 →（后续输出暂不扩张）

## 二、当前现状（已核实）

| 检查项 | 现状 | 结论 |
| --- | --- | --- |
| capabilities/page.tsx | 212 行，大模板卡铺开所有字段 | 需改小卡列表+详情 |
| 能力名来源 | `project-memory-display.ts` 扫描事实（如"已导入完整项目结构…"） | 需映射为能力名称 |
| 能力页角标 | 第125行 `能力 {index + 1}` | 序号占位，需去掉 |
| project-intelligence/page.tsx | **578 行**，混数据加载/字段配置/入口卡/字段审查 | 主要风险，需拆 |
| fact-sources/page.tsx | "字段 · N 项""字段来源"字段心智 | 需改资产分类视角 |
| 后端测试锚点 | **无** ProjectMemoryControllerTest / ProjectAnalysisServiceTest；有 V2CoreControllerTest | 用 V2CoreControllerTest 承接 |
| ModelGatewayService | `callJson(provider, prompt, tokenLimit)` 现成，ProjectAnalysisService 已示范接入 | P4 复用，不新增依赖 |

## 三、硬性约束

1. 不推倒重写，不改数据库结构，不新增一级导航。
2. 模型生成内容永远是候选，用户确认后才入正式资产，不自动覆盖。
3. 不用"用模型完善全部能力"这类文案。
4. 修改小步提交，先写/改测试再实现。
5. 复用现有 ModelGatewayService / ProjectMemory 写入路径，不新增模型依赖、不新增实体。
6. 本地规则 fallback 必须保留（模型未配置/失败时页面仍可用）。
7. 原始事实不丢：映射后的能力名出现，原始事实保留在 detail.recognized。

## 四、实施阶段

调整顺序原则（采纳审查建议）：**先把无后端风险的资产库和页面心智做实，再接模型候选。** 模型候选实现复杂则单独开轮，不影响前 4 个提交先落地。

---

### P0 测试先行

**新增** `frontend/tests/capability-asset-library.test.mjs`，断言：
- capabilities/page.tsx 默认渲染小卡（含"查看详情"），不默认铺开所有字段
- 卡片主标题不能是 `能力 N`（序号不能是主标题）
- 能力名以"能力"结尾（如"项目结构识别能力"）
- 存在"生成能力解读"入口文案
- 存在可复用场景标签（README / 简历 / 面试 等）

**修改** `frontend/tests/page-decomposition.test.mjs`：
- 追加 `project-intelligence/page.tsx ≤ 500` 行约束（当前 578，P2 会拆组件降至约束内）
- capabilities/page.tsx ≤ 400（当前 212，宽松）

验收：测试先失败，P1/P2 实现后通过。

---

### P1 能力资产库前端化（先不接模型）

**新建** `frontend/src/lib/capability-names.ts`：
- 关键词分桶映射 + 兜底规则（采纳审查建议的保守兜底）：

```
结构识别     ← /已导入.*项目结构|完整项目结构|目录树|文件结构/
技术栈理解   ← /技术栈|主要技术|框架|依赖/
测试证据     ← /测试目录|测试文件|测试入口|单测|集成测试/
运行配置     ← /本地启动|启动配置|启动入口|环境变量/
变更沉淀     ← /Git 变化|Agent 写回|开发成果整理|变更追溯/
文档治理     ← /readme|架构文档|协作规则|工程文档/
风险识别     ← /风险|问题|隐患|待修复/
输出素材     ← /素材|输出|模板|表达/
兜底         → "项目资产沉淀能力"（原始事实保留到 detail.recognized）
```

- 不再用"取事实首段 + 能力"（会生成怪标题）。

**新建** `frontend/src/lib/capability-assets.ts`：
- 从 capabilities/page.tsx 抽出 `buildCapabilityAssets`
- 扩展字段：`name` / `oneLine` / `status`（已确认/可补充/待补证据）/ `evidenceCount` / `scenes`（README/简历/面试/周报）/ `detail`

**改造** `capabilities/page.tsx`（≤400 行）：
- 默认小卡片网格，每张小卡展示：能力名称 / 一句话说明 / 可信状态 / 证据数量 / 可复用场景标签 / 操作（查看详情、生成能力解读、复制表达）
- 去掉 `能力 {index + 1}` 序号角标，改为"已确认能力"/"能力资产"
- "查看详情"展开详情（drawer/expand/子路由），详情含：能力说明/解决的问题/工程价值/已识别内容/关联模块文件/来源证据/README表达/简历表达/面试讲解点/确认状态/手动修正入口

验收：一屏看到更多能力；能力名以"能力"结尾不再是整句事实；原始事实仍在"已识别内容"。

---

### P2 可信依据与项目理解降字段化

**修改** `frontend/src/app/project-intelligence/fact-sources/page.tsx`：
- 侧栏标题 `字段 · N 项` → `资产分类 · N 类`
- 右侧标题 `字段来源 · N 条` → `资产来源 · N 条`
- fieldKey 语义化展示映射（不改 fieldKey 本身）：
  positioning→项目定位 / completedCapabilities→能力与成果 / currentStage→当前阶段 / technicalDecisions→技术决策 / currentRisks→风险记录 / developerLearnings→经验沉淀 / nextStepSuggestions→下一步目标 / showcaseAssets→可展示成果
- ResourceTimeline detail 输出改为：资产分类 / 可信状态（已确认/待确认）/ 来源类型 / 关联资产内容；sourceType、confidence、sourceId 进"高级信息"折叠区
- sourceId 默认不裸露

**拆分** `frontend/src/app/project-intelligence/page.tsx`（当前 578 行 → 降到 ≤500）：
- 抽出 `ProjectAssetOverview.tsx`（只读资产总览卡）
- 抽出 `ProjectAssetEditPanel.tsx`（字段编辑面板）
- 抽出 `ProjectAssetEntryPanel.tsx`（右侧入口卡）
- 默认页只显示只读资产总览，不显示 9 个 textarea 区块
- 顶部"编辑项目资产"按钮，点击后展开 `ProjectAssetEditPanel`
- "为什么可信？"保留在每张资产卡或详情里，不作主入口

验收：新用户打开先看到项目被理解成什么，不是字段编辑框；可信依据页无"字段"心智；sourceId 不默认裸露。

---

### P3 工作台主 CTA 微调

**修改** `frontend/src/lib/project-flow-state.ts` 的 `hasConfirmedAssets` 分支：
- 主 CTA：`查看能力与成果`，primaryHref → `/project-intelligence/capabilities`
- 次级入口：`生成成果输出` → `/ai-review`（不回退上一轮输出价值可见性）
- 其余状态分支不变

验收：有确认资产无待确认→主CTA查看能力与成果；有待确认成果→开发成果审查；无今日开发→刷新今日开发。

---

### P4 模型候选解读（可单独开轮）

**后端** 新增 endpoint（放在 ProjectMemoryController，语义属于 memory）：
```
POST /api/projects/{projectId}/memory/capabilities/interpret
入参：{ capabilityFact: string }
出参：{ degraded: boolean, source: "MODEL"|"LOCAL_RULE", message: string,
        candidate: { summary, problem, value, readme, resume, interview } }
```
- 复用 `ModelGatewayService.callJson`
- 取用户 provider，无 provider / 调用失败 → 返回 `degraded=true, source=LOCAL_RULE` 本地候选
- **只返回 candidate，不写 ProjectMemory**
- prompt 喂入：项目定位、技术栈、该条能力事实、可信依据摘要，要求返回 JSON

**前端** capabilities/page.tsx 小卡"生成能力解读"按钮：
- 调 interpret 接口
- 候展示在"候选解读"区（不覆盖现有正式说明）
- 候选区动作：采纳为正式能力说明 / 编辑后采纳 / 忽略 / 重新生成
- degraded=true 时明确提示"模型不可用，已用本地规则生成候选"
- "采纳"走现有 PATCH /memory（追加或替换 completedCapabilities），不新增数据库字段、不新增实体

**后端测试**：在 `V2CoreControllerTest` 增加 interpret endpoint 覆盖（不新增测试类；不写 ProjectAnalysisServiceTest）。

验收：模型生成是候选不自动入资产；无 provider 降级本地规则页面仍可用；用户可编辑后采纳；degraded 字段可验收。

---

## 五、不做事项

1. 不扩张成果输出模板。
2. 不把"生成能力解读"做成自动覆盖资产。
3. 不新增一级导航。
4. 不改数据库结构，不新增实体。
5. 不用"用模型完善全部能力"文案。
6. 不为能力资产新建后端实体（复用 ProjectMemory + ModelGatewayService）。
7. 不删除可信依据/sourceId，只降级到高级信息。

## 六、测试计划

先写/改测试，再改实现。最低测试集：

```
cd frontend
node tests/capability-asset-library.test.mjs
node tests/product-language-convergence.test.mjs
node tests/page-decomposition.test.mjs
for f in tests/*.mjs; do node "$f"; done
npm.cmd run build

cd ../backend   # P4 才需要
mvn -q -Dtest=V2CoreControllerTest test
```

## 七、提交策略（5 个小提交）

| # | 提交 | 内容 | 阶段 |
| --- | --- | --- | --- |
| 1 | `test: add capability asset library checks` | 新增 capability-asset-library 测试 + page-decomposition 约束，先失败 | P0 |
| 2 | `refactor: extract capability assets and real capability names` | capability-names.ts + capability-assets.ts + 小卡列表+详情，去掉序号角标 | P1 |
| 3 | `refactor: turn trust evidence and project understanding into asset views` | fact-sources 资产视角 + project-intelligence 拆组件只读总览 | P2 |
| 4 | `refactor: guide confirmed assets to capability review` | hasConfirmedAssets 主 CTA→查看能力与成果，保留成果输出次级入口 | P3 |
| 5 | `feat: generate capability interpretation candidates` | interpret endpoint + 候选解读区 + V2CoreControllerTest 覆盖 | P4 |

每个提交后跑相关 mjs 测试。最终合入前跑全量前端测试 + build +（P4）后端测试。
P4 若实现比预期复杂，可单独开轮，不影响前 4 个提交先落地。

## 八、最终验收标准

1. 能力页默认是小卡列表，不铺满所有字段。
2. 能力名像真实能力（以"能力"结尾），不是扫描结果整句；无"能力 N"主标题。
3. 能力详情能解释：是什么/解决什么/为什么重要/证据/可用于什么。
4. 生成能力解读是候选，用户确认才入资产；无 provider 降级本地规则，页面仍可用。
5. 文案无"用模型完善全部能力"。
6. 可信依据页无"字段"心智，sourceId 不默认裸露，展开 detail 不回退字段心智。
7. 项目理解页默认是资产总览，手动修正折叠为编辑模式。
8. 工作台主 CTA 随状态准确引导，成果输出保留为次级入口。
9. 成果输出不做大扩张。
10. project-intelligence/page.tsx ≤ 500 行。
11. 全量前端 mjs 测试 + build 通过；P4 后端 V2CoreControllerTest 通过。

## 九、核心原则

本轮不是"生成更多文本"，而是"沉淀更硬的项目资产"。
先把资产变真、变实、变硬，后面的输出才会有质量。
模型只应锦上添花，不能成为资产可信性的来源本身。
