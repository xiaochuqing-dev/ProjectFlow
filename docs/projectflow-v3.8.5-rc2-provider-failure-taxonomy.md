# ProjectFlow V3.8.5 RC2 Provider 失败分类

分类使用 A 至 J：A 模型真实越界，B Prompt 含义不清，C Validator 过严，D Schema 不必要地复杂，E Evidence eligibility 缺陷，F 角色图工程缺陷，G 窗口或缓存缺陷，H 语言差但不是事实违规，I 截断，J 其他或证据不足。

历史安全工件确认 GLM 和 DeepSeek 各有 12 个 case 出现一次 UNSUPPORTED_CLAIM。工件保留 case ID、计数和聚合诊断，但没有 raw response 或细分 validator 命中字段。因此 24 个实例逐项登记为 J / UNKNOWN_FROM_SANITIZED_ARTIFACT，不能事后把每个实例武断归因给模型、Prompt 或 Validator。精确清单位于 `docs/acceptance-evidence/v3.8.5/provider-failure-taxonomy.json`，并由 `UnsupportedClaimFailureTaxonomyTest` 冻结。

代码审计能够确认两个跨案例因素，但不能倒推为每个实例的精确原因：旧 Story schema 属于 D，因为要求模型同时维护文字和全局角色关系；旧 ProjectFlow Dogfood 属于 F，因为窗口级模型被允许改写全局 Primary/Supporting 图。RC2 的处理不是放松 Evidence，而是缩小模型职责、保留 Unknown、由工程层唯一构图。

真实 run `31318477841` 的最终 19-case 两个 Provider 均为 0 rejected output、0 failed/pending window。此前 run `31303975027` 的 DeepSeek small-script reasoning-only 空 content 按 I 记录为已确认归一化输出失败；当前 run attempt 1 的 17-window 仅能从安全工件确认 15 succeeded、1 failed、1 pending，精确原因不足，按 J 保留，attempt 2 成功不覆盖首次失败。后续若仍有拒绝，只按实际保留的安全诊断归类。
