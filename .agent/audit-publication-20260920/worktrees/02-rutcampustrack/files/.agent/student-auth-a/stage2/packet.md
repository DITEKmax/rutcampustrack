# Compact packet — Auth Stage2

## 1. Goal

Связать public PASSWORD/OTP/TMA входы с PostgreSQL session authority и
реализовать public current/select-role/refresh/logout/logout-all/sessions/history/
change-password, чтобы login → role → Today/Homework получал session-bound
selected access и серверный результат.

## 2. Context/evidence

- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Baseline — accepted dirty worktree; pre-edit `auth-app` compileJava +
  compileTestJava был PASS, exit 0, ~33s (тяжёлые проверки отложены до lease
  root).
- Stage1/shared64 accepted. Существующие изменения в JwtService,
  JwtAuthenticationFilter, GlobalExceptionHandler, shared-security и связанные
  deleted/new paths принадлежат чужой работе и сохраняются.
- Canonical accepted session sources: `session/SessionLifecycleService`,
  `session/{PasswordPolicy,SessionAdmissionException}`,
  `session/port/{SessionStatePort,CredentialSessionTransactionPort}`,
  `session/jdbc/JdbcSessionAuthority`, session models and V24 migration.
- Current gaps: duplicate AuthApi/AuthSessionApi logout/change-password;
  Redis-backed refresh authority; legacy logout cleanup; role-only String
  principal. The requested compact contract is the owner decision; no product
  decision is outstanding.
- `.agents/skills/rct-verification/SKILL.md` is absent in this checkout; the
  project workflow and `tests/AGENTS.md` verification requirements are applied,
  and the absence is recorded for root.

## 3. Relevant scope

Modify only the assigned auth-api/auth-app production files and owned tests:

- `auth-api-contract`: `AuthApi.java`, `ChangePasswordRequest.java`,
  `TokenResponse.java`.
- `auth-app`: `AuthController.java` plus new `AuthSessionController.java`,
  `AuthService.java`, `OtpService.java`, `TmaService.java`, `JwtService.java`,
  `JwtAuthenticationFilter.java`, `SecurityConfig.java`,
  `GlobalExceptionHandler.java`, new `security/SessionPrincipal.java`, new
  `session/AuthSessionException.java`, new `session/port/AuthSessionQueryPort.java`,
  new `session/jdbc/JdbcAuthSessionQueryAdapter.java`.
- Existing owned tests listed in the task packet and new
  `AuthSessionControllerTest`, `JdbcAuthSessionQueryAdapterIT`,
  `SessionAuthFlowIT`.
- Evidence only under `.agent/student-auth-a/stage2/`.

Frozen/owned by others: `AuthSessionApi` and its accepted DTOs,
shared-security, V24/SQL, session lifecycle/ports/authority/models, Gateway,
BFF, WS, profile/Light/proto/generated/build/config outside `SecurityConfig`.

## 4. Required behavior

- AuthApi retains login/refresh/public-key/OTP/TMA only. AuthController has no
  legacy logout/change-password/WsTicket mapping. AuthSessionController owns all
  seven AuthSessionApi mappings exactly once.
- PASSWORD/OTP/TMA use one session issuance seam. After proof, authoritative
  rolesVersion/grants hint is loaded, `SessionLifecycleService.createSession`
  creates fresh sid/current JTI/fixed absolute refresh expiry, and refresh plus
  selected access or neutral bootstrap are signed. Default is selectable STUDENT,
  then TEACHER; no User.role/status authority and no automatic HEADMAN/ADMIN.
- Refresh is cookie-only and strict: atomic DB CAS, one winner, rotated replay
  409, older/unknown 401, no grace/revoke loser, unchanged absolute expiry,
  remaining positive cookie max-age, response after commit.
- JwtService has strict session refresh and neutral bootstrap token forms while
  preserving accepted access/internal admission semantics. Bootstrap has no role,
  status, group, headman or readOnly claims. Selected access carries the accepted
  full identity tuple.
- Filter creates immutable typed SessionPrincipal with decimal user name and
  sub/sid/sv/rv plus optional selected identity; role-only bearer is rejected.
