---
phase: 6
slug: rest-api-hateoas
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-30
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) |
| **Config file** | none — configuration via `AbstractAcademicIntegrationTest` base class |
| **Quick run command** | `.\gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest"` |
| **Full suite command** | `.\gradlew :services:academic-service:academic-app:test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew :services:academic-service:academic-app:test --tests "*.<ControllerBeingImplemented>Test"`
- **After every plan wave:** Run `.\gradlew :services:academic-service:academic-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | USER-01 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.createUser_*"` | ❌ W0 | ⬜ pending |
| 06-01-02 | 01 | 1 | USER-02 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest"` | ❌ W0 | ⬜ pending |
| 06-01-03 | 01 | 1 | USER-03, USER-04 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.headman_*"` | ❌ W0 | ⬜ pending |
| 06-01-04 | 01 | 1 | USER-05 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.transfer_*"` | ❌ W0 | ⬜ pending |
| 06-01-05 | 01 | 1 | USER-06, USER-07 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.me_*"` | ❌ W0 | ⬜ pending |
| 06-02-01 | 02 | 1 | GSEM-01, GSEM-02 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.GroupControllerTest"` | ❌ W0 | ⬜ pending |
| 06-02-02 | 02 | 1 | GSEM-03, GSEM-04 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.SemesterControllerTest"` | ❌ W0 | ⬜ pending |
| 06-03-01 | 03 | 2 | SUBJ-01, SUBJ-02, SUBJ-03 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.SubjectControllerTest"` | ❌ W0 | ⬜ pending |
| 06-03-02 | 03 | 2 | USER-08 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.AssignmentControllerTest"` | ❌ W0 | ⬜ pending |
| 06-04-01 | 04 | 2 | ASST-01, ASST-02, ASST-03 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.AssistantControllerTest"` | ❌ W0 | ⬜ pending |
| 06-05-01 | 05 | 3 | HW-01, HW-02, HW-03 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.HomeworkControllerTest"` | ❌ W0 | ⬜ pending |
| 06-06-01 | 06 | 3 | THRSH-01, THRSH-02, THRSH-03, THRSH-04 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.ThresholdControllerTest"` | ❌ W0 | ⬜ pending |
| 06-07-01 | 07 | 4 | DASH-01 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*.DashboardControllerTest"` | ❌ W0 | ⬜ pending |
| 06-XX-XX | all | all | Role enforcement | integration | Included in each controller test class (negative path) | ❌ W0 | ⬜ pending |
| 06-XX-XX | all | all | HATEOAS links | integration | Included in controller tests (assert JSON path `_links`) | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `UserControllerTest.java` — stubs for USER-01 through USER-08 + 403 enforcement
- [ ] `GroupControllerTest.java` — stubs for GSEM-01
- [ ] `SemesterControllerTest.java` — stubs for GSEM-02, GSEM-03, GSEM-04
- [ ] `SubjectControllerTest.java` — stubs for SUBJ-01, SUBJ-02, SUBJ-03
- [ ] `AssignmentControllerTest.java` — stubs for USER-08
- [ ] `AssistantControllerTest.java` — stubs for ASST-01, ASST-02, ASST-03
- [ ] `HomeworkControllerTest.java` — stubs for HW-01, HW-02, HW-03
- [ ] `ThresholdControllerTest.java` — stubs for THRSH-01 through THRSH-04
- [ ] `DashboardControllerTest.java` — stubs for DASH-01

*Framework install: not needed — JUnit 5 + Spring Boot Test + Testcontainers already in `build.gradle.kts`*
*All test classes extend `AbstractAcademicIntegrationTest` and use `MockMvc` or `TestRestTemplate` with injected gateway headers (`X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`).*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
