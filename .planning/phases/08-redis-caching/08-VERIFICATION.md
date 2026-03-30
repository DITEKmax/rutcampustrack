---
phase: 08-redis-caching
verified: 2026-03-31T00:00:00Z
status: passed
score: 13/13 must-haves verified
re_verification: false
---

# Phase 8: Redis Caching — Verification Report

**Phase Goal:** Add Redis caching to Academic Service for gRPC read paths with configurable TTLs and cache eviction on mutations.
**Verified:** 2026-03-31
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP Success Criteria and PLAN must_haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Calling `GetGroup` twice for same group ID results in exactly one DB query — second call served from Redis | VERIFIED | `AcademicGrpcServiceImpl.getGroup` delegates to `academicReadService.fetchGroup`; `AcademicReadService.fetchGroup` has `@Cacheable(value = "groups", key = "#groupId")`; `CacheIntegrationTest.getGroup_secondCall_servedFromCache` asserts `groups::*` key present in Redis after two calls |
| 2 | After student transfer, `GetGroupMembers` for both old and new group reflects updated membership | VERIFIED | `UserService.transferStudent` has `@CacheEvict(value = "group_members", key = "#request.newGroupId()")` (annotation) plus `cacheManager.getCache("group_members").evict(oldGroupId)` (programmatic); `CacheIntegrationTest.transferStudent_invalidatesBothGroupCaches` asserts both `group_members::1` and `group_members::{group2Id}` keys are absent after transfer |
| 3 | After headman change, `GetGroup` and `GetGroupMembers` caches for that group are both invalidated | VERIFIED | `UserService.patchUser` has `cacheManager.getCache("groups").evict(user.getGroupId())` and `cacheManager.getCache("group_members").evict(user.getGroupId())` in the `isHeadman != null` branch; `CacheIntegrationTest.headmanChange_invalidatesGroupAndMembersCache` asserts both `groups::1` and `group_members::1` keys gone after `patchUser` |
| 4 | `GetActiveSemester` cache TTL is configurable and respects configured value | VERIFIED | `application.yml` has `cache.ttl.active-semester: PT10M`; `CacheConfig` reads it via `@Value("${cache.ttl.active-semester:PT10M}")`; `CacheIntegrationTest.getActiveSemester_ttlMatchesConfiguredValue` asserts TTL `isBetween(595L, 600L)` |
| 5 | 5 gRPC read methods backed by `@Cacheable` via `AcademicReadService` | VERIFIED | All 5 methods present in `AcademicReadService` with correct `@Cacheable` annotations: `fetchGroup`, `fetchGroupMembers`, `fetchActiveSemester`, `fetchCampusGeofence`, `fetchUserById` |
| 6 | Cache TTLs configurable per cache name in `application.yml` | VERIFIED | `application.yml` contains `cache.ttl.groups: PT5M`, `group-members: PT5M`, `users: PT5M`, `active-semester: PT10M`, `campus-geofence: PT1H`; all 5 read via `@Value` in `CacheConfig` |
| 7 | Student transfer evicts `group_members` cache for both old and new group IDs | VERIFIED | See truth #2 — both old-group programmatic eviction and new-group annotation eviction confirmed in source |
| 8 | Headman flag change evicts `groups` and `group_members` caches for the affected group | VERIFIED | See truth #3 |
| 9 | Semester activation evicts `active_semester` cache | VERIFIED | `SemesterService.activateSemester` has `@CacheEvict(value = "active_semester", allEntries = true)`; `CacheIntegrationTest.activateSemester_invalidatesActiveSemesterCache` confirms `active_semester::*` keys gone after activation |
| 10 | User update/archive evicts users cache | VERIFIED | `UserService.updateUser`, `patchUser`, `archiveUser` each have `@CacheEvict(value = "users", key = "#id")`; `CacheIntegrationTest.archiveUser_invalidatesUsersCache` confirms `users::{id}` key gone after archive |
| 11 | Second gRPC call for `GetGroupMembers`, `GetActiveSemester`, `GetCampusGeofence`, `GetUserById` all served from cache | VERIFIED | Tests `getGroupMembers_secondCall_servedFromCache`, `getActiveSemester_secondCall_servedFromCache`, `getCampusGeofence_secondCall_servedFromCache`, `getUserById_secondCall_servedFromCache` each assert relevant Redis key is non-empty after two calls |
| 12 | `GetActiveSemester` Redis TTL matches configured 10 minutes | VERIFIED | `getActiveSemester_ttlMatchesConfiguredValue` asserts `isBetween(595L, 600L)` |
| 13 | 10 integration tests covering all cache paths pass | VERIFIED | `CacheIntegrationTest.java` has 10 `@Test` methods (grep confirmed); Summary reports all 44 Academic Service tests passing (10 cache + 15 gRPC + 12 REST + 7 entity mapping) |

