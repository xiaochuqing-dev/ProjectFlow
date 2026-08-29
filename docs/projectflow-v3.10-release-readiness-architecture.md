# ProjectFlow V3.10 Release Readiness 架构冻结

日期：2026-08-25

状态：FROZEN_FOR_IMPLEMENTATION

基线：V3.9 final master `dd5ee41b6afcbd7703fa0883dc115c11f4821447`

分支起点：`c65d6d1807bb`

## 1. 决策边界

本文件冻结 PHASE B3 的高风险架构。后续实现只能在不改变数据安全、凭据安全和失败语义的前提下做小幅机械调整；任何会放宽未知 schema、明文凭据、外部网络暴露或用户数据覆盖边界的变化都必须重新进行 Sol 级审查。

V3.10 只完成 release readiness：版本化数据库迁移、升级备份与恢复、OS-backed Provider credential、local security、稳定运行目录、source-independent Windows portable runtime、供应链门禁和相关回归。V3.8.5 History、V3.9 Continuity、Memory、Context、Hermes、Obsidian 和现有 UI 只做不回归验证，不另建引擎。最终桌面 shell、installer 品牌体验、tray、updater GUI、后台 daemon 和 V4 视觉工作继续延期。

## 2. 版本化数据库迁移

### 2.1 单一 schema owner

选择 Flyway Community，版本由 Spring Boot 3.5.14 dependency management 锁定。只引入 `flyway-core` 与 PostgreSQL 官方数据库模块。原因是当前需求是有序 SQL、checksum、schema history、H2/PostgreSQL 支持和确定的 fail-fast，不需要同时引入 Liquibase 的第二套 changelog/rollback 语义。

Release、embedded release 和 external release profile 统一采用：

- Flyway owns schema。
- Hibernate `ddl-auto=validate`，不创建或修改表。
- 不叠加 `schema.sql`、`data.sql`、Liquibase 或 Hibernate update。
- 单元测试可以保留隔离的 `create-drop`，但不得作为 release migration Evidence。

### 2.2 迁移版本

- V1：冻结的 V3.9 schema baseline，包含 V3.9 已有表、约束和索引。
- V2：为 `ai_providers` 增加 nullable `secret_ref`，保留 nullable legacy `api_key` 作为一次性迁移输入，不再用于新写入。
- 后续版本只追加；V3.10 不伪造通用 DOWN migration。

空库从 V1 顺序执行到最新。已知 V3.9 库在 V1 controlled baseline 后执行 V2。成功的 legacy credential migration 把 `api_key` 值清为 null；列暂时保留是为了失败恢复，不表示允许继续明文写入。

### 2.3 三类数据库判定

启动时由受控 `FlywayMigrationStrategy` 在 JPA 初始化前执行：

1. Empty：业务 schema 无用户表且无 Flyway history。直接执行所有 migration。
2. Known V3.9：无 Flyway history，且标准化 metadata 与仓库冻结的、按 H2/PostgreSQL 分开的 V3.9 signature 完全一致。先满足备份前置条件，再显式 baseline 到 V1，随后 migrate。
3. Unknown/partial：无 Flyway history且不是 empty，也不与 V3.9 signature 完全一致。抛出 `UNSUPPORTED_LEGACY_SCHEMA` 并阻断启动；禁止猜测、自动 repair 或 `baselineOnMigrate`。

已有 Flyway history 的数据库必须先 validate checksum/version，再 migrate；未知、future 或 checksum mismatch 使用 `SCHEMA_MIGRATION_BLOCKED` 阻断。

V3.9 signature 来自仓库内代表性 V3.9 fixture，不含真实用户数据。签名标准化 table、column、type family、nullability、primary/unique/foreign-key 和 index identity，并按数据库方言分别冻结。Flyway 自身表、H2 system schema 和 PostgreSQL system schema不参与签名。

### 2.4 H2 与 PostgreSQL

H2 embedded 在 known V3.9 baseline 或已有 history 且存在 pending migration 时，先通过 H2 `BACKUP TO` 创建事务一致 zip，并写入带 SHA-256 的 manifest；备份失败阻断 migration。

External PostgreSQL 不由应用偷偷运行企业备份。非空数据库存在 pending migration 时必须提供显式 `PROJECTFLOW_POSTGRES_BACKUP_CONFIRMED=true`，否则以 `BACKUP_REQUIRED` 阻断；运维合同要求先完成 `pg_dump -Fc`、校验 archive 和恢复演练。Flyway 自身事务能力不替代备份。

### 2.5 旧 ApplicationReady 数据迁移

已有 ProjectFact、Timeline、Capability 和 Provider protocol 等数据修复服务不是 schema owner。V3.10 保留其幂等数据语义，但必须满足：

- schema migration 完成后才运行。
- 不能用逐条 `catch(Exception) -> continue` 把部分迁移伪装成成功。
- 可选/历史派生数据失败可以 degraded，并保留诊断；会造成事实、凭据或所有权不一致的迁移必须 block。
- 数据修复不得修改 Flyway history。

