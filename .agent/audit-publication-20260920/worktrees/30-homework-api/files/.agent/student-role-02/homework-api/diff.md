# Student homework API diff manifest

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`  
Branch: `codex/student-role-02-homework-api`  
Revision: uncommitted working tree on the baseline.

## Feature files

- `proto/academic.proto` — student completion request/response RPC.
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java` — active STUDENT/group/semester guards and desired-state persistence.
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentity.java` and `StudentHomeworkGrpcIdentityInterceptor.java` — signed internal JWT identity boundary for the mutation RPC.
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` — RPC mapping and status translation.
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/HomeworkCompletionRepository.java` — atomic insert-on-conflict and idempotent delete.
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java` and `.../grpc/StudentHomeworkGrpcIdentityInterceptorTest.java` — focused domain/auth tests.
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentApi.java` and `.../contract/model/StudentApiModels.java` — Java HTTP contract, DTOs, responses and ProblemCode.
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAcademicClient.java`, `.../student/StudentApiController.java` and `.../student/StudentQueryService.java` — BFF client, range resolution, mapping and no-store endpoints.
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java` — required 404 branch for the new exhaustive ProblemCode enum.
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java` — focused default/clamp/order/range tests.

## Contract-generated files

- `docs/openapi/mobile-bff.json` — exported by Java-first `OpenApiSnapshotIT`.
- `frontends/mobile-core/src/api/generated/mobile-bff.ts` — regenerated from the canonical snapshot.
- `frontends/mobile-core/src/api/student-client.ts` and `src/api/types.ts` — typed GET/PUT methods and schema aliases.

## Scoped correction

- `frontends/mobile-core/src/test-adapter/fixture-transport.test.ts` — root-authorized removal of the baseline `no-this-alias` lint finding while retaining the mock receiver assertion.

No lockfiles, configs, migrations, committed generated protobuf files, UI
features, unrelated OpenAPI services or main checkout files were changed.
Exact checks and exit codes are in `checks.json`; runtime limits are recorded
there and in `evidence.md`.
