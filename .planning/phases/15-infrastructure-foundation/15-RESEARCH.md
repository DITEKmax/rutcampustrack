# Phase 15: Infrastructure Foundation - Research

**Researched:** 2026-04-04
**Domain:** Spring Boot 3.4 + MongoDB + gRPC client + RabbitMQ consumer + AOP security
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Full denormalization — each attendance document stores `lesson_id`, `user_id`, `group_id`, `subject_id`, `semester_id`, `lesson_number`, `date`, `status`, `source`, `marked_by`, `created_at`, `updated_at`. Report queries are pure MongoDB aggregations with no gRPC calls at read time.
- **D-02:** `semester_id` resolved via cached GetActiveSemester gRPC call on service startup. Cache held in a @Service bean, refreshed on `semester.archived` event. Acceptable because semester changes are rare (twice a year).
- **D-03:** Single `attendances` collection for all attendance records. Compound indexes handle all query patterns (by lesson, by student+semester, by group+subject+semester).
- **D-04:** Default MongoDB ObjectId as `_id`. Unique constraint on `{lesson_id, user_id}` as a separate compound unique index.
- **D-05:** Declare DLQ infrastructure in Phase 15 (DLQ exchange + queue + binding). Phase 16 adds the error handler logic. Clean separation: infra in 15, logic in 16.
- **D-06:** Generic envelope deserialization — single `@RabbitListener` receives all events as a generic envelope (Map or JsonNode), routes by `event_type` field to typed handler methods. Matches the fanout pattern where one queue receives everything.
- **D-07:** Wire ALL gRPC RPCs needed for v4.0 in Phase 15: `GetActiveLesson`, `GetLessonById`, `GetLessonsByGroup` (Schedule) + `GetGroupMembers`, `GetCampusGeofence`, `GetActiveSemester`, `IsHeadman` (Academic). Each is a one-liner wrapper following the pattern in schedule-service's `AcademicGrpcClient`.
- **D-08:** No `attendance.proto` for v4.0. Attendance Service is a gRPC consumer only, not a provider.
- **D-09:** Mock gRPC blocking stubs with Mockito for unit tests. Real gRPC integration tests deferred to E2E.
- **D-10:** Testcontainers for both MongoDB and RabbitMQ. MongoDB for index verification (success criteria 1-2), RabbitMQ for consumer queue binding verification (criteria 4). Redis not needed until Phase 17.
- **D-11:** `@ActiveProfiles("test")` + abstract base test class with `@Testcontainers` and `@DynamicPropertySource` for MongoDB + RabbitMQ URLs.
- **D-12:** Copy + extend GlobalExceptionHandler from academic-service. RFC 7807 pattern with MongoDB-specific handlers: `DuplicateKeyException` -> 409, gRPC `StatusRuntimeException` -> 502/503.
- **D-13:** Test application.yml overrides gRPC channels to localhost. `@Profile("!test")` on any startup beans that require live services.

### Claude's Discretion

- Package structure within attendance-app (config/, grpc/, event/, security/ etc.)
- Specific MongoDB index definitions beyond the unique {lesson_id, user_id} compound index
- RabbitMQ queue naming convention (e.g., `attendance-service.events`)
- DLQ exchange/queue naming
- Order of bean initialization for semester cache

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | System initializes MongoDB indexes programmatically (unique on {lesson_id, user_id}, query indexes for reports) | MongoTemplate + @PostConstruct pattern; IndexOperations API confirmed |
| INFRA-02 | System serializes enums as lowercase strings in MongoDB via MongoCustomConversions | Spring Data MongoDB MongoCustomConversions + custom WritingConverter/ReadingConverter pattern |
| INFRA-03 | gRPC client connects to Schedule Service (GetActiveLesson, GetLessonById, GetLessonsByGroup) | net.devh grpc-client-spring-boot-starter 3.1.0 pattern confirmed from schedule-service |
| INFRA-04 | gRPC client connects to Academic Service (GetGroupMembers, GetCampusGeofence, GetActiveSemester) | Same gRPC client pattern; existing AcademicGrpcClient in schedule-service is the canonical reference |
| INFRA-05 | RabbitMQ consumer declares durable queue bound to rut-uit.events fanout exchange | Spring AMQP Queue + Binding + @RabbitListener pattern confirmed from schedule-service RabbitConfig |
</phase_requirements>

---

## Summary

Phase 15 wires up the Attendance Service infrastructure without any business logic. The project already has mature reference implementations for every concern in this phase: `AcademicGrpcClient` (schedule-service) shows the gRPC wrapper pattern, `RabbitConfig` (schedule-service) shows exchange declaration, and `RequireRole`/`RoleCheckAspect`/`UserContextFilter` (academic-service/schedule-service) show the AOP security pattern. All of these can be copied and adapted — the primary work is adaptation, not invention.

