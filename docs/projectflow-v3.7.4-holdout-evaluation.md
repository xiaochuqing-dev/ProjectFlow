# ProjectFlow V3.7.4 Holdout Evaluation

## 冻结边界

Holdout 共 8 个独立用例，标签、Capability fixture、Prompt Builder 与 Ground Truth 均在正式运行前冻结。两个模型使用 Strong Fact Contract v2、Semantic Scout v11、Final Synthesis v6；正式运行后未修改 Prompt、标签、阈值或用例，也未按失败用例增加特判。

冻结文件 SHA-256 为 `13FF099D00E750406BF21C40CBAE1BEC0ACAE346C5DD24EB6AA1051C1A9F33F8`。Holdout Ground Truth SHA-256 为 `00F9F32C4D4E8C1184021F36558F9226C54DAA863A2558436974C94E624D23A5`。

## 首轮且唯一正式结果

| 指标 | GLM-5.2 | DeepSeek V4 Pro |
| --- | ---: | ---: |
| 用例 / 完成 | 8 / 8 | 8 / 8 |
| 硬失败 / Schema 失败 / 降级 | 0 / 1 / 1 | 0 / 0 / 0 |
| Unsupported Claim Rate | 0.0000 | 0.0000 |
| Critical Evidence Recall | 0.9091 | 0.8182 |
| Evidence Precision | 1.0000 | 0.9000 |
| Tool Precision / Recall | 0.6667 / 1.0000 | 0.6667 / 0.5000 |
| Deep-read Sufficiency | 1.0000 | 0.6667 |
| Conflict Detection | 1.0000 | 0.0000 |
| 请求 / Token | 13 / 126,279 | 10 / 71,830 |
| 平均 / P95 延迟 ms | 143,101.63 / 383,084 | 64,154.13 / 114,022 |

GLM 的 `holdout-tail-revision` 第二阶段出现 Schema mismatch，唯一一次定向修复仍失败，最终状态为 `FAILED_DEGRADED`；确定性 fallback 保住了产品链完成与非法引用为零，但这不是零降级通过。

DeepSeek 完成全部结构化请求且没有降级，但 Critical Evidence Recall 0.8182 低于冻结门槛 0.90，Deep-read Sufficiency 0.6667，且 `holdout-agent-conflict` 未识别冲突。它在若干需深读用例中跳过 AGENT_RESULT、GIT_HISTORY 或 DOC_READER。这是正式门禁失败，不能用 Calibration 结果替代。

GLM 结果 JSON SHA-256：`FFE8B97ADB4689A53B5098150CE9E875F6FA070CD22540AEE86A364B7C086304`。

DeepSeek 结果 JSON SHA-256：`67E7C2DBC4538C65F4FDDA2FB70014261690564F9409DA3B97BD8EA41F59319E`。

## 结论

Strong Fact 的 unsupported claim 与非法 Evidence 边界未发现违规，但两模型 Holdout 没有共同达到全部冻结门槛。Project Understanding Foundation 不能声明稳定，V3.8 Evolution Reconstruction 必须阻断。失败结果已保留，没有重跑或调低门槛。
