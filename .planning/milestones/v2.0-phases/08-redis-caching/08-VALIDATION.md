---
phase: 8
slug: redis-caching
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-31
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) via `spring-boot-starter-test` |
| **Config file** | none — auto-configured by Spring Boot Test |
| **Quick run command** | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest" -x processResources` |
| **Full suite command** | `./gradlew :services:academic-service:academic-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest" -x processResources`
- **After every plan wave:** Run `./gradlew :services:academic-service:academic-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 1 | CACHE-01 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getGroup_secondCall_servedFromCache_exactlyOneDbQuery"` | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 1 | CACHE-01 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getGroupMembers_secondCall_servedFromCache"` | ❌ W0 | ⬜ pending |
| 08-01-03 | 01 | 1 | CACHE-01 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getActiveSemester_ttlIsConfiguredValue"` | ❌ W0 | ⬜ pending |
| 08-01-04 | 01 | 1 | CACHE-01 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getCampusGeofence_secondCall_servedFromCache"` | ❌ W0 | ⬜ pending |
| 08-01-05 | 01 | 1 | CACHE-01 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getUserById_secondCall_servedFromCache"` | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 2 | CACHE-02 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.transferStudent_invalidatesBothGroupCaches"` | ❌ W0 | ⬜ pending |
| 08-02-02 | 02 | 2 | CACHE-02 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.headmanChange_invalidatesGroupAndMembersCache"` | ❌ W0 | ⬜ pending |
| 08-02-03 | 02 | 2 | CACHE-02 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.activateSemester_invalidatesActiveSemesterCache"` | ❌ W0 | ⬜ pending |
| 08-02-04 | 02 | 2 | CACHE-02 | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.archiveUser_invalidatesUsersCache"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `AbstractAcademicCacheIntegrationTest.java` — base class with Postgres + Redis Testcontainers
- [ ] `CacheIntegrationTest.java` — all CACHE-01 and CACHE-02 test method stubs
- [ ] `CacheConfig.java` — `@EnableCaching` + `RedisCacheManager` bean
- [ ] `AcademicReadService.java` — `@Cacheable` methods extracted from gRPC service

*Existing infrastructure covers PostgreSQL Testcontainers; Redis container must be added.*

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
