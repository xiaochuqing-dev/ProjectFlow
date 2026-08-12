# V3.8.5 RC3 验收证据索引

当前状态：PENDING_HUMAN_REVIEW_ROUND3 / NOT PASS。PR #15 保持 Draft、未合并。

## 冻结基线

- Ground Truth SHA-256：`ab7be7129130645000e9028031132c0b8e9362a7e6d1efb7b9d4abf0318d7d3f`。
- Round 1 与 Round 2 均为 `NEEDS_REVISION_NOT_APPROVED`，文件未修改。
- Round 2 manifest raw/canonical-LF：`e1aca397b469c4d1e4e4b4f6bb856306b2b3340bcb5df97e80d71a286a247349` / `b2841c74491d172919db4a37e723d6533ad99f77799e0191fd5d2a7bdb90e887`。
- Round 2 worksheet raw/canonical-LF：`8e9c04bde787b6bb6c2528f96e5d296dcf66186f66290298cf18ca21f68d73e7` / `44655c49ef0d21c58e7aef7df4e1295dba6e48a5ddff3039f4d42edb96824692`。

## RC3 真实 Provider

- run `31586433372`，validation head `b9e9c2de9a76b1b351f0e8db3651a46214c5433c`：GLM 与 DeepSeek qualification 均为 19/19；DeepSeek scenarios attempt 1 为 11/11。
- 同一 run 的 GLM scenarios attempt 1 为 1/11，仅重跑 job 的 attempt 2 为 0/11；两次失败均保留。
- correction-only 诊断 run `31592405476`，head `f3d520432a0be857cd21255051c796b28359fbfb`：attempt 1、2、3 均为两个 Story 窗口各在两次有界请求后 `HTTP 429`，0 个成功模型调用。日志只记录安全分类和请求数。
- 资格与 DeepSeek 场景候选工件已重新下载、扫描并核对哈希；未发现 Key、Authorization、Prompt、raw response、reasoning、raw payload 或机器绝对路径。由于缺少合格 GLM 11/11，候选文件没有覆盖仓库内 RC2 规范化工件。

候选 canonical-LF SHA-256：

- GLM Ground Truth：`1b5a47aebb088208c2ef6743faa4fedb3b98afbaa8f14bc965b56563596a588d`
- GLM V3.8.0：`b250c29e0c412b187589a3196a28a06f4e6e99f4239f1f2efdedca0f46c7fa7b`
- DeepSeek Ground Truth：`42a12a276f867d28ab84b5283138c870f030a10cabd9f4a4834c391645774efd`
- DeepSeek V3.8.0：`f66db5c47ed342fc4cc58583691c7724928b295db0db956284d63d563f7731b8`
- DeepSeek scenarios：`639a24891a0917b4a9498c262664ceb457caa8fc2111e40db98ec1454c357d27`

## Round 3

Round 3 设计为 30 Story / 8 Chapter，双 Provider 各 15/4，人工字段全部空白且 reviewerCount=0。冻结脚本和合同已完成，但因 GLM 11/11 未通过，`human-review-round3-manifest.json` 与 `human-review-round3-worksheet.md` 没有生成。不得用 RC2 GLM 场景或失败工件拼接样本。

## 确定性验证

- 本地 backend/H2：602 项，0 失败，0 错误，11 个条件跳过；只排除本机 Docker PostgreSQL 与尚不存在的 Round 3 清单合同，Maven 以 0 退出。
- 根 `Start-ProjectFlow.bat -NoBrowser`：Next 16.2.11、Spring Boot/H2、双端就绪通过；Build ID `20JnrO0wTzUPAG3ebVDwu`，退出后 3000/8080 无监听。
- run `31592405476` 的 browser、frontend、Hermes、Obsidian、sensitive-content 通过；backend/H2 与 PostgreSQL 仅因 Round 3 文件未生成而失败。
- 最终 evidence-head required CI 尚不存在，不能写成通过。

## 当前阻塞与权限

唯一外部执行阻塞是 GLM `HTTP 429`。恢复可用的 GitHub Actions Secret 后，先运行 correction 探针；通过后再运行完整 GLM 11/11，复制并扫描六份同头工件，冻结 Round 3，再运行最终静态 CI。用户完成真实人工评分并明确批准前，不得 Ready、merge、backfill、Tag、Release 或清理分支/worktree。
