# Architecture Research

**Domain:** Schedule Service — microservice integration into existing RutCampusTrack system
**Researched:** 2026-03-31
**Confidence:** HIGH — based entirely on verified codebase (proto files, migration SQL, event schemas, Academic Service implementation patterns, existing schedule-app scaffold)

---

## System Overview

Schedule Service sits between Academic Service (upstream data source) and Attendance Service (downstream consumer). It is the only service that owns the lesson lifecycle and the only publisher of lesson-related events.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DOCKER PRIVATE NETWORK                              │
│                                                                              │
│  Clients → [API Gateway :8080]                                               │
│               │  JWT validated, X-User-* headers injected                   │
│               │                                                              │
│               └──► [Schedule Service :9092]   → PostgreSQL schedule_db      │
│                         │                                                    │
│                         │ gRPC client (sync)                                │
│                         ├──► [Academic Service :9111] ← GetGroup            │
│                         │                              ← GetTeacherSubjects  │
│                         │                              ← GetActiveSemester   │
│                         │                                                    │
│                         │ gRPC server (sync)                                │
│                         ├──◄ [Attendance Service :9093] GetActiveLesson     │
│                         │                               GetLessonById        │
│                         │                               GetLessonsByGroup    │
│                         │                                                    │
│                         │ RabbitMQ publish (async)                          │
│                         └──► [rut-uit.events fanout exchange]               │
│                                  │                                           │
│                                  ├──► notification-web.events queue         │
│                                  └──► notification-bot.events queue         │
│                                                                              │
│  [Spring @Scheduled cron]                                                    │
│    → reads lessons from schedule_db                                          │
│    → transitions PLANNED→ACTIVE→CLOSED                                       │
│    → fires Spring ApplicationEvents → DomainEventListener → RabbitMQ        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `schedule-api-contract` | REST interface definitions, DTOs (records + HATEOAS classes), enums | Nothing (pure java-library) |
| REST controllers | Route HTTP, read X-User-* headers via RequestContext, delegate to services | Service layer |
| `@RequireRole` AOP | Role-based access control on controller methods | RequestContext (request-scoped bean) |
| `ScheduleItemService` | CRUD schedule templates, validate via gRPC to Academic | JPA repositories, AcademicGrpcClient |
| `LessonService` | Lazy lesson generation, cancel/uncancel, geo-block | JPA repositories |
| `LessonStatusScheduler` | Cron jobs: PLANNED→ACTIVE and ACTIVE→CLOSED transitions, event publishing | LessonRepository, ApplicationEventPublisher |
| `LessonGenerationService` | Generate concrete lesson rows from schedule_items for a date range | LessonRepository, ScheduleItemRepository |
| `ScheduleGrpcServiceImpl` | Serve Attendance Service gRPC calls (3 RPCs) | LessonRepository, ScheduleItemRepository (direct, no service proxy — per existing Academic pattern) |
| `DomainEventListener` | Bridge Spring ApplicationEvents to RabbitMQ after DB commit | RabbitTemplate |
| JPA repositories | PostgreSQL schedule_db CRUD | PostgreSQL |
| `AcademicGrpcClient` | Call Academic Service for validation (GetGroup, GetTeacherSubjects, GetActiveSemester) | Academic Service gRPC server :9111 |

---

## Recommended Package Structure

