---
phase: 7
slug: grpc-server
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-30
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | No separate config — uses `@SpringBootTest` annotations |
| **Quick run command** | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest*"` |
| **Full suite command** | `.\gradlew :services:academic-service:academic-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest*"`
- **After every plan wave:** Run `.\gradlew :services:academic-service:academic-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | GRPC-01 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getGroup*"` | ❌ W0 | ⬜ pending |
| 07-01-02 | 01 | 1 | GRPC-02 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getGroupMembers*"` | ❌ W0 | ⬜ pending |
| 07-01-03 | 01 | 1 | GRPC-03 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getTeacherSubjects*"` | ❌ W0 | ⬜ pending |
| 07-01-04 | 01 | 1 | GRPC-04 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.isHeadman*"` | ❌ W0 | ⬜ pending |
| 07-01-05 | 01 | 1 | GRPC-05 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getActiveSemester*"` | ❌ W0 | ⬜ pending |
| 07-01-06 | 01 | 1 | GRPC-06 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getCampusGeofence*"` | ❌ W0 | ⬜ pending |
| 07-01-07 | 01 | 1 | GRPC-07 | integration | `.\gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getUserById*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java` — stubs for all 7 GRPC-* requirements
- [ ] `services/academic-service/academic-app/src/test/resources/application-test.yml` — in-process gRPC server config (if absent)

*Existing `AbstractAcademicIntegrationTest` base class provides Testcontainers PostgreSQL infrastructure.*

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
