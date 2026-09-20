# ACCESS-A2 source packet

Risk: S3. This packet is for the sole writer in the isolated worktree
`C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-access-scope`,
branch `codex/v2-access-scope`.

## Goal

Implement the authoritative Academic `ResolveStudentProjectionScope` boundary
for a signed student: own semester history and effective subjects plus a
separate rank cohort.

## Context/evidence

The frozen baseline is E revision
`b8220ac92125a8afa37598b270aa4fab7aa1f470`.  The owner packet is
`ACCESS-A2.md`; the orchestration rules have SHA256
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
Critical originals were read in E: the additive Academic proto, group-history
entity/repository, transfer service, existing signed identity interceptor and
service, V24 grant authority, and V25 effective assignments.  A callable
ownership gateway was unavailable; the frozen packet and SLOTS assignment were
the ownership evidence.  The source worktree was created from the exact E
revision and foreign dirty work was left untouched.

## Relevant scope

Only the additive `proto/academic.proto` contract, the new
`academic/studentprojection/**` port/adapter/resolver/error models, the narrow
Academic gRPC handler and signed-identity interceptor registration, focused
unit/PostgreSQL/gRPC tests, this packet, and the local `AGENTS.md` pointer are
in scope.  The approved typed-error addendum permits one Academic dependency
line for `proto-google-common-protos:2.29.0`.

## Required behavior

Accept signed `STUDENT` claims only and re-read the current STUDENT grant,
grant status, and roles version.  Require session identity fields to be
present and positive, reject stale/missing/suspended authority before source
disclosure, and permit terminal statuses only as signed read-only own scope.
Validate the complete chronological half-open group history, including valid
zero-duration rows, reject inverted/overlapping/ambiguous data, clip to the
selected semester, and derive transfer history from all actual group changes.
Resolve subjects by subject ID, group, semester, and effective interval.  Use
the Moscow server date for past classification.  A past transferred scope is
`HIDDEN`, has no roster query/rank group, and is ineligible.  Otherwise rank
uses the current authoritative grant/history (or the sole historical group
for an untransferred past semester), selected-semester membership, and the
exact active cohort without synthetic self insertion or former-group union.
Every missing, inconsistent, or unavailable source is a typed failure.
All resolver reads execute in one explicit PostgreSQL `REPEATABLE_READ`
transaction snapshot.

## Constraints

Do not edit migrations, grant writers, transfer behavior, Schedule/map/UI/BFF
code, generated outputs, lockfiles, secrets, or the original/E checkout.  Do
not run Gradle, Docker, build, test, or runtime commands during source
authoring.  Do not spawn children or use Terra.  Preserve all existing Academic
and homework/map registrations.

## Existing patterns

Use `StudentHomeworkGrpcIdentity.CLAIMS` and `InternalJwtValidator` for signed
identity, V24 `user_role_grants` plus `users.roles_version` for current
authority, V25 `assignments` for effective subject identity, parameterized
`JdbcTemplate` reads, immutable local records, and the existing
`google.rpc.Status` + `Any.pack` + `StatusProto` typed gRPC envelope pattern.

## Acceptance criteria

Focused coverage includes active/past/terminal scopes, same-day and return
transfers, clipped/overlap/inverted/missing history, Moscow day boundaries,
stale/wrong/suspended/foreign denial, subject-ID/effective interval union,
exact cohort/no self insertion, HIDDEN zero-roster behavior, typed errors, and
preservation of all existing registrations.  PostgreSQL coverage exercises
the real parameterized adapter with multiple groups.

## Verification

This source lease records planned unit, compile, gRPC, and PostgreSQL
`integrationTest` selectors only, including a concurrent authority/history
snapshot case.  No checks or runtime were executed here.
After integration, the assigned checker should run the selectors from
`summary.md`, then a fresh independent Sol review, followed by real transfer
and grant-authority runtime evidence when that gate is available.

## Do not

Do not claim tests, generated-code compilation, Docker/PostgreSQL execution,
or runtime transfer/grant evidence from this source-only checkpoint.  Do not
merge to main or push the branch; return the stable source diff and pending
gates to the parent.

## A3 F1/F2 bounded repair addendum

### Goal

Close the independent A3 authorization and transport findings while keeping
the A2 projection contract and assigned worktree unchanged.

### Context/evidence

F1 reproduced because A2 checked only signed session identifiers and current
grants/roles version; it did not re-read `auth_sessions`.  The canonical live
session admission is `JwtAuthenticationFilter` at
`services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java:165-195`,
with `SessionSnapshot.isLiveAt` using `now.isBefore(refreshExpiresAt)` and
`sameIdentity` comparing selected role/status/group/headman/read-only state.
F2 reproduced because transaction advice can throw before the resolver method
body, bypassing its `DataAccessException` catch and producing an internal,
unspecified response.

### Relevant scope

Only `StudentProjectionQuery`, `JdbcStudentProjectionQueryAdapter`,
`StudentProjectionScopeService`, `StudentProjectionGrpcErrors`, and focused
unit/gRPC/PostgreSQL tests plus this local evidence are changed for A3.  No
schema, auth-service writer, proto, dependency, or cross-service changes are
part of this repair.

### Required behavior

Read `auth_sessions` by exact `user_id` and `sid` in the resolver's same
`REPEATABLE_READ` transaction.  Require a live, unrevoked, unexpired row with
matching session version, selected current STUDENT grant, and signed selected
identity.  Complete authorization before semester/history/assignment/subject/
roster reads.  Map only Spring transaction/data-access failures at the gRPC
boundary to typed `DEPENDENCY_UNAVAILABLE`/`UNAVAILABLE`; programmer failures
remain `INTERNAL`/`UNSPECIFIED`.  Preserve unresolved scope's accepted
`PERMISSION_DENIED` mapping.

### Constraints

Source/test authoring and `git diff --check` only; no Gradle, Docker,
PostgreSQL, generated-code, runtime, network, push, merge, or migration
operation.  Preserve all foreign changes and existing registrations.

### Existing patterns

Use V24 `auth_sessions`, `user_role_grants`, and `users.roles_version`, the
canonical auth filter's live/identity rules, parameterized `JdbcTemplate`,
immutable port records, and the existing `StatusProto` envelope.

### Acceptance criteria

Missing, revoked, expired, wrong-user/sid, stale session/roles versions,
active-role mismatch, role switch, and signed selected-identity mismatch deny
without dependent reads.  Valid active/terminal snapshots preserve A2 own
scope/HIDDEN behavior.  Transaction-begin data-access failures are typed
unavailable, while unknown failures remain internal.  The PostgreSQL test
invokes the actual `@Transactional` annotation through a Spring proxy for
the concurrent snapshot case.

### Verification

Focused unit, gRPC, compile, and PostgreSQL `integrationTest` selectors remain
pending the main heavy lease.  This source stage runs only `git diff --check`
and records its revision, exit code, and environment below in `checks.json`.

### Do not

Do not add session writers/migrations, infer history from the JWT group hint,
broaden transport mapping to arbitrary runtime exceptions, or claim any
product check/runtime result before the later lease.
