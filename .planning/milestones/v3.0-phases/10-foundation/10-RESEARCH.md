# Phase 10: Foundation - Research

**Researched:** 2026-03-31
**Domain:** Schedule Service bootstrap — JPA entities, security infrastructure, timezone configuration, gRPC port, Testcontainers base class
**Confidence:** HIGH (all findings verified against codebase — existing Academic Service patterns, V1 migration SQL, schedule-app scaffold)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LSSN-03 | Lesson generation is idempotent (retry-safe via UNIQUE constraint) | `UNIQUE (schedule_item_id, date)` already in V1 migration; Testcontainers base class enables real PostgreSQL constraint verification; `ON CONFLICT DO NOTHING` pattern documented |
| CRON-04 | Cron runs every minute with proper timezone handling | `TZ=Europe/Moscow` in docker-compose, `hibernate.jdbc.time_zone=Europe/Moscow` in application.yml, Clock bean injection, `@Scheduled(cron="0 * * * * *", zone="Europe/Moscow")` pattern documented |
</phase_requirements>

---

## Summary

Phase 10 establishes the structural skeleton of Schedule Service before any domain logic is written. The Academic Service (shipped in v2.0) already provides the exact patterns to follow: Testcontainers abstract base class, security filter/aspect/context chain, JPA entity conventions, and enum converters. The schedule-app scaffold already exists with Spring Boot, JPA, Flyway, and RabbitMQ wired — the V1 migration is already applied to schedule_db.

The two requirements assigned to this phase (LSSN-03, CRON-04) are both infrastructure concerns: the UNIQUE constraint that makes lesson generation idempotent is already in V1__baseline.sql, and the timezone configuration must be established now before any time-comparison logic is written in later phases.

The key task is: copy the four security classes from `academic-app/security/` to `schedule-app/security/` (adjusting the package and UserRole import), add `UserRole` enum to `schedule-api-contract`, add missing build dependencies (AOP, Testcontainers, gRPC starters), update `application.yml` with gRPC port and timezone, add `TZ=Europe/Moscow` to docker-compose, wire a `Clock` bean, write the abstract Testcontainers base class, and write two integration tests (entity schema validation + security 403 smoke test).

**Primary recommendation:** Follow Academic Service patterns exactly. Every structural decision for this phase already has a proven precedent in the same codebase.

---

## Project Constraints (from CLAUDE.md)

### Mandatory Rules

- Contract-first: `schedule-api-contract` is a pure `java-library`; controllers `implements` the contract interface; `@RequestMapping` ONLY in the interface
- Request DTOs = Java `record`; Response DTOs = class extending `RepresentationModel`
- NO Lombok in `*-api-contract` modules; Lombok permitted in `*-app` (entities, internal classes)
- Enums: Java `UPPER_CASE`, PostgreSQL `lowercase` strings; conversion via `LowercaseEnumConverter` with `autoApply=true`; NEVER `@Enumerated(EnumType.ORDINAL)`
- PostgreSQL: all values lowercase; Flyway migrations only; `ddl-auto: validate`; `BIGSERIAL` PKs; `TIMESTAMPTZ` for timestamps (UTC)
- REST: HATEOAS Level 3; RFC 7807 errors; `@ControllerAdvice` for error handling
- Packages: `ru.rutcampustrack.{service}.{module}`
- gRPC: `ru.rutcampustrack.{service}.grpc`
- Soft delete for users (`status = 'archived'`); never `DELETE`
- `@RequireRole` AOP over Spring Security

### Schedule Service Specific (from CONTEXT.md / STATE.md pre-decisions)

