# ProjectFlow V3.10 复用与研究决策

日期：2026-08-25

## 范围与方法

本文只记录 V3.10 PHASE B2 的复用、边界和一手资料研究结果，不代表相关功能已经实现，也不授权在本阶段改动运行时、数据库或打包链。

研究通过 `agent-reach doctor --json`、Exa 有界检索、Jina Reader 读取官方页面和 GitHub 官方源码完成。doctor 结果显示 Web Reader 可用，Exa/GitHub 已配置但本机未能完成实时能力验证；因此每题只保留官方文档或官方源码的 1–3 个来源。没有把 Key、完整响应、凭证或本机绝对路径写入研究结果。结束前必须再次运行 `agent-reach check-update`。

当前仓库基线：Spring Boot 3.5.14、Java 17；`backend/pom.xml` 目前只有 PostgreSQL、H2 和 Testcontainers 依赖，没有 Flyway 或 Liquibase。生产配置仍使用 Hibernate `ddl-auto:update`，embedded profile 使用文件型 H2；现有 `ProjectFlowH2UpgradeIntegrationTest` 和 PostgreSQL Testcontainers 测试是迁移回归的基线。前端是 Next.js 16.2.11，现有启动器使用 `npm run build`/`next start`，嵌入式数据库目录仍是仓库下的 `.projectflow/local-data`。

## 总体决定

1. 不在 B2 直接把 JPA `ddl-auto:update`、Flyway、Liquibase、`schema.sql` 混用。若 V3.10 需要正式 schema migration，先对现有 H2/PostgreSQL 结构做有界清点、隔离备份和可重复 baseline，再选定一个 migration owner。
2. 密钥访问先抽象为单一 `SecretStore` 边界。Windows 生产候选为 Credential Manager；DPAPI 只作为明确的用户/机器绑定加密实现，不把密文误称为可迁移备份。非 Windows 只使用测试 fake 或明确的未配置状态，禁止回退到明文文件。
3. Next standalone 和 `jpackage` 都是打包候选，不是同一个运行时。Next standalone 仍需要 Node 运行时；`jpackage` 只解决 Java 应用与 Java runtime 的 Windows 打包。Windows portable 交付先以可验证的 app-image 为候选，不把 MSI/EXE 或目录复制宣称为跨平台、免依赖恢复。
4. 安装模式的数据默认迁移到 `%LOCALAPPDATA%\ProjectFlow`；portable 必须显式选择并使用可写数据目录。旧 `.projectflow/local-data` 只做可回滚、可校验、幂等迁移，不自动合并不同工作树的数据。
5. H2 backup 只负责本地 H2 恢复。外部 PostgreSQL 使用 `pg_dump`/`pg_restore` 和独立运维凭据；不实现 H2 zip 到 PostgreSQL 的自动恢复或跨数据库“兼容恢复”承诺。

## 1. Spring Boot、Flyway/Liquibase 与既有 H2/PostgreSQL 数据库

### 一手资料（3 个）

