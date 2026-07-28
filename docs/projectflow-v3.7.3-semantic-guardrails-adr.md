# ProjectFlow V3.7.3 Semantic Guardrails ADR

状态：Accepted

## 职责边界

工程系统负责广泛发现、安全采样、来源分类、去重、多样性、currentness metadata、allow-list、客观可用性和结果验证。公开 Source Map 的 engineering-only 候选不再标注 HIGH/MEDIUM/LOW，而是 `UNKNOWN`。

模型结合整个项目判断语义角色、重要性、信息缺口、应否深读、适用视图和冲突。模型不得请求命令、参数、绝对路径或未知 Evidence，也不得把 Agent Result、README 宣传或 commit message 自动升级为事实。

## 结构化验证

Scout Schema 要求完整 assessment、tool request、shape、unknown/conflict/currentness 和 self-check。Normalization 只统一已登记别名和形状，不创造新语义。未知 Evidence、不可用 capability/view、无完整理由的请求和越界 epistemic status 被过滤或判为无效。

Final Profile 只保留 eligible section type 和 allowed evidence refs。无证据时保留 UNKNOWN/限制，不生成 Architecture、Backend、Database、Timeline 或 Evolution。High-value Evidence Gate 未通过时不做第二次模型调用；通过后 Final 失败则保留当前降级档案。

## 质量边界

内部 Eval 指标只存在于 test source 和 `target` 工件。产品只展示 Evidence References、Epistemic Status、Coverage、Unknowns、Conflicts、Limitations、Provider diagnostics、degraded status 和长任务阶段，不展示通用准确率、幻觉率、分数、排名或 benchmark。
