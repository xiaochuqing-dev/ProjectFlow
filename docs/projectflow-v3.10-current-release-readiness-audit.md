ProjectFlow V3.10 当前 Release Readiness 审计

审计基线

审计时间：2026-08-25。
审计工作树：codex/v3.10-release-readiness-closure，HEAD c65d6d1；其父提交及 origin/master 为 dd5ee41。c65d6d1 仅启动 V3.10 readiness 分支，以下事实来自该提交可见的 V3.9 代码、测试、CI 和文档。
审计范围：Persistence/schema ownership、H2/PostgreSQL、Provider credential/custom header/JWT、bind/CORS、data/log/config 目录、launcher/dev-tool 依赖、packaging/update/rollback/recovery、dependency/Actions、clean install、legacy upgrade、backup/crash recovery。
本次只读盘点未运行 Maven、npm、Docker、Playwright 或真实 Provider，也未执行 GitHub 写操作。因此文中“通过”只表示仓库已有可核对证据，不表示本机本次复跑。
分类口径：already good 表示 V3.9 已有边界或可重复的开发态证据；needs extension 表示已有局部实现但还不能作为 release 合同；P0 表示 V3.10 release gate 当前不能宣称通过；P1 表示不阻断当前核心开发态但必须在 release hardening 中补齐；deferred V4 表示明确不属于 V3.10 的最终桌面产品范围。

总判断

V3.9 的 Continuity、History、H2/当前 PostgreSQL Testcontainers、前端、Playwright、Hermes、Obsidian 和敏感内容门禁已有代码及 CI 入口。V3.10 目前不能判定为 release-ready：schema 仍由 Hibernate ddl-auto:update 拥有，Provider key 和 custom safe header value 仍是数据库明文，auth/bind 组合没有 release-safe 默认值，启动仍依赖 Maven/npm/source tree，未发现可交付 release candidate、升级前备份恢复和 Windows release gate。

V3.9 文档自身已把版本化迁移、OS Secret Store、发布运行包、升级恢复和依赖安全列为 V3.10 范围；这不是把 V3.9 的“无 Tag/Release”误判成回归。见 docs/projectflow-v3.9-final-closure-report.md:41,58,60。

Already good

1. 事实与派生层边界已有说明。Current Project State 是 project_history_snapshots、corrections 和 coverage 的 DTO/read projection，不增加 Current State 表；ProjectFact 仍是事实源，snapshot 可替换。见 docs/data-model.md:11,39-49,75,109。

2. H2 与 PostgreSQL 的当前运行路径都存在。默认配置使用 PostgreSQL，embedded profile 使用文件型 H2，二者均明确为 ddl-auto:update。见 backend/src/main/resources/application.yml:13-20 和 backend/src/main/resources/application-embedded.yml:6-15。PostgreSQL Testcontainers 固定 postgres:16-alpine，并在测试中注入容器 JDBC 参数；当前测试覆盖基础持久化、checkpoint、dirty generation 和 correction 并发。见 backend/src/test/java/com/projectflow/ProjectFlowPostgresIT.java:97-120,368-479。

3. 旧数据兼容有真实测试边界。H2 升级测试会删除旧列、以 ddl-auto:update 重启当前应用，检查旧 snapshot、Provider、batch、segment、sediment、fact、capability、job 和 retry/cancel 关系不丢；另有 nullable reliability 字段兼容测试。见 backend/src/test/java/com/projectflow/ProjectFlowH2UpgradeIntegrationTest.java:52-145,151-239 和 backend/src/test/java/com/projectflow/ProjectAnalysisJobCompatibilityTest.java:22-55。该证据是 V3.9/Hibernate 兼容，不等同于 V3.10 versioned migration。

4. 持久化 Job 的服务重启语义已有代码。ApplicationReadyEvent 会重新处理 active jobs：QUEUED 重新入队，模型请求状态未知的任务标为不可自动重发，未发生模型调用的任务标为可重试；不会为未知请求盲目重发。见 backend/src/main/java/com/projectflow/service/ProjectAnalysisJobService.java:291-320。该边界属于应用任务恢复，不覆盖数据库备份、进程崩溃后的数据恢复或 schema 回滚。