## 3. Provider Credential / Secret Store

### 3.1 单一边界

新增 `ProviderCredentialStore`，业务只使用稳定 provider ID 和 opaque `secretRef`：

- `writeAndVerify(providerId, secret)`
- `read(secretRef)`
- `delete(secretRef)`
- `status(secretRef)`

实现不得向 DTO、日志、异常、数据库或 Agent result返回 secret。读取结果只在一次 Model Gateway request 生命周期内存在；adapter 从 canonical request 的临时 credential 读取，不再从 `AiProvider` entity 读取明文。

### 3.2 Windows 与测试实现

Windows release 使用 JNA 调用当前用户范围 DPAPI。密文 blob 原子写入 release config 下的 `credentials` 子目录，引用格式为 `win-dpapi:user:v1:<provider-id>`。不使用 `CRYPTPROTECT_LOCAL_MACHINE`，不把 blob 宣称为可跨用户或跨机器备份。路径必须保持在配置根内，写入采用临时文件、flush、原子替换和有界大小。

CI 和受保护的 real-provider smoke 使用进程内 `InMemoryProviderCredentialStore`；它只在明确 test/CI profile 可启用，不落盘，不是 Windows 安全验收。非 Windows release 或未支持平台使用 `UnavailableProviderCredentialStore`，禁止静默回退到明文文件、环境配置文件或数据库。

### 3.3 新建、更新、删除

- 新 key：先写 store 并 read-back verify，再在数据库事务中保存 `secretRef`；数据库失败时清理本次新建的 secret。
- 空白 key update：保留现有 secretRef。
- 显式 clear：先确认 metadata 更新语义；数据库不再引用后删除 secret。删除失败保留可诊断 cleanup 状态，不回显内容。
- 删除 Provider：先确保 credential cleanup 可完成；cleanup 失败时阻止删除 metadata，避免不可追踪 orphan。
- GET/API/frontend：只返回 `apiKeyConfigured`/store status 和 masked state，不返回 secretRef 全值或 secret。

### 3.4 V3.9 明文两阶段迁移

每个 legacy row 使用稳定 provider ID：

1. 在数据库事务外有界读取 legacy plaintext。
2. 写入 OS/test store。
3. read-back 并常量时间验证。
4. 在数据库事务内设置 secretRef，同时把 `api_key` 置 null。
5. 提交后清除内存 byte/char buffer。

Store 写/验证失败时数据库不变。数据库提交失败时，若 secret 是本轮新建则尝试删除；删除失败记录不含值的 orphan cleanup diagnostic，下一次以同一稳定 ref 幂等重试。存在 legacy plaintext 但 store 不可用时启动阻断为 `SECRET_MIGRATION_FAILED`；没有 legacy plaintext 时，核心 History/Memory 读取可以继续，但 Provider call 以 `SECRET_STORE_UNAVAILABLE` 明确 degraded。

成功迁移的硬门是所有 `ai_providers.api_key` 非空行数为 0。

### 3.5 自定义 header

`authHeaderName` 和 `queryKeyName` 只是名称，credential 仍由 Secret Store 提供。`safeHeaders` 只允许非凭据型附加 header；Authorization、Cookie、Proxy-Authorization、API key/token/secret/credential 等名称一律拒绝。用户需要自定义 API key header 时使用 `API_KEY_HEADER + authHeaderName`，不能把 credential 塞进 `safeHeaders`。

## 4. Local Security

### 4.1 Runtime mode

- `local-release`：默认 `server.address=127.0.0.1`，frontend `HOSTNAME=127.0.0.1`，auth 可以关闭。
- `external`：必须显式 opt-in；auth 必须启用，JWT 必须由用户提供且不是 placeholder，CORS origins 必须为显式有界列表，数据库/Redis 默认凭据不得使用。
- `developer`：保留现有开发入口，但人话标注开发用途，不得冒充 release gate。

Preflight 发现 auth-off 且非 loopback bind、external mode 缺认证、placeholder JWT、默认数据库密码或 wildcard CORS 时阻断，使用稳定错误码。

### 4.2 其他安全合同

- 自动生成的 password reset code 不写日志；auth-required release 必须通过显式受控配置获得 reset/bootstrap secret。
- CORS allowed headers 使用明确 allow-list，不使用 `*`。
- health 只返回存活/就绪和非敏感错误码，不返回路径、DSN、凭据或 exception。
- Provider、数据库和 HTTP 日志经过统一 redaction；Authorization、API key、DB password、custom credential header、raw provider payload 不进入日志。
- 普通 CI、artifact 和报告不包含真实 Provider key。真实 Provider 只走 protected workflow repository secret。

## 5. Runtime 目录与 legacy 数据

Release installed mode 默认根为 Windows `%LOCALAPPDATA%\ProjectFlow`，由 launcher 显式解析并注入；不依赖当前工作目录。目录合同版本为 `v1`：

