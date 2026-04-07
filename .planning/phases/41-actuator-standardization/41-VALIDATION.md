---
phase: 41
slug: actuator-standardization
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 41 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | none — each service uses `@SpringBootTest` with `@ActiveProfiles("test")` |
| **Quick run command** | `./gradlew.bat :services:auth-service:test --tests "*ActuatorIT"` |
| **Full suite command** | `./gradlew.bat :services:auth-service:test :services:academic-service:academic-app:test :services:schedule-service:schedule-app:test :services:attendance-service:attendance-app:test` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:{service}:test --tests "*ActuatorIT"`
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 41-01-01 | 01 | 1 | MON-01 | T-41-01 | /actuator/health returns 200 UP | integration | `./gradlew.bat :services:auth-service:test --tests "*ActuatorIT"` | ❌ W0 | ⬜ pending |
| 41-01-02 | 01 | 1 | MON-01 | T-41-01 | /actuator/info returns 200 | integration | same | ❌ W0 | ⬜ pending |
| 41-01-03 | 01 | 1 | MON-02 | T-41-02 | /actuator/env returns 404 in prod | integration | same with `@ActiveProfiles("prod")` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java` — stubs for MON-01, MON-02
- [ ] `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java` — stubs for MON-01, MON-02
- [ ] `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ActuatorIT.java` — stubs for MON-01, MON-02
- [ ] `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ActuatorIT.java` — stubs for MON-01, MON-02

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| None | — | All verifiable via integration tests | — |

---

## Regression Guardrails

- Existing test suites for all 4 services must remain green after actuator addition
- No new endpoints beyond health and info should appear in `/actuator` response
