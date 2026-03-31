# Phase 8: Redis Caching - Research

**Researched:** 2026-03-31
**Domain:** Spring Cache abstraction + Spring Data Redis + Testcontainers Redis
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Cache 5 of 7 gRPC methods: `GetGroup`, `GetGroupMembers`, `GetActiveSemester`, `GetCampusGeofence`, `GetUserById`
- **D-02:** Skip `GetTeacherSubjects` (complex joins, hard to evict cleanly) and `IsHeadman` (cheap single-row lookup)
- **D-03:** Caching is gRPC-only — REST endpoints are not cached in this phase
- **D-04:** Per-cache TTLs configured in `application.yml` (not hardcoded)
- **D-05:** Volatile caches (GetGroupMembers, GetGroup, GetUserById) default to 5 minutes
- **D-06:** Near-static caches (GetCampusGeofence) use longer TTL (e.g., 1 hour)
- **D-07:** GetActiveSemester uses moderate TTL (e.g., 10 minutes)
- **D-08:** `@CacheEvict` annotations live on REST service layer methods (UserService, GroupService, SemesterService)
- **D-09:** Student transfer uses `@Caching` to explicitly evict both old and new group's `group_members` entries
- **D-10:** Headman change evicts both `groups` and `group_members` caches for the affected group
- **D-11:** Semester activation evicts `active_semester` cache
- **D-12:** User update/archive evicts `users` cache for that user ID
- **D-13:** Testcontainers Redis for integration tests — real Redis, not mocked
- **D-14:** datasource-proxy (or CountingDataSource wrapper) to verify "exactly one DB query" — assert query count between first and second gRPC call
- **D-15:** RedisTemplate commands to verify TTL values directly in tests

### Claude's Discretion

- Cache key naming convention (e.g., `groups::42`, `group_members::42`)
- `RedisCacheConfiguration` bean structure and serializer choice
- Whether to use `GenericJackson2JsonRedisSerializer` or `StringRedisSerializer` + manual conversion
- Exact `@Caching` annotation structure for multi-evict methods

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CACHE-01 | Read-heavy gRPC paths cached with configurable TTL | Spring Cache abstraction with `RedisCacheManager`, per-cache `RedisCacheConfiguration` with `entryTtl`, TTL bound to `application.yml` via `@ConfigurationProperties` or `@Value` |
| CACHE-02 | Cache invalidated on data mutations (with cascading eviction) | `@CacheEvict` on service mutations; `@Caching` for multi-cache evictions; cascading student transfer uses two explicit `@CacheEvict` entries keyed on old/new group IDs |
</phase_requirements>

---

## Summary

Phase 8 adds Spring Cache support to the 5 read-heavy gRPC methods in `AcademicGrpcServiceImpl`. The Redis dependency (`spring-boot-starter-data-redis`) is already present in `build.gradle.kts`. No new production dependencies are needed. The work consists of three distinct activities:

1. **Annotation layer** — add `@Cacheable` to 5 gRPC methods and `@CacheEvict`/`@Caching` to service-layer mutations.
2. **Configuration** — create `CacheConfig` bean with `RedisCacheManager`, per-cache TTLs read from `application.yml`, JSON serialization.
3. **Test layer** — extend `AbstractAcademicIntegrationTest` with a Testcontainers Redis container, add a query-counting `DataSource` wrapper, write integration tests verifying cache hit rate and TTL values.

The biggest implementation risk is **AOP self-invocation**: `@Cacheable` on `AcademicGrpcServiceImpl` methods will work correctly because these methods are not called internally from within the same class — they are called by the gRPC framework through the externally visible `StreamObserver` interface, which always goes through the Spring proxy. Similarly `@CacheEvict` on service layer methods is called from controllers, so proxy interception is guaranteed. No self-invocation workarounds are needed.

The second risk is **entity serialization**: JPA entities (`User`, `Group`, `Semester`, `CampusSetting`) do not implement `java.io.Serializable` and contain `OffsetDateTime` / `LocalDate` fields. `GenericJackson2JsonRedisSerializer` handles this correctly via Jackson (no `Serializable` required), but Jackson must be able to see getters. Lombok `@Getter` on entities satisfies this.

**Primary recommendation:** Use `GenericJackson2JsonRedisSerializer`, bind TTLs from `application.yml` with `@Value`, and run a single `AbstractAcademicCacheIntegrationTest` base class that adds the Testcontainers Redis container alongside the existing Postgres container.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-data-redis` | managed by Spring Boot 3.4 BOM | Redis connection factory, `RedisCacheManager`, `RedisTemplate` | Already in `build.gradle.kts` line 24 — no change needed |
| `org.testcontainers:testcontainers-bom` | `1.20.4` (already in BOM) | Testcontainers version management | Already declared in `dependencyManagement` |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.testcontainers:testcontainers` | managed by BOM 1.20.4 | `GenericContainer` for Redis | Test scope only — Testcontainers BOM already present |
| `net.ttddyy.observation:datasource-proxy` | `1.10` (latest as of 2025) | Wrap DataSource with query counter for cache-hit assertions | Test scope only — needed for "exactly one DB query" assertion (D-14) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `GenericJackson2JsonRedisSerializer` | `StringRedisSerializer` + manual Jackson | String serializer needs explicit serialize/deserialize calls in service code — more boilerplate, no benefit |
| `GenericJackson2JsonRedisSerializer` | `JdkSerializationRedisSerializer` (default) | JDK serialization breaks across JAR deploys if class structure changes; not recommended |
| `datasource-proxy` for query counting | `@SpyBean` on repository | SpyBean approach works for unit-style but requires mocking, not full integration |
| `datasource-proxy` for query counting | Counting via Hibernate statistics | Hibernate stats need explicit `statistics.enabled=true` and are reset per `SessionFactory` scope — slightly more complex to use in tests |

