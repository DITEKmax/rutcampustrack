# Password recovery local gateway 429 correction — S3

Status: source frozen after focused Java PASS; ready for bounded Sol recheck of the local-denial case. Root owns integration and later actual API + PWA runtime acceptance.

Worktree: `.agent/worktrees/headman-assistants-delivery-20260922`
Branch: `codex/password-recovery-20260930`
Parent revision: `866ee0def8da4d93afd6f1910487415ad8f08581`
Contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/password-recovery-ui-contract.md`

## Finding and required behavior

Sol's source recheck found that a locally denied `/api/auth/password-reset/verify` request still received the gateway's generic 429, without `extras.code=OTP_RATE_LIMITED` or `extras.retryAfterSeconds`. The frozen contract requires those fields for local and backend reset verification limits.

Only the generated local response for the exact reset-verify path is specialized. The route configuration in `services/api-gateway/src/main/resources/application.yml` (`auth-password-reset-verify`, lines 135–148) sets `requestedTokens=12` and `replenishRate=1` token/second. With an exhausted bucket, the conservative full refill is 12 seconds. The response body therefore reports `retryAfterSeconds: 12`, the `Retry-After` header is `12`, and `Cache-Control` is `no-store`.

The generic local 429 remains unchanged for other paths. A nonempty backend 429 remains byte-for-byte pass-through, preserving backend error extensions and headers. No rate values, Auth behavior, bot behavior, or other scopes changed.

## Scope

Product/test changes:

- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RateLimitProblemDetailsFilter.java`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitProblemDetailsFilterTest.java`

The local-denial test uses the actual reset-verify route path and `setComplete()` limiter path. It asserts `OTP_RATE_LIMITED`, the 12-second body/header value, and `no-store`. Existing generic local-429 and nonempty-backend-429 tests continue to cover their separate behavior.

## Verification

One authorized escalated focused batch ran under JDK `C:/Users/maksd/.jdks/ms-21.0.10` with `--no-daemon --no-parallel --max-workers=1 --no-problems-report` and `-Dorg.gradle.java.compile-classpath-packaging=true`:

```text
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report "-Dorg.gradle.java.compile-classpath-packaging=true" :services:api-gateway:test :services:auth-service:auth-app:test --tests ru.rutcampustrack.gateway.ratelimit.RateLimitProblemDetailsFilterTest --tests ru.rutcampustrack.auth.controller.PasswordResetValidationExceptionHandlerTest
```

Terminal session `17363`, exit code `0`, `BUILD SUCCESSFUL`. JUnit XML confirms the Gateway filter class passed 7/7 tests and the scoped Auth validation class passed 1/1. The Auth case is unchanged from parent commit `866ee0de`; it ran only because this was the pre-authorized combined target batch. Full actual output is preserved in `password-recovery-review-correction-local-429-866ee0de-java.log`.

`git diff --check` before the batch returned 0. Do not rerun these Java targets after the source is committed; the focused PASS is the evidence for this correction. The earlier un-escalated access failures are superseded by this successful escalated batch.

## Limits

This focused test exercises the response decorator, not Redis or a live Gateway-to-Auth request. It verifies the exact route classification and local deny response. Full route throttling and the actual API + PWA path remain root-owned integration checks. No containers, Telegram access, push, merge, deployment, or production data operations were used for this correction.

## Diff inventory and handoff

The correction changes only the two scoped product/test files above plus this evidence and its captured Gradle output. The three unrelated untracked evidence folders remain untouched and excluded. Submit the stable diff, frozen contract, and this evidence to a fresh bounded Sol recheck of the local-denial behavior.
