# Roadmap — Milestone 1: Auth Service + API Gateway

## Overview
| Phase | Name | Dependencies | Status |
|-------|------|-------------|--------|
| 1.1 | Auth Service Core (JWT + Login) | - | completed |
| 1.2 | Auth Service OTP + Change Password | 1.1 | planned |
| 1.3 | API Gateway JWT Filter + Routing | 1.1 | pending |
| 1.4 | Seed Data + Integration Testing | 1.1, 1.2, 1.3 | pending |

---

## Phase 1.1: Auth Service Core (JWT + Login)

**Goal**: Auth Service can authenticate users via login/password and issue JWT tokens.

**Scope**:
- RSA key pair generation + persistence (JwtService)
- JWT access/refresh token generation and validation
- Redis integration for refresh tokens and public key cache
- POST /auth/login — credential validation against academic_db
- POST /auth/refresh — token rotation
- POST /auth/logout — refresh token invalidation
- GET /auth/public-key — RSA public key endpoint
- Spring Security config (public vs authenticated routes)
- DTOs: LoginRequest, TokenResponse, RefreshRequest
- Exceptions: InvalidCredentialsException + GlobalExceptionHandler (RFC 7807)
- Auth Service connects read-only to academic_db (User entity, UserRepository)

**Requirements**: FR-1, FR-2, FR-3, FR-4, NFR-1, NFR-4

**Verification**:
- POST /auth/login with valid credentials → 200 + JWT pair
- POST /auth/login with invalid credentials → 401
- POST /auth/refresh with valid refresh token → 200 + new JWT pair
- POST /auth/logout → refresh token deleted from Redis
- GET /auth/public-key → PEM format RSA public key

---

## Phase 1.2: Auth Service OTP + Change Password

**Goal**: OTP-based authentication via Telegram and password change functionality.

**Plans:** 1/1 plans complete

Plans:
- [x] 01.2-01-PLAN.md — OTP flow (request/verify) with Redis rate limiting + change password endpoint

**Scope**:
- OtpService: generate, store, verify OTP codes in Redis
- Rate limiting: max 3 attempts/5min, resend cooldown 60sec
- POST /auth/otp/request — generate and store OTP
- POST /auth/otp/verify — verify OTP and return JWT pair
- POST /auth/change-password — authenticated password change
- DTOs: OtpRequest, OtpVerifyRequest, ChangePasswordRequest
- Exceptions: OtpExpiredException, OtpRateLimitException

**Requirements**: FR-5, FR-6

**Verification**:
- OTP request → code stored in Redis with TTL 120s
- OTP verify with correct code → JWT pair
- OTP verify with wrong code → 401
- OTP request when rate limited → 429
- Change password with correct current → 200, password_hash updated
- Change password with wrong current → 401

---

## Phase 1.3: API Gateway JWT Filter + Routing

**Goal**: Gateway validates JWT tokens and routes requests to downstream services with user context headers.

**Scope**:
- JwtAuthenticationFilter (GatewayFilter): validate Bearer token, inject X-User-Id/X-User-Role/X-Group-Id/X-Is-Headman headers
- Public key fetching from Auth Service on startup + hourly refresh
- Public routes whitelist (no JWT required)
- Route configuration verification (already in application.yml)
- Error responses for 401 Unauthorized

**Requirements**: FR-7, FR-8, FR-9

**Verification**:
- Request with valid JWT → proxied to downstream with X-headers
- Request without JWT to protected route → 401
- Request with expired JWT → 401
- Request to public route without JWT → proxied normally
- Gateway fetches public key from Auth Service on startup

---

## Phase 1.4: Seed Data + Integration Testing

**Goal**: Test data and end-to-end verification of auth flow through Gateway.

**Scope**:
- Flyway V2__seed_test_data.sql: test users (admin, student, teacher) with BCrypt passwords, test group, test semester
- End-to-end test: login through Gateway → get JWT → access protected route → verify headers
- Verify all auth endpoints through Gateway routing

**Requirements**: FR-10

**Verification**:
- Full login flow through Gateway: POST /api/auth/login → JWT
- Protected route with JWT through Gateway → 200 with correct X-headers
- OTP flow end-to-end
- Change password end-to-end