**Installation (test scope only):**
```kotlin
// build.gradle.kts — add to dependencies block
testImplementation("net.ttddyy.observation:datasource-proxy:1.10")
// No new production dependencies needed
```

**Version verification:** `spring-boot-starter-data-redis` is managed by Spring Boot 3.4 BOM. `testcontainers-bom:1.20.4` is already declared. `datasource-proxy:1.10` is the current stable release from the `jdbc-observations` project (renamed from `ttddyy/datasource-proxy`).

---

## Architecture Patterns

### Recommended Project Structure

```
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/
├── config/
│   ├── CacheConfig.java           ← NEW: @EnableCaching + RedisCacheManager bean
│   ├── EnumConverters.java        ← existing
│   └── LowercaseEnumConverter.java ← existing
├── grpc/
│   └── AcademicGrpcServiceImpl.java  ← ADD: @Cacheable on 5 methods
├── user/
│   └── UserService.java           ← ADD: @CacheEvict / @Caching on mutations
├── group/
│   └── GroupService.java          ← ADD: @CacheEvict on updateGroup, deleteGroup
└── semester/
    └── SemesterService.java       ← ADD: @CacheEvict on activateSemester

src/main/resources/
└── application.yml                ← ADD: cache.ttl.* properties

src/test/java/ru/rutcampustrack/academic/integration/
├── AbstractAcademicIntegrationTest.java    ← MODIFY: add Redis container, remove Redis exclude
├── AbstractAcademicCacheIntegrationTest.java  ← NEW: base for cache tests with query counter
└── CacheIntegrationTest.java               ← NEW: CACHE-01, CACHE-02 test methods
```

### Pattern 1: RedisCacheManager with Per-Cache TTL from application.yml

**What:** A `@Configuration @EnableCaching` class creates a `RedisCacheManager` bean. TTLs are injected with `@Value` from `application.yml` so they remain configurable without recompilation.

**When to use:** Always — this is the only `CacheManager` in the application.

```java
// Source: Spring Data Redis docs https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.ttl.groups:5m}")
    private Duration groupsTtl;

    @Value("${cache.ttl.group-members:5m}")
    private Duration groupMembersTtl;

    @Value("${cache.ttl.users:5m}")
    private Duration usersTtl;

    @Value("${cache.ttl.active-semester:10m}")
    private Duration activeSemesterTtl;

    @Value("${cache.ttl.campus-geofence:1h}")
    private Duration campusGeofenceTtl;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                    LaissezFaireSubTypeValidator.instance,
                    ObjectMapper.DefaultTyping.NON_FINAL,
                    JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("groups",
                    base.entryTtl(groupsTtl))
                .withCacheConfiguration("group_members",
                    base.entryTtl(groupMembersTtl))
                .withCacheConfiguration("users",
                    base.entryTtl(usersTtl))
                .withCacheConfiguration("active_semester",
                    base.entryTtl(activeSemesterTtl))
                .withCacheConfiguration("campus_geofence",
                    base.entryTtl(campusGeofenceTtl))
                .build();
    }
}
```

**application.yml additions:**
```yaml
cache:
  ttl:
    groups: 5m
    group-members: 5m
    users: 5m
    active-semester: 10m
    campus-geofence: 1h
```

**Important:** Spring Boot's `ConversionService` can convert ISO-8601 duration strings like `"5m"` to `java.time.Duration` when using `@Value`. Alternatively use `PT5M` notation or `@ConfigurationProperties` for type-safe binding.

### Pattern 2: @Cacheable on gRPC Service Methods

**What:** Annotate the 5 selected `AcademicGrpcServiceImpl` methods. Return type is not cached directly — the gRPC methods return void and write to `StreamObserver`. The cacheable data is the domain object fetched from the repository. Therefore caching must be placed on an intermediate method (or the repository call must be extracted into a separate `@Cacheable` method in a different Spring bean).

**Critical design decision:** `@Cacheable` placed directly on `getGroup(GroupRequest, StreamObserver)` will NOT work — the method return type is `void`. Spring Cache requires a non-void return type on the `@Cacheable` method.

**Solution:** Extract data-fetching logic into a private helper that returns a domain object, OR create a thin `AcademicCacheService` intermediary bean. The cleanest approach consistent with the existing codebase pattern (direct repository injection) is to create package-private fetch methods annotated `@Cacheable` that are delegated to by the gRPC handler:

```java
// AcademicGrpcServiceImpl.java
@Override
public void getGroup(GroupRequest request, StreamObserver<GroupResponse> responseObserver) {
    Group group = fetchGroup(request.getGroupId());  // cached call goes through proxy
    // ... build response
}

// In a SEPARATE Spring bean (e.g., AcademicReadService) to avoid self-invocation:
@Cacheable(value = "groups", key = "#groupId")
public Group fetchGroup(Long groupId) {
    return groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
}
```

