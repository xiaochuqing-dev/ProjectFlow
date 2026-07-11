# ProjectFlow V3.3.8 context

主流程保持“待整理变更、开发推进段、批次化沉淀处理、项目沉淀、能力分析”。V3.3.8 只强化真实模型链路，不扩展推荐算法或业务入口。

所有模型入口必须使用 `ModelTaskType` 和统一网关。参数由 Provider/model capability、任务类型、输入规模和输出结构共同决定；不支持的字段不发送。JSON 语法、截断、Schema mismatch、reasoning 耗尽、证据拒绝和 Provider 故障保持不同语义与恢复路径。

真实 DeepSeek、固定模型自动化和人工质量抽样必须分开记录。任何诊断都不得保存 Key、Authorization、reasoning 原文、完整 prompt、原始响应或未脱敏源码。
