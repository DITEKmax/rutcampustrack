# Stage2 affected final evidence

Status: `AFFECTED_EVIDENCE_READY`.

## Scope

This handoff records the bounded correction of the `affected-01` gate. The
session controller now invalidates the user's legacy Redis WS tickets only
after `logout`, `logoutAll` or `changePassword` succeeds durably. The OTP
integration fixtures use a policy-valid new password and a student selected
access token for the typed wrong-current-password case. `WsTicketService`
itself and unrelated dirty work were preserved.

## Criteria

The corrected focused unit suites and affected integration suites must have no
skips, failures or errors. The four corrected suites below provide `28/28`
tests with zero-result fields.

## Evidence

Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Environment: Windows PowerShell on `DITEK-PK`, shared checkout, Gradle
wrapper. Root supplied the following successful runtime runs:

- Unit command:
  `.\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.controller.AuthSessionControllerTest --tests ru.rutcampustrack.auth.service.OtpServiceTest --no-daemon --no-parallel --max-workers=1 --console=plain`
  (session `97448`, exit `0`, `BUILD SUCCESSFUL` in `46s`).
- Affected integration command:
  `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.LogoutLifecycleIT --tests ru.rutcampustrack.auth.integration.OtpIT --no-daemon --no-parallel --max-workers=1 --console=plain`
  (session `87435`, exit `0`, `BUILD SUCCESSFUL` in `1m`).

| Suite | Tests | Skipped | Failures | Errors | SHA-256 |
|---|---:|---:|---:|---:|---|
| `AuthSessionControllerTest` | 8 | 0 | 0 | 0 | `345276B281D40161CC9A1076A3B89C8B6F32DD546EFDA1FA70BB124A818A9DC6` |
| `OtpServiceTest` | 9 | 0 | 0 | 0 | `7B5A16B56DA3545BE66EF57DA1AFAE0247DD8D4447EC097EC8BF1FB85CD9B483` |
| `LogoutLifecycleIT` | 3 | 0 | 0 | 0 | `0DECAE6D49FCC5AFB934AB282B0581F91123F1302D116E9D0985F606F9F1B6E8` |
| `OtpIT` | 8 | 0 | 0 | 0 | `3FC49EB2733D3570286EE91E84EBA232824F80F4CB6BFB5D3B08232124A85C5E` |

Combined result: `28/28` passed, with zero skipped, failures and errors.

## Checks

| Check | Result | Exit |
|---|---|---:|
| Root focused unit run | `AuthSessionControllerTest 8/8`, `OtpServiceTest 9/9` | 0 |
| Root affected integration run | `LogoutLifecycleIT 3/3`, `OtpIT 8/8` | 0 |
| Copy source XML to `junit/affected-final/{unit,integration}` | All four SHA-256 values and byte comparisons match | 0 |
| Parse copied JUnit XML | Total `28`; all skipped/failures/errors are `0` | 0 |

## Runtime evidence

Session `97448` is the focused unit evidence. Session `87435` is the affected
Boot/Redis/PostgreSQL integration evidence: the corrected logout lifecycle
passes with WS-ticket cleanup and refresh revocation, and both OTP password
flows pass. The original failure remains immutable in
`failures/affected-01/` and is linked to this correction by scope and suite.

## Diff

This evidence handoff adds only four byte-identical JUnit reports under
`.agent/student-auth-a/stage2/junit/affected-final/` and this record. No source,
test or Gradle file was edited during evidence capture; the original
`affected-01` archive was not overwritten.

## Limitations

This package covers the corrected affected unit/integration selectors only. It
does not claim the pending OpenAPI export/compare, broader root checks, or
independent final review.
