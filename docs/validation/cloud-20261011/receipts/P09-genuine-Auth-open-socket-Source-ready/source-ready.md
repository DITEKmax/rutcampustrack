# P09 real Auth/open Notification socket — Source ready

Scope: ONE missing finite P09/BOUNDARY-43 atom. Parent P09 remains UNKNOWN. Actual native compilation/test/transport effect/cleanup result: NOT_RUN. No product runtime PASS or failure inferred.

Worktree `/workspace/rct-worktrees/p09-auth-socket-join-20261013`, branch `codex/p09-auth-socket-join-20261013`, frozen parent86a9c859d361399b2c0deeca0ec20f2c950ec698. Normal semantic commit `a7648c62e34eb75776603ffcc171e8aaf2f54f42`. Clean own worktree after commit; exact inventory2paths/329insertions. Root separately approved nontransitive dependency correction before edits.

## Source inventory/seal

- Created test `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/RealAuthOpenSocketLogoutIT.java`: SHA256 `214c60272df3cbb471bb4cba4fe974c67d5e285d15517401517dc57a5831e607`,324lines.
- Modified checking config `services/auth-service/auth-app/build.gradle.kts`: SHA256 `282a4efc96471a80d07adcc68bd21568d350a48bf7dab29e58fc130b2b903eaf`. Notification project test-only dependency has `isTransitive=false`; explicit existing-BOM spring-boot-starter-websocket supplies required WS classes while avoiding broad Notification Mongo/webpush/data auto-configuration on all Auth tests. No new version/locks/main dependency.
- No other product/test/config/API/proto/generated/migration/workflow/shared-doc source changes. Three Compose files and PublicKeyProvisioningTest reserved to key author, untouched. No own children. No B013 work or counterexample preparation/execution.

## Actual-source effects the test requires

Exact method `realAuthLogoutStopsNewPrivateDeliveryOnSameOpenSocketWhileControlStillReceives` explicitly uses AuthApplication and existing AbstractIntegrationTest PG16/Redis7/migrations/test profile. Nested @TestConfiguration is excluded from Auth component scan and is explicitly registered only in a separate Notification servlet context. Seven actual production WS components are imported; production WsTicketClient calls actual Auth HTTP. Neither auth authority nor logout/admission is mocked or replaced by a map.

Actual login for two seed actors→distinct user IDs plus PG nonrevoked session metadata→real ticket issuance/HTTP consumption via production WS handshake→two native STOMP sockets/own recipient topics→both baseline private markers delivered with no cross-recipient marker→A still connected→HTTP logout204 plus actual durable PG revoked_at→next recipient-specific private messages→B delivered/still connected/nonrevoked while same A transport closes and no after marker/cross marker arrives. A successful REST response alone cannot satisfy the socket/output assertions. Existing source-sensitive check is not yet run; counterexample scope/grant remains separate.

No arbitrary sleeps. Existing Awaitility pattern requires positive delivery to establish readiness, then bounded actual A disconnect plus B delivery. Per connect/deferred result5s, per delivery/closure await5s, production WsTicketClient connect500ms/read1500ms. Product30s ticket TTL and security clock unchanged. No retry/reconnect after logout. Private publication is through genuine SimpMessagingTemplate/simple broker, not a Rabbit event producer: no outbox/Rabbit/history/browser/TLS/edge/production isolation proof claimed.

## Cleanup and privacy Source

try-with-resources closes Notification context; nested finally independently attempts socket disconnects, client.stop, and each still-admitted Actor logout. Own temporary RSA directory is removed in @AfterAll, following existing Auth pattern. Inherited fresh nonreused PG/Redis/Testcontainers are owned by the normal existing Testcontainers lifecycle; Root runtime observer must verify actual terminal births/container absence. No runtime resources created during Source preparation except assigned checkout and own safe receipts.

Secret configuration is a RAM MapPropertySource initializer, not command-line argv; no URL tickets, token/body/key logging/serialization introduced. Actor.toString is explicitly redacted. Assertions emit status/booleans/private synthetic fixed marker sets only; no token response/claims assertion dump. Generated synthetic keys are never read into receipts. Existing SAFE outcome collector must project eventual raw test artifacts privately; no raw XML/log exported here.

## Verification performed

Read actual accepted P09/BOUNDARY-43 originals and current independent split-evidence disposition; full task restore/AGENTS/RULES/workflow before action. Scoped Git diff-check PASS; exact inventory/status/hash inspection completed; ordinary git add/commit on only assigned paths exited0 without bypass switches. Actual default common hooks/pre-commit file is absent and core.hooksPath unset: hook execution/Gitleaks/parser/formatter/compile/integration results NOT_RUN, not claimed PASS. No shared hook install/config change.

## Native plan for Root review/fresh grant

Reuse original source-plan scope. One sequential heavy lease, JDK21/existing Gradle8.12 wrapper, own build/generated outputs. Proposed tasks in one sequential invocation: `:services:notification-service:notification-app:compileJava :services:auth-service:auth-app:compileJava :services:auth-service:auth-app:compileTestJava :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.RealAuthOpenSocketLogoutIT --no-daemon --max-workers=1 --system-prop=org.gradle.java.compile-classpath-packaging=true`. Relevant source formatter/check may precede this under separately granted standard checking command. Existing unchanged CI/Coverage Auth *IT jobs automatically include the class; no gate config changes.

Runtime: two fresh nonreused postgres:16/redis:7-alpine containers and existing Ryuk lifecycle helper as needed; no shared named volumes, queues, fullstand or foreign data. Auth academic_db with existing synthetic fixtures/Flyway owns sole fresh schema. Random mapped host PostgreSQL/Redis ports plus2random loopback servlet ports in one test JVM. New RSA temp directory under JVM temp path. No Rabbit/Mongo/Academic/Schedule/browser/edge/TLS data required. Stand physical disk deficit is not resolved by this plan; no prune/delete/cap lowering. Exact image cache availability, numeric capacity/lease/caps/deadline/process/container births and final cleanup need fresh Root metadata/observer binding before any native operation; not guessed from Source.

Next: independent Source review (test effect/recipient barrier/cleanup/dependency isolation), apply findings within same2paths; then scoped existing formatter/compile/ONE real join run under fresh Rootgrant+normal approval. Separately review/request outbound-only scratch counterexample and exact restored GREEN; no B013/grants mutation. Root integrates and obtains current CI before any matrix promotion.
