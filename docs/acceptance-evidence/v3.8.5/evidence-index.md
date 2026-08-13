# V3.8.5 RC3 验收证据索引

当前状态：PENDING_HUMAN_REVIEW_ROUND3 / NOT PASS。PR #15 保持 Draft、未合并。

## 冻结基线

- Ground Truth SHA-256：`ab7be7129130645000e9028031132c0b8e9362a7e6d1efb7b9d4abf0318d7d3f`。
- Round 1 与 Round 2 均为 `NEEDS_REVISION_NOT_APPROVED`，文件未修改。
- Round 2 manifest raw/canonical-LF：`e1aca397b469c4d1e4e4b4f6bb856306b2b3340bcb5df97e80d71a286a247349` / `b2841c74491d172919db4a37e723d6533ad99f77799e0191fd5d2a7bdb90e887`。
- Round 2 worksheet raw/canonical-LF：`8e9c04bde787b6bb6c2528f96e5d296dcf66186f66290298cf18ca21f68d73e7` / `44655c49ef0d21c58e7aef7df4e1295dba6e48a5ddff3039f4d42edb96824692`。

## RC3 真实 Provider

- 旧失败链 `31586433372` 与 correction-only `31592405476` 原样保留，不被后续成功覆盖。
- correction 探针 run `31733370522`，head `73d11250cddce3594d5ddb4ef54cd8c6d652dac7`：GLM 资格与 correction 场景均通过，确认额度恢复。
- 正式 affected run `31733839404`，同一 head、双 Provider、max：GLM 与 DeepSeek qualification 均为 19/19，scenarios 均为 11/11。
- GLM scenarios：52 次物理请求、798,608 tokens、1 次 repair、153 次公开确定性标题回退；DeepSeek scenarios：57 次物理请求、1,002,415 tokens、2 次 repair、44 次公开确定性标题回退。
- 两家 Dogfood 均找到旧 ae9f P0 且 Claim state 为 OBSERVED；非法 Evidence、跨项目引用、不受支持强事实均为 0。六份工件未发现 Key、Authorization、Prompt、raw response、reasoning、raw payload 或机器绝对路径。

Round 3 来源工件 canonical-LF SHA-256：

- GLM Ground Truth：`bad38011a54fecf4575722503a417c526381e299cb42e9372b583765943e4971`
- GLM scenarios：`be43f2218a40100ad1de104f187ae87d0b75bc0d18ffb1071f572a508e6db638`
- GLM V3.8.0：`7ebf202f48723b9506607ce2bb524b444c25ed3f446c997bae32f96a778f1eb2`
- DeepSeek Ground Truth：`6a6797602298cb9fe42bfe8c2ccd2ffeaf3d98be1ba502e17aae8781d2e4715f`
- DeepSeek scenarios：`bbb47df770581e9bceacbee465494fabe3952aaf3ecd6542faa2fdea74172ee9`
- DeepSeek V3.8.0：`7a8a103eb61522eadd529b136fa59e95c5752cbb76599237be33160b0c5d8cfe`

## Round 3

Round 3 已冻结为 30 Story / 8 Chapter，双 Provider 各 15/4，人工字段全部空白且 reviewerCount=0。manifest/worksheet canonical-LF SHA-256 分别为 `f316b71a6bec24f7ba40c2da81ef210b101b3ca238c688793fa32d48be877c1b` 与 `4d57d7d1fa5bb975465db9be413f70cf943ca7c9c70d8174ba0d4dcdd7d85ca6`。清单合同 1/1 通过。

## 确定性验证

- 本地 backend/H2：597 项，0 失败，0 错误，5 个条件跳过；Maven 以 0 退出，耗时 5 分 24 秒。
- 根 `Start-ProjectFlow.bat -NoBrowser`：Next 16.2.11、Spring Boot/H2、双端就绪通过；Build ID `yFBn3UKDWR9I_MD9j1vq0`，readyAt `2026-08-14T04:15:07.3018054+08:00`，退出后 3000/8080 无监听。
- 阻塞证据 head `e0fd50ed98e75c38fe1d762c89b501344b496c04` 的 push run `31594703405` 与 PR run `31594709131`：browser、frontend、Hermes、Obsidian、sensitive-content 通过；backend/H2 与 PostgreSQL 都是 597 项中仅 `ProjectHistoryHumanReviewRound3ManifestTest` 1 项失败。
- 当前 run `31733839404` 的静态 backend/PostgreSQL 失败仍只来自运行开始时尚无 Round 3 文件；最终 Evidence head 推送后必须重新取得 required CI 全绿。

## 当前阻塞与权限

外部 GLM 执行阻塞已解除，自动化剩余门禁是最终 Evidence head 的静态 CI。人工门禁仍未开始：用户完成真实评分并明确批准前，不得 Ready、merge、backfill、Tag、Release 或清理分支/worktree。
