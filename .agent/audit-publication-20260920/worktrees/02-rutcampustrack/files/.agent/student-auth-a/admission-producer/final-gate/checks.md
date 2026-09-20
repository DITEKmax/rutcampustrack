# Stage 1 coherent verification gate

Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Environment: Windows PowerShell, Java 21, Gradle 8.12; escalation was limited to reading the existing Gradle cache and to Docker for the PostgreSQL integration test.

| Scope | Command | Exit | Result |
|---|---|---:|---|
| Effective source guard | SHA-256 comparison of 15 present files plus absence of 2 deleted old-issuer files and Auth13 manifest hash | 0 | `bad=0`; Auth13 `5DDABF...D18B` |
| Shared JWT consumer | `.\gradlew.bat :services:shared:shared-security:test --tests ru.rutcampustrack.shared.security.InternalJwtValidatorTest --tests ru.rutcampustrack.shared.security.DualModeUserContextFilterTest --no-daemon --no-parallel --max-workers=1 --console=plain` | 0 | `BUILD SUCCESSFUL in 43s`; 22/22 |
| Auth producer/admission unit | `.\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.service.JwtTokenPurposeTest --tests ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest --tests ru.rutcampustrack.auth.session.SessionAdmissionServiceTest --tests ru.rutcampustrack.auth.arch.AuthApiContractTest --no-daemon --no-parallel --max-workers=1 --console=plain` | 0 | `BUILD SUCCESSFUL in 51s`; 36/36 |
| Real PostgreSQL admission | `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.InternalSessionAdmissionIT --no-daemon --no-parallel --max-workers=1 --console=plain` | 0 | `BUILD SUCCESSFUL in 1m 13s`; 6/6; PostgreSQL 16.13; Flyway V1..V24 |
| Scoped whitespace | `git diff --check -- <15 present Stage 1 paths>` | 0 | no whitespace errors; LF/CRLF advisory only |
| Runtime cleanup | `docker ps --format ...` after the integration test | 0 | no running containers |

Seven copied JUnit XML files are byte-identical to the corresponding successful Gradle results. Aggregate: 64 tests, 0 failures, 0 errors, 0 skipped. Product runtime outside the focused HTTP/DB integration test is not part of this Stage 1 slice.
