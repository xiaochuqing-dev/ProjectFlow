# ProjectFlow V3.10 升级、备份与恢复合同

日期：2026-08-25

状态：FROZEN_FOR_IMPLEMENTATION

## 1. 适用范围

本文定义 embedded H2 与 external PostgreSQL 的升级前置条件、备份有效性、失败恢复和回滚边界。它不把应用导出、数据库备份、跨数据库迁移和二进制回滚混为一件事，也不承诺任意 migration 都有安全 DOWN 操作。

## 2. 通用不变量

1. 未确认数据库类型、schema identity 和 migration history 前不写入。
2. unknown/partial schema 不 baseline、不 repair、不删表，直接阻断。
3. 升级前的唯一用户数据不得被移动、覆盖或删除。
4. 备份完成必须同时具备 payload、manifest、SHA-256 和 `complete=true`；临时文件或缺项 manifest 不是有效备份。
5. Migration 失败保留原数据库、升级前备份和明确错误码；不得清库后重试。
6. Restore 前再次保护当前数据，避免一次错误 restore 消灭最后可用状态。
7. 二进制版本与 schema version 不兼容时阻断旧 binary，不能以 Hibernate update 强行适配。
8. 备份、manifest、日志和报告不得包含数据库密码、Provider plaintext key、Authorization 或构建机绝对路径。

## 3. Embedded H2

### 3.1 Pre-upgrade backup

以下任一条件成立时必须在 Flyway migration 前备份：

- known V3.9 database 将 controlled baseline 并升级。
- 已有 Flyway history 且存在 pending migration。

应用使用同一 JDBC connection 执行 H2 `BACKUP TO`，得到事务一致 zip。禁止在数据库运行时直接复制 `.mv.db`。备份先写临时名，完成后计算 SHA-256、写临时 manifest，再原子改为最终名。

Manifest v1 包含：backup ID、product/source version、database type、UTC time、pre-migration schema classification、Flyway current/target version、payload filename、payload bytes、SHA-256、complete flag、创建方式和 data-directory contract version。只保存相对文件名。

默认至少保留最近 3 个有效升级备份；新备份成功且升级成功后才执行有界 retention。任何时候不得因为 retention 删除唯一有效升级前备份。临时/损坏文件可在不引用时清理。

### 3.2 Restore

Restore 是显式停机操作：

1. 确认 ProjectFlow backend/frontend 已停止且 PID/端口已释放。
2. 读取 manifest，验证版本、payload size、SHA-256 和 complete flag。
3. 对当前数据库创建独立 emergency backup；失败则停止 restore。
4. Restore 到同一 data root 下的临时目标。
5. 以只读/隔离 connection 打开并验证 schema identity。
6. 原子切换当前库；原文件移动到带 recovery ID 的保留名，不直接删除。
7. 启动当前兼容 binary，执行 Flyway validate、core count/hash smoke 和 secret migration status检查。

损坏 payload、错误数据库名、unknown schema、目标占用、磁盘不足、权限失败或隔离打开失败均不得替换当前库。

### 3.3 H2 recovery evidence

验收必须使用合成 V3.9 fixture，覆盖 Project、ProjectMemory、ProjectFact、ProjectHistoryEvent、Snapshot、Correction、Agent Candidate、Provider metadata、revision/current-state/context package identity。升级前后 Project loss、Raw Event loss、Strong Fact loss、Correction loss均为 0；重复启动不重复 mutation。

## 4. External PostgreSQL

### 4.1 应用边界

ProjectFlow 不在应用进程内持有数据库管理员备份凭据，也不自动执行任意 shell command。非空 PostgreSQL 有 pending migration 时，操作者必须先完成独立备份并显式设置一次启动确认；缺少确认使用 `BACKUP_REQUIRED` 阻断。

推荐操作流程：

1. 使用与服务器兼容的 PostgreSQL client 执行 `pg_dump -Fc`。
2. 保存 archive SHA-256、服务器 major、数据库名的安全标识、UTC time和操作人确认；不保存密码。
3. 在隔离目标数据库执行 `pg_restore` 演练，检查 extension、role、owner、tablespace 和 schema。
4. 运行 V3.10 migration dry validation/fixture test。
5. 设置 `PROJECTFLOW_POSTGRES_BACKUP_CONFIRMED=true`，只对本次受控启动生效。

