# Homework API repair diff manifest

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Revision is an
uncommitted working tree; no commit or integration was performed.

Repair-owned production changes:

- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java` — safe positive Long parse and Homework-specific semester preflight.
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAcademicClient.java` — scoped active-semester `NOT_FOUND` → dependency-unavailable mapping.
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkJsonConfiguration.java` — type-scoped strict Boolean deserializer.
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkNoStoreFilter.java` — bounded GET/PUT Homework no-store policy applied before auth.
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentApi.java` — Java-first no-store declarations on Homework error responses.

Repair-owned checks and generated contract artifacts:

- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` — focused defect matrix and RPC absence assertions.
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java` — method-specific active-semester mock.
- `docs/openapi/mobile-bff.json` — exporter output after Java header contract change.
- `frontends/mobile-core/src/api/generated/mobile-bff.ts` — regenerated from canonical snapshot.
- `.agent/student-role-02/homework-repair/*` — packet, reproduction, evidence, checks and this manifest.

The worktree also contains the prior author's frozen Homework feature and
other role files (Academic/proto, existing BFF models/controller/client,
mobile-core API surface and prior `.agent` evidence). Those files were read and
preserved; this leaf did not redesign or revert them. The ordinary diff includes
that pre-existing dirty feature because no commit/integration boundary was
available in the shared worktree.
