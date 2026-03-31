# Stack Research

**Domain:** Schedule Service v3.0 — lesson scheduling, auto-generation, cron status transitions, gRPC server + client, RabbitMQ events
**Researched:** 2026-03-31
**Confidence:** HIGH (all additions verified against existing working patterns in the codebase)

---

## Context: What Already Works (Do NOT Re-add)

The following are confirmed working in `academic-app/build.gradle.kts` and the base scaffold of `schedule-app/build.gradle.kts`. They carry over unchanged:

| Already Present | Notes |
|-----------------|-------|
| `spring-boot-starter-web` | in `schedule-app/build.gradle.kts` |
| `spring-boot-starter-data-jpa` | in `schedule-app/build.gradle.kts` |
| `spring-boot-starter-validation` | in `schedule-app/build.gradle.kts` |
| `spring-boot-starter-hateoas` | in `schedule-app/build.gradle.kts` |
| `spring-boot-starter-amqp` | in `schedule-app/build.gradle.kts` — RabbitMQ publisher |
| `springdoc-openapi-starter-webmvc-ui:2.7.0` | in `schedule-app/build.gradle.kts` |
| `postgresql` (driver) | in `schedule-app/build.gradle.kts` |
| `flyway-core` + `flyway-database-postgresql` | in `schedule-app/build.gradle.kts` |
| `lombok` | in `schedule-app/build.gradle.kts` |
| `spring-boot-starter-test` + `junit-platform-launcher` | in `schedule-app/build.gradle.kts` |

The `@RequireRole` AOP pattern, `LowercaseEnumConverter`, `GlobalExceptionHandler`, HATEOAS assembler pattern, contract-first interface pattern — all carry over from Academic Service by copy, not new dependencies.

---

