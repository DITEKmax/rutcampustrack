# ACCESS-A2 diff manifest

Base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`  
Branch: `codex/v2-access-scope`  
Worktree: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-access-scope`

The source-only diff contains these assigned files:

- `AGENTS.md` — short pointer to the current orchestration rules and frozen
  ACCESS-A2 packet.
- `proto/academic.proto` — additive resolver RPC/messages/enums with the exact
  frozen tags and reserved field 2.
- `services/academic-service/academic-app/build.gradle.kts` — the one approved
  `proto-google-common-protos:2.29.0` implementation dependency.
- `.../academic/grpc/AcademicGrpcServiceImpl.java` — resolver injection,
  response mapping, and typed error transport.
- `.../academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java` — resolver
  method registration while retaining both homework registrations.
- `.../academic/studentprojection/StudentProjectionException.java` — typed
  resolver failures.
- `.../academic/studentprojection/StudentProjectionGrpcErrors.java` — stable
  gRPC status/detail mapping.
- `.../academic/studentprojection/StudentProjectionQuery.java` — immutable
  read port snapshots, including the durable session snapshot.
- `.../academic/studentprojection/JdbcStudentProjectionQueryAdapter.java` —
  parameterized authority/session/history/assignment/subject/roster reads.
- `.../academic/studentprojection/StudentProjectionScope.java` — immutable
  response model.
- `.../academic/studentprojection/StudentProjectionScopeService.java` —
  signed authority/session identity, history, subject, date, rank resolution,
  and explicit repeatable-read transaction isolation.
- `.../academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java` — update
  existing constructor fixture and add resolver interceptor coverage.
- `.../academic/grpc/StudentProjectionGrpcServiceTest.java` — handler field and
  typed-detail coverage.
- `.../academic/studentprojection/StudentProjectionScopeServiceTest.java` —
  deterministic service cases, including session fail-closed checks before
  dependent reads.
- `.../academic/studentprojection/StudentProjectionQueryAdapterIT.java` —
  PostgreSQL16/Flyway multiple-group/session adapter cases and a
  transaction-proxied concurrent authority/history snapshot case.
- `.../academic/grpc/StudentProjectionGrpcServiceTest.java` — transaction/data
  access boundary mapping and unknown programmer failure assertions.

No migration, generated source, lockfile, Schedule/map/UI/BFF, writer, or
original/E checkout file is part of this worktree diff.  The local
`.agent/access-a2/` packet/evidence/check files are source evidence for this
branch and do not alter shared orchestration state.