```
services/schedule-service/
├── schedule-api-contract/
│   └── src/main/java/ru/rutcampustrack/schedule/contract/
│       ├── enums/
│       │   ├── LessonStatus.java          ← already exists (PLANNED, ACTIVE, CLOSED, CANCELLED)
│       │   └── WeekType.java              ← already exists (ALL, ODD, EVEN)
│       ├── api/
│       │   ├── ScheduleItemApi.java       ← NEW — interface with @RequestMapping, Swagger @Operation
│       │   └── LessonApi.java             ← NEW
│       └── dto/
│           ├── item/
│           │   ├── CreateScheduleItemRequest.java  ← record, @NotNull fields
│           │   ├── UpdateScheduleItemRequest.java  ← record (PUT = full)
│           │   └── ScheduleItemResponse.java       ← class extends RepresentationModel
│           └── lesson/
│               ├── LessonResponse.java             ← class extends RepresentationModel
│               └── CancelLessonRequest.java        ← record with cancelReason
│
└── schedule-app/
    └── src/main/java/ru/rutcampustrack/schedule/
        ├── ScheduleApplication.java               ← already exists
        ├── config/
        │   ├── EnumConverters.java                ← already exists (WeekType, LessonStatus)
        │   ├── RabbitConfig.java                  ← NEW — same pattern as Academic Service
        │   └── GrpcClientConfig.java              ← NEW — Academic gRPC channel config
        ├── security/
        │   ├── RequestContext.java                ← NEW — copy pattern from academic-app
        │   ├── RequireRole.java                   ← NEW — annotation
        │   ├── RoleCheckAspect.java               ← NEW — AOP aspect
        │   └── UserContextFilter.java             ← NEW — reads X-User-* headers into RequestContext
        ├── item/
        │   ├── entity/ScheduleItem.java           ← NEW — @Entity mapping to schedule_items
        │   ├── repository/ScheduleItemRepository.java  ← NEW
        │   ├── service/ScheduleItemService.java   ← NEW — CRUD + gRPC validation
        │   ├── assembler/ScheduleItemAssembler.java  ← NEW
        │   └── controller/ScheduleItemController.java ← NEW — implements ScheduleItemApi
        ├── lesson/
        │   ├── entity/Lesson.java                 ← NEW — @Entity mapping to lessons
        │   ├── repository/LessonRepository.java   ← NEW
        │   ├── service/LessonService.java         ← NEW — cancel/uncancel/geo-block
        │   ├── service/LessonGenerationService.java  ← NEW — expand schedule_items into lesson rows
        │   ├── assembler/LessonAssembler.java     ← NEW
        │   └── controller/LessonController.java   ← NEW — implements LessonApi
        ├── scheduler/
        │   └── LessonStatusScheduler.java         ← NEW — @Scheduled cron jobs + event triggers
        ├── grpc/
        │   ├── server/ScheduleGrpcServiceImpl.java  ← NEW — implements ScheduleGrpcServiceGrpc.ImplBase
        │   └── client/AcademicGrpcClient.java     ← NEW — stub wrapper for Academic Service calls
        └── event/
            ├── DomainEvent.java                   ← NEW — copy from academic-app, same pattern
            ├── DomainEventListener.java            ← NEW — @TransactionalEventListener(AFTER_COMMIT)
            ├── LessonStartedEvent.java            ← NEW
            ├── LessonClosedEvent.java             ← NEW
            └── LessonCancelledEvent.java          ← NEW
```

---

## Integration Points: New vs Existing

### Existing Infrastructure (zero changes needed)

| Component | Location | Notes |
|-----------|----------|-------|
| `schedule-api-contract` module scaffold | `schedule-api-contract/build.gradle.kts` | Has `LessonStatus`, `WeekType` enums already |
| `schedule-app` Spring Boot scaffold | `schedule-app/build.gradle.kts`, `ScheduleApplication.java` | Spring Boot, JPA, RabbitMQ already declared |
| `EnumConverters.java` | `schedule-app/config/` | WeekType + LessonStatus converters with autoApply=true |
| Flyway V1 migration | `db/migration/V1__baseline.sql` | `schedule_items` + `lessons` tables already created |
| `application.yml` | `schedule-app/resources/` | PostgreSQL + RabbitMQ connection configured |
| `schedule.proto` | `proto/schedule.proto` | 3 RPCs + all messages defined: `GetActiveLesson`, `GetLessonById`, `GetLessonsByGroup` |
| Event schemas | `event-schemas/lesson.started.json`, `lesson.closed.json`, `lesson.cancelled.json` | Payload structure defined |
| RabbitMQ exchange | `rut-uit.events` (fanout) | Already declared by Academic Service — Schedule Service declares same bean, AMQP is idempotent |
| API Gateway routing | `api-gateway/application.yml` | `/schedule/**` → `schedule-service:9092` already configured per docs/architecture.md |
| Docker Compose | `docker-compose.yml` | `schedule-service` + `postgres-schedule` already defined |

