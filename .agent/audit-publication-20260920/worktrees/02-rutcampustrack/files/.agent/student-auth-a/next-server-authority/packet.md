# A next server authority — read-only freeze packet

Date: 2026-09-09. Risk: S3. Base `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Status: `GATED_READY`; this is not a product-write or runtime grant.

## 1. Goal

Freeze the next A-owned server slice: implement the accepted session ports against PostgreSQL in exactly one JDBC adapter and prove atomic behavior in one integration-test class. Start only after the parent accepts B0 PostgreSQL evidence and the exact V24 source. HTTP session/profile endpoints, JWT cutover, Gateway admission, BFF/shell composition and WebSocket revocation remain later scopes.

## 2. Context/evidence

Accepted A domain: `session-domain/repair-01/manifest.json` SHA `2200AED1C86FA3D54BC2AF1C3E6BD642A2923B36858406B349255935BEA8BA64`; review SHA `8DEEF183CB78106BEC45A9AA8D9435E77145E16F9899DC24BA5CE7C4C4662FAF`. Frozen authority contract SHA `47FC36F57AFCEEC489B5C95C6C169C2CBA9F53FEED2287CF6946B1CB150D807E`; addendum SHA `F42578CD925CCC8A9C488B903D75DE086C6BEB511FC31C490778A3C6C62FA6CA`.

Read-only audit found B's V24 candidate at 7,618 bytes, SHA `1D15418FA4869D1288B3FA25F688A237087294F360AEA6729F524DE2F5B67F90`; B's latest evidence still awaits/runs PostgreSQL verification and its task is active, so this is not B0 acceptance. All 13 reserved B Auth contract files are absent: `AuthSessionApi`, `InternalSessionAdmissionApi` and 11 DTOs through `AuthAdmissionResponse`. In A, `JdbcSessionAuthority`, its IT, `SessionAdmissionService` and the admission controller are absent. Current `AuthService` SHA `45BCEEB5...` still treats Redis refresh keys as authority; `InternalIssuerController` SHA `FEA8F94D...` still signs caller-supplied identity. The later admission design remains `.agent/student-auth-a/admission/preflight.md` SHA `045144C87ADD0199F1F84719BFD28FCC7CD5931701AC47C5ACA019FF2B0FA9B1`.

## 3. Relevant scope

Future A ownership is exactly:

1. new `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java`;
2. new `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java`;
3. evidence under `.agent/student-auth-a/session-jdbc/final/**`.

The adapter implements both existing `SessionStatePort` and `CredentialSessionTransactionPort`.

External ownership is explicit. **B** supplies accepted V24, both Auth API interfaces, 11 Auth DTOs, Java-first exports, public `SESSION_STATE_STALE`, and later Gateway API-module build dependencies. **C** owns `InternalJwtIssuerClient`, `InternalJwtIssuerFilter`, `JwtAuthenticationFilter`, typed mapping, admission-before-limiter and identity stripping/overwrite; current snapshots are `511FC90E...`, `75323F42...`, `D294CEAA...`. **E** consumes B-generated clients and A/C behavior in mobile-core/PWA/TMA, wires the seven profile views, reboots after role switch, and purges the current owner's homework/schedule/map state only after confirmed invalidation/logout. Shared DTO/generated/proto/migration/build/BFF/shell/index/config files are excluded from A.

## 4. Required behavior

Use V24 and one transaction manager. Lock user before session in every mutation; derive grants and `roles_version` coherently. Create sessions only after locked credential/grant/version recheck and append LOGIN atomically. Read one coherent `(userId,sid,now)` snapshot. Select only an own selectable grant with expected version; same-role success is idempotent without a fake event. Rotate refresh JTI by strict CAS, retain absolute expiry, return a typed conflict for the immediate previous loser and reject older unknown JTI without revoking the winner. Clear an unselectable active grant with a version bump before returning state. Revoke current/all and password-change must include current session and event in the same commit. Password change locks/rechecks observed credential and updates hash/flags atomically. Every operation enforces sid/user ownership. No bearer, JTI, hash or service credential appears in failures, logs or evidence. Redis is not authority. Never physically delete session/history rows.

## 5. Constraints

No implementation or runtime until the parent provides B writer release, final V24 hash, actual PostgreSQL command/exit/XML, fresh B0 review and an A heavy-runtime lease. Rehash V24 before writing; a changed hash requires dated evidence. One fresh Luna max leaf is sole writer of the two product paths. Preserve foreign dirty files. No entity/repository/controller/JWT/DTO/build/config/migration/shared edits, reused database/container, deploy, production migration, secrets or real OTP/TMA/messages.

## 6. Existing patterns

Use Java 21, Spring JDBC/named parameters and the existing transaction manager. Use command/`Clock` time consistently. Map SQL constraint/serialization outcomes to existing typed port results. V24 is schema authority; legacy `User.role`, status/headman flags and Redis keys are not post-cutover authorization truth.

## 7. Acceptance criteria

Fresh PostgreSQL 16 IT covers creation/snapshot, own selectable role, same-role idempotency, terminal read-only and suspended failure; foreign sid/grant and version mismatch; two concurrent refreshes with exactly one winner and one previous-JTI conflict; fixed expiry; revoke/revoke-all/password atomic rollback under injected failure with exact events; denial for a snapshot after revoke commit; no stale-grant resurrection or mixed snapshot during concurrent version change; and absence of synthetic secrets from errors/logs. This proves the adapter only, not Auth HTTP/JWT/Gateway/BFF/WS or full-role acceptance.

## 8. Verification

After gates, use isolated database `rct_student_auth`, Testcontainers reuse disabled and the parent's exclusive lease:

`./gradlew :services:auth-service:auth-app:integrationTest --tests "ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT" --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks`

Record revision, command, environment, start/end, exit, JUnit XML counts/timestamps/hashes, database/container identity, final two hashes, `git diff --check` and scoped status. Stop the writer, then run fresh Sol high review against this packet, accepted V24 and stable diff.

## 9. Do not

Do not target an unaccepted B0 candidate, create B contracts in A, mix controller/JWT/Gateway work into this slice, model atomicity with mocks, treat Redis deletion as durable revoke, add refresh grace/retry, infer grants from legacy columns, cache admission, hand-edit generated clients, or claim the server/profile path complete from JDBC alone.