**Self-invocation prevention:** `@Cacheable` on a method in the SAME class as the caller is silently bypassed by Spring AOP (proxy not invoked on self-calls). The fetch methods MUST live in a separate Spring-managed bean that is injected into `AcademicGrpcServiceImpl`. Recommended: create `AcademicReadService` in `ru.rutcampustrack.academic.grpc` package with all 5 cacheable read methods.

```java
@Service
public class AcademicReadService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final CampusSettingRepository campusSettingRepository;

    // constructor injection

    @Cacheable(value = "groups", key = "#groupId")
    public Group fetchGroup(Long groupId) { ... }

    @Cacheable(value = "group_members", key = "#groupId")
    public List<User> fetchGroupMembers(Long groupId) { ... }

    @Cacheable(value = "active_semester")
    public Semester fetchActiveSemester() { ... }

    @Cacheable(value = "campus_geofence")
    public CampusSetting fetchCampusGeofence() { ... }

    @Cacheable(value = "users", key = "#userId")
    public User fetchUserById(Long userId) { ... }
}
```

`AcademicGrpcServiceImpl` then injects `AcademicReadService` and calls these methods. The gRPC service itself keeps its `StreamObserver` structure unchanged.

### Pattern 3: @CacheEvict and @Caching for Cascading Evictions

**What:** Mutation methods on service layer beans get `@CacheEvict` annotations. Multi-cache evictions (e.g., student transfer) use `@Caching`.

```java
// UserService.java

// User update — evict users cache for specific user ID
@CacheEvict(value = "users", key = "#id")
@Transactional
public User updateUser(Long id, UpdateUserRequest request) { ... }

// User archive — evict users cache for specific user ID
@CacheEvict(value = "users", key = "#id")
@Transactional
public void archiveUser(Long id) { ... }

// Headman change (via patchUser when isHeadman changes)
// must evict both groups (headman info in group?) and group_members
// Note: group entity does NOT store headman_id — headman is a User flag
// So only group_members needs eviction when headman flag changes
@Caching(evict = {
    @CacheEvict(value = "group_members", key = "#result.groupId"),
    @CacheEvict(value = "users", key = "#id")
})
@Transactional
public User patchUser(Long id, PatchUserRequest request) { ... }

// Student transfer — evict group_members for BOTH old and new group
// Problem: old group ID is only known INSIDE the method, not as a parameter
// Solution: use SpEL with #result or use @CacheEvict(allEntries=true) on group_members
// Better: programmatic eviction via CacheManager for old group, @CacheEvict for new group
```

**Transfer eviction challenge:** The old group ID is NOT a parameter of `transferStudent` — it is fetched from the user entity inside the method. SpEL `@CacheEvict` cannot use `#result` for eviction keys. Three options:

1. **Accept old group as a method parameter** — change method signature to `transferStudent(Long id, TransferStudentRequest request, Long oldGroupId)`. Not clean, leaks implementation detail.
2. **Inject `CacheManager` into `UserService`** and manually call `cache.evict(oldGroupId)` after the transfer. Most flexible.
3. **Use `@CacheEvict(value = "group_members", allEntries = true)`** for both eviction points. Evicts ALL group_members cache entries. Overkill but guaranteed correct; acceptable for this use case since group_members cache has a 5-minute TTL and this is an infrequent admin operation.

**Recommended approach for transfer:** Use `CacheManager` injection for the old group eviction (precise) + `@CacheEvict` for the new group (via annotation). This is the pattern from D-09.

```java
@Service
public class UserService {

    private final CacheManager cacheManager;

    @Transactional
    public User transferStudent(Long id, TransferStudentRequest request) {
        User user = findUserById(id);
        Long oldGroupId = user.getGroupId();

        // ... transfer logic ...

        // Manual eviction for old group (ID only known at runtime)
        Cache groupMembersCache = cacheManager.getCache("group_members");
        if (groupMembersCache != null && oldGroupId != null) {
            groupMembersCache.evict(oldGroupId);
        }

        return userRepository.save(user);
    }
}
```

Add `@CacheEvict(value = "group_members", key = "#request.newGroupId")` on the method signature for the new group.

```java
@CacheEvict(value = "group_members", key = "#request.newGroupId")
@Transactional
public User transferStudent(Long id, TransferStudentRequest request) {
    // ... plus manual eviction of oldGroupId above ...
}
```

**Semester activation:**
```java
@CacheEvict(value = "active_semester", allEntries = true)
@Transactional
public Semester activateSemester(Long id) { ... }
```

**GroupService — update/delete:**
```java
@Caching(evict = {
    @CacheEvict(value = "groups", key = "#id"),
    @CacheEvict(value = "group_members", key = "#id")
})
@Transactional
public Group updateGroup(Long id, UpdateGroupRequest request) { ... }

@Caching(evict = {
    @CacheEvict(value = "groups", key = "#id"),
    @CacheEvict(value = "group_members", key = "#id")
})
@Transactional
public void deleteGroup(Long id) { ... }
```

### Pattern 4: Testcontainers Redis Integration Test Setup

**What:** Extend `AbstractAcademicIntegrationTest` to include a Redis container. The current `AbstractAcademicIntegrationTest` EXCLUDES Redis autoconfiguration — this must be reversed for cache tests.

