# Timestamp fixture correction packet

Status: `SOURCE_READY / RELEASE`
Risk: S3 (auth/session integration invariant)
Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Assigned route: fresh bounded implementation leaf, `gpt-5.6-luna / max`; one
writer in the shared checkout, no child agents.

## 1. Goal

Устранить подтверждённый timing flake в real-PostgreSQL
`InternalSessionAdmissionIT`, сохранив production `JdbcSessionAuthority`,
Spring-created admission wiring, system-clock path and existing typed mappings.

## 2. Context/evidence

Immutable failure reference:
`../runtime-final/failure-05-postgres-it.md`; its JUnit XML is SHA-256
`E6942C019856054243E3CF1064DD5BDC9AD17AAD514A62CD5A6A70D4B1FF64C2`, 12590
bytes. The exact run had 6 tests, 5 passed and 1 failure: the suspended HTTP
fixture returned 503 instead of 403. The recorded source cause is that
`seedUser` persists grants from a later whole-second `Instant.now()`, while the
four mutations reused an earlier method `now.plusSeconds(1)`. `RoleGrant`
rejects `updatedAt` before `createdAt`, and the JDBC transaction maps that
invariant failure to `AUTHORITY_UNAVAILABLE`.

## 3. Relevant scope

Product/test scope is exactly
`services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`.
Evidence scope is this new `timestamp-fixture-correction/` directory. The
pre-edit target was untracked in `HEAD` and had the required SHA-256
`FA8FB0AA20074D494CCCC5B89D014275C3EB9582C723E3DE086EF665F5B162F9`, 27566
bytes. Existing dirty paths and evidence remain untouched.

## 4. Required behavior

All four grant status mutations call one `updateGrantStatus(UserFixture,
AuthRole, RoleStatus)` helper. The helper resolves the exact persisted
`RoleGrant`, writes the lower-case enum wire value, derives `updated_at` from
that grant's `createdAt().plusSeconds(1)`, and directly queries PostgreSQL for
`updated_at >= created_at` before the caller admits the token.

## 5. Constraints

Preserve real `JdbcSessionAuthority`, the production
`AnnotationConfigApplicationContext` constructor path and existing Spring
clock behavior. Preserve malformed/revoked 401, role-not-granted/selectable
403, stale 409, unavailable 503, `extras.code` and `Cache-Control: no-store`.
No sleeps, wall-clock workaround, SQL migration, production/domain change,
mock replacement for the real PostgreSQL cases, status relaxation, stage,
commit, reset, revert or deletion.

## 6. Existing patterns

`UserFixture` retains the exact `List<RoleGrant>` loaded by `seedUser`.
`RoleGrant.createdAt()` is the inserted grant `created_at`; status SQL uses
lower-case `RoleStatus.name().toLowerCase(Locale.ROOT)`. The existing `grantId`
helper remains a thin delegate to the new exact `grant(...)` lookup.

## 7. Acceptance criteria

Four call sites use the helper; one helper contains the grant update and one
direct SQL boolean invariant assertion. No grant update uses stale shared
`now.plusSeconds(1)`. The file remains semantically unchanged apart from
fixture timestamp safety. The source precondition and post-edit hash/size are
recorded, and only the assigned test file plus new evidence appears in this
leaf's scope.

## 8. Verification

Run only static/hash/structure/whitespace/diff checks in this leaf. Every check
records revision, command, exit code, Windows PowerShell environment and
evidence in `checks.md`. PostgreSQL/Gradle/Docker runtime is deliberately not
run; the immutable prior failure is recorded in `runtime-evidence.md` and root
owns the post-fix runtime and independent recheck.

## 9. Do not

Do not edit production auth/JWT/session code, shared contracts, migrations,
other tests, generated files, lockfiles or frontend work. Do not introduce a
test double, sleep, current-time mutation, broad refactor or new scope
decision. Escalate to root only for a product/contract/scope delta; none was
needed for this correction.