The two genuinely new concerns for this service (compared to prior phases) are MongoDB-specific: (1) programmatic index creation via `MongoTemplate.indexOps()` at startup, and (2) enum serialization via `MongoCustomConversions` instead of the JPA `LowercaseEnumConverter` used in PostgreSQL services. Neither is complex, but both have a specific Spring Data MongoDB API that must be used correctly.

The `build.gradle.kts` for attendance-app is currently missing: `grpc-client-spring-boot-starter`, `grpc-server-spring-boot-starter`, `javax.annotation-api`, the protobuf plugin, Testcontainers BOM, and Testcontainers MongoDB/RabbitMQ modules. These must be added as the first task before any other code can compile.

**Primary recommendation:** Copy schedule-service's build.gradle.kts as the template for gRPC/protobuf setup, then layer in Testcontainers MongoDB + RabbitMQ dependencies. Adapt all security and RabbitMQ config classes directly from existing services with package renames only.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-data-mongodb | managed by Spring Boot 3.4 BOM | MongoDB document access | Already in attendance-app build.gradle.kts |
| spring-boot-starter-amqp | managed by Spring Boot 3.4 BOM | RabbitMQ consumer/producer | Already in attendance-app build.gradle.kts |
| net.devh:grpc-client-spring-boot-starter | 3.1.0.RELEASE | @GrpcClient injection, channel management | Identical to schedule-service |
| net.devh:grpc-server-spring-boot-starter | 3.1.0.RELEASE | Required alongside client starter for full startup | Identical to schedule-service |
| com.google.protobuf:protoc | 3.25.3 | Proto code generation | Identical to schedule-service |
| io.grpc:protoc-gen-grpc-java | 1.63.0 | gRPC Java stubs generation | Identical to schedule-service |
| com.google.protobuf Gradle plugin | 0.9.4 | Wires protoc into Gradle build | Identical to schedule-service |
| javax.annotation:javax.annotation-api | 1.3.2 | Required annotation class for generated gRPC code | Identical to schedule-service |
| spring-boot-starter-aop | managed BOM | AOP for @RequireRole aspect | Must be added; not yet in attendance-app |

### Testing

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| org.testcontainers:testcontainers-bom | 1.20.4 | BOM for consistent Testcontainers versions | Test scope; matches schedule-service |
| org.springframework.boot:spring-boot-testcontainers | managed | @DynamicPropertySource + @ServiceConnection | All integration tests |
| org.testcontainers:junit-jupiter | 1.20.4 | @Testcontainers annotation support | All integration tests |
| org.testcontainers:mongodb | 1.20.4 | MongoDB container for index + serialization tests | INFRA-01, INFRA-02, INFRA-05 |
| org.testcontainers:rabbitmq | 1.20.4 | RabbitMQ container for queue binding test | INFRA-05 |

**Installation additions to attendance-app/build.gradle.kts:**
```kotlin
plugins {
    // ... existing plugins ...
    id("com.google.protobuf") version "0.9.4"
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    // ... existing ...
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:rabbitmq")
}

sourceSets {
    main {
        proto {
            srcDir(rootProject.file("proto"))
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins { create("grpc") { } }
        }
    }
}
```

---

## Architecture Patterns

### Recommended Package Structure

```
attendance-app/src/main/java/ru/rutcampustrack/attendance/
├── config/
│   ├── MongoConfig.java          # MongoCustomConversions + programmatic index creation
│   └── RabbitConfig.java         # FanoutExchange + DLQ exchange + Queue + Binding declarations
├── grpc/
│   ├── ScheduleGrpcClient.java   # GetActiveLesson, GetLessonById, GetLessonsByGroup
│   └── AcademicGrpcClient.java   # GetGroupMembers, GetCampusGeofence, GetActiveSemester, IsHeadman
├── event/
│   └── EventConsumer.java        # @RabbitListener — generic envelope router (D-06)
├── semester/
│   └── SemesterCacheService.java # @Service bean caching active semester_id (D-02)
├── security/
│   ├── RequestContext.java       # request-scoped bean, ScopedProxyMode.TARGET_CLASS
│   ├── RequireRole.java          # annotation
│   ├── RoleCheckAspect.java      # @Aspect @Around
│   └── UserContextFilter.java    # OncePerRequestFilter populating RequestContext
└── exception/
    └── GlobalExceptionHandler.java  # RFC 7807, + DuplicateKeyException, StatusRuntimeException
```

### Pattern 1: MongoDB Index Creation at Startup (INFRA-01)

**What:** Use `MongoTemplate.indexOps()` in a `@PostConstruct` method (or `@EventListener(ApplicationReadyEvent)`) to ensure indexes exist before any write occurs.

