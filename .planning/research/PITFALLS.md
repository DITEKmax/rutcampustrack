# Pitfalls Research

**Domain:** Academic Service — CRUD, gRPC server, Redis caching, RabbitMQ publishing added to existing microservice system
**Researched:** 2026-03-30
**Confidence:** HIGH (verified against actual codebase, schema, existing Auth Service patterns)

---

## Critical Pitfalls

### Pitfall 1: Flyway Checksum Collision on Existing V1/V2 Migrations

**What goes wrong:**
Academic Service already has `V1__baseline.sql` and `V2__seed_test_data.sql` deployed to `academic_db` in production/staging. Any modification to these files (even whitespace) causes Flyway to throw `FlywayException: Migration checksum mismatch` on startup. The service refuses to start until the problem is manually resolved.

**Why it happens:**
Developers find a small error in the existing seed data or want to add a column they forgot, edit an existing migration file instead of creating `V3__...sql`, and push. Everything works locally (clean DB) but fails against any environment where Flyway already ran the original versions.

**How to avoid:**
Treat V1 and V2 as immutable. All new schema changes go into V3, V4, etc. This includes:
- Adding columns (ALTER TABLE)
- Adding new ENUMs or new ENUM values
- Adding indexes
- Any new test data needed by integration tests

If test data from V2 is insufficient for Academic Service tests, add `V3__seed_academic_data.sql`. Never touch V1 or V2.

**Warning signs:**
- Someone opens V1 or V2 in an editor to "fix a small thing"
- A PR diff shows changes to existing migration files
- Tests pass locally but fail in CI Testcontainers (because Testcontainers creates a fresh DB, so checksum errors only appear against persistent DBs)

**Phase to address:**
Phase 2.1 (Entity + Repository setup) — establish the rule before any entity work begins. Add a comment block at the top of V1 and V2: `-- IMMUTABLE: do not edit`.

---

### Pitfall 2: Auth Service Entity vs Academic Service Entity Divergence (Shared Table, Two Owners)

**What goes wrong:**
`users` table lives in `academic_db` and is owned by Academic Service (Flyway, full JPA entity). Auth Service also maps `users` via JPA (`Flyway disabled`) with its own `User` entity class. When Academic Service adds a new column (e.g., `updated_at` trigger logic, a new field) or adds a new ENUM value for `account_status`, Auth Service's entity gets a Hibernate validation error at startup (`ddl-auto: validate`).

The current Auth `User.java` maps only the columns it needs for login, but if the columns don't match what Hibernate expects against the database state, startup fails.

**Why it happens:**
Two services map the same table. Hibernate `validate` checks that every mapped column exists. If Academic Service's Flyway migration renames or drops a column, Auth Service breaks silently at next deploy.

**How to avoid:**
- Auth Service entity must only map columns it actively reads: `id`, `login`, `password_hash`, `role`, `status`, `is_headman`, `group_id`, `telegram_id`. It already does this correctly.
- Academic Service must never rename or remove columns that Auth Service maps. This is a contract.
- Document in a comment inside Auth `User.java` which columns are "shared contract" between the two services.
- For any new columns Academic Service needs, add them via Flyway migration — they are additive and do not break Auth Service.

**Warning signs:**
- Auth Service fails to start after an Academic Service Flyway migration runs
- Auth Service logs: `Schema-validation: missing column [...]`
- A new enum value added to `user_role` or `account_status` in PostgreSQL but not added to the Java enum in auth-service

**Phase to address:**
Phase 2.1 (Entity layer) — document the shared-table contract. Add integration test for Auth Service that runs after any Academic Service schema change.

---

### Pitfall 3: gRPC Port Conflict and Spring MVC Port Sharing

**What goes wrong:**
Academic Service runs on port 9091 (HTTP/REST). When `grpc-spring-boot-starter` (net.devh) is added, it starts a gRPC server on a separate port (default 9090 in the starter). That port is already used by Auth Service. The service either fails to bind, or internal callers connect to the wrong service.

