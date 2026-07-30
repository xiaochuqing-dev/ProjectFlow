# ProjectFlow V3.7.4 Product Acceptance

1. 是否仍坚持 Quality First？YES。正式链路未因 Token 或时间隐藏降质。
2. 是否存在隐藏 Token 降质？NO。Token 上限、重试与降级均可诊断。
3. 是否存在隐藏时间降质？NO。长请求继续运行到 Provider/任务边界，真实长尾已记录。
4. 强事实状态是否正式建立？YES。Strong Fact Contract v2 已实现并测试。
5. OBSERVED/VERIFIED 与 DECLARED/INFERRED 是否分离？YES。
6. 模型共识是否被错误当成事实？NO。
7. Agent Result 是否被错误升级？NO；只能形成候选或过程 Evidence。
8. 是否编造“为什么这样设计”？NO；无历史 Evidence 时保留 UNKNOWN。
9. 是否编造废弃方案？NO。
10. 是否编造技术债？NO。
11. 正常 Git 项目是否稳定？本地与 GLM 产品 E2E 稳定；双模型 Holdout 未共同达标，因此整体 NO。
12. README/Manifest/Test/CI 是否稳定发现？YES，确定性发现和原回归通过。
13. GitHub Evidence 边界是否明确？YES；只读、授权、有界、脱敏，不把远程宣传当已验证事实。
14. 奇怪文件名是否可发现？YES。
15. 内容是否优先于文件名？YES。
16. 8 万行中部事实是否发现？YES，确定性 Content Map 与模型评测均覆盖。
17. 8 万行尾部修订是否发现？YES；GLM Holdout 该用例发生第二阶段降级，已披露。
18. 跨块合并是否保留引用？YES。
19. 未读取区域是否披露？YES。
20. 冲突是否保留？工程边界 YES；DeepSeek Holdout 冲突召回为 0，模型门禁 NO。
21. Unknown 是否保留？YES。
22. Agent 是否能列出全部授权项目？YES。
23. Agent 是否能读取任一授权项目历史？YES。
24. 是否支持跨项目查询？YES，带所有权校验与硬上限。
25. 是否存在跨项目数据串线？NO。
26. Agent 是否能直接写强事实？NO。
27. Agent candidate flow 是否可用？YES。
28. Context Package 是否有来源？YES，含来源、revision、状态分区、预算和限制。
29. Model A 是否通过？Calibration YES；Holdout 有 1 次可见降级；产品 E2E 8/8 但 1 次 Final fallback。不能称全绿。
30. Model B 是否通过？NO。Calibration 通过，正式 Holdout Critical Evidence Recall 0.8182 未达 0.90；后续产品 E2E 因官方账户 HTTP 402 未完成。
31. 两模型是否共用事实契约？YES。
32. 是否运行 Holdout？YES，两个模型均运行冻结集一次。
33. Holdout 是否与 Calibration 分离？YES，独立 ID、Ground Truth 与冻结哈希。
34. 原 V3.7.3 Regression 是否退化？GLM 38/38 通过；DeepSeek 核心回归完成但语义指标较弱。没有用回归替代 Holdout。
35. 是否完成真实 E2E？GLM 原始 8 项完成；DeepSeek 在 Holdout 后账户额度耗尽，HTTP 402，未完成。
36. 是否更新 README？YES。
37. 是否更新 Context/Protocol？YES。
38. 是否上传巨型 Raw Artifact？NO。
39. 是否创建 Tag？NO。
40. 是否创建 Release？NO。

最终结论：PROJECT UNDERSTANDING FOUNDATION = NOT STABLE；STRONG FACT SAFETY GATE = NOT PASSED；REAL PROJECT GENERALIZATION GATE = NOT PASSED；V3.8 EVOLUTION RECONSTRUCTION = BLOCKED。
