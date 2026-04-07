---
phase: 29-pwa-scaffold-auth
plan: 01
subsystem: auth-service
tags: [auth, cookie, httpOnly, refresh-token, security]
dependency_graph:
  requires: []
  provides: [cookie-based-refresh-token, access-token-only-response]
  affects: [api-gateway-cors, pwa-auth-integration]
tech_stack:
  added: [ResponseCookie, CookieValue]
  patterns: [httpOnly-cookie-transport, token-rotation-via-cookie]
key_files:
  created:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/AccessTokenResponse.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TokenPair.java
  modified:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java
    - services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AuthIntegrationTest.java
  deleted:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/RefreshRequest.java
decisions:
  - "/auth/logout added to permitAll — cookie is the auth mechanism, no JWT needed"
  - "MissingRequestCookieException handler returns 400 (not 500) for missing cookie"
  - "RefreshRequest DTO deleted — no longer used by any endpoint"
  - "AccessTokenResponse record created for login/refresh (no refreshToken field)"
  - "TokenPair internal record for AuthService return type"
metrics:
  duration: ~10 min
  completed: 2026-04-06
  tasks_completed: 2
  tasks_total: 2
  files_changed: 9
  tests: 11 (integration)
---

# Phase 29 Plan 01: Auth httpOnly Cookie Refactor Summary

Refresh token transport moved from JSON body to httpOnly+Secure+SameSite=Strict cookie with Path=/api/auth, XSS-safe for PWA browser clients while preserving OTP body-based flow for Telegram bot.

## What Was Done

### Task 1: Refactor AuthController + AuthService for httpOnly cookie refresh token (TDD)

**RED:** Created `AuthCookieIntegrationTest` with 10 failing tests covering login cookie assertions, refresh via cookie, missing cookie 400, logout clears cookie, and token rotation.

**GREEN:**
- `AuthService.login()` now returns `TokenPair` (internal record) instead of `TokenResponse`
- `AuthService.refresh()` takes raw `String refreshToken` instead of `RefreshRequest`
- `AuthController.login()` sets `refresh_token` as httpOnly cookie via `ResponseCookie`, returns `AccessTokenResponse` (accessToken + expiresIn only)
- `AuthController.refresh()` reads from `@CookieValue(name = "refresh_token")`, returns `AccessTokenResponse` with new cookie
- `AuthController.logout()` invalidates token in Redis and clears cookie with `maxAge=0`
- `AuthController.verifyOtp()` unchanged — still returns full `TokenResponse` with refreshToken in body for bot clients
- `SecurityConfig`: `/auth/logout` added to `permitAll()` (cookie-authenticated, no JWT needed)
- `GlobalExceptionHandler`: `MissingRequestCookieException` handler returns 400

**Commits:** `a69d5f8` (RED), `b6ec15c` (GREEN)

### Task 2: Update integration tests for cookie-based auth flow

- Rewrote `AuthIntegrationTest` with 11 tests validating the new cookie-based contract
- Tests cover: login returns cookie + AccessTokenResponse, cookie attributes (HttpOnly/Secure/SameSite/Path), refresh via cookie, token rotation, missing cookie 400, logout clears cookie with Max-Age=0, post-logout refresh 401
- Removed duplicate `AuthCookieIntegrationTest` (merged into main test class)
- Removed dead `RefreshRequest` DTO

**Commits:** `18e4750` (tests), `003a192` (cleanup)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing error handling] MissingRequestCookieException returns 400**
- **Found during:** Task 1 GREEN phase
- **Issue:** Spring's default behavior returns 500 when `@CookieValue` is missing (caught by generic Exception handler)
- **Fix:** Added `MissingRequestCookieException` handler to `GlobalExceptionHandler` returning 400 with proper RFC 7807 body
- **Files modified:** `GlobalExceptionHandler.java`
- **Commit:** `b6ec15c`

**2. [Rule 2 - Security config] /auth/logout added to permitAll**
- **Found during:** Task 1 GREEN phase
- **Issue:** Logout endpoint was under `anyRequest().authenticated()` which requires JWT Bearer token. With cookie-based logout, the refresh token cookie IS the authentication mechanism
- **Fix:** Added `/auth/logout` to `permitAll()` matcher list in `SecurityConfig`
- **Files modified:** `SecurityConfig.java`
- **Commit:** `b6ec15c`

**3. [Rule 1 - Dead code] RefreshRequest DTO removed**
- **Found during:** Task 2
- **Issue:** `RefreshRequest` no longer referenced by any code after refactor
- **Fix:** Deleted the file
- **Files deleted:** `RefreshRequest.java`
- **Commit:** `003a192`

## Threat Surface Scan

All changes align with the plan's threat model (T-29-01 through T-29-05). No new threat surface introduced beyond what was planned.

## Known Stubs

None — all endpoints are fully wired with real implementations.

## Self-Check: PASSED

All files verified present, all deleted files confirmed removed, all 4 commits verified in git log.