- `data/database`：H2 与持久业务数据。
- `backups`：已完成且有 manifest/hash 的备份。
- `logs`：滚动日志。
- `cache`：可重建缓存。
- `config`：非明文配置、credential encrypted blobs 和实例 metadata。
- `temp`：可清理临时文件。
- `run`：PID、lock 和受控运行状态。

Binaries 只存在于 release install root。安装根可只读，升级只替换 binaries，不递归复制或删除 data root。

优先级为显式 `PROJECTFLOW_DATA_DIR`，然后显式 portable root，最后 LocalAppData installed root。portable 只能显式开启且 data root 必须可写；不得因旁边存在 `.projectflow` 自动猜 portable。

旧 repo `.projectflow/local-data` 只通过显式迁移工具处理：单 legacy source、目标为空、进程已停止、先备份、复制到 temp、hash/可打开验证、原子切换、写 marker。多个 source、目标非空冲突、权限/空间不足或校验失败都阻断，不合并、不覆盖、不删除唯一旧数据。旧目录至少保留一个成功启动与备份周期。

## 6. Release Runtime / Packaging

V3.10 Windows portable RC 采用一个确定性目录/zip，不绑定未来 V4 shell：

- Spring Boot executable jar。
- `jlink` 生成的 Java 17 runtime image。
- Next `output: standalone` 产物、`public`、`.next/static`。
- 与 CI 锁定 Node 主版本一致的 `node.exe` runtime。
- release start/stop/restore scripts。
- H2 restore 所需的锁定 runtime jar。
- version manifest、third-party notice、逐文件 SHA-256 和 archive SHA-256。

构建阶段允许 Maven/npm/Git；运行阶段禁止调用 Maven、npm、Git、`npm ci`、`npm run build` 或 source tree。Start 顺序为：manifest/checksum preflight → runtime/data dirs → explicit legacy data action → port/lock preflight → backend schema/secret startup → backend ready health → frontend → user URL。Stop 只终止 manifest/PID 证明属于本实例的进程，等待端口释放，不按端口强杀未知进程。

Manifest 至少记录 product version、source SHA、UTC build time、build mode、Spring Boot、Next、Java runtime、Node runtime、Flyway schema version、runtime data contract version、bundled runtimes 和 artifact hashes；禁止绝对构建机路径。

## 7. Backup / Recovery / Rollback

四种语义分开：

- Binary rollback：只替换 binaries；旧 binary 在 schema version 不兼容时必须阻断，不能强启。
- Schema rollback：默认不提供自动 DOWN；通过修复 migration 或 restore 到升级前完整数据库实现。
- User data restore：使用验证过的 backup，restore 前再次保护当前库。
- Migration failure recovery：Flyway transaction/failed state诊断 + 保留升级前 backup；不自动删除原库。

详细操作合同见 `docs/projectflow-v3.10-upgrade-and-recovery-contract.md`。

## 8. 启动失败语义

启动 gate 与行为：

1. `RELEASE_RUNTIME_INCOMPLETE`：manifest/checksum/runtime 缺失，block。
2. `DATA_DIRECTORY_UNWRITABLE` 或 legacy conflict：block，不改源数据。
3. `PORT_CONFLICT`/foreign PID：block，不杀未知进程。
4. `BACKUP_FAILED`/`BACKUP_REQUIRED`：pending DB migration 前 block。
5. `UNSUPPORTED_LEGACY_SCHEMA`/`SCHEMA_MIGRATION_BLOCKED`：block。
6. `SECRET_MIGRATION_FAILED`：存在 legacy plaintext 时 block；无 legacy plaintext 但 store unavailable 时 core ready，Provider degraded。
7. `PROVIDER_UNAVAILABLE`、429/5xx/invalid credential：Provider task失败，core History/Memory read继续。
8. Obsidian unavailable：projection失败并可重试，core继续。

所有诊断只包含错误码、阶段、数据库类型、schema version/计数和安全摘要；不包含绝对路径、连接密码、secret、prompt、raw response 或 Authorization。

## 9. Supply-chain 与验收

Frontend production audit、backend CVE/SBOM inventory、Actions runtime、artifact sensitive scan 和 Windows release smoke 是 required gate。Critical 必须为 0；High 优先修为 0。无法安全修复的 High 必须逐项记录 CVE、实际依赖路径、reachability/mitigation 和 Sol 接受，未审查 High 阻断 PASS。

普通 CI 不接触真实 key。Secret Store 完成后，Luna Responses、DeepSeek Chat 和 Qwen Messages 必须在同一 source head 通过 protected in-memory credential path smoke；Windows CI 另用无真实 key 的 DPAPI round-trip证明 OS-backed adapter。

冻结 acceptance cases 位于 `docs/acceptance-evidence/v3.10/release-readiness-ground-truth.json`。它们是确定性工程门，不进入任何生产 Prompt，不需要模型 Calibration。

## 10. V4 compatibility

未来 V4 shell 只能消费本阶段稳定的 runtime manifest、start/stop、health、data root、schema migration 和 Secret Store边界。V4 不应重新发明数据库迁移、数据目录或 credential 持久化。本阶段不创建 Tag 或 Release。