**Why it happens:**
`grpc-spring-boot-starter` defaults to port 9090. Developers add the dependency, run the app, and the gRPC server silently tries to bind 9090 while Auth Service already holds it. In Docker Compose they are in separate containers so this does not cause immediate failure, but callers from Schedule Service (Phase 3) that hardcode `academic-service:9090` will hit Auth Service instead.

**How to avoid:**
Explicitly configure the gRPC port in `application.yml` for Academic Service:

```yaml
grpc:
  server:
    port: 9091   # same as HTTP? NO — gRPC needs its own port
    port: 19091  # use a dedicated gRPC port range
```

Recommended convention: HTTP ports 9090-9094, gRPC ports 19090-19094. Academic gRPC = 19091. Update Docker Compose to expose 19091. Update all future gRPC clients (Schedule Service, Attendance Service) to call `academic-service:19091`.

Also: the `net.devh:grpc-spring-boot-starter` must be excluded from the API contract module — it belongs only in `academic-app`.

**Warning signs:**
- App starts but gRPC calls from other services return `UNAVAILABLE` connection refused
- Docker Compose logs show `bind: address already in use` on port 9090
- gRPC health check returning data from Auth Service (wrong service)

**Phase to address:**
Phase 2.3 (gRPC server implementation) — configure port before writing any RPC handlers.

---

### Pitfall 4: @Cacheable Without Explicit Key = Cache Poisoning Across Users

**What goes wrong:**
`@Cacheable("group-members")` without a `key` expression caches the result of the first call and returns it to ALL subsequent callers regardless of `group_id` parameter. Every student in the university sees the members of the first group ever cached.

The Spring default key uses `SimpleKeyGenerator` which generates a key from method parameters — BUT only if the parameters are correctly passed. If the service method signature takes a Long `groupId` but the cache is configured on a facade method that calls with a constant, the key degenerates.

**Why it happens:**
Developers write `@Cacheable("group:{id}:members")` thinking the cache name is the key. The cache name is not the key — it is the cache region. The `key` attribute must be set separately: `@Cacheable(value = "group-members", key = "#groupId")`.

Also, Spring's default key for single-parameter methods IS the parameter value, so this only breaks for zero-parameter methods (e.g., `getActiveSemester()`, `getCampusGeofence()`) — those correctly default to `SimpleKey.EMPTY` and work fine.

**How to avoid:**
Always explicit:
```java
@Cacheable(value = "group-info", key = "#groupId")
public GroupResponse getGroup(Long groupId) { ... }

@Cacheable(value = "group-members", key = "#groupId")
public List<StudentInfo> getGroupMembers(Long groupId) { ... }

@Cacheable(value = "teacher-subjects", key = "#teacherId + ':' + #semesterId")
public List<TeacherSubjectInfo> getTeacherSubjects(Long teacherId, Long semesterId) { ... }
```

For no-param methods, explicit empty key is optional but documents intent:
```java
@Cacheable(value = "semester-active", key = "'active'")
public SemesterResponse getActiveSemester() { ... }
```

**Warning signs:**
- Different users get identical group member lists regardless of their group
- Cache hit rate is 100% immediately after first request (suspiciously perfect)
- Integration test: call `getGroup(1L)` then `getGroup(2L)` — second call returns group 1 data

**Phase to address:**
Phase 2.4 (Redis caching layer) — write cache key policy before implementing any `@Cacheable`.

---

### Pitfall 5: Cache Invalidation Missing for Cascading Updates

**What goes wrong:**
Admin moves a student from group A to group B (transfer). The service correctly updates `users.group_id` and inserts into `student_group_history`. But `group:{groupA}:members` and `group:{groupB}:members` caches still hold stale data. Schedule Service and Attendance Service call `GetGroupMembers` via gRPC which reads from cache — they see the student in old group for up to 5 minutes. Automatic `absent` records get written for the wrong group.

