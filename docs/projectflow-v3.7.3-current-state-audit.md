# ProjectFlow V3.7.3 Current-state Audit

审计日期：2026-07-27

基线：`origin/master` SHA `084372c51c5c74376b3e3f0c806e22417622ac74`，V3.7.2 最终文档回填已合并。

## 已确认问题

1. direct Eval、Provider probe 和真实端到端测试把显式 Provider 超时压到 45 秒。V3.7.2 GLM 的 38-run 有 19 次 transport timeout，八个生产链案例只有 2 个完成。
2. 生产 Semantic Scout 与测试 Eval 各自维护 Prompt 文本，无法证明版本、规则和输出契约一致。
3. 工程候选分数被公开为 Evidence importance，文件类型和启发式因而越过“工程事实整理、模型语义判断”的职责边界。
4. Tool/View 只有后置过滤，没有先向模型提供基于真实环境计算的 eligible 集合及逐项理由。
5. Scout 输出仍兼容旧 `recommendedToolCalls`，缺少完整的信息缺口、预期价值、目标 Evidence 和现有证据不足理由。
6. AUTO 总时长仍被历史 Durable Job 默认值解释为固定截止时间，connection、request、overall deadline 未形成独立契约。
7. Job 的中断恢复分支存在空操作；模型请求期间没有统一的心跳与取消轮询。

## 保留边界

- 继续复用 Durable Job、Model Gateway、官方 Provider SDK、Capability Registry、Structure SPI 和已有 bounded Provider。
- ProjectFact 仍是唯一事实来源；Understanding、Plan、Profile、Coverage 和 Eval 都是可替换派生层。
- GET understanding/structure/evolution 继续只读持久化结果。
- 不新增依赖、数据库 schema、Provider Manager、模型排行榜、通用 Eval/RAG/workflow、parser、SCIP producer、watcher、Tag 或 Release。
- 原始 18-case Ground Truth、公式和门槛保持不变。

## 最小实施范围

实施集中在时间策略、Gateway/SDK timeout 传播、Job 取消与恢复、共享 Prompt Builder、Evidence importance 职责、Tool/View eligibility、结构化输出验证、内部 Eval 和必要的兼容 DTO。UI 只更新版本和类型契约，不加入内部质量指标。
