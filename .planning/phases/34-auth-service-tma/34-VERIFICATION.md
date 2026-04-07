---
phase: 34-auth-service-tma
verified: 2026-04-07T01:45:00Z
status: passed
score: 6/6 must-haves verified
---

# Phase 34: Auth Service TMA — Verification Report

**Phase Goal:** Auth Service can exchange Telegram initData for a JWT (HMAC-SHA256 validation), and provides a body-based refresh endpoint for Mini App (WebView drops httpOnly cookies)
**Verified:** 2026-04-07T01:45:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | POST /auth/tma with valid Telegram initData returns JWT access + refresh tokens | VERIFIED | TmaIntegrationTest.tma_withValidInitData_returnsTokenPair PASS; TmaService.authenticateWithInitData issues JWT pair via JwtService |
| 2 | POST /auth/tma with tampered initData returns 401 with clear error | VERIFIED | TmaIntegrationTest.tma_withTamperedHash_returns401 PASS; GlobalExceptionHandler.handleTmaValidation returns 401 application/problem+json |
| 3 | POST /auth/refresh-body with valid refresh token returns new access + refresh tokens | VERIFIED | TmaIntegrationTest.refreshBody_withValidToken_returnsNewPair PASS; delegates to AuthService.refresh() |
| 4 | User lookup by telegram_id works correctly | VERIFIED | TmaService.authenticateWithInitData calls userRepository.findByTelegramId; TmaIntegrationTest.tma_withUnlinkedTelegramId_returns401 PASS |
| 5 | POST /auth/tma with expired auth_date returns 401 | VERIFIED | TmaIntegrationTest.tma_withExpiredAuthDate_returns401 PASS; auth_date max-age check in TmaService (172800s > 86400s threshold) |
| 6 | POST /auth/refresh-body with invalid refresh token returns 401 | VERIFIED | TmaIntegrationTest.refreshBody_withInvalidToken_returns401 PASS |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/TmaService.java` | HMAC-SHA256 validation + user lookup + token issuance | VERIFIED | 137 lines; contains `MessageDigest.isEqual`, `"WebAppData"`, `Mac.getInstance("HmacSHA256")`, `userRepository.findByTelegramId`, `HexFormat.of().formatHex` |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/TmaProperties.java` | TMA bot token + auth_date max age config | VERIFIED | `@ConfigurationProperties(prefix = "tma")` with `String botToken` and `long authDateMaxAgeSeconds` |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TmaAuthRequest.java` | Request DTO for TMA auth | VERIFIED | record with `@NotBlank(message = "initData must not be blank") String initData` |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/TmaValidationException.java` | Exception for invalid/tampered initData | VERIFIED | `extends RuntimeException` with single String constructor |
| `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/TmaIntegrationTest.java` | Integration tests (min 80 lines) | VERIFIED | 156 lines, 6 @Test methods, `buildValidInitData` + `buildValidInitDataWithAuthDate` HMAC helpers |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AuthController.java` | `TmaService.authenticateWithInitData` | POST /auth/tma endpoint | VERIFIED | Line 92: `tmaService.authenticateWithInitData(request)` |
| `AuthController.java` | `AuthService.refresh` | POST /auth/refresh-body endpoint | VERIFIED | Line 101: `authService.refresh(request)` |
| `TmaService.java` | `UserRepository.findByTelegramId` | Telegram user lookup | VERIFIED | Line 69: `userRepository.findByTelegramId(telegramId)` |

---

### Wiring Verification

| Component | Wired To | Status | Details |
|-----------|----------|--------|---------|
| SecurityConfig permitAll | `/auth/tma`, `/auth/refresh-body` | VERIFIED | Lines 32-33 in SecurityConfig.java — both paths in requestMatchers |
| AuthApplication | `TmaProperties.class` | VERIFIED | `@EnableConfigurationProperties({JwtProperties.class, OtpProperties.class, TmaProperties.class})` |
| GlobalExceptionHandler | `TmaValidationException` | VERIFIED | `handleTmaValidation` handler at lines 102-115, returns 401 application/problem+json |
| AbstractIntegrationTest | `tma.bot-token = test_bot_token_12345` | VERIFIED | `@DynamicPropertySource` at line 36-37 overrides tma.bot-token and tma.auth-date-max-age-seconds |
| application.yml | `tma:` section | VERIFIED | Lines 49-51: `bot-token: ${TMA_BOT_TOKEN:test_bot_token_for_dev}`, `auth-date-max-age-seconds: 86400` |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `compileJava` succeeds | `./gradlew.bat :services:auth-service:compileJava` | BUILD SUCCESSFUL in 1s | PASS |
| Full test suite (21 tests, 0 failures) | `./gradlew.bat :services:auth-service:test --rerun-tasks` | BUILD SUCCESSFUL in 22s | PASS |
| TmaIntegrationTest (6/6 tests pass) | XML report: TmaIntegrationTest tests="6" failures="0" | All 6 pass | PASS |

---

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| AUTH-01 | TMA initData HMAC-SHA256 validation returning JWT pair | SATISFIED | TmaService.validateInitData + authenticateWithInitData; POST /auth/tma endpoint; 4 integration tests cover valid, tampered, expired, unlinked scenarios |
| AUTH-02 | Body-based token refresh for WebView environments | SATISFIED | POST /auth/refresh-body delegates to AuthService.refresh(); 2 integration tests cover valid and invalid token scenarios |

---

### Security / Threat Model Verification

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-34-01: Spoofing via forged initData | HMAC-SHA256 with `MessageDigest.isEqual` constant-time comparison | VERIFIED — `MessageDigest.isEqual` at TmaService line 102 |
| T-34-02: Replay via old initData | `auth_date` max-age check against `tmaProperties.authDateMaxAgeSeconds()` | VERIFIED — TmaService lines 60-63, tested in `tma_withExpiredAuthDate_returns401` |
| T-34-03: Timing oracle on hash comparison | `MessageDigest.isEqual` (constant-time) vs `String.equals` | VERIFIED — correct implementation confirmed |
| T-34-04: Unlinked telegram_id accepted | `findByTelegramId()` empty Optional -> InvalidCredentialsException -> 401 | VERIFIED — tested in `tma_withUnlinkedTelegramId_returns401` |
| T-34-05: Bot token exposure | `${TMA_BOT_TOKEN:test_bot_token_for_dev}` — env var, never hardcoded | VERIFIED — application.yml uses env var placeholder |

---

### Anti-Patterns Found

None. No TODOs, FIXMEs, placeholder returns, or stub implementations found in any phase-34 files.

---

### Human Verification Required

None — all behaviors are fully verified programmatically via integration tests and static analysis.

---

## Gaps Summary

No gaps. All 6 observable truths from the plan's must_haves are verified. All 4 roadmap success criteria are satisfied. Both requirements AUTH-01 and AUTH-02 are covered. The full test suite (21 tests across AuthIntegrationTest, OtpIntegrationTest, and TmaIntegrationTest) passes with 0 failures. All key links are wired. Compilation is clean. STRIDE threats T-34-01 through T-34-05 are all mitigated.

---

_Verified: 2026-04-07T01:45:00Z_
_Verifier: Claude (gsd-verifier)_
