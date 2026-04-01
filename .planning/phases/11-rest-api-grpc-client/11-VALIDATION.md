---
phase: 11
slug: rest-api-grpc-client
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers + MockMvc |
| **Config file** | `services/schedule-service/schedule-app/src/test/resources/application-test.yml` |
| **Quick run command** | `.\gradlew :services:schedule-service:schedule-app:test --tests "*.integration.*"` |
| **Full suite command** | `.\gradlew :services:schedule-service:schedule-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew :services:schedule-service:schedule-app:test --tests "*.integration.*" -x javadoc`
- **After every plan wave:** Run `.\gradlew :services:schedule-service:schedule-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | TMPL-01 | integration | `--tests "*.ScheduleItemApiTest.createTemplate*"` | ❌ W0 | ⬜ pending |
| 11-01-02 | 01 | 1 | TMPL-02 | integration | `--tests "*.ScheduleItemApiTest.updateTemplate*"` | ❌ W0 | ⬜ pending |
| 11-01-03 | 01 | 1 | TMPL-03 | integration | `--tests "*.ScheduleItemApiTest.deleteTemplate*"` | ❌ W0 | ⬜ pending |
| 11-01-04 | 01 | 1 | TMPL-04 | integration | `--tests "*.ScheduleItemApiTest.listTemplates*"` | ❌ W0 | ⬜ pending |
| 11-01-05 | 01 | 1 | TMPL-05 | integration | `--tests "*.ScheduleItemApiTest.grpcValidation*"` | ❌ W0 | ⬜ pending |
| 11-02-01 | 02 | 1 | LSSN-04 | integration | `--tests "*.LessonApiTest.cancelLesson*"` | ❌ W0 | ⬜ pending |
| 11-02-02 | 02 | 1 | LSSN-05 | integration | `--tests "*.LessonApiTest.restoreLesson*"` | ❌ W0 | ⬜ pending |
| 11-02-03 | 02 | 1 | LSSN-06 | integration | `--tests "*.LessonApiTest.massCancelLessons*"` | ❌ W0 | ⬜ pending |
| 11-02-04 | 02 | 1 | LSSN-07 | integration | `--tests "*.LessonApiTest.toggleGeoBlock*"` | ❌ W0 | ⬜ pending |
| 11-03-01 | 03 | 2 | VIEW-01 | integration | `--tests "*.ScheduleViewTest.anyRoleCanView*"` | ❌ W0 | ⬜ pending |
| 11-03-02 | 03 | 2 | VIEW-02 | integration | `--tests "*.ScheduleViewTest.responseContainsAllFields*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java` — stubs for TMPL-01..05
- [ ] `src/test/java/ru/rutcampustrack/schedule/integration/LessonApiTest.java` — stubs for LSSN-04..07
- [ ] `src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewTest.java` — stubs for VIEW-01..02
- [ ] `build.gradle.kts` — add `grpc-client-spring-boot-starter` + protobuf plugin

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
