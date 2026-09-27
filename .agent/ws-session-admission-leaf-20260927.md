# WS session admission — evidence

## Scope and contract

S3 leaf implementation in `codex/ws-session-admission-20260927`, based on `e6e6dad2bc8e598cf7f572742307c870aa1f7e54`. Canonical contract: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\orchestration-v2\evidence\2026-09-27-delivery\ws-session-admission-contract.md`. Main `RULES.md` SHA-256: `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.

Changed only Auth WS ticket/internal admission and Notification WebSocket binding/delivery lifecycle plus focused tests. Session authority continues to come from `SessionPrincipal` and `AuthService.admit`; tickets contain no bearer. Notification binds only handshake-established identity, checks live admission on SUBSCRIBE and each outbound MESSAGE, fails closed on unknown/outage/revocation, and bounds both raw transports and STOMP bindings to 8192 each. Normal and abnormal disconnect paths evict bindings and release capacity. Existing topic checks and client SEND denial remain.

## Acceptance evidence

- Issue/consume preserves `userId`, `sid`, `sv`, `rv` and selected role/status/group/headman/read-only identity. Consume is single-use and performs live admission; stale authority returns 401 and authority outage remains 503.
- Shared `WsSessionAdmissionRequest` and `InternalWsSessionAdmissionApi` define `POST /internal/auth/admit-ws-session`; live admission returns 204/no-store. The DTO remains the Java contract source.
- Ticket handshake stores only admitted identity and a transport binding id. CONNECT requires an already-registered transport; SUBSCRIBE retains exact private/group/headman checks and rechecks live authority. Outbound MESSAGE checks the same binding before sending.
- Registry admission denial closes and evicts without retry; raw and STOMP capacities are independently bounded; lifecycle regression tests cover cap rejection, disconnect release, denial eviction, and outbound fail-closed.
- `WsTicketIT` checks complete identity, successful live admission, logout denial, and stale role grant denial while burning the ticket. The stale test uses a `user_role_grants` update (the authority trigger advances `roles_version`) rather than logout, because logout intentionally deletes unused Redis tickets before consume.

## Checks and evidence

Every Gradle call used `--system-prop=org.gradle.java.compile-classpath-packaging=true --max-workers=1`. After the pre-existing problems-report destination collision, `--no-problems-report` was used on subsequent approved checks; local `gradlew --help` confirmed that switch (help exit 0).

| Check | Result |
|---|---|
| First non-escalated Auth `:services:auth-service:auth-app:integrationTest --tests "ru.rutcampustrack.auth.integration.WsTicketIT"` | Exit 1 before compilation: sandbox `AccessDeniedException` on generated `shared-web-api-0.1.0.jar`. The referenced `ConsumeWsTicketRequest.java` source exists; no dependency change was made. |
| Escalated full `WsTicketIT` | Exit 1: compilation passed; 7/8 tests passed. The stale test expected 401 after `/auth/logout`, but logout's existing cleanup removed the ticket, so consume returned 404. Gradle also reported `FileAlreadyExistsException` while writing an existing ignored `build/reports/problems/problems-report.html`. |
| Escalated targeted `WsTicketIT.consumeTicket_revokedBeforeConsume_isRejectedAndBurned` after test correction | Test XML: 1 test, 0 failures. Gradle command exit 1 solely on the same existing problems-report destination collision. The PASS test was not rerun just to change Gradle report status. |
| Scoped Notification unit command for `TicketHandshakeInterceptorTest`, `SubscriptionAuthInterceptorTest`, `WsSessionBindingRegistryTest`, `WsSessionAdmissionOutboundInterceptorTest` | Initial exit 1: 17 tests, 3 failures in `SubscriptionAuthInterceptorTest` because its fixture omitted the newly required raw transport registration. The fixture was corrected; only `SubscriptionAuthInterceptorTest` was rerun. |
| Targeted `SubscriptionAuthInterceptorTest` with `--no-problems-report` | Exit 0; XML: 6 tests, 0 failures. Other selected classes passed in the earlier 17-test run. |
| First `:services:notification-service:notification-app:integrationTest --tests "ru.rutcampustrack.notification.ws.StompIntegrationIT"` | Exit 1: all 3 tests failed at Spring context creation. XML traced the cause to `WsSessionBindingRegistry.<init>()` (second test-only constructor meant Spring needed an explicit production constructor). |
| Same `StompIntegrationIT` after `@Autowired` on the production constructor, with `--no-problems-report` | Exit 0; XML: 3 tests, 0 failures. |
| `:services:auth-service:auth-app:bootJar :services:notification-service:notification-app:bootJar` with `--no-problems-report` | Exit 0; both artifacts built. |
| `git diff --check` after source corrections | Exit 0; Git only reported normal LF→CRLF normalization notices for two modified test files. |

The ignored Gradle problems report remains in this worktree: reading/removing that exact file returned Windows `Access denied`, including the authorized escalation. This did not block later checks using the locally confirmed `--no-problems-report` option.

## Runtime, diff, and limits

Author runtime evidence: not run. Root owns the combined two-session Auth+Notification runtime acceptance (revoke one active socket, preserve delivery to the second, then reconnect). The executed STOMP integration tests cover the real handshake/CONNECT/subscription/server-send paths, not that two-session revoke scenario.

Product/test diff (23 files):

- Auth contract: `InternalWsTicketApi.java`, `ConsumeWsTicketResponse.java`, new `InternalWsSessionAdmissionApi.java`, new `WsSessionAdmissionRequest.java`.
- Auth app: `InternalWsTicketController.java`, `WsTicketController.java`, `WsTicketService.java`, new `InternalWsSessionAdmissionController.java`, `WsTicketIT.java`.
- Notification app: `build.gradle.kts`, `SubscriptionAuthInterceptor.java`, `TicketHandshakeInterceptor.java`, `WebSocketConfig.java`, `WsTicketClient.java`, new `WsSessionAdmissionOutboundInterceptor.java`, new `WsSessionBindingRegistry.java`, new `WsSessionBindingWebSocketDecoratorFactory.java`, `SubscriptionAuthInterceptorTest.java`, `TicketHandshakeInterceptorTest.java`, `WebSocketConfigTest.java`, `StompIntegrationIT.java`, new `WsSessionAdmissionOutboundInterceptorTest.java`, new `WsSessionBindingRegistryTest.java`.
- Evidence: this file only. Existing unrelated `.agent/` untracked files were preserved and excluded from the scoped commit.

Already-dispatched in-flight bytes are not recalled, per contract. No product UI, academic, migration, deployment, or unrelated warning changes were made. Independent fresh Sol high review and root-owned runtime acceptance remain pending.