**When to use:** Service startup. Spring Data MongoDB does NOT auto-create indexes from `@Document` annotations by default in production mode — must be explicit.

**Example:**
```java
// Source: Spring Data MongoDB docs — IndexOperations API
@Configuration
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    public MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps("attendances");

        // INFRA-01: Unique compound index — idempotency guarantee
        indexOps.ensureIndex(new Index()
                .on("lesson_id", Sort.Direction.ASC)
                .on("user_id", Sort.Direction.ASC)
                .unique());

        // Query index: student attendance per semester (RPRT-03, RPRT-04)
        indexOps.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("semester_id", Sort.Direction.ASC)
                .on("lesson_date", Sort.Direction.DESC));

        // Query index: group journal (RPRT-01, RPRT-02)
        indexOps.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .on("semester_id", Sort.Direction.ASC)
                .on("subject_id", Sort.Direction.ASC));

        // Query index: all records for a lesson (auto-absent, lesson view)
        indexOps.ensureIndex(new Index().on("lesson_id", Sort.Direction.ASC));
    }
}
```

`ensureIndex` is idempotent — safe to call on every startup. Throws `DuplicateKeyException` only when inserting a document that violates the unique constraint, not when calling `ensureIndex` on an existing index.

### Pattern 2: Enum Serialization as Lowercase in MongoDB (INFRA-02)

**What:** Spring Data MongoDB does not use JPA converters. Instead, register `MongoCustomConversions` with a pair of `Converter<Enum, String>` (writing) and `Converter<String, Enum>` (reading) for each enum type.

**When to use:** Any enum field on a MongoDB `@Document` entity.

**Example:**
```java
// Source: Spring Data MongoDB docs — Custom Conversions
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {
    // ... OR inject as a @Bean without extending —

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
            // AttendanceStatus
            (WritingConverter) (AttendanceStatus s) -> s.name().toLowerCase(),
            (ReadingConverter) (String s) -> AttendanceStatus.valueOf(s.toUpperCase()),
            // AttendanceSource
            (WritingConverter) (AttendanceSource s) -> s.name().toLowerCase(),
            (ReadingConverter) (String s) -> AttendanceSource.valueOf(s.toUpperCase())
        ));
    }
}
```

**Critical pitfall:** The lambda approach above requires explicit type witnesses for the compiler. A cleaner alternative is concrete `@WritingConverter` and `@ReadingConverter` annotated inner classes:

```java
@WritingConverter
public static class AttendanceStatusWriter implements Converter<AttendanceStatus, String> {
    @Override public String convert(AttendanceStatus source) { return source.name().toLowerCase(); }
}

@ReadingConverter
public static class AttendanceStatusReader implements Converter<String, AttendanceStatus> {
    @Override public AttendanceStatus convert(String source) { return AttendanceStatus.valueOf(source.toUpperCase()); }
}
```

This is the pattern Spring Data MongoDB documentation recommends. Register all converters in the `MongoCustomConversions` bean.

**Note:** `FREE_ATTENDANCE` serializes to `"free_attendance"` and deserializes back to `FREE_ATTENDANCE.valueOf("FREE_ATTENDANCE")` — the underscore survives round-trip correctly.

### Pattern 3: gRPC Client Wrapper (INFRA-03, INFRA-04)

**What:** `@GrpcClient("service-name")` injects a blocking stub. Wrapper class adds 3-second deadline and translates `StatusRuntimeException` to domain exceptions.

**When to use:** Every outbound gRPC call.

**Example (from existing AcademicGrpcClient in schedule-service — canonical reference):**
```java
@Component
public class ScheduleGrpcClient {

    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    public LessonResponse getActiveLesson(Long groupId, String timestamp) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getActiveLesson(ActiveLessonRequest.newBuilder()
                            .setGroupId(groupId)
                            .setTimestamp(timestamp)
                            .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Lesson", "groupId/timestamp", groupId + "/" + timestamp);
            }
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }
    // ... GetLessonById, GetLessonsByGroup follow same pattern
}
```

The `attendance-app` needs `ScheduleGrpcClient` (wrapping `ScheduleGrpcService`) and a clone of the existing `AcademicGrpcClient` (wrapping `AcademicGrpcService` — GetGroupMembers, GetCampusGeofence, GetActiveSemester, IsHeadman).

**application.yml additions:**
```yaml
grpc:
  server:
    port: -1          # Attendance Service is not a gRPC server in v4.0 (D-08)
  client:
    schedule-service:
      address: static://schedule-service:19092
      negotiation-type: plaintext
    academic-service:
      address: static://academic-service:19091
      negotiation-type: plaintext
```

