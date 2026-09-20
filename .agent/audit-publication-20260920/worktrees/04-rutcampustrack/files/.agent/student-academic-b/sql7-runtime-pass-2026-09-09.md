# SQL7 successful runtime evidence - 2026-09-09

- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Environment: Windows local worktree, Java 21.0.10, PostgreSQL 16 Testcontainers, Docker Server 28.5.2.
- Pre-run exact-seven source guard: MATCH.
- Heavy lease: exclusive B during both commands; RELEASED after completion.
- No rerun is required.

## Academic

- Invocation recorded: `2026-09-09T15:27:40Z`.
- Completion recorded: `2026-09-09T15:31:11Z`.
- Exact command: `.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue`.
- Exit code: `0`.
- Gradle: `BUILD SUCCESSFUL in 2m 11s`; 37 actionable tasks, 2 executed.
- Fresh XML: `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.xml`.
- XML result: 9 tests, 0 failures, 0 errors, 0 skipped.
- XML SHA256: `CF086E070724CC9E076EEFF33B88F0B7D410C23E8EF5557525FB9E3F03E20ECF`.

## Schedule

- Invocation recorded: `2026-09-09T15:32:02Z`.
- Completion recorded: `2026-09-09T15:33:40Z`.
- Exact command: `.\gradlew.bat :services:schedule-service:schedule-app:integrationTest --tests ru.rutcampustrack.schedule.migration.StudentOccurrenceMigrationIT --tests ru.rutcampustrack.schedule.migration.FlywayMigrationIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue`.
- Exit code: `0`.
- Gradle: `BUILD SUCCESSFUL in 1m 15s`; 37 actionable tasks, 2 executed.
- Fresh XML `StudentOccurrenceMigrationIT`: 5 tests, 0 failures, 0 errors, 0 skipped; SHA256 `B9E1AEC9B46C066EAC428C29080FA412611312BA037C51AF00F9B837ED6AEA01`.
- Fresh XML `FlywayMigrationIT`: 3 tests, 0 failures, 0 errors, 0 skipped; SHA256 `100D7F0D54B9DB1A59B45C54DC0F1F106A75B80BC7F41514E370B487084B16AF`.

## Result

Total: 17/17 tests passed. The exact-three fixture correction received fresh Sol PASS. A separate V24 content review also passed with no findings. Whole B0 remains open for proto/standalone Java compilation and final review.