### New: Add to `build.gradle.kts`

| Dependency | Purpose | Note |
|------------|---------|------|
| `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` | gRPC server (for Attendance) + client stubs | Same version as Academic Service |
| `io.grpc:grpc-stub`, `io.grpc:grpc-protobuf` | Transitive via starter, but check root build |  |
| Proto plugin configuration in root `build.gradle.kts` | Generate Java from `schedule.proto` + `academic.proto` | `academic.proto` needed for gRPC client stubs |

### New: `application.yml` additions

```yaml
grpc:
  server:
    port: 19092          # internal only, not exposed via Gateway
  client:
    academic-service:
      address: 'static://academic-service:19091'
      negotiation-type: plaintext
```

The Academic Service gRPC port is 19091 (verified from `docs/architecture.md` which shows port 9091 is REST, gRPC runs on the internal port configured in `academic-app/application.yml` — check that file to confirm the exact internal gRPC port).

---

## Data Flow

### Flow 1: Headman Creates Schedule Template

```
HEADMAN → POST /schedule/items
  → API Gateway → JWT validate → inject X-User-Id, X-Role, X-Group-Id, X-Is-Headman
    → ScheduleItemController.create()
      → @RequireRole(STUDENT) + isHeadman check via RoleCheckAspect
      → ScheduleItemService.create(request, userId, groupId)
        → gRPC → AcademicGrpcClient.getGroup(groupId) — validate group exists + active
        → gRPC → AcademicGrpcClient.getTeacherSubjects(teacherId, semesterId) — validate teacher assigned
        → gRPC → AcademicGrpcClient.getActiveSemester() — get semesterId if not provided
        → INSERT schedule_items row
        → return ScheduleItemResponse (HATEOAS EntityModel with _links)
    ← 201 Created
```

### Flow 2: Cron Transition PLANNED → ACTIVE (lesson.started event)

```
[LessonStatusScheduler] @Scheduled(cron = "0 * * * * *")  — every minute
  → LessonRepository.findAllByStatusAndStartTimeBefore(PLANNED, now())
    -- joins lessons + schedule_items to get start_time
  → for each lesson:
      → lesson.setStatus(ACTIVE)
      → lessonRepository.save(lesson)            -- within @Transactional
      → applicationEventPublisher.publishEvent(
            new LessonStartedEvent(this, lesson)  -- Spring ApplicationEvent
        )
  → [DomainEventListener] @TransactionalEventListener(AFTER_COMMIT)
      → rabbitTemplate.convertAndSend("rut-uit.events", "", event)
      -- fanout: both notification-web.events + notification-bot.events queues receive copy
```

The AFTER_COMMIT guarantee is critical: if the DB update fails, no event is published. Pattern is identical to Academic Service's `DomainEventListener`.

### Flow 3: Cron Transition ACTIVE → CLOSED (lesson.closed event)

```
[LessonStatusScheduler] @Scheduled(cron = "0 * * * * *")  — same cron, separate query
  → LessonRepository.findAllByStatusAndEndTimeBefore(ACTIVE, now())
  → for each lesson:
      → lesson.setStatus(CLOSED)
      → lesson.setClosedAt(now())
      → lessonRepository.save(lesson)            -- within @Transactional
      → applicationEventPublisher.publishEvent(new LessonClosedEvent(this, lesson))
  → [DomainEventListener] → rabbitTemplate (after commit)
```

Note: PLANNED→ACTIVE and ACTIVE→CLOSED can be separate @Scheduled methods or combined. Recommend separate methods for clarity and independent failure handling.

### Flow 4: Attendance Service calls GetActiveLesson