**Why it happens:**
Developers add `@CacheEvict` only to the method that directly modifies the cached resource. They forget that:
1. Transferring a student invalidates TWO group caches (the old group AND the new group)
2. Archiving a user invalidates the group membership cache for that user's group
3. Assigning/removing headman invalidates `group:{id}:info` (headman flag is in the group response)
4. Teacher-subject-group assignment invalidates `teacher:{id}:subjects`

**How to avoid:**
Map all cache dependencies before coding:

| Operation | Caches to Evict |
|-----------|-----------------|
| Transfer student | `group:{oldGroupId}:members`, `group:{newGroupId}:members` |
| Archive user | `group:{user.groupId}:members` |
| Assign/revoke headman | `group:{groupId}:info`, `group:{groupId}:members` |
| Create/delete subject | `teacher:{teacherId}:subjects` for all affected teachers |
| Activate/deactivate semester | `semester:active` |
| Update campus geofence | `campus:geofence` |

Use `@Caching` annotation for multi-cache evictions:
```java
@Caching(evict = {
    @CacheEvict(value = "group-members", key = "#oldGroupId"),
    @CacheEvict(value = "group-members", key = "#newGroupId")
})
public void transferStudent(Long userId, Long oldGroupId, Long newGroupId) { ... }
```

**Warning signs:**
- After a student transfer, gRPC `GetGroupMembers` still returns the student in the old group
- Stale `is_headman` flag returned after headman is revoked
- Tests that verify cache invalidation are absent

**Phase to address:**
Phase 2.4 (Redis caching layer) — document the eviction map in a comment block inside the cache config class before implementing.

---

### Pitfall 6: Soft Delete Leaking into JPA Queries

**What goes wrong:**
Users with `status = 'archived'` appear in group member lists, teacher subject assignments, headman checks, and dashboard counts. The `GetGroupMembers` gRPC RPC returns archived students. Schedule Service generates lessons and expects to notify those students. Attendance auto-absent fires for archived students.

**Why it happens:**
JPA repositories default to `findAll()` with no filter. Developers add role-based filters but forget status filters. The soft-delete pattern requires explicit `WHERE status != 'archived'` in every query, every JPQL expression, every Spring Data method name.

**How to avoid:**
Two-layer protection:

1. Use a base JPA Specification or add `@Where` on the entity:
```java
@Entity
@Where(clause = "status != 'archived'")
public class User { ... }
```
WARNING: `@Where` is a Hibernate-specific annotation. Verify it works with `ddl-auto: validate` and does not break Auth Service's entity (Auth Service uses its own User entity).

2. All repository methods that list users add `AndStatusNot(AccountStatus.ARCHIVED)` in method names, or use explicit JPQL. Never rely on "it's obvious".

3. The gRPC `GetGroupMembers` handler must explicitly filter `status = 'active'` regardless of what the repository default is.

**Warning signs:**
- Dashboard shows suspended/expelled students in group counts
- `GetGroupMembers` returns users with status != active
- Integration test: archive a student, call `GetGroupMembers` — verify they are absent from response

**Phase to address:**
Phase 2.1 (Entity + Repository layer) — add the `@Where` clause on entity definition and add a failing integration test for the archived-user scenario before writing any service logic.

---

### Pitfall 7: N+1 Queries When Building HATEOAS Responses for Paginated Lists

**What goes wrong:**
`GET /api/academic/users?page=0&size=20` returns 20 users. The assembler for each `UserResponse` calls `groupRepository.findById(user.getGroupId())` to embed the group name in the link. This produces 1 query for the user page + 20 queries for group lookups = 21 queries per page request.

With 500+ students across 10 groups this hits the database 21 times per page. At 50 concurrent admin sessions during enrollment period, this becomes 1050 queries for a single paginated list endpoint.

**Why it happens:**
`User` entity maps `group_id` as a plain `Long` (correct — avoids eager fetch of `Group` entity). The assembler tries to enrich the response with group name but does the lookup per-entity instead of batching.

**How to avoid:**
Option A (preferred for this scale): Keep `group_id` as Long, include it in the response, let the client resolve group names via a separate `GET /api/academic/groups/{id}` call (already cached in Redis).