- `grpc.server.port: 19092` — mirrors REST port 9092; avoids Auth (9090) and Academic (19091) conflicts
- `TZ=Europe/Moscow` in docker-compose.yml + `spring.jpa.properties.hibernate.jdbc.time_zone=Europe/Moscow` + injected `Clock` bean
- `@Profile("!test")` guard on `SchedulingConfig` — consistent with `@ActiveProfiles("test")` in abstract test base
- `@MockitoBean RabbitTemplate` in non-event test bases (prevents `DomainEventListener` breaking tests without RabbitMQ)
- gRPC client deadline: always `.withDeadlineAfter(3s)` on Academic gRPC calls (Phase 11 concern, but client bean wired here)

---

## Standard Stack

### Already in `schedule-app/build.gradle.kts` (do NOT re-add)

| Library | Purpose | Status |
|---------|---------|--------|
| `spring-boot-starter-web` | REST controllers | Present |
| `spring-boot-starter-data-jpa` | JPA / Hibernate | Present |
| `spring-boot-starter-validation` | `@Valid` on request DTOs | Present |
| `spring-boot-starter-hateoas` | HATEOAS `EntityModel`, `_links` | Present |
| `spring-boot-starter-amqp` | RabbitMQ (Phase 13) | Present |
| `springdoc-openapi-starter-webmvc-ui:2.7.0` | Swagger UI | Present |
| `postgresql` (runtimeOnly) | PostgreSQL JDBC driver | Present |
| `flyway-core` + `flyway-database-postgresql` | Migrations | Present |
| `lombok` (compileOnly + annotationProcessor) | Entity boilerplate | Present |
| `spring-boot-starter-test` + `junit-platform-launcher` | Test framework | Present |

### NEW Additions Required for Phase 10

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| `spring-boot-starter-aop` | Spring Boot 3.4 BOM | `@RequireRole` AOP aspect (`RoleCheckAspect`) | Same dependency needed by Academic Service for `@Around` advice |
| `testcontainers-bom:1.20.4` | 1.20.4 (dependencyManagement) | BOM for Testcontainers modules | Same version proven in `academic-app/build.gradle.kts` |
| `spring-boot-testcontainers` | 3.4 BOM | `@Testcontainers` + `@ServiceConnection` support | Required for Testcontainers integration with Spring Boot Test |
| `testcontainers:junit-jupiter` | via BOM | JUnit 5 container lifecycle | Required for `@Testcontainers` annotation |
| `testcontainers:postgresql` | via BOM | Real PostgreSQL 16 container | H2 cannot emulate `week_type` / `lesson_status` custom PostgreSQL ENUMs |
| `net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE` | 3.1.0.RELEASE | gRPC server (Phase 14) | Port config needed now; same version as Academic Service |
| `net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE` | 3.1.0.RELEASE | gRPC client to Academic Service (Phase 11) | Client config wired now; same version as server starter |
| `javax.annotation:javax.annotation-api:1.3.2` | 1.3.2 | `@Generated` on proto stubs (Java 9+ removal) | Required once proto plugin generates stubs |
| `com.google.protobuf` plugin | 0.9.4 | Generate Java stubs from `proto/` | Same version as Academic Service — must match |

Note: gRPC starters and proto plugin are needed even in Phase 10 because `application.yml` references `grpc.server.port` and the build must compile with the gRPC plugin configured (even if no proto-generated files are referenced by Phase 10 code).

**Phase 10 build.gradle.kts additions:**
```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf") version "0.9.4"   // NEW
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")   // NEW
    }
}

// In dependencies:
implementation("org.springframework.boot:spring-boot-starter-aop")              // NEW
implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")        // NEW
implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")        // NEW
compileOnly("javax.annotation:javax.annotation-api:1.3.2")                       // NEW
testImplementation("org.springframework.boot:spring-boot-testcontainers")        // NEW
testImplementation("org.testcontainers:junit-jupiter")                           // NEW
testImplementation("org.testcontainers:postgresql")                              // NEW
testImplementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")    // NEW (test scope for in-process gRPC)

// Also add protobuf source sets + generateProtoTasks block (same as academic-app)
```

### `schedule-api-contract` additions

