# ACCESS-A2 evidence

## Ownership and baseline

- Assigned worktree: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-access-scope`.
- Branch: `codex/v2-access-scope`.
- Source/base revision: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
- Root checkout was already dirty; it was not edited.  The target path and
  branch were checked before creation.  This leaf created the worktree with
  `git worktree add -b codex/v2-access-scope ... b8220ac...`; the parent later
  independently verified the worktree and base.
- Direct Git inspection of the source E checkout hit Git's existing dubious
  ownership guard (exit code 128).  No safe-directory/global-config bypass was
  attempted.  The parent handoff recorded verification of the exact commit
  object before source authoring continued.
- No callable ownership gateway was available.  The frozen ACCESS-A2 packet
  and SLOTS assignment are the recorded ownership evidence.

## Critical source readings

- `proto/academic.proto`: existing Academic service and immutable subject tags.
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/StudentGroupHistory.java:14,30,34`:
  history rows carry `group_id`, `joined_at`, and `left_at`.
- `.../repository/StudentGroupHistoryRepository.java:9-10`: current history
  repository patterns.
- `.../user/UserService.java:298-321`: transfer closes the open row and adds
  a new history row; grant synchronization remains a downstream gate.
- `.../grpc/AcademicGrpcServiceImpl.java:82`: existing constructor and
  service registrations were preserved while adding one resolver port.
- `.../grpc/StudentHomeworkGrpcIdentityInterceptor.java:21-46`: signed JWT
  context binding is retained for homework and extended to the resolver RPC.
- `.../resources/db/migration/V24__auth_session_authority.sql:32-120`:
  positive `roles_version`, current role grants, and their version trigger.
- `.../resources/db/migration/V25__student_subject_homework_foundation.sql:129-165`:
  effective assignments are immutable, typed, and half-open.
- `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtClaims.java:8-19`:
  signed identity includes session, role/status, group hint, roles version,
  and read-only state.  The resolver uses the group only for exact identity
  equality and never as a history-scope fallback.
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java:165-195`:
  live session admission requires exact user/session and session/roles versions
  plus `sameIdentity` role/status/group/headman/read-only equality.
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/SessionSnapshot.java:87-89`:
  the live boundary is unrevoked and strictly before `refreshExpiresAt`.
- `services/academic-service/academic-app/src/main/resources/db/migration/V24__auth_session_authority.sql:122-160`:
  `auth_sessions` is the durable session source with `sid`, `user_id`, active
  grant, session version, expiry, and revocation columns.

## Implemented source evidence

- `proto/academic.proto` has the additive RPC and exact A1 request/response,
  membership-segment, rank-visibility, and typed-error ledger.  Existing
  fields/tags remain unchanged; response field 2 and `group_id` are reserved.
- `StudentProjectionScopeService` validates signed session fields, current
  STUDENT grant status and roles version, full chronological history, clipped
  own segments, effective assignment intervals, Moscow date, and exact rank
  cohort.  It checks past-transfer HIDDEN before terminal handling, returns
  before the roster adapter in HIDDEN cases, and fails closed on absent or
  inconsistent dependencies.  Its read-only transaction explicitly uses
  PostgreSQL `REPEATABLE_READ` so authority, history, subjects, and roster are
  resolved from one database snapshot.
- A3 F1 repair adds an `auth_sessions` read before any semester/history,
  assignment, subject, or roster query.  It validates exact sid/user, strict
  live expiry/revocation, session version, active current STUDENT grant, and
  selected signed role/status/group/headman/read-only identity.  The JWT group
  remains an equality check only and is never a history fallback.
- `JdbcStudentProjectionQueryAdapter` uses parameterized SQL against V24
  grants/roles version, group history, V25 assignments, subjects, and active
  grant/history roster membership.  It does not read `users.group_id`, legacy
  `teacher_subject_groups`, or legacy `users.status` as an authority gate.
  Dynamic assignment arguments are ordered as semester, groups, date-until,
  date-from to match the SQL placeholders.
- `AcademicGrpcServiceImpl` maps the immutable resolver result to the new
  proto and maps failures to `google.rpc.Status` with packed
  `AcademicProjectionErrorDetail`; unresolved student scope is explicitly
  `PERMISSION_DENIED`, while dependency/source failures remain `UNAVAILABLE`.
  Its existing constructor call sites were
  updated with a mock dependency only in the focused existing test.
- A3 F2 repair maps Spring `DataAccessException` and `TransactionException`
  failures, including causes raised by transaction advice before resolver
  entry, to `DEPENDENCY_UNAVAILABLE`/gRPC `UNAVAILABLE`.  An unknown
  programmer exception still maps to `INTERNAL` with an UNSPECIFIED detail.