**application-test.yml overrides:**
```yaml
grpc:
  server:
    port: -1
  client:
    schedule-service:
      address: static://localhost:19092
      negotiation-type: plaintext
    academic-service:
      address: static://localhost:19091
      negotiation-type: plaintext
```

### Pattern 4: RabbitMQ Consumer Queue with DLQ (INFRA-05)

**What:** Declare a durable queue bound to the existing fanout exchange. Declare DLQ infrastructure alongside (D-05).

**When to use:** Any service that consumes from the shared event bus.

**Example (adapted from schedule-service RabbitConfig):**
```java
@Configuration
public class RabbitConfig {

    // Existing fanout exchange (declared idempotently by every service)
    @Bean
    public FanoutExchange attendanceEventsExchange() {
        return new FanoutExchange("rut-uit.events", true, false);
    }

    // Dead-letter exchange (direct, for DLQ routing)
    @Bean
    public DirectExchange attendanceDlqExchange() {
        return new DirectExchange("rut-uit.events.dlq", true, false);
    }

    // Main consumer queue — durable, bound to fanout
    @Bean
    public Queue attendanceEventsQueue() {
        return QueueBuilder.durable("attendance-service.events")
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", "attendance-service.events.dlq")
                .build();
    }

    // DLQ queue — durable
    @Bean
    public Queue attendanceDlqQueue() {
        return QueueBuilder.durable("attendance-service.events.dlq").build();
    }

    // Binding: fanout exchange -> main queue
    @Bean
    public Binding attendanceEventsBinding(Queue attendanceEventsQueue,
                                            FanoutExchange attendanceEventsExchange) {
        return BindingBuilder.bind(attendanceEventsQueue).to(attendanceEventsExchange);
    }

    // Binding: DLQ exchange -> DLQ queue
    @Bean
    public Binding attendanceDlqBinding(Queue attendanceDlqQueue,
                                         DirectExchange attendanceDlqExchange) {
        return BindingBuilder.bind(attendanceDlqQueue)
                .to(attendanceDlqExchange)
                .with("attendance-service.events.dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
```

**EventConsumer (D-06 — generic envelope routing):**
```java
@Component
public class EventConsumer {

    @RabbitListener(queues = "attendance-service.events")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) return;
        switch (eventType) {
            case "lesson.started"   -> handleLessonStarted(envelope);
            case "lesson.closed"    -> handleLessonClosed(envelope);
            case "lesson.cancelled" -> handleLessonCancelled(envelope);
            // Phase 17+ events added here
        }
    }

    private void handleLessonStarted(Map<String, Object> envelope) {
        // Phase 16 fills this in
    }
    // ... stubs for closed, cancelled
}
```

### Pattern 5: AOP Security (INFRA-05 success criterion 5)

**What:** Copy `RequireRole`, `RoleCheckAspect`, `RequestContext`, `UserContextFilter` from academic-service verbatim, changing only the package name to `ru.rutcampustrack.attendance.security` and the enum import to `ru.rutcampustrack.attendance.contract.enums.UserRole`.

**Note:** `UserRole` enum is NOT in `attendance-api-contract` yet (only AttendanceStatus, AttendanceSource, ExcuseType, ExcuseTicketStatus exist there). The contract module must have `UserRole` added, OR the security layer can import it from `academic-api-contract` (which is already a dependency of the contract module chain). The cleanest approach matching other services is to define `UserRole` in `attendance-api-contract` — but since it already exists in `academic-api-contract`, it can be imported directly. **Verify which approach the planner selects.**

**RequestContext critical note:**
```java
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)  // MANDATORY
public class RequestContext { ... }
```
Without `ScopedProxyMode.TARGET_CLASS`, singleton beans (RoleCheckAspect) get a stale instance at startup.

### Pattern 6: Semester Cache Service (D-02)

**What:** A `@Service` bean that holds the active `semester_id` in memory. Populated at startup via `GetActiveSemester` gRPC, refreshed on `semester.archived` RabbitMQ event.

```java
@Service
public class SemesterCacheService {

    private volatile Long activeSemesterId;
    private final AcademicGrpcClient academicGrpcClient;

    // @Profile("!test") prevents gRPC call during tests
    @Profile("!test")
    @PostConstruct
    public void loadActiveSemester() {
        SemesterResponse response = academicGrpcClient.getActiveSemester();
        this.activeSemesterId = response.getId();
    }

    public Long getActiveSemesterId() {
        return activeSemesterId;
    }

    public void refresh() {
        SemesterResponse response = academicGrpcClient.getActiveSemester();
        this.activeSemesterId = response.getId();
    }
}
```

The `semester.archived` event trigger (calling `refresh()`) is wired in `EventConsumer`. In tests, `SemesterCacheService` has a null `activeSemesterId` until explicitly set — tests that need it must use `@MockitoBean SemesterCacheService`.

