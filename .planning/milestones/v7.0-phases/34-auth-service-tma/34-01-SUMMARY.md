---
phase: 34-auth-service-tma
plan: 01
subsystem: auth
tags: [java, spring-boot, jwt, telegram, hmac, tma, redis]

requires:
  - phase: 33-infra-scaffolding
    provides: API Gateway PUBLIC_PATHS already includes /api/auth/tma and /api/auth/refresh-body

provides:
  - POST /auth/tma — Telegram initData HMAC-SHA256 validation returning JWT pair (AUTH-01)
  - POST /auth/refresh-body — body-based refresh token exchange for Mini App WebView (AUTH-02)
  - TmaService with constant-time HMAC comparison via MessageDigest.isEqual
  - TmaProperties @ConfigurationProperties with TMA_BOT_TOKEN env var
  - TmaValidationException mapped to 401 application/problem+json

affects:
  - frontends/mini-app
  - phase-35-mini-app-scaffold
  - phase-36-mini-app-auth-screen

tech-stack:
  added: []
  patterns:
    - "HMAC-SHA256 Telegram initData validation using javax.crypto.Mac (JDK stdlib, no new deps)"
    - "Constant-time hash comparison with MessageDigest.isEqual to prevent timing oracle"
    - "body-based refresh endpoint pattern for WebView-incompatible httpOnly cookies"

key-files:
  created:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/TmaProperties.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TmaAuthRequest.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/TmaValidationException.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/service/TmaService.java
    - services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/TmaIntegrationTest.java
  modified:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java
    - services/auth-service/src/main/resources/application.yml
    - services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AbstractIntegrationTest.java

key-decisions:
  - "MessageDigest.isEqual used for constant-time hash comparison to prevent timing-based information leakage (T-34-03)"
  - "TMA_BOT_TOKEN env var with test_bot_token_for_dev fallback — never hardcoded in source"
  - "refresh-body delegates directly to existing AuthService.refresh() — one-line implementation, same Redis rotation logic"
  - "auth_date max-age configurable via TmaProperties.authDateMaxAgeSeconds (default 86400s) for replay protection"

patterns-established:
  - "Pattern: TmaService follows OtpService constructor injection pattern exactly"
  - "Pattern: TmaIntegrationTest.buildValidInitData() generates correctly-signed initData using same HMAC algorithm as TmaService"
  - "Pattern: @DynamicPropertySource in AbstractIntegrationTest overrides tma.bot-token for test isolation"

requirements-completed:
  - AUTH-01
  - AUTH-02

duration: 35min
completed: 2026-04-07
---

# Phase 34 Plan 01: TMA Auth + Refresh-Body Summary

**HMAC-SHA256 Telegram initData validation in auth-service: POST /auth/tma exchanges initData for JWT pair, POST /auth/refresh-body provides WebView-compatible cookie-less token refresh**

## Performance

- **Duration:** 35 min
- **Started:** 2026-04-07T01:00:00Z
- **Completed:** 2026-04-07T01:35:00Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- TmaService validates Telegram initData using HMAC-SHA256 with `MessageDigest.isEqual` constant-time comparison, auth_date replay protection, and telegram_id user lookup
- POST /auth/tma and POST /auth/refresh-body endpoints added to AuthController, both permitted in SecurityConfig without authentication
- 6 integration tests in TmaIntegrationTest covering all AUTH-01 and AUTH-02 scenarios; full auth-service test suite (existing + new) passes

## Task Commits

1. **Task 1: TMA service layer** - `17f4b10` (feat)
2. **Task 2: Controller endpoints + integration tests** - `25df05f` (feat)

## Files Created/Modified

- `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/TmaProperties.java` — `@ConfigurationProperties(prefix = "tma")` with botToken and authDateMaxAgeSeconds
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TmaAuthRequest.java` — request record with `@NotBlank String initData`
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/TmaValidationException.java` — extends RuntimeException, mapped to 401 problem+json
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/TmaService.java` — HMAC-SHA256 validation, auth_date check, telegram_id lookup, JWT issuance
- `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/TmaIntegrationTest.java` — 6 integration tests with buildValidInitData helper
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — added /tma and /refresh-body endpoints, 3-arg constructor
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java` — added handleTmaValidation handler
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java` — added /auth/tma and /auth/refresh-body to permitAll
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java` — added TmaProperties.class to @EnableConfigurationProperties
- `services/auth-service/src/main/resources/application.yml` — added tma section with TMA_BOT_TOKEN placeholder
- `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AbstractIntegrationTest.java` — added tma.bot-token and tma.auth-date-max-age-seconds to @DynamicPropertySource

## Decisions Made

- `MessageDigest.isEqual` used instead of `String.equals` for constant-time hash comparison, preventing timing oracle attacks (STRIDE T-34-03)
- `TMA_BOT_TOKEN` env var with `test_bot_token_for_dev` default in application.yml — bot token never hardcoded in source (T-34-05)
- `refresh-body` delegates to existing `AuthService.refresh()` with no new logic — same Redis JTI rotation, same `RefreshRequest` DTO (pitfall 5 from research: avoid over-engineering)
- `auth_date` max-age is configurable via `TmaProperties.authDateMaxAgeSeconds` defaulting to 86400s (24h) for replay protection (T-34-02)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

Docker Desktop was not running when tests were first executed, causing Testcontainers `IllegalStateException: Could not find a valid Docker environment`. Docker Desktop was started and tests passed on second run. This is an environment condition, not a code issue.

## Known Stubs

None.

## Threat Flags

None — all threat mitigations from the plan's STRIDE threat register were implemented as specified:
- T-34-01: HMAC-SHA256 validation with `MessageDigest.isEqual` constant-time comparison
- T-34-02: `auth_date` max-age check rejecting replayed initData
- T-34-03: Constant-time comparison via `MessageDigest.isEqual`
- T-34-04: `findByTelegramId()` empty → 401, no user enumeration
- T-34-05: Bot token from `TMA_BOT_TOKEN` env var

## Next Phase Readiness

- AUTH-01 and AUTH-02 fully implemented and tested
- Mini App frontend (phase 35+) can now call POST /api/auth/tma with Telegram initData and receive JWT pair
- POST /api/auth/refresh-body available for WebView refresh token rotation without httpOnly cookies
- No blockers for next phase

## Self-Check

- [x] TmaProperties.java exists
- [x] TmaAuthRequest.java exists
- [x] TmaValidationException.java exists
- [x] TmaService.java exists (contains MessageDigest.isEqual, HexFormat, HmacSHA256, findByTelegramId, WebAppData)
- [x] TmaIntegrationTest.java exists (6 tests)
- [x] Commits 17f4b10 and 25df05f verified in git log

---
*Phase: 34-auth-service-tma*
*Completed: 2026-04-07*
