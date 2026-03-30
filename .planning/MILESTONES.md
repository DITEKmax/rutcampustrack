# Milestones

## v1.0 Auth Service + API Gateway (Shipped: 2026-03-30)

**Phases completed:** 4 phases, 4 plans, 17 tasks
**Timeline:** 3 days (2026-03-28 → 2026-03-30)
**Tests:** 26 (15 integration + 11 unit)
**LOC:** ~2,254

**Key accomplishments:**

1. Auth Service with JWT RSA (2048-bit), login/refresh/logout, BCrypt, Spring Security
2. OTP flow with Redis-backed rate limiting (3 attempts/5min, 60s cooldown, 120s TTL)
3. API Gateway JWT filter (GlobalFilter, order -100) with null-safe header injection and RFC 7807 errors
4. Full Testcontainers integration test suite covering all auth endpoints against real PostgreSQL + Redis
5. Gateway E2E verification script (`scripts/verify-gateway-e2e.sh`)

**Archives:**
- `.planning/milestones/v1.0-ROADMAP.md`
- `.planning/milestones/v1.0-REQUIREMENTS.md`
- `docs/phase-1-report.md`

---
