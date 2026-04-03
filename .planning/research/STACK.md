# Stack Research

**Domain:** Attendance Service MVP — MongoDB geo-checkin, RabbitMQ consumer, gRPC clients
**Researched:** 2026-04-04
**Confidence:** HIGH (all versions verified against existing codebase + Spring Boot 3.4.1 BOM + official sources)

---

## Context: What Already Exists

This is a subsequent milestone on an existing Spring Boot 3.4 monorepo. The following are **already present in `attendance-app/build.gradle.kts`** and must not be re-added:

| Already Present | Source |
|-----------------|--------|
| `spring-boot-starter-web` | attendance-app/build.gradle.kts (existing) |
| `spring-boot-starter-data-mongodb` | attendance-app/build.gradle.kts (existing) |
| `spring-boot-starter-validation` | attendance-app/build.gradle.kts (existing) |
| `spring-boot-starter-hateoas` | attendance-app/build.gradle.kts (existing) |
| `spring-boot-starter-amqp` | attendance-app/build.gradle.kts (existing) |
| `springdoc-openapi-starter-webmvc-ui:2.7.0` | attendance-app/build.gradle.kts (existing) |
| `lombok` (compileOnly + annotationProcessor) | attendance-app/build.gradle.kts (existing) |
| `spring-boot-starter-test` | attendance-app/build.gradle.kts (existing) |
| `junit-platform-launcher` | attendance-app/build.gradle.kts (existing) |

**What the existing file is missing for the full MVP feature set:** Testcontainers, gRPC client stubs, Protobuf plugin, AOP, and `javax.annotation-api`.

---

## Core Technologies

### Spring Data MongoDB (already present, version context)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| `spring-boot-starter-data-mongodb` | managed by Boot 3.4.1 BOM → Spring Data MongoDB 4.4.1 | MongoDB document repositories, `@Document`, queries | Already in build; Spring Boot auto-configures from `spring.data.mongodb.uri`. Do NOT add explicit version — BOM manages it. |

Spring Boot 3.4.1 imports Spring Data BOM 2024.1.1, which sets Spring Data MongoDB to 4.4.1. This version is compatible with the `mongo:7` image already declared in `docker-compose.yml`.

**Required `application.yml` addition:**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://mongo-attendance:27017/attendance_db
```

No `ddl-auto`, no Flyway — MongoDB is schemaless. Index declarations go on `@Document` entity classes via `@Indexed` / `@CompoundIndex`.

---

## Required Additions to `attendance-app/build.gradle.kts`

### 1. Testcontainers for MongoDB Integration Tests

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

// in dependencies block:
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:mongodb")
testImplementation("org.testcontainers:rabbitmq")
```

**Why `1.20.4`:** This exact BOM version is already used by schedule-app and academic-app. Using the same version eliminates any risk of BOM conflicts across the monorepo.

**Why `org.testcontainers:mongodb`:** This is the correct artifact under the `org.testcontainers` group managed by testcontainers-bom. The artifact `testcontainers-mongodb` (version 2.0.x) is a completely different, unrelated namespace — do not use it.

**Why `spring-boot-testcontainers`:** Provides `@ServiceConnection` support. Annotating a `MongoDBContainer` bean with `@ServiceConnection` causes Spring Boot to auto-configure `spring.data.mongodb.uri` to point at the running container — no manual `@DynamicPropertySource` or property override needed. This is the idiomatic Spring Boot 3.1+ approach.

**Why `rabbitmq` container:** Attendance Service consumes from the fanout exchange. RabbitMQ consumer integration tests need a real broker to verify `@RabbitListener` wiring, deserialization, and business logic triggered by events.

**Test base pattern:**
```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class AttendanceIntegrationTestBase {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");  // matches docker-compose.yml

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
}
```

### 2. gRPC Client Dependencies (+ Protobuf Plugin)

