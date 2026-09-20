# Student homework DB/API verification compact contract

## 1. Goal

Дать parent/root проверяемое runtime evidence для свежего student homework
compact contract: выполнить desired-state completion через реальный PostgreSQL
и проверить новый HTTP→gRPC BFF boundary отдельными integration tests.

## 2. Context / evidence

- Frozen baseline/revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`;
  branch `codex/student-role-02-homework-api`.
- Existing API lane is uncommitted in the assigned worktree and already has
  `HomeworkStudentService`, native `INSERT ... ON CONFLICT DO NOTHING`,
  idempotent delete, signed student gRPC identity and BFF StudentApi.
- Existing unit checks pass, but the author packet records no real PostgreSQL
  concurrency evidence and no new-endpoint HTTP/gRPC integration evidence.
- Parent explicitly extended this verification scope to the two test classes
  below; no production correction is authorized by this packet.

## 3. Relevant scope

Sole writer in this worktree:

- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java`
  — task-owned PostgreSQL 16-alpine Spring integration test.
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`
  — focused real-random-port BFF and local gRPC boundary test.
- `.agent/student-role-02/homework-db-verification/*` — packet, checks,
  evidence, diff and summary for this bounded verification leaf.

## 4. Required behavior

- Start only task-owned, random-port Testcontainers resources. The Academic
  test must exercise the actual Spring `HomeworkStudentService` and
  `HomeworkCompletionRepository` against migrated PostgreSQL 16-alpine.
- Concurrent repeated `completed=true` commands for one signed active student
  must complete without unique-constraint errors, leave exactly one target
  completion row, and preserve another student's completion row.
- Concurrent repeated `completed=false` commands must complete without errors,
  leave zero target rows, and preserve another student's completion row.
- The real service test may cover one meaningful DB-backed authz rejection; it
  must not replace the required concurrency proof with mocks or SQL-only calls.
- The BFF test must cover the new GET and PUT using a real random-port HTTP
  client and a bounded local gRPC fake/server: signed claims supply student and
  group identity, PUT forwards desired state and identity, spoofed HTTP actor/
  group headers are ignored, valid responses are no-store, and malformed,
  absent/tampered/wrong-role/unknown or invalid inputs map to the contract
  status class/ProblemDetails.

## 5. Constraints

- No child agents, commits, integration, deploy, migration, config, lockfile,
  generated file or production-code edits.
- Preserve all existing uncommitted API/UI/BFF work and unrelated evidence.
- Do not touch the existing reused Academic container or root fixtures; use
  random mapped ports and clean up only resources created by these tests.
- Do not print JWTs, secrets, personal data or full transcripts. Do not turn a
  WARN/ERROR into a code change without request linkage and reproduction.
- Terra escalation is forbidden unless root records the required defect or
  complexity gate with request, reproduction, new evidence, correction, bounded
  scope and root decision.

## 6. Existing patterns

- `HomeworkMigrationIT` and `HomeworkControllerIT` show Academic DB fixtures;
  `HealthDegradationIT` demonstrates a dedicated container, while this packet
  requires no reuse to avoid shared-resource interference.
- `AbstractAcademicIntegrationTest` shows test profile and infrastructure
  exclusions; its reused PostgreSQL must not be reused here.
- `StudentHttpGrpcAuthIT` is the BFF precedent for `TestRestTemplate`, random
  port and local gRPC server/fake. Reuse its setup conventions without editing
  the existing test.
- Java `StudentApi`, `StudentApiController`, `MobileAcademicClient`, proto and
  `StudentHomeworkGrpcIdentityInterceptor` are the critical originals.

## 7. Acceptance criteria

- The PostgreSQL concurrency IT is green with all concurrent futures settled,
  no thrown SQL/unique error, target count exactly one after true and exactly
  zero after false, and other-student state unchanged.
- The HTTP/gRPC IT is green and proves the new GET/PUT request/response path,
  identity forwarding, desired state, no-store and relevant negative mappings.
- The new tests compile and run under the existing `test`/`integrationTest`
  split without changing dependencies or build configuration.
- Scope diff contains only the two test classes and this leaf's evidence files;
  no critical finding remains unresolved.

## 8. Verification

- Record baseline/revision, commands, exit codes, Java/Gradle/Docker/
  Testcontainers environment and concise runtime evidence in `checks.json`.
- Run scoped `git diff --check`, targeted Academic and Mobile BFF compile/test
  or integration tasks, and `OpenApiSnapshotIT` once without update mode to
  recheck the checked-in Java-first snapshot.
- Use `--no-daemon`; Gradle/Docker commands require the already recorded
  elevated route because absolute classpath access is sandbox-blocked. Report
  any environment failure as BLOCKED with reproduction rather than altering
  code/config.
- Finish with a clean scope diff and handoff summary; parent/root decides
  integration and independent review.

## 9. Do not

Не изменять `HomeworkStudentService`, repositories, proto, controllers, BFF
clients, OpenAPI, generated TypeScript, Gradle files, migrations, test
dependencies, shared integration bases, root checks or чужие worktrees. Не
выдавать SQL-only или mock-only результат за real PostgreSQL/runtime proof.
