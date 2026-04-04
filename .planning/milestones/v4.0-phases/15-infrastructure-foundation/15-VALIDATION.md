---
phase: 15
slug: infrastructure-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `services/attendance-service/attendance-app/src/test/resources/application-test.yml` |
| **Quick run command** | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*UnitTest*"` |
| **Full suite command** | `./gradlew.bat :services:attendance-service:attendance-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick unit tests
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| TBD | 01 | 1 | INFRA-01 | integration | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*MongoIndex*"` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | INFRA-02 | integration | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*EnumSerialization*"` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | INFRA-03 | unit | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*ScheduleGrpcClient*"` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | INFRA-04 | unit | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*AcademicGrpcClient*"` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | INFRA-05 | integration | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*RabbitConfig*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Abstract base test class with `@Testcontainers` + `@DynamicPropertySource` for MongoDB + RabbitMQ
- [ ] `application-test.yml` with test profile configuration
- [ ] Testcontainers MongoDB + RabbitMQ dependencies in build.gradle.kts

*Wave 0 installs test infrastructure needed by all subsequent test tasks.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| gRPC round-trip to live Schedule/Academic services | INFRA-03, INFRA-04 | Requires all 3 services running | Start all services via docker-compose, hit health endpoint, verify no gRPC errors in logs |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
