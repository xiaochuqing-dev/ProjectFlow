# ProjectFlow V3.8.5 Dogfood 对比记录

状态：固定兼容模型的完整 Dogfood 对比只属于执行器和数据守恒证据；DeepSeek 真实 Dogfood 场景已运行但 FAIL，GLM 真实 Dogfood 和人工评分未运行。整体资格为 BLOCKED。

## 数量对比

| 指标 | V3.8.0 基线记录 | V3.8.5 fixed dogfood |
| --- | ---: | ---: |
| Commit | 197 | 216 |
| Source Event | 2,611 | 2,915 |
| Technical Atom | 未记录 | 2,689 |
| 全部 Story | 536 | 337 |
| 默认可见 Primary | 未记录 | 227 |
| Supporting | 未拆分 | 110 |
| 隐藏或合并 | 未记录 | 110 |
| Chapter | 27 | 9 |
| Thread | 392 | 258 |
| Story 窗口 | 未记录 | 8 |
| Story 模型调用 | 未记录 | 8 |
| Chapter 模型调用 | 未记录 | 0（17 窗口场景另有 11 次） |
| 最终 cache | 未记录 | 命中 |
| 未处理 / pending / failed / skipped | 未记录 | 0 / 0 / 0 / 0 |
| Event conservation | 未记录 | true |
| Invalid Evidence / cross-project ref | 未记录 | 0 / 0 |
| Generic template rate | 未记录 | 0.0000（固定模型指标，不等于可读性通过） |
| First-layer technical leak rate | 未记录 | 0.00593 |

固定场景总计 60 个物理请求、12,000 个固定 usage token。耗时只作过程诊断，不能替代人工质量结论。

## 真实 Provider Dogfood

DeepSeek V3.8.5 真实场景工件共 11 个场景、83 个物理请求、1,079,860 token、模型耗时 5,512,516 ms，10 个场景通过，1 个 `projectflow-current-history-dogfood` 失败。失败原因是 `Primary and supporting history references are inconsistent`，因此不能把固定对比表中的数量当作真实 Provider 的最终 ProjectFlow 历史结果。GLM 真实场景未执行。

五类 DeepSeek 非代码项目场景均通过：演示材料、研究报告、数据分析、品牌页、无 Git 版本。17 窗口 continuation、restart/cache、schema failure、取消恢复、Prompt overflow 和 correction 场景也通过；这证明边界流程，但不能抵消 Dogfood 失败或 19-case qualification FAIL。

## 代表性 Primary Story

每条记录保留 Story ID 和 Evidence 下钻入口。以下标题是固定模型实际输出，故意保留其中的问题。

| Story | 第一层标题 | Evidence 示例 |
| --- | --- | --- |
| `story-7867fde7c59a229514a2` | 整理前端区域并形成可阅读结果 | `commit:ae9fba1...`; `file:frontend/next.config.ts` |
| `story-982c8b1c6b0d6861cd78` | 整理 env example 并形成可阅读结果 | `commit:ae9fba1...`; `file:.env.example` |
| `story-b7ebe5f270c98454bc5f` | 整理 readme 并形成可阅读结果 | `commit:ae9fba1...`; `file:README.md` |
| `story-c54965e820fc4476add2` | 整理 gitignore 并形成可阅读结果 | `commit:ae9fba1...`; `file:.gitignore` |
| `story-e8069755af997e5084ce` | 整理 docker compose 并形成可阅读结果 | `file:docker-compose.yml` |
| `story-efd1535ad50eb7417a68` | 整理文档区域并形成可阅读结果 | `file:docs/architecture.md` |
| `story-242743a6dfdb60ac4d56` | 更新登录流程，形成新的可确认版本 | `commit:88730a2...` |
| `story-2b2c0766aa26f28f543b` | 整理 project 并形成可阅读结果 | `file:backend/src/main/java/com/projectflow/entity/ProjectSpace.java` |
| `story-d237c391cce392e8fa2c` | 整理 task 并形成可阅读结果 | `file:backend/src/main/java/com/projectflow/entity/TaskItem.java` |
| `story-b263154eac17d43b1292` | 整理 dev log 并形成可阅读结果 | `file:backend/src/main/java/com/projectflow/entity/DevLog.java` |
| `story-b475a90848e62540048f` | 整理 start projectflow 并形成可阅读结果 | `file:start-projectflow.bat` |
| `story-e1e0806ff13b4fb50a9c` | 整理 dashboard 并形成可阅读结果 | `file:frontend/src/app/dashboard/page.tsx` |
| `story-9f5119a76df75c5ba5f3` | 整理 add import and ai reflection workflows 并形成可阅读结果 | `commit:a87fac7...` |
| `story-52bab0f37df0221bd0d5` | 整理 ai review 并形成可阅读结果 | `file:frontend/src/app/ai-review/page.tsx` |
| `story-a6ac8d104f6954786bad` | 整理 projects 并形成可阅读结果 | `file:frontend/src/app/projects/page.tsx` |

