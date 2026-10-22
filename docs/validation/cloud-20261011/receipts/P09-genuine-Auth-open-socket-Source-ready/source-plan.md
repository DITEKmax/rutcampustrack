# P09 source plan — awaiting Root scope approval

Status: SOURCE_ONLY / native NOT_RUN. Requested Sol6.1/high/forknone; actual metadata UNKNOWN. No own children. Root86a9c859d361399b2c0deeca0ec20f2c950ec698; CI producer1742d1e806faa5c39c6d182a3554daa8f4239801. No product/test/config files changed; no Docker/Java/Gradle/browser/network/API/private artifact execution.

## Accepted atom and originals

TASK-CONTRACT.md P09: already-open WebSocket after revoke receives no new private delivery. Matrix original `receipts/matrix-review/required-boundary-rows.json` BOUNDARY-43 requires real Auth Redis ticket plus actual Notification socket/two users/revoke/event. Current independent `receipts/current1742-P07-P09-qualified-disposition/REPORT.md` explicitly identifies the missing genuine Auth logout→open socket join and preserves parent UNKNOWN. Existing race/actual30s TTL/logout-before-consume methods remain adequate; do not duplicate them.

Read full task restore TASK-CONTRACT/CURRENT/decisions/questions/resources, packet, AGENTS root/services/tests, RULES and agent-workflow before Source planning. Cloud runtime skill read; environment-status tool discovered but not called under Source-only/no-API authority. No native resource readiness inferred.

## Minimal proposed inventory

Own frozen86a9 WT/branch: `/workspace/rct-worktrees/p09-auth-socket-join-20261013`, `codex/p09-auth-socket-join-20261013` (not yet created).

1. CREATE `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/RealAuthOpenSocketLogoutIT.java`. One meaningful method, extends existing package-local AbstractIntegrationTest; genuine Auth HTTP/PG/Redis lifecycle, secondary narrow Notification Spring socket context in the same test JVM. Explicit AuthApplication test configuration avoids ambiguity from both Boot application classes. Nested Notification test configuration imports exactly the production WS stack used by PrivateWsTransportIT; no mock WsTicketClient/authority/logout/admission.
2. MODIFY only with Root explicit config ownership grant: `services/auth-service/auth-app/build.gradle.kts`, one `testImplementation(project(":services:notification-service:notification-app"))`. This is test scope only, no main dependency/API/schema/lockfile change. Auth already has Gateway testImplementation and real cross-module ExportCrossLayerBoundaryIT. Notification main depends only on Auth API, so this does not introduce a main project cycle.
3. Own Source/evidence only under `/workspace/scratch/rct-validation/p09_auth_socket_join`. Root remains shared task-doc/grant/integration owner. Three Compose files and PublicKeyProvisioningTest.java belong to notification key-isolation author and are untouched. No product Java/API/proto/generated/migration/lockfile/workflow paths requested.

## Source-grounded test flow

Reuse AbstractIntegrationTest genuine Auth RANDOM_PORT, PostgreSQL16/Redis7 and canonical Academic Flyway migrations; existing test profile/config and synthetic student/teacher seed actors have different IDs. Generate own synthetic RSA key directory using existing ExportCrossLayerBoundaryIT pattern; no existing key read. Security clock remains real current time; no fixture teaching clock/TTL override.

Start an explicitly bounded second servlet context with Notification WebSocketConfig, TicketHandshakeInterceptor, WsTicketClient, SubscriptionAuthInterceptor, WsSessionBindingRegistry, WsSessionAdmissionOutboundInterceptor, WsSessionBindingWebSocketDecoratorFactory and WsTicketProperties. Follow narrow PrivateWsTransportIT context exclusions; also exclude irrelevant datasource/JPA/Flyway/security/management/gRPC auto-config as required by actual Auth test classpath. Point its production WsTicketClient at the genuine Auth random loopback HTTP port with the same synthetic internal-issuer secret. No HttpServer/map authority fixture.

For two distinct admitted actors A and control B: actual /auth/login and /auth/ws-ticket, native STOMP /ws/websocket with ticket header, subscribe each own /topic/user/{id}. Observe both baseline private messages before assuming subscribe readiness, using bounded Awaitility just as existing PrivateWsTransportIT. Check no recipient receives the other's distinct marker. Keep the same A socket open; execute actual /auth/logout for A, separately assert HTTP204 and PostgreSQL durable session revocation metadata. Publish fresh recipient-specific after markers through production SimpMessagingTemplate/broker. Await B receives its marker and A transport disconnects; assert A received no after marker, B remains connected, and neither crosses recipient markers. Thus HTTP success alone cannot pass.

Deterministic observable completion (positive B delivery plus actual A close), no arbitrary sleeps and no reconnect. Cleanup finally closes both clients/context, logs out any still-admitted test sessions, deletes only own generated synthetic key directory; existing Testcontainers lifecycle owns disposable DB/Redis. No synthetic token/ticket/secret/key/body serialization or raw XML/log output; use existing safe outcomes projection on actual run.

Qualification: genuine Auth authority/JDBC/logout/ticket/HTTP client and genuine Notification native socket/broker/outbound guard; direct server-side private publish bypasses Rabbit/outbox/history producer. Plain loopback HTTP/ws follows existing integration transport, and proves neither production TLS/edge/container isolation nor browser delivery. Whole P09, foreign socket attempts and administrator capability stay UNKNOWN outside this one atom.

## Native services/resources, deferred until fresh grant

One sequential heavy lease. Existing JDK21 / Gradle wrapper8.12. One test JVM running two servlet contexts (random ephemeral loopback ports), Auth datasource/cache clients, Notification in-memory STOMP broker; no fullstand/nativeMongo/Rabbit/browser/renderer/Academic process/edge. Two disposable Testcontainers: postgres:16 with database academic_db and existing synthetic test credentials; redis:7-alpine, non-reused, random mapped host ports. Testcontainers/Ryuk helper if required by existing runtime, inventory/cleanup must be included in fresh plan. No shared named volumes/queues/DB/data reset or production migrations. Flyway executes existing canonical migrations only in own fresh DB. Own build/generated outputs and ephemeral RSA key directory; no real/private retained materials.

Numeric RAM/disk/PID/wall/cleanup caps and Testcontainers names/births must be chosen from fresh Root resource observer/native plan before execution, not invented from Source. Current shared stand disk constraint remains; no cap lowering/prune/data deletion. Existing images/cache availability unmeasured. Loopback plaintext is a declared finite integration qualification, no TLS disable/change to production.

## Sufficient verification after review/grant

From own WT existing wrapper tasks: Notification compileJava, Auth compileJava/compileTestJava, then `:services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.RealAuthOpenSocketLogoutIT` sequentially in one invocation, `--no-daemon --max-workers=1`; use existing compile-classpath packaging system property where needed. No blanket suite. Relevant formatter/static Source check first, then exact one nonempty test result/current Source hashes, source-sensitive relevant counterexample and independent review. Current CI/Coverage run this class automatically under unchanged existing Auth IT matrix (*IT); no workflow change requested.

Counterexample scope must be separately reviewed/granted: temporary own scratch-only outbound generic MESSAGE bypass demonstrates the new test fails at post-logout no-delivery/closure, then exact restore and targeted GREEN. No Auth rights/grants/B013 mutation, no mutation executed/prepared now.

Next: Root approves/rejects exact two-path implementation inventory and test-only dependency ownership, then author creates assigned frozen WT and implements the single test. Native remains NOT_RUN until separate fresh Root grant and normal platform approval.