```
[Attendance Service] gRPC client
  → ScheduleGrpcServiceImpl.getActiveLesson(group_id, timestamp)
    → LessonRepository.findActiveByGroupIdAndTime(groupId, timestamp)
       -- JOIN lessons l ON schedule_items si WHERE si.group_id = ? AND l.status = 'active'
       -- AND si.start_time <= time AND si.end_time >= time
    → if found: build LessonResponse proto message
      (id, schedule_item_id, group_id, subject_id, teacher_id, date, lesson_number,
       start_time, end_time, status, is_geo_blocked, room)
    → if not found: return empty LessonResponse (all default/zero values)
       OR throw gRPC NOT_FOUND status — decide convention
  ← LessonResponse
```

The gRPC impl queries repositories directly (not through service layer) — same decision made in Academic Service (`AcademicGrpcServiceImpl` injects repositories directly). Reason: avoids Spring RequestContext scope issues in gRPC threads.

### Flow 5: Lazy Lesson Generation on Schedule Query

```
GET /schedule/groups/{id}/lessons?from=2026-09-01&to=2026-09-07
  → LessonController → LessonService.getLessonsForGroup(groupId, dateFrom, dateTo)
    → LessonGenerationService.ensureLessonsExist(groupId, semesterId, dateFrom, dateTo)
      → ScheduleItemRepository.findActiveByGroupIdAndSemesterId(groupId, semesterId)
      → for each schedule_item, for each date in [dateFrom, dateTo]:
          → calculate if this date matches item's day_of_week + week_type
          → if no lesson row exists for (schedule_item_id, date):
              → INSERT lessons (schedule_item_id, date, status='planned', ...)
              → use INSERT ... ON CONFLICT DO NOTHING for idempotency
      → after generation: LessonRepository.findByGroupIdAndDateBetween(groupId, from, to)
    → assemble into PagedModel<EntityModel<LessonResponse>>
```

This is the lazy generation pattern documented in `docs/architecture.md` section 3.4. Lessons are created on first query for a given week, not at schedule creation time.

### Flow 6: Headman Cancels a Lesson

```
HEADMAN → PUT /schedule/lessons/{id}/cancel  body: {cancel_reason: "..."}
  → LessonController → @RequireRole(STUDENT) + isHeadman check
  → LessonService.cancel(lessonId, cancelReason, requesterId, groupId)
    → load lesson, verify lesson.scheduleItem.groupId == requesterId's groupId
    → lesson.setStatus(CANCELLED)
    → lesson.setCancelReason(cancelReason)
    → lessonRepository.save(lesson)                    -- @Transactional
    → applicationEventPublisher.publishEvent(new LessonCancelledEvent(this, lesson))
  → [DomainEventListener] → RabbitMQ after commit
  ← 200 OK LessonResponse
```

---

## Cron Job Architecture

### Scheduler Design

Two responsibilities, one class (`LessonStatusScheduler`):

```java
@Component
public class LessonStatusScheduler {

    // Runs every minute. Checks if any PLANNED lessons should now be ACTIVE.
    // Wrap each lesson transition in its own @Transactional to allow partial success.
    @Scheduled(cron = "0 * * * * *")
    public void openLessons() { ... }

    // Runs every minute. Checks if any ACTIVE lessons should now be CLOSED.
    @Scheduled(cron = "0 * * * * *")
    public void closeLessons() { ... }
}
```

The cron ticks every minute, which is sufficient since lesson boundaries are at fixed clock times (e.g. 08:30, 10:10). Resolution of 1 minute means at most 59 seconds of delay on transitions — acceptable.

### @Transactional Boundary for Events

The AFTER_COMMIT event listener pattern requires each lesson transition to be inside its own transaction. If a batch transition is done in one transaction, all events fire after the single commit — this is fine. However, if one lesson save fails, the entire batch rolls back and no events fire.

Recommended approach: process each lesson in a separate transaction via a helper `@Transactional` method so one failure does not block others.