Option B: Fetch users with a JPQL JOIN FETCH to group in a single query when group name is needed. Only needed if the client genuinely needs denormalized group name in the list response.

Option C: Use a custom query projection that includes group name via JOIN, returns a flat DTO directly — no second-level lookup.

Never lookup by individual ID inside an assembler loop.

**Warning signs:**
- Hibernate `show-sql: true` logs show repeated identical queries with different IDs
- Response time scales linearly with page size
- `DEBUG` logs show 20+ `SELECT * FROM groups WHERE id = ?` during a single request

**Phase to address:**
Phase 2.2 (REST API + HATEOAS) — decide the denormalization strategy for HATEOAS assemblers before writing any assembler code. Document the decision.

---

### Pitfall 8: Role Authorization Based on Header Strings — No Type Safety

**What goes wrong:**
Gateway injects `X-User-Role: STUDENT` (uppercase string from JWT claim). The Academic Service controller reads `@RequestHeader("X-User-Role") String role` and does `if (role.equals("ADMIN"))`. A future change to the JWT claim format (e.g., lowercase `admin` from a different token issuance path, or `ROLE_ADMIN` from Spring Security convention) silently breaks authorization — the check returns false, the ADMIN sees 403 on their own endpoints.

Also: HEADMAN authorization is a two-step check: `role == STUDENT && is_headman == true`. Forgetting the `is_headman` header check means any student can access headman endpoints if they know the URL.

**Why it happens:**
String-based header auth lacks the type safety of a proper security context. Developers write `role.equals("ADMIN")` in one place, `role.equalsIgnoreCase("admin")` in another, and `role.equals("ROLE_ADMIN")` in a third. No single source of truth.

**How to avoid:**
Create a `RequestContext` record or similar object populated from headers at the entry point (a `HandlerMethodArgumentResolver` or a dedicated `@ControllerAdvice` interceptor):

```java
public record RequestContext(Long userId, UserRole role, Long groupId, boolean isHeadman) {
    public boolean isAdmin() { return role == UserRole.ADMIN; }
    public boolean isHeadman() { return role == UserRole.STUDENT && isHeadman; }
    public boolean isTeacher() { return role == UserRole.TEACHER; }
}
```

Parse `X-User-Role` into `UserRole` enum once. If parsing fails (unknown value), return 403. Never do string comparisons in controllers or services.

**Warning signs:**
- Multiple `role.equals("...")` scattered across controllers
- Headman check only checks role, not `X-Is-Headman` header
- No centralized authority/permission utility class

**Phase to address:**
Phase 2.2 (REST API scaffolding) — implement `RequestContext` before writing any role-based endpoint.

---

### Pitfall 9: RabbitMQ Exchange Declaration Mismatch

**What goes wrong:**
Academic Service declares `rut-uit.events` exchange as `fanout` with `durable: true` on first startup. Later, a developer changes the declaration to `direct` or sets `durable: false` in a config refactor. RabbitMQ throws `PRECONDITION_FAILED — inequivalent arg 'type'` and the service fails to connect to the broker. All event publishing stops silently — no exception is thrown by default if the channel is reused and the error is swallowed.

Also: the exchange may already exist from a previous Docker Compose run. Changing exchange properties without deleting the exchange first causes the same error.

**Why it happens:**
Spring AMQP auto-declares exchanges on startup. If the exchange exists with different properties, RabbitMQ rejects the re-declaration. Developers don't check existing exchange state before changing configuration.

**How to avoid:**
Lock in the exchange declaration in a dedicated `RabbitMqConfig` class:

```java
@Bean
public FanoutExchange academicEventsExchange() {
    return ExchangeBuilder.fanoutExchange("rut-uit.events")
        .durable(true)
        .build();
}
```

Rule: never modify the exchange `type` or `durable` flag after first deploy. For Academic Service, the exchange is already defined in the event-schemas as fanout — keep it fanout. Document this constraint in the config class.

If the exchange needs to change, run `rabbitmqadmin delete exchange name=rut-uit.events` against the broker before redeployment.