```kotlin
// in plugins block:
id("com.google.protobuf") version "0.9.4"

// in dependencies block:
implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
compileOnly("javax.annotation:javax.annotation-api:1.3.2")

// sourceSets and protobuf blocks (copy verbatim from schedule-app):
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

**Why `grpc-client-spring-boot-starter` only (NOT server starter):** Attendance Service is a pure gRPC consumer in v4.0 — it calls Academic Service (GetCampusGeofence, GetGroupMembers) and Schedule Service (GetActiveLesson, GetLessonById). It exposes no gRPC server of its own. Adding the server starter would open an unnecessary port and load server beans.

**Why `3.1.0.RELEASE`:** This is the proven working version in the monorepo. Both schedule-app (client to academic) and academic-app (server + test client) use this exact version. Changing it risks breaking gRPC runtime compatibility.

**Why `javax.annotation-api:1.3.2`:** The `@javax.annotation.Generated` annotation used in proto-generated stubs was removed from the JDK in Java 9+. Without this dependency, the Gradle build fails to compile the generated stub classes. Same fix already applied in schedule-app and academic-app.

**Why identical `protoc:3.25.3` and `protoc-gen-grpc-java:1.63.0`:** These must match exactly across all services that share the same `proto/` directory, otherwise generated stub classes are incompatible across service boundaries. Do not bump these independently.

**gRPC client `application.yml` additions:**
```yaml
grpc:
  client:
    academic-service:
      address: static://academic-service:19091
      negotiation-type: plaintext
    schedule-service:
      address: static://schedule-service:19092
      negotiation-type: plaintext
```

**gRPC client wrapper pattern (copy from `AcademicGrpcClient` in schedule-app):**
```java
@Component
public class ScheduleGrpcClient {

    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    public LessonResponse getActiveLesson(Long groupId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getActiveLesson(ActiveLessonRequest.newBuilder()
                            .setGroupId(groupId)
                            .setTimestamp(OffsetDateTime.now().toString())
                            .build());
        } catch (StatusRuntimeException e) {
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }
}
```

### 3. AOP Support

```kotlin
implementation("org.springframework.boot:spring-boot-starter-aop")
```

**Why:** The `@RequireRole` annotation + `RoleCheckAspect` pattern is used in academic-app and schedule-app for role authorization without loading Spring Security on the service layer. Attendance Service needs the same pattern — it has distinct authorization requirements per endpoint (STUDENT geo-checkin, HEADMAN manual marking, TEACHER/HEADMAN reports).

---

## Geo-Distance Calculation: No Library Needed

**Decision: Implement Haversine as a single static utility method. Zero external dependencies.**

Do NOT add any geo library (GeoTools, spatial4j, JTS, locationtech). The geofence check is:
> "Is the student's submitted {lat, lng} within `radius_m` meters of campus center?"

This is a single computation against one static reference point fetched from Academic Service via gRPC (`GetCampusGeofence`). It is not a spatial query across stored geo-indexed documents.

MongoDB geospatial operations (`$geoWithin`, `$near`, `2dsphere` index) are for querying stored documents by proximity — the wrong tool for validating input against a fixed point.

**Haversine utility:**
```java
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtils() {}

    /**
     * Returns distance in meters between two WGS-84 coordinates.
     * Accuracy: ~0.5% — sufficient for campus-scale geofence validation (radii < 1 km).
     */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static boolean isWithinGeofence(double studentLat, double studentLng,
                                            double centerLat, double centerLng, int radiusM) {
        return distanceMeters(studentLat, studentLng, centerLat, centerLng) <= radiusM;
    }
}
```

This is the standard Haversine formula with Earth mean radius 6,371,000 m (IUGG recommendation). Error at campus distances (100–500 m radius) is negligible.

---

## RabbitMQ Consumer Setup

**No new library** — `spring-boot-starter-amqp` is already present.

What IS new: the consumer-side configuration. All prior services (academic, schedule) only **publish** to the fanout exchange `rut-uit.events`. Attendance Service is the **first consumer**.

### Use a durable named queue, NOT AnonymousQueue

Fanout exchange broadcasts every message to all bound queues. Each consumer service must declare its own queue bound to the exchange.

| Option | Durability | Message retention during restart | Right for Attendance? |
|--------|------------|----------------------------------|----------------------|
| `AnonymousQueue` (auto-delete, exclusive) | No | Lost | NO — lesson.closed events arriving during restart are silently dropped |
| Named durable queue | Yes | Held by broker | YES — events accumulate while service is restarting, processed on reconnect |

A `lesson.closed` event that is lost means absent students are never auto-marked. That is a data integrity issue, not just a cosmetic miss.

### RabbitMQ consumer config bean:

```java
@Configuration
public class RabbitConsumerConfig {