- Protected AuthSession operations admit live sid/user/sv/rv/selected identity
  against PostgreSQL before effect. Bootstrap permits only current/select-role/
  sessions/logout/logout-all. Stale/revoked/foreign/503 errors remain typed.
- Query adapter provides authoritative login hint, labels, own live sessions and
  own history with bounded keyset cursors and decimal IDs/versions.
- Password change uses exact PasswordPolicy text, BCrypt guard and atomic
  credential/session/event port; current password is required but exempt from new
  policy; clear cookie only after success and never expose plaintext/hash.
- AuthSessionException/GlobalExceptionHandler mappings and no-store headers are
  exact per contract. TokenResponse has redacted `toString`.

## 5. Constraints

- One writer in shared checkout; no children, no Terra, no commit/stage/reset,
  no network/secrets/production or destructive DB actions.
- Preserve unrelated dirty work and accepted Stage1 behavior. Do not add cache,
  legacy authority, fake CAS, alternate OpenAPI/DTO generation or bootstrap
  prefix allowlists. No manual TS/gen or TMA host QA.
- Heavy Gradle/Docker/runtime commands require root lease. Static inspection,
  targeted source checks and `git diff --check` are allowed before lease.

## 6. Existing patterns

- Java 21 records and frozen AuthSessionApi DTOs.
- `SessionLifecycleService` delegates one atomic operation to accepted ports;
  `JdbcSessionAuthority` owns DB locks, fixed-expiry CAS, and same-transaction
  events.
- `SessionAdmissionException` and shared `ErrorResponse` use typed ProblemDetails
  plus `Cache-Control: no-store`.
- `AuthCookies` is HttpOnly/Secure/SameSite=Strict, Path `/api/auth`.
- `UserRepository` remains credential lookup only; new query/JDBC adapter owns
  role/session truth.

## 7. Acceptance criteria

- No duplicate Spring mappings and no `refresh:*` authority reads/writes.
- All login modes issue DB-backed session-bound pair; bootstrap reaches only its
  five exact methods; role select passes Stage1 admission.
- Every protected public operation rejects stale/revoked/foreign/unavailable
  authority before effect; own-only labels/pages and bounded cursors hold.
- Refresh concurrency is exactly 1×200/1×409, old replay 401, fixed expiry and
  remaining cookie max-age hold.
- Logout/password durable commit precedes cookie clear; injected DB failure has
  no partial success. Exact password policy and bearer/plaintext/JTI redaction
  hold. Existing purpose/admission semantics remain green.

## 8. Verification

- Before lease: targeted source searches, `git diff --check`, and compile/test
  command request prepared; no heavy command run.
- After source ready and root lease: exact auth-api compile/tests, auth-app
  focused unit selectors, isolated PostgreSQL V1–V24 SessionAuthFlowIT and query
  adapter IT, existing Auth/Otp/Tma/Logout/SameSite IT, affected Stage1 purpose
  and InternalSessionAdmissionIT. Record command, exit code, environment,
  revision and evidence; raw final XML is byte-identical under `junit/**`.
- Runtime scope is applicable to this auth change; runtime checks include
  session issuance/admission, refresh CAS/replay/expiry, logout/all, password
  rollback, labels and cursors. No runtime evidence is claimed before execution.
- Root will dispatch fresh independent Sol high review after stable diff and
  checks.

## 9. Do not

Не редизайнить продукт и не изменять frozen/чужие файлы; не использовать
legacy role/status minting, Redis refresh fallback, fake read/write CAS,
bootstrap capability claims, expiry extension, benign loser revoke, cookie
clear on authority failure, fabricated labels/events, plaintext/bearer/JTI in
messages/logs/toString, controller-only proof или Today/Homework E2E claim.

## Ownership/runtime metadata

- Developer: `/root/auth_stage2`, fresh `gpt-5.6-luna`, `max`; sole writer.
- Environment: Windows PowerShell, shared checkout above, Java/Gradle project.
- Pre-edit dirty paths: 35 porcelain entries; all remain outside this packet's
  ownership unless explicitly listed above. No files are staged or committed.