The security layer (`@RequireRole` annotation) references `UserRole`. Academic Service defined `UserRole` in its own contract module. Schedule Service must also have `UserRole` in its contract module (or import from academic contract — but that creates cross-service coupling). Correct approach: define `UserRole` in `schedule-api-contract/enums/` (same values: `ADMIN`, `TEACHER`, `STUDENT`).

Additionally, `schedule-api-contract/build.gradle.kts` needs `spring-data-commons` if `Pageable` is used in Phase 11 APIs. No change needed for Phase 10 — existing deps are sufficient for the enums alone.

---

## Architecture Patterns

### Recommended Project Structure (Phase 10 scope)

```
services/schedule-service/
├── schedule-api-contract/
│   └── src/main/java/ru/rutcampustrack/schedule/contract/
│       └── enums/
│           ├── LessonStatus.java     (already exists)
│           ├── WeekType.java         (already exists)
│           └── UserRole.java         (NEW — ADMIN, TEACHER, STUDENT)
│
└── schedule-app/
    └── src/main/java/ru/rutcampustrack/schedule/
        ├── ScheduleApplication.java  (already exists)
        ├── config/
        │   ├── EnumConverters.java   (already exists — WeekType, LessonStatus)
        │   └── ClockConfig.java      (NEW — @Bean Clock.system(ZoneId.of("Europe/Moscow")))
        ├── security/
        │   ├── RequestContext.java   (NEW — request-scoped, ScopedProxyMode.TARGET_CLASS)
        │   ├── RequireRole.java      (NEW — annotation, references schedule contract UserRole)
        │   ├── RoleCheckAspect.java  (NEW — @Around("@annotation(requireRole)"))
        │   └── UserContextFilter.java (NEW — reads X-User-* headers)
        ├── item/
        │   └── entity/ScheduleItem.java  (NEW — @Entity for schedule_items table)
        ├── lesson/
        │   └── entity/Lesson.java        (NEW — @Entity for lessons table)
        └── exception/
            ├── AccessDeniedException.java   (NEW)
            └── GlobalExceptionHandler.java  (NEW — maps AccessDeniedException → 403)
    └── src/test/java/ru/rutcampustrack/schedule/integration/
        ├── AbstractScheduleIntegrationTest.java  (NEW — Testcontainers base class)
        ├── EntityMappingIntegrationTest.java     (NEW — Hibernate validate smoke test)
        └── SecuritySmokeTest.java                (NEW — 403 without X-User-* headers)
    └── src/test/resources/
        └── application-test.yml  (NEW — test overrides: in-memory gRPC, disable RabbitMQ auto-config)
```

### Pattern 1: JPA Entity Mapping — `ScheduleItem`

Maps `schedule_items` table. Key points:
- `TIME` columns (`start_time`, `end_time`) → `LocalTime` in Java
- `week_type` PostgreSQL custom enum → `WeekType` Java enum → handled by `EnumConverters.WeekTypeConverter` (already exists, `autoApply=true`)
- `TIMESTAMPTZ` (`created_at`) → `OffsetDateTime`
- No `@ManyToOne` associations — FK columns stored as plain `Long` (project-wide decision)
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` for BIGSERIAL PK

```java
// Source: V1__baseline.sql + Academic Service entity patterns
@Entity
@Table(name = "schedule_items")
@Getter
@NoArgsConstructor
public class ScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Setter @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Setter @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Setter @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    @Setter @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;          // 0-5 per CHECK constraint

    @Setter @Column(name = "lesson_number", nullable = false)
    private Short lessonNumber;       // 1-8 per CHECK constraint

    @Setter @Column(name = "start_time", nullable = false)
    private LocalTime startTime;      // TIME -> LocalTime (no timezone)

    @Setter @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Setter @Column(name = "week_type", nullable = false)
    private WeekType weekType;        // autoApply converter: lowercase string

    @Setter @Column(length = 64)
    private String room;

    @Setter @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Setter @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
