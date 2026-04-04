---
phase: 17
slug: write-path-geo-checkin-manual-marking
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test + Testcontainers |
| **Config file** | `services/attendance-service/attendance-app/build.gradle.kts` |
| **Quick run command** | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*GeoUtilsTest" --tests "*CheckinRateLimiterTest"` |
| **Full suite command** | `./gradlew.bat :services:attendance-service:attendance-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*GeoUtilsTest" --tests "*CheckinRateLimiterTest"`
- **After every plan wave:** Run `./gradlew.bat :services:attendance-service:attendance-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 17-01-01 | 01 | 0 | INFRA-06 | config | `./gradlew.bat :services:attendance-service:attendance-app:dependencies` | ❌ W0 | ⬜ pending |
| 17-02-01 | 02 | 1 | CHKN-01 | unit | `*GeoUtilsTest` | ❌ W0 | ⬜ pending |
| 17-02-02 | 02 | 1 | CHKN-01 | integration | Full suite | ❌ W0 | ⬜ pending |
| 17-03-01 | 03 | 1 | CHKN-05, CHKN-06, CHKN-07 | unit | `*CheckinRateLimiterTest` | ❌ W0 | ⬜ pending |
| 17-04-01 | 04 | 2 | CHKN-01..07 | integration | Full suite | ❌ W0 | ⬜ pending |
| 17-05-01 | 05 | 2 | MARK-01, MARK-02 | integration | Full suite | ❌ W0 | ⬜ pending |
| 17-06-01 | 06 | 3 | INFRA-06 | integration | Full suite | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `build.gradle.kts` — add `spring-boot-starter-data-redis` + `testcontainers:redis`
- [ ] `AbstractAttendanceIntegrationTest.java` — add Redis `GenericContainer` + `@DynamicPropertySource`
- [ ] `application-test.yml` — add `spring.data.redis.*` placeholder properties
- [ ] Exception classes — `OutsideGeofenceException`, `RateLimitExceededException` + handlers in `GlobalExceptionHandler`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| *None* | — | — | — |

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
