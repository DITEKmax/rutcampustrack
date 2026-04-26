---
phase: 3
phase_legacy_id: 1.3
slug: api-gateway-jwt-filter-routing
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-29
updated: 2026-03-29
---

# Phase 3 (legacy 1.3) — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (via `spring-boot-starter-test`) + Gradle build |
| **Config file** | build.gradle.kts (api-gateway module) |
| **Quick run command** | `./gradlew.bat :services:api-gateway:compileJava` |
| **Full suite command** | `./gradlew.bat :services:api-gateway:test` |
| **Estimated runtime** | ~20 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:api-gateway:compileJava`
- **After every plan wave:** Run `./gradlew.bat :services:api-gateway:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 20 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01.3-01-01 | 01 | 1 | FR-7 | build | `./gradlew.bat :services:api-gateway:compileJava` | yes | pending |
| 01.3-01-02 | 01 | 1 | FR-7, FR-9 | build | `./gradlew.bat :services:api-gateway:compileJava` | yes | pending |
| 01.3-01-03 | 01 | 1 | FR-7.3, FR-7.5, FR-9.1 | unit test | `./gradlew.bat :services:api-gateway:test` | yes | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

Unit test scaffolds created in Task 3 of Plan 01:

- [x] `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` — 9 unit tests for filter (mock `ServerWebExchange`)
- [x] `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/config/PublicKeyConfigTest.java` — 2 unit tests for PEM parsing and missing-key exception

---

## Test Coverage Map

| Req ID | Behavior | Test File | Test Count |
|--------|----------|-----------|------------|
| FR-7.3 | Valid JWT passes filter, headers injected | JwtAuthenticationFilterTest | 3 (valid JWT, group_id present, group_id absent) |
| FR-7.5 | Missing/invalid token -> 401 RFC 7807 | JwtAuthenticationFilterTest | 3 (no header, bad prefix, malformed JWT) |
| FR-7 (expiry) | Expired JWT -> 401 | JwtAuthenticationFilterTest | 1 |
| FR-9.1 | Public routes bypass filter | JwtAuthenticationFilterTest | 2 (exact path, prefix match) |
| FR-7.1 | PEM key parsing | PublicKeyConfigTest | 1 |
| FR-7.1 | Missing key -> IllegalStateException | PublicKeyConfigTest | 1 |

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| JWT validation with real token | FR-7 | Requires running Auth Service + Gateway | Start docker compose, auth-service, gateway. Login, use JWT to hit protected route |
| Public route bypass | FR-9 | Requires running Gateway | Send request to /api/auth/login without JWT, verify 200 |
| Header injection | FR-7 | Requires downstream service inspection | Check X-User-Id/X-User-Role headers arrive at downstream |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 20s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** ready
