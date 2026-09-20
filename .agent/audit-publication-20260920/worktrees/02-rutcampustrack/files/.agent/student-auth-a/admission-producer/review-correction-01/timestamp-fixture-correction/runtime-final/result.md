# Focused PostgreSQL admission runtime result

- Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` plus the exact effective Stage 1 overlay.
- Pre-run guard: `0` bad across 15 present Stage 1 files and 2 intended deletions; Auth13 manifest SHA-256 `5DDABFE8EB55046DF5BB9F6D0EC0245B7D47E6CE2909D789415DC2D823A5D18B`, 2958 bytes.
- Corrected test: `InternalSessionAdmissionIT.java`, SHA-256 `FB13C6300FB7EDB776135BCE4C3FE7276E8FCB220411C75B277A9EAEB6621389`, 28120 bytes.
- Command: `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.InternalSessionAdmissionIT --no-daemon --no-parallel --max-workers=1 --console=plain`.
- Started UTC: `2026-09-10T21:23:14.1041317Z`.
- Environment: Windows PowerShell, Java 21, approved escalation for existing Gradle cache and Docker; Testcontainers `postgres:16`, PostgreSQL 16.13, ephemeral port 49937; reuse false.
- Database evidence: Flyway validated 24 migrations and applied V1 through V24 successfully.
- Exit code: `0`; `BUILD SUCCESSFUL in 1m 13s`; 24 tasks, 2 executed and 22 up-to-date.
- Tests: 6 passed, 0 failures, 0 errors, 0 skipped.
- Cleanup: Testcontainers PostgreSQL `07456cbb83fa5d0121d514585ff01b53b0425d84f4d86787caabde3a8bfda501` and Ryuk `95d0c0cfb2164ad0ecd4032e0825193c06ea8d9116a19577dcf14c4ce02dbcfc` exited; post-run `docker ps` returned no running containers.
- Heavy runtime: RELEASED after completion.

Byte-identical JUnit evidence:

- `junit/TEST-ru.rutcampustrack.auth.integration.InternalSessionAdmissionIT.xml`: 6/0/0/0, SHA-256 `91C2A48C96007D14C88E3759C9FB3ACAA1DA5D53CAB4BD52703D336D9EDAE629`, 11605 bytes.
