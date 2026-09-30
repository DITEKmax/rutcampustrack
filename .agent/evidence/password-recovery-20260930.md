# Password recovery — scoped implementation and evidence

Status: stable S3 implementation candidate, pending fresh independent Sol high review and root integration acceptance. Do not mark release-ready before those gates.
Owner decision: GO resumed on 2026-09-30. One writer in this assigned worktree; no children; Terra prohibited.
Worktree: .agent/worktrees/headman-assistants-delivery-20260922
Branch: codex/password-recovery-20260930
Base revision: 650e481a9fe68add76e38b9b11467b734223744b
Contract: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/password-recovery-ui-contract.md
Canonical routing rules: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md, SHA-256 397C1B0984648CA2EE7066DEFCB5DEE797DCC546B3C760443012F817ECFAD976. The worktree copy has SHA-256 878853C528ED1F3C86875B055205EEEB039718E794D7490C953BEE33E00F8D64 and is stale; canonical source-checkout RULES/CURRENT and the root packet were read and take precedence. Applied project skills: rct-source-resolution and rct-verification. Read services/AGENTS.md and tests/AGENTS.md.

## 1. Goal

For JS-SYSTEM-03/04, a user requests a purpose-bound password_reset OTP from the PWA using login or from Telegram using /reset, verifies without an existing session, chooses a new password without the old password, then returns to login. A login OTP cannot reset a password; reset proof cannot issue a session; successful completion revokes every active session.

## 2. Context and evidence

- Existing OtpService owns the login OTP flow and Redis atomic one-use consumption. AuthService.changePassword requires an authenticated current session and old password.
- Existing Academic password_reset_tokens schema is sufficient. V24 supplies auth_sessions and append-only account_security_events; no Academic migration is needed.
- Reset OTP/event, opaque ticket, ticket consumption, credential replacement, session revocation, and PASSWORD_CHANGED security event are purpose-specific. The public service path is exercised with canonical Academic V1..V24 migrations plus real PostgreSQL, Redis and RabbitMQ Testcontainers.
- Root’s accepted UI flow is separate. No frontend files or generated/proto APIs were changed.
- Initial worktree inventory showed three unrelated untracked evidence folders. They remain untouched and are excluded below.

## 3. Relevant scope

- Auth API contract and service, purpose-aware OTP event, Redis account-window budgets, password reset ticket, atomic credential/session transaction and focused auth checks.
- Narrow gateway allowlist and per-IP rate limits for request, verify and complete.
- notification-bot /reset flow, purpose-specific OTP message, reset HTTP client, password.changed notification, event schemas and focused tests.
- No frontend, proto, general notification framework, Academic schema, deployment or production migration changes.

## 4. Required behavior

- Request accepts exactly one of login or telegramId and returns the same generic 202 shape for known and unknown identities. Login resolves to the bound Telegram account; known login/Telegram aliases share account budgets; unknown identities use stable hashed buckets; the gateway applies per-IP limits.
- OTP purpose is password_reset, one-use, expires in 120 seconds, and allows at most three account-window attempts per five minutes. Request/send throttling and failed-verification limits cannot be reset by requesting a fresh challenge. TTL and remaining attempts come from the server.
- Verification returns only an opaque single-use expiring reset ticket; only its hash is stored. It never creates access or refresh credentials.
- Completion applies existing password policy and atomically consumes the ticket, updates the credential, revokes every active session and writes PASSWORD_CHANGED. It succeeds when revocation affects zero rows. Account status remains unchanged.
- Reset responses are no-store. Responses and logs do not include secret proofs. Request responses do not disclose account existence.
- password.changed contains only telegram_id and publishes after the credential transaction commits. Producer publication is best effort because Auth has no shared durable outbox/retry; external Telegram delivery is not claimed as verified.

## 5. Constraints

- Public paths are /api/auth/password-reset/request, /verify and /complete. Service paths are the corresponding /auth/password-reset routes.
- Request: POST {login} XOR {telegramId} -> generic 202 {challengeId,ttlSeconds}.
- Verify: POST {challengeId,code} -> 200 {resetTicket,expiresInSeconds,attemptsRemaining}; OTP_INVALID=400, OTP_EXPIRED=410, OTP_RATE_LIMITED=429.
- Complete: POST {resetTicket,newPassword} -> 204; RESET_TICKET_INVALID=410; password policy violation=400.
- Existing event topology remains synchronous Spring DomainEventListener -> rut-uit.events fanout; notification-bot.events consumes it and NACKs handler failures to its DLQ. Do not claim durable producer retry or successful Telegram delivery.
- Preserve all unrelated changes. No push, merge, deploy, production migration, data deletion or external communication.