```java
// AbstractAcademicCacheIntegrationTest.java — new class
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractAcademicCacheIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();

        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Do NOT exclude Redis autoconfiguration here — Redis is needed
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }
}
```

**Key difference from `AbstractAcademicIntegrationTest`:** Redis autoconfiguration is NOT excluded. RabbitMQ is still excluded.

**Existing tests still use `AbstractAcademicIntegrationTest`** (which excludes Redis) — they continue to work. Cache tests extend `AbstractAcademicCacheIntegrationTest`.

### Pattern 5: Query Count Verification with datasource-proxy

**What:** Wrap the DataSource with a counting proxy in the test application context to assert "exactly one DB query" between two gRPC calls.

```java
// In a @TestConfiguration class loaded by the cache integration test
@TestConfiguration
public class QueryCountingConfig {

    @Bean
    @Primary
    public DataSource proxyDataSource(DataSource actualDataSource) {
        return ProxyDataSourceBuilder.create(actualDataSource)
                .countQuery()
                .build();
    }
}
```

**Test assertion pattern:**
```java
@Autowired
private DataSource dataSource;

@Test
void getGroup_secondCall_servedFromCache() {
    // First call — hits DB
    GroupResponse first = stub.getGroup(GroupRequest.newBuilder().setGroupId(1L).build());

    // Record query count after first call
    QueryCountHolder.clear();  // reset counter

    // Second call — should come from Redis, NO DB query
    GroupResponse second = stub.getGroup(GroupRequest.newBuilder().setGroupId(1L).build());

    // Assert no DB queries
    assertThat(QueryCountHolder.getGrandTotal()).isZero();
    assertThat(first.getId()).isEqualTo(second.getId());
}
```

**Alternative simpler approach** (no datasource-proxy): Use `@SpyBean` on `GroupRepository` and verify `findById` is called exactly once:

```java
@SpyBean
private GroupRepository groupRepository;

@Test
void getGroup_secondCall_servedFromCache() {
    stub.getGroup(request);  // first call — DB hit
    stub.getGroup(request);  // second call — cache hit

    verify(groupRepository, times(1)).findById(1L);
}
```

This approach requires `AcademicReadService` to be a separate Spring bean (which it is by the design in Pattern 2) and works reliably with `@SpyBean`. It is simpler than datasource-proxy and may be preferred as the primary approach.

### Pattern 6: TTL Verification with RedisTemplate

**What:** Directly query Redis TTL to verify the configured TTL value is applied (D-15).

```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

@Test
void getActiveSemester_ttlIsConfigured() {
    // Prime the cache
    stub.getActiveSemester(Empty.getDefaultInstance());

    // Check TTL directly in Redis
    // Default key pattern: cacheName::cacheKey
    Set<String> keys = redisTemplate.keys("active_semester::*");
    assertThat(keys).isNotEmpty();

    String key = keys.iterator().next();
    Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

    // Configured TTL is 10 minutes = 600 seconds
    // Allow ±5 seconds for test execution time
    assertThat(ttlSeconds).isBetween(595L, 600L);
}
```

### Anti-Patterns to Avoid

- **`@Cacheable` on gRPC void methods directly:** The method signature `void getGroup(GroupRequest, StreamObserver)` returns void — Spring Cache ignores void return types and does not cache. Always extract the DB fetch into a separate bean method with a real return type.
- **`@Cacheable` calling method within same class:** Even if extraction is attempted as a private method in `AcademicGrpcServiceImpl`, it won't work — Spring AOP proxy is bypassed. The cacheable method MUST be on a separate injected bean.
- **JDK default serialization (`JdkSerializationRedisSerializer`):** Entity classes do not implement `Serializable`. Using JDK serialization would fail at cache write time with `NotSerializableException`. Use `GenericJackson2JsonRedisSerializer`.
- **`@CacheEvict(allEntries = true)` on groups cache:** Would evict ALL groups when one group changes. Use key-specific eviction: `@CacheEvict(value = "groups", key = "#id")`.
- **Caching in `@Transactional` method before commit:** The service methods are `@Transactional`. `@CacheEvict` on these methods will evict cache as the method exits, which may be before commit (if an exception is thrown). For eviction this is acceptable (worst case: evict cache entry that will be restored on next read). For `@CachePut`, this is more risky — avoid `@CachePut` in this phase, only use `@Cacheable` + `@CacheEvict`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-cache TTL | Custom `CacheInterceptor` subclass | `RedisCacheManager` with `withCacheConfiguration` per cache name | Spring's `RedisCacheManager` natively supports per-cache `RedisCacheConfiguration` with distinct `entryTtl` values |
| JSON serialization to Redis | Manual `ObjectMapper.writeValueAsString` in service code | `GenericJackson2JsonRedisSerializer` configured on `RedisCacheManager` | The serializer handles all types including `LocalDate`, `OffsetDateTime` (with `JavaTimeModule`) automatically |
| Cache invalidation tracking | DB table of cache keys | `@CacheEvict` / Spring `CacheManager.getCache().evict(key)` | Spring abstracts Redis key management; manual tracking is error-prone and unnecessary |
| Redis connection in tests | Mocked `RedisConnectionFactory` | Testcontainers `GenericContainer("redis:7-alpine")` | Real Redis validates actual TTL behavior and key patterns — mocks cannot verify TTL |
| Query count in tests | Log parsing or Hibernate statistics | `@SpyBean` on repository + Mockito `verify(times(1))` | Simple, direct, and does not require test-scope dependency on `datasource-proxy` |

