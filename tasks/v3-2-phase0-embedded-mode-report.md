# ProjectFlow V3.2 Phase 0 Execution Report

Date: 2026-06-19
Scope: PRD Phase 0, embedded personal mode and one-command Windows startup.

## Implemented

- Added `backend/src/main/resources/application-embedded.yml`.
- Embedded profile uses H2 file storage at `PROJECTFLOW_DATA_DIR` or `./.projectflow/local-data`.
- Embedded profile excludes Redis auto-configuration and uses Spring's built-in `simple` in-memory cache.
- Changed H2 from test-only to runtime dependency so embedded mode can start outside tests.
- Added `start-projectflow-embedded.ps1` and `start-projectflow-embedded.bat`.
- Changed `start-projectflow.bat` to default to embedded mode.
- Preserved the Docker/PostgreSQL/Redis path via `start-projectflow.ps1` and new `start-projectflow-docker.bat`.
- Added `export-embedded-data.ps1` to copy embedded data files into `artifacts/embedded-export/<timestamp>`.
- Updated `README.md` with embedded startup, data location, export path, and Docker fallback.

## Practical Decisions

- Did not force a pure single JAR in this slice. The current Next.js app is still served by Next production server, so Phase 0 uses a single Windows launcher that starts backend and frontend together.
- Used Spring's built-in memory cache instead of Caffeine. The PRD allows either Caffeine or Spring Cache built-in implementation, and avoiding a new dependency keeps this step low-risk in the current local environment.
- Did not add any user-home Agent log scanning. That belongs to later phases and requires explicit authorization.

## Verification

- Red test first: `mvn.cmd -q -Dtest=EmbeddedProfileConfigurationTest test` failed before implementation because `embedded` still used default PostgreSQL settings and no cache type.
- Green test after implementation: `mvn.cmd -q -Dtest=EmbeddedProfileConfigurationTest test` passed.
- Script check: `.\start-projectflow-embedded.ps1 -CheckOnly` passed and resolved Maven, npm, and the embedded data directory.
- PowerShell parser checks passed for `start-projectflow-embedded.ps1`, `export-embedded-data.ps1`, and the original `start-projectflow.ps1`.
- Embedded runtime check passed without Docker: backend `http://127.0.0.1:8080/api/health` returned ready and frontend `http://127.0.0.1:3000/login` returned ready.
- Embedded export check passed: `.\export-embedded-data.ps1 -OutputDir <temp>` copied `projectflow.mv.db`.
- Full backend test suite passed: `mvn.cmd -q test`.
- Frontend production build passed: `npm.cmd run build`.

## Remaining Phase 0 Work

- Measure cold startup after dependencies and frontend build are already present; the backend test context started in about 6 to 8 seconds, but full user startup still needs timing.
- Decide whether to move from single launcher to static export hosted by Spring Boot. That needs a separate check of current Next.js route behavior.
- Add a SQL-level export/migration path if PostgreSQL migration becomes a hard requirement. The current export script provides a file-level backup path.
