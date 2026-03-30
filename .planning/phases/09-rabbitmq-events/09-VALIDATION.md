---
phase: 9
slug: rabbitmq-events
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-31
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (via `spring-boot-starter-test`) |
| **Config file** | `src/test/resources/application-test.yml` (existing) |
| **Quick run command** | `./gradlew :services:academic-service:academic-app:test --tests "*EventIntegrationTest*"` |
| **Full suite command** | `./gradlew :services:academic-service:academic-app:test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:academic-service:academic-app:test --tests "*EventIntegrationTest*"`
- **After every plan wave:** Run `./gradlew :services:academic-service:academic-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | EVENT-01 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GroupEventIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 09-01-02 | 01 | 1 | EVENT-02 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*SemesterEventIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 09-01-03 | 01 | 1 | EVENT-03 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*HomeworkEventIntegrationTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `testImplementation("org.testcontainers:rabbitmq")` in `build.gradle.kts` — RabbitMQContainer class
- [ ] `src/test/.../integration/AbstractAcademicEventIntegrationTest.java` — shared base class with PostgreSQL + RabbitMQ containers
- [ ] `src/test/.../integration/GroupEventIntegrationTest.java` — stubs for EVENT-01
- [ ] `src/test/.../integration/SemesterEventIntegrationTest.java` — stubs for EVENT-02
- [ ] `src/test/.../integration/HomeworkEventIntegrationTest.java` — stubs for EVENT-03

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Rollback produces no event | EVENT-02 | Verifiable in integration test (force rollback, assert no message) | Covered by automated test — not manual |

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