## NEW Additions Required for Schedule Service

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `@Scheduled` (Spring built-in) | Spring Boot 3.4 (no dep) | Cron jobs: activate lessons, close lessons, mark absent | Zero extra dependency; single-instance VPS deployment makes Quartz overhead unjustified |
| `grpc-server-spring-boot-starter` | `3.1.0.RELEASE` | Serve ScheduleGrpcService (GetActiveLesson, GetLessonById, GetLessonsByGroup) | Same version already proven in Academic Service gRPC server |
| `grpc-client-spring-boot-starter` | `3.1.0.RELEASE` | Call AcademicGrpcService for validation (group/semester/subject/teacher existence) | Same library, same version; client and server starters must match |
| `com.google.protobuf` plugin | `0.9.4` | Generate Java stubs from `proto/schedule.proto` | Same plugin version as Academic Service |
| `protoc` | `3.25.3` | Proto compiler | Matches Academic Service exactly — do not diverge |
| `protoc-gen-grpc-java` | `1.63.0` | gRPC Java code generator | Matches Academic Service exactly — do not diverge |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-aop` | 3.4 BOM | `@RequireRole` AOP aspect (same as Academic Service) | Required from phase 1 when role-checking starts |
| `javax.annotation:javax.annotation-api` | `1.3.2` | `@Generated` annotation for proto-generated stubs | Required on Java 9+ — stubs won't compile without it |
| `testcontainers-bom` | `1.20.4` | Version management for Testcontainers | Manages `postgresql`, `rabbitmq`, `junit-jupiter` without explicit versions |
| `spring-boot-testcontainers` | 3.4 BOM | `@ServiceConnection` and `@Testcontainers` integration | Required for PostgreSQL and RabbitMQ containers in integration tests |
| `testcontainers:junit-jupiter` | via BOM | JUnit 5 lifecycle for containers | Required for `@Testcontainers` annotation support |
| `testcontainers:postgresql` | via BOM | Real PostgreSQL 16 with `week_type` and `lesson_status` ENUMs | H2 cannot emulate PostgreSQL custom ENUMs — same decision as Academic Service |
| `testcontainers:rabbitmq` | via BOM | Real RabbitMQ container for event publish tests | Same pattern as `EventIntegrationTest` in Academic Service |
| `awaitility` | Spring Boot BOM (no version needed since Boot 3.2) | Polling assertions for cron task tests | Replaces fragile `Thread.sleep` in tests that wait for scheduled methods to fire |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `@EnableScheduling` on main class or `SchedulingConfig` | Activates `@Scheduled` processing | Guard with `@Profile("!test")` or `@ConditionalOnProperty` to prevent test interference |
| In-process gRPC test server | `grpc.server.in-process-name` property | Same pattern as `AcademicGrpcIntegrationTest` — zero port binding, no flaky port conflicts |

---

## Cron Scheduling — `@Scheduled` Decision

**Use `@Scheduled`, NOT Quartz.**

The decision tree:

- **Single VPS instance** (Docker Compose, solo developer): No clustering, no distributed lock needed. Quartz's primary value is HA clustering where two nodes would otherwise double-fire the same cron.
- **Three cron jobs** (`activateLessons`, `closeLessons`, `markAbsent`): These are simple queries by time window + status. No dynamic scheduling, no per-job state, no retry logic beyond the next scheduled run.
- **Quartz cost**: Requires a `quartz_*` schema (8 tables in PostgreSQL), a `SchedulerFactory` bean, a `JobStore` configuration, and dependency on `spring-boot-starter-quartz`. This is non-trivial overhead for three fire-and-forget cron methods.
- **Future-proofing**: When/if multi-node deployment is needed, add ShedLock on top of `@Scheduled` — it requires one table and one annotation, and does not require rewriting the scheduling logic.

**`@EnableScheduling`** goes on a `SchedulingConfig` configuration class (not the main `@SpringBootApplication` class) so it can be excluded in tests with `@Profile("!test")`.

**Test isolation**: Mock the `ScheduledAnnotationBeanPostProcessor` via `@MockitoBean` in the abstract test base, OR use the profile guard. The profile guard is simpler and consistent with the existing `@ActiveProfiles("test")` pattern already in `AbstractAcademicIntegrationTest`.

**Cron expression for Moscow time (UTC+3):**
```java
@Scheduled(cron = "0 * * * * *", zone = "Europe/Moscow")  // every minute
public void activateLessons() { ... }
```
The `zone` parameter on `@Scheduled` eliminates JVM timezone dependency.

---

## gRPC Client Setup

Schedule Service calls Academic Service for validation (does this group/semester/subject/teacher exist?). The client setup is a mirror of what Academic Service already does in its test scope.

**`application.yml`:**
```yaml
grpc:
  server:
    port: 9095        # Schedule Service gRPC server port (internal only, not through Gateway)
  client:
    academic-service:
      address: static://localhost:9096    # Academic Service gRPC port
      negotiation-type: plaintext