### Anti-Patterns to Avoid

- **Auto-index creation via `spring.data.mongodb.auto-index-creation=true`:** Works in dev but is disabled by default in Spring Boot production mode. Do not rely on it — always use programmatic `ensureIndex`.
- **`@Enumerated(EnumType.ORDINAL)` or `@Enumerated(EnumType.STRING)` annotations on MongoDB entities:** These are JPA annotations and have no effect on MongoDB. Failing to register converters means enums are stored as `{"_class":"...","name":"PRESENT"}` or similar. Use `MongoCustomConversions`.
- **`channelTransacted=true` on RabbitTemplate:** Causes message loss with AFTER_COMMIT semantics (documented in schedule-service RabbitConfig). Do not add it.
- **New ObjectMapper in RabbitConfig:** Must inject the Spring-managed ObjectMapper (already has JavaTimeModule). Creating a new `ObjectMapper()` loses JavaTimeModule and adds `@class` type fields to messages.
- **Outbound gRPC connection on test startup:** Without `@MockitoBean ScheduleGrpcClient` and `@MockitoBean AcademicGrpcClient` in the abstract test base, Spring context fails with `UNAVAILABLE` when trying to connect to ports 19092/19091.
- **`grpc.server.port` not set to -1 in tests:** Without this, Netty tries to bind a gRPC server port during tests, causing port conflicts.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MongoDB index management | Custom init script | `MongoTemplate.indexOps().ensureIndex()` | Idempotent, Spring-managed, test-friendly |
| Enum↔String conversion for MongoDB | Custom `@PrePersist` / reflection | `MongoCustomConversions` with `@WritingConverter`/`@ReadingConverter` | Framework-supported, works with Spring Data query methods |
| gRPC channel lifecycle | Manual channel creation/shutdown | `@GrpcClient` from `net.devh:grpc-client-spring-boot-starter` | Handles connection pooling, reconnect, interceptors |
| RabbitMQ queue/exchange topology | Manual AMQP API | Spring AMQP `Queue`, `FanoutExchange`, `Binding` beans | Declarative, re-declared idempotently on each startup |
| Request context propagation | ThreadLocal manual | Request-scoped bean with `ScopedProxyMode.TARGET_CLASS` | Framework-managed lifecycle, test-safe |

**Key insight:** All infrastructure patterns in this phase exist verbatim in academic-service and schedule-service. This phase is copy-adapt-test, not design-build-test.

---

## Common Pitfalls

### Pitfall 1: Enum Stored as Object Instead of String

**What goes wrong:** MongoDB stores `{"status": {"_class": "...AttendanceStatus", "name": "PRESENT"}}` or just `"PRESENT"` (uppercase) instead of `"present"`.

**Why it happens:** `MongoCustomConversions` not registered, or registered with converters for the wrong type (e.g., converter for `Object` instead of specific enum type).

**How to avoid:** Register explicit `Converter<AttendanceStatus, String>` and `Converter<String, AttendanceStatus>` (and similarly for `AttendanceSource`) in the `MongoCustomConversions` bean. Verify with the INFRA-02 test: write a document, read back the raw MongoDB document and assert the field is a lowercase string.

**Warning signs:** Integration test reads enum back as null, or document inspection shows `{}` object instead of a string.

### Pitfall 2: gRPC Client Causes Test Context Failure

**What goes wrong:** `@SpringBootTest` fails with `io.grpc.StatusRuntimeException: UNAVAILABLE: io exception` because the gRPC client attempts to connect to `static://academic-service:19091` at context startup.

**Why it happens:** `@GrpcClient` stubs are eagerly initialized. Without a running Academic Service or mock, the connection fails.

**How to avoid:** In `AbstractAttendanceIntegrationTest`, add `@MockitoBean ScheduleGrpcClient` and `@MockitoBean AcademicGrpcClient`. Also set `grpc.server.port=-1` in `application-test.yml` to prevent Netty server binding.

**Warning signs:** All tests fail at context load with UNAVAILABLE, not at test assertion time.

### Pitfall 3: Unique Index Violation vs. `ensureIndex` Failure

**What goes wrong:** Developer conflates "calling `ensureIndex` throws DuplicateKeyException" with "inserting a duplicate document throws DuplicateKeyException."

**Why it happens:** Misreading the API.

**How to avoid:** `ensureIndex` is idempotent — it creates the index if absent, does nothing if present, never throws on duplicate index creation. `DuplicateKeyException` is thrown only when a write operation violates a unique index. The INFRA-01 test inserts two identical documents and expects the second to throw.

### Pitfall 4: `MongoAutoConfiguration` Not Excluded in Tests (If Using In-Memory)