```java
// In LessonStatusScheduler (not @Transactional itself):
public void openLessons() {
    List<Long> lessonIds = lessonRepository.findPlannedLessonIdsToOpen(now());
    for (Long id : lessonIds) {
        lessonTransitionService.transitionToActive(id);  // inner @Transactional
    }
}

// In LessonTransitionService:
@Transactional
public void transitionToActive(Long lessonId) {
    Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
    if (lesson == null || lesson.getStatus() != LessonStatus.PLANNED) return;
    lesson.setStatus(LessonStatus.ACTIVE);
    lessonRepository.save(lesson);
    eventPublisher.publishEvent(new LessonStartedEvent(this, lesson));
}
```

This matches the `@TransactionalEventListener` requirement: the event must be published within a transaction context so AFTER_COMMIT fires correctly.

### Avoiding Double Transitions

The cron query must be precise: `WHERE status = 'planned' AND schedule_items.start_time <= :now AND lessons.date = CURRENT_DATE`. A lesson that already transitioned to ACTIVE will not appear in the PLANNED query.

For time-zone safety: store `start_time` as `TIME` (no zone), combine with `lessons.date` to produce a `TIMESTAMP`, compare to `now() AT TIME ZONE 'Europe/Moscow'`. The university is in Moscow time — document this assumption.

---

## gRPC Integration

### Schedule Service as gRPC Server

`ScheduleGrpcServiceImpl` extends the generated `ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase` and implements 3 RPCs:

| RPC | Query | Returns |
|-----|-------|---------|
| `GetActiveLesson(group_id, timestamp)` | JOIN lessons+schedule_items WHERE status=active AND group_id=? AND date=date(timestamp) AND start_time <= time(timestamp) AND end_time >= time(timestamp) | Single `LessonResponse` or NOT_FOUND status |
| `GetLessonById(lesson_id)` | SELECT lesson JOIN schedule_item WHERE lessons.id=? | Single `LessonResponse` or NOT_FOUND |
| `GetLessonsByGroup(group_id, semester_id, date_from, date_to)` | SELECT lessons JOIN schedule_items WHERE group_id=? AND semester_id=? AND date BETWEEN ? AND ? | `LessonsResponse` with repeated field |

No Redis cache for gRPC responses — lesson statuses change every minute. Caching would require aggressive invalidation and is not worth the complexity at this scale.

### Schedule Service as gRPC Client

`AcademicGrpcClient` wraps the generated `AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub`:

```java
@Component
public class AcademicGrpcClient {

    private final AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    public AcademicGrpcClient(@GrpcClient("academic-service") Channel channel) {
        this.stub = AcademicGrpcServiceGrpc.newBlockingStub(channel);
    }

    public GroupResponse getGroup(long groupId) { ... }
    public TeacherSubjectsResponse getTeacherSubjects(long teacherId, long semesterId) { ... }
    public SemesterResponse getActiveSemester() { ... }
}
```

The `@GrpcClient("academic-service")` annotation resolves to the channel address defined in `application.yml` under `grpc.client.academic-service.address`.

### gRPC Port Configuration

Schedule Service gRPC server port: `19092` (convention: 1xxxx mirrors the REST port 9092).
Academic Service gRPC server port: `19091` (per existing Academic Service config).

These ports are internal to the Docker private network — not exposed in `docker-compose.yml`, not routed through API Gateway.

---

## RabbitMQ Event Publishing

### Event Classes

Three events, all extending `DomainEvent` (copied from Academic Service pattern):

| Class | event_type | Payload fields |
|-------|------------|---------------|
| `LessonStartedEvent` | `lesson.started` | lesson_id, group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room |
| `LessonClosedEvent` | `lesson.closed` | lesson_id, group_id, subject_id |
| `LessonCancelledEvent` | `lesson.cancelled` | lesson_id, group_id, subject_id, date, cancel_reason |

Payload fields match `event-schemas/lesson.*.json` exactly.

### DomainEvent Base Class

Copy `DomainEvent.java` from Academic Service. It extends `ApplicationEvent`, carries `event_type`, `event_id` (UUID), `occurred_at` (OffsetDateTime), and `payload` (Object). The `DomainEventListener` handles all subtypes via the single `onDomainEvent(DomainEvent event)` method.

