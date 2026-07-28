# ProjectFlow V3.7.3 Product Acceptance

1. 大项目是否可运行超过数分钟？YES。ProjectFlow itself 真实端到端运行 983,932 ms 后成功。
2. 是否仍存在隐藏短硬上限？NO。AUTO/UNLIMITED 没有主动 overall deadline；连接、单次 Provider 请求和重试仍有界。
3. UNLIMITED 是否可取消？YES。Provider 等待期间每 250 ms 检查取消、总体截止时间并更新心跳。
4. 坏连接是否有界释放？YES。连接超时默认 10 秒且限制在 1–60 秒；Provider 请求超时有限，SDK 内建重试关闭。
5. 是否有阶段进度和 Checkpoint？YES。持久化 Job 暴露 Intake、Discovery、History、Scout、Execution、Final、Persist 等阶段和心跳；重启时不自动重发状态未知的模型请求。
6. 是否为了 Token 降低质量？NO。当前唯一质量模式是 QUALITY_FIRST，必要 Evidence、深读和合格 Final 不因耗时或 Token 被静默省略。
7. Eval 和 Production Prompt 是否共用？YES。Scout、Final 和 direct Eval 统一使用 ProjectUnderstandingPromptBuilder。
8. Ground Truth 是否进入 Prompt？NO。Builder 输入类型不包含 Ground Truth，parity/leak tests 通过，Ground Truth blob 未变。
9. 模型是否真正判断 Evidence 重要性？YES。工程侧 importance 为 UNKNOWN，模型根据 Evidence 对结论的支持、限制、纠正或冲突价值判断。
10. 工程系统是否越界预判重要性？NO。工程系统只负责发现、客观分类、安全采样、eligibility、allow-list 和结果校验。
11. No-Git 是否仍请求 Git？NO。正式评测和真实端到端都没有不可用 Git 请求。
12. 无 SCIP 是否仍请求 SCIP？NO。SCIP 只复用有效精确索引，不存在时保持 fallback diagnostics。
13. Tool Selection 是否通过？YES。Precision 1.0000，Recall 0.8750，Unnecessary Rate 0。
14. Dynamic View 是否通过？YES。Recall 0.9529。
15. Conflict Detection 是否改善？YES。最终为 0.6667；仍作为后续可提升项保留。
16. Repeatability 是否通过？YES。0.9680。
17. Stage 2 是否有增益？YES。Evidence Gain 1.0000，View Gain 0.0476。
18. 8 个端到端是否全部通过？YES。8/8。
19. GLM 是否达到质量 Gate？YES。38/38，所有阻断门槛通过。
20. V3.8 是否可以放行？YES。真实模型、本地门禁、PR 两组 CI、PostgreSQL 16 Testcontainers 和最终功能 master CI 全部通过。
21. 是否出现 Provider Manager / Model Leaderboard 漂移？NO。
22. 是否创建 Tag？NO。
23. 是否创建 Release？NO。
