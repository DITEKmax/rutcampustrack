# Password recovery review corrections — S3

Status: source correction candidate; Java verification is blocked before test execution by a Gradle `AccessDeniedException`. Keep this explicit for the bounded Sol recheck. Root owns integration and the later real API + PWA path.

Worktree: `.agent/worktrees/headman-assistants-delivery-20260922`
Branch: `codex/password-recovery-20260930`
Parent source revision: `5b42b4e111f78c45bb63d31a878f6a57c240cf18`
Canonical rules SHA-256: `397C1B0984648CA2EE7066DEFCB5DEE797DCC546B3C760443012F817ECFAD976`
Contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/password-recovery-ui-contract.md`

## Goal and accepted behavior

Finish the three bounded review corrections without changing the frozen password-recovery contract or login OTP behavior:

1. A locally denied empty-body gateway 429 keeps its existing generic Problem Details response. A nonempty auth-service 429 retains its `OTP_RATE_LIMITED`, `retryAfterSeconds`, `Retry-After`, and `Cache-Control: no-store` response.
2. Invalid bodies on all three public password-reset routes keep the shared RFC 9457 validation shape while omitting submitted values, including code, ticket, and password sentinels. Reset errors remain `no-store`.
3. `/reset` in group/supergroup chats tells the user in Russian to open a private bot chat and exits before the auth request or tracker write.

## Review findings and corrections

- Gateway review finding: `RateLimitProblemDetailsFilter.writeWith` replaced all 429 bodies. It now uses the generic body only when the response body publisher is empty; nonempty backend bodies pass through untouched. `setComplete()` still supplies the local generic response. The targeted test verifies both local-denial behavior and backend extensions/headers.
- Auth review finding: shared `GlobalExceptionHandler` serialized `FieldError.rejectedValue`. A controller-scoped advice now maps the same validation fields/messages with `rejectedValue = null`, the existing problem type/title/detail, and `Cache-Control: no-store`. A MockMvc request test covers request, verify, and complete validation errors with sentinels.
- Bot review finding: `/reset` could store a group chat id alongside a private Telegram destination. The handler now rejects non-private chats before reading the account id, invoking Auth, or storing tracker state; a parametrized test covers group and supergroup.

## Scope

Changed product/test files:

- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RateLimitProblemDetailsFilter.java`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitProblemDetailsFilterTest.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/PasswordResetValidationExceptionHandler.java` (new)
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/PasswordResetValidationExceptionHandlerTest.java` (new)
- `services/notification-bot/bot/handlers/password_reset.py`
- `services/notification-bot/tests/test_password_reset_handler.py`

This correction does not change the shared handler, rate values, auth purpose/budget rules, session transaction, request contract, UI, generated/proto APIs, or schemas. Three unrelated evidence folders were preserved and excluded: `group-notification-history-20260925`, `headman-trend-detail-20260925`, and `notification-preferences-roles-20260925`.

## Checks and evidence

All Gradle attempts used JDK `C:/Users/maksd/.jdks/ms-21.0.10`, `--no-daemon --no-parallel --max-workers=1 --no-problems-report`, and `-Dorg.gradle.java.compile-classpath-packaging=true`. They ran with default sandbox permissions, not escalation.

| Check | Exact command or session | Exit | Result |
|---|---|---:|---|
| Bot focused tests with repository default coverage config | `python -m pytest -q tests/test_password_reset_handler.py` from `services/notification-bot`, Python 3.12.14 venv | 1 | 4 tests passed; isolated-file coverage was 9.58%, below repository-wide `fail-under=50`. This is the global coverage wrapper, not a product assertion failure. |
| Bot focused tests without the unrelated global coverage threshold | `python -m pytest -q --no-cov tests/test_password_reset_handler.py` from `services/notification-bot`, Python 3.12.14 venv | 0 | 4 passed, including group and supergroup rejection. |
| Aiogram private chat type comparison | `python -c 'from aiogram.enums import ChatType; print(ChatType.PRIVATE == "private")'` in the same venv | 0 | `True`; the enum comparison used by the guard matches the installed aiogram type. |
| Initial two-target Gradle run, terminal session `64004` | `gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report "-Dorg.gradle.java.compile-classpath-packaging=true" :services:api-gateway:test :services:auth-service:auth-app:test --tests ru.rutcampustrack.gateway.ratelimit.RateLimitProblemDetailsFilterTest --tests ru.rutcampustrack.auth.controller.PasswordResetValidationExceptionHandlerTest` | 1 | Stopped before tests. It found an incompatible wildcard inference in the Gateway fallback; source was corrected with an explicit `Flux<DataBuffer>` type witness. Gradle also reported `AccessDeniedException` for `services/shared/shared-observability/build/libs/shared-observability-0.1.0.jar`. |
| Corrected two-target Gradle run, terminal session `38059` | Same focused command after the type correction | 1 | Type error was gone. `:services:api-gateway:compileJava` still failed on `AccessDeniedException` for the same up-to-date JAR; neither targeted test executed. Further identical Gradle retries are not justified. |
| JDK read diagnostic | `jar.exe tf <shared-observability-0.1.0.jar> *> $null` under JDK 21, default sandbox | 0 | The same JDK can read the artifact. PowerShell `OpenRead` also succeeded, and no Java process remained after Gradle exited. Root cause of Gradle's access failure remains unknown. |
| Source whitespace | `git diff --check` from assigned worktree | 0 | No whitespace errors. Git printed its existing global-ignore permission warning and Python CRLF advisories. |

Previously passing password-reset transaction/account-window scenarios from the parent implementation were not repeated because the correction does not touch those paths. They are evidence for the unchanged parent source only; this correction's two Java tests remain unverified until the build-access issue is resolved.

## Runtime proof pattern and limits

No new actual gateway-to-auth or API-to-PWA runtime was run in this correction. For root's disposable local API + PWA check without real Telegram, reuse the existing event fixture pattern in `AuthOtpFlowIT`: it binds a per-run test queue to `rut-uit.events` (lines 68–79, 119–130), requests a reset for the seeded disposable account, and consumes the `otp.requested` event in memory to pair `payload.challenge_id` with `payload.code` (lines 188–216). The test then submits that proof to the real reset endpoints. Apply the same temporary queue binding to the isolated local RabbitMQ used with the real API and UI, keep the proof in process memory, enter it once in the PWA, and do not print/log it. `AbstractIntegrationTest` is the existing disposable PostgreSQL/Redis fixture base; no Telegram credentials or Telegram delivery are needed for this proof.

Limits: `password.changed` publication remains best effort with no durable Auth outbox/retry, as stated in the frozen contract. Actual Telegram delivery is still deferred. The Python broad-suite unrelated fixture failure from the earlier check remains documented in `.agent/orchestration-v2/evidence/2026-09-27-delivery/recovery-bot-5b42b4e-check.md`; it was not rerun. No containers were started by either Gradle attempt because both stopped before tests. No push, merge, deployment, or production operation was performed.

## Review handoff

Provide the stable scoped diff, this evidence, and the frozen contract to a fresh Sol high reviewer for bounded recheck of the three findings. Root retains final integration and actual API + PWA runtime acceptance.
