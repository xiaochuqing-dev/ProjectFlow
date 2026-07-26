# ProjectFlow V3.7.2 Product Acceptance

Acceptance date: 2026-07-26

Scope notice: 本指标仅代表本阶段人工标注代表性测试集，不构成对任意项目的通用准确率承诺。

## Manual product questions

1. 真实模型是否带来明显价值？有局部明显价值，但未达到版本质量门槛。奇怪文档、冲突与过程 metadata 表现有价值；fullstack、Agent Result、工具选择和稳定性不足。
2. 奇怪文档是否识别？是。文件名不进入跳过规则，内容信号进入 Scout 和 DOC_READER。
3. README stale/conflict 是否识别？是。pilot 的两个冲突 case 均稳定识别，生产解析保留双方证据。
4. Shape 是否合理？部分合理。pilot Shape F1 为 0.8148、exact 为 0.7368，fullstack 复合自由文本与空结果需要 v3 校准后重验。
5. Tool plan 是否合理？未达门槛。pilot recall 0.6667、unnecessary rate 0.2381；registry 拒绝边界正确，但模型选择仍需重验。
6. Dynamic Profile 是否有管理价值？局部有，pilot view recall 0.6706 未达门槛。产品仍只展示有证据视图、unknown、conflict、history limitation 和降级状态。
7. Stage 2 是否值得？pilot 有正向 evidence/view gain，但旧 Schema 触发额外修复请求；独立 Final Schema 与高价值门控已实现，尚待可用 Provider 重验。
8. 成本是否可接受？未能确认。逻辑阶段严格为 0/1/2，pilot token/latency 已记录；无可靠日期价格且 Provider 最终返回 402，不伪造金额。
9. 稳定性是否可接受？否。pilot key-output Jaccard 为 0.5708，低于 0.80 门槛。
10. 模型失败是否优雅降级？是。Stage 2 timeout/schema/Provider/cancel/interruption 均保留当前 Stage 1 和工具证据。
11. 内部指标是否完全不进入产品？是。后端/前端边界测试禁止评测字段进入生产 source。
12. 产品是否仍聚焦项目事实与演进？是。评测、Profile 与 Adapter 结果都不能绕过 ProjectFact。
13. 是否出现 Agent Manager / Eval Platform 倾向？否。Harness 是测试代码，无 API/UI/数据库；没有 Agent orchestration。
14. Adapter Contract 是否足够薄？是。三个接口、一个 envelope 和一个无持久化 validator；没有外部产品 PoC。
15. 是否重复造轮子？否。未新增 parser、SCIP producer、RAG、workflow、secret scanner、telemetry agent 或 eval framework。
16. 是否参考开源成熟方案？是。采用数据集/实验、版本化、脱敏 telemetry 和 thin-adapter 模式；选择记录在开源调研。
17. 是否有任何夸大准确率表述？否。所有指标均绑定日期、数据集、模型、Prompt 和 scope notice。

## Extreme acceptance matrix

| # | Scenario | Acceptance evidence | Result |
|---|---|---|---|
| 1 | Empty: 0 model | Service zero-model test and Ground Truth | PASS |
| 2 | Blank: 0 model | Blank-text zero-model test | PASS |
| 3 | Strange document: discover/deep read | Real pilot 3/3 and DOC_READER contract | PASS |
| 4 | Small script: no large architecture | Real pilot missed shape/evidence; v3 pending rerun | NOT PASSED |
| 5 | Frontend: no invented backend | Shape stable; tool plan noisy | PARTIAL |
| 6 | Backend: no invented frontend | Shape stable; tool plan noisy | PARTIAL |
| 7 | Desktop: no Web template | Real pilot shape correct | PASS |
| 8 | Monorepo: multi-shape/module diversity | Shape/evidence stable; tool plan noisy | PARTIAL |
| 9 | No Git: no Timeline | Deterministic history boundary passes; real pilot omitted expected current evidence | PARTIAL |
| 10 | Short Git: stays short | Historical Coverage regression | PASS |
| 11 | Long Git: no complete reconstruction claim | Milestone-window and bounded-history rules | PASS |
| 12 | Stale README warning | Real pilot conflict detection 3/3 | PASS |
| 13 | README/source conflict | Real pilot conflict detection 3/3 | PASS |
| 14 | Agent Result not Fact | Promotion boundary passes; real selection unstable | PARTIAL |
| 15 | Token usage only process metadata | Gate unit test and semantic rule | PASS |
| 16 | Unknown tool rejected | Planner registry test with DROP_DATABASE | PASS |
| 17 | Provider timeout degrades | Parameterized Stage 2 failure test | PASS |
| 18 | Final Synthesis failure keeps Stage 1 | Current `FAILED_DEGRADED` integration test | PASS |
| 19 | Secret not leaked | Redactor, envelope and artifact boundary tests | PASS |
| 20 | Repeated run key stability | Real pilot 0.5708 versus 0.80 gate | NOT PASSED |
| 21 | Large repository bounded cost | Case 18, context packer and discovery limits | PASS |
| 22 | No Key deterministic usable | No-model service test | PASS |
| 23 | Key removed after test does not crash | Provider absence returns deterministic path | PASS |
| 24 | Malformed external envelope rejected | Validator test | PASS |
| 25 | Duplicate external envelope deduplicated | Fingerprint/idempotency test | PASS |
| 26 | Missing project binding rejected | Validator ownership-boundary test | PASS |

Overall product-quality decision: NOT PASSED until the final v3 real-Provider batch is rerun and all release thresholds pass. Engineering safety boundaries remain accepted. No Tag or GitHub Release is part of V3.7.2 acceptance.

## 2026-07-27 funded GLM revalidation

The funded GLM `glm-5.2` / OpenAI Responses probe passed, and the complete unchanged 38-run batch was executed. The result remains `NOT PASSED`: 19 runs timed out, failure rate was 0.5000, Tool recall 0.1667, unnecessary-tool rate 0.7778, Dynamic View recall 0.0941 and Repeatability 0.4130.

The new real production-chain acceptance called `ProjectUnderstandingService.refresh()` for eight core cases. Strange document and small script passed after correcting physical-versus-logical request bookkeeping. Frontend, backend, fullstack, no Git, Agent Result and ProjectFlow itself timed out in Stage 1. The strange-document case actually executed `DOC_READER`, passed real Tool Evidence into Final Synthesis, cited valid Evidence IDs and persisted/read back the snapshot.

Unsupported Claim Rate remained 0 and critical must-not violations remained 0, but these do not compensate for the failed reliability, evidence recall, Tool, View, conflict and repeatability gates. V3.8 remains blocked. No Ground Truth, Prompt or threshold was modified.
