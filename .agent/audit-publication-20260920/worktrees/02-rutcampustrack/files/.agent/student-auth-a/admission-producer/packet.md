# Auth admission/JWT producer — frozen S3 contract

## 1. Goal

Implement and verify a bounded producer slice that exchanges an **original session-bound access token** for one short-lived internal JWT after a live PostgreSQL session snapshot. Freeze the internal identity wire for C/B1a, strengthen the shared validator, and remove the old caller-claim issuer. This slice does **not** claim that public login/session/refresh/logout/password/profile/WS cutover is complete.

## 2. Context / evidence

- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` with existing A purpose/domain/JDBC changes preserved.
- Accepted JDBC verdict: `.agent/student-auth-a/session-jdbc/final/independent-recheck-pass.md`, SHA256 `8C6694E5CBAED9E68CB9B4DEB80ECB467522C61CB6FADEA727E78A752E95BDF1`.
- B Auth13 is explicitly accepted: fresh Sol high `AUTH13_SUBSET_PASS` and `B0_EXACT25_CONTRACT_SOURCE_PASS`; accepted source manifest `C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack/.agent/student-academic-b/union/resume-contracts-2026-09-09/file-sha256.md`, SHA256 `790081134DF7467362C844144345EF471B18B8F8D98A386A92DB448E2FE36097`. Import only the 13 Auth API/DTO rows listed below, byte-for-byte.
- C consumer contract is accepted. The producer must use its exact claim names/types and status mapping below.
- Critical originals opened before freeze: `auth/service/JwtService.java`, `auth/controller/InternalIssuerController.java`, `auth/security/InternalIssuerSecretFilter.java`, `auth/config/SecurityConfig.java`, `auth/session/port/SessionStatePort.java`, `auth/session/jdbc/JdbcSessionAuthority.java`, shared `InternalJwtClaims.java`, `InternalJwtValidator.java`, `InternalJwtTestFactory.java`, current JWT/issuer/arch tests.

## 3. Relevant scope / sole-writer ownership

### Immutable accepted Auth13 import

Copy exactly from WT34a5; do not edit after copy:

1. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthSessionApi.java` — `C0E33F5A93054B34DD9326E2CC865274384122D07B135457CCF1B2067954DEB2`
2. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/InternalSessionAdmissionApi.java` — `DB14CD24EB3448A12059A8D968A618E79EFF2B7629BDCC57215E4D3F51A92DD3`
3. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AccountHistoryEvent.java` — `3A745E2B1270F07E1F0523678D93BAB911AF0EBD1DCF4B4CB2EC9B4D66FDCD0F`
4. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AccountHistoryPage.java` — `593D09DEDFFD0F6B279594350B70088B76BD669B836BC071020E45A3135B9F9D`
5. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionRequest.java` — `A874654D1E410590F43E59B11318F80C6C35A32AC9D0A32D940A53665A089452`
6. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionResponse.java` — `1841B46A85ADB96212A9D167A9253A9CB9002021C96C6CEB0954DAD8C3E45912`
7. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionSummary.java` — `38813017B0E10E4F6C9C1398EC8EB5D6546C0352151DA6E78362587B70BBD572`
8. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionsPage.java` — `02AEFD0580AD2F1B939D4C5EACE3CCE17F06B66B440FF7AFA7273AE61B9A6990`
9. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/CurrentSessionResponse.java` — `DA0899902573AB79D06C6713E44222322B96290F441DD7C4B913D809E965E9A1`
10. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordPolicyResponse.java` — `5124D523F20AE93C4C3404D8419D37C8FB1B7C18A9198885E177121C3483BF7D`
11. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/RoleGrantResponse.java` — `216D5BCE545AB120EECA05E14FB22E58085B407EB4CA9DBBDAB83DF076DABA24`
12. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleRequest.java` — `B62255C419C0BAD4F2854E6FAC4C358DC9E59AB1F9EC84617A6673E47B82AD56`
13. `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleResponse.java` — `613957796DCA67F271ECB876D45791CECB7719C5DDE07A96C7D06893553777F2`

### Producer product files

- modify `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java`
- add `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionAdmissionService.java`
- add `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionAdmissionException.java`
- add `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalSessionAdmissionController.java`
- delete `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalIssuerController.java`
- modify `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java` only for typed admission errors and `Cache-Control: no-store`

### Reserved shared-security files

- modify `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtClaims.java`
- modify `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java`
- modify `services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java`
- modify `services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java`
- modify `services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/DualModeUserContextFilterTest.java`

### Auth tests

- add `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionAdmissionServiceTest.java`
- add `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`
- delete obsolete `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalIssuerIT.java`
- modify `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java`
- modify `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java` only to compile/test the new producer API; do not change `JwtAuthenticationFilter.java`
- modify `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/arch/AuthApiContractTest.java` to recognize `InternalSessionAdmissionApi` and reject reliance on the old issuer controller
- writer evidence only under `.agent/student-auth-a/admission-producer/` except this frozen packet

No other product/build/config/test source is owned in this stage.

## 4. Required behavior and frozen wire

### JWT wire

Access admission input and internal JWT use canonical values:

- `sub`: JSON string, canonical positive Java `long` decimal
- `sid`: JSON string, canonical lowercase UUID
- `sv`: JSON string, canonical positive Java `long` decimal
- `rv`: JSON string, canonical positive Java `long` decimal
- `role`: JSON string, one of `STUDENT|TEACHER|HEADMAN|ADMIN`
- `status`: JSON string, one of `ACTIVE|SUSPENDED|EXPELLED|GRADUATED|ARCHIVED`
- optional `group_id`: JSON string, canonical positive Java `long` decimal
- `is_headman`: JSON boolean
- `readOnly`: JSON boolean
- `token_use`: exact `access` on admission input and exact `internal` on produced token
- `iss`: exact `rutcampustrack-auth`
- `aud`: singleton exact `rutcampustrack` for access; singleton exact `rutcampustrack-internal` for internal
- required whole-second `iat` and `exp`, with `iat < exp`

Freeze shared record signature as:

`InternalJwtClaims(long userId, UUID sessionId, long sessionVersion, long rolesVersion, String role, String status, Long groupId, boolean isHeadman, boolean readOnly)`.

Do not add an old-signature or defaulting constructor.

### Producer flow

- Add a new session-bound access-token generator/parser in the real `auth/service/JwtService.java`. Keep the existing legacy `generateAccessToken(User)` only because public login cutover is stage 2; label it legacy and never accept its output at admission. Remove the caller-claim `generateInternalToken(long, role, group...)` API.
- `SessionAdmissionService` parses the original access token with the strict session-bound parser, calls exactly one `SessionStatePort.snapshot(userId, sid, now)`, and rejects any failure/mismatch. It must not read legacy `User.role/status/group/isHeadman`.
- Compare access `sv` and `rv` to the coherent snapshot; require a non-null active grant; compare role/status/group/isHeadman/readOnly to that authoritative grant/snapshot. `is_headman` is true exactly for `HEADMAN`.
- Produce the internal JWT only from the accepted `SessionSnapshot`. Internal expiration is `min(originalAccessExp, now + internalIssuerTtl)`, truncated to whole seconds, strictly after now. `AuthAdmissionResponse.expiresAt` equals JWT `exp` and all response identity fields equal JWT fields.
- `InternalSessionAdmissionController` implements immutable `InternalSessionAdmissionApi`. `/internal/auth/admit` continues to inherit the existing `/internal/**` constant-time service-secret filter. Missing/wrong service credential remains distinct from user/session denial.
- Delete the old `InternalIssuerController`; `/internal/issue-internal-jwt` must have no controller mapping. The old API/DTO contract files are outside this stage and remain untouched.
- `InternalJwtValidator` must reject missing, extra-audience, wrong-type, noncanonical, out-of-range, unknown role/status, non-boolean and invalid time claims. It returns only the frozen record.

Typed admission failure mapping through `GlobalExceptionHandler`, with RFC problem JSON, `extras.code`, and `Cache-Control: no-store`:

- `INVALID_SESSION` and `SESSION_REVOKED` -> 401
- `ROLE_NOT_GRANTED` and `ROLE_NOT_SELECTABLE` -> 403
- `SESSION_STATE_STALE` -> 409
- `AUTHORITY_UNAVAILABLE` -> 503

Malformed/untrusted original access is `INVALID_SESSION`; service-credential/protocol failures remain distinct and never echo bearer material.

## 5. Constraints

- One fresh Luna max developer is sole writer. It is not alone in the repository: preserve all existing A/B/C/D/E changes and never revert foreign work.
- Source stage first. Do not run Gradle, Docker, Testcontainers or other heavy runtime until root grants a separate stage-runtime lease after source freeze and exact hash guard.
- The 13 imported files must remain byte-equal to WT34a5 and keep the listed hashes. Do not copy the two B build files or any other B file.
- Keep `SessionStatePort`, `JdbcSessionAuthority`, V24, all existing JDBC tests/evidence, `SecurityConfig`, `InternalIssuerSecretFilter`, `JwtAuthenticationFilter`, public login services/controllers and application config unchanged.
- No cache and no caller-supplied identity. Never log, include in exceptions, or expose access/internal tokens through `toString`.

## 6. Existing patterns and consumer delta

- Existing `InternalIssuerSecretFilter` protects all `/internal/**` using constant-time comparison; reuse it without mutation.
- Existing `SessionStatePort.snapshot` is one transaction-shaped coherent read and already returns roles/session versions plus active grant.
- Existing `JdbcSessionAuthorityIT` shows the approved PG16/Flyway24 standalone fixture and lock/race patterns; reuse patterns without editing or rerunning it.
- Additional consumers/fixtures outside A reservation are a declared union delta, not A write scope: direct `InternalJwtClaims` constructors in mobile-bff `StudentQueryHomeworkTest`, `StudentHomeworkHttpGrpcIT`; academic `HomeworkStudentServiceTest`, `HomeworkStudentCompletionConcurrencyIT`, `StudentHomeworkGrpcIdentityInterceptorTest`; attendance `StudentGrpcBoundaryTest`, `AttendanceStudentGrpcServiceTest`. `InternalJwtTestFactory.validToken` callers are schedule `ScheduleUserContextFilterStrictModeIT`/`ScheduleUserContextFilterIT`; attendance equivalents; academic equivalents; mobile-bff `StudentHttpGrpcAuthIT`, `StudentHomeworkHttpGrpcIT`, `OpenApiSnapshotIT`. Audit custom builders in academic/attendance/schedule `integration/InternalJwtTestConfig`, notification `SecurityInfrastructureTest`, gateway `InternalJwtIssuerIT`, and auth JWT tests.
- Main semantic consumers owned by B1a/service owners: shared `DualModeUserContextFilter`; mobile-bff identity/request context/student services; academic and attendance HTTP/gRPC identity filters/interceptors/services; schedule and notification filters. Union compilation must cover them; A does not edit them now.

## 7. Acceptance criteria

- Exact 13/13 import hash match; no other B source imported.
- Canonical session-bound access parses and produces an internal token/response with exact equality for sub/sid/sv/rv/role/status/group/isHeadman/readOnly and expiration.
- Legacy access without sid/sv/rv/status/readOnly and every malformed/wrong-purpose/wrong-audience token fail admission.
- Valid live ACTIVE, terminal read-only and HEADMAN snapshots are represented correctly; null active role, SUSPENDED/unselectable, stale sv/rv, changed grant/status/group, revoked/expired/missing/foreign session fail with the exact mapped code/status.
- Two calls cause two `snapshot` calls and two newly signed internal tokens; no cache.
- Real PG16 seeded-session IT uses actual `JdbcSessionAuthority`/V24 authority and demonstrates success plus post-commit revoke, roles-version change, session-version change and active-grant/status mismatch denial.
- Old issuer route has no mapping; service-secret failures remain 401 at the guard and never become a user-session problem code.
- Shared validator has focused positive/negative coverage for every frozen claim invariant and has no compatibility constructor/defaulting path.
- No token appears in DTO/service/exception/controller `toString`, logs or problem bodies.

## 8. Verification

During source stage: static hash/structure/diff checks only; publish `SOURCE_READY` with exact manifest and no runtime claim.

After root runtime lease:

1. focused shared-security unit tests;
2. focused Auth JWT/admission/arch unit tests;
3. exact `InternalSessionAdmissionIT` against fresh `postgres:16`, Flyway V24 authority, reuse disabled;
4. verify JUnit XML counts, commands, revision, environment, copied byte-equal XML, ports/process/container cleanup and RELEASE;
5. compile affected Auth/shared modules; all downstream consumer modules compile later on the union owner because their fixtures are outside A scope;
6. fresh independent Sol high review of stable producer diff/evidence. Accepted shared record/wire subset is handed to B1a/C immediately after this focused acceptance.

## 9. Do not

- Do not implement any of the seven public `AuthSessionApi` methods in this stage, add stubs, duplicate mappings, or claim public login/session cutover.
- Do not edit `AuthApi.java`, legacy `ChangePasswordRequest.java`, `AuthController`, `AuthService`, `OtpService`, `TmaService`, `SecurityConfig`, `JwtAuthenticationFilter`, query ports, Gateway/BFF/WS or any downstream consumer.
- Do not edit the 13 accepted contracts, B build files, V24/JDBC/domain files, migrations, generated code, profile UI or theme scope.
- Do not retain old caller-claim internal issuance, use legacy claims as authority, accept numeric JSON for sid/sv/rv/group, add a cache, or weaken issuer/audience/purpose/time checks.
- Do not run heavy checks before separate lease; do not stage, commit, push, deploy or run production migrations.

Stage 2 is separately frozen after this producer source is stable: coherent public login/OTP/TMA/session/refresh/logout/password/history wiring for all seven `AuthSessionApi` methods, elimination of duplicate legacy routes, exact query-port delta, and real E1 cutover. It reserves legacy `AuthApi.java`/`ChangePasswordRequest.java` plus Auth controller/services/security/config changes only after an exact contract.