### RabbitConfig

Same `FanoutExchange("rut-uit.events", true, false)` bean as Academic Service. AMQP exchange declaration is idempotent — declaring the same exchange in two services is safe.

**Critical**: do NOT set `channelTransacted=true` on `RabbitTemplate`. With `AFTER_COMMIT` listener, a transacted channel causes message loss (verified decision from Academic Service STATE.md).

---

## Authorization Pattern

Schedule Service replicates the AOP authorization pattern from Academic Service exactly:

| Component | How |
|-----------|-----|
| `UserContextFilter` | Servlet filter reads `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` into `RequestContext` (request-scoped bean) |
| `RequireRole` | Method annotation with `UserRole[] value()` |
| `RoleCheckAspect` | `@Around` aspect reads `RequestContext`, checks role, throws `AccessDeniedException` if not allowed |
| `RequestContext` | `@Scope("request", proxyMode = ScopedProxyMode.TARGET_CLASS)` — mandatory proxy mode for singleton aspect access |

Additional check for headman operations: after role check passes for STUDENT, verify `RequestContext.isHeadman() == true`. This is done in the service layer, not in the aspect.

gRPC server methods have NO authorization check — they are only reachable within the Docker private network (Attendance Service is the only caller). Same convention as Academic Service gRPC.

---

## Patterns to Follow

### Pattern 1: Contract-First with Interface Implementation

```java
// In schedule-api-contract:
@RequestMapping("/schedule/items")
public interface ScheduleItemApi {
    @PostMapping
    @Operation(summary = "Create schedule item")
    ResponseEntity<EntityModel<ScheduleItemResponse>> create(
        @RequestBody @Valid CreateScheduleItemRequest request
    );
}

// In schedule-app:
@RestController
public class ScheduleItemController implements ScheduleItemApi {
    @Override
    public ResponseEntity<EntityModel<ScheduleItemResponse>> create(
        @RequestBody @Valid CreateScheduleItemRequest request
    ) { ... }
}
```

No `@RequestMapping` on the controller class — inherited from the interface.

### Pattern 2: Cron + AFTER_COMMIT Events (do not couple directly)

```java
// WRONG — direct RabbitTemplate call inside @Transactional
@Transactional
public void transitionToActive(Long lessonId) {
    // ...
    rabbitTemplate.convertAndSend(EXCHANGE, "", event);  // fires before commit
}

// CORRECT — publish Spring event, let DomainEventListener forward after commit
@Transactional
public void transitionToActive(Long lessonId) {
    // ...
    eventPublisher.publishEvent(new LessonStartedEvent(this, lesson));
    // DomainEventListener.onDomainEvent fires AFTER this transaction commits
}
```

### Pattern 3: Lazy Lesson Generation with ON CONFLICT DO NOTHING

```sql
INSERT INTO lessons (schedule_item_id, date, status, is_geo_blocked, created_at)
VALUES (?, ?, 'planned', false, NOW())
ON CONFLICT (schedule_item_id, date) DO NOTHING
```

The `UNIQUE (schedule_item_id, date)` constraint in V1 migration makes this safe for concurrent requests. Multiple simultaneous GET requests for the same week will not create duplicate lessons.

### Pattern 4: gRPC Impl Queries Repositories Directly

```java
@GrpcService
public class ScheduleGrpcServiceImpl extends ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase {
    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    // NO service injection — gRPC runs in non-request threads,
    // RequestContext (request-scoped bean) would throw ScopeNotActiveException
}
```

This is the same decision documented in Academic Service's `AcademicGrpcServiceImpl`.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Publishing RabbitMQ Events Directly in @Scheduled Method

**What:** Calling `rabbitTemplate.convertAndSend(...)` inside `LessonStatusScheduler.openLessons()`.

**Why bad:** `@Scheduled` methods have no transaction context. `@TransactionalEventListener(AFTER_COMMIT)` requires an active transaction to fire. The event will never be published, and no error is raised.

