---
phase: 1-auth-core
phase_legacy_id: 1.1-auth-core
verified: 2026-03-29T12:00:00Z
status: passed
score: 9/9 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 8/9
  gaps_closed:
    - "POST /auth/logout invalidates refresh token in Redis (FR-3.2: logout is now an authenticated endpoint)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Full login + refresh + logout cycle with real Redis"
    expected: "POST /auth/login returns 200 with accessToken+refreshToken; POST /auth/refresh returns new pair; POST /auth/logout with valid Bearer returns 204; POST /auth/logout without Bearer returns 401; second POST /auth/refresh with old token returns 401"
    why_human: "Requires running auth-service with local profile and live Redis/PostgreSQL to verify end-to-end token lifecycle"
  - test: "RSA key persistence across restart"
    expected: "Tokens issued before restart remain valid after restart (keys reloaded from filesystem, not regenerated)"
    why_human: "Requires starting auth-service, issuing a token, stopping, restarting, and re-validating — cannot verify from static analysis"
---

# Phase 1.1: Auth Service Core (JWT + Login) Verification Report

**Phase Goal:** Auth Service can authenticate users via login/password and issue JWT tokens.
**Verified:** 2026-03-29
**Status:** passed — all 9 must-haves verified
**Re-verification:** Yes — after gap closure (FR-3.2 logout authentication enforcement)

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | POST /auth/login with valid credentials returns 200 with accessToken + refreshToken | VERIFIED | AuthController.login() → AuthService.login() → UserRepository.findByLogin() + BCrypt match → JwtService.generateAccessToken/generateRefreshToken → TokenResponse(accessToken, refreshToken, expiresIn) |
| 2 | POST /auth/login with invalid credentials returns 401 with RFC 7807 error | VERIFIED | AuthService throws InvalidCredentialsException on bad creds; GlobalExceptionHandler returns HTTP 401 with ErrorResponse record (type, title, status, detail, instance, timestamp) |
| 3 | POST /auth/refresh with valid refresh token returns new accessToken + refreshToken | VERIFIED | AuthService.refresh() parses token, checks Redis key exists, deletes old, generates new pair, stores new refresh:{userId}:{jti} in Redis, returns TokenResponse |
| 4 | POST /auth/refresh with invalid/expired refresh token returns 401 | VERIFIED | AuthService.refresh() catches parse exception and throws TokenRefreshException; GlobalExceptionHandler returns HTTP 401 |
| 5 | POST /auth/logout invalidates refresh token in Redis | VERIFIED | Token deletion via redisTemplate.delete("refresh:{userId}:{jti}") is implemented correctly. SecurityConfig now places /auth/logout under .anyRequest().authenticated() — JWT filter validates Bearer token before handler is reached. JwtAuthenticationFilter validates RS256 token and populates SecurityContext. FR-3.2 is satisfied. |
| 6 | GET /auth/public-key returns RSA public key in PEM format | VERIFIED | AuthController.getPublicKey() → AuthService.getPublicKey() → JwtService.getPublicKeyPem() returns PEM string; PublicKeyResponse record includes algorithm "RS256" |
| 7 | RSA keys persist across restarts (loaded from filesystem) | VERIFIED | JwtService.init() checks Files.exists(privateKeyPath) and Files.exists(publicKeyPath) before generating; loads from jwt.key-dir (default ./keys, overridable via JWT_KEY_DIR env var) |
| 8 | Refresh tokens stored in Redis with 7-day TTL | VERIFIED | redisTemplate.opsForValue().set(redisKey, "valid", Duration.ofSeconds(jwtProperties.refreshTokenExpiration())); application.yml sets refresh-token-expiration: 604800 (7 days) |
| 9 | Only active users can authenticate (status=active checked) | VERIFIED | AuthService.login() checks user.getStatus() != AccountStatus.ACTIVE → throws InvalidCredentialsException; AccountStatus enum has ACTIVE, EXPELLED, SUSPENDED, ARCHIVED; LowercaseEnumConverter auto-maps to/from DB lowercase strings |

**Score:** 9/9 truths verified

---