**Key insight:** Spring Cache abstraction (`@Cacheable`, `@CacheEvict`, `@Caching`) is a complete, tested solution. The only custom code needed is the `RedisCacheManager` bean configuration and a thin `AcademicReadService` to solve the void-return and self-invocation problems.

---

## Common Pitfalls

### Pitfall 1: @Cacheable on void gRPC methods (silent no-op)

**What goes wrong:** Developer adds `@Cacheable("groups")` directly to `getGroup(GroupRequest, StreamObserver<GroupResponse>)`. Application starts, no error is thrown, but no caching happens. Every gRPC call hits the database.

**Why it happens:** Spring Cache requires a non-void return type. The annotation is silently ignored on void methods.

**How to avoid:** Introduce `AcademicReadService` as a separate Spring bean with non-void cacheable methods. `AcademicGrpcServiceImpl` injects and delegates to it.

**Warning signs:** Second call to `getGroup` still shows Hibernate SQL in logs.

### Pitfall 2: @Cacheable self-invocation bypass

**What goes wrong:** Developer extracts fetch methods as `private` or package-private methods in `AcademicGrpcServiceImpl` and annotates them with `@Cacheable`. Annotations are silently ignored.

**Why it happens:** Spring AOP creates a proxy wrapper around the bean. Internal calls (`this.fetchGroup(...)`) bypass the proxy entirely. The `@Cacheable` interceptor never fires.

**How to avoid:** Cache-annotated methods MUST be on a separate Spring-managed bean that is injected via constructor. This is why `AcademicReadService` is a dedicated `@Service`.

**Warning signs:** Same as Pitfall 1 — DB queries appear on every call.

### Pitfall 3: Redis container still excluded in cache integration tests

**What goes wrong:** The cache integration test class extends `AbstractAcademicIntegrationTest`, which excludes `RedisAutoConfiguration`. Spring Boot starts without cache manager. `@Cacheable` annotations are backed by a `NoOpCacheManager` (or an error is thrown on missing beans). Tests pass trivially — "cache hit" assertion passes because the spy shows only 1 call, but for the wrong reason.

**Why it happens:** `AbstractAcademicIntegrationTest.overrideProperties` adds `RedisAutoConfiguration` and `RedisRepositoriesAutoConfiguration` to `spring.autoconfigure.exclude`. This was added in Phase 5 to avoid connection failures in non-cache tests.

**How to avoid:** Cache tests MUST extend `AbstractAcademicCacheIntegrationTest` (new class), not `AbstractAcademicIntegrationTest`. The new base class omits the Redis exclusion and adds the Redis container.

**Warning signs:** `CacheManager` bean is `NoOpCacheManager` type; `RedisTemplate` is not available; `@Autowired RedisTemplate` fails to inject.

### Pitfall 4: Jackson cannot serialize OffsetDateTime without JavaTimeModule

**What goes wrong:** `GenericJackson2JsonRedisSerializer` serializes a `User` entity. Jackson encounters `OffsetDateTime` fields. Default Jackson (without `JavaTimeModule`) cannot serialize `java.time.*` types and throws `InvalidDefinitionException: Java 8 date/time type not supported by default`.

**Why it happens:** `JavaTimeModule` is not registered by default on a plain `new ObjectMapper()`.