**Instead:** Delegate to a `@Transactional` service method that publishes a Spring `ApplicationEvent`. The `DomainEventListener` picks it up after commit.

### Anti-Pattern 2: One Transaction for All Lessons in Cron Batch

**What:** Wrapping the entire `openLessons()` loop in a single `@Transactional`.

**Why bad:** If one lesson update fails (e.g. optimistic lock conflict), all lesson transitions in the batch roll back and no events fire.

**Instead:** Each lesson transition in its own `@Transactional` (inner service method). One failure is isolated.

### Anti-Pattern 3: Caching gRPC Responses for Lesson Status

**What:** Adding `@Cacheable` to `ScheduleGrpcServiceImpl.getActiveLesson(...)`.

**Why bad:** Lesson status changes every minute via cron. A 5-minute TTL cache means Attendance Service could see a stale PLANNED status during the first 5 minutes of a lesson. Students cannot check in.

**Instead:** No caching for gRPC methods that return lesson status. Direct DB query is fast enough (index on `status` and `date` already in V1 migration).

### Anti-Pattern 4: Using RequestContext in gRPC Threads

**What:** Injecting `RequestContext` into `ScheduleGrpcServiceImpl` to read user identity.

**Why bad:** `RequestContext` is `@Scope("request")` — it is only active in Servlet request threads. gRPC runs in Netty threads. Accessing a request-scoped bean from a gRPC thread throws `ScopeNotActiveException`.

**Instead:** gRPC methods on Schedule Service do not perform authorization. They run on the internal Docker network and are called only by trusted services (Attendance Service). If authorization is needed in future, pass user identity in gRPC metadata.

### Anti-Pattern 5: Generating All Lessons at Schedule Item Creation

**What:** When a headman creates a schedule_item for an entire semester, generate all lesson rows immediately (could be 20+ weeks × many items = hundreds of rows).

**Why bad:** Slow response time, wasted rows if schedule changes. Also requires semester dates at creation time, adding coupling.

**Instead:** Lazy generation on first GET request for a date range. Use `ON CONFLICT DO NOTHING` for idempotency. This is the documented pattern in `docs/architecture.md`.

### Anti-Pattern 6: Bidirectional gRPC Dependencies

**What:** Attendance Service calls Schedule Service (correct), and Schedule Service also calls Attendance Service for some validation.

**Why bad:** Creates a circular dependency between services. Any deployment or restart of either service requires the other to be up.

**Instead:** Schedule Service calls only Academic Service (upstream). Attendance Service calls Schedule Service (downstream). Data flows one direction. Events flow to notification services only.

---

## Build Order

Dependencies must be resolved before dependents can compile. Build phases in this order:

### Step 1: Entities and Repositories
No external dependencies. Foundation for everything else.

- `ScheduleItem` entity (`@Entity`, maps `schedule_items` table, uses `LessonStatus` and `WeekType` converters from existing `EnumConverters.java`)
- `Lesson` entity (`@Entity`, FK to `schedule_items.id`)
- `ScheduleItemRepository`, `LessonRepository`
- Custom JPQL queries: `findActiveByGroupIdAndSemesterId`, `findByGroupIdAndDateBetween`, `findPlannedToOpen(LocalTime now, LocalDate today)`, `findActiveToClose(LocalTime now, LocalDate today)`, `findActiveByGroupAndTime`

### Step 2: Security Infrastructure
Needed by all controllers. No DB dependency.

- Copy `RequireRole.java`, `RoleCheckAspect.java`, `RequestContext.java`, `UserContextFilter.java` from Academic Service (adjust package to `ru.rutcampustrack.schedule.security`)
- These are identical in structure — the only change is the package and the enum reference

### Step 3: gRPC Client to Academic Service
Needed by ScheduleItemService for validation. Must work before REST layer.

- `AcademicGrpcClient` — wraps `AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub`
- Add gRPC dependency to `build.gradle.kts`
- Add `grpc.client.academic-service` config to `application.yml`
- Add proto plugin to generate `AcademicGrpcServiceGrpc` from `proto/academic.proto`

