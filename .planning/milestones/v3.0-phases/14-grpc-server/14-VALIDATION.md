---
phase: 14
slug: grpc-server
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `services/schedule-service/schedule-app/build.gradle.kts` |
| **Quick run command** | `./gradlew :services:schedule-service:schedule-app:test --tests "ru.rutcampustrack.schedule.grpc.*"` |
| **Full suite command** | `./gradlew :services:schedule-service:schedule-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:schedule-service:schedule-app:test --tests "ru.rutcampustrack.schedule.grpc.*"`
- **After every plan wave:** Run `./gradlew :services:schedule-service:schedule-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 1 | GRPC-01 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getActiveLesson_returnsActiveLesson"` | ❌ W0 | ⬜ pending |
| 14-01-02 | 01 | 1 | GRPC-01 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getActiveLesson_notFound*"` | ❌ W0 | ⬜ pending |
| 14-01-03 | 01 | 1 | GRPC-01 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getActiveLesson_multipleActive_returnsFirstByLessonNumber"` | ❌ W0 | ⬜ pending |
| 14-01-04 | 01 | 1 | GRPC-02 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getLessonById_*"` | ❌ W0 | ⬜ pending |
| 14-01-05 | 01 | 1 | GRPC-02 | integration | same class | ❌ W0 | ⬜ pending |
| 14-01-06 | 01 | 1 | GRPC-03 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getLessonsByGroup_*"` | ❌ W0 | ⬜ pending |
| 14-01-07 | 01 | 1 | GRPC-03 | integration | same class | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplTest.java` — stubs for GRPC-01, GRPC-02, GRPC-03
- [ ] New native query `findActiveLessonForGroup` in `LessonRepository` — required by GRPC-01

*Existing infrastructure covers all setup — no new framework install needed.*

---

## Manual-Only Verifications

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
