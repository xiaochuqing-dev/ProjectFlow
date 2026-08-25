# ProjectFlow V3.10 Windows portable runtime

This boundary packages the existing Spring Boot executable jar and Next standalone output. It does not add a desktop shell, installer, updater, service, watcher or daemon.

## Build

From the repository root, run:

```powershell
./scripts/release/build-windows-portable.ps1 -OutputRoot ./artifacts/projectflow-portable -Clean
```

The build may use Maven, npm, Git, jlink and the build machine JDK/Node. The output contains `backend/projectflow.jar`, `frontend/server.js` with `.next/static` and `public`, a jlink Java 17 image, `runtime/node/node.exe`, the locked H2 runtime jar, release scripts, `manifest.json` and `checksums.sha256.json`. The zip and its sidecar SHA-256 are emitted beside the package directory.

The runtime manifest records a full source SHA, build time, resolved Spring Boot/Next/Java/Node/Flyway versions, data-contract version and payload hashes. The packager fails closed if build provenance cannot be resolved; it never fabricates migration evidence.

## Runtime

```powershell
./scripts/release/start-projectflow.ps1 -NoBrowser
./scripts/release/stop-projectflow.ps1
./scripts/release/restore-projectflow.ps1 -BackupManifest <validated-manifest>
```

Installed mode defaults to `%LOCALAPPDATA%\ProjectFlow`. `PROJECTFLOW_DATA_DIR` or `-DataRoot` has explicit priority. `-Portable` is opt-in and uses a separate sibling data root unless `-PortableRoot` is supplied. The launcher rejects the install root, source root, filesystem root and reparse-point escape. Runtime processes use the bundled Java and Node directly; no Maven, npm, Git, source tree, package install or build is invoked.

The launcher verifies manifest/checksums before creating `data/database`, `data/storage`, `backups`, `logs`, `cache`, `config`, `temp` and `run`. It performs an atomic-write and free-space preflight, starts the backend with `embedded,release`, enables the release Flyway gate, and injects the external backup/config/log/temp/cache directories. It binds the local release to loopback, writes state only to the external data root, and records PID, process start time, command hash and bundled artifact marker. Stop terminates only a process whose recorded identity still matches; unknown listeners produce `PORT_CONFLICT` and are not killed.

Before an already-managed embedded H2 database is opened, the launcher creates an H2 payload backup from the bounded, trusted schema-state marker; `backup-projectflow.ps1` refuses to create a usable manifest when the identity is present but missing or unknown. A markerless V3.9 database takes the release Flyway path, which performs exact schema inspection and creates its migration-owned backup before baseline/upgrade, so the launcher does not reject the legacy upgrade before Flyway runs. Restore first creates an emergency backup, validates the selected payload/hash and H2-openability in isolation, moves the current database into a retained recovery directory, re-opens the installed files before success, and rolls back on failure. It never deletes the last current or backup copy.

`dpapi-smoke.ps1` is a supplementary Windows-only smoke for a current-user DPAPI round-trip using a non-provider test value. The backend release path now uses the current-user `WindowsDpapiProviderCredentialStore`; CI selects the explicit in-memory adapter instead. The Java adapter tests cover round-trip, missing-reference and cleanup behavior. DPAPI remains bound to the Windows user profile and is not a cross-user portable backup format.

## Verification

```powershell
./scripts/release/test-release-scripts.ps1
./scripts/release/manifest-projectflow.ps1 -Root <package> -Verify
./scripts/release/dpapi-smoke.ps1 -DataRoot <isolated-data-root>
```

`.github/workflows/windows-release.yml` performs clean unpack, PATH-without-developer-tools startup, bundled-process identity checks, stop-and-restart, exact health/stop/port checks, full install-tree checksum stability, Java DPAPI and legacy H2 gates, H2 backup/restore with a fail-closed schema-identity assertion, synthetic V3.9 baseline startup with protected-row preservation, foreign-port refusal, PowerShell DPAPI smoke and sensitive marker scanning. It uses pinned current action commits and never supplies a real Provider key.

The release profile now enables the versioned Flyway migration strategy and fail-closed V3.9/current schema classification. Legacy provider keys migrate through write, read-back verification, and transactional `secretRef` persistence with the plaintext column cleared; a failed store or database write preserves the prior state. The portable launcher keeps binaries separate from the external data, backup, log, config and temporary directories, and starts only the bundled Java/Node artifacts. These boundaries are covered by the Java migration, H2 backup/upgrade, credential-store and Windows release-script gates. External PostgreSQL backups remain an explicit operator preflight; the application does not claim to create an enterprise backup. A release claim still requires the corresponding clean Windows workflow run, not only local unit or integration results.