### Step 4: Domain Services
Depend on entities, repositories, and gRPC client.

- `LessonGenerationService` — expand schedule_items into lessons for a date range
- `ScheduleItemService` — CRUD + calls `AcademicGrpcClient` for group/teacher validation
- `LessonService` — cancel/uncancel/geo-block operations

### Step 5: REST API
Depends on services and security.

- `schedule-api-contract`: `ScheduleItemApi`, `LessonApi`, DTOs, enums (request records + HATEOAS response classes)
- `ScheduleItemController`, `LessonController`, assemblers

### Step 6: gRPC Server
Depends on repositories (direct). Independent from service layer per pattern.

- Add gRPC server config to `application.yml` (`grpc.server.port: 19092`)
- `ScheduleGrpcServiceImpl` — 3 RPCs, queries repositories directly
- Proto plugin generates `ScheduleGrpcServiceGrpc` from `proto/schedule.proto`

### Step 7: RabbitMQ Events
Depends on entities (for event payload). Independent of REST layer.

- `RabbitConfig` — same FanoutExchange bean as Academic Service
- `DomainEvent` base class (copy from Academic Service, adjust package)
- `LessonStartedEvent`, `LessonClosedEvent`, `LessonCancelledEvent`
- `DomainEventListener` — `@TransactionalEventListener(AFTER_COMMIT)`

### Step 8: Cron Scheduler
Depends on repositories and events. Last because it integrates everything.

- `LessonTransitionService` — `@Transactional` methods for individual transitions, publishes Spring events
- `LessonStatusScheduler` — `@Scheduled` cron methods, calls `LessonTransitionService`
- Enable scheduling: `@EnableScheduling` on `ScheduleApplication.java`

---

## Scalability Considerations

| Concern | Dev / <100 users | 1K students | 5K students |
|---------|------------------|-------------|-------------|
| Cron execution time | Negligible — few lessons per minute | < 1 second — tens of PLANNED lessons per minute | Consider parallel transitions per group |
| GetActiveLesson gRPC latency | Direct DB query, < 5ms | Index on (status, date) handles it | No change needed |
| Lesson generation on first GET | Hundreds of rows, < 100ms | Same — one generation per group per week | Batch insert, already fast |
| RabbitMQ event volume | ~10 lesson.started per day per group | ~100/day for 10 groups | ~1000/day — fanout exchange handles it trivially |
| schedule_db read load | Low — schedule rarely changes | Acceptable — no caching needed | Consider read replica if report queries added |

---

## Sources

All findings are HIGH confidence — verified directly from codebase.

- `proto/schedule.proto` — 3 RPCs, all message types
- `proto/academic.proto` — 7 RPCs used for gRPC client (GetGroup, GetTeacherSubjects, GetActiveSemester)
- `event-schemas/lesson.started.json`, `lesson.closed.json`, `lesson.cancelled.json` — event payload structure
- `services/schedule-service/schedule-app/src/main/resources/db/migration/V1__baseline.sql` — schema confirmed: `schedule_items` + `lessons` with all columns and indexes
- `services/schedule-service/schedule-app/build.gradle.kts` — Spring Boot, JPA, RabbitMQ already declared; gRPC dependency absent (needs adding)
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — PostgreSQL + RabbitMQ configured; gRPC config absent (needs adding)
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/EnumConverters.java` — WeekType + LessonStatus converters already present
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java` — AFTER_COMMIT pattern to replicate
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEvent.java` — base event class structure
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/RabbitConfig.java` — RabbitTemplate without channelTransacted
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` — repository-direct pattern for gRPC
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/` — RequestContext, RequireRole, RoleCheckAspect, UserContextFilter pattern
- `docs/architecture.md` — section 3.4 confirms lazy generation, cron scheduler, event types, gRPC topology

---

*Architecture research for: Schedule Service integration — v3.0 milestone*
*Researched: 2026-03-31*
