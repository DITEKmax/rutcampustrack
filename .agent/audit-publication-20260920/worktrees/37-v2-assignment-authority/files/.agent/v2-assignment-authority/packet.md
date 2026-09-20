# L5A source packet

## 1. Goal

Implement Academic subject lesson types and immutable V25 assignment CREATE/READ
authority. Assignment closure/replacement remains fenced for L5B.

## 2. Context/evidence

Baseline is clean E revision `b8220ac92125a8afa37598b270aa4fab7aa1f470` in
worktree `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-assignment-authority`.
The original checkout was dirty and was not used. V25 defines
`subject_lesson_types` and immutable `assignments`; legacy TSG rows lack date and
lesson-type provenance. V24 `user_role_grants` is read as the durable teacher
grant authority. Proto already contains both assignment-bearing response shapes.

Rules SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
Canonical contract SHA256: `287641EC02BCE97C2F587AE7DB12A3143B076A57394FCC7648697F11BC54B28E`.

## 3. Relevant scope

Academic entities/repositories, subject and assignment services/controllers/
assemblers/DTOs, narrow gRPC `GetTeacherSubjects` and `GetAssignmentsByIds`,
semester row-lock/date guard, dedicated closure exception/handler, and focused
auth/date/overlap/closure/gRPC tests. Legacy TSG classes remain available only
for unrelated source compatibility; no L5A writer uses them.

## 4. Required behavior

Use trusted headman group and server-derived subject/group. Create subject,
1-3 distinct lesson types, and complete initial assignment tuples atomically;
reject nonempty legacy `teacherIds`. Require an active V24 TEACHER grant,
canonical subject type, inclusive semester bounds and exclusive end dates. Lock
referenced semester rows in ascending order before validation/insertion. Prevent
same-teacher/type overlap while allowing different teachers. Reads expose full
identity and deterministic ordering; current teacher reads use Moscow effective
date and active semester. REST keeps stored null end; both gRPC shapes normalize
null to `semester.dateTo + 1`. Closure routes validate authorization and exact
relation, then return typed 409 with no writes.

## 5. Constraints

No schema/migration/proto/generated client/dependency/build configuration,
Schedule/Homework/Attendance product implementation, auth resolver migration,
ADMIN write expansion, force history deletion, client adaptation, push, deploy,
main merge, or runtime execution before the main lease.

## 6. Existing patterns

Use Spring Data row locks, request-scoped `RequestContext`, existing headman
role aspect, `ErrorResponse.PROBLEM_BASE`, `ResourceNotFoundException`, and
PostgreSQL V25 exclusion/foreign-key constraints. Lowercase enum conversion is
the existing Academic JPA convention.

## 7. Acceptance criteria

Headman create/add paths carry exact type/date identity; same-name subjects keep
distinct IDs; rename preserves assignment identity; null gRPC end is concrete;
REST null is preserved; future/expired assignments do not grant current reads;
overlap and semester date races fail closed; current and historical gRPC reads
are complete; both closure routes produce the exact typed 409 and zero writes;
legacy TSG is not a live authority. L5B and clients remain explicitly OPEN.

## 8. Verification

Authored focused tests cover date semantics, lock-before-overlap ordering,
overlap no-insert, headman create/add, legacy rejection, closure no-write,
semester date guard, and gRPC historical identity/effective end. Only static
checks are run in this leaf. Gradle/compile/unit/PostgreSQL/concurrency runtime
checks are `NOT_RUN`: main heavy/runtime lease is unavailable.

## 9. Do not

Do not infer ADMIN writes, accept ambiguous scalar/teacher tuples, infer dates or
lesson types, delete immutable history, expose executable close links, weaken
row-lock protocols with unlocked fallbacks, or claim unexecuted checks passed.