**What goes wrong:** If a test does NOT use Testcontainers MongoDB, it tries to connect to `mongodb://mongo-attendance:27017` and fails.

**Why it happens:** The MongoDB URI in `application.yml` points to the Docker container hostname.

**How to avoid:** `AbstractAttendanceIntegrationTest` MUST use Testcontainers MongoDB and `@DynamicPropertySource` to override `spring.data.mongodb.uri`. This is the pattern from D-11 and matches schedule-service's PostgreSQL override.

### Pitfall 5: `UserRole` Enum Not Available in attendance-api-contract

**What goes wrong:** `RequireRole` annotation references `UserRole`, but `UserRole` is defined in `academic-api-contract`, not `attendance-api-contract`.

**Why it happens:** The attendance contract module currently only has `AttendanceStatus`, `AttendanceSource`, `ExcuseType`, `ExcuseTicketStatus`.

**How to avoid:** Two options — (A) define `UserRole` in `attendance-api-contract` (cleanest, self-contained), or (B) add `academic-api-contract` as a dependency of `attendance-api-contract` (creates cross-service contract coupling). Option A is recommended and matches how schedule-service handles this (it has its own `UserRole` in `schedule-api-contract`).

---

## Code Examples

### INFRA-01: Programmatic Index Initialization
```java
// MongoConfig.java — PostConstruct creates all 5 indexes defined in database-schema.md
@PostConstruct
public void initIndexes() {
    IndexOperations ops = mongoTemplate.indexOps("attendances");

    ops.ensureIndex(new Index()
            .on("lesson_id", Sort.Direction.ASC)
            .on("user_id", Sort.Direction.ASC)
            .unique()
            .named("uniq_lesson_user"));

    ops.ensureIndex(new Index()
            .on("user_id", Sort.Direction.ASC)
            .on("semester_id", Sort.Direction.ASC)
            .on("lesson_date", Sort.Direction.DESC)
            .named("idx_user_semester_date"));

    ops.ensureIndex(new Index()
            .on("group_id", Sort.Direction.ASC)
            .on("semester_id", Sort.Direction.ASC)
            .on("subject_id", Sort.Direction.ASC)
            .named("idx_group_semester_subject"));

    ops.ensureIndex(new Index()
            .on("lesson_id", Sort.Direction.ASC)
            .named("idx_lesson_id"));
}
```

### INFRA-02: MongoCustomConversions for Enum Serialization
```java
// In MongoConfig or a dedicated @Configuration class
@Bean
public MongoCustomConversions mongoCustomConversions() {
    return new MongoCustomConversions(List.of(
        new AttendanceStatusWriter(),
        new AttendanceStatusReader(),
        new AttendanceSourceWriter(),
        new AttendanceSourceReader()
    ));
}

@WritingConverter
static class AttendanceStatusWriter implements Converter<AttendanceStatus, String> {
    public String convert(AttendanceStatus s) { return s.name().toLowerCase(); }
}

@ReadingConverter
static class AttendanceStatusReader implements Converter<String, AttendanceStatus> {
    public AttendanceStatus convert(String s) { return AttendanceStatus.valueOf(s.toUpperCase()); }
}

@WritingConverter
static class AttendanceSourceWriter implements Converter<AttendanceSource, String> {
    public String convert(AttendanceSource s) { return s.name().toLowerCase(); }
}

@ReadingConverter
static class AttendanceSourceReader implements Converter<String, AttendanceSource> {
    public AttendanceSource convert(String s) { return AttendanceSource.valueOf(s.toUpperCase()); }
}
```

### INFRA-05: Abstract Test Base
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractAttendanceIntegrationTest {

    @MockitoBean
    protected ScheduleGrpcClient scheduleGrpcClient;

    @MockitoBean
    protected AcademicGrpcClient academicGrpcClient;

    @MockitoBean
    protected SemesterCacheService semesterCacheService;

    static final MongoDBContainer MONGODB;
    static final RabbitMQContainer RABBITMQ;

    static {
        MONGODB = new MongoDBContainer("mongo:7.0");
        MONGODB.start();
        RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management");
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
    }
}
```

**Note:** `MongoDBContainer` from `org.testcontainers:mongodb` (image: `mongo:7.0`). Use `getReplicaSetUrl()` for the URI, not `getConnectionString()` — the former returns a proper `mongodb://` URI.

