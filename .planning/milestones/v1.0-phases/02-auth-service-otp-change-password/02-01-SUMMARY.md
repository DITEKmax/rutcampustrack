---
phase: 02-auth-service-otp-change-password
phase_legacy_id: 01.2-auth-service-otp-change-password
plan: 01
subsystem: auth
tags: [otp, redis, rate-limiting, jwt, spring-boot, password-change]

# Dependency graph
requires:
  - phase: 01-auth-service-core-jwt-login
    phase_legacy_id: 01.1-auth-service-core-jwt-login
    provides: JWT RSA token generation, AuthService, UserRepository, JwtService, SecurityConfig with /auth/otp/** permitAll

provides:
  - POST /auth/otp/request - OTP code generation with Redis-backed rate limiting
  - POST /auth/otp/verify - OTP verification returning JWT pair, cleans all OTP Redis keys
  - POST /auth/change-password - Authenticated password change with current password validation
  - OtpService with requestOtp/verifyOtp methods
  - OtpProperties @ConfigurationProperties for externalized OTP config
  - OtpExpiredException (401) and OtpRateLimitException (429) exception classes

affects: [api-gateway, notification-bot, phase-1.3, phase-1.4]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - SecureRandom for OTP code generation (not Math.random)
    - Three Redis keys per OTP flow: otp:{id} (code), otp_sent:{id} (cooldown), otp_attempts:{id} (rate limit)
    - Field-level @Setter on Lombok entity (not class-level) for only mutable fields
    - @ConfigurationProperties record for externalized OTP config (same pattern as JwtProperties)

key-files:
  created:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpRequest.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpVerifyRequest.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/ChangePasswordRequest.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/OtpExpiredException.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/OtpRateLimitException.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/service/OtpService.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/OtpProperties.java
  modified:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/User.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java
    - services/auth-service/src/main/resources/application.yml

key-decisions:
  - "OTP send step is stubbed (code stored in Redis only) — actual Telegram delivery deferred to notification-bot phase"
  - "OtpExpiredException used for both expired and wrong code (same 401 response) to avoid revealing state"
  - "Field-level @Setter (not class-level) to minimize mutation surface on User entity"

patterns-established:
  - "OTP Redis keys: otp:{telegramId}, otp_sent:{telegramId}, otp_attempts:{telegramId}"
  - "Rate limit: 3 attempts per 5-min window, 60s resend cooldown per user"
  - "Increment-then-set-expiry pattern for Redis counters (expire only on first increment)"

requirements-completed: [FR-5, FR-6]

# Metrics
duration: 4min
completed: 2026-03-29
---

# Phase 01.2 Plan 01: OTP Authentication and Password Change Summary

**Telegram OTP login flow (3 Redis keys, rate-limited) and authenticated password change endpoint added to auth-service**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-03-29T06:40:59Z
- **Completed:** 2026-03-29T06:45:20Z
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- POST /auth/otp/request: generates 6-digit code via SecureRandom, stores in Redis with 120s TTL, enforces 60s resend cooldown and 3-attempt limit per 5-min window
- POST /auth/otp/verify: validates code against Redis, cleans up all 3 OTP keys on success, returns JWT access+refresh pair (same flow as login)
- POST /auth/change-password: requires Bearer JWT, validates current password with BCrypt, updates password_hash, sets password_changed=true, clears initial_password
- OtpProperties @ConfigurationProperties with externalized otp.* config (code-length, ttl-seconds, max-attempts, attempts-window-seconds, resend-cooldown-seconds)
- RFC 7807 error responses: OtpExpiredException -> 401, OtpRateLimitException -> 429

## Task Commits

Each task was committed atomically:

1. **Task 1: DTOs and Exceptions** - `a27ebf3` (feat)
2. **Task 2: OtpService + AuthService.changePassword + User setters** - `de53704` (feat)
3. **Task 3: Controller endpoints + Exception handling + Build verification** - `e3ffd67` (feat)

## Files Created/Modified

- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpRequest.java` - OTP request DTO with @NotNull telegramId
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpVerifyRequest.java` - OTP verify DTO with telegramId + @Size(6,6) code
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/ChangePasswordRequest.java` - Change password DTO with @Size(min=6) newPassword
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/OtpExpiredException.java` - 401 for invalid/expired OTP
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/OtpRateLimitException.java` - 429 for rate limit breach
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/OtpService.java` - Core OTP logic with Redis rate limiting
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/OtpProperties.java` - Externalized OTP config record
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java` - Added changePassword method
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/User.java` - Field-level @Setter on passwordHash, passwordChanged, initialPassword
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` - 3 new endpoints wired to OtpService + AuthService
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java` - OtpExpiredException and OtpRateLimitException handlers
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java` - Registered OtpProperties
- `services/auth-service/src/main/resources/application.yml` - Added otp: section

## Decisions Made

- OTP actual Telegram delivery is stubbed — the code is stored in Redis only, readable via redis-cli during testing. Actual delivery will be handled by notification-bot in a future phase. This decouples auth-service from notification concerns.
- OtpExpiredException is thrown for both "code expired" and "wrong code" cases (indistinguishable 401 response) to prevent enumeration attacks.
- Field-level @Setter rather than class-level to maintain minimal mutation surface on the User entity.

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

- **OTP Telegram delivery**: `OtpService.requestOtp` stores code in Redis but does NOT send it to Telegram. The code is only accessible via redis-cli (`GET otp:{telegramId}`). This is an intentional stub per plan spec — notification-bot delivery deferred to a future phase.

## Issues Encountered

None - all tasks completed without issues. Full build (`./gradlew.bat :services:auth-service:build`) passes.

## User Setup Required

None - no external service configuration required beyond what Phase 1.1 established.

## Next Phase Readiness

- Auth service now has complete authentication flows: password login, token refresh, logout, OTP login, password change
- Public key endpoint ready for API Gateway JWT validation
- Ready for Phase 1.3 (API Gateway JWT filter and routing)

---
*Phase: 02-auth-service-otp-change-password (legacy 01.2-auth-service-otp-change-password)*
*Completed: 2026-03-29*

## Self-Check: PASSED

- All 7 created files confirmed present on disk
- All 3 task commits (a27ebf3, de53704, e3ffd67) confirmed in git log
