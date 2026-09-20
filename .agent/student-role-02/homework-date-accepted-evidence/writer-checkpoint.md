# Homework date delta — pause checkpoint

Pause requested by root for 20 minutes on 2026-09-07. No commit, reset,
delete, merge, integration, or new check is performed after this checkpoint.

## Frozen state

- Worktree: `codex/student-role-02-homework-api`
- Baseline/HEAD: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- Scope: fresh `completedAt` compact contract for student homework feed across
  Academic proto/domain/gRPC, Mobile BFF Java/OpenAPI/client and generated
  mobile-core types; no UI/config/lockfile/dependency work.
- Writer: this worktree only. Existing parent work and the separate requests
  worktree are preserved.
- Runtime owner: Testcontainers-created PostgreSQL containers from Gradle IT;
  the last Gradle process ended normally and containers were reaped by the
  test JVM. No product process or external deployment is running.

## Finished checks and evidence

All commands ran in Windows PowerShell, Java 21.0.10, Gradle 8.12,
Docker Desktop/Testcontainers, with `--no-daemon`:

| Check | Session | Result |
|---|---:|---|
| Initial full Academic `integrationTest` | 65481 | exit 1, 152 tests/2 failures: PostgreSQL timestamp precision fixture and expected Academic snapshot line-ending drift |
| `compileTestJava` after fixture correction | 18184 | exit 0 |
| Java-first Academic OpenAPI snapshot update using `-Popenapi.snapshot.update=true` | 58053 | exit 0, one snapshot test |
| Academic OpenAPI no-update recheck | 17971 | exit 0, one snapshot test |
| `HomeworkStudentCompletionConcurrencyIT` after MICROS fixture correction | 96684 | exit 0, 6/6 |
| Full Academic `integrationTest` after corrections | 32843 | exit 0, 152/152; build 4m07s |

The precision defect was test-only: the inserted fixture timestamp is now
truncated to PostgreSQL `TIMESTAMPTZ` microseconds before insert and compared
at that precision. No production code or schema changed for it.

Academic OpenAPI has no semantic/path/schema delta from this gRPC/domain
change: `git hash-object --path=docs/openapi/academic.json` remains the same
as `HEAD` (`ee4580139e3b4f358b2b7ef9b6594862e574932f`), and normalized
`git diff` is empty. The runtime snapshot pass only rewrote the working file's
raw line-ending/final-LF representation (`curRaw=41d45f00...`, filtered hash
still equals HEAD). Treat that as generated-environment evidence, not as an
Academic REST contract change; parent should decide whether to discard that
raw-only working-tree residue before integration.

Earlier accepted evidence remains valid: Academic focused unit/auth tests
6/6, BFF query tests 6/6, BFF HTTP/local-gRPC boundary 7/7, Java-first BFF
OpenAPI and generated mobile-core/typecheck/lint/fixture checks exit 0. The
separate `homework-db-verification` packet records its 3/3 PostgreSQL
concurrency, 5/5 HTTP→gRPC and 4/4 BFF snapshot results.

## WARN/ERROR evidence

- Testcontainers/Test PostgreSQL logs include Flyway's pre-existing
  `transaction in progress` warning, Hibernate dialect warning, Mockito's
  dynamic-agent warning and the test profile's auth public-key connection
  error to `localhost:9999`.
- BFF runtime logs include the unrelated optional Mongo localhost warning.
- These warnings/errors were not linked to the date-delta request and did not
  change code. The tests completed their assertions and full Academic suite
  exited 0.

## Exact source manifest at pause

Tracked modified paths:

```text
docs/openapi/academic.json                 # raw line-ending-only residue; normalized diff empty
docs/openapi/mobile-bff.json
frontends/mobile-core/src/api/generated/mobile-bff.ts
frontends/mobile-core/src/api/student-client.ts
frontends/mobile-core/src/api/types.ts
frontends/mobile-core/src/test-adapter/fixture-transport.test.ts
proto/academic.proto
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/HomeworkCompletionRepository.java
services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentApi.java
services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAcademicClient.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentApiController.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java
```

Untracked feature/test paths:

```text
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentity.java
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkJsonConfiguration.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkNoStoreFilter.java
services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java
services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java
```

`.agent/student-role-02/` contains earlier packets plus this checkpoint; it
is evidence only and is not a product file.

## Pending after pause

1. Resume only after the requested pause; do not rerun tests merely because
   the checkpoint exists.
2. Write the final date-delta `checks.json`, `evidence.md`, `summary.md` and
   source/diff manifest under `.agent/student-role-02/homework-date-delta/`,
   carrying the exact exits above and the runtime limitations.
3. Decide with root how to handle the Academic OpenAPI raw-only line-ending
   residue; do not describe it as a schema/path change.
4. Capture final scoped diff/hash and bootJar/generated-contract evidence,
   then hand the stable worktree to the independent Sol review. Dependency
   scanner findings and requests-domain work remain outside this writer's
   scope.

No Terra escalation is open: the only reproduced defect was a test fixture
precision mismatch and it is corrected with targeted evidence.