```

**Injection:**
```java
@GrpcClient("academic-service")
private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub academicStub;
```

**In tests** (in-process pattern — same as `AcademicGrpcIntegrationTest`):
```java
// In @SpringBootTest properties:
"grpc.server.in-process-name=schedule-grpc-test",
"grpc.server.port=-1",
"grpc.client.inProcess.address=in-process:schedule-grpc-test",
"grpc.client.inProcess.negotiationType=plaintext"
```

For the Academic Service gRPC client calls in integration tests, Academic Service will not be running — mock the `AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub` with `@MockitoBean`.

---

## Timezone Handling

**No new library needed. Use `java.time` types already on classpath.**

The `schedule_db` schema in `docs/database-schema.md` uses `TIME` and `DATE` columns (not `TIMESTAMPTZ`) for schedule slot times:

| Column | PostgreSQL Type | Java Type | Rationale |
|--------|-----------------|-----------|-----------|
| `schedule_items.start_time` | `TIME` | `LocalTime` | Time-of-day bell slot — no timezone |
| `schedule_items.end_time` | `TIME` | `LocalTime` | Same |
| `lessons.date` | `DATE` | `LocalDate` | Calendar date — no timezone |
| `lessons.created_at` | `TIMESTAMPTZ` | `OffsetDateTime` | UTC audit timestamp |
| `lessons.closed_at` | `TIMESTAMPTZ` | `OffsetDateTime` | UTC audit timestamp |

For cron job comparisons (is this lesson's `start_time` within the next minute?), use `LocalTime.now(ZoneId.of("Europe/Moscow"))`. Pass the `ZoneId` explicitly — do NOT rely on `TimeZone.setDefault()` because it is a JVM-global mutation that breaks test isolation.

**Inject a `Clock` for testability:**
```java
@Bean
public Clock clock() {
    return Clock.system(ZoneId.of("Europe/Moscow"));
}
// Inject Clock into the cron service; in tests, replace with Clock.fixed(...)
```

`Jackson` already handles `LocalDate`, `LocalTime`, and `OffsetDateTime` via `JavaTimeModule`, which Spring Boot auto-configures. No additional Jackson modules needed.

---

## Complete `schedule-app/build.gradle.kts` (target state)

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf") version "0.9.4"      // NEW
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")   // NEW
    }
}

dependencies {
    implementation(project(":services:schedule-service:schedule-api-contract"))

    // Spring Boot (existing)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-aop")                     // NEW — @RequireRole aspect

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // gRPC server (serves ScheduleGrpcService)                                             NEW
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")

    // gRPC client (calls AcademicGrpcService for validation)                              NEW
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")

    // Required for proto-generated stubs on Java 9+                                       NEW
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")               // NEW
    testImplementation("org.testcontainers:junit-jupiter")                                  // NEW
    testImplementation("org.testcontainers:postgresql")                                     // NEW
    testImplementation("org.testcontainers:rabbitmq")                                       // NEW
    testImplementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")           // NEW — in-process gRPC tests
    testImplementation("org.awaitility:awaitility")                                         // NEW — cron tests
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
            it.plugins {
                create("grpc") { }
            }
        }
    }
}
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| `@Scheduled` (built-in, no dep) | Quartz (`spring-boot-starter-quartz`) | Use Quartz only when: 2+ service instances run simultaneously AND jobs must not double-fire AND ShedLock is insufficient |
| `@Scheduled` + ShedLock (future) | Quartz now | ShedLock is one table + one annotation; add it when multi-node is needed without rewriting cron logic |
| `LocalTime` + `LocalDate` for schedule slots | `ZonedDateTime` everywhere | Use `ZonedDateTime` if a lesson can span a DST boundary or if lessons need to be stored in absolute UTC time; RUT MIIT bell schedule is local-time by nature |
| Explicit `ZoneId` parameter in `now()` calls | `TimeZone.setDefault("Europe/Moscow")` | `setDefault` is a JVM-global mutation that makes tests non-reproducible on machines in other timezones |
| Injected `Clock` bean | `LocalTime.now()` inline | Use inline `now()` only for throwaway scripts; inject `Clock` whenever testability matters |
| `grpc-client-spring-boot-starter:3.1.0.RELEASE` | `spring-grpc` (Spring official) | Use `spring-grpc` only after upgrading to Spring Boot 4 — it requires Boot 4 and is incompatible with Boot 3.x |

---

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `spring-boot-starter-quartz` | Requires `quartz_*` DB schema (8 tables), `SchedulerFactory`, `JobStore` config — disproportionate overhead for 3 cron methods on single VPS | `@Scheduled` built-in |
| `spring-grpc:spring-grpc-spring-boot-starter` | Requires Spring Boot 4 — breaks the project's Spring Boot 3.4.1 baseline | `net.devh:grpc-*-spring-boot-starter:3.1.0.RELEASE` |
| `io.grpc:grpc-netty-shaded` (explicit) | Pulled transitively by `net.devh` starters; adding explicitly risks version conflict with the starter's transitive | Let the starter manage it |
| `spring-boot-starter-data-redis` | Schedule Service has no caching requirement in v3.0 — it has no read-heavy reference data; lesson queries go directly to PostgreSQL | Not needed; Academic Service owns Redis cache |
| H2 for tests | Cannot emulate PostgreSQL custom ENUMs `week_type` and `lesson_status` — same validated decision from Academic Service | Testcontainers PostgreSQL only |
| `jackson-datatype-hibernate6` | Not needed — Schedule Service has no Redis serialization of Hibernate proxy objects; Academic Service needed this specifically for Redis cache of entity proxies | Not applicable |

---

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `grpc-server-spring-boot-starter:3.1.0.RELEASE` | Spring Boot 3.4.1, Java 21 | Compiled against Spring Boot 3.2.4; confirmed working on 3.4.1 in Academic Service |
| `grpc-client-spring-boot-starter:3.1.0.RELEASE` | Same as server starter | Must use identical version to server starter to share the same gRPC runtime |
| `com.google.protobuf:protoc:3.25.3` | `protoc-gen-grpc-java:1.63.0` | Same pin as Academic Service — do not change one without the other |
| `com.google.protobuf` plugin `0.9.4` | Gradle 8.x, Java 21 | Same version as Academic Service — confirmed working |
| `testcontainers-bom:1.20.4` | Spring Boot Testcontainers 3.4 | Used successfully in Academic Service; 1.20.4 is latest stable at time of research |
| `awaitility` (no version, BOM managed) | Spring Boot 3.4 BOM | Spring Boot manages Awaitility since 3.2; no explicit version required |

---

## Sources

- `services/academic-service/academic-app/build.gradle.kts` — confirmed working versions for gRPC starters, Testcontainers BOM, protobuf plugin (HIGH confidence — source of truth in this repo)
- `services/schedule-service/schedule-app/build.gradle.kts` — current state of schedule-app, confirms what's already present (HIGH confidence)
- `services/academic-service/academic-app/src/test/java/.../AcademicGrpcIntegrationTest.java` — in-process gRPC test pattern with `grpc.server.in-process-name` (HIGH confidence)
- `services/academic-service/academic-app/src/test/java/.../AbstractAcademicIntegrationTest.java` — `@ActiveProfiles("test")`, `@MockitoBean RabbitTemplate`, container lifecycle pattern (HIGH confidence)
- `docs/database-schema.md` — `schedule_items` uses `TIME` + `DATE` types confirming `LocalTime` + `LocalDate` mapping (HIGH confidence)
- [Spring Framework Scheduling reference](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — `@EnableScheduling`, `@Scheduled(cron, zone)` parameters (HIGH confidence)
- [grpc-spring-boot-starter client configuration](https://yidongnan.github.io/grpc-spring-boot-starter/en/client/configuration.html) — `@GrpcClient`, `grpc.client.*` properties (MEDIUM confidence — third-party docs, consistent with working code in repo)
- [Baeldung: Disable @EnableScheduling in Tests](https://www.baeldung.com/spring-test-disable-enablescheduling) — `@Profile` guard and `@MockitoBean ScheduledAnnotationBeanPostProcessor` patterns (MEDIUM confidence)
- [Baeldung: Testing @Scheduled](https://www.baeldung.com/spring-testing-scheduled-annotation) — Awaitility + `@SpyBean` pattern for cron verification (MEDIUM confidence)
- [Medium: Quartz vs @Scheduled vs Message Queues](https://medium.com/turkcell/managing-background-jobs-in-spring-boot-quartz-vs-scheduled-vs-message-queues-e2dc5d4cfc2b) — comparison confirming `@Scheduled` is correct for single-instance (MEDIUM confidence)

---

*Stack research for: RutCampusTrack v3.0 Schedule Service — new capabilities only*
*Researched: 2026-03-31*
