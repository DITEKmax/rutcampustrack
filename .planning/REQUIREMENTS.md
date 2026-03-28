# Requirements — Milestone 1: Auth Service + API Gateway

## Functional Requirements

### FR-1: User Login
- **FR-1.1**: POST /auth/login accepts login + password, returns JWT pair (accessToken, refreshToken, expiresIn)
- **FR-1.2**: Validate credentials against `users` table in academic_db (BCrypt)
- **FR-1.3**: Only active users (status='active') can login
- **FR-1.4**: Access Token: JWT with RSA signature, 15 min TTL, claims: sub (user_id), role, group_id, is_headman
- **FR-1.5**: Refresh Token: JWT, 7 days TTL, stored in Redis as `refresh:{user_id}:{jti}`

### FR-2: Token Refresh
- **FR-2.1**: POST /auth/refresh accepts refreshToken, returns new JWT pair
- **FR-2.2**: Validate refresh token exists in Redis and not expired
- **FR-2.3**: Delete old refresh token, create new one (rotation)

### FR-3: Logout
- **FR-3.1**: POST /auth/logout invalidates refresh token (delete from Redis)
- **FR-3.2**: Requires valid JWT (authenticated endpoint)

### FR-4: Public Key Endpoint
- **FR-4.1**: GET /auth/public-key returns RSA public key in PEM format
- **FR-4.2**: Key cached in Redis with TTL 3600 sec

### FR-5: OTP Flow
- **FR-5.1**: POST /auth/otp/request accepts telegram_id, generates 6-digit code, stores in Redis (TTL 120 sec)
- **FR-5.2**: Rate limiting: max 3 attempts per 5 min (otp_attempts:{telegram_id}), resend cooldown 60 sec (otp_sent:{telegram_id})
- **FR-5.3**: POST /auth/otp/verify accepts telegram_id + code, returns JWT pair on success
- **FR-5.4**: Verify OTP matches, not expired, attempts not exceeded

### FR-6: Change Password
- **FR-6.1**: POST /auth/change-password accepts currentPassword + newPassword
- **FR-6.2**: Requires valid JWT (authenticated)
- **FR-6.3**: Verify current password, update password_hash, set password_changed=true, clear initial_password

### FR-7: API Gateway JWT Filter
- **FR-7.1**: On startup, fetch public key from Auth Service (GET http://auth-service:9090/auth/public-key)
- **FR-7.2**: Cache key, refresh every hour
- **FR-7.3**: For non-public routes: validate Bearer token, check signature and expiry
- **FR-7.4**: Extract claims, inject headers: X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman
- **FR-7.5**: Invalid/missing token → 401 Unauthorized

### FR-8: Gateway Routing
- **FR-8.1**: /api/auth/** → auth-service:9090
- **FR-8.2**: /api/academic/** → academic-service:9091
- **FR-8.3**: /api/schedule/** → schedule-service:9092
- **FR-8.4**: /api/attendance/**, /api/reports/** → attendance-service:9093
- **FR-8.5**: /api/ws/** → notification-web:9094

### FR-9: Public Routes (no JWT)
- **FR-9.1**: /api/auth/login, /api/auth/otp/**, /api/auth/public-key, /api/auth/refresh

### FR-10: Test Seed Data
- **FR-10.1**: Flyway V2 migration with test users: admin, student, teacher (with BCrypt hashed passwords)
- **FR-10.2**: Test group and semester for integration testing

## Non-Functional Requirements

### NFR-1: Security
- RSA key pair generated on first startup, persisted to filesystem
- BCrypt password hashing (strength 10)
- No plaintext secrets in code or config (externalized via env vars)

### NFR-2: Performance
- JWT validation in Gateway < 5ms (local crypto, no network call)
- Redis operations < 10ms

### NFR-3: Reliability
- Gateway retries public key fetch on startup failure
- Graceful handling of Redis unavailability

### NFR-4: Conventions Compliance
- Auth Service: no separate api-contract module (per phases-plan.md)
- DTOs as Java records
- RFC 7807 error responses
- Spring Security config: public vs authenticated routes
