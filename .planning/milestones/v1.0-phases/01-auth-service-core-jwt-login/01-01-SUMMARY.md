---
phase: 1-auth-core
phase_legacy_id: 1.1-auth-core
plan: 01
subsystem: auth-service
tags: [jwt, rsa, spring-security, redis, jpa, postgresql]
dependency_graph:
  requires: []
  provides: [jwt-auth, refresh-token-rotation, user-credentials-validation]
  affects: [api-gateway]
tech_stack:
  added:
    - spring-boot-starter-data-jpa
    - postgresql driver
  patterns:
    - RSA-256 JWT with filesystem key persistence
    - Redis refresh token storage with TTL (refresh:{userId}:{jti})
    - LowercaseEnumConverter with autoApply=true
    - RFC 7807 Problem Details error responses
key_files:
  created:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/JwtProperties.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/LowercaseEnumConverter.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/EnumConverters.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/User.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/enums/UserRole.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/enums/AccountStatus.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/repository/UserRepository.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/LoginRequest.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/RefreshRequest.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TokenResponse.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/PublicKeyResponse.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/ErrorResponse.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/InvalidCredentialsException.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/TokenRefreshException.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql
  modified:
    - services/auth-service/build.gradle.kts
    - services/auth-service/src/main/resources/application.yml
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java
decisions:
  - Auth-service reads academic_db via JPA but does not own schema (Flyway disabled, ddl-auto=validate)
  - Local enum copies in auth-service (UserRole, AccountStatus) — no dependency on academic-api-contract
  - RSA 2048-bit keys persisted to filesystem (jwt.key-dir), generated on first startup
  - Refresh token rotation: old token deleted from Redis on each use, new token issued
  - Logout is idempotent: unparseable tokens are silently ignored
metrics:
  duration: 7m 23s
  completed: 2026-03-28
  tasks_completed: 7
  files_created: 20
  files_modified: 3
---

# Phase 1.1 Plan 01: Auth Service Core (JWT + Login) Summary

JWT authentication with RSA-256 key management, Redis refresh token rotation, and read-only JPA connection to academic_db for credential validation via 4 REST endpoints.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Dependencies, Configuration, Properties | b408abe | build.gradle.kts, application.yml, JwtProperties.java, AuthApplication.java |
| 2 | Enums, Entity, Repository, DTOs, Exceptions | 1f08ff2 | 14 files (enums, User, UserRepository, 5 DTOs, 3 exception classes) |
| 3 | JwtService (RSA Key Management + JWT) | 68d7d0b | JwtService.java |
| 4 | AuthService (Login, Refresh, Logout) | b957629 | AuthService.java |
| 5 | SecurityConfig + AuthController | 90cbccd | SecurityConfig.java, AuthController.java |
| 6 | Seed Test Data (V2 Flyway migration) | 89b781c | V2__seed_test_data.sql |
| 7 | Build Verification | — | (no files, verification only) |

## What Was Built

### Auth Service Core
- **4 REST endpoints**: POST /auth/login, POST /auth/refresh, POST /auth/logout, GET /auth/public-key
- **JwtService**: Generates/loads RSA 2048-bit key pair from filesystem. Access tokens (15 min) contain sub, role, group_id, is_headman claims. Refresh tokens (7 days) contain sub + jti (UUID). Signs with RS256. Public key cached in Redis.
- **AuthService**: Login validates BCrypt password against academic_db user, checks ACTIVE status, prevents Telegram-only user login. Refresh implements token rotation (old Redis key deleted, new issued). Logout is idempotent.
- **SecurityConfig**: Stateless, CSRF disabled, all /auth/* endpoints are public, swagger endpoints public.
- **GlobalExceptionHandler**: RFC 7807 Problem Details for InvalidCredentialsException (401), TokenRefreshException (401), MethodArgumentNotValidException (400), generic Exception (500).

### Academic Service
- **V2 Flyway migration**: Seeds test group (IVT-21-1), test semester (Spring 2026), 3 test users (admin/teacher/student, password="password"), campus geofence settings (RUT MIIT coordinates).

## Decisions Made

1. **Auth-service reads academic_db but does not own its schema** — Flyway is disabled in auth-service; academic-app owns all migrations. This maintains clean schema ownership boundaries.

2. **Local enum copies in auth-service** — UserRole and AccountStatus are duplicated rather than adding a dependency on academic-api-contract, keeping auth-service independent.

3. **RSA keys persisted to filesystem** — Keys survive service restarts without external secret management. `jwt.key-dir` is configurable via env var for production deployment.

4. **Refresh token rotation** — Each refresh operation invalidates the old token in Redis and issues a new one. Prevents token reuse after theft.

5. **Idempotent logout** — Invalid or expired tokens during logout are silently ignored, allowing clients to call logout even after token expiry.

## Deviations from Plan

None — plan executed exactly as written.

## Build Results

- `./gradlew.bat :services:auth-service:build` — BUILD SUCCESSFUL
- `./gradlew.bat :services:academic-service:academic-app:build` — BUILD SUCCESSFUL

## Manual Integration Test Plan

Prerequisites: `docker compose up -d` (PostgreSQL academic_db, Redis running)

1. Start academic-app to run V1+V2 Flyway migrations:
   ```bash
   ./gradlew.bat :services:academic-service:academic-app:bootRun --no-daemon
   ```
   Wait for startup, then stop (migrations persisted to DB).

2. Start auth-service with local profile:
   ```bash
   ./gradlew.bat :services:auth-service:bootRun --no-daemon --args='--spring.profiles.active=local'
   ```

3. Test endpoints:
   ```bash
   # Login (expect 200 + accessToken + refreshToken)
   curl -s -X POST http://localhost:9090/auth/login \
     -H "Content-Type: application/json" \
     -d '{"login":"student","password":"password"}' | jq .

   # Invalid credentials (expect 401 RFC 7807)
   curl -s -X POST http://localhost:9090/auth/login \
     -H "Content-Type: application/json" \
     -d '{"login":"student","password":"wrong"}' | jq .

   # Refresh (use refreshToken from login, expect 200 + new tokens)
   curl -s -X POST http://localhost:9090/auth/refresh \
     -H "Content-Type: application/json" \
     -d '{"refreshToken":"<paste_refresh_token>"}' | jq .

   # Public key (expect 200 + PEM + "RS256")
   curl -s http://localhost:9090/auth/public-key | jq .

   # Logout (expect 204)
   curl -s -X POST http://localhost:9090/auth/logout \
     -H "Content-Type: application/json" \
     -d '{"refreshToken":"<paste_refresh_token>"}' -w "\n%{http_code}"

   # Refresh after logout (expect 401)
   curl -s -X POST http://localhost:9090/auth/refresh \
     -H "Content-Type: application/json" \
     -d '{"refreshToken":"<same_token>"}' | jq .
   ```

## Self-Check: PASSED
