# Auth token purpose correction — compact contract

Revision baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` (detached HEAD).
Role: sole writer for the bounded auth purpose scope; no children. Risk: S3
(authentication boundary). Model/effort: fresh implementation leaf, Luna max;
root owns contract and acceptance.

## 1. Goal

Fix the confirmed refresh-as-access authentication defect with a signed,
fail-closed token-purpose contract while preserving issuer, audience and RSA
signature validation.

## 2. Context / evidence

Read project `AGENTS.md`, `docs/agent-workflow.md`, `services/AGENTS.md`,
`tests/AGENTS.md`, and `.agents/skills/rct-verification/SKILL.md`. The
canonical probe is
`C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\student-role-02\auth-token-purpose-probe\evidence.md`;
the staged profile decision is
`C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\student-role-02\profile-auth-decision-packet.md`.
The probe's real compiled Auth filter accepted a refresh token and created
`ROLE_null`; missing-header and access controls passed. No real keys, Redis,
DB, network, or product exploit is claimed.

## 3. Relevant scope

Only these source files may change:

- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java`

Evidence and verification artifacts belong only under
`.agent/student-auth-a/purpose/`.

## 4. Required behavior

- Every generated token carries `token_use`: `access`, `refresh`, or `internal`.
- `parseAccessToken` validates signed issuer/audience, purpose `access`,
  positive numeric subject, non-expired required expiration, and a known
  `UserRole` (`ADMIN`, `TEACHER`, `STUDENT`).
- `parseRefreshToken` validates signed issuer/audience, purpose `refresh`,
  positive numeric subject, non-expired required expiration, and nonblank `jti`.
- `parseToken` is the strict access alias because `WsTicketController` uses it
  for access claims.
- `extractUserId` and `extractJti` use the strict refresh parser; inspected
  callsites are refresh-only (`AuthService`, `TmaService`, `OtpService`).
- `JwtAuthenticationFilter` uses strict access parsing, rejects malformed or
  wrong-purpose claims, and never creates `ROLE_null`; invalid bearer input
  leaves the request unauthenticated.
- Internal tokens keep internal audience and purpose; access parsing rejects
  them for the audience/purpose boundary.

## 5. Constraints

No Gateway, DTO, proto, migration, build/config/dependency edits; no real-key
initialization, secrets, network, Redis, or DB fixtures. Tests use a synthetic
in-memory RSA pair installed through reflection. No immediate revocation claim
or refresh lifecycle redesign is in scope.

## 6. Existing patterns

`JwtService` uses JJWT `0.12.6`, RSA `RS256`, issuer
`rutcampustrack-auth`, external audience `rutcampustrack`, and internal
audience `rutcampustrack-internal`. `UserRole` is `ADMIN`, `TEACHER`,
`STUDENT`. Auth/TMA/OTP extract JTI from refresh tokens; `WsTicketController`
parses an access token through `parseToken`.

## 7. Acceptance criteria

Focused tests demonstrate:

- signed access parses positively;
- refresh-as-access is rejected;
- access-as-refresh is rejected;
- internal token with wrong audience is rejected by access parsing;
- missing/wrong purpose and malformed required claims are rejected;
- filter accepts valid access and rejects refresh, internal, missing, malformed,
  and invalid-role bearer values without `ROLE_null`.

## 8. Verification

Inspect callers and build scripts before implementation. After implementation,
run only the root-leased focused unit command:

`./gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.service.JwtTokenPurposeTest --tests ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest --no-daemon --max-workers=1`

Record revision, exact command, exit code, PowerShell/Java/Gradle environment,
test evidence, runtime applicability, diff manifest, and limitations in this
directory. Focused servlet/filter behavior is covered by synthetic unit tests;
full HTTP/Gateway ingress and security-scanner checks remain an explicitly open
S3 integration gate for the root/session integration packet.

## 9. Do not

Do not touch Gateway, generated/shared contracts, configs, dependencies,
database, migrations, external runtime, real keys/secrets, or unrelated dirty
work. Do not create children or commit. Do not treat WARN/ERROR as a defect
without request-linked reproduction and evidence.
