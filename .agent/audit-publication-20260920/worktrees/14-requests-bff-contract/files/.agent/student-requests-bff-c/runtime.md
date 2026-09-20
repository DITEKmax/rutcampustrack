# Runtime evidence

Дата: 2026-09-08. Для этой bounded S2 дельты применим focused unit/serialization
runtime: Gradle собрал BFF и выполнил два указанных JUnit-класса. Product runtime
через HTTP, gRPC, Testcontainers, Mongo, Redis или внешний сервис не требовался.

Единственный разрешённый запуск:

```text
.\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --no-parallel --max-workers=1 --console=plain
```

- worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`
- start: `2026-09-08T11:11:54.2693754+03:00`
- end: `2026-09-08T11:13:21.9903189+03:00`
- exit code: `0`
- result: `BUILD SUCCESSFUL`; `39 actionable tasks: 39 executed`
- `MobileAttendanceClientErrorTest`: 17 tests, 0 failures, 0 errors, 0 skipped
- `StudentRequestDetailJsonTest`: 2 tests, 0 failures, 0 errors, 0 skipped

JUnit XML evidence:

- `services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest.xml`
  SHA-256 `87098D308BE390CC907C71D4C3A8B824BE28F4D1B32319000B2A449AA7AA776F`
- `services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest.xml`
  SHA-256 `292DB928565C03F02777B0D934C6565475C9CD00728C7C587D3BC2EE16C33D54`

No process or container was left running by this lane. Full HTTP/Nginx/Redis/Mongo
runtime remains outside this focused contract check and is not claimed here.
