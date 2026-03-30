# Phase 8: Redis Caching - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Add Spring Cache (`@Cacheable`) on 5 read-heavy gRPC methods with per-cache configurable TTLs stored in `application.yml`. Mutations in REST service layer trigger `@CacheEvict` with cascading eviction for multi-entity operations (e.g., student transfer invalidates two group caches). Verified by integration tests using Testcontainers Redis.

</domain>

<decisions>
## Implementation Decisions

### Caching Scope
- **D-01:** Cache 5 of 7 gRPC methods: `GetGroup`, `GetGroupMembers`, `GetActiveSemester`, `GetCampusGeofence`, `GetUserById`
- **D-02:** Skip `GetTeacherSubjects` (complex joins, hard to evict cleanly) and `IsHeadman` (cheap single-row lookup)
- **D-03:** Caching is gRPC-only — REST endpoints are not cached in this phase

### TTL Strategy
- **D-04:** Per-cache TTLs configured in `application.yml` (not hardcoded)
- **D-05:** Volatile caches (GetGroupMembers, GetGroup, GetUserById) default to 5 minutes
- **D-06:** Near-static caches (GetCampusGeofence) can use longer TTL (e.g., 1 hour)
- **D-07:** GetActiveSemester uses moderate TTL (e.g., 10 minutes) — changes infrequently but must reflect activation promptly

### Eviction Cascades
- **D-08:** `@CacheEvict` annotations live on REST service layer methods (UserService, GroupService, SemesterService) — co-located with the mutation
- **D-09:** Student transfer uses `@Caching` to explicitly evict both old and new group's `group_members` cache entries by group ID
- **D-10:** Headman change evicts both `groups` (group info includes headman) and `group_members` caches for the affected group
- **D-11:** Semester activation evicts `active_semester` cache
- **D-12:** User update/archive evicts `users` cache for that user ID

### Testing Approach
- **D-13:** Testcontainers Redis for integration tests — real Redis, not mocked
- **D-14:** datasource-proxy (or CountingDataSource wrapper) to verify "exactly one DB query" — assert query count between first and second gRPC call
- **D-15:** RedisTemplate commands to verify TTL values directly in tests

### Claude's Discretion
- Cache key naming convention (e.g., `groups::42`, `group_members::42`)
- `RedisCacheConfiguration` bean structure and serializer choice
- Whether to use `GenericJackson2JsonRedisSerializer` or `StringRedisSerializer` + manual conversion
- Exact `@Caching` annotation structure for multi-evict methods

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### gRPC Service (caching target)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` — 7 RPCs, caching annotations go on 5 of these
- `proto/academic.proto` — gRPC contract, defines method signatures and message types

### REST Services (eviction target)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java` — transferStudent, update, archive methods need @CacheEvict
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java` — update, headman change methods need @CacheEvict
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java` — activate method needs @CacheEvict

### Build & Config
- `services/academic-service/academic-app/build.gradle.kts` — spring-boot-starter-data-redis already present (line 24)
- `services/academic-service/academic-app/src/main/resources/application.yml` — add cache TTL config here

### Entities & Repositories
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/` — JPA entities (return types for cached methods)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/` — repositories queried by gRPC service

### Test Infrastructure
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/` — existing Testcontainers PostgreSQL base class to extend with Redis container

### Prior Research
- `.planning/research/STACK.md` — Redis caching research with RedisCacheConfiguration patterns
- `.planning/codebase/CONCERNS.md` — notes that Redis dependency exists but is unused

### Success Criteria
- `.planning/ROADMAP.md` — Phase 8 success criteria (4 items)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AcademicGrpcServiceImpl` — single class with all 7 RPCs, clean method signatures suitable for `@Cacheable`
- `spring-boot-starter-data-redis` — already in build.gradle.kts, no new dependency needed
- `AbstractAcademicIntegrationTest` — Testcontainers base class, extend with Redis container

### Established Patterns
- gRPC service queries repositories directly (no REST service delegation) — caching annotations go on gRPC service methods
- `@SQLRestriction("status <> 'archived'")` on User entity — cached results already filter archived users (except `findByIdIncludingArchived`)
- Long FK fields, no JPA associations — cache keys are simple Long IDs

### Integration Points
- `RedisCacheConfiguration` bean — needs to be created (no cache config exists yet)
- `@EnableCaching` — needs to be added to application main class or config
- `application.yml` — TTL properties need to be added
- REST service methods — need `@CacheEvict` / `@Caching` annotations added

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-redis-caching*
*Context gathered: 2026-03-31*
