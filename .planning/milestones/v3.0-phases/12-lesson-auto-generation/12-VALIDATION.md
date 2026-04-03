---
phase: 12
slug: lesson-auto-generation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16) |
| **Config file** | `AbstractScheduleIntegrationTest.java` — shared Testcontainer base |
| **Quick run command** | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGeneration*" --info` |
| **Full suite command** | `./gradlew.bat :services:schedule-service:schedule-app:test :services:academic-service:academic-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGeneration*" --info`
- **After every plan wave:** Run `./gradlew.bat :services:schedule-service:schedule-app:test :services:academic-service:academic-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 12-01-01 | 01 | 1 | LSSN-02 | unit | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationServiceTest*"` | ❌ W0 | ⬜ pending |
| 12-01-02 | 01 | 1 | LSSN-01 | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 12-02-01 | 02 | 1 | LSSN-01 | integration | `./gradlew.bat :services:academic-service:academic-app:test --tests "*EntityMappingIntegrationTest*"` | ✅ | ⬜ pending |
| 12-02-02 | 02 | 1 | LSSN-02 | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationIntegrationTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `services/schedule-service/schedule-app/src/test/.../LessonGenerationServiceTest.java` — unit test for parity algorithm (pure Java, no Spring context)
- [ ] `services/schedule-service/schedule-app/src/test/.../LessonGenerationIntegrationTest.java` — integration test: POST template → lessons in DB, PUT template → re-generation

*Existing `EntityMappingIntegrationTest` in academic-service covers Flyway migration validation via `ddl-auto: validate`.*

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