1. [Spring Boot Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)：官方文档说明 H2 属于 embedded database；没有 Flyway/Liquibase 时 embedded 默认可能使用 `create-drop`，非 embedded 默认是 `none`。脚本初始化默认 fail-fast；使用 Flyway 或 Liquibase 时应单独使用 migration tool，不要再叠加 `schema.sql`/`data.sql`。Flyway 的 PostgreSQL 运行还需要数据库专用模块。
2. [Flyway Baselines](https://documentation.red-gate.com/flyway/flyway-concepts/baselines)：既有数据库第一次接入时，baseline 标识 Flyway 之前的状态并建立 schema history；已有迁移脚本时必须明确 baseline version。Redgate 文档还要求用与目标环境相同的 baseline/shadow 状态验证后续 migration。
3. [Liquibase: Generate a changelog from an existing database](https://docs.liquibase.com/oss/implementation-guide-4-33/generate-changelog-from-existing-database)：既有库应先生成反映当前 schema 的 changelog，再用 `changelog-sync` 填充 `DATABASECHANGELOG`；sync 不改 schema，只标记当前状态已纳入追踪。

### 许可证、维护和安全记录

- Spring Boot 官方项目使用 Apache-2.0，Spring 维护链稳定；但文档版本、Boot parent、Hibernate 和数据库驱动必须与当前锁定版本一起验收。
- Flyway 文档来自 Redgate。Community/商业 edition、可用功能和许可证随具体 artifact/version 变化，不能只根据文档名作许可证承诺；引入前必须锁定坐标、版本、许可证和安全公告处理方式。
- Liquibase 同时存在 Community/OSS 与 Secure 发行边界；许可证、支持范围和扩展能力必须按精确依赖记录，不能把 Secure 文档视作免费组件授权。
- baseline、sync 或 `baselineOnMigrate` 都是高影响操作：错误的 baseline 会把未验证的既有表当成可信起点；启动 migration 失败必须阻止应用把部分状态当作成功，失败诊断不得包含连接密码。

### 复用候选与决定

- 复用当前 H2 旧库升级测试、PostgreSQL 16 Testcontainers 工作流和现有 schema repair 诊断，先建立“旧数据库副本 → baseline/迁移 → 业务 smoke test”的门禁。
- V3.10 不同时引入 Flyway 和 Liquibase。若需求只是版本化 SQL 和明确启动失败语义，Flyway 是较小候选；若确实需要跨数据库 changelog、显式 rollback 和结构化变更，再单独评估 Liquibase。两者都不是 B2 已批准依赖。
- 迁移切换后，生产 PostgreSQL 不能继续依赖 `ddl-auto:update`；H2 只能证明基础兼容，涉及 enum、锁、索引、时间精度和约束的迁移必须在 PostgreSQL 真实容器上通过。迁移失败保留旧数据和旧可启动版本，不自动删除或重建数据库。
- 现有 `api_key` 等敏感字段若后续迁移到系统凭据存储，必须把“schema migration”和“secret migration”拆成可恢复步骤；不能把一次启动同时视为两者都完成。

### 未知与 B2 门禁

- 当前真实用户 H2 文件和外部 PostgreSQL 的 schema 版本、手工 repair 历史、既有 enum/索引差异尚未形成基线清单。
- 尚未决定 migration owner、baseline version、回滚策略、多实例并发锁和旧安装回退窗口。
- 在这些信息补齐前，不得删除 `ddl-auto:update`、改写既有 H2 文件、自动 baseline 生产库或声称 PostgreSQL/H2 完全 parity。

## 2. Windows Credential Manager、DPAPI 与 Java 集成

### 一手资料（3 个）

1. [Microsoft CredWrite](https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credwritea)：Credential Manager 按当前用户安全上下文创建或替换凭据；同一 target/type 会被替换，错误通过 Win32 错误码返回。
2. [Microsoft CryptProtectData](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata)：DPAPI 默认绑定加密用户的 logon credential，通常也绑定同一台机器；可选 entropy 必须在解密时一致。`CRYPTPROTECT_LOCAL_MACHINE` 会放宽为同机任意用户，不能默认使用；返回的 blob 还需正确 `LocalFree`。
3. [JNA WinCrypt.java](https://github.com/java-native-access/jna/blob/master/contrib/platform/src/com/sun/jna/platform/win32/WinCrypt.java)：官方源码提供 `DATA_BLOB` 与 DPAPI native boundary；文件注明 LGPL-2.1-or-later 或 Apache-2.0 双许可证。JNA 主仓库仍在维护，但依赖版本、Windows ABI 和 native memory 释放必须锁定并测试。该源码不能直接证明 Credential Manager 的完整 `CredReadW`/`CredWriteW` 封装已经存在，后者需要先做小范围 binding PoC。

### 许可证、维护和安全记录

- Credential Manager/DPAPI 是 Windows 系统 API，不新增第三方运行时许可证；维护性取决于受支持 Windows 版本和调用用户 token。
- JNA 可维护且有 Apache-2.0 选项，但需要在 `THIRD_PARTY_NOTICES` 中按选定版本记录；不得复制 native binding 后失去许可证和版本追踪。
- Credential Manager 适合用户级凭据生命周期；DPAPI 适合本机/用户绑定的密文。DPAPI 密文复制到另一台机器或另一用户通常不可解密，不能当作 portable backup。禁止日志、DTO、异常和诊断回显密钥。
- 服务账户、远程桌面用户、无交互 token、组策略和企业凭据漫游可能改变可读写行为；系统调用失败必须返回“凭据存储不可用/需要重新配置”，不能自动写明文配置文件。

### 复用候选与决定

- 定义 `SecretStore`：`get(providerId)`、`put(providerId, value)`、`delete(providerId)` 只返回状态/密钥是否存在，不让业务层知道 Win32 结构。Windows 首选 Credential Manager Generic Credential，target 使用 ProjectFlow 命名空间和 provider 稳定 ID。
- 若需要把密文落盘，才使用 DPAPI 用户范围保护；不使用 `CRYPTPROTECT_LOCAL_MACHINE`，不把 DPAPI blob 放进跨机器同步或数据库备份。
- 非 Windows 测试使用内存 fake/确定性失败 fake，分别覆盖“可读写”和“平台不支持”；测试不得把 fake 结果描述为 Windows 安全验收。生产非 Windows 若无受支持 provider，应保持未配置状态。
- 当前数据库仍有 `AiProvider.api_key` 兼容字段。后续迁移应先写入系统存储并验证读取，再把数据库字段变为受控兼容/空值；保持现有写入 DTO、`apiKeyConfigured` 等 API 语义，GET 和错误响应永不返回原文。

### 未知与 B2 门禁

- 尚未验证公司策略下 Credential Manager 的 target 长度、服务账户、漫游和删除语义；也未确定是否允许加入 JNA 及其版本。
- 需要 Windows 真机测试：同一用户读写、换用户拒绝、重启后读取、删除、凭据损坏、无权限和 JNA native memory 释放。
- 需要定义旧数据库 `api_key` 的一次性迁移、失败重试和回滚；在此之前不能清空旧字段或把 portable 数据复制到另一用户后宣称可恢复。

## 3. Next standalone、Java runtime、jpackage 与 Windows portable

### 一手资料（3 个）

1. [Next.js output: standalone](https://nextjs.org/docs/app/api-reference/config/next-config-js/output)：`output: 'standalone'` 生成 `.next/standalone` 和最小 `server.js`，可减少部署依赖，但默认不复制 `public` 和 `.next/static`；启动仍是 `node .next/standalone/server.js`。
2. [Oracle jpackage command, Java 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html)：`jpackage` 可生成包含 runtime 的 app-image、Windows exe 或 msi；格式必须在目标平台构建，不能跨平台生成。未提供 runtime image 时会调用 `jlink`。
3. [OpenJDK LICENSE](https://github.com/openjdk/jdk/blob/master/LICENSE)：OpenJDK 代码以 GPL v2 为主，并包含适用文件的 Classpath Exception；实际发布仍须记录所选 JDK distribution、版本、许可证和安全更新来源。

### 许可证、维护和安全记录

- Next.js 上游仓库为 MIT，文档和框架仍在维护；生产包必须锁定 `package-lock.json`、Node 主版本和 Next 版本，并按当前依赖图做安全审计。standalone 不会替 Node runtime 负责 CVE 更新。
- `jpackage` 随 JDK 提供，不是独立第三方服务；OpenJDK GPL/Classpath Exception 和所选 vendor 的二进制分发条款必须随发布包记录。Windows MSI 还涉及 WiX/签名和安装上下文的额外验证。
- standalone 的 traced files 不是完整源目录；必须显式带上静态资源，不能把缺少资源的 `server.js` 当作可交付 portable 包。jpackage app-image 也只是某一平台/架构的应用目录，不能当作跨平台镜像。

### 复用候选与决定

- V3.10 先保留现有 `npm run build` + `next start` 启动链，避免在数据库、Windows 迁移和前端打包三个高风险面同时切换。
- 若要做 Windows 发行包，Next standalone 可作为前端部署产物；打包脚本必须复制 `public`、`.next/static`，带一个明确版本的 Node runtime，或在启动前明确检查用户 Node 版本。`jpackage` 只包装 Java backend/runtime，不能隐式打包 Node。
- portable 优先验收 `jpackage --type app-image` 目录：在干净 Windows 用户、无 Maven/Java 环境下启动 Java backend，并对 Node 依赖做单独检查。EXE/MSI 是后续安装体验工作，不作为 app-image 已通过的替代证据。
- 不引入自定义 Next server、不把 Java runtime 和 Node runtime 混成一个“万能 runtime”、不把安装目录作为数据库写入目录。

### 未知与 B2 门禁

- 尚未选定 Node 的发行版、Windows x64/arm64 目标、代码签名证书、JDK vendor、WiX 版本和离线依赖分发方案。
- 需要在干净 Windows 账户上验证 PATH 缺失、无管理员权限、端口占用、杀毒软件锁文件、Node native addon、静态资源和升级回滚。
- 只有 app-image、EXE/MSI、前端 standalone、后端 jpackage 和数据迁移全部分别通过后，才能发布“Windows portable”；任何单项缺失都必须在人话诊断中暴露。

## 4. LocalAppData、portable 数据目录与 legacy 迁移

### 一手资料（2 个）

1. [Microsoft KNOWNFOLDERID](https://learn.microsoft.com/en-us/windows/win32/shell/knownfolderid)：`FOLDERID_LocalAppData` 是 per-user、non-roaming 的 `%LOCALAPPDATA%`（通常为 `%USERPROFILE%\AppData\Local`）；旧 CSIDL 仅作兼容。
2. [Microsoft Known Folders](https://learn.microsoft.com/en-us/windows/win32/shell/known-folders)：新代码应使用 Known Folder ID/`SHGetKnownFolderPath`，而不是旧 `SHGetFolderPath`；已知目录可能被重定向，调用方还要负责释放返回的内存。

### 许可证、维护和安全记录

- Known Folder 是 Windows 系统能力，无额外第三方许可证；Microsoft 文档维护 API 兼容和重定向语义。Java 实现若只读取 `LOCALAPPDATA` 环境变量可避免新增 native 依赖；若需要处理特殊重定向，再复用第 2 题的 JNA 边界。
- `%LOCALAPPDATA%` 不漫游，适合本地 H2、日志、缓存和锁；凭据不应因为目录迁移而复制成普通文件。安装目录可能位于不可写的 Program Files，不能假设旁边可写。
- 路径必须做 canonical/符号链接/junction、保留设备名、大小写碰撞和目录逃逸检查。迁移采用临时目录、校验、原子 rename 和 marker；失败保留旧目录，不删除用户数据。

### 复用候选与决定

- 数据目录优先级建议为：显式 `PROJECTFLOW_DATA_DIR`/测试注入；显式 `--portable` 且目录可写时的 portable root；Windows 安装模式 `%LOCALAPPDATA%\ProjectFlow`；其他平台按现有受控用户目录策略。不要通过“当前目录存在 `.projectflow`”猜测 portable。
- installed mode 分离 `data`、`logs`、`config`；secret 只走 `SecretStore`。portable mode 的数据目录必须由用户显式确认，不能把 app-image 内部当作稳定数据卷。
- 旧 `.projectflow/local-data` 迁移按实例执行：先获得进程锁，复制/备份 H2 文件及必要元数据到临时目录，校验可打开和版本，再写 migration marker，最后切换新 root。源目录在一个成功启动和备份周期内保留；重复运行必须得到同一结果。
- 如果检测到多个 legacy root 或不同工作树的数据库，显示冲突并要求选择，禁止自动合并、覆盖或按目录名猜测项目身份。旧日志可保留，旧 secret 不通过文件迁移。

### 未知与 B2 门禁

- 当前仓库只明确使用 `.projectflow/local-data`；用户是否有多个 clone、多个用户、重定向 profile、网络盘或已有 portable 数据尚未盘点。
- 需要冻结新 root 命名、实例稳定 ID、迁移 marker schema、锁语义、ACL 和清理窗口，并覆盖中断、只读目录、磁盘不足、junction、杀毒锁文件和回滚。
- 在迁移验收前，保留 `PROJECTFLOW_DATA_DIR` 兼容覆盖和旧路径只读发现能力；不能一次发布中同时更改数据位置、数据库 schema 和 secret 存储而没有独立恢复点。

## 5. H2 backup 与外部 PostgreSQL restore 边界

### 一手资料（3 个）

1. [H2 Tutorial: Upgrade, Backup, and Restore](https://h2database.github.io/html/tutorial.html)：H2 推荐压缩 SQL script 作为更可读、与数据库版本更独立的备份；`BACKUP` 生成事务一致的 zip。运行时直接复制数据库文件不受支持，除非文件系统提供快照语义。
2. [PostgreSQL pg_dump](https://www.postgresql.org/docs/current/app-pgdump.html)：`pg_dump -Fc`/directory archive 由 `pg_restore` 恢复，归档跨架构可移植；文档警告恢复会执行源 superuser 选择的任意代码，不可信 dump 必须先检查。
3. [PostgreSQL pg_restore](https://www.postgresql.org/docs/current/app-pgrestore.html)：`pg_restore` 需要连接目标数据库，可选择性、重排或在新库中恢复；它不是应用内的文件复制操作。

### 许可证、维护和安全记录

- H2 官方源码采用 MPL-2.0 或 EPL-1.0 双许可证，项目仍维护；备份文件仍可能包含 API 配置、用户数据和事实内容，必须按敏感数据处理并限制权限。
- PostgreSQL 使用 PostgreSQL License，社区维护；`pg_dump`/`pg_restore` 是外部客户端工具，版本、服务器权限、扩展、角色和 tablespace 都属于运维边界，不应由应用偷偷代管。
- 备份应加密/限权并排除日志中的 DSN、密码和 dump 内容；恢复不可信 archive 前检查 TOC/SQL，防止恢复过程执行恶意对象或函数。

### 复用候选与决定

- embedded H2 提供“应用停止或通过 SQL `BACKUP` 的一致性备份 + 隔离副本 restore smoke test”。优先保留 SQL script 作为跨 H2 版本恢复材料，zip 仅作为快速本地恢复格式；禁止裸复制正在运行的 `.mv.db`。
- 外部 PostgreSQL 只提供明确的运维 runbook：`pg_dump -Fc`、目标库准备、权限/扩展检查、`pg_restore`、应用 migration 和 smoke test。应用可检测数据库类型并显示边界，但不自动执行任意外部恢复命令。
- H2 zip 不能直接送入 PostgreSQL；H2 SQL script 也不是安全的跨数据库迁移。若未来需要 H2→PostgreSQL，只能以 schema mapping、数据脱敏、迁移脚本和真实 PostgreSQL 验收为独立项目。
- 备份恢复验收必须覆盖：H2 隔离文件 round-trip；PostgreSQL 16 Testcontainers dump/restore；损坏 archive、权限拒绝、目标 schema 不匹配、未安装 `pg_restore` 和恢复中断。失败保留原库，不自动清空目标。

### 未知与 B2 门禁

- 尚未确定生产 PostgreSQL 的 major version、扩展/角色/tablespace、备份保留和密钥管理策略，也未定义 H2 文件版本升级与备份加密格式。
- 需要确认用户希望的是应用级导出、数据库管理员备份还是灾难恢复；三者的凭据、权限和恢复责任不同。
- 在 runbook 和干净目标库演练完成前，不得把 embedded backup 按钮描述成外部 PostgreSQL backup/restore，不得把 H2 恢复成功当作 PostgreSQL 恢复证据。

## 后续只允许的验证项

- 先生成现有 H2 与 PostgreSQL schema/数据基线和脱敏 manifest，再决定是否引入一个 migration tool。
- 先做 `SecretStore` 接口与 Windows/non-Windows contract test，再做 Windows 真机 native provider；不先改现有 API key 表。
- 先产出可重复的 Next standalone 与 Java app-image 构建清单，再决定是否进入 EXE/MSI。
- 先完成 LocalAppData/portable/legacy 的路径与回滚测试，再迁移真实用户目录。
- 先做 H2 与 PostgreSQL 隔离 restore 演练，再发布备份/恢复用户入口。