**How to avoid:** When constructing the `ObjectMapper` for `GenericJackson2JsonRedisSerializer`, register `JavaTimeModule`:
```java
ObjectMapper om = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

**Warning signs:** `com.fasterxml.jackson.databind.exc.InvalidDefinitionException` in logs when a cached method is first called.

### Pitfall 5: @EnableCaching not present — no error, no caching

**What goes wrong:** `CacheConfig.java` creates the `RedisCacheManager` bean but `@EnableCaching` is omitted. All `@Cacheable` and `@CacheEvict` annotations are silently ignored — no proxy interceptors are registered.

**Why it happens:** Spring Cache AOP interceptors are only registered when `@EnableCaching` is present on a `@Configuration` class (or via `spring.cache.type` auto-config — but with a custom `CacheManager` bean, `@EnableCaching` is required).

**How to avoid:** `@EnableCaching` on `CacheConfig`. Verify at startup with `debug: true` log and look for `CacheInterceptor` or `AnnotationCacheAspect`.

**Warning signs:** `@CacheEvict` methods do not clear Redis entries; repeated calls continue to hit DB.

### Pitfall 6: Cache key collision between GetActiveSemester and GetCampusGeofence

**What goes wrong:** `fetchActiveSemester()` and `fetchCampusGeofence()` take no parameters — `@Cacheable` default key is the empty `SimpleKey.EMPTY`. If both share a cache name or if the cache name produces the same Redis key prefix, entries collide.

**Why it happens:** Spring's default key generation for zero-argument methods produces `SimpleKey.EMPTY`. Two different zero-arg methods in the same cache name would collide.

**How to avoid:** Use distinct cache names (`active_semester` vs `campus_geofence`). With distinct names, the Redis key pattern is `active_semester::org.springframework.cache.interceptor.SimpleKey []` vs `campus_geofence::org.springframework.cache.interceptor.SimpleKey []` — no collision.

**Warning signs:** `getCampusGeofence` returns semester data, or vice versa.

### Pitfall 7: TTL verification test is fragile due to timing

**What goes wrong:** Test asserts `ttlSeconds == 600` exactly. On slow CI machines, 1-2 seconds of test execution time elapse between cache write and TTL check. Assertion fails intermittently.

**Why it happens:** Redis TTL is set at cache write time and starts counting immediately. By the time the test checks it, some seconds have elapsed.

**How to avoid:** Assert a range: `assertThat(ttlSeconds).isBetween(595L, 600L)` — allows 5 seconds of slack. Alternatively assert `ttlSeconds > 0` and `ttlSeconds <= 600`.

---

## Code Examples

Verified patterns from official sources:

### CacheConfig Bean (Spring Data Redis docs pattern)
```java
// Source: https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.ttl.groups:PT5M}")
    private Duration groupsTtl;

    @Value("${cache.ttl.group-members:PT5M}")
    private Duration groupMembersTtl;

    @Value("${cache.ttl.users:PT5M}")
    private Duration usersTtl;

    @Value("${cache.ttl.active-semester:PT10M}")
    private Duration activeSemesterTtl;

    @Value("${cache.ttl.campus-geofence:PT1H}")
    private Duration campusGeofenceTtl;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("groups", base.entryTtl(groupsTtl))
                .withCacheConfiguration("group_members", base.entryTtl(groupMembersTtl))
                .withCacheConfiguration("users", base.entryTtl(usersTtl))
                .withCacheConfiguration("active_semester", base.entryTtl(activeSemesterTtl))
                .withCacheConfiguration("campus_geofence", base.entryTtl(campusGeofenceTtl))
                .build();
    }
}
```

### AcademicReadService (@Cacheable on separate bean)
```java
// Source: Spring Framework Cache docs
// https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html
@Service
public class AcademicReadService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final CampusSettingRepository campusSettingRepository;

    // constructor injection

    @Cacheable(value = "groups", key = "#groupId")
    public Group fetchGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
    }

    @Cacheable(value = "group_members", key = "#groupId")
    public List<User> fetchGroupMembers(Long groupId) {
        return userRepository.findByGroupId(groupId);
    }

    // key="#root.methodName" for zero-arg singletons avoids SimpleKey.EMPTY ambiguity
    @Cacheable(value = "active_semester", key = "'current'")
    public Semester fetchActiveSemester() {
        return semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "isActive", true));
    }

    @Cacheable(value = "campus_geofence", key = "'global'")
    public CampusSetting fetchCampusGeofence() {
        return campusSettingRepository.findById(1L)
                .orElseThrow(() -> new ResourceNotFoundException("CampusSetting", "id", 1L));
    }

    @Cacheable(value = "users", key = "#userId")
    public User fetchUserById(Long userId) {
        return userRepository.findByIdIncludingArchived(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
```

**Note on zero-arg key:** Use an explicit string literal key like `key = "'current'"` or `key = "'global'"` for zero-argument methods. This produces readable Redis keys like `active_semester::current` and avoids `SimpleKey.EMPTY` in the key which is less debuggable.

### Student Transfer with Cascading Eviction
```java
// UserService.java
@CacheEvict(value = "group_members", key = "#request.newGroupId")
@Transactional
public User transferStudent(Long id, TransferStudentRequest request) {
    User user = findUserById(id);
    Long oldGroupId = user.getGroupId();

    // ... existing transfer logic unchanged ...

    // Programmatically evict old group cache (ID only known at runtime)
    Cache groupMembersCache = cacheManager.getCache("group_members");
    if (groupMembersCache != null && oldGroupId != null) {
        groupMembersCache.evict(oldGroupId);
    }
    // Also evict users cache for the transferred student
    Cache usersCache = cacheManager.getCache("users");
    if (usersCache != null) {
        usersCache.evict(id);
    }

    return userRepository.save(user);
}
```

### AbstractAcademicCacheIntegrationTest Base Class
```java
// No @SpringBootTest here — subclasses provide that or it's inherited
@ActiveProfiles("test")
public abstract class AbstractAcademicCacheIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();

        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // RabbitMQ still excluded — not needed for cache tests
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }
}
```

### CacheIntegrationTest: Cache Hit Verification
```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "grpc.server.in-process-name=academic-cache-test",
        "grpc.server.port=-1",
        "grpc.client.inProcess.address=in-process:academic-cache-test",
        "grpc.client.inProcess.negotiationType=plaintext"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CacheIntegrationTest extends AbstractAcademicCacheIntegrationTest {

    @GrpcClient("inProcess")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @SpyBean
    private GroupRepository groupRepository;

    @SpyBean
    private UserRepository userRepository;

    @SpyBean
    private SemesterRepository semesterRepository;

    @SpyBean
    private CampusSettingRepository campusSettingRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearCaches(@Autowired CacheManager cacheManager) {
        cacheManager.getCacheNames().forEach(name ->
            cacheManager.getCache(name).clear());
        Mockito.clearInvocations(groupRepository, userRepository,
                semesterRepository, campusSettingRepository);
    }

    @Test
    void getGroup_secondCall_servedFromCache_exactlyOneDbQuery() {
        GroupRequest req = GroupRequest.newBuilder().setGroupId(1L).build();

        stub.getGroup(req);  // first call — DB hit
        stub.getGroup(req);  // second call — cache hit

        verify(groupRepository, times(1)).findById(1L);
    }

    @Test
    void getActiveSemester_ttlIsConfiguredValue() {
        stub.getActiveSemester(Empty.getDefaultInstance());

        Set<String> keys = redisTemplate.keys("active_semester::*");
        assertThat(keys).isNotEmpty();
        Long ttl = redisTemplate.getExpire(keys.iterator().next(), TimeUnit.SECONDS);
        // Configured: PT10M = 600 seconds, allow 5s execution slack
        assertThat(ttl).isBetween(595L, 600L);
    }

    @Test
    void transferStudent_invalidatesBothGroupCaches() {
        // Prime both group_members caches
        stub.getGroupMembers(GroupMembersRequest.newBuilder().setGroupId(1L).build());
        // ... prime group_members for group 2 (if exists) ...

        Mockito.clearInvocations(userRepository);

        // Perform transfer (via REST — use TestRestTemplate or call service directly)
        // ... transfer student from group 1 to group 2 ...

        // Both group_members entries should be evicted
        stub.getGroupMembers(GroupMembersRequest.newBuilder().setGroupId(1L).build());
        stub.getGroupMembers(GroupMembersRequest.newBuilder().setGroupId(2L).build());

        // Each call after eviction should hit DB once
        verify(userRepository, times(2)).findByGroupId(anyLong());
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RedisCacheManagerBuilderCustomizer` bean | Direct `RedisCacheManager` `@Bean` | Both supported in Spring Boot 3.x | `RedisCacheManagerBuilderCustomizer` is the Spring Boot auto-config integration point; direct `@Bean` overrides auto-config entirely. Direct `@Bean` is correct when custom serializer is needed (cannot set serializer via `RedisCacheManagerBuilderCustomizer` without explicit builder) |
| `ttddyy/datasource-proxy` (Maven group `net.ttddyy`) | `jdbc-observations/datasource-proxy` (Maven group `net.ttddyy.observation`) | 2023 — project renamed/moved | Use `net.ttddyy.observation:datasource-proxy:1.10` not the old `net.ttddyy:datasource-proxy` |
| Java 8 date types not supported in Jackson | `JavaTimeModule` registered manually | Jackson 2.x — stable | Register `JavaTimeModule` on any `ObjectMapper` used for Redis serialization |

**Deprecated/outdated:**
- `JdkSerializationRedisSerializer`: Still supported but fragile — avoid entirely in favor of `GenericJackson2JsonRedisSerializer`
- `RedisCache.create(String, RedisOperations, ...)` static factory: Replaced by `RedisCacheConfiguration` builder pattern in Spring Data Redis 2.x+

---

## Open Questions

1. **`@Caching` on `patchUser` — headman flag check is conditional**
   - What we know: `patchUser` handles multiple independent changes (displayName, groupId, isHeadman). Cache eviction for `group_members` only makes sense when `isHeadman` is being changed.
   - What's unclear: Should the `group_members` eviction happen unconditionally on every PATCH, or only when `isHeadman` changes?
   - Recommendation: Evict `users` cache unconditionally on any patchUser call. For `group_members`, use programmatic eviction inside the method conditioned on `request.isHeadman() != null` to avoid unnecessary evictions.

2. **Key serialization format for `List<User>` return type of `fetchGroupMembers`**
   - What we know: `GenericJackson2JsonRedisSerializer` with `activateDefaultTyping` stores the concrete type in the JSON `@class` field. `List<User>` serializes to a JSON array wrapped with the ArrayList class name.
   - What's unclear: If the entity class is refactored (package rename, class rename), all existing cache entries become undeserializable.
   - Recommendation: Accept this limitation for a development-phase project. Document that Redis must be flushed after a refactor involving entity class renames.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Redis (Docker) | CacheIntegrationTest via Testcontainers | ✓ (Docker available per docker-compose.yml) | redis:7-alpine pulled at test time | — |
| Docker daemon | Testcontainers | ✓ (docker-compose.yml already in use) | — | — |
| `spring-boot-starter-data-redis` | CacheConfig, RedisCacheManager | ✓ — already in build.gradle.kts line 24 | managed by Spring Boot 3.4 BOM | — |
| `testcontainers` core module | GenericContainer for Redis | ✓ — `testcontainers-bom:1.20.4` in dependencyManagement; `org.testcontainers:testcontainers` needed | 1.20.4 | — |
| `net.ttddyy.observation:datasource-proxy` | Query count tests (optional) | Not yet in build.gradle.kts | 1.10 (current) | Use `@SpyBean` on repositories instead (recommended) |

**Missing dependencies with no fallback:** None — core functionality works with existing dependencies.

**Missing dependencies with fallback:**
- `datasource-proxy` — can be replaced entirely with `@SpyBean` on repositories + Mockito `verify(times(1))`. This is simpler and the recommended approach. Add `datasource-proxy` only if counting at the JDBC level is specifically required.
- `org.testcontainers:testcontainers` core jar — likely already on classpath transitively via `org.testcontainers:postgresql`. Verify in `./gradlew dependencies` output; if not present, add explicitly.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via `spring-boot-starter-test` |
| Config file | none — auto-configured by Spring Boot Test |
| Quick run command | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest" -x processResources` |
| Full suite command | `./gradlew :services:academic-service:academic-app:test` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CACHE-01 | GetGroup served from cache on second call | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getGroup_secondCall_servedFromCache_exactlyOneDbQuery"` | ❌ Wave 0 |
| CACHE-01 | GetGroupMembers served from cache on second call | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getGroupMembers_secondCall_servedFromCache"` | ❌ Wave 0 |
| CACHE-01 | GetActiveSemester TTL matches configured value | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getActiveSemester_ttlIsConfiguredValue"` | ❌ Wave 0 |
| CACHE-01 | GetCampusGeofence served from cache on second call | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getCampusGeofence_secondCall_servedFromCache"` | ❌ Wave 0 |
| CACHE-01 | GetUserById served from cache on second call | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.getUserById_secondCall_servedFromCache"` | ❌ Wave 0 |
| CACHE-02 | Student transfer invalidates both old and new group_members caches | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.transferStudent_invalidatesBothGroupCaches"` | ❌ Wave 0 |
| CACHE-02 | Headman change invalidates groups and group_members for that group | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.headmanChange_invalidatesGroupAndMembersCache"` | ❌ Wave 0 |
| CACHE-02 | Semester activation invalidates active_semester cache | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.activateSemester_invalidatesActiveSemesterCache"` | ❌ Wave 0 |
| CACHE-02 | User archive invalidates users cache for that user | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest.archiveUser_invalidatesUsersCache"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :services:academic-service:academic-app:test --tests "*.CacheIntegrationTest" -x processResources`
- **Per wave merge:** `./gradlew :services:academic-service:academic-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `AbstractAcademicCacheIntegrationTest.java` — base class with Postgres + Redis Testcontainers
- [ ] `CacheIntegrationTest.java` — all CACHE-01 and CACHE-02 test methods
- [ ] `CacheConfig.java` — `@EnableCaching` + `RedisCacheManager` bean
- [ ] `AcademicReadService.java` — `@Cacheable` methods extracted from gRPC service

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| No Lombok in `*-api-contract` modules | `AcademicReadService` and `CacheConfig` live in `academic-app` — Lombok permitted but not needed for service/config classes |
| Enums stored as lowercase strings in PostgreSQL | Irrelevant to caching layer — entity mapping unchanged |
| `ddl-auto: validate` | Unchanged — no DB schema changes in this phase |
| `@ControllerAdvice` for error handling | Unchanged — gRPC exception advice (`GrpcExceptionAdvice`) already handles `ResourceNotFoundException` |
| HATEOAS Level 3 for REST | Unchanged — this phase adds no REST endpoints |
| REST paths under `/api/{service}/...` | Unchanged |
| gRPC methods as primary mutation-free read paths | Confirmed — caching targets are exactly the read gRPC methods |
| Packages: `ru.rutcampustrack.{service}.{module}` | New classes: `ru.rutcampustrack.academic.config.CacheConfig`, `ru.rutcampustrack.academic.grpc.AcademicReadService` |

---

## Sources

### Primary (HIGH confidence)
- [Spring Data Redis — Redis Cache](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html) — `RedisCacheManager`, `RedisCacheConfiguration`, per-cache TTL via `withCacheConfiguration`
- [Spring Framework — Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html) — `@Cacheable`, `@CacheEvict`, `@Caching`, self-invocation limitation documented
- [Spring Boot — Caching](https://docs.spring.io/spring-boot/reference/io/caching.html) — `RedisCacheManagerBuilderCustomizer`, auto-config behavior
- Prior phase research in `.planning/research/STACK.md` — `GenericJackson2JsonRedisSerializer` pattern, already validated for this project

### Secondary (MEDIUM confidence)
- [Baeldung — Spring Boot Cache with Redis](https://www.baeldung.com/spring-boot-redis-cache) — `RedisCacheManager` configuration patterns, verified against official Spring docs
- [rieckpil — Testing Caching Mechanism with Testcontainers](https://rieckpil.de/testing-caching-mechanism-with-testcontainers-in-spring-boot/) — Testcontainers Redis + `@SpyBean` test pattern
- [Baeldung — Invoke Spring @Cacheable from Another Method](https://www.baeldung.com/spring-invoke-cacheable-other-method-same-bean) — self-invocation solutions
- [Baeldung — Testing @Cacheable on Spring Data Repositories](https://www.baeldung.com/spring-data-testing-cacheable) — `@SpyBean` + `verify(times(1))` pattern for cache hit assertion

### Tertiary (LOW confidence)
- [jdbc-observations/datasource-proxy GitHub](https://github.com/jdbc-observations/datasource-proxy) — `datasource-proxy` for query counting in tests (flagged as optional — `@SpyBean` is preferred)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `spring-boot-starter-data-redis` already present, APIs stable since Spring Boot 3.x
- Architecture patterns: HIGH — `@Cacheable` on separate bean to avoid self-invocation is well-established; `RedisCacheManager` with `withCacheConfiguration` is the documented API
- Pitfalls: HIGH — void-return @Cacheable, self-invocation, missing `@EnableCaching`, and JavaTimeModule issues are all documented Spring behaviors verified against official sources
- Test patterns: MEDIUM — `@SpyBean` + `verify(times(1))` is standard; TTL range assertion is pragmatic and stable

**Research date:** 2026-03-31
**Valid until:** 2026-05-01 (Spring Cache API is stable; Testcontainers 1.20.x series is stable)