- `StudentHomeworkGrpcIdentityInterceptor` keeps both existing homework method
  registrations and adds `ResolveStudentProjectionScope`.
- Focused tests are authored but intentionally unexecuted:
  `StudentProjectionScopeServiceTest`, `StudentProjectionGrpcServiceTest`,
  `StudentHomeworkGrpcIdentityInterceptorTest`, and
  `StudentProjectionQueryAdapterIT`.  The PostgreSQL class includes a gated
  concurrency case that pauses after authority read, commits a concurrent
  grant/history transfer, and verifies the resolver completes from its initial
  snapshot.
- Focused A3 unit cases cover missing/revoked/expired/wrong-user/wrong-sid,
  stale session version, role switch, and selected identity mismatches with
  zero dependent reads; gRPC cases cover transaction-begin dependency mapping,
  unknown programmer mapping, and unresolved permission denial.  The
  PostgreSQL case inserts a durable `auth_sessions` row and exercises the
  actual annotation-backed resolver proxy.

## Authority policy decision

The current STUDENT grant's `status` and `group_id`, together with
`users.roles_version` and the signed role/status/session identity, are the
authoritative read inputs.  The legacy `users.status` and `users.group_id` are
not used to infer scope or suppress an active current grant.  This follows the
owner confirmation on 2026-09-13 and leaves the known transfer/grant writer
gap for the downstream gate.

## Check and runtime state

## A4 F3 fixture correction

The PostgreSQL RR concurrency fixture in
`StudentProjectionQueryAdapterIT.insertSession` declared seven JDBC bind
placeholders (the `session_version` literal and `auth_method` literal are not
binds) but supplied eight arguments.  The repair removes only the extra final
`Timestamp.from(createdAt)` argument, preserving the nine auth-session columns,
two SQL literals, and the seven intended bindings: sid, user, active grant,
refresh JTI, expiry, created-at, and last-seen-at.  F1/F2 production source and
their focused tests are unchanged.  The post-repair `git diff --check` result
is recorded in `checks.json`; product tests remain pending the main heavy
lease.

## A5 F4 authority-divergence classification

The active current-rank branch now classifies absent current history and a
current-group mismatch with the authoritative STUDENT grant as
`INCONSISTENT_SOURCE`, preserving gRPC `UNAVAILABLE`.  The existing earlier
empty clipped-membership guard still classifies a genuine absence of selected
semester membership as `STUDENT_SCOPE_UNRESOLVED`, with no subject or roster
query.  Focused unit cases cover both divergence forms and the distinct empty
semester case; a gRPC case verifies the inconsistent-source transport detail.
F1/F2/F3 behavior remains unchanged and product tests are still pending the
main heavy lease.

## H12 execution evidence

The frozen product manifest is recorded in `manifest.sha256`.  Its canonical
hash is `E9C165EAC47D71B8EA07CDD379324F07A5C20166C14848BCF1B069B36C4234E0`
(sorted `path=UPPER_SHA256` rows, UTF-8, LF joined without a trailing LF).

The exact focused unit command from the H12 lease exited 0.  Gradle reported
`BUILD SUCCESSFUL`; JUnit reports contain 17
`StudentProjectionScopeServiceTest`, 6 `StudentProjectionGrpcServiceTest`,
and 9 `StudentHomeworkGrpcIdentityInterceptorTest` cases, with zero skipped,
failures, or errors.

The exact PostgreSQL integration selector exited 1 after 3 tests.  Every
failure occurred at `StudentProjectionQueryAdapterIT.java:261` while the
fixture executed `INSERT INTO groups (name, code) VALUES (?, ?) RETURNING id`;
PostgreSQL reported `relation "groups" does not exist`.  Testcontainers
started PostgreSQL 16.13 and Flyway applied 26 migrations to each of the
three generated schemas before this fixture failure.  The report's JDBC URL
shows `?loggerLevel=OFF?currentSchema=...`, which is recorded as observed
evidence only; no source repair was attempted under the stop-on-failure gate.
The PostgreSQL and Ryuk container IDs from the report returned no rows from
post-run `docker ps -a --filter id=...`, so the owned resources were released.
The remaining integration assertions, including adapter binding and the
repeatable-read concurrency case, are therefore unverified.

## H14 separator correction and execution

