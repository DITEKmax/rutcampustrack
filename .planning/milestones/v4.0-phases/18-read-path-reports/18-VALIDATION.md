---
phase: 18
slug: read-path-reports
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (MongoDB, Redis, RabbitMQ) |
| **Config file** | `services/attendance-service/attendance-app/build.gradle.kts` |
| **Quick run command** | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "ru.rutcampustrack.attendance.report.*"` |
| **Full suite command** | `./gradlew.bat :services:attendance-service:attendance-app:test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick run command (report tests only)
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 18-01-01 | 01 | 1 | RPRT-05 | arch-unit | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*ArchitectureTest*"` | ❌ W0 | ⬜ pending |
| 18-01-02 | 01 | 1 | RPRT-01 | integration | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*LessonReportControllerTest*"` | ❌ W0 | ⬜ pending |
| 18-02-01 | 02 | 2 | RPRT-02 | integration | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*JournalControllerTest*"` | ❌ W0 | ⬜ pending |
| 18-02-02 | 02 | 2 | RPRT-03 | integration | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*StudentStatsControllerTest*"` | ❌ W0 | ⬜ pending |
| 18-02-03 | 02 | 2 | RPRT-04 | integration | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*StudentRecordsControllerTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Add `com.tngtech.archunit:archunit-junit5:1.3.0` to `attendance-app/build.gradle.kts` testImplementation
- [ ] Stub test classes for report domain tests extending `AbstractAttendanceIntegrationTest`

*Existing Testcontainers infrastructure (MongoDB, Redis, RabbitMQ) covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Journal grid renders correctly in frontend | RPRT-02 | Frontend rendering not in scope | Verify JSON shape matches expected nested structure |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