```

### Pattern 2: JPA Entity Mapping — `Lesson`

Maps `lessons` table. Key points:
- `lesson_status` PostgreSQL enum → `LessonStatus` Java enum → `EnumConverters.LessonStatusConverter` (already exists)
- FK to `schedule_items.id` stored as plain `Long scheduleItemId` (no `@ManyToOne`)
- `DATE` column → `LocalDate`
- `closed_at` nullable `TIMESTAMPTZ` → `OffsetDateTime` (nullable)
- UNIQUE constraint `(schedule_item_id, date)` — the idempotency anchor for LSSN-03

```java
// Source: V1__baseline.sql
@Entity
@Table(name = "lessons")
@Getter
@NoArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter @Column(name = "schedule_item_id", nullable = false)
    private Long scheduleItemId;

    @Setter @Column(nullable = false)
    private LocalDate date;

    @Setter @Column(nullable = false)
    private LessonStatus status = LessonStatus.PLANNED;

    @Setter @Column(name = "is_geo_blocked", nullable = false)
    private boolean isGeoBlocked = false;

    @Setter @Column(name = "cancel_reason", length = 512)
    private String cancelReason;

    @Setter @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Setter @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
```

### Pattern 3: Security Infrastructure

Verbatim copy of Academic Service security classes with package changed from `ru.rutcampustrack.academic` to `ru.rutcampustrack.schedule` and `UserRole` import changed to `ru.rutcampustrack.schedule.contract.enums.UserRole`.

Four files:
1. `RequestContext.java` — `@Scope("request", proxyMode = ScopedProxyMode.TARGET_CLASS)` — mandatory proxy mode
2. `RequireRole.java` — `@Target(METHOD)`, `@Retention(RUNTIME)`, `UserRole[] value()`
3. `RoleCheckAspect.java` — `@Aspect @Component`, `@Around("@annotation(requireRole)")`
4. `UserContextFilter.java` — `extends OncePerRequestFilter`, reads `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`

Critical: `RoleCheckAspect` throws `AccessDeniedException` (not Spring Security's version) which `GlobalExceptionHandler` maps to HTTP 403.

```java
// Source: academic-app/security/RoleCheckAspect.java (pattern to replicate)
@Around("@annotation(requireRole)")
public Object checkRole(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
    UserRole[] required = requireRole.value();
    UserRole actual = requestContext.getRole();
    if (actual == null || !Arrays.asList(required).contains(actual)) {
        throw new AccessDeniedException("Required role: " + Arrays.toString(required));
    }
    return pjp.proceed();
}
```

A request with NO `X-User-Id` header means `UserContextFilter` does NOT set any field on `RequestContext`, so `requestContext.getRole()` returns `null` — the aspect throws `AccessDeniedException` → 403. This is the 403 smoke test scenario.

### Pattern 4: Testcontainers Abstract Base Class

Mirror of `AbstractAcademicIntegrationTest`. For Phase 10, Schedule Service has no Redis — exclude only RabbitMQ autoconfiguration.

```java
// Source: AbstractAcademicIntegrationTest.java (pattern to replicate)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractScheduleIntegrationTest {

    @MockitoBean
    RabbitTemplate rabbitTemplate;  // prevents DomainEventListener breaking tests

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("schedule_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }
}
```

### Pattern 5: `application-test.yml`

```yaml
# Source: academic-app/src/test/resources/application-test.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
logging:
  level:
    ru.rutcampustrack: DEBUG
    org.hibernate.SQL: DEBUG
grpc:
  server:
    port: -1   # disables Netty port binding in tests