**Warning signs:**
- `AmqpIOException: connection reset` or `PRECONDITION_FAILED` in logs
- Events appear to publish (no exception) but notification services receive nothing
- Exchange properties in RabbitMQ management UI differ from code declarations

**Phase to address:**
Phase 2.5 (RabbitMQ event publishing) — define `RabbitMqConfig` with locked-in exchange declaration as the first step.

---

### Pitfall 10: Auto-Generated Login Sequence Gap Under Concurrent User Creation

**What goes wrong:**
Two admin sessions create students simultaneously. Both call `SELECT MAX(id) FROM users WHERE login LIKE 'student%'` to find the next sequence number. Both get the same result (e.g., 5), both try to insert `student00006`. One succeeds, one gets `duplicate key value violates unique constraint "users_login_key"`. The admin sees a 500 error with no actionable message.

**Why it happens:**
Using MAX()+1 as a sequence in application code has a TOCTOU race condition. The check and the insert are not atomic.

**How to avoid:**
Use a dedicated PostgreSQL sequence, not application-level MAX():

```sql
-- V3__add_login_sequences.sql
CREATE SEQUENCE student_login_seq START 1 INCREMENT 1;
CREATE SEQUENCE teacher_login_seq START 1 INCREMENT 1;
```

```java
// In user creation service:
Long seq = (Long) em.createNativeQuery("SELECT nextval('student_login_seq')").getSingleResult();
String login = String.format("student%05d", seq);
```

This is atomic. Sequences have gaps on rollback, but gaps are acceptable — `student00003` existing without `student00002` is fine. What is not fine is a 500 error on concurrent creation.

**Warning signs:**
- `DataIntegrityViolationException: unique constraint "users_login_key"` in logs under load
- Integration test with two concurrent user creation requests reveals the race

**Phase to address:**
Phase 2.2 (User CRUD) — implement sequence-based login generation from day one. Do not prototype with MAX().

---

### Pitfall 11: gRPC + Spring Boot — Bean Initialization Order with @GrpcService

**What goes wrong:**
`@GrpcService` beans are instantiated by the gRPC server framework before the Spring application context fully initializes. If a gRPC service implementation (`AcademicGrpcServiceImpl`) injects a Spring `@Service` bean that depends on Redis (`@Cacheable`), and Redis is not yet connected, the gRPC server starts and accepts connections that immediately fail with `NullPointerException` on the first call.

Also: `net.devh:grpc-spring-boot-starter` does not participate in Spring's conditional bean loading the same way Spring MVC controllers do. Misconfigured `@EnableCaching` (cache manager not initialized before gRPC beans) causes `NoSuchBeanDefinitionException: No qualifying bean of type 'CacheManager'` at startup.

**Why it happens:**
`@GrpcService` creates beans at a different lifecycle phase. The assumption that Spring Boot auto-configuration runs before gRPC server initialization is not always correct, especially when `@Cacheable` proxies are involved.

**How to avoid:**
- Define `CacheManager` bean explicitly (not rely only on auto-config `@EnableCaching`):
```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.builder(factory)
            .withInitialCacheConfigurations(cacheConfigs())
            .build();
    }
}
```
- Keep gRPC service implementations thin — delegate immediately to `@Service` beans. Do not put business logic or repository calls directly in `@GrpcService` classes.
- Test gRPC endpoints with Testcontainers (Redis + PostgreSQL) in integration tests to catch initialization failures before runtime.

**Warning signs:**
- `IllegalStateException: CacheManager is not initialized` at startup
- gRPC health check returns `SERVING` but actual RPC calls return `INTERNAL` on first call
- App starts without error but first `GetGroup` gRPC call throws NPE in service layer

**Phase to address:**
Phase 2.3 (gRPC server) — write a `CacheConfig` class with explicit `CacheManager` bean BEFORE connecting gRPC service implementations to it.

---

### Pitfall 12: Circular Reference Between Group and User Entities

