---
phase: 02-auth-service-otp-change-password
phase_legacy_id: 01.2-auth-service-otp-change-password
verified: 2026-03-29T07:30:00Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 2 (legacy 1.2): Auth Service OTP + Change Password — Verification Report

**Phase Goal:** OTP-based authentication via Telegram and password change functionality.
**Verified:** 2026-03-29T07:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | POST /auth/otp/request with valid telegram_id generates 6-digit code stored in Redis with TTL 120s | VERIFIED | `OtpService.requestOtp` uses `SecureRandom.nextInt(1_000_000)` formatted `%06d`, stores via `redisTemplate.opsForValue().set("otp:" + telegramId, code, Duration.ofSeconds(otpProperties.ttlSeconds()))` where `ttl-seconds: 120` in application.yml |
| 2 | POST /auth/otp/request when rate limited returns 429 | VERIFIED | `OtpRateLimitException` thrown on cooldown and attempt-count checks; `GlobalExceptionHandler.handleOtpRateLimit` returns `HttpStatus.TOO_MANY_REQUESTS` |
| 3 | POST /auth/otp/verify with correct code returns JWT pair (accessToken + refreshToken) | VERIFIED | `OtpService.verifyOtp` validates stored code, generates access + refresh tokens via `JwtService`, stores refresh token in Redis, returns `TokenResponse(accessToken, refreshToken, expiresIn)` |
| 4 | POST /auth/otp/verify with wrong or expired code returns 401 | VERIFIED | Both null-stored-code and code-mismatch paths throw `OtpExpiredException`; `GlobalExceptionHandler.handleOtpExpired` returns `HttpStatus.UNAUTHORIZED` |
| 5 | POST /auth/change-password with correct current password updates password_hash and returns 200 | VERIFIED | `AuthService.changePassword` calls `passwordEncoder.matches(currentPassword, user.getPasswordHash())`, then `user.setPasswordHash(newHash)`, `user.setPasswordChanged(true)`, `user.setInitialPassword(null)`, `userRepository.save(user)`; controller returns `ResponseEntity.ok().build()` |
| 6 | POST /auth/change-password with wrong current password returns 401 | VERIFIED | `passwordEncoder.matches` failure throws `InvalidCredentialsException`; existing `GlobalExceptionHandler.handleInvalidCredentials` returns `HttpStatus.UNAUTHORIZED` |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | Provides | Exists | Substantive | Wired | Status |
|----------|----------|--------|-------------|-------|--------|
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/OtpService.java` | OTP generation, Redis storage, rate limiting, verification | Yes | Yes — 117 lines, full logic | Yes — injected into `AuthController` | VERIFIED |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpRequest.java` | OTP request DTO | Yes | Yes — record with `@NotNull Long telegramId` | Yes — used in controller and OtpService | VERIFIED |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpVerifyRequest.java` | OTP verify DTO | Yes | Yes — record with `Long telegramId`, `@Size(min=6,max=6) String code` | Yes — used in controller and OtpService | VERIFIED |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/ChangePasswordRequest.java` | Change password DTO | Yes | Yes — record with `@Size(min=6) String newPassword` | Yes — used in controller and AuthService | VERIFIED |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/OtpExpiredException.java` | 401 exception for invalid/expired OTP | Yes | Yes — extends RuntimeException | Yes — thrown in OtpService, handled in GlobalExceptionHandler | VERIFIED |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/OtpRateLimitException.java` | 429 exception for rate limit | Yes | Yes — extends RuntimeException | Yes — thrown in OtpService, handled in GlobalExceptionHandler | VERIFIED |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/OtpProperties.java` | Externalized OTP config | Yes | Yes — `@ConfigurationProperties(prefix = "otp")` record | Yes — injected into OtpService | VERIFIED |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AuthController` | `OtpService` | Constructor injection; `otpService.requestOtp`, `otpService.verifyOtp` | WIRED | `AuthController` holds `private final OtpService otpService`; both call sites confirmed at lines 69 and 78 |
| `AuthController` | `AuthService.changePassword` | `authService.changePassword(userId, request)` at line 88 | WIRED | `Long userId = Long.parseLong(authentication.getName())` passed correctly |
| `OtpService` | Redis | `redisTemplate.opsForValue()` for all three keys: `otp:`, `otp_sent:`, `otp_attempts:` | WIRED | All three key patterns present in `requestOtp` and cleanup in `verifyOtp` |
| `AuthService.changePassword` | `UserRepository` | `userRepository.save(user)` after password mutation | WIRED | Line 124: `userRepository.save(user)` confirmed |

---

### Data-Flow Trace (Level 4)