## Supporting 归并示例

固定输出公开 10 个可核对关系；`EXPLICIT_SUPPORTING_STORY` 表示模型/确定性结果明确声明的 Supporting，`FOLDED_ENGINEERING_SUPPORT` 表示展示压缩后归入主结果的工程支撑。

| Supporting Story | Primary Story | 关系 | Evidence 示例 |
| --- | --- | --- | --- |
| `story-9d40b86562b1b294d62b` | `story-e8069755af997e5084ce` | EXPLICIT_SUPPORTING_STORY | `commit:ae9fba1...` |
| `story-cdcd22d45a5e19993c22` | `story-7867fde7c59a229514a2` | EXPLICIT_SUPPORTING_STORY | `file:backend/pom.xml` |
| `story-9407adef33ca917f4ca4` | `story-242743a6dfdb60ac4d56` | EXPLICIT_SUPPORTING_STORY | `file:frontend/src/components/AuthPageShell.tsx` |
| `story-b876a325cb9d1887536f` | `story-242743a6dfdb60ac4d56` | EXPLICIT_SUPPORTING_STORY | `file:frontend/src/components/AuthPanel.tsx` |
| `story-7dfdb393a956de3f2717` | `story-93feedf589d409bdd0e4` | EXPLICIT_SUPPORTING_STORY | `file:tasks/prd-projectflow-v3-2-final-plan.md` |
| `story-94ba0bc421cbcffd0072` | `story-774452d3004ddcd00808` | EXPLICIT_SUPPORTING_STORY | `file:backend/src/main/java/com/projectflow/entity/ProjectAnalysisJob.java` |
| `story-8852bbf12c6b37da8173` | `story-b475a90848e62540048f` | EXPLICIT_SUPPORTING_STORY | `file:start-projectflow.bat` |
| `story-eb6d2367a95611dc22cf` | `story-b7ebe5f270c98454bc5f` | EXPLICIT_SUPPORTING_STORY | `file:README.md` |
| `story-f37a81f2d97090dab7aa` | `story-b252950a2801efd15c79` | EXPLICIT_SUPPORTING_STORY | `commit:75ae484...` |
| `story-8ccdb310afce3ac947fe` | `story-774452d3004ddcd00808` | EXPLICIT_SUPPORTING_STORY | `file:backend/src/main/java/com/projectflow/controller/ProjectController.java` |

## Chapter 与演变链

固定输出的 9 个 Chapter 中，以下 5 个被保留为代表样本：

- `chapter-15abc2223888b5fdb119`：推进登录流程与 env example 并形成阶段结果，46 Story / 336 Raw Event。
- `chapter-ad7bdcd1bceb4fd8b12a`：推进 project import、v3 文档与相关材料并形成阶段结果，65 Story / 336 Raw Event。
- `chapter-96141c2e7e980633d9ae`：推进 asset hardening、体验转换和异步能力整合，34 Story / 302 Raw Event。
- `chapter-d93ed6ea7ae04512c2b6`：推进 V3.3.2 分析、归档和文档整理，41 Story / 296 Raw Event。
- `chapter-3efe2fe42d27953f4ee7`：推进 V3.3.7、PR #1 和合并结果，23 Story / 312 Raw Event。

三条完整演变链（均可从 Thread 下钻到 Story/Evidence）：

- `thread-09cbdcdcaa75cc428cf3` -> `story-3f0cda0492739977156c`：Persistent Scan Job Design，当前状态为可核对版本。
- `thread-c651d4a4e61434eb0ff5` -> `story-5a5c6b41a5dcb4192366`：ProjectFlow V3.3 沉淀链路。
- `thread-cd1df45b8ffb3e4d4e06` -> `story-bfd8951d52df33c6755f`：同一主题的后续演进与当前结果。

## 仍不理想的样例

固定模型仍输出“整理前端区域”“整理 gitignore”“整理 project”“整理 task”“整理 ai review”等对象级模板；Chapter 也含有截短英文提交对象。DeepSeek 非代码候选已经出现较自然的动作和结果，但未完成人工复核。所有候选都只能进入人工池，不被宣称为可读性通过。

## 证据边界

工件不保存完整 Prompt、raw response、reasoning、Key、Authorization 或机器绝对路径。固定模型仅证明请求、解析、角色图、Evidence 校验、checkpoint 和 cache 行为；GLM/DeepSeek 19-case 资格结果均 FAIL，人工分数仍为 NOT_RUN。
