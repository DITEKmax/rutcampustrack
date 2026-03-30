---
phase: 5
slug: entity-and-repository-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-30
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `services/academic-service/academic-app/src/test/resources/application-test.yml` |
| **Quick run command** | `./gradlew.bat :services:academic-service:academic-app:test --tests "*EntityValidationTest"` |
| **Full suite command** | `./gradlew.bat :services:academic-service:academic-app:test` |
| **Estimated runtime** | ~30 seconds (Testcontainers PostgreSQL startup + tests) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:academic-service:academic-app:test`
- **After every plan wave:** Run `./gradlew.bat :services:academic-service:academic-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | USER-01..05 | integration | `./gradlew.bat :services:academic-service:academic-app:test --tests "*UserRepositoryTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | GSEM-01..04 | integration | `./gradlew.bat :services:academic-service:academic-app:test --tests "*SemesterRepositoryTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | SUBJ-01..03 | integration | `./gradlew.bat :services:academic-service:academic-app:test --tests "*SubjectRepositoryTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | ASST-01..03 | integration | `./gradlew.bat :services:academic-service:academic-app:test --tests "*HeadmanAssistantRepositoryTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | HW-01..03 | integration | `./gradlew.bat :services:academic-service:academic-app:test --tests "*HomeworkRepositoryTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | THRSH-01..04 | integration | `./gradlew.bat :services:academic-service:academic-app:test --tests "*AttendanceThresholdRepositoryTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `application-test.yml` — test profile excluding RabbitMQ autoconfiguration
- [ ] `AbstractAcademicIntegrationTest.java` — base class with Testcontainers PostgreSQL + Flyway
- [ ] Testcontainers dependencies in `academic-app/build.gradle.kts`

*Wave 0 establishes the test infrastructure that all subsequent integration tests depend on.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Auth Service startup compatibility | Success Criteria #4 | Cross-service validation | Start auth-service against academic_db after V3 migration; verify no schema errors |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