**What goes wrong:**
`User` entity has `@ManyToOne Group group` (eager), `Group` entity has `@OneToMany List<User> members` (lazy). Jackson serializes a `User` response, hits the `Group`, starts serializing `Group.members`, hits a `User`, starts serializing that `User.group`, and throws `StackOverflowError` or infinite JSON recursion.

This does not appear in unit tests (mocked entities have no circular references) and only manifests in integration tests or production on first response.

**Why it happens:**
Bidirectional JPA relationships combined with Jackson serialization. Developers add `@OneToMany` on `Group` for convenience queries, forgetting the serialization impact.

**How to avoid:**
In Academic Service: `User` entity maps `group_id` as a plain `Long` field (Auth Service already does this correctly). Do NOT add `@ManyToOne Group` to the `User` entity — the group relationship is navigated via `groupId` through the repository. Do NOT add `@OneToMany List<User>` to `Group` entity. All entity relationships in Academic Service are expressed as Long foreign keys, not JPA associations. This is already the correct pattern from the Auth Service `User.java`.

If group composition is needed in a query, use a JPQL query: `SELECT u FROM User u WHERE u.groupId = :groupId`.

**Warning signs:**
- `StackOverflowError` in Jackson serializer during first real API call
- `com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Infinite recursion`
- Entities have `@ManyToOne` or `@OneToMany` annotations

**Phase to address:**
Phase 2.1 (Entity layer) — establish the rule: no JPA associations, only Long FK fields. Document it in a comment on the base entity or in a code review checklist.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip `RequestContext` abstraction, read headers directly in each controller | Faster to write | Role check logic duplicated, inconsistency bugs | Never — this is a 1-hour investment that prevents weeks of auth bugs |
| Use `@Transactional` on all service methods without thinking | No lazy-load exceptions | Holds DB connection for entire HTTP request including Redis calls | Never for read-only endpoints — use `@Transactional(readOnly = true)` |
| Return `Page<Entity>` instead of `Page<DTO>` from service | Less mapping code | Exposes persistence layer to controller, breaks serialization | Never — always map to DTO before leaving service boundary |
| Skip `@CacheEvict` on write methods until "later" | Faster feature development | Stale data cascades to gRPC consumers in Phase 3 | Acceptable in first dev iteration IF accompanied by a TODO test that fails |
| Use `@SpringBootTest` for every test | Simple setup | Tests run in 30+ seconds, entire test suite takes 10 minutes | Use `@WebMvcTest` for controller logic, `@DataJpaTest` for repository, only `@SpringBootTest` for integration scenarios |
| Hardcode `X-User-Role` header string comparisons | Simple to prototype | Type safety issues, future claim format changes break auth silently | Prototyping only — replace before first PR |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| gRPC + Spring Boot (net.devh) | Adding `grpc-spring-boot-starter` to `academic-api-contract` module | Add only to `academic-app` — contract module is a pure java-library |
| gRPC port | Using default port 9090 (conflicts with Auth Service) | Explicitly set `grpc.server.port=19091` in `application.yml` |
| Redis `@Cacheable` + gRPC | gRPC service bean calls `@Cacheable` method before `CacheManager` is ready | Explicit `CacheManager` bean definition with `@DependsOn` if needed |
| RabbitMQ fanout exchange | Declaring exchange with wrong type (direct instead of fanout) | Lock exchange type in `RabbitMqConfig` as `fanout`, document as immutable |
| RabbitMQ event publishing | Publishing inside `@Transactional` — message sent even if DB transaction rolls back | Use `@TransactionalEventListener(phase = AFTER_COMMIT)` or publish after transaction completes |
| Flyway + Auth Service | Auth Service has `flyway.enabled: false` — Academic Service migration runs, Auth Service `validate` breaks | Every new Flyway migration in Academic Service must be checked against Auth Service `User.java` mapped columns |
| `@Cacheable` + Spring proxy | Calling `@Cacheable` method from within the same bean (self-invocation) bypasses the proxy | Always call cacheable methods from a different bean, never `this.getGroup()` |
| PostgreSQL EXCLUDE constraint on semesters | `EXCLUDE USING btree` requires `btree_gist` extension | Extension is already created in V1 — do not drop or recreate the constraint in new migrations |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| N+1 in group member list (assembler per-entity lookup) | Response time scales with page size, `show-sql` logs show 20+ identical selects | Never resolve foreign key name in assembler loop — use projection or accept ID-only | At 20+ students per page (first load) |
| Uncached `getActiveSemester()` called per request | Every API call that needs semester context makes a DB query | Cache with `semester:active` key, TTL 30 min, evict on semester change | At 50+ concurrent users |
| `@Transactional` on write methods holding connection during RabbitMQ publish | DB connection held while waiting for broker ack, exhausting connection pool under load | Publish event AFTER transaction commits via `TransactionSynchronizationManager` | At 10+ concurrent writes |
| Full table scan on `users` when listing by role | `SELECT * FROM users WHERE role = ?` without index | Index `idx_users_role` already exists in V1 migration | At 1000+ users |
| Password generation via `BCryptPasswordEncoder` with default cost 10 on every user creation call blocking the thread | Batch user import (50 students at once) takes 50+ seconds | BCrypt is intentionally slow — acceptable per user, but batch import needs async handling or pre-generated passwords | At 10+ concurrent user creation requests |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Exposing `initial_password` in list endpoints | Any caller who lists users can harvest all initial passwords | Only return `initial_password` in the single-user creation response and `GET /users/{id}` for ADMIN. Exclude from paginated list DTO. Set to NULL in DB after first password change. |
| HEADMAN accessing other groups' data | Headman of group A modifies subjects for group B by changing path parameter | In every HEADMAN endpoint, verify that `groupId` in path matches `X-Group-Id` header. Log attempt and return 403. |
| STUDENT accessing other students' profiles | `GET /users/{id}` returns full profile including phone, telegram_id | STUDENT role can only access their own `userId` (must equal `X-User-Id`). Return 403 for other IDs. |
| Logging `initial_password` in DEBUG output | Plaintext passwords in logs | Explicitly mask `initial_password` field in any log statements. Never log the full user entity. |
| ADMIN deleting a user with `DELETE /users/{id}` (hard delete) | Loss of `student_group_history`, attendance records, homework completions referencing the user ID | Hard delete is not implemented — only `PATCH /users/{id}` setting `status=archived`. Controller must not expose a DELETE mapping for users. |

