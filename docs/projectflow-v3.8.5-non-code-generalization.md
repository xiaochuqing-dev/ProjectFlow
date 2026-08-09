# ProjectFlow V3.8.5 非代码项目泛化记录

状态：自动化双 Provider 非代码门禁 PASS；真实人工可读性 PENDING，因此不宣称最终泛化质量 PASS。

RC2 保持 Provider-neutral：模型不生成 role 或工程结构；确定性语言层根据材料类型生成安全 fallback，不把演示、研究、数据、品牌页或无 Git 版本强行描述成后端、Controller、数据库或发布能力。Gateway、Agent、Hermes 与 Obsidian 读取同一个 corrected view，GET 不触发模型调用。

## workflow 31318477841 场景结果

| 场景 | GLM | DeepSeek Flash | 当前性与结构安全 |
| --- | --- | --- | --- |
| 演示材料 | PASS | PASS | CURRENT_STATE_ONLY；0 Evidence/Strong Fact 违规 |
| 研究报告 | PASS | PASS | CURRENT_STATE_ONLY；0 Evidence/Strong Fact 违规 |
| 数据分析 CSV/JSON | PASS | PASS | CURRENT_STATE_ONLY；0 Evidence/Strong Fact 违规 |
| 品牌页 | PASS | PASS | CURRENT_STATE_ONLY；0 Evidence/Strong Fact 违规 |
| 无 Git、只有版本材料 | PASS | PASS | CURRENT_STATE_ONLY；未伪造历史成熟度 |

两者最终 5/5，未处理窗口、Invalid Evidence、跨项目引用、unsupported strong fact、raw response、reasoning、Key 和绝对路径持久化均为 0/false。DeepSeek attempt 1 的这五类和 Dogfood 也通过；失败只发生在 17-window 及其 correction 前置依赖，attempt 2 后全部 11/11。

这些结果证明材料类型与安全边界，不等于大众语言已由人工认可。冻结 30 Story / 8 Chapter 已包含非代码分层样本，当前评分为空。PR #15 继续 Draft。