## 6. Existing patterns

- OTP generation and atomic Redis scripts: OtpService.
- Credential/session transactions and security events: CredentialSessionTransactionPort, SessionLifecycleService and JdbcSessionAuthority.
- Existing password policy: PasswordPolicy.
- Reset no-store coverage, including error responses: PasswordResetNoStoreFilter. Typed reset errors: GlobalExceptionHandler.
- Bot event and delivery patterns: otp.requested, DomainEventListener, event_dispatcher and existing notification handlers.

## 7. Acceptance criteria

- Login OTP retains login behavior and cannot reset. password_reset OTP cannot pass the login verification route.
- Invalid, expired, exhausted and replayed proofs are denied; account attempts persist across new challenges; concurrent completion has one winner.
- No authentication or old password is required to reset. Verification creates no session. Completion changes the credential, revokes all prior active access/refresh sessions and creates no replacement session; success is allowed when there are zero active sessions (historical revoked rows may remain).
- Password policy is enforced and account status is unchanged.
- Known and unknown requests have indistinguishable public responses; request/send and verify budgets remain bounded.
- Event schema and bot formatting preserve purpose and server-supplied TTL/attempt metadata. password.changed has only telegram_id and is emitted after commit.
- Actual Telegram delivery remains deferred and unverified.

## 8. Verification and runtime evidence

The first resumed analysis read the saved JUnit XML before changes. Both request variants had returned 500 because integer Lua arguments were passed to StringRedisSerializer. The apparent zero-session failure was a raw total-row assertion: auth_sessions keeps revoked history. This diagnosis and the exact next command were sent to root before correction.

The first corrected three-case run proved request serialization and account-window persistence. The main reset and zero-active-session scenarios reached their later Cache-Control assertions and saw “no-store, no-store”; both the filter and controller set the same directive. The duplicate directive still made responses non-cacheable; this exact-header assertion stopped the scenarios before their remaining checks. The controller duplicate was removed, and the filter remains the single no-store source for success and error responses. The Lua wrapper now supplies String values through an Object[] without the compiler varargs warning.

| Check | Source state / session | Exact command | Exit | Result |
|---|---|---|---:|---|
| Prior full AuthOtpFlowIT evidence, read from saved XML | Earlier WIP, session 36520, JDK 21.0.10 | .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report "-Dorg.gradle.java.compile-classpath-packaging=true" :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.AuthOtpFlowIT | 1 | Four cases; one existing login OTP passed, three recovery failures. XML showed the shared Redis serializer ClassCastException and historical-row-count assumption. |
| Corrected focused recovery run | Before the no-store normalization, session 4496 | .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report "-Dorg.gradle.java.compile-classpath-packaging=true" :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.AuthOtpFlowIT.passwordResetIsPurposeBoundAtomicAndRevokesEverySession --tests ru.rutcampustrack.auth.integration.AuthOtpFlowIT.passwordResetFailedAttemptsSurviveANewChallenge --tests ru.rutcampustrack.auth.integration.AuthOtpFlowIT.passwordResetCompletesWhenTheAccountHasNoActiveSessions | 1 | Three cases completed. Account-wide failures survived a new challenge and passed. Other two cases reached the duplicate no-store exact-header assertion; no Redis 500 remained. |
| Final focused reset runtime | Final product source, session 92297, JDK 21.0.10 | .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report "-Dorg.gradle.java.compile-classpath-packaging=true" :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.AuthOtpFlowIT.passwordResetIsPurposeBoundAtomicAndRevokesEverySession --tests ru.rutcampustrack.auth.integration.AuthOtpFlowIT.passwordResetCompletesWhenTheAccountHasNoActiveSessions | 0 | BUILD SUCCESSFUL, 2/2 passed with canonical Academic V1..V24, PostgreSQL, Redis and RabbitMQ. Covers purpose isolation, generic request, concurrent one-winner completion, security event/schema, credential change, all active session revocation, no session issued by verification, zero-active-session completion, ticket replay denial and subsequent login. Account-window test passed in session 4496 and was not rerun. |
| Historical password.changed schema contract test | Earlier source before final no-store/serializer correction | .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report "-Dorg.gradle.java.compile-classpath-packaging=true" :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.events.PasswordChangedContractTest | 0 | Earlier BUILD SUCCESSFUL; not rerun because it is schema-only and unchanged. Final integration run compiled current auth source and test sources. |
| Event JSON syntax | Schema unchanged after check | python -m json.tool event-schemas/password.changed.json | 0 | Parsed successfully; schema rejects additional payload fields. Runtime event validation also passed in the final atomic reset scenario. |
| Bot source/test syntax | Bot files unchanged after check | python -m compileall -q services/notification-bot/bot services/notification-bot/tests | 0 | Python sources and tests compiled. |
| Bot behavioral tests | Host Python lacks pytest | python -m pytest -q tests/test_otp_requested.py tests/test_otp_verified.py tests/test_password_reset_handler.py tests/test_login_handler.py (cwd services/notification-bot) | 1 | No pytest module; no bot behavior tests ran. Existing CI uses Python 3.12, installs requirements.txt and requirements-test.txt, then runs pytest tests/ -v from services/notification-bot. Do not install local dependencies for this handoff. |
| Final source whitespace check | Final product source before staging | git diff --check | 0 | No whitespace errors. Git printed its existing user global-ignore permission warning and line-ending advisory for otp_requested.py; neither is connected to a request criterion. |
| Container cleanup | After sessions 4496 and 92297 | docker ps --all --filter label=org.testcontainers=true --format '{{.ID}} {{.Image}} {{.Status}}'; docker ps --format '{{.ID}} {{.Image}} {{.Status}}' | 0 | Both outputs empty. No Testcontainers-labeled or running Docker containers remained; none were manually stopped or removed. Both Gradle sessions terminated. |