### Security Test Pattern (success criterion 5)
```java
// AttendanceSecuritySmokeTest extends AbstractAttendanceIntegrationTest
@Test
void request_withTeacherRole_returns403_onStudentEndpoint() throws Exception {
    mockMvc.perform(get("/attendance/health-check")
                    .header("X-User-Id", "1")
                    .header("X-User-Role", "TEACHER"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status", is(403)));
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@Document` + `spring.data.mongodb.auto-index-creation=true` | Programmatic `ensureIndex` at startup | Spring Boot 3.x production defaults | Must be explicit; auto-creation disabled by default |
| JPA `@Converter(autoApply=true)` for enums | `MongoCustomConversions` with `@WritingConverter`/`@ReadingConverter` | MongoDB has always needed this — JPA converters only work with JPA | Wrong tool if applied to MongoDB |
| `net.devh:grpc-spring-boot-starter` (legacy combined) | Separate `grpc-client-spring-boot-starter` + `grpc-server-spring-boot-starter` | net.devh v3.x split the artifact | Use explicit client/server starters as in schedule-service |

---

## Open Questions

1. **`UserRole` enum source for attendance-service security layer**
   - What we know: `RequireRole` annotation requires `UserRole`. Academic-service and schedule-service each define their own copy in their respective `-api-contract` modules.
   - What's unclear: Should `attendance-api-contract` define its own `UserRole` (cleanest) or depend on `academic-api-contract`?
   - Recommendation: Define `UserRole` in `attendance-api-contract` to keep the contract self-contained — same pattern as schedule-api-contract. This is a one-line enum addition to the contract module.

2. **`semester.archived` event schema**
   - What we know: `SemesterCacheService` must refresh on `semester.archived` event (D-02). The four existing event schemas are for lesson lifecycle and attendance.
   - What's unclear: `semester.archived` event schema is not in `event-schemas/`. Academic-service may or may not publish this.
   - Recommendation: For Phase 15 (infra only), the refresh path is wired as a no-op stub in EventConsumer. The actual refresh is called from Phase 16 when event consumers are implemented. Flag as a dependency on Academic Service's event publishing.

3. **MongoDB replica set requirement for transactions**
   - What we know: Phase 15 does not use MongoDB transactions. Testcontainers `MongoDBContainer` provides a standalone instance by default.
   - What's unclear: Phase 17 (geo-checkin with Redis dedup) may require multi-document operations.
   - Recommendation: Irrelevant for Phase 15. Use standalone `MongoDBContainer` for now.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers (MongoDB, RabbitMQ) | Yes | 28.5.2 | — |
