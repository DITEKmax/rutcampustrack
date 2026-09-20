# Student homework API compact contract

## 1. Goal

Реализовать свежий compact contract для student homework feed и desired-state
completion: domain-backed Academic gRPC + Mobile BFF HTTP API + Java-first
OpenAPI snapshot и generated TypeScript/mobile-core client surface.

## 2. Context / evidence

- Baseline/revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; worktree
  `codex/student-role-02-homework-api`.
- Existing `Homework` and `HomeworkCompletion` entities use `homeworks` and
  `homework_completions` with unique `(homework_id, student_id)`.
- `AcademicGrpcServiceImpl.getHomeworksForWeek` already reads a bounded date
  range, verifies requested student belongs to requested group, and batch loads
  completion flags.
- Existing Mobile BFF validates signed JWT at HTTP, forwards token + gRPC
  secret via `MobileGrpcAuth`, and exposes RFC ProblemDetails/no-store patterns.
- Existing completion REST endpoints are toggle operations (`POST`/`DELETE`),
  conflict/nonexistent-state sensitive, and are not the new API contract.
- Source authority is the frozen task packet from root plus current Java/proto
  originals; no product or Figma decision is changed here.

## 3. Relevant scope

Sole writer in this worktree: `proto/academic.proto`; required Academic
homework domain/gRPC code and focused tests; Mobile BFF student contract models,
`StudentApi`, query/controller/client and focused tests; canonical
`docs/openapi/mobile-bff.json`; `frontends/mobile-core` generated OpenAPI type
and typed `StudentApi` methods/types. `.agent/student-role-02/homework-api/*`
is evidence owned by this task.

## 4. Required behavior

- `GET /api/v1/student/homework?from=YYYY-MM-DD&to=YYYY-MM-DD` accepts optional
  ISO date bounds. Missing bounds resolve using `Europe/Moscow` today and active
  semester end; default `from` clamps today to `[dateFrom,dateTo]` so a clock
  outside the active semester never produces an invalid range. The resolved
  inclusive range must lie inside the active semester; malformed, reversed, or
  explicit out-of-semester ranges return 400.
- Response is
  `{semester:{id,name,dateFrom,dateTo},from,to,serverNow,items:[...]}` where
  ids are strings, `subject` contains string `id`/`name`, `link` is the original
  external link or JSON null, and items sort by lesson date, uncompleted before
  completed, lesson number, then id.
- `PUT /api/v1/student/homework/{id}/completion` accepts
  `{completed:boolean}` and returns `{id,completed}`. Actor and group come from
  the validated STUDENT JWT; clients never provide actor/group. Domain verifies
  the current user is an active STUDENT in its own group, homework group matches,
  homework belongs to the active semester, and desired state is idempotent.
- Completion writes are safe for concurrent repeated requests using the existing
  unique constraint and an atomic insert-on-conflict/delete strategy. No offline
  write protocol is added.
- BFF uses existing 400/401/403/404/503 ProblemDetails and no-store response
  headers; dependency/auth failures fail closed. gRPC student mutation validates
  the signed internal JWT at the method boundary and preserves the shared-secret
  interceptor.

## 5. Constraints

- No child agents, no main checkout edits, no commits before review, no deploy,
  migration, destructive data operation, lockfile/config/UI redesign, or
  compatibility layer.
- Reuse existing entities/table and gRPC read path; do not weaken auth by
  trusting request actor/group fields or spoofable headers.
- Java annotations are the source for the HTTP contract; export canonical
  OpenAPI then regenerate mobile-core types with the existing script.
- Preserve unrelated dirty work in all repositories/worktrees.

## 6. Existing patterns

- `StudentQueryService.requireStudent`, `MobileRequestContext`,
  `MobileGrpcAuth`, `MobileProblemHandler`, and `MobileBffException` define BFF
  auth/error conventions.
- Attendance's `StudentGrpcIdentityInterceptor` and context key are the signed
  JWT gRPC boundary pattern; Academic's global gRPC secret interceptor remains
  active.
- `HomeworkCompletionRepository` already exposes per-student queries; add only
  narrowly scoped atomic repository operations required by desired state.
- OpenAPI snapshot is produced/verified by `OpenApiSnapshotIT`; mobile-core
  `scripts/generate-types.mjs` is the sole generated TS path.

## 7. Acceptance criteria

- Authorized student reads active-semester homework with exact response fields,
  null link preservation, deterministic ordering and Moscow/default range.
- Repeated complete and uncomplete desired-state calls return stable state and
  do not create duplicate completion rows; concurrent repeats do not surface a
  unique-constraint failure.
- Wrong role, missing scope, foreign group, inactive/foreign semester, unknown
  homework, invalid range, invalid JWT, and dependency failure map to the
  specified status class; gRPC sees the authenticated actor only.
- Existing homework behavior compiles and focused tests remain green.
- Canonical OpenAPI contains both operations, schemas, security and standard
  responses; generated mobile-core types match the snapshot and StudentApi is
  typed for both methods.

## 8. Verification

- Mechanical: `git diff --check`; proto/Java compile through focused Gradle
  tasks; mobile-core generation `npm run generate:types` and `npm run typecheck`.
- Unit: Homework desired-state/auth/order tests and BFF query/client/controller
  validation tests.
- Integration/contract: Academic/BFF focused tests where available,
  `OpenApiSnapshotIT` update + canonical recheck, gRPC JWT negative cases.
- Runtime: no product ports or external deployment; isolated testcontainers or
  in-process gRPC only if required. Record revision, command, exit code,
  environment and evidence in `.agent/student-role-02/homework-api/checks.json`.

## 9. Do not

Do not alter existing UI features, core index, unrelated OpenAPI services,
lockfiles/config, old toggle endpoints, database migrations, generated protobuf
files committed by build, auth semantics outside this student mutation, or
Figma. Do not treat WARN/ERROR as a code defect without request linkage and
reproduction. Do not escalate to Terra without the recorded defect/complexity
gate required by root.