5. Provider 出站 URL 与响应暴露已有安全收敛。URL 仅允许 HTTPS，HTTP 仅允许本机开发主机；阻止私网、loopback、metadata IP、URL credentials、query/fragment 等。见 backend/src/main/java/com/projectflow/service/AiProviderUrlGuard.java:14-69。Provider API response 只返回 safe header 名称和 apiKeyConfigured 布尔值，不返回 API key。见 backend/src/main/java/com/projectflow/service/AiProviderService.java:266-294。

6. 自定义 header 有名称、保留 header、CR/LF 和值格式校验；模型 adapter 也确实会把 safeHeaders 送入请求，而不是让业务层拼任意命令。见 backend/src/main/java/com/projectflow/service/AiProviderService.java:340-373 和 backend/src/main/java/com/projectflow/service/model/OpenAiSdkSupport.java:12-34。该校验不代表凭据已加密，见下文 P0。

7. V3.9 普通质量门禁已有单一 workflow。quality-gates.yml 包含 H2、PostgreSQL 16、frontend lint/build/contracts、Hermes、Obsidian、Playwright、敏感内容扫描和可选受保护 real-provider workflow。见 .github/workflows/quality-gates.yml:34-214,216-337,340-470。workflow 级 permissions 为 contents: read，属于较小权限边界。

8. 嵌入式开发启动器有相对路径、依赖 hash、frontend build、health wait、端口检查、日志和 last-embedded-build.json 证据；它会检测并按 package-lock hash 触发 npm ci。见 start-projectflow-embedded.ps1:27-42,284-367。旧 H2 lock 和 ProjectFlow 自身端口有处理逻辑，非 ProjectFlow 占用端口会阻断，而不是强杀未知进程。见 start-projectflow-embedded.ps1:146-217,220-249。

Needs extension

1. Persistence/schema ownership。backend/pom.xml 没有 Flyway/Liquibase 依赖或 migration plugin；application.yml 和 application-embedded.yml 都使用 ddl-auto:update。见 backend/pom.xml:114-139、backend/src/main/resources/application.yml:17-20、backend/src/main/resources/application-embedded.yml:12-15。docs/data-model.md:11 明确写明 V3.10 才拥有 formal versioned migrations；docs/migration-compatibility.md:9,31 也明确说明当前不是 Flyway 版本化系统。

2. H2/PostgreSQL 证据仍是当前 schema 的兼容/集成证据。PostgreSQL 测试本身以 ddl-auto:update 启动，未证明 empty DB、已知 V3.9 DB、unknown/partial schema、失败 migration 或 V3.10 rerun。V3.9 closure report 记录本机 Docker Engine 未运行，新增 PostgreSQL 16 并发 gate 依赖远端 CI；见 docs/projectflow-v3.9-final-closure-report.md:29-37。V3.10 还缺 V3.9 PostgreSQL legacy fixture 和升级前后数据计数证明。

3. Provider credential 明文边界。AiProvider.api_key 是 text column，authHeaderName、queryKeyName、safeHeaders 也持久化；见 backend/src/main/java/com/projectflow/entity/AiProvider.java:38-67。StringMapConverter 只是把 Map 序列化为 JSON text，没有加密或 OS secret reference；见 backend/src/main/java/com/projectflow/support/StringMapConverter.java:13-34。safeHeaders 的名称可被限制，但其值仍可承载 credential，不能因字段名 safe 而视为安全。

4. JWT/auth runtime 默认值。application.yml 的 PROJECTFLOW_AUTH_REQUIRED 默认 false，JWT secret 使用 placeholder；见 backend/src/main/resources/application.yml:29-40。placeholder 会触发 JwtService 生成仅存于内存的随机 key，重启后 session 失效，并明确记录为不安全生产；见 backend/src/main/java/com/projectflow/security/JwtService.java:57-69。auth-off 时 AuthService 会自动创建或使用第一个本地用户；见 backend/src/main/java/com/projectflow/service/AuthService.java:47-63,113-123。auth-required 时启动日志直接输出 password reset code；见同文件:61-63。未发现 production profile 对这些默认值做硬阻断。