## Required Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java` | RSA key management, JWT generation and validation | VERIFIED | 148 lines; RSA 2048 keygen/load, generateAccessToken (RS256, sub+role+group_id+is_headman claims, 15min TTL), generateRefreshToken (RS256, sub+jti, 7-day TTL), parseToken, getPublicKeyPem |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java` | Login, refresh, logout business logic | VERIFIED | 114 lines; full login/refresh/logout/getPublicKey logic, Redis integration, token rotation |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` | REST endpoints: login, refresh, logout, public-key | VERIFIED | 4 endpoints: POST /auth/login, POST /auth/refresh, POST /auth/logout (204), GET /auth/public-key; @Valid on request bodies; Swagger @Operation annotations |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java` | Spring Security filter chain, public/authenticated routes | VERIFIED | Stateless, CSRF disabled, PasswordEncoder bean; /auth/logout is under .anyRequest().authenticated() (NOT in permitAll); JwtAuthenticationFilter added before UsernamePasswordAuthenticationFilter via addFilterBefore() |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java` | Bearer token validation filter (gap fix artifact) | VERIFIED | 56 lines; OncePerRequestFilter; extracts Bearer token from Authorization header; calls jwtService.parseToken(); extracts sub+role claims; populates SecurityContextHolder with UsernamePasswordAuthenticationToken + ROLE_ authority; invalid tokens silently ignored (Spring Security handles as unauthenticated) |
| `services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/User.java` | JPA entity mapping to academic_db.users table | VERIFIED | All required fields: id, login, passwordHash, role (UserRole), status (AccountStatus), isHeadman, groupId; @Table(name="users"); Lombok @Getter @NoArgsConstructor |
| `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql` | Test users with BCrypt password hashes | VERIFIED | Inserts admin/teacher/student with BCrypt hash of "password" (cost 10), test group IVT-21-1, test semester Spring 2026, campus geofence settings |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| AuthController | AuthService | Constructor injection | WIRED | AuthController(AuthService authService); all 4 handler methods call authService.* |
| AuthService | UserRepository | findByLogin() for credential validation | WIRED | userRepository.findByLogin(request.login()) in login(); userRepository.findById(userId) in refresh() |
| AuthService | JwtService | generateAccessToken/generateRefreshToken | WIRED | jwtService.generateAccessToken(user), jwtService.generateRefreshToken(user), jwtService.extractJti/extractUserId used throughout |
| AuthService | StringRedisTemplate | refresh:{userId}:{jti} key storage and deletion | WIRED | redisTemplate.opsForValue().set() with TTL in login/refresh; redisTemplate.delete() in refresh (rotation) and logout; redisTemplate.hasKey() in refresh validation |
| JwtService | RSA keys on filesystem | Load from jwt.key-dir, generate if missing | WIRED | @PostConstruct init() checks Files.exists() → loads via loadPrivateKey/loadPublicKey; generates and writes if missing; jwt.key-dir: ${JWT_KEY_DIR:./keys} |
| SecurityConfig | JwtAuthenticationFilter | addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class) | WIRED | Constructor-injected; filter added in securityFilterChain() bean; /auth/logout falls under .anyRequest().authenticated() — filter runs before every request |
| JwtAuthenticationFilter | JwtService | parseToken() | WIRED | JwtAuthenticationFilter(JwtService jwtService) constructor injection; jwtService.parseToken(token) called in doFilterInternal() to validate access tokens |

---

## Data-Flow Trace (Level 4)

Not applicable — this phase produces an API service (no UI components rendering dynamic data). All data flows are through REST endpoints verified via key link analysis above.

---

## Behavioral Spot-Checks

Step 7b: SKIPPED — Running the auth service requires live Docker infrastructure (PostgreSQL academic_db + Redis). Cannot verify endpoint behavior without starting the service stack. Integration test plan is documented in SUMMARY.md.

---

## Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|---------|
| FR-1.1 | POST /auth/login accepts login + password, returns JWT pair (accessToken, refreshToken, expiresIn) | SATISFIED | AuthController.login() → TokenResponse(accessToken, refreshToken, expiresIn) |
| FR-1.2 | Validate credentials against users table in academic_db (BCrypt) | SATISFIED | UserRepository.findByLogin() queries academic_db; passwordEncoder.matches() verifies BCrypt |
| FR-1.3 | Only active users (status='active') can login | SATISFIED | AccountStatus.ACTIVE check in AuthService.login(); LowercaseEnumConverter maps 'active' ↔ ACTIVE |
| FR-1.4 | Access Token: JWT RS256, 15 min TTL, claims: sub, role, group_id, is_headman | SATISFIED | JwtService.generateAccessToken() sets sub=userId, role, group_id, is_headman; RS256 via Jwts.SIG.RS256; TTL=900s |
| FR-1.5 | Refresh Token: JWT, 7 days TTL, stored in Redis as refresh:{user_id}:{jti} | SATISFIED | JwtService.generateRefreshToken() with jti=UUID; stored as "refresh:{userId}:{jti}" with TTL=604800s |
| FR-2.1 | POST /auth/refresh accepts refreshToken, returns new JWT pair | SATISFIED | AuthService.refresh() returns new TokenResponse |
| FR-2.2 | Validate refresh token exists in Redis and not expired | SATISFIED | redisTemplate.hasKey(redisKey) check; token parse catches expiry |
| FR-2.3 | Delete old refresh token, create new one (rotation) | SATISFIED | redisTemplate.delete(redisKey) before generating new pair |
| FR-3.1 | POST /auth/logout invalidates refresh token (delete from Redis) | SATISFIED | redisTemplate.delete("refresh:{userId}:{jti}") in AuthService.logout() |
| FR-3.2 | Requires valid JWT (authenticated endpoint) | SATISFIED | /auth/logout removed from permitAll(); falls under .anyRequest().authenticated(); JwtAuthenticationFilter validates Bearer token and populates SecurityContextHolder before the handler is invoked |
| FR-4.1 | GET /auth/public-key returns RSA public key in PEM format | SATISFIED | PublicKeyResponse.publicKey contains PEM string from JwtService.getPublicKeyPem() |
| FR-4.2 | Key cached in Redis with TTL 3600 sec | SATISFIED | JwtService.init(): redisTemplate.opsForValue().set("jwt:public_key", publicKeyPem, Duration.ofSeconds(3600)) |
| NFR-1 | RSA key pair generated on first startup, persisted; BCrypt strength 10; no plaintext secrets | SATISFIED | RSA keygen in JwtService.init(); BCryptPasswordEncoder() in SecurityConfig (default strength 10); POSTGRES_ACADEMIC_PASSWORD and JWT_KEY_DIR externalized via env vars with dev defaults |
| NFR-4 | No api-contract module; DTOs as Java records; RFC 7807; Spring Security config | SATISFIED | No api-contract module; LoginRequest, RefreshRequest, TokenResponse, PublicKeyResponse, ErrorResponse are all records; GlobalExceptionHandler uses ErrorResponse record; SecurityConfig present |

**Orphaned requirements check:** FR-5, FR-6, FR-7, FR-8, FR-9, FR-10 mapped to phases 1.2, 1.3, 1.4 in ROADMAP.md — not orphaned for this phase.

---

## Anti-Patterns Found

No blockers found. No TODO/FIXME/PLACEHOLDER comments. No return null/empty stubs. No empty lambda handlers. All implementations are substantive.

Previous warning (SecurityConfig.java: /auth/logout is permitAll()) is now RESOLVED — /auth/logout is under .anyRequest().authenticated() in the updated SecurityConfig.

---

## Human Verification Required

### 1. Full Authentication Lifecycle (including authenticated logout)

**Test:** Start docker compose, run academic-app to apply Flyway migrations, then start auth-service with `--spring.profiles.active=local`. Execute in sequence:
1. POST /auth/login with `{"login":"student","password":"password"}` — save accessToken and refreshToken
2. POST /auth/refresh with the refreshToken — verify new pair returned
3. POST /auth/logout with `Authorization: Bearer <accessToken>` and body `{"refreshToken":"<refreshToken>"}` — verify 204
4. POST /auth/logout without Authorization header — verify 401
5. POST /auth/refresh with the old refreshToken — verify 401

**Expected:** Steps 1-2 return 200 with token pairs. Step 3 returns 204. Step 4 returns 401 (Spring Security rejects unauthenticated request). Step 5 returns 401 (token already deleted from Redis).
**Why human:** Requires running Docker infrastructure (PostgreSQL + Redis) and live service startup.

### 2. RSA Key Persistence Across Restart

**Test:** Start auth-service, record an issued accessToken, stop the service, restart it (keys directory unchanged), then attempt to validate/use the previously issued token.
**Expected:** Token remains valid after restart — keys loaded from filesystem, not regenerated.
**Why human:** Requires service start/stop cycle with filesystem state between runs.

---

## Gaps Summary

No gaps. All 9 must-haves are verified.

**Gap closed since initial verification:**

FR-3.2 (logout authentication enforcement) is now satisfied. The fix introduced two changes:

1. `JwtAuthenticationFilter.java` — a new `OncePerRequestFilter` that reads the `Authorization: Bearer` header, validates the token via `JwtService.parseToken()`, and sets `SecurityContextHolder` authentication. Invalid or missing tokens do not set authentication (allowing Spring Security to reject protected endpoints with 401).

2. `SecurityConfig.java` — `/auth/logout` was removed from the `permitAll()` matcher list. It now falls under `.anyRequest().authenticated()`. The `JwtAuthenticationFilter` is wired before `UsernamePasswordAuthenticationFilter` via `addFilterBefore()`. Callers must present a valid Bearer access token to reach the logout handler; unauthenticated requests will receive 401.

The phase goal ("Auth Service can authenticate users via login/password and issue JWT tokens") is fully achieved. All endpoints are implemented, all security constraints are enforced, and all data flows are wired correctly.

---

_Verified: 2026-03-29_
_Verifier: Claude (gsd-verifier)_
