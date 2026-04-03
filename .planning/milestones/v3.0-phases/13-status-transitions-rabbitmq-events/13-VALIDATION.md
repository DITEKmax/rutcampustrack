---
phase: 13
slug: status-transitions-rabbitmq-events
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `services/schedule-service/schedule-app/src/test/resources/application-test.yml` |
| **Quick run command** | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.integration.*"` |
| **Full suite command** | `./gradlew.bat :services:schedule-service:schedule-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.integration.*"`
- **After every plan wave:** Run `./gradlew.bat :services:schedule-service:schedule-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | EVNT-01 | unit | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.event.*"` | ❌ W0 | ⬜ pending |
| 13-01-02 | 01 | 1 | EVNT-02 | unit | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.event.*"` | ❌ W0 | ⬜ pending |
| 13-02-01 | 02 | 1 | CRON-01 | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.integration.LessonStatusTransition*"` | ❌ W0 | ⬜ pending |
| 13-02-02 | 02 | 1 | CRON-02 | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.integration.LessonStatusTransition*"` | ❌ W0 | ⬜ pending |
| 13-02-03 | 02 | 1 | CRON-03 | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.integration.LessonStatusTransition*"` | ❌ W0 | ⬜ pending |
| 13-03-01 | 03 | 2 | EVNT-03 | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.integration.*Cancel*"` | ❌ W0 | ⬜ pending |
| 13-03-02 | 03 | 2 | EVNT-04 | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*.integration.*Event*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Integration test stubs for cron transitions with `Clock.fixed()` time control
- [ ] Event publishing verification via mocked `RabbitTemplate` (already in `AbstractScheduleIntegrationTest`)

*Existing infrastructure covers most phase requirements — `AbstractScheduleIntegrationTest` already mocks `RabbitTemplate`.*

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