| Java 21 | Build + run | Yes (JAVA_HOME set in CLAUDE.md) | 21.0.9 (ms) | — |
| MongoDB (Docker image mongo:7.0) | INFRA-01, INFRA-02 test | Pulled via Testcontainers | 7.0 | — |
| RabbitMQ (Docker image rabbitmq:3.13) | INFRA-05 test | Pulled via Testcontainers | 3.13 | — |
| Schedule Service (live, port 19092) | INFRA-03 production | Not needed for tests | — | @MockitoBean ScheduleGrpcClient |
| Academic Service (live, port 19091) | INFRA-04 production | Not needed for tests | — | @MockitoBean AcademicGrpcClient |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** Live Schedule and Academic Services — tests mock both clients.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (junit-jupiter) via Spring Boot Test |
| Config file | None — uses `useJUnitPlatform()` from root build.gradle.kts |
| Quick run command | `./gradlew :services:attendance-service:attendance-app:test --tests "*Infrastructure*" --tests "*Security*"` |
| Full suite command | `./gradlew :services:attendance-service:attendance-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFRA-01 | Unique {lesson_id, user_id} index exists; second identical insert throws DuplicateKeyException | Integration (Testcontainers MongoDB) | `./gradlew :services:attendance-service:attendance-app:test --tests "*MongoIndexTest*"` | Wave 0 |
| INFRA-02 | AttendanceStatus written to MongoDB stored as lowercase string (e.g., "present" not "PRESENT") | Integration (Testcontainers MongoDB) | `./gradlew :services:attendance-service:attendance-app:test --tests "*EnumSerializationTest*"` | Wave 0 |
| INFRA-03 | ScheduleGrpcClient beans load; context starts without errors when stub is mocked | Integration (Spring context) | `./gradlew :services:attendance-service:attendance-app:test --tests "*Abstract*"` (context load) | Wave 0 |
| INFRA-04 | AcademicGrpcClient beans load; context starts without errors when stub is mocked | Integration (Spring context) | same as INFRA-03 | Wave 0 |
| INFRA-05 | Message published to rut-uit.events fanout received by attendance-service.events queue | Integration (Testcontainers RabbitMQ) | `./gradlew :services:attendance-service:attendance-app:test --tests "*RabbitConsumerTest*"` | Wave 0 |
| Success criterion 5 | @RequireRole(STUDENT) rejects X-User-Role: TEACHER with 403 | Integration (MockMvc) | `./gradlew :services:attendance-service:attendance-app:test --tests "*SecuritySmokeTest*"` | Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :services:attendance-service:attendance-app:test`
- **Per wave merge:** same (suite is small, ~10 tests total for this phase)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/ru/rutcampustrack/attendance/integration/AbstractAttendanceIntegrationTest.java` — shared base with MongoDB + RabbitMQ Testcontainers and mocked gRPC clients
- [ ] `src/test/java/ru/rutcampustrack/attendance/integration/MongoIndexTest.java` — covers INFRA-01
- [ ] `src/test/java/ru/rutcampustrack/attendance/integration/EnumSerializationTest.java` — covers INFRA-02
- [ ] `src/test/java/ru/rutcampustrack/attendance/integration/RabbitConsumerTest.java` — covers INFRA-05
- [ ] `src/test/java/ru/rutcampustrack/attendance/integration/SecuritySmokeTest.java` — covers success criterion 5
- [ ] `src/test/resources/application-test.yml` — gRPC overrides to localhost, MongoDB/RabbitMQ URLs via @DynamicPropertySource

---

## Project Constraints (from CLAUDE.md)

Directives the planner MUST verify compliance against:

| Directive | Applies to Phase 15 |
|-----------|---------------------|
| Contract-first: controller `implements` contract interface; mappings ONLY in interface | No controllers added in Phase 15. Add HealthCheckController implementing a contract interface if needed for security smoke test |
| Request DTO = Java `record`; Response DTO = class with RepresentationModel | No DTOs in Phase 15 |
| NO Lombok in `*-api-contract` modules | `UserRole` addition to `attendance-api-contract` must not use Lombok |
| Enum in PostgreSQL: lowercase strings via `LowercaseEnumConverter` | Not applicable — this service uses MongoDB |
| Enum in MongoDB: lowercase strings via `MongoCustomConversions` | INFRA-02 requirement |
| `ddl-auto: validate` (Hibernate only) | Not applicable — no JPA in this service |
| HATEOAS Level 3, RFC 7807 errors | GlobalExceptionHandler must use RFC 7807 `ErrorResponse` record |
| `@ControllerAdvice` centralized error handling | `GlobalExceptionHandler` copied from academic-service |
| Package structure: `ru.rutcampustrack.attendance.{module}` | All packages must follow this |
| REST paths: `/api/attendance/...` via Gateway | Health-check controller should use `/attendance/health-check` (gateway strips `/api`) |
| gRPC package: `ru.rutcampustrack.{service}.grpc` | Client wrappers in `ru.rutcampustrack.attendance.grpc` |
| NEVER `@Enumerated(EnumType.ORDINAL)` | Not applicable directly, but equivalent MongoDB pitfall is not registering converters |

---

## Sources

### Primary (HIGH confidence)

- Existing codebase: `services/schedule-service/schedule-app/build.gradle.kts` — gRPC/protobuf build configuration
- Existing codebase: `services/schedule-service/schedule-app/.../AcademicGrpcClient.java` — gRPC wrapper pattern
- Existing codebase: `services/schedule-service/schedule-app/.../RabbitConfig.java` — RabbitMQ exchange/converter pattern
- Existing codebase: `services/academic-service/academic-app/.../RequireRole.java`, `RoleCheckAspect.java`, `RequestContext.java`, `UserContextFilter.java` — AOP security pattern
- Existing codebase: `services/academic-service/academic-app/.../GlobalExceptionHandler.java` — RFC 7807 error pattern
- Existing codebase: `services/schedule-service/schedule-app/.../AbstractScheduleIntegrationTest.java` — Testcontainers base test pattern
- `proto/academic.proto` — AcademicGrpcService RPC definitions (GetGroupMembers, GetCampusGeofence, GetActiveSemester, IsHeadman)
- `proto/schedule.proto` — ScheduleGrpcService RPC definitions (GetActiveLesson, GetLessonById, GetLessonsByGroup)
- `docs/database-schema.md` — MongoDB attendance collection structure and index definitions

### Secondary (MEDIUM confidence)

- Spring Data MongoDB documentation — `MongoCustomConversions` API with `@WritingConverter`/`@ReadingConverter` (standard pattern, well-established)
- Spring AMQP documentation — `QueueBuilder.durable()` with `x-dead-letter-exchange` argument (standard pattern)

### Tertiary (LOW confidence)

- None.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions taken verbatim from existing schedule-service build.gradle.kts
- Architecture: HIGH — all patterns traced to existing code in the repository
- Pitfalls: HIGH — derived from documented issues in RabbitConfig comments and test patterns
- MongoDB converter pattern: MEDIUM — standard Spring Data MongoDB API, not verified against Context7 but well-established

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable Spring Boot 3.4 ecosystem; net.devh gRPC starter version stable)