    public static final String ATTENDANCE_QUEUE = "rut-uit.attendance-service";
    private static final String EXCHANGE = "rut-uit.events";

    @Bean
    public Queue attendanceServiceQueue() {
        return new Queue(ATTENDANCE_QUEUE, true);  // durable=true
    }

    @Bean
    public FanoutExchange eventsExchange() {
        return new FanoutExchange(EXCHANGE, true, false);  // must match publisher declaration
    }

    @Bean
    public Binding attendanceQueueBinding(Queue attendanceServiceQueue,
                                           FanoutExchange eventsExchange) {
        return BindingBuilder.bind(attendanceServiceQueue).to(eventsExchange);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
```

### Listener:

```java
@Component
public class LessonEventConsumer {

    @RabbitListener(queues = RabbitConsumerConfig.ATTENDANCE_QUEUE)
    public void onEvent(Map<String, Object> message) {
        String eventType = (String) message.get("event_type");
        // route by eventType: "lesson.started", "lesson.closed", "lesson.cancelled"
    }
}
```

### Critical: ObjectMapper injection

**Inject the Spring Boot-managed `ObjectMapper`** — the same instance used by `RabbitTemplate` in the publisher services. Do NOT create a new `ObjectMapper`. A new one defaults to including `@class` type fields in JSON (if NON_FINAL typing was ever enabled elsewhere), and more importantly breaks Jackson module registration (JavaTimeModule, etc.).

This is Pitfall 3 documented in `services/academic-service/academic-app/src/main/java/.../event/RabbitConfig.java`. The fix is the same: inject `ObjectMapper objectMapper` as a constructor/method parameter and pass to `Jackson2JsonMessageConverter`.

### Event envelope structure

Messages published by Schedule Service follow the schema in `event-schemas/`:
```json
{
  "event_type": "lesson.closed",
  "event_id": "uuid",
  "occurred_at": "2026-04-04T10:15:00Z",
  "payload": {
    "lesson_id": 123,
    "group_id": 42,
    "subject_id": 7
  }
}
```

Deserialize to `Map<String, Object>` or a shared envelope record, then route by `event_type`. The payload per event type is defined in `event-schemas/*.json`.

---

## Version Compatibility Summary

| Dependency | Version | Source | Confidence |
|------------|---------|--------|------------|
| `spring-boot-starter-data-mongodb` | Spring Data MongoDB 4.4.1 (managed) | Spring Boot 3.4.1 → Spring Data BOM 2024.1.1 | HIGH |
| `testcontainers-bom` | 1.20.4 | explicit — matches schedule-app and academic-app | HIGH |
| `org.testcontainers:mongodb` | 1.20.4 (via BOM) | testcontainers-bom | HIGH |
| `org.testcontainers:rabbitmq` | 1.20.4 (via BOM) | testcontainers-bom — same as academic-app | HIGH |
| `spring-boot-testcontainers` | managed by Boot BOM | Spring Boot 3.4.1 | HIGH |
| `net.devh:grpc-client-spring-boot-starter` | 3.1.0.RELEASE | explicit — same as schedule-app | HIGH |
| `com.google.protobuf:protoc` | 3.25.3 | explicit — same as schedule-app and academic-app | HIGH |
| `io.grpc:protoc-gen-grpc-java` | 1.63.0 | explicit — same as schedule-app and academic-app | HIGH |
| `javax.annotation:javax.annotation-api` | 1.3.2 | explicit — same as schedule-app | HIGH |
| `spring-boot-starter-aop` | managed by Boot BOM | Spring Boot 3.4.1 | HIGH |
| Haversine geo | no dependency | pure Java (`java.lang.Math`) | HIGH |

---

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| Pure Java Haversine | MongoDB `$geoWithin` + `2dsphere` index | Geofence is a single static circle fetched from gRPC; `$geoWithin` queries stored documents — the wrong abstraction here |
| Pure Java Haversine | `org.locationtech.jts` / GeoTools / spatial4j | External library with large transitive dependency tree for a 10-line computation |
| Durable named queue | `AnonymousQueue` (auto-delete) | Auto-delete queues lose messages during service restart; `lesson.closed` loss means permanent absent-marking failure |
| `grpc-client-spring-boot-starter` only | both client + server starters | No gRPC server in v4.0; adding server starter opens an unnecessary port (19093) and loads server lifecycle beans |
| `@ServiceConnection` on `MongoDBContainer` | Manual `@DynamicPropertySource` | `@ServiceConnection` is the Spring Boot 3.1+ idiomatic approach — less boilerplate, proven in schedule-app and academic-app |

---

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `spring-boot-starter-data-jpa` / `flyway-*` / `postgresql` | Attendance Service has no relational DB | MongoDB only — already absent from attendance-app |
| `grpc-server-spring-boot-starter` | No gRPC server in v4.0 Attendance Service | Add only in a future milestone if gRPC server endpoint is needed |
| Any geo library (GeoTools, JTS, spatial4j) | Single-point geofence needs only Haversine | Pure Java utility class (`GeoUtils`) |
| `spring-boot-starter-data-redis` | No caching requirement in v4.0 | Add only if caching requirements emerge in v4.1+ |
| `new ObjectMapper()` in `RabbitConsumerConfig` | Bypasses Spring Boot's registered Jackson modules; risks `@class` type headers | Inject the Spring Boot-managed `ObjectMapper` bean |
| `AnonymousQueue` for RabbitMQ consumer | Auto-deletes on disconnect; silently drops events during service restart | Durable named queue with explicit `BindingBuilder` |
| `testcontainers-mongodb:2.0.4` (wrong artifact group) | Different namespace, not part of `testcontainers-bom` | `org.testcontainers:mongodb` (version from BOM) |

---

## Complete `attendance-app/build.gradle.kts` (Target State)

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf") version "0.9.4"                         // ADD
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")      // ADD
    }
}

dependencies {
    implementation(project(":services:attendance-service:attendance-api-contract"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-aop")            // ADD — @RequireRole aspect
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")      // ADD
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")                     // ADD

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")      // ADD
    testImplementation("org.testcontainers:junit-jupiter")                          // ADD
    testImplementation("org.testcontainers:mongodb")                                // ADD
    testImplementation("org.testcontainers:rabbitmq")                               // ADD
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {                                                                        // ADD BLOCK
    main {
        proto {
            srcDir(rootProject.file("proto"))
        }
    }
}

protobuf {                                                                          // ADD BLOCK
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

## Sources

- `services/attendance-service/attendance-app/build.gradle.kts` — current state (what already exists)
- `services/schedule-service/schedule-app/build.gradle.kts` — gRPC client + Testcontainers + Protobuf plugin pattern (verified working)
- `services/academic-service/academic-app/build.gradle.kts` — gRPC server + Testcontainers + RabbitMQ publisher pattern (verified working)
- `services/academic-service/academic-app/src/main/java/.../event/RabbitConfig.java` — ObjectMapper injection warning (Pitfall 3, documented inline)
- `services/schedule-service/schedule-app/src/main/java/.../grpc/AcademicGrpcClient.java` — `@GrpcClient` + `BlockingStub` + 3s deadline pattern
- `proto/schedule.proto`, `proto/academic.proto` — gRPC service contracts consumed by Attendance Service
- `docker-compose.yml` — confirms `mongo:7` image for `mongo-attendance` container
- `event-schemas/lesson.closed.json`, `event-schemas/attendance.marked.json` — canonical event envelope structure
- [Spring Data BOM 2024.1.1 release](https://github.com/spring-projects/spring-data-bom/releases/tag/2024.1.1) — confirms Spring Data MongoDB 4.4.1 (HIGH confidence)
- [Spring Boot 3.4.1 build.gradle](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.4.1/spring-boot-project/spring-boot-dependencies/build.gradle) — confirms Spring Data BOM 2024.1.1, Testcontainers BOM 1.20.4 (HIGH confidence)
- [Testcontainers MongoDB module docs](https://java.testcontainers.org/modules/databases/mongodb/) — confirms `org.testcontainers:mongodb` artifact name (HIGH confidence)
- [RabbitMQ Spring AMQP fanout tutorial](https://rabbitmq.com/tutorials/tutorial-three-spring-amqp.html) — confirms queue-per-service consumer binding pattern (MEDIUM confidence)

---
*Stack research for: RutCampusTrack v4.0 Attendance Service MVP — new capabilities only*
*Researched: 2026-04-04*