```

### Pattern 6: Clock Bean for Timezone Testability

```java
// New: schedule-app/config/ClockConfig.java
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }
}
```

In tests, override with `Clock.fixed(Instant.parse("2026-09-01T05:30:00Z"), ZoneId.of("Europe/Moscow"))` via `@MockitoBean Clock clock`.

### Anti-Patterns to Avoid

- **Importing `UserRole` from academic-api-contract:** Creates cross-service module dependency. Define `UserRole` in `schedule-api-contract`.
- **`@Enumerated(EnumType.ORDINAL)` on any enum field:** Project-wide prohibition; use `autoApply=true` `AttributeConverter` only.
- **`@ManyToOne` associations:** Project-wide decision — FK columns stored as `Long` IDs, prevents N+1 and cascade issues.
- **`@EnableScheduling` on `ScheduleApplication`:** Must be on a separate `SchedulingConfig` class guarded with `@Profile("!test")` so cron jobs don't fire during integration tests.
- **`TimeZone.setDefault("Europe/Moscow")`:** JVM-global mutation breaks test isolation; use `TZ` env var + injected `Clock` bean.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PostgreSQL enum type conversion | Custom switch/map in entity | `EnumConverters.WeekTypeConverter` + `EnumConverters.LessonStatusConverter` (already in schedule-app) | Already exists with `autoApply=true`; adding `@Convert` on each field is redundant and error-prone |
| Test database lifecycle | Embedded H2 / manual JDBC setup | `AbstractScheduleIntegrationTest` Testcontainers base class | H2 cannot emulate `week_type` / `lesson_status` custom PostgreSQL ENUMs; Testcontainers proven in Academic Service |
| Role-based access control | Spring Security filter chain | `@RequireRole` + `RoleCheckAspect` (copy from academic-app) | API Gateway already validates JWT; service only needs role check on headers; full Spring Security is overkill |
| Clock injection for time tests | Static `LocalTime.now()` | `Clock` bean injected into services | `Clock.fixed(...)` in `@MockitoBean` makes time deterministic for cron tests |
| gRPC port management | Hard-coded port in tests | `grpc.server.port=-1` in `application-test.yml` | Prevents port binding; in-process pattern for gRPC tests in Phase 14 |

---

## Common Pitfalls

### Pitfall 1: `LocalTime` Mapping for `TIME` Columns Without Timezone Configuration

**What goes wrong:** `ScheduleItem.start_time` is `TIME` in PostgreSQL. Without `hibernate.jdbc.time_zone=Europe/Moscow`, Hibernate sends/receives `LocalTime` values adjusted to the JVM's default timezone (UTC in Docker). A lesson at `08:30 Moscow` time is stored/read as `08:30 UTC` — no conversion error, but time comparisons in cron logic are wrong.

**Why it happens:** `TIME` columns have no timezone context. Hibernate's JDBC layer applies the `hibernate.jdbc.time_zone` setting when serializing temporal values to JDBC. Without it, JVM default timezone is used.

**How to avoid:** Set `spring.jpa.properties.hibernate.jdbc.time_zone=Europe/Moscow` in `application.yml` AND `TZ=Europe/Moscow` in `docker-compose.yml` for both `schedule-service` and `postgres-schedule`. Both settings together ensure consistency.

**Warning signs:** Lessons go ACTIVE 3 hours after their scheduled time in production. `SELECT NOW()` in PostgreSQL returns UTC while application expects Moscow time.

### Pitfall 2: `week_type` and `lesson_status` PostgreSQL Enums Not Recognized by Hibernate

**What goes wrong:** Hibernate `ddl-auto: validate` fails at startup with `Wrong column type encountered in column [week_type] in table [schedule_items]; found [week_type (Types#OTHER)], but expecting [varchar(255)]`.

**Why it happens:** PostgreSQL custom enum types (`CREATE TYPE week_type AS ENUM (...)`) are reported as `Types#OTHER` by the JDBC driver. Hibernate's schema validator does not know how to compare `varchar` to a custom PostgreSQL enum type.

**How to avoid:** The existing `EnumConverters.WeekTypeConverter` and `EnumConverters.LessonStatusConverter` in `schedule-app/config/EnumConverters.java` with `autoApply=true` handle the Java-to-String conversion. For Hibernate schema validation, the stored type on the Java side is declared as `String` (the converter output), so Hibernate validates against `varchar`. Since V1 migration uses `week_type NOT NULL` (custom type), the validator may still fail.

**Solution verified from Academic Service**: Add PostgreSQL implicit cast for the custom enum type columns (same fix as V5 migration in academic-app). Or map the column with `@Column(columnDefinition = "week_type")` to bypass Hibernate's type check. The schedule-app already has the converters; the question is whether `ddl-auto: validate` tolerates custom PostgreSQL enum types. If not, add a V2 migration that adds implicit casts (same as academic V5 migration pattern).

**Warning signs:** `SchemaManagementException` on application startup. `EntityMappingIntegrationTest` fails with column type mismatch. Fix: check if academic V5 migration approach is needed here too.

### Pitfall 3: `@Scope("request")` Without `proxyMode = ScopedProxyMode.TARGET_CLASS`

**What goes wrong:** `RoleCheckAspect` is a singleton bean that holds a reference to `RequestContext`. Without `proxyMode`, Spring injects the actual request-scoped instance at startup — which is a stale empty instance from the startup context, not the per-request value.

**Why it happens:** Singleton beans capture their dependencies at creation time. Request-scoped beans only have valid state during a servlet request. Without a proxy, the singleton gets the wrong instance.

**How to avoid:** `@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)` on `RequestContext`. This is copied verbatim from Academic Service — the comment in the source code explicitly documents this requirement.

**Warning signs:** All role checks fail (403) even with correct headers. `RequestContext.getRole()` always returns `null`. Tests pass but manual requests fail.

### Pitfall 4: gRPC Server Port Conflict

**What goes wrong:** `grpc.server.port: 9092` (same as REST port) or `grpc.server.port: 19091` (same as Academic Service gRPC port) causes either a port conflict on startup or cross-service confusion.

**How to avoid:** Use `grpc.server.port: 19092`. This mirrors the REST port (9092) with `1` prefix — same convention as Academic Service (REST 9091 → gRPC 19091). Auth Service REST is 9090. No conflict.

**Warning signs:** `Address already in use: 19091` on startup log. gRPC calls from Attendance Service reach Academic Service instead of Schedule Service.

### Pitfall 5: `@EnableScheduling` on Main Class Fires Cron Jobs During Tests

**What goes wrong:** Cron jobs activate/close lessons immediately during integration test setup, changing lesson statuses before assertions run.

**How to avoid:** Put `@EnableScheduling` on a `SchedulingConfig` class annotated `@Profile("!test")`. The existing `@ActiveProfiles("test")` in `AbstractScheduleIntegrationTest` excludes this config automatically.

---

## Code Examples

### Entity Mapping Validation Test

```java
// Source: academic-app EntityMappingIntegrationTest.java (pattern to replicate)
class EntityMappingIntegrationTest extends AbstractScheduleIntegrationTest {

    @Autowired ScheduleItemRepository scheduleItemRepository;
    @Autowired LessonRepository lessonRepository;

    @Test
    void contextLoads_entitiesValidateAgainstSchema() {
        // If context starts, Hibernate ddl-auto: validate passed for both entities
        assertThat(scheduleItemRepository).isNotNull();
        assertThat(lessonRepository).isNotNull();
    }
}
```

### Security 403 Smoke Test

```java
// Source: academic-app RestApiIntegrationTest pattern
@AutoConfigureMockMvc
class SecuritySmokeTest extends AbstractScheduleIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void request_withoutRoleHeaders_returns403() throws Exception {
        mockMvc.perform(get("/schedule/items"))  // any protected endpoint
               .andExpect(status().isForbidden());
    }
}
```

This test requires a minimal endpoint to exist. Either add a placeholder `GET /schedule/items` endpoint that returns empty list (with `@RequireRole({UserRole.STUDENT, UserRole.ADMIN, UserRole.TEACHER})`), or use MockMvc to call any controller method. The test verifies the filter + aspect chain is wired correctly.

### `application.yml` Additions (Phase 10)

```yaml
# Add to existing application.yml:
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: Europe/Moscow    # CRON-04: TIME columns use Moscow timezone

grpc:
  server:
    port: 19092                       # Internal gRPC server (not through Gateway)
  client:
    academic-service:
      address: 'static://academic-service:19091'
      negotiation-type: plaintext
```

### `docker-compose.yml` Addition (Phase 10)

```yaml
# Add to schedule-service container:
schedule-service:
  environment:
    TZ: Europe/Moscow                 # CRON-04: JVM and OS timezone alignment
```

---

## Environment Availability

| Dependency | Required By | Available | Notes |
|------------|------------|-----------|-------|
| Gradle wrapper (`gradlew.bat`) | Build | Yes | `/rutcampustrack/gradlew.bat` present |
| Java 21 (`C:\Users\maksd\.jdks\ms-21.0.9`) | Build | Yes (per CLAUDE.md) | Set via `$env:JAVA_HOME` before build |
| Docker / Docker Compose | Integration tests (Testcontainers) | Yes (per existing Academic Service tests passing) | `docker-compose.yml` present; PostgreSQL containers already defined |
| PostgreSQL 16 (via Testcontainers) | `AbstractScheduleIntegrationTest` | Yes | Testcontainers pulls `postgres:16` image; same image used in Academic Service tests |
| `postgres-schedule` Docker container | Manual dev run | Defined in `docker-compose.yml` | `schedule_db` already exists with V1 migration applied |

No blocking dependencies.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `spring-boot-starter-test`) |
| Config file | None — uses JUnit Platform via Gradle `useJUnitPlatform()` in root `build.gradle.kts` |
| Quick run command | `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest"` |
| Full suite command | `./gradlew :services:schedule-service:schedule-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LSSN-03 | UNIQUE constraint `(schedule_item_id, date)` is present — Hibernate validates schema | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest"` | Wave 0 |
| CRON-04 | `grpc.server.port: 19092` is in `application.yml` and app starts — verified by context load test | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest"` | Wave 0 |
| SC-5 (security smoke) | Request without `X-User-*` headers returns 403 | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.SecuritySmokeTest"` | Wave 0 |

### Sampling Rate

- Per task commit: `./gradlew :services:schedule-service:schedule-app:test --tests "*.EntityMappingIntegrationTest" --tests "*.SecuritySmokeTest"`
- Per wave merge: `./gradlew :services:schedule-service:schedule-app:test`
- Phase gate: Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/.../integration/AbstractScheduleIntegrationTest.java` — shared base class (covers both requirements)
- [ ] `src/test/java/.../integration/EntityMappingIntegrationTest.java` — Hibernate schema validation (LSSN-03, CRON-04)
- [ ] `src/test/java/.../integration/SecuritySmokeTest.java` — 403 without headers
- [ ] `src/test/resources/application-test.yml` — test overrides (gRPC port -1, disable RabbitMQ auto-config)
- [ ] Placeholder REST endpoint for security smoke test (at minimum `GET /schedule/items` returning empty list)

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| H2 in-memory for tests | Testcontainers PostgreSQL 16 | v1.0 (this project) | Custom PostgreSQL ENUMs (`week_type`, `lesson_status`) are unsupported in H2; Testcontainers is the standard |
| Spring Security for role checks | `@RequireRole` AOP + header-injected context | v2.0 (this project) | API Gateway validates JWT; services only need header-based role check; removes Spring Security complexity |
| `TimeZone.setDefault()` | `TZ` env var + injected `Clock` bean | Always recommended | JVM-global mutation breaks parallel tests; `Clock.fixed()` injection is the standard testability pattern |
| `net.devh:grpc-*:2.x` starters | `net.devh:grpc-*:3.1.0.RELEASE` | Spring Boot 3.x migration | v3.x required for Spring Boot 3+ compatibility |

**Deprecated/outdated:**

- `spring-grpc` (Spring official gRPC starter): Requires Spring Boot 4. Not applicable — project is on Spring Boot 3.4.1. Use `net.devh` starters.
- `@Enumerated(EnumType.ORDINAL)`: Forbidden project-wide — fragile to enum reordering.

---

## Open Questions

1. **Does `ddl-auto: validate` accept PostgreSQL custom enum columns without additional migration?**
   - What we know: Academic Service solved this with a V5 migration adding implicit casts (`CREATE CAST (varchar AS week_type) WITH INOUT AS IMPLICIT`). V1__baseline.sql for schedule-service uses `week_type` and `lesson_status` custom types.
   - What's unclear: Whether Testcontainers will apply V1 migration cleanly and Hibernate validate will pass, or whether an additional V2 migration is needed.
   - Recommendation: The `EntityMappingIntegrationTest` will immediately surface this. If Hibernate validation fails, add a V2 migration mirroring academic-app's V5 implicit cast approach. Plan for this possibility — create V2 migration if the test fails.

2. **Should `UserRole` be duplicated in `schedule-api-contract` or imported from `academic-api-contract`?**
   - What we know: Cross-service contract module dependency violates microservice isolation principles. Academic Service defines its own `UserRole` in its own contract module.
   - What's unclear: Whether there is a shared `common-api-contract` module that could hold cross-cutting types like `UserRole`.
   - Recommendation: Define `UserRole` in `schedule-api-contract`. Duplication is correct for microservice isolation. `settings.gradle.kts` shows no shared common contract module exists.

---

## Sources

### Primary (HIGH confidence)

- `services/schedule-service/schedule-app/src/main/resources/db/migration/V1__baseline.sql` — confirmed schema: `schedule_items` (TIME, week_type custom enum), `lessons` (lesson_status custom enum, UNIQUE constraint)
- `services/schedule-service/schedule-app/build.gradle.kts` — confirmed current dependencies (base without gRPC, Testcontainers, AOP)
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — confirmed current config (no gRPC section, no timezone)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/` — RequestContext, RequireRole, RoleCheckAspect, UserContextFilter (all 4 source files read)
- `services/academic-service/academic-app/src/test/java/.../AbstractAcademicIntegrationTest.java` — Testcontainers base class pattern
- `services/academic-service/academic-app/src/test/resources/application-test.yml` — test overrides pattern
- `services/academic-service/academic-app/build.gradle.kts` — gRPC starter versions (3.1.0.RELEASE), Testcontainers BOM (1.20.4), protobuf plugin (0.9.4)
- `services/academic-service/academic-app/src/main/resources/application.yml` — confirmed `grpc.server.port: 19091` for Academic Service
- `.planning/STATE.md` — pre-decisions: `grpc.server.port: 19092`, `TZ=Europe/Moscow`, `@Profile("!test")` guard
- `.planning/research/STACK.md` — full dependency analysis for v3.0 Schedule Service
- `.planning/research/ARCHITECTURE.md` — component boundaries, security pattern, data flows
- `.planning/research/PITFALLS.md` — timezone mismatch, duplicate rows, scheduling test isolation

### Secondary (MEDIUM confidence)

- `CLAUDE.md` project rules — no Lombok in contracts, `ddl-auto: validate`, BIGSERIAL PKs, TIMESTAMPTZ, HATEOAS, RFC 7807

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all versions verified against working `academic-app/build.gradle.kts` in the same repo
- Architecture: HIGH — direct inspection of all source files; patterns are copy-with-package-change
- Pitfalls: HIGH — sourced from `.planning/research/PITFALLS.md` and `STATE.md` which document lessons learned from Academic Service

**Research date:** 2026-03-31
**Valid until:** 2026-05-01 (stable dependencies; Spring Boot 3.4 + Testcontainers BOM; no fast-moving components in this phase)