该确认不是备份本身；CI fixture 可以显式提供确认，但必须同时跑真实 Testcontainers dump/restore gate。

### 4.2 Failure recovery

Flyway transaction 能回滚的 migration 失败后，应用仍阻断并保留 diagnostic。不能事务回滚或产生 failed history 时，由管理员根据 Flyway history、数据库日志和已验证 archive决定修复或 restore。应用不得自动 `repair`、drop schema 或清除 failed entry。

PostgreSQL restore 使用全新/隔离目标优先；验证完成后再切换连接。不要把 H2 zip、H2 SQL script、docker volume 或 `export-embedded-data.ps1` 描述为 PostgreSQL disaster recovery。

## 5. Legacy data directory migration

Repo-local `.projectflow/local-data` 到 release data root 的迁移独立于 schema migration：

1. 明确指定单一 legacy source，禁止全盘猜测。
2. backend 停止并释放 H2 lock。
3. 对 legacy H2 创建有效备份。
4. 复制到目标 temp，验证文件 hash和可打开性。
5. 写带 source fingerprint、target identity、UTC time和状态的 marker。
6. 原子切换后启动，随后才运行 controlled schema migration。

目标已有数据、多个 source、source/target 相同、junction/symlink 逃逸、权限不足或空间不足都使用 `LEGACY_DATA_CONFLICT` 阻断。源数据至少保留一个成功启动与备份周期；V3.10 不自动删除。

## 6. Credential 与数据库恢复

数据库备份可能包含尚未迁移的 V3.9 plaintext key，因此备份按敏感数据处理并限制访问。恢复后：

- 如果 row 有 legacy plaintext 且无 secretRef，重新执行两阶段 Secret Store migration。
- 如果 row 有 secretRef 但 OS store 内容在当前用户/机器不可读，保留 metadata，Provider 状态为需要重新配置；不得从数据库恢复 plaintext。
- DPAPI blob 不保证跨用户/跨机器恢复。
- Provider metadata count/identity 应保持，credential availability 与业务数据恢复结果分开报告。

## 7. Binary rollback

二进制 manifest 声明其最小/最大 Flyway schema version。旧 binary 发现数据库 schema 高于其最大兼容版本时返回 `INCOMPATIBLE_SCHEMA_VERSION` 并阻断。允许的恢复方式只有：

- 使用与当前 schema 兼容的新 binary 修复。
- 停机后恢复升级前完整数据库，再运行旧 binary。

不得只替换 jar/前端后继续打开更高版本 schema，也不得用 Hibernate update 逆向修改。

## 8. 失败注入与验收

必须覆盖：

- V3.9 H2 → V3.10 success、restart、rerun。
- V3.9 PostgreSQL → V3.10 success、restart、rerun。
- backup 创建失败时 migration 0 次。
- migration SQL failure 后原数据/backup 可恢复。
- interrupted/failed history 明确阻断。
- H2 backup round-trip restore 与损坏 backup拒绝。
- PostgreSQL dump/restore隔离演练。
- incompatible binary/schema 清晰阻断。
- restore 前 emergency backup失败时不替换当前库。

所有测试只使用合成 fixture，不使用真实用户数据库。若本机 Docker 不可用，PostgreSQL 结果必须标为未运行并由 required GitHub CI补证，不能用 H2 代替。

## 9. 用户可见错误码

- `BACKUP_REQUIRED`
- `BACKUP_FAILED`
- `BACKUP_INVALID`
- `RESTORE_BLOCKED`
- `RESTORE_VERIFICATION_FAILED`
- `UNSUPPORTED_LEGACY_SCHEMA`
- `SCHEMA_MIGRATION_BLOCKED`
- `INCOMPATIBLE_SCHEMA_VERSION`
- `LEGACY_DATA_CONFLICT`

错误响应使用正式中文安全摘要；工程日志可以保留错误码、阶段、schema version 和 correlation ID，但不得保留敏感内容。
