# ProjectFlow V3.10 Runtime Data Directory 合同

日期：2026-08-25

状态：FROZEN_FOR_IMPLEMENTATION

合同版本：projectflow-runtime-data-v1

## 1. 目标

Release binaries、用户数据、数据库、备份、日志、缓存、配置和临时文件必须分离。更新或替换 release install directory 不得覆盖用户数据；install directory 可只读；启动结果不依赖当前工作目录或 source repository。

## 2. Runtime modes

### Installed local release

Windows 默认 data root 为 Known Folder LocalAppData 下的 `ProjectFlow`，通常对应 `%LOCALAPPDATA%\ProjectFlow`。Launcher 优先使用显式参数/环境覆盖，否则解析 LocalAppData；缺失或不可写时阻断，不回退到 install directory。

### Explicit portable

只有用户传入 `-Portable` 或显式 portable data root 时启用。默认 root 为 release directory 同级的用户指定可写目录，不是 binaries 子目录。Portable 必须通过写入、原子 move、lock 和剩余空间 preflight；失败不回退到 source/repo local。

### Developer

开发启动器可继续使用仓库 `.projectflow/local-data` 和 `logs`，但使用 `developer` mode/profile，证据只代表开发启动。它不得写 release manifest，也不得作为 clean release gate。

### External/server

External PostgreSQL mode仍使用同一 logs/config/cache/temp/run 合同，database 子目录可以只保存无敏感的 instance metadata，不伪装为 PostgreSQL backup位置。

## 3. 解析优先级

1. 测试注入的隔离 root。
2. 显式 `PROJECTFLOW_DATA_DIR` 或 launcher `-DataRoot`。
3. 显式 portable mode root。
4. Windows installed LocalAppData root。
5. 不支持平台的 release mode：明确 `DATA_DIRECTORY_UNSUPPORTED`，不写当前目录。

解析完成后必须 canonicalize，拒绝空值、filesystem root、install root、source root、junction/symlink 逃逸、Windows device name 和不可创建目录。所有子目录通过 resolved root生成，调用方不能拼任意绝对路径。

## 4. Layout

```text
ProjectFlow/
  data/
    database/
    storage/
  backups/
  logs/
  cache/
  config/
    credentials/
    instance.json
  temp/
  run/
    backend.pid
    frontend.pid
    instance.lock
```

含义：

- `data/database`：embedded H2 文件，只由数据库和 migration/restore工具写。
- `data/storage`：现有附件/项目材料等受控持久数据。
- `backups`：只保存 complete manifest引用的升级/恢复备份。
- `logs`：rolling application/launcher日志。
- `cache`：可删除并重建，不作为事实源。
- `config`：非明文配置、实例 ID 和 DPAPI encrypted blobs；Provider secret本身不进普通 JSON/YAML。
- `temp`：未完成构建/迁移/备份临时文件，可在无引用时清理。
- `run`：PID、实例 lock 和启动状态；异常退出后可验证并清理 stale state。

## 5. 文件权限与原子性

Release root 使用当前用户最小权限。凭据、数据库、备份和 config 不授予 Everyone 写权限。敏感文件创建后尽可能将 ACL限制为当前用户；DPAPI 仍是 confidentiality主边界，ACL 不是明文 fallback。

Manifest、marker、instance metadata、credential blob 和 backup metadata采用同目录临时文件、flush/fsync、原子 move。若文件系统不支持原子 move则操作失败并保留旧文件，不能用先删后写降级。

## 6. Logs

Release 日志按大小滚动，单文件默认上限 10 MiB，保留最多 7 个历史文件和 30 天，以更早触发者为准。Launcher 日志也遵守有界保留，不无限 append。

日志 redaction 至少覆盖 Authorization、Bearer/API key、JWT、database password、credential header/query value、reset code、raw Provider payload 和 URL user info。默认不记录 request/response body、Prompt、reasoning、完整文档或绝对用户路径。异常只记录安全类型、code、correlation ID 和已脱敏摘要。

## 7. Temp/cache cleanup

启动前可以清理超过 24 小时、且不被 active manifest/lock引用的 temp。Cache 可显式清空，不影响 ProjectFact、History、Snapshot、Correction、Capability、Context、Obsidian state或 database。Backups 永不由 temp cleanup处理。

## 8. Legacy repo-local migration

Legacy source 是显式指定的单个 `<repo>/.projectflow/local-data`。工具支持 `detect`、`plan`、`migrate`、`verify` 和 `rollback-to-source`，默认只 detect/plan。

Migrate gate：

1. Source/target resolved path 不相同且都在允许根内。
2. ProjectFlow 已停止，无 live PID/端口/H2 lock。
3. 只发现一个 source；多个 clone 不自动选择。
4. Target database为空；已有 target使用 `LEGACY_DATA_CONFLICT`。
5. Source先形成有效 H2 backup。
6. 复制到 target temp并计算文件 SHA-256。
7. 隔离打开 copied DB，验证 known V3.9/current schema classification。
8. 写 marker并原子切换。
9. 首次 release start成功后写 completed 状态；source仍保留。

Marker只保存 contract version、source相对安全 fingerprint、target instance ID、文件 hash、schema classification、UTC timestamps和状态，不保存绝对路径或 credential。

中断时下次根据 marker和 hash继续/回滚。禁止合并两套 H2 数据、递归复制正在运行的数据库、删除 source、覆盖 target或把旧 logs/credentials当作数据库内容迁移。

## 9. Release update

Release package只写 install directory中的 binaries和 manifest。Start/Stop/Restore脚本通过 data root合同访问用户状态。更新流程：

1. 下载/解包到新的 versioned install directory。
2. 验证 checksum/manifest。
3. 停止旧实例。
4. 使用同一 data root启动新版本并执行受控 backup/migration。
5. 成功后保留旧 binaries和升级前 backup至明确清理窗口。

禁止把新 package解包到 data root、递归覆盖 LocalAppData或在安装卸载时默认删除 data/backups。

## 10. Runtime preflight

启动前依次验证：

- version/checksum manifest完整。
- install root包含 backend、frontend、Java和Node runtime。
- data root合法、可写、非 install/source root。
- required subdirectories可创建，atomic write/move可用。
- instance lock/PID为本实例或可证明 stale。
- backend/frontend ports空闲；未知占用直接 `PORT_CONFLICT`。
- disk space满足最小启动和可能的 pre-migration backup。
- secret store mode与平台/profile匹配。

失败码包括 `RELEASE_RUNTIME_INCOMPLETE`、`DATA_DIRECTORY_UNWRITABLE`、`DATA_DIRECTORY_UNSUPPORTED`、`LEGACY_DATA_CONFLICT`、`PORT_CONFLICT`、`SECRET_STORE_UNAVAILABLE`。任何失败都不得杀未知进程、移动用户数据或写 install root。

## 11. Windows CI Evidence

Required Windows gate必须在 clean unpack目录验证：

- PATH移除 Maven/npm/Git/JAVA_HOME后仍能用 bundled runtime启动。
- install目录运行前后没有新写入。
- 首次/第二次启动、health、stop和端口释放。
- data在外部 root持久存在。
- stale PID/lock只在进程不存在且identity匹配时清理。
- port conflict不杀未知进程。
- DPAPI round-trip、删除和不可读状态，不使用真实 Provider key。
- V3.9 legacy H2 migration、backup/restore和sensitive log scan。

CI fake/in-memory credential store不能替代这一 Windows DPAPI Evidence。
