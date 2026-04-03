# Phase 14: gRPC Server - Research

**Researched:** 2026-04-04
**Domain:** gRPC server implementation (net.devh grpc-spring-boot-starter) for Schedule Service
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** When no active lesson exists for the group at the given timestamp, return gRPC `NOT_FOUND` status with descriptive message. Caller handles absence explicitly.
- **D-02:** When multiple lessons overlap for the same group (rare scheduling error), return the first by `lesson_number ASC` (`ORDER BY lesson_number ASC LIMIT 1`).
- **D-03:** Port `GrpcExceptionAdvice` from academic-service into `ru.rutcampustrack.schedule.grpc` package. Map: `ResourceNotFoundException` → `NOT_FOUND`, `IllegalArgumentException` → `INVALID_ARGUMENT`, `Exception` → `INTERNAL`.
- **D-04:** Validate `GetLessonsByGroup` date range: if `date_from > date_to`, return `INVALID_ARGUMENT` with description. Validate in the service impl before querying.
- **D-05:** Add `grpc-server-spring-boot-starter` alongside existing `grpc-client-spring-boot-starter`. Both coexist — client connects to academic-service (port 19091), server listens on port 19092 (already configured in application.yml).
- **D-06:** Test `ScheduleGrpcServiceImpl` methods directly — inject repositories, call methods with mock `StreamObserver`. Same pattern as academic-service tests. No in-process gRPC channel setup needed.