5. Bind/CORS/network exposure。application.yml 没有 server.address；embedded launcher 只给 Next 使用 127.0.0.1，并没有给 Spring Boot 设置 backend bind。见 backend/src/main/resources/application.yml:1-2、start-projectflow-embedded.ps1:331-335。CORS 只挂 /api/**，origin 来自环境变量，allowedHeaders 为 *，allowCredentials 为 false；见 backend/src/main/java/com/projectflow/config/WebConfig.java:14-27。docker-compose.yml 直接发布 PostgreSQL 5432 和 Redis 6379，默认密码仍为 change-me-local；见 docker-compose.yml:1-24。当前不能通过“auth-off 仍不会暴露 LAN”的 release gate。

6. Data/log/config 目录。embedded 模式把 H2 与 storage data 放在 source repo/.projectflow/local-data，把日志放在 source repo/logs；见 start-projectflow-embedded.ps1:27-32 和 backend/src/main/resources/application-embedded.yml:23-26。日志会覆盖 backend/frontend 当前日志，未见 rotation 或 retention contract。export-embedded-data.ps1 只在运行目录外创建目录并递归 Copy-Item，未见 quiesce、schema/version manifest、完整性校验或 restore 操作；见 export-embedded-data.ps1:7-24。开发态目录可用，但不能直接作为只读 install dir 与持久用户数据分离的 release contract。

7. Launcher/dev-tool dependency。Start-ProjectFlow.bat 进入 embedded PowerShell；embedded 启动会解析 mvn.cmd 和 npm.cmd，必要时执行 npm ci，运行 npm run build，再用 mvn spring-boot:run；见 Start-ProjectFlow.bat:1-6 和 start-projectflow-embedded.ps1:282-342。Docker/team launcher 还依赖 docker.exe、Maven、npm，并在 start 时运行 docker compose 和 frontend build；见 start-projectflow.ps1:8,203-241。该脚本仍显示 ProjectFlow V3.3.8，说明它是开发/团队入口而不是 versioned release runtime。

8. Packaging/update/rollback。仓库 inventory 未发现 Dockerfile、installer、release candidate、runtime bundle、update/rollback script 或 application production profile。README 只描述 clone 后的 embedded launcher，并明确无 Tag/Release；见 README.md:389-395,441-444 和 docs/projectflow-v3.9-final-closure-report.md:58。当前没有 source-independent 启动、version manifest、checksums、bundled runtime、data-preserving update 或 binary/schema rollback 路径。

9. Dependency/Actions。frontend 有 package-lock.json 且 CI 使用 npm ci，但 package.json 仍使用多个 caret ranges；见 frontend/package.json:5-29、frontend/package-lock.json:2-8。当前唯一 Actions workflow 使用 checkout/setup-java/setup-node/setup-python/upload-artifact 的 major tags，未见 SHA pin、Windows release job、npm audit、Maven CVE inventory、SBOM 或 license/maintenance gate；.github inventory 仅发现 quality-gates.yml。V3.9 closure report 历史上记录过 frontend 4 个 high 和 Actions Node 20 退役告警，并将其放入 V3.10；见 docs/projectflow-v3.9-final-closure-report.md:41,60。该旧数字不能当作当前扫描结果，V3.10 必须重新扫描。

10. Clean install、existing-user upgrade、backup、crash recovery。CI 能从 checkout 运行 Maven/H2、PostgreSQL、npm ci、build 和 Playwright，但没有 release artifact 解包后离开 source tree 的 clean-install 证据。H2 legacy upgrade 和应用 Job restart recovery 已有局部证据；V3.10 迁移、H2 pre-upgrade backup/restore、PostgreSQL external backup preflight、abrupt kill 后数据完整性、failed migration recovery、binary downgrade incompatibility 和 repeated release start 尚无对应 release gate。当前 exporter 不是备份/恢复合同。

P0

P0-1 Schema migration ownership 未完成。只要 release profile 仍依赖 ddl-auto:update，就无法审计空库建表、已知 V3.9 signature、unknown schema block、失败恢复和 rerun idempotency。V3.9 H2 compatibility test 不能替代 V3.10 versioned migration；见 docs/data-model.md:11、backend/src/main/resources/application.yml:17-20、backend/pom.xml:114-139。

P0-2 Provider secret 与 runtime default 未达到 release security boundary。数据库仍保存 plaintext api_key 和可含 credential 的 safe_headers；auth 默认关闭；JWT placeholder 会生成临时 key；reset code 写日志；docker 默认密码和公开端口存在。V3.10 在 OS-backed/CI test-double secret path、迁移失败保护、plaintext=0、placeholder reject、loopback-only 和 no-secret-log 证据出现前不能判 PASS。相关事实见 AiProvider.java:38-67、StringMapConverter.java:18-34、application.yml:29-40、JwtService.java:57-69、AuthService.java:61-63、docker-compose.yml:5-24。

P0-3 没有可交付 release runtime。当前用户必须有 source tree，并依赖 Maven/npm；没有 versioned release candidate、checksums、runtime manifest、install-dir/read-only 证明或 source-independent startup。V3.10 release-ready 不能用开发 launcher 的“启动成功”替代；见 start-projectflow-embedded.ps1:284-342 和 README.md:389-395。

P0-4 Backup/upgrade/restore/recovery gate 缺失。没有 pre-upgrade safe backup、有效性校验、保留策略、restore procedure、PostgreSQL admin recovery contract、failed migration recovery 或 downgrade/schema incompatibility block 的可执行证据。export-embedded-data.ps1 的递归复制不能证明这些语义。

P0-5 Network boundary 与 dependency gate 当前均未闭合。没有显式 Spring loopback bind，auth-off 与 backend default bind 的组合未被 release test 锁定；dependency/CVE 当前也未有新的真实扫描结果。若 release target 是 local/desktop，前者直接阻断；若依赖扫描仍有未接受 High/Critical，后者按 V3.10 policy 直接阻断。这里不把旧的“4 high”数字冒充当前结果。

P1

P1-1 建立 runtime directory contract，分开 binaries、data、DB、backups、logs、cache、config、temp；保留 repo-local developer mode 但不让它冒充 release mode。需要日志轮转、有限保留、权限、secret redaction、temp 清理和 legacy .projectflow/local-data detect/copy/verify/conflict/rollback。

P1-2 完善 launcher 的 clean shutdown、orphan process、port conflict、stale lock、data-dir permission、backup failure 和 repeated start smoke；team launcher 的版本显示和 profile 也应与 3.9/3.10 事实一致。

P1-3 为 Actions 使用受支持且可审计的版本策略，新增 dependency/CVE、SBOM、license/maintenance、artifact scan 和 Windows required/release job；保留 ordinary CI 与 protected real-provider workflow 的权限隔离。

P1-4 形成 existing-user upgrade/runbook：empty H2、empty PostgreSQL、V3.9 H2、V3.9 PostgreSQL、unknown/partial schema、interrupted migration、rerun、backup/restore、data-count/hash comparison。至少覆盖 Project、ProjectFact、HistoryEvent ledger、Snapshot、Correction、Agent candidate、Provider metadata、revision/current-state/context package。

P1-5 明确 external PostgreSQL 只做 preflight、transaction/fail-safe 与管理员备份要求，不把 docker-compose named volume 或应用 exporter 描述为企业级备份；明确 update 不覆盖 data，失败时 core read 与 credential 状态的继续/阻断语义。

Deferred V4

V3.9/V3.10 边界明确不做 Electron/Tauri 最终 shell、tray、watcher、daemon、开机启动、updater GUI、品牌安装向导和最终视觉 GUI。见 PROJECT_CONTEXT.md:35,286-299 和 docs/architecture.md:91。V3.10 仍需 portable/deterministic runtime artifact；“最终桌面外壳”不能用来掩盖当前 P0。

最小可重复验证矩阵（本次未执行）

1. H2 当前回归：在 backend 运行 mvn -B test；工作流入口见 .github/workflows/quality-gates.yml:49-100。
2. PostgreSQL 当前回归：Docker 可用时在 backend 运行 mvn -B -Ppostgres-it verify；入口见 .github/workflows/quality-gates.yml:110-129。
3. 前端当前回归：在 frontend 运行 npm ci，再运行 npm run lint、npm run build、npm run test:contracts；入口见 .github/workflows/quality-gates.yml:131-147。
4. 浏览器回归：在 frontend 安装 Chromium 依赖后运行 npm run test:e2e；入口见 .github/workflows/quality-gates.yml:169-198。
5. 开发启动器检查：运行 Start-ProjectFlow.bat -CheckOnly；它只能证明 Maven/npm、相对路径、版本和 frontend dependency marker 检查，不能证明 release package。
6. V3.10 尚缺的阻断命令不是现有脚本：versioned migration、empty/legacy/unknown schema fixture、secret-store migration、backup/restore、source-independent package、Windows release smoke、CVE/SBOM/Actions audit。没有这些入口前，不能把 V3.10 记为 PASS。

审计自检

本文件是本工作树唯一修改文件。审计未写入源码、配置、测试、CI、数据库、真实 Provider 或 GitHub。报告没有保存 API key、Authorization、prompt、raw response、reasoning 或绝对机器路径。