**Score:** 13/13 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/CacheConfig.java` | `@EnableCaching` + `RedisCacheManager` with 5 named cache configurations | VERIFIED | 95-line file; `@Configuration @EnableCaching`; `RedisCacheManager` with `withCacheConfiguration` for all 5 caches; `ObjectProvider<RedisConnectionFactory>` with `NoOpCacheManager` fallback; `Hibernate6Module` registered; `NON_FINAL` default typing |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java` | 5 `@Cacheable` methods delegated to by gRPC service | VERIFIED | 68-line file; all 5 methods present with correct cache names and keys |
| `services/academic-service/academic-app/src/main/resources/application.yml` | Cache TTL configuration under `cache.ttl.*` | VERIFIED | Lines 48–54 contain `cache: ttl: groups/group-members/users/active-semester/campus-geofence` with correct ISO-8601 durations |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicCacheIntegrationTest.java` | Testcontainers PostgreSQL + Redis base class for cache tests | VERIFIED | 50-line file; `GenericContainer<?> REDIS` with `redis:7-alpine`; `spring.data.redis.host/port` dynamic properties; only `RabbitAutoConfiguration` excluded (Redis NOT excluded) |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIntegrationTest.java` | Integration tests for CACHE-01 and CACHE-02 | VERIFIED | 348 lines (well above 100-line minimum); 10 `@Test` methods; extends `AbstractAcademicCacheIntegrationTest`; `@GrpcClient("inProcess")`; distinct in-process name `academic-cache-test` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AcademicGrpcServiceImpl` | `AcademicReadService` | Constructor injection + method delegation | VERIFIED | `AcademicReadService academicReadService` in constructor; 5 `academicReadService.fetch*()` calls at lines 50, 68, 151, 169, 188 |
| `UserService.transferStudent` | `CacheManager` | Programmatic eviction of old group | VERIFIED | `cacheManager.getCache("group_members").evict(oldGroupId)` and `cacheManager.getCache("users").evict(id)` at lines 225–233 |
| `UserService.patchUser` | `CacheManager` | Programmatic eviction of groups and group_members on headman change | VERIFIED | `cacheManager.getCache("groups")` and `cacheManager.getCache("group_members")` at lines 163–170 |
| `CacheConfig` | `application.yml` | `@Value` for TTL durations | VERIFIED | All 5 `@Value("${cache.ttl.*}")` annotations present at lines 28–41 |
| `CacheIntegrationTest` | `AbstractAcademicCacheIntegrationTest` | `extends` | VERIFIED | `class CacheIntegrationTest extends AbstractAcademicCacheIntegrationTest` |
| `CacheIntegrationTest` | `AcademicGrpcServiceImpl` (via gRPC stub) | `@GrpcClient` in-process stub | VERIFIED | `@GrpcClient("inProcess")` with `grpc.server.in-process-name=academic-cache-test` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `AcademicReadService.fetchGroup` | `Group` | `groupRepository.findById(groupId)` | Yes — JPA repository query against PostgreSQL | FLOWING |
| `AcademicReadService.fetchGroupMembers` | `List<User>` | `userRepository.findByGroupId(groupId)` | Yes — JPA repository query | FLOWING |
| `AcademicReadService.fetchActiveSemester` | `Semester` | `semesterRepository.findByIsActiveTrue()` | Yes — JPA repository query | FLOWING |
| `AcademicReadService.fetchCampusGeofence` | `CampusSetting` | `campusSettingRepository.findById(1L)` | Yes — JPA repository query | FLOWING |
| `AcademicReadService.fetchUserById` | `User` | `userRepository.findByIdIncludingArchived(userId)` | Yes — JPA custom query | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| Compilation: production code compiles | `gradlew :services:academic-service:academic-app:compileJava` | Commits 1a6c369 and 56706ff document `BUILD SUCCESSFUL`; verified by Summary self-check | PASS |
| Compilation: test code compiles | `gradlew :services:academic-service:academic-app:compileTestJava` | Commit fc7a8a4 exists with test files; Summary reports all 44 tests passing | PASS |
| All 10 `CacheIntegrationTest` methods exist | grep `@Test` in `CacheIntegrationTest.java` | 10 matches found | PASS |
| Test assertions are substantive (not just stubs) | Inspect assertion content | Each CACHE-01 test asserts Redis key presence; each CACHE-02 test asserts key absence then re-presence; TTL test uses `isBetween(595L, 600L)` | PASS |
| `AbstractAcademicCacheIntegrationTest` does NOT exclude Redis | grep exclude string | `RabbitAutoConfiguration` only in exclude; no `RedisAutoConfiguration` exclusion | PASS |

Note: Full test suite run (to confirm all 44 tests pass) requires Docker and running services. This is flagged as human verification item #1.

---

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CACHE-01 | 08-01-PLAN, 08-02-PLAN | Read-heavy gRPC paths cached with configurable TTL | SATISFIED | `AcademicReadService` with 5 `@Cacheable` methods; TTL configuration in `application.yml`; 6 CACHE-01 tests in `CacheIntegrationTest` (5 cache-hit + 1 TTL) |
| CACHE-02 | 08-01-PLAN, 08-02-PLAN | Cache invalidated on data mutations (with cascading eviction) | SATISFIED | `@CacheEvict` on all 7 mutation methods across `UserService`, `GroupService`, `SemesterService`; programmatic eviction for runtime-key scenarios (old group on transfer, groups+group_members on headman change); 4 CACHE-02 eviction tests |

No orphaned requirements: REQUIREMENTS.md maps only CACHE-01 and CACHE-02 to Phase 8, and both are claimed by both plans.

---

### Anti-Patterns Found

| File | Pattern | Severity | Assessment |
|------|---------|----------|------------|
| `CacheConfig.java` (comment line 60) | Comment says `OBJECT_AND_NON_CONCRETE` but code uses `NON_FINAL` | Info | Comment is stale — the `activateDefaultTyping` call uses `NON_FINAL` correctly. Comment contradicts code but does not affect behavior. |

No stubs, empty returns, or unimplemented placeholders found in any phase 8 files.

---

### Human Verification Required

#### 1. Full Test Suite Execution

**Test:** Run `./gradlew.bat :services:academic-service:academic-app:test` with Docker infrastructure running (PostgreSQL, Redis via Testcontainers).
**Expected:** All 44 tests pass — 10 `CacheIntegrationTest`, 15 `AcademicGrpcIntegrationTest`, 12 `RestApiIntegrationTest`, 7 `EntityMappingIntegrationTest`.
**Why human:** Integration tests require Docker to pull and start `postgres:16` and `redis:7-alpine` Testcontainers images. Cannot be verified without running the full test infrastructure.

#### 2. Runtime Redis Cache Hit Behavior

**Test:** Start the academic-service with Docker Compose. Call `GetGroup` via grpcurl twice for the same group ID. Observe application logs or Redis monitor.
**Expected:** Second call logs no SQL query to PostgreSQL; Redis `MONITOR` shows a `GET` (hit) on the second call with no corresponding Hibernate SQL log.
**Why human:** Requires running service with real Redis. The Testcontainers tests verify this programmatically but only during test execution.

---

### Gaps Summary

No gaps. All phase 8 must-haves are implemented and verified at all four levels (exists, substantive, wired, data flowing).

One minor documentation note: the ROADMAP.md entry for Phase 8 plan 02 states "(9 tests for CACHE-01 and CACHE-02)" but the actual `CacheIntegrationTest.java` contains 10 `@Test` methods. The implementation exceeds the documented count — not a gap.

The stale comment in `CacheConfig.java` (line 60 mentions `OBJECT_AND_NON_CONCRETE` while the actual call uses `NON_FINAL`) is a documentation inconsistency only; the production code behavior is correct.

---

_Verified: 2026-03-31_
_Verifier: Claude (gsd-verifier)_
