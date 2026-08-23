# V3.8.5 验收证据索引

当前状态：`PASS_BY_EXPLICIT_OWNER_OVERRIDE`。PR #15 已以 `29c154eb618ca43edf58c631c14cc1d296e14f3f` 合入 master，合并后 required CI run `32652683003` 全绿。Acceptance-backfill PR #16 承载最终元数据并在合并时生效。NO TAG / NO RELEASE。

2026-08-24 Final Human Sign-off：项目所有者明确批准最终 package 与 merge，并豁免本轮量化人工评分。独立证据为 `final-human-signoff.json`；reviewerCount=1，数值评分为 null，原阈值未宣称通过，single-reviewer、未重确认 item count 和 P0 仅按无新报告记录的限制均已披露。Round 1/2/3 与 Final Chapter 冻结工件保持原样。

2026-08-24 Acceptance Backfill：`final-acceptance-backfill.json` 记录 PR #15 Head/merge SHA、合并时间、Head push/PR CI、master CI、本地启动器、真实人工 override、最终三模型 run、十一份 canonical-LF 哈希和当前风险。

2026-08-24 PHASE A0 复核：PR Head/base、最终 Provider run 与冻结包未变化；Final Chapter/Round 3 manifest 合同测试 2/2 通过。交接材料记录的 8.5/10 总体意见已作为有限范围 human judgement 保存，但未被换算为精确 Gate 分数。当前状态审计见 `docs/projectflow-v3.8.5-final-signoff-current-state-audit.md`，产品语言与 V4.0 GUI 债务合同见 `docs/product-language-and-progressive-disclosure-contract.md`。

## Final Chapter Closure

- 最终同头 run `32609107531`，源码头 `e1b67f28428e73f39fc23aa6f85961155a20ffd8`。GPT 5.6 Luna Responses/max、DeepSeek V4 Flash Chat/max、Qwen3.7 Plus Messages/max 均为 qualification 19/19、Chapter scenarios 9/9；没有替代 Qwen 模型。
- Luna qualification/scenarios 为 42/56 次请求、123,198/338,147 Token；DeepSeek 为 42/55 次请求、188,051/412,738 Token；Qwen 为 42/60 次请求、177,380/421,525 Token。三家最终未解决、重试与安全计数均为 0；场景 repair 分别为 4、3、4。
- 当前 ProjectFlow 确定性 Dogfood 为 275 Commit、3,525 Source Event、371 Story、9 Chapter、271 Thread，0 模型调用；整体/最大 Chapter Representative Primary Coverage 为 0.7783/0.6507，安全、overlap、orphan 与 unsupported claim 计数均为 0。
- Final Chapter manifest 冻结 12 个 Chapter，三家各 4 个；Round 3 Story 展示变更子集为 30，Truth/Evidence semantic hash 30/30 不变。人工字段全部空白，reviewerCount=0。

最终六工件 canonical-LF SHA-256：

- Luna Ground Truth：`4e6031a1da307891c09ef19c20a8ee1304bd0e792a1a14541de38bbc255f9b30`
- Luna scenarios：`7ec1a4c84f538d4ba0df755817b0ef6cf281a541200f022d9644a2ddefe4f159`
- DeepSeek Ground Truth：`71f6843a5b1ee82bc0c2b674b9e64bc856421b8d1fd83af1a8cde7f1fa9b3094`
- DeepSeek scenarios：`58095cf37e387245d775642d590b6abf8590b32052e9ddfad42239b562e25ba9`
- Qwen Ground Truth：`64cc9f8463f1fcb618db64c6d3e3c34361721eea06855d1d2f43a0cab6c94709`
- Qwen scenarios：`4e049f4f7a0032a0f856ad4f986ec37fda3d5a4e7483e8524d32280faacf1edb`

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
- 根 `Start-ProjectFlow.bat -NoBrowser`：从干净 master `29c154eb618ca43edf58c631c14cc1d296e14f3f` 重建 Next 16.2.11 并启动 Spring Boot/H2，双端就绪通过；Build ID `8MRIhvsa6p0s1fOpqYUPl`，readyAt `2026-08-24T00:47:26.5337922+08:00`，退出后 3000/8080 无监听。
- 阻塞证据 head `e0fd50ed98e75c38fe1d762c89b501344b496c04` 的 push run `31594703405` 与 PR run `31594709131`：browser、frontend、Hermes、Obsidian、sensitive-content 通过；backend/H2 与 PostgreSQL 都是 597 项中仅 `ProjectHistoryHumanReviewRound3ManifestTest` 1 项失败。
- Evidence head `49622f16aebf77e892c70a5b091f17c2b8ebaa6c` 的 push run `31740051324` 与 PR run `31740054761` 均成功；backend/H2、PostgreSQL、browser、frontend、Hermes、Obsidian、sensitive-content 全绿。真实 Provider job 按非手动触发设计跳过。

## 当前关闭状态与限制

工程 Provider 阻塞已关闭，项目所有者已用明确 override 批准继续且 PR #15 已合并。final PR Head `60200af43045b4861c2a794130ed5183c8fe07bb` 的 push run `32652420679`、PR run `32652422016` 以及 merge master run `32652683003` 均成功。量化人工阈值仍未证明，single-reviewer、scope 与 P0 依据限制继续有效；acceptance-backfill 合并和最终 master 验证完成前不清理本阶段分支。Tag 与 Release 始终禁止。