The first H12 integration run exposed an unconditional `?currentSchema=`
append: the Testcontainers JDBC URL already carried `?loggerLevel=OFF`, so
Flyway migrated per-test schemas while the fixture connection stayed on the
wrong schema.  The sole source correction in
`StudentProjectionQueryAdapterIT.dataSourceForSchema` now selects the literal
ASCII `&` when the base URL contains a query and `?` otherwise, preserving all
existing options and schema isolation.  A source inspection and credential-
free public URL check passed, and the post-correction `git diff --check`
passed (exit 0).

The H14 integration selector was then run under its explicit lease.  It
exited 1 with 3/3 tests failing at the shared fixture insert (helper line
264; callers lines 80, 110, and 142): PostgreSQL reported
`column "code" of relation "groups" does not exist`.  The JUnit report shows
the corrected URLs using `?loggerLevel=OFF&currentSchema=...`, PostgreSQL
16.13 startup, and all 26 migrations applied in each generated schema.  This
is the newly exposed fixture-schema defect; no repair or retry was attempted
after the failure.  The PostgreSQL and Ryuk IDs from the report returned no
rows from post-run ID-filtered `docker ps -a`, confirming cleanup.

## H15 V26 fixture correction and execution

Root-confirmed V8 migration `V8__group_unify_name.sql` drops `groups.code`.
The sole H15 product-test correction removes that obsolete column and value
from `insertGroup`; all remaining fixture helpers were statically reconciled
against V1/V8/V9/V10/V12/V24/V25/V26 with no further discrepancy found.
The frozen 15-file manifest is `C74CC6601ED9B20E3367E894D0F6C5BC7FF2406F5EBEFE6170A9513116C738D4`.

The exact H15 `integrationTest` selector exited 0.  JUnit recorded all three
`StudentProjectionQueryAdapterIT` cases passed: current grant authority,
assignment parameter ordering across two groups, and the annotation-proxied
repeatable-read concurrent update case.  Gradle reported `BUILD SUCCESSFUL`
in 1m20s (37 actionable tasks; 2 executed, 35 up-to-date).  Testcontainers
PostgreSQL 16.13 started, Flyway applied all 26 migrations in each generated
schema, and report URLs preserved `?loggerLevel=OFF&currentSchema=...`.
Post-run ID-filtered `docker ps -a` returned no rows for the PostgreSQL and
Ryuk IDs, confirming cleanup.  No source changes or retries followed the
successful selector.

Only source inspection and file authoring were performed until the bounded
`git diff --check` recorded in `checks.json`.  No Gradle, Java build,
generated-proto compilation, Docker, PostgreSQL container, gRPC server, or
application runtime was started under the source-authoring lease.  The precise
pending selectors and their expected execution class are in `checks.json` and
`summary.md`.

## H18 A7 regression correction and execution

The H17 unit failure was a test fixture clock mismatch: the new regression
used the shared `assertCode` helper's 2026-09-15 clock while its closed-history
case requires server date 2026-10-02.  The bounded test-only repair makes the
selected semester bounds (2026-09-01 through 2026-09-30 inclusive) and the
service clock (2026-10-02T10:00:00Z) explicit in
`pastUntransferredClosedHistoryIsInconsistentBeforeRoster`; production source,
the shared clock helper, and the typed gRPC assertion were left unchanged.

The frozen 15-file product manifest after that correction is
`0A033E8E07EC57AD6219844512D0232C666BC71385F3A844D24F4C28C13A7923`.
The exact H18 unit selector exited 0 and Gradle reported `BUILD SUCCESSFUL`.
JUnit XML recorded 34 tests with zero skipped, failures, or errors: 18
`StudentProjectionScopeServiceTest`, 7 `StudentProjectionGrpcServiceTest`,
and 9 `StudentHomeworkGrpcIdentityInterceptorTest`.

The exact H18 `integrationTest` selector exited 0 and Gradle reported
`BUILD SUCCESSFUL`.  Its JUnit XML recorded 3/3
`StudentProjectionQueryAdapterIT` cases with zero skipped, failures, or
errors, including current-grant authority, multi-group assignment binding,
and the annotation-backed PostgreSQL repeatable-read snapshot case.  The run
used Testcontainers `postgres:16` (server 16.13), and Flyway applied 26
migrations to each generated schema with the preserved `?loggerLevel=OFF` and
`&currentSchema=` URL options.  The owned PostgreSQL container
`73ff363e3fe667ed57e716a9e16c10134e373321d257ce97aa61634cb58f3edb` and Ryuk
container `ebe8756a663f0d2b18882cbeac5be578b2c4edb008b2e48ad856c0d4c21904ad`
both returned no rows from ID-filtered `docker ps -a` cleanup checks (exit 0).
No source changes or retries followed the passing selectors.  Fresh
independent Sol review remains pending after release.
