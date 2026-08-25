# ProjectFlow V3.9 真实 Provider 结果

当前状态：`AUTOMATED_REAL_PROVIDER_PASS / HUMAN_REVIEW_REQUIRED`。自动结果不能代替真人连续性评审，也不授权 Ready、合并、Tag 或 Release。

最终受影响重验为 GitHub Actions run `32666372066`，生产/eval 源码 Head 为 `eb38c78fe70d3cf9280e716f7fc906d8729b15b1`，输入为 `run_real_model=true`、`real_model_scope=affected`、`real_model_provider=all`。凭据只从受保护的 Repository Secret 注入；提交的工件不保存 Key、Authorization、Prompt、raw response、reasoning 或机器绝对路径。

## 最终矩阵

| Provider | Qualification | V3.8.5 Chapter regression | V3.9 continuity |
| --- | --- | --- | --- |
| GPT 5.6 Luna / Responses / max | PASS 19/19；42 请求；121,336 Token；310,572 ms；fallback 0；repair 0 | PASS 9/9；56 请求；344,326 Token；587,351 ms；fallback 0；repair 4 | PASS 3/3；15 请求；89,174 Token；134,210 ms；fallback 0；repair 0 |
| DeepSeek V4 Flash / Chat / max | PASS 19/19；42 请求；208,020 Token；559,460 ms；fallback 0；repair 0 | PASS 9/9；55 请求；409,930 Token；674,772 ms；fallback 0；repair 3 | PASS 3/3；15 请求；107,474 Token；163,444 ms；fallback 0；repair 0 |
| Qwen3.7 Plus / Messages / max | PASS 19/19；42 请求；172,592 Token；1,397,739 ms；fallback 11；repair 0 | PASS 9/9；57 请求；446,754 Token；3,106,499 ms；fallback 51；repair 4 | PASS 3/3；15 请求；111,102 Token；838,300 ms；fallback 16；repair 0 |

Qualification 合计 57/57，Chapter regression 合计 27/27，continuity 合计 9/9。所有最终场景的 validation repair failure、Invalid Evidence、跨项目引用、Unsupported Strong Fact、Raw Event loss 与绝对路径/Secret 泄漏均为 0。

V3.9 continuity 执行三个真实产品场景：小 delta 的 checkpoint/Context/no-op、真实 Provider 调用后的内存 HTTP 503 与持久化恢复、无 Git 文档连续性。三种协议的小 delta 都保持未受影响 Story/Thread identity 100%，最终 no-change 都为 0 模型请求；HTTP 503 场景只恢复失败范围，不重放成功 checkpoint；无 Git 文档保持 Event conservation 与零一层技术泄漏。

## 失败保留与修复

较早的 run `32659635453` 不被最终成功覆盖。其 Luna 和 DeepSeek 产品场景通过，Qwen continuity 为 3/3，但 Qwen Chapter regression 为 8/9；唯一失败是 `projectflow-current-history-dogfood`，`unsupportedClaimCount=1`。诊断证明缺陷在共享 Chapter 代表性工程合同，而不是 Qwen 专用分支。修复在保留旧 Chapter 前执行完整 representation plan 验证，并在回填时保留已选中的公开成果措辞。失败指标、根因、回归名称与原工件哈希保存在 `docs/acceptance-evidence/v3.9/failed-runs/32659635453/qwen-chapter-regression-summary.json`。

## 工件完整性

| Provider | Qualification SHA-256 | Chapter SHA-256 | Continuity SHA-256 |
| --- | --- | --- | --- |
| Luna | `aa617e591a02fe950da55c8fb9550c4c1ff961cf5833643c1127aca97006a30d` | `c670d136d7a5c4bc99cacd8d25d8f3928c0d1fd01068f0f39580faaeafba035a` | `5597c2aac09092f9520845222a7c67d8d972f5071fb0221ae40e018318bb90ef` |
| DeepSeek | `c0ae352df79aa9ba5807f77e442359e0da1dc3230d43bfb6cfd159d82de09c53` | `a86aaf3c014607a2da996d7555594aca63a6d369fa151c4bd736d698b1d5e015` | `924e3c90adf971eb1a42c830e70de61ce0349354f1cbf441e719ec7d7ce7e82c` |
| Qwen | `318562e4464775bab9eb64fa4fc90da13fcaf683954e14ced016cbfb74d72fa5` | `d5010781bd6d7bbb5b88d215ffa09e8d271719a040b7e1660d4055ac36e4058f` | `d9720b2377c8106eef70c8b5326a3ea20a878be67fb2194e16fc6a7b7af557c7` |

V3.9 自己冻结的 15 Calibration + 15 Holdout 主要验证 Event identity、Delta、Story/Thread/Chapter continuity、Correction、Current State、Context 和 Obsidian 等工程语义，因此 30/30 由 production 路径的确定性 Maven/Python 测试执行，不为每个 case 额外制造模型请求。身份、Evidence、Correction、checkpoint 和 Current State 始终由工程规则拥有，模型只改善已知 ID 的可替换措辞。
