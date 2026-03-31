---
phase: 08-redis-caching
plan: 01
subsystem: academic-service
tags: [redis, caching, grpc, cache-eviction, spring-cache]
dependency_graph:
  requires: [07-grpc-server]
  provides: [redis-caching-infrastructure]
  affects: [academic-app]
tech_stack:
  added: []
  patterns: [Spring Cache @Cacheable/@CacheEvict, RedisCacheManager, GenericJackson2JsonRedisSerializer, programmatic cache eviction via CacheManager]
key_files:
  created:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/CacheConfig.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java
    - services/academic-service/academic-app/src/main/resources/application.yml
decisions:
  - "AcademicReadService is a separate Spring bean (not inner methods of gRPC service) to avoid AOP self-invocation where @Cacheable proxy is bypassed (per D-01)"
  - "Programmatic CacheManager eviction used for old group on student transfer and for groups+group_members on headman change (dynamic keys not available in SpEL at annotation level)"
  - "Cache TTLs use ISO-8601 duration format in application.yml (PT5M, PT10M, PT1H) for readability"
  - "allEntries=true used on active_semester @CacheEvict for safety even though only one entry exists keyed 'current'"
metrics:
  duration: 4 minutes
  completed_date: "2026-03-31"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 5
---

# Phase 8 Plan 01: Redis Caching Infrastructure Summary

**One-liner:** Redis cache layer for Academic Service gRPC reads — CacheConfig with 5 named caches (TTL from application.yml), AcademicReadService as separate @Cacheable bean, @CacheEvict on all REST mutation methods with programmatic eviction for runtime-only keys.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create CacheConfig, AcademicReadService, refactor gRPC service | 1a6c369 | CacheConfig.java (created), AcademicReadService.java (created), AcademicGrpcServiceImpl.java (refactored), application.yml (cache TTLs added) |
| 2 | Add @CacheEvict to REST service mutation methods | 56706ff | UserService.java, GroupService.java, SemesterService.java |

## What Was Built

### CacheConfig.java
`@EnableCaching` + `RedisCacheManager` with 5 named cache configurations. Uses `GenericJackson2JsonRedisSerializer` with Jackson `ObjectMapper` configured for:
- Java time module (`JavaTimeModule`)
- `activateDefaultTyping` with `NON_FINAL` + `PROPERTY` — enables polymorphic deserialization so entity subclasses survive Redis round-trips
- TTLs injected via `@Value("${cache.ttl.*}")` with ISO-8601 defaults

### AcademicReadService.java
Separate `@Service` bean with 5 `@Cacheable` methods:
- `fetchGroup(Long groupId)` — cache `groups`, key `#groupId`
- `fetchGroupMembers(Long groupId)` — cache `group_members`, key `#groupId`
- `fetchActiveSemester()` — cache `active_semester`, key `'current'`
- `fetchCampusGeofence()` — cache `campus_geofence`, key `'global'`
- `fetchUserById(Long userId)` — cache `users`, key `#userId`

String literal keys for zero-arg methods produce readable Redis keys (`active_semester::current`, `campus_geofence::global`) instead of `SimpleKey.EMPTY`.

### AcademicGrpcServiceImpl.java (refactored)
- Removed: `SemesterRepository`, `CampusSettingRepository` fields (no longer needed directly)
- Added: `AcademicReadService` as constructor-injected dependency
- 5 cached methods now delegate via `academicReadService.fetch*(...)`
- `getTeacherSubjects` and `isHeadman` unchanged (not cached per D-02)

### Cache eviction on mutation methods

| Method | Eviction strategy |
|--------|-------------------|
| `UserService.updateUser` | `@CacheEvict(users, key=#id)` |
| `UserService.patchUser` | `@CacheEvict(users, key=#id)` + programmatic `groups` + `group_members` eviction when `isHeadman` changes (per D-10) |
| `UserService.archiveUser` | `@CacheEvict(users, key=#id)` |
| `UserService.transferStudent` | `@CacheEvict(group_members, key=#request.newGroupId())` + programmatic eviction of old group and user |
| `GroupService.updateGroup` | `@Caching` evict `groups::#id` + `group_members::#id` |
| `GroupService.deleteGroup` | `@Caching` evict `groups::#id` + `group_members::#id` |
| `SemesterService.activateSemester` | `@CacheEvict(active_semester, allEntries=true)` |

### application.yml additions
```yaml
cache:
  ttl:
    groups: PT5M
    group-members: PT5M
    users: PT5M
    active-semester: PT10M
    campus-geofence: PT1H
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All cache paths wire to real repositories and entities.

## Self-Check: PASSED

Files exist:
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/CacheConfig.java
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java

Commits exist:
- 1a6c369 feat(08-01): add CacheConfig, AcademicReadService, and refactor gRPC service
- 56706ff feat(08-01): add @CacheEvict to REST service mutation methods

Compilation: BUILD SUCCESSFUL (compileJava exits 0)