## 9. Do not

- Do not refactor all authentication or alter login OTP semantics outside the purpose-isolation seam.
- Do not ask for a current password, issue a session from reset proof, persist raw reset proof/password, or reveal account existence.
- Do not alter frontend, proto, generated types, Academic schema, unrelated docs or foreign evidence. Do not claim durable notification or real Telegram delivery.
- Do not change code because of WARN/ERROR without linking it to this contract and reproducing the criterion failure.

## Diff inventory

Changed/created files in this scope:

.agent/evidence/password-recovery-20260930.md
event-schemas/otp.requested.json
event-schemas/password.changed.json
services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java
services/api-gateway/src/main/resources/application.yml
services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/PasswordResetApi.java
services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordResetCompleteRequest.java
services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordResetRequest.java
services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordResetRequestResponse.java
services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordResetVerifyRequest.java
services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordResetVerifyResponse.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/PasswordResetNoStoreFilter.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/PasswordResetController.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/event/OtpRequestedEvent.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/event/PasswordChangedEvent.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/PasswordResetException.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/PasswordResetService.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java
services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/port/CredentialSessionTransactionPort.java
services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/events/OtpRequestedContractTest.java
services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/events/PasswordChangedContractTest.java
services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java
services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java
services/notification-bot/bot/__main__.py
services/notification-bot/bot/consumers/event_dispatcher.py
services/notification-bot/bot/handlers/__init__.py
services/notification-bot/bot/handlers/password_reset.py
services/notification-bot/bot/notifications/otp_requested.py
services/notification-bot/bot/notifications/otp_verified.py
services/notification-bot/bot/notifications/password_changed.py
services/notification-bot/bot/services/auth_http_client.py
services/notification-bot/bot/services/otp_message_tracker.py
services/notification-bot/tests/test_event_dispatcher.py
services/notification-bot/tests/test_otp_requested.py
services/notification-bot/tests/test_otp_verified.py
services/notification-bot/tests/test_password_changed.py
services/notification-bot/tests/test_password_reset_handler.py
services/shared/shared-events/src/test/java/ru/rutcampustrack/shared/events/EventSchemaCoverageTest.java

Preserved and excluded: .agent/evidence/group-notification-history-20260925/, .agent/evidence/headman-trend-detail-20260925/, .agent/evidence/notification-preferences-roles-20260925/.

## Limits and handoff

- The runtime evidence is service-level, not a live gateway-to-bot deployment. Gateway allowlist/rate configuration is source-reviewed, not exercised through the gateway.
- Python behavior tests remain a gap because pytest is absent on this host. Existing CI job/command is the intended environment; no dependency install was performed here.
- password.changed producer publication is best effort, with no durable Auth outbox or producer retry. Telegram network delivery was not exercised and remains owner-deferred.
- Product source is frozen for fresh independent Sol high review of the full backend/auth/bot contract diff. Root retains final integration acceptance. No full suite, push, merge, deploy or production data operation was run.
