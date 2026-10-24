# Device preferences backend package

Source-ready: f92cf2ba5 + 3c02a3241 (HEAD 3c02a3241c3ac228f887fdd7b5af9e20c2bf4753). Clean author worktree; no push/main/stand writes. Heavy lease released. Independent source review f92 PASS; final narrow metadata/fixture review is root-owned pending.

User result: authenticated browser can durably read/save light/dark/system independently of account, preserving the preference across logout/login/account switch; another cookie jar remains independent. Backend mirror never authorizes with a cookie alone.

## API delta for root API-CHANGES
- Public GET/PUT /api/auth/device-preferences; internal /auth/device-preferences, existing auth route.
- GET establishes lost/missing/expired context; response {theme:null|light|dark|system, revision:nonnegative canonical decimal string}. Initial revision0. GET renews context TTL.
- PUT {theme:light|dark|system,expectedRevision:canonical nonnegative decimal string bounded by Long.MAX_VALUE}; durable CAS response contains committed theme/revision+1. No identity/device ID fields.
- PUT409 DEVICE_CONTEXT_LOST => GET/reconcile; PUT409 DEVICE_REVISION_CONFLICT => readback/bounded reconcile. Unknown PUT outcome => readback, no blind retry.
- Validation400 INVALID_REQUEST; storage/commit503 DEVICE_PREFERENCES_UNAVAILABLE; unchanged live-session admission errors include INVALID_SESSION/SESSION_REVOKED401, SESSION_STATE_STALE409, AUTHORITY_UNAVAILABLE503. Every normal success/error is no-store; error RFC9457, no selector/JDBC text.
- Exact bootstrap GET/PUT allowlist in both Auth/Gateway; live bearer required. Terminal read-only account grants follow existing bootstrap policy, no new selectable-role bypass.
- __Host-rct_device cookie: random32-byte base64url selector; Secure,HttpOnly,SameSite=Strict,Path=/,no Domain. TTL default31536000secs (config60..63072000). Refresh/CSRF/logout protocol unchanged; logout does not clear device cookie.
- Additive Auth FlywayV15 auth_device_preferences(selector_hash bytea SHA256 PRIMARY KEY,theme nullable,revision bigint,expires_at); no account/SID/IP/UA. Runtime SELECT/INSERT/UPDATE/DELETE ACL.
- TransactionTemplate REQUIRED holds canonical identity/session admission locks through preference commit, then locks device row. Limited expired-context cleanup default500/batch (max5000), hourly ShedLock job.
- Single-source Java contract -> runtime OpenAPI3.1 -> mobile-core generated auth.ts. Required nullable theme encoded as string|null plus allowed-values pattern (Swagger3.1 ignored nullable=true). Explicit RFC9457 schemas for GET401/503 and PUT400/401/409/503.

## Verification
- /tmp/device-api-checks-20261024-3.log: combined affected command PASS2m08s. Auth DevicePreferencesHttpIT6, SameSiteCookieContractIT4, OpenApiSnapshotIT1, Auth filter9, Gateway filter57 plus existing ArchUnit/naming guards. Observed XML counts were read before later snapshot-only tasks replaced the integration-test report directory.
- Command: source /workspace/rct-pr3-setup/env.sh; bash gradlew :services:auth-service:auth-app:integrationTest --tests '*DevicePreferencesHttpIT' --tests '*SameSiteCookieContractIT' --tests '*OpenApiSnapshotIT' -Popenapi.snapshot.update=true :services:auth-service:auth-app:test --tests '*JwtAuthenticationFilterPurposeTest' :services:api-gateway:test --tests '*JwtAuthenticationFilterTest' --max-workers=2 --no-daemon --console=plain --continue
- HTTP6 proves account switch/independent jars/persistence, no-cookie-auth, revoked/stale/bootstrap/terminal readonly-grant admission, expiry/context/validation/redaction, simultaneous CAS+unknown-result readback, unavailable preference table and deferred commit failure rollback(no ACK), real PostgreSQL blocked PUT admission locks vs concurrent logout through commit. Real Auth migrations+restricted DB runtime principal+PostgreSQL/Redis Testcontainers with separate random ports; stand unchanged.
- /tmp/device-api-openapi-20261024-correction.log: snapshot-only PASS1m20s after null/required/error annotation correction; no behavior-suite repetition.
- /tmp/device-api-final-artifacts-20261024.log: final snapshot-only + own Auth bootJar PASS1m14s after completing remaining multi-line PUT error annotations.
- /tmp/device-api-generated-20261024.log: auth type generation and --check PASS; JSONSchema2020-12 verifies null/light/dark/system accepted, invalid theme/missing theme/numeric revision rejected, all declared errors use RFC9457 ErrorResponse. Source snapshot SHA256530af77254fb426aff6a008c03895e95f5812a2006906d730e91d73c5056ffb4.
- Scoped AOSP formatter and git diff --check PASS.

Corrections: initial behavioral Gradle attempt stopped at existing explicit ArchUnit contract allowlist (Auth filter9 already PASS), fixed by registering DevicePreferencesApi. HTTP fixture terminal readonly account changed to real bootstrap protocol before its first run. No runtime product defect occurred. OpenAPI metadata fixed after generated-type inspection. Two extra snapshot-only passes are confined to those contract defects. Initial bash-wrapper/default-sandbox socket failures ran no tests; resolved with bash gradlew and network-enabled sandbox.

## Local runtime artifact
/workspace/rct-worktrees/device-api/services/auth-service/auth-app/build/libs/auth-app-0.1.0.jar
SHA2561a802a0d6e9870dbcdc06179d884858170ae366ddbc7d25e5b3c584237321e92
Built final Java source before final commit; subsequent writes generatedfiles+commit only. Root/runtime_stand own integration, local rollout and revision comparison. No production migration/deploy or GitHub workflows run. Secure __Host cookie intentionally preserved: browser validation needs compatible secure origin/local proxy, plain HTTP is not a reason to weaken cookie security.

## Inventory (no deletions)
- M /workspace/rct-worktrees/device-api/docs/openapi/auth.json
- M /workspace/rct-worktrees/device-api/frontends/mobile-core/src/api/generated/auth.ts
- M /workspace/rct-worktrees/device-api/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java
- M /workspace/rct-worktrees/device-api/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/DevicePreferencesApi.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/DevicePreferencesResponse.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/UpdateDevicePreferencesRequest.java
- M /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/DevicePreferencesController.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/device/DevicePreferencesCleanupJob.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/device/DevicePreferencesException.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/device/DevicePreferencesService.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/DevicePreferencesExceptionHandler.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/main/resources/db/auth-migration/V15__auth_device_preferences.sql
- M /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/arch/AuthApiContractTest.java
- M /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java
- A /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/DevicePreferencesHttpIT.java
- M /workspace/rct-worktrees/device-api/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/support/AuthDatabaseFixture.java