---

## "Looks Done But Isn't" Checklist

- [ ] **Soft delete:** `GET /users` returns only `status != archived` — verify with integration test: create user, archive, list, assert absent
- [ ] **Cache invalidation:** `getGroupMembers` returns updated list after student transfer — verify by evict test, not just happy-path test
- [ ] **gRPC GetGroupMembers:** returns only active (non-archived) students — verify with Testcontainers integration test calling actual gRPC endpoint
- [ ] **HEADMAN group boundary:** HEADMAN can only modify subjects/assistants for their own `group_id` — verify with a test that uses a different `X-Group-Id`
- [ ] **RabbitMQ publish after commit:** `group.updated` event is NOT published if the DB transaction rolls back — verify by simulating a constraint violation mid-transaction
- [ ] **Semester constraint:** Only one active semester exists at a time — the EXCLUDE constraint in PostgreSQL enforces this, but verify that the service returns a clear error (not 500) when trying to activate a second semester
- [ ] **Login sequence:** Concurrent user creation (two simultaneous requests) does not produce a 500 on duplicate login — test with two parallel threads
- [ ] **gRPC server port:** gRPC server is on port 19091, not 9090 — verify by connecting with `grpcurl localhost:19091`
- [ ] **`@Cacheable` self-invocation:** No cacheable method calls another cacheable method within the same `@Service` bean via `this.` — code review check
- [ ] **HATEOAS links correctness:** `_links.self.href` points to the correct `/api/academic/...` path (not direct service port) — test through Gateway

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Flyway checksum mismatch on V1/V2 | MEDIUM | 1. Revert the file change. 2. Create a new Vn migration for the intended change. 3. Manually run `UPDATE flyway_schema_history SET checksum=... WHERE version='1'` only if absolutely necessary (last resort). |
| Auth Service breaks after Academic migration | HIGH | 1. Identify which column/type changed. 2. Roll back the migration (delete row from flyway_schema_history + reverse the DDL). 3. Add the column as nullable first, deploy Auth Service update, then make it non-null. |
| Cache poisoning (wrong key) | LOW | 1. Flush the affected cache key with `redis-cli DEL`. 2. Fix the `key` expression. 3. Deploy. No data loss. |
| gRPC port conflict | LOW | 1. Stop services. 2. Update `grpc.server.port` in application.yml. 3. Update all gRPC client configs. 4. Restart. |
| RabbitMQ exchange type mismatch | LOW | 1. `rabbitmqadmin delete exchange name=rut-uit.events`. 2. Restart service — exchange is recreated with correct type. |
| Circular entity serialization StackOverflow | MEDIUM | 1. Remove the bidirectional JPA association. 2. Replace with Long FK field. 3. Update all queries. |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Flyway checksum collision | Phase 2.1 (Entity layer) | Mark V1/V2 as immutable; CI fails if they are modified |
| Auth Service entity divergence | Phase 2.1 (Entity layer) | Auth Service integration tests run after every Academic migration |
| gRPC port conflict | Phase 2.3 (gRPC server) | `grpcurl localhost:19091 list` succeeds; `localhost:9090` still serves Auth |
| @Cacheable missing explicit key | Phase 2.4 (Redis cache) | Integration test: call method twice with different args, verify different cache entries |
| Cache invalidation gaps | Phase 2.4 (Redis cache) | Integration tests verify stale-data scenarios for every mutating operation |
| Soft delete leaking | Phase 2.1 (Entity layer) | Integration test: archived user absent from GetGroupMembers gRPC response |
| N+1 in paginated responses | Phase 2.2 (REST HATEOAS) | `show-sql: true` in test profile; assert query count per request |
| Header auth type safety | Phase 2.2 (REST HATEOAS) | `RequestContext` implemented before first role-checked endpoint |
| RabbitMQ exchange mismatch | Phase 2.5 (RabbitMQ) | `RabbitMqConfig` bean with locked exchange type; integration test publishes and consumes |
| Login sequence race condition | Phase 2.2 (User CRUD) | Concurrent test: two parallel user creation requests both succeed with distinct logins |
| gRPC bean init order | Phase 2.3 (gRPC server) | Testcontainers integration test: app starts with Redis + PG; first gRPC call succeeds |
| Circular JPA entity reference | Phase 2.1 (Entity layer) | No `@ManyToOne` or `@OneToMany` in any entity — verified by code review and absence of `@JoinColumn` |

---

## Sources

- Direct codebase analysis: `services/auth-service/src/main/java/.../entity/User.java` (shared table pattern confirmed)
- Direct codebase analysis: `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql`, `V2__seed_test_data.sql`
- Direct codebase analysis: `services/academic-service/academic-app/build.gradle.kts` (gRPC dependency commented out, to be added Phase 2)
- Direct codebase analysis: `services/academic-service/academic-app/src/main/resources/application.yml` (port 9091, Flyway enabled)
- Direct codebase analysis: `services/auth-service/src/main/resources/application.yml` (port 9090, Flyway disabled)
- Direct codebase analysis: `proto/academic.proto` (7 RPCs, gRPC package `ru.rutcampustrack.academic.grpc`)
- Direct codebase analysis: `event-schemas/homework.published.json` (fanout exchange event contract)
- Spring AMQP documentation: transactional event publishing patterns
- Spring Cache documentation: `@Cacheable` key generation, self-invocation bypass limitation
- net.devh grpc-spring-boot-starter: port configuration defaults, bean lifecycle order

---
*Pitfalls research for: Academic Service (CRUD + gRPC + Redis + RabbitMQ) added to existing microservice system*
*Researched: 2026-03-30*