### Claude's Discretion
- Whether to create a `ScheduleReadService` (like academic-service's `AcademicReadService`) or query repositories directly from the gRPC impl (no caching needed yet, so direct is simpler)
- Repository query design for GetActiveLesson: new native query or compose from existing `findPlannedDueForActivation`-style queries
- Repository query for GetLessonsByGroup: filter by group_id + semester_id + date range, JOIN schedule_items for enrichment
- Whether to parse `timestamp` string in GetActiveLesson into `LocalDateTime` or `OffsetDateTime`
- Test data setup helpers (reuse from existing test classes or create new)

### Deferred Ideas (OUT OF SCOPE)
- None stated
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GRPC-01 | GetActiveLesson returns the currently active lesson for a group | Query `lessons` JOIN `schedule_items` WHERE status='active' AND group_id=? AND date=today — return first by lesson_number ASC LIMIT 1; NOT_FOUND when empty |
| GRPC-02 | GetLessonById returns lesson details by ID | `lessonRepository.findById(id)` + `scheduleItemRepository.findById(scheduleItemId)` for enrichment; NOT_FOUND when absent |
| GRPC-03 | GetLessonsByGroup returns all lessons for a group in a date range | Query group_id via schedule_items + date range filter; validate date_from <= date_to; INVALID_ARGUMENT otherwise |
</phase_requirements>

---

## Summary

Phase 14 implements the gRPC server side for Schedule Service — the last deliverable of the v3.0 milestone. Three RPCs serve Attendance Service: `GetActiveLesson`, `GetLessonById`, `GetLessonsByGroup`. The project already has a working reference implementation (academic-service's `AcademicGrpcServiceImpl` + `GrpcExceptionAdvice`) and proto codegen is already wired in `build.gradle.kts`. The only new dependency is `grpc-server-spring-boot-starter:3.1.0.RELEASE` (the client starter of the same version is already present). `grpc.server.port: 19092` is already in `application.yml` and `grpc.server.port: -1` is already in `application-test.yml`.

The schedule-service's `grpc` package currently contains only `AcademicGrpcClient.java`. The new impl file, the exception advice, and the test class will all go into `ru.rutcampustrack.schedule.grpc`. No new entities, no Flyway migrations, and no proto changes are needed — all required fields are already in `schedule.proto`.

The primary design choice left to discretion is query strategy. Direct repository queries (no intermediate service layer) are appropriate because no caching is needed. Two new repository methods are required on `LessonRepository`: one for GetActiveLesson (find ACTIVE lesson by group_id at a timestamp) and one for GetLessonsByGroup (find all lessons for a group across a date range via schedule_items JOIN). Both must use native queries with `status::text` cast following the established pattern.

**Primary recommendation:** Port academic-service's gRPC server pattern verbatim, add only the two new native queries to `LessonRepository`, keep the impl flat (no `ScheduleReadService` indirection), and test via direct method invocation with `mock(StreamObserver.class)`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `net.devh:grpc-server-spring-boot-starter` | 3.1.0.RELEASE | Netty gRPC server auto-config, `@GrpcService`, `@GrpcAdvice` | Same version as the client starter already present; project-wide decision D-05 |
| `net.devh:grpc-client-spring-boot-starter` | 3.1.0.RELEASE | Already present — client to academic-service | Must remain at same version as server starter |
| `io.grpc:protoc-gen-grpc-java` | 1.63.0 | Proto codegen (already configured) | Pinned in build.gradle.kts protobuf block |
| `com.google.protobuf:protoc` | 3.25.3 | Proto compiler (already configured) | Pinned in build.gradle.kts |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `javax.annotation:javax.annotation-api` | 1.3.2 | `@Generated` for proto classes (already present) | Required for proto codegen on Java 11+ |
| Testcontainers PostgreSQL | 1.20.4 | Real DB in tests (already in BOM) | All integration tests extend `AbstractScheduleIntegrationTest` |

**Installation — only ONE new line needed:**
```kotlin
// In services/schedule-service/schedule-app/build.gradle.kts
implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
```

---

## Architecture Patterns

### New Files

```
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/
├── AcademicGrpcClient.java                  ← EXISTING (do not touch)
├── ScheduleGrpcServiceImpl.java             ← NEW (implements ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase)
└── GrpcExceptionAdvice.java                 ← NEW (ported from academic-service)

services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/
└── ScheduleGrpcServiceImplTest.java         ← NEW (direct method invocation, StreamObserver mock)
```

### Pattern 1: @GrpcService Implementation

The generated base class is `ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase` (in package `ru.rutcampustrack.schedule.grpc`, per `option java_package` in schedule.proto).

```java
// Source: services/academic-service/academic-app/.../AcademicGrpcServiceImpl.java (verbatim pattern)
@GrpcService
public class ScheduleGrpcServiceImpl extends ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase {

    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    public ScheduleGrpcServiceImpl(LessonRepository lessonRepository,
                                    ScheduleItemRepository scheduleItemRepository) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
    }

    @Override
    public void getActiveLesson(ActiveLessonRequest request,
                                StreamObserver<LessonResponse> responseObserver) {
        // parse timestamp → LocalDateTime Moscow
        // query ACTIVE lessons for group_id, ORDER BY lesson_number ASC LIMIT 1
        // if empty → throw ResourceNotFoundException (caught by GrpcExceptionAdvice)
        // else build LessonResponse and call responseObserver.onNext + onCompleted
    }

    @Override
    public void getLessonById(LessonByIdRequest request,
                               StreamObserver<LessonResponse> responseObserver) { ... }

    @Override
    public void getLessonsByGroup(LessonsByGroupRequest request,
                                   StreamObserver<LessonsResponse> responseObserver) { ... }
}
```

### Pattern 2: @GrpcAdvice Error Mapping

```java
// Source: services/academic-service/academic-app/.../GrpcExceptionAdvice.java
@GrpcAdvice
public class GrpcExceptionAdvice {
    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public Status handleNotFound(ResourceNotFoundException e) {
        return Status.NOT_FOUND.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public Status handleBadRequest(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleInternal(Exception e) {
        log.error("gRPC internal error", e);
        return Status.INTERNAL.withDescription("Internal server error").withCause(e);
    }
}
```

The import is `ru.rutcampustrack.schedule.exception.ResourceNotFoundException` (the schedule-service's own exception, not the academic one).

### Pattern 3: Building LessonResponse from Lesson + ScheduleItem

`LessonResponse` proto message fields and their Java sources:

| Proto field | Source entity | Notes |
|-------------|---------------|-------|
| `id` | `Lesson.id` | |
| `schedule_item_id` | `Lesson.scheduleItemId` | |
| `group_id` | `ScheduleItem.groupId` | Need to JOIN/fetch ScheduleItem |
| `subject_id` | `ScheduleItem.subjectId` | |
| `teacher_id` | `ScheduleItem.teacherId` | |
| `date` | `Lesson.date.toString()` | ISO date string |
| `lesson_number` | `ScheduleItem.lessonNumber` | Cast `short` to `int` |
| `start_time` | `ScheduleItem.startTime.toString()` | |
| `end_time` | `ScheduleItem.endTime.toString()` | |
| `status` | `Lesson.status.name().toLowerCase()` | e.g. "active" |
| `is_geo_blocked` | `Lesson.isGeoBlocked` | |
| `room` | `ScheduleItem.room != null ? room : ""` | Nullable — use empty string per project pattern |

```java
// Source: established project pattern (AcademicGrpcServiceImpl nullable handling)
LessonResponse buildResponse(Lesson lesson, ScheduleItem item) {
    return LessonResponse.newBuilder()
            .setId(lesson.getId())
            .setScheduleItemId(lesson.getScheduleItemId())
            .setGroupId(item.getGroupId())
            .setSubjectId(item.getSubjectId())
            .setTeacherId(item.getTeacherId())
            .setDate(lesson.getDate().toString())
            .setLessonNumber(item.getLessonNumber())
            .setStartTime(item.getStartTime().toString())
            .setEndTime(item.getEndTime().toString())
            .setStatus(lesson.getStatus().name().toLowerCase())
            .setIsGeoBlocked(lesson.isGeoBlocked())
            .setRoom(item.getRoom() != null ? item.getRoom() : "")
            .build();
}
```

### Pattern 4: Timestamp Parsing for GetActiveLesson

The `timestamp` field in `ActiveLessonRequest` is ISO-8601 string. Decision: parse to `LocalDateTime` representing Moscow wall-clock time (consistent with how cron queries use `LocalDateTime` for Moscow time).

```java
// Recommended approach — consistent with LessonStatusTransitionJob
LocalDateTime nowMoscow = LocalDateTime.parse(request.getTimestamp());
// or, if ISO with offset: OffsetDateTime.parse(request.getTimestamp()).atZoneSameInstant(MOSCOW).toLocalDateTime()
```

The active lesson query: find lesson WHERE `status = 'active'` AND the ScheduleItem `group_id = ?` AND `lesson.date = nowMoscow.toLocalDate()` AND `start_time <= nowMoscow.toLocalTime()` AND `end_time + 5 min >= nowMoscow.toLocalTime()`. Simpler approach: just find all ACTIVE lessons for the group on that date and return the first by lesson_number — the status `active` already guarantees the cron has opened it and not yet closed it.

### Pattern 5: GetLessonsByGroup Repository Query

GetLessonsByGroup needs lessons enriched with ScheduleItem data. Two approaches:
1. **Fetch lessons via ScheduleItem IDs** — find all ScheduleItem IDs for group_id + semester_id, then fetch lessons within date range. This composes existing `findByScheduleItemIdInAndDateBetweenAndStatusIn` with all statuses.
2. **Single native JOIN query** — single SQL with JOIN on schedule_items for group_id + semester_id + date range filter.

Approach 1 is simpler and leverages existing repository methods. Approach 2 requires a new native query but avoids two round-trips. Given the small dataset and no caching requirement, Approach 1 is recommended.

### Pattern 6: New Repository Query for GetActiveLesson

A new native query is needed to find the active lesson for a group at a timestamp:

```java
// Add to LessonRepository — consistent with existing native query patterns
@Query(value = """
    SELECT l.* FROM lessons l
    JOIN schedule_items si ON si.id = l.schedule_item_id
    WHERE l.status::text = 'active'
      AND si.group_id = :groupId
      AND l.date = CAST(:date AS date)
    ORDER BY si.lesson_number ASC
    LIMIT 1
    """, nativeQuery = true)
Optional<Lesson> findActiveLessonForGroup(
        @Param("groupId") Long groupId,
        @Param("date") LocalDate date);
```

This returns at most one result (LIMIT 1, ordered by lesson_number ASC per D-02). When empty, throw `ResourceNotFoundException("Lesson", "group_id+status", "active")`.

### Pattern 7: Test Class Structure (D-06)

```java
// Direct method invocation — no in-process channel
class ScheduleGrpcServiceImplTest extends AbstractScheduleIntegrationTest {

    @Autowired
    ScheduleGrpcServiceImpl grpcService;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;  // prevent outbound connections

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
    }

    @Test
    void getActiveLesson_returnsActiveLesson() {
        // arrange: create ScheduleItem + ACTIVE Lesson
        // act: grpcService.getActiveLesson(request, mockObserver)
        // assert: verify(mockObserver).onNext(argThat(r -> r.getId() == ...))
    }

    @Test
    void getActiveLesson_notFound_throwsResourceNotFoundException() {
        // arrange: no lessons
        // act + assert: assertThatThrownBy(() -> grpcService.getActiveLesson(...))
        //     .isInstanceOf(ResourceNotFoundException.class)
    }
}
```

The `@MockitoBean AcademicGrpcClient` prevents `AbstractScheduleIntegrationTest`'s startup from attempting a gRPC connection to academic-service (same as `LessonStatusTransitionJobTest`).

### Anti-Patterns to Avoid

- **Self-invocation with @Cacheable**: Academic-service introduced `AcademicReadService` to avoid this, but since schedule-service gRPC impl has NO caching (discretion choice), a separate read service is unnecessary. Query repositories directly.
- **Using @Transactional on gRPC tests**: Same issue as with cron tests — `@TransactionalEventListener(AFTER_COMMIT)` won't fire. Use `@AfterEach` cleanup instead (already established pattern in this project).
- **Nullable proto fields set via `.setXxx(null)`**: Proto3 setters don't accept nulls. Always use null-check and substitute empty string `""` or `0L` (see `room`, `telegramId` in `AcademicGrpcServiceImpl`).
- **Forgetting `status::text` cast in native queries**: Every existing native query in this project uses `status::text = 'value'` not `status = 'value'`. Omitting the cast causes PostgreSQL `operator does not exist: lesson_status = text` error.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| gRPC exception mapping | Custom try/catch in each RPC method | `@GrpcAdvice` + `@GrpcExceptionHandler` | Declarative, applies across all RPC methods automatically |
| gRPC server lifecycle | Manual Netty server init | `grpc-server-spring-boot-starter` auto-config | Handles port binding, TLS config, Spring lifecycle integration |
| Proto class generation | Manual proto compilation | `com.google.protobuf` Gradle plugin (already configured) | Already working — proto codegen runs at build time |

---

## Common Pitfalls

### Pitfall 1: Using `grpc-server-spring-boot-starter` version mismatch
**What goes wrong:** If server starter version differs from client starter, Spring Boot auto-config conflicts occur at startup — typically `BeanCreationException` for `GrpcServerLifecycle`.
**Why it happens:** `grpc-spring-boot-starter` auto-configs register beans keyed by version; mixing `3.1.0.RELEASE` client with `3.0.x` server causes bean wiring failures.
**How to avoid:** Use identical versions — both must be `3.1.0.RELEASE` (the client is already at this version).
**Warning signs:** `BeanCreationException` mentioning `GrpcServer` at startup.

### Pitfall 2: `status::text` cast missing in native queries
**What goes wrong:** `operator does not exist: lesson_status = text` PostgreSQL error.
**Why it happens:** PostgreSQL custom enum type `lesson_status` has no implicit cast from text. Every native query in this repo casts with `status::text`.
**How to avoid:** Follow every existing native query: `WHERE l.status::text = 'active'`.
**Warning signs:** Test fails with `PSQLException: operator does not exist`.

### Pitfall 3: `ResourceNotFoundException` import confusion
**What goes wrong:** Compiler error or wrong exception type mapped by `@GrpcExceptionHandler`.
**Why it happens:** Both `ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException` (academic-service) and `ru.rutcampustrack.schedule.exception.ResourceNotFoundException` (schedule-service) exist in the classpath. `GrpcExceptionAdvice` must handle the schedule-service version.
**How to avoid:** Import `ru.rutcampustrack.schedule.exception.ResourceNotFoundException` explicitly in `GrpcExceptionAdvice`.
**Warning signs:** gRPC returns `INTERNAL` instead of `NOT_FOUND` for missing lessons.

### Pitfall 4: `grpc.server.port: -1` missing for new test context
**What goes wrong:** Netty tries to bind port 19092 during tests, fails if already bound or causes port conflicts.
**Why it happens:** Adding `grpc-server-spring-boot-starter` activates Netty gRPC server startup in the test Spring context.
**How to avoid:** `application-test.yml` already has `grpc.server.port: -1` — verify it remains in place. The new test class extends `AbstractScheduleIntegrationTest` which uses `@ActiveProfiles("test")`, picking up `application-test.yml`.
**Warning signs:** `Address already in use` or `BindException` in test output.

### Pitfall 5: `@MockitoBean AcademicGrpcClient` missing in new test class
**What goes wrong:** Spring context fails to start because `AcademicGrpcClient` attempts connection to `static://localhost:19091` which is not running in test environment.
**Why it happens:** `AcademicGrpcClient` is a `@Component` eagerly initialized. Without a mock, `@GrpcClient` annotation triggers gRPC channel creation at startup.
**How to avoid:** Add `@MockitoBean AcademicGrpcClient academicGrpcClient;` to the test class (same as `LessonStatusTransitionJobTest`).
**Warning signs:** `StatusRuntimeException: UNAVAILABLE` during Spring context startup in tests.

---

## Code Examples

### GetActiveLesson — calling pattern
```java
// Source: schedule.proto + AcademicGrpcServiceImpl.java pattern
@Override
public void getActiveLesson(ActiveLessonRequest request,
                             StreamObserver<LessonResponse> responseObserver) {
    LocalDate date = LocalDateTime.parse(request.getTimestamp()).toLocalDate();
    Lesson lesson = lessonRepository.findActiveLessonForGroup(request.getGroupId(), date)
            .orElseThrow(() -> new ResourceNotFoundException("Lesson", "group_id", request.getGroupId()));
    ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
            .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", lesson.getScheduleItemId()));
    responseObserver.onNext(buildResponse(lesson, item));
    responseObserver.onCompleted();
}
```

### GetLessonsByGroup — date validation (D-04)
```java
@Override
public void getLessonsByGroup(LessonsByGroupRequest request,
                               StreamObserver<LessonsResponse> responseObserver) {
    LocalDate from = LocalDate.parse(request.getDateFrom());
    LocalDate to = LocalDate.parse(request.getDateTo());
    if (from.isAfter(to)) {
        throw new IllegalArgumentException("date_from must not be after date_to");
    }
    List<ScheduleItem> items = scheduleItemRepository
            .findByGroupIdAndSemesterIdAndIsActiveTrue(request.getGroupId(), request.getSemesterId());
    List<Long> itemIds = items.stream().map(ScheduleItem::getId).toList();
    // reuse existing: findByScheduleItemIdInAndDateBetweenAndStatusIn with all statuses
    List<Lesson> lessons = itemIds.isEmpty()
            ? List.of()
            : lessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn(
                    itemIds, from, to,
                    List.of("planned", "active", "closed", "cancelled"));
    Map<Long, ScheduleItem> itemMap = items.stream()
            .collect(Collectors.toMap(ScheduleItem::getId, i -> i));
    List<LessonResponse> responses = lessons.stream()
            .filter(l -> itemMap.containsKey(l.getScheduleItemId()))
            .map(l -> buildResponse(l, itemMap.get(l.getScheduleItemId())))
            .toList();
    responseObserver.onNext(LessonsResponse.newBuilder().addAllLessons(responses).build());
    responseObserver.onCompleted();
}
```

### Test StreamObserver verification
```java
// Source: D-06 decision + academic-service test pattern
@SuppressWarnings("unchecked")
StreamObserver<LessonResponse> mockObserver = mock(StreamObserver.class);
grpcService.getActiveLesson(request, mockObserver);
ArgumentCaptor<LessonResponse> captor = ArgumentCaptor.forClass(LessonResponse.class);
verify(mockObserver).onNext(captor.capture());
verify(mockObserver).onCompleted();
assertThat(captor.getValue().getId()).isEqualTo(expectedLesson.getId());
```

---

## Environment Availability

Step 2.6: SKIPPED — Phase 14 is a pure code change within the existing Spring Boot service. No new external dependencies beyond adding the gRPC server starter JAR from Maven Central, which Gradle resolves at build time. The existing `grpc-client-spring-boot-starter:3.1.0.RELEASE` confirms Maven Central connectivity is functional.

---

## Project Constraints (from CLAUDE.md)

These directives apply to all code written in this phase:

| Directive | Impact on Phase 14 |
|-----------|--------------------|
| Contract-first: `*-api-contract` has NO Lombok | `GrpcExceptionAdvice` and `ScheduleGrpcServiceImpl` live in `schedule-app` — Lombok is permitted there |
| Enum: `UPPER_CASE` in Java, `lowercase` in PostgreSQL | `lesson.getStatus().name().toLowerCase()` for proto string field |
| DB: native queries use `status::text` cast | All new native queries must use `l.status::text = 'active'` |
| `ddl-auto: validate` | No new DB tables/columns — not applicable |
| REST: HATEOAS, RFC 7807 | Not applicable — gRPC only, no new REST endpoints |
| Package: `ru.rutcampustrack.schedule.grpc` | All new gRPC classes in this package |
| Soft delete: never DELETE | Not applicable — read-only gRPC |
| `NEVER @Enumerated(EnumType.ORDINAL)` | Not applicable — no new entities |

---

## Validation Architecture

`workflow.nyquist_validation` is not set in `.planning/config.json` — treated as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers |
| Config file | `build.gradle.kts` (JUnit Platform Launcher in testRuntimeOnly) |
| Quick run command | `./gradlew :services:schedule-service:schedule-app:test --tests "ru.rutcampustrack.schedule.grpc.*"` |
| Full suite command | `./gradlew :services:schedule-service:schedule-app:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GRPC-01 | GetActiveLesson returns active lesson for group | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getActiveLesson_returnsActiveLesson"` | ❌ Wave 0 |
| GRPC-01 | GetActiveLesson returns NOT_FOUND when no active lesson | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getActiveLesson_notFound*"` | ❌ Wave 0 |
| GRPC-01 | GetActiveLesson returns first by lesson_number when multiple active (D-02) | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getActiveLesson_multipleActive_returnsFirstByLessonNumber"` | ❌ Wave 0 |
| GRPC-02 | GetLessonById returns lesson details | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getLessonById_*"` | ❌ Wave 0 |
| GRPC-02 | GetLessonById returns NOT_FOUND for missing ID | integration | same class | ❌ Wave 0 |
| GRPC-03 | GetLessonsByGroup returns lessons in date range | integration | `./gradlew :services:schedule-service:schedule-app:test --tests "*.ScheduleGrpcServiceImplTest.getLessonsByGroup_*"` | ❌ Wave 0 |
| GRPC-03 | GetLessonsByGroup returns INVALID_ARGUMENT when date_from > date_to (D-04) | integration | same class | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :services:schedule-service:schedule-app:test --tests "ru.rutcampustrack.schedule.grpc.*"`
- **Per wave merge:** `./gradlew :services:schedule-service:schedule-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplTest.java` — covers GRPC-01, GRPC-02, GRPC-03
- [ ] New native query method `findActiveLessonForGroup` in `LessonRepository` — required by GRPC-01 test

*(Existing test infrastructure: `AbstractScheduleIntegrationTest` covers all setup — no new framework install needed)*

---

## Open Questions

1. **GetLessonsByGroup: should inactive ScheduleItems be included?**
   - What we know: `scheduleItemRepository.findByGroupIdAndSemesterIdAndIsActiveTrue(...)` excludes deactivated templates; lessons derived from deactivated templates still exist in DB.
   - What's unclear: If a template was deactivated mid-semester, should its remaining lessons appear in the range query?
   - Recommendation: Use `findByGroupIdAndSemesterIdAndIsActiveTrue` for simplicity (matches Phase 11 VIEW endpoint behavior). If this causes missing lessons, it's a product decision deferred to v3.x.

2. **GetActiveLesson timestamp format**
   - What we know: Proto field is `string timestamp = 2; // ISO-8601`. No format constraints enforced by proto.
   - What's unclear: Whether Attendance Service will send `2026-04-04T10:30:00` (local) or `2026-04-04T10:30:00+03:00` (with offset).
   - Recommendation: Support both by trying `OffsetDateTime.parse` first, fall back to `LocalDateTime.parse`. Or document that callers must send Moscow local time without offset (simpler, consistent with how cron uses `LocalDateTime`).

---

## Sources

### Primary (HIGH confidence)
- `services/academic-service/academic-app/.../AcademicGrpcServiceImpl.java` — `@GrpcService` pattern, StreamObserver usage, null handling
- `services/academic-service/academic-app/.../GrpcExceptionAdvice.java` — `@GrpcAdvice` / `@GrpcExceptionHandler` pattern
- `services/schedule-service/schedule-app/build.gradle.kts` — existing dependency versions, proto codegen config
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — `grpc.server.port: 19092` confirmed present
- `services/schedule-service/schedule-app/src/test/resources/application-test.yml` — `grpc.server.port: -1` confirmed present
- `proto/schedule.proto` — all 3 RPCs, all message fields
- `services/schedule-service/schedule-app/.../LessonRepository.java` — `status::text` cast pattern, existing queries to compose
- `services/schedule-service/schedule-app/.../LessonStatusTransitionJobTest.java` — `@MockitoBean AcademicGrpcClient`, `@AfterEach` cleanup pattern
- `.planning/phases/14-grpc-server/14-CONTEXT.md` — locked decisions D-01 through D-06

### Secondary (MEDIUM confidence)
- `net.devh:grpc-spring-boot-starter` v3.1.0 docs: server starter co-existence with client starter on same application is the standard use case (verified by existing academic-service which runs both in the same build).

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions verified directly from `build.gradle.kts`; only one new line needed
- Architecture: HIGH — direct copy of academic-service pattern, all code read from source
- Repository queries: HIGH — native query pattern verified from 4 existing queries in `LessonRepository`
- Pitfalls: HIGH — all pitfalls derived from existing codebase code comments and test patterns
- Test strategy: HIGH — `AbstractScheduleIntegrationTest` pattern fully understood from source

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable internal codebase — only stale if dependencies upgrade)
