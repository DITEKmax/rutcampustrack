---
phase: 10
slug: foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-31
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (via `spring-boot-starter-test`) |
| **Config file** | None — uses JUnit Platform via Gradle `useJUnitPlatform()` in root `build.gradle.kts` |
| **Quick run command** | `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest"` |
| **Full suite command** | `./gradlew :services:schedule-service:schedule-app:test` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest" --tests "*.SecuritySmokeTest"`
- **After every plan wave:** Run `./gradlew :services:schedule-service:schedule-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 1 | LSSN-03 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest"` | Wave 0 | pending |
| 10-01-02 | 01 | 1 | CRON-04 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest"` | Wave 0 | pending |
| 10-01-03 | 01 | 2 | SC-5 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.SecuritySmokeTest"` | Wave 0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/.../integration/AbstractScheduleIntegrationTest.java` — shared Testcontainers base class
- [ ] `src/test/java/.../integration/EntityMappingIntegrationTest.java` — Hibernate schema validation (LSSN-03, CRON-04)
- [ ] `src/test/java/.../integration/SecuritySmokeTest.java` — 403 without headers
- [ ] `src/test/resources/application-test.yml` — test overrides (gRPC port -1, disable RabbitMQ auto-config)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| gRPC port 19092 no conflict | SC-2 | Port conflict only visible with all services running | Start schedule-service alongside auth and academic; verify no port bind errors |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