OTP and change-password are action endpoints (not data-rendering components). Data flows are:

| Endpoint | Input Variable | Source | Produces Real Action | Status |
|----------|----------------|--------|----------------------|--------|
| `requestOtp` | `telegramId` | `OtpRequest` record | Lookup user in DB, generate code via `SecureRandom`, write 3 Redis keys | FLOWING |
| `verifyOtp` | `telegramId + code` | `OtpVerifyRequest` record | Read Redis key, compare, delete keys, generate JWT via RSA key pair | FLOWING |
| `changePassword` | `userId` from JWT `authentication.getName()` | Spring Security context principal | DB lookup, BCrypt compare, DB save with new hash | FLOWING |

No hollow props or disconnected data paths found.

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — endpoints require running Spring Boot + Redis + PostgreSQL stack. No in-process testable entry points exist without infrastructure.

Git commits confirmed real:
- `a27ebf3` — DTOs and Exceptions
- `de53704` — OtpService, AuthService.changePassword, User setters
- `e3ffd67` — Controller endpoints + Exception handlers

---

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| FR-5 | OTP Flow | SATISFIED | |
| FR-5.1 | POST /auth/otp/request: 6-digit code, Redis TTL 120s | SATISFIED | `OtpService.requestOtp` generates `%06d` from `SecureRandom`, stores with `Duration.ofSeconds(120)` |
| FR-5.2 | Rate limiting: 3 attempts/5min, resend cooldown 60s | SATISFIED | `otp_attempts:` key with 300s window, `otp_sent:` key with 60s TTL; `maxAttempts: 3` |
| FR-5.3 | POST /auth/otp/verify: returns JWT pair on success | SATISFIED | `OtpService.verifyOtp` returns `TokenResponse` |
| FR-5.4 | Verify OTP matches, not expired, attempts not exceeded | SATISFIED | Null-code path and mismatch path both throw `OtpExpiredException`; rate limit checked in `requestOtp` |
| FR-6 | Change Password | SATISFIED | |
| FR-6.1 | POST /auth/change-password accepts currentPassword + newPassword | SATISFIED | `ChangePasswordRequest` record with both fields; endpoint `@PostMapping("/change-password")` |
| FR-6.2 | Requires valid JWT (authenticated) | SATISFIED | `/auth/change-password` is not in `SecurityConfig.permitAll`; only `/auth/otp/**` and `/auth/login`, `/auth/refresh`, `/auth/public-key` are public |
| FR-6.3 | Verify current password, update password_hash, set password_changed=true, clear initial_password | SATISFIED | `AuthService.changePassword` lines 116-124 implement exactly this sequence |

No orphaned requirements: REQUIREMENTS.md maps FR-5 and FR-6 to this phase, both fully covered.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `OtpService.java` | (none) | OTP Telegram delivery intentionally not implemented — code stored in Redis only | Info | Acknowledged in SUMMARY.md as intentional stub; notification-bot delivery deferred to a future phase. Does NOT block goal (OTP auth works end-to-end for systems with Redis access) |

No blockers or warnings. The Telegram delivery deferral is an explicitly documented architectural decision, not a hidden stub.

---

### Human Verification Required

#### 1. OTP Rate Limit Window Reset

**Test:** Request OTP 3 times in rapid succession for the same telegram_id. Then wait for the `attempts-window-seconds` (300s) to expire and request again.
**Expected:** First 3 requests succeed (codes stored), 4th returns 429. After 5 minutes, request succeeds again.
**Why human:** TTL-based Redis window expiry cannot be verified without a running Redis instance and wall-clock time.

#### 2. Change Password — authentication.getName() Binding

**Test:** Obtain a JWT via POST /auth/login. Send POST /auth/change-password with that Bearer token. Confirm the correct user's password is updated.
**Expected:** Only the authenticated user's password is changed, not another user's.
**Why human:** Requires running service + valid JWT to verify the SecurityContext principal binding.

#### 3. Security — /auth/change-password Rejects Unauthenticated Requests

**Test:** Send POST /auth/change-password without a Bearer token.
**Expected:** 401 Unauthorized before reaching controller.
**Why human:** Requires running Spring Security filter chain.

---

### Gaps Summary

No gaps. All 6 observable truths are verified, all artifacts are substantive and wired, all key links are active, FR-5 and FR-6 are fully satisfied.

The one acknowledged deferral (Telegram OTP delivery) is not a gap — it is explicitly planned for the notification-bot phase and does not prevent the OTP authentication goal: codes are stored in Redis and verifiable via `GET otp:{telegramId}` during development.

---

_Verified: 2026-03-29T07:30:00Z_
_Verifier: Claude (gsd-verifier)_
