# Pitfalls Research

**Domain:** Attendance Service MVP — MongoDB-based attendance tracking, first RabbitMQ consumer, gRPC clients to Schedule + Academic, geo-validation, auto-absent on lesson.closed, domain isolation (checkin/ vs report/)
**Researched:** 2026-04-04
**Confidence:** HIGH (verified against actual codebase, existing service patterns, proto contracts, schedule service implementation, database schema)

---

## Critical Pitfalls

### Pitfall 1: MongoDB Indexes Not Created — Unique Constraint Missing at Runtime

**What goes wrong:**
The `attendances` collection requires a unique index on `{lesson_id: 1, user_id: 1}` to guarantee idempotency (one record per student per lesson). Without this index, concurrent geo-checkin requests or RabbitMQ consumer retries insert duplicate documents silently. Auto-absent bulk inserts create duplicate `absent` records. Reports then show duplicated absence counts.

**Why it happens:**
Spring Data MongoDB disables `auto-index-creation` by default since version 3.0. The `@Indexed` / `@CompoundIndex` annotations on `@Document` classes are silently ignored unless `spring.data.mongodb.auto-index-creation=true` is set OR `autoIndexCreation()` is overridden in a `MongoConfig`. Developers familiar with Hibernate (`ddl-auto: validate`) expect the schema to be enforced; MongoDB has no equivalent enforcement at startup.

**How to avoid:**
Create a `MongoIndexInitializer` component that runs on `ApplicationReadyEvent` and creates indexes programmatically using `MongoTemplate.indexOps()`:

```java
@Component
public class MongoIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private final MongoTemplate mongoTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        mongoTemplate.indexOps("attendances")
            .ensureIndex(new CompoundIndexDefinition(
                new Document("lesson_id", 1).append("user_id", 1))
                .unique());
        // create all other indexes here
    }
}
```

Do NOT rely on `@CompoundIndex` annotations alone without verifying `auto-index-creation` is enabled. Do NOT enable auto-index-creation in production without understanding that index creation blocks the collection on MongoDB 4.x.

**Warning signs:**
- Collection exists but `db.attendances.getIndexes()` only shows `_id` index
- Duplicate attendance records for same `(lesson_id, user_id)` pair after retry
- `MongoWriteException: duplicate key` never thrown even when sending identical check-in twice

**Phase to address:**
Infrastructure / MongoDB setup phase — write index initialization before any service logic. Add a test that verifies the unique index exists by attempting two identical inserts and expecting `DuplicateKeyException`.

---

### Pitfall 2: RabbitMQ Consumer Queue Not Bound — lesson.closed Events Silently Dropped

**What goes wrong:**
The Attendance Service is the first RabbitMQ consumer in this system. The Schedule Service publishes to the `rut-uit.events` fanout exchange with no routing key. If the Attendance Service queue (`attendance-service.events`) is not declared and bound to the exchange before the first `lesson.closed` event arrives, that event is lost forever — fanout exchange drops messages with no bound queues. Auto-absent never fires for any lesson that closed during service startup.

**Why it happens:**
The existing `RabbitConfig` in Schedule Service only declares the exchange (publisher side). Fanout exchanges do not store messages — they route to bound queues only. If no queue is bound when the event is published, the message is gone. Unlike topic exchanges with persistent queues, there is no "replay" mechanism. Developers who only look at the publisher config assume consumers will receive messages retroactively.

**How to avoid:**
The Attendance Service `RabbitConfig` must declare its own durable queue AND the binding to the fanout exchange:

```java
@Bean
public Queue attendanceEventsQueue() {
    return QueueBuilder.durable("attendance-service.events").build();
}

@Bean
public Binding attendanceEventsBinding(Queue attendanceEventsQueue,
                                        FanoutExchange eventsExchange) {
    return BindingBuilder.bind(attendanceEventsQueue).to(eventsExchange);
}
```

The `FanoutExchange` bean for `rut-uit.events` must also be declared in the Attendance Service config (idempotent — RabbitMQ ignores re-declarations of existing exchanges with same parameters). Start Attendance Service before running any cron that transitions lessons to CLOSED in integration tests.

**Warning signs:**
- RabbitMQ Management UI shows `rut-uit.events` exchange with zero bindings after Attendance Service starts
- Auto-absent never triggers even when `lesson.closed` events are published
- No `@RabbitListener` method ever fires in logs

**Phase to address:**
RabbitMQ consumer infrastructure phase — declare queue + binding as first step before writing the consumer logic. Add a smoke test: publish a synthetic `lesson.closed` event and verify the listener method is invoked.

---

### Pitfall 3: Auto-Absent Race Condition — Late Check-In After lesson.closed Event

**What goes wrong:**
A student submits geo-checkin (POST `/attendance/check-in`) at `t=0`. The request is in-flight. The cron job fires at `t=1ms`, transitions the lesson to CLOSED, publishes `lesson.closed`. The Attendance Service consumer receives the event at `t=2ms` and runs auto-absent: queries all group members via gRPC, bulk-inserts `absent` records for everyone without an existing record. The unique index on `{lesson_id, user_id}` blocks the student's absent insert (good). But then the check-in request completes at `t=3ms` and upserts `present` — overwriting nothing because no record existed when the `absent` insert was blocked. Result: student gets `absent` even though they checked in before the lesson closed.

**Why it happens:**
The `lesson.closed` event fires AFTER the Schedule Service transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`). There is a window between the lesson status changing to CLOSED in PostgreSQL and the consumer processing the event. Geo-checkin validation calls `GetActiveLesson` gRPC which checks `status = 'ACTIVE'` — if the lesson transitioned to CLOSED before the check-in gRPC call returns, the checkin is rejected. But if the check-in's gRPC call ran before the CLOSED transition and the checkin request is still processing when the consumer fires, both operations race to insert the attendance record.

**How to avoid:**
Use an atomic upsert in the auto-absent step that only sets absent if no record exists for the student:

```java
// Only insert absent if no record exists — never overwrite present/excused
mongoTemplate.upsert(
    Query.query(Criteria.where("lesson_id").is(lessonId)
                        .and("user_id").is(userId)
                        .and("status").doesNotExist()),  // no status field means no record
    new Update()
        .setOnInsert("status", "absent")
        .setOnInsert("marked_by", "auto_scheduler")
        .setOnInsert("created_at", Instant.now()),
    AttendanceRecord.class
);
```

The `$setOnInsert` operator only writes fields when the operation results in an insert, not an update. This is atomic at the MongoDB document level. Additionally, validate that the lesson is still ACTIVE at the start of `CheckInService` (not just when the gRPC call returns) using the `lesson_id` from the response, and reject if status != `active`.

**Warning signs:**
- Students report being marked absent even though they checked in while lesson was ongoing
- Attendance records show `marked_by: auto_scheduler` for students who have `checkin_location` data in the same record
- Race window increases under load (gRPC call to Academic Service for geofence takes >100ms)

**Phase to address:**
Auto-absent consumer phase — use `$setOnInsert` from the start, never a plain insert. Write a concurrency test: submit check-in and lesson.closed event simultaneously and assert the result is `present`, not `absent`.

---

### Pitfall 4: @Enumerated Is JPA-Only — MongoDB Stores Wrong String Values

**What goes wrong:**
`AttendanceRecord` entity uses `AttendanceStatus` enum with `@Enumerated(EnumType.STRING)` (copied from the JPA pattern used in Schedule/Academic services). Spring Data MongoDB ignores `@Enumerated` entirely. Enums are serialized using Jackson's default behavior: `UPPER_CASE` name (e.g., `"PRESENT"`, `"AUTO_SCHEDULER"`). Documents in MongoDB contain `"PRESENT"` instead of `"present"`. Report queries filtering by `status = "present"` return nothing. The unique constraint and existing documents in other dev environments have lowercase strings.

**Why it happens:**
The codebase already uses `LowercaseEnumConverter` with `autoApply=true` for JPA — this is a `javax.persistence.AttributeConverter` and has zero effect on MongoDB. The MongoDB `MappingMongoConverter` uses Jackson by default for enum serialization. Developers copy the `@Enumerated` annotation out of habit and assume it works the same way.

**How to avoid:**
Register a global `MongoCustomConversions` bean that converts all enums to lowercase strings:

```java
@Bean
public MongoCustomConversions mongoCustomConversions() {
    return MongoCustomConversions.create(config -> {
        config.registerConverter(new AttendanceStatusWriteConverter());
        config.registerConverter(new AttendanceStatusReadConverter());
        // repeat for each enum used in MongoDB documents
    });
}
```

Alternatively, annotate enum fields with `@Field("status")` and use `@ValueConverter` (Spring Data MongoDB 3.3+):

```java
@Field("status")
@ValueConverter(AttendanceStatusConverter.class)
private AttendanceStatus status;
```

Store ALL enum values as lowercase in MongoDB to match the project convention. Never use `@Enumerated` on MongoDB `@Document` classes — remove it on sight.

**Warning signs:**
- `db.attendances.find({status: "present"})` returns zero documents when records exist
- `db.attendances.find({status: "PRESENT"})` returns documents (wrong case in DB)
- Report aggregate queries always return empty results

**Phase to address:**
MongoDB entity / document mapping phase — write a mapping test before any service logic. The test inserts a record with status `PRESENT` Java enum and asserts the stored MongoDB document contains `"present"` (lowercase string).

---

### Pitfall 5: GetLessonsByGroup Includes CANCELLED Lessons — Auto-Absent Marks Wrong Students

**What goes wrong:**
The auto-absent flow for historical lesson reports calls `GetLessonsByGroup` to find closed lessons for a group. The gRPC implementation (confirmed in `ScheduleGrpcServiceImpl`) returns ALL statuses including `cancelled`. The auto-absent consumer iterates over the response and attempts to create absent records for cancelled lessons. This pollutes the `attendances` collection with `absent` records for lessons that never happened. Reports show incorrect absent counts for cancelled lessons (which should have zero effect on statistics per business rules).

**Why it happens:**
Known tech debt: `GetLessonsByGroup` does not filter by status in the gRPC server code. The query passes all four statuses explicitly: `List.of("planned", "active", "closed", "cancelled")`. This was acceptable for the Schedule-side view (showing all lesson statuses in calendar), but the Attendance Service consumer must not auto-absent for cancelled lessons.

**How to avoid:**
The Attendance Service consumer MUST filter the gRPC response before processing:

```java
List<LessonResponse> closedLessons = response.getLessonsList().stream()
    .filter(l -> "closed".equals(l.getStatus()))
    .toList();
```

Never trust that a gRPC response filtered upstream — always apply business-rule filters on the consumer side. Document this as a known tech debt: the gRPC server should accept a `status_filter` field in the request, but until it does, client-side filtering is mandatory.

**Warning signs:**
- `attendances` collection contains records with `lesson_id` values that correspond to cancelled lessons
- Report stats show absent counts for lessons flagged as `status: cancelled` in the schedule
- `marked_by: auto_scheduler` records for lesson IDs that have zero `present`/`excused` records (indicator of mass ghost absents)

**Phase to address:**
RabbitMQ consumer / auto-absent phase — add explicit status filter as the first line of the lesson processing loop. Add integration test that verifies no attendance record is created for a cancelled lesson.

---

### Pitfall 6: IllegalArgumentException from Schedule gRPC Becomes HTTP 500 at Consumer

**What goes wrong:**
`ScheduleGrpcServiceImpl.getLessonsByGroup()` throws `IllegalArgumentException` when `date_from` is after `date_to`. This exception is not mapped to a gRPC status code in the Schedule Service `GrpcExceptionAdvice`. The gRPC call from Attendance Service receives a generic `UNKNOWN` status instead of `INVALID_ARGUMENT`. The Attendance Service catches this as a generic `StatusRuntimeException` with `UNKNOWN` status and rethrows as HTTP 500. The real cause is invisible in Attendance Service logs.

**Why it happens:**
Known tech debt in Schedule Service: `IllegalArgumentException` → `HTTP 500` in the REST layer. The same gap exists in the gRPC layer: without explicit `@GrpcExceptionHandler` for `IllegalArgumentException`, it propagates as `UNKNOWN`. The Attendance Service has no reason to send invalid date ranges in normal operation, but defensive programming requires handling this case gracefully.

**How to avoid:**
In Attendance Service gRPC clients, catch `StatusRuntimeException` and check `status.getCode()`:

```java
try {
    return scheduleStub.getLessonsByGroup(request);
} catch (StatusRuntimeException e) {
    if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
        return LessonsResponse.getDefaultInstance();
    }
    log.error("Schedule gRPC call failed: status={}, description={}",
              e.getStatus().getCode(), e.getStatus().getDescription());
    throw new ScheduleServiceUnavailableException(e);
}
```

Log gRPC status code and description — do not swallow or rethrow blindly. Separately, add `IllegalArgumentException` → `INVALID_ARGUMENT` mapping to Schedule Service `GrpcExceptionAdvice` (tech debt fix in a later phase).

**Warning signs:**
- HTTP 500 responses from Attendance Service with no useful error message
- gRPC call logs show `Status{code=UNKNOWN, description=null}` from Schedule Service
- Auto-absent or report endpoints fail with generic 500 for date boundary conditions

**Phase to address:**
gRPC client wrappers phase — write a defensive gRPC client layer before writing any consumer that calls Schedule Service. Add a test with mocked gRPC stubs that verifies graceful handling of `UNKNOWN` status.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| `auto-index-creation=true` in dev profile | Zero-config index management | Startup time grows with data; index creation blocks collection on MongoDB 4.x | Dev/test only, never production |
| Skip DLQ configuration for lesson.closed consumer | Simpler RabbitMQ config | Failed auto-absent silently drops, students permanently marked absent incorrectly | Never — DLQ is required even for MVP |
| Blocking gRPC stub (`..BlockingStub`) for all calls | Simpler code, no async complexity | Blocks thread during gRPC call; under load pins request threads | Acceptable for MVP; plan non-blocking for v5+ |
| Copying attendance data (semester_id, group_id, subject_id) from gRPC response into MongoDB document | Fast denormalized reads for reports | If academic data changes (group rename, semester activation), MongoDB documents have stale metadata | Acceptable — documents are write-once attendance facts, stale display metadata is acceptable |
| No MongoDB transactions for checkin + event publish | Simpler, no replica set requirement in dev | checkin saved but event not published = silent uncounted attendance | Acceptable if event publish is fire-and-forget; NOT acceptable if audit trail required |
| `@Profile("!test")` to disable cron — also blocks consumer | Cron-free tests | RabbitMQ listener container must still run in tests to verify consumer logic; cannot disable both with one profile | Separate profiles: `@Profile("!test")` for cron only, consumer always active |

---

## Integration Gotchas

Common mistakes when connecting to external services.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Schedule Service gRPC (GetActiveLesson) | Assume `NOT_FOUND` means "no lesson today" — throw 400 to student | `NOT_FOUND` means lesson not in ACTIVE status for group; return `409 CONFLICT` with `no_active_lesson` error code so client knows to show "no active lesson" UI state |
| Academic Service gRPC (GetCampusGeofence) | Call on every checkin request (gRPC round trip per checkin) | Geofence changes rarely — cache response in-memory or Redis with 60-minute TTL; serve from cache for checkin validation |
| Academic Service gRPC (GetGroupMembers) | Call for every auto-absent event; no caching | Cache group members per `group_id` in-memory for the duration of auto-absent processing; one gRPC call per group per `lesson.closed` event |
| RabbitMQ fanout exchange (rut-uit.events) | Declare the exchange bean in Attendance Service with `autoDelete=true` (copied from wrong examples) | Exchange must be `durable=false, autoDelete=false` — must match Schedule Service declaration exactly or RabbitMQ throws `inequivalent arg` error on second declaration |
| MongoDB upsert (check-in idempotency) | Use `save()` with existing `_id` (overwrites status back to present on re-submit) | Use `upsert()` with `setOnInsert()` for the immutable fields; use `$set` only for fields that are safe to update (like `updated_at`) |
| gRPC channel to Schedule Service | Hard-code `localhost:19092` (works on developer machine, fails in Docker) | Use `grpc.client.schedule-service.address=static://schedule-service:19092` in application.yml, resolved via Docker service name |

---

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Report query loads all attendance records in memory then groups by student | Report endpoint takes 5+ seconds for group with 25 students over one semester (~500 records × N lessons) | Use MongoDB aggregation pipeline with `$group` on server side; never `.findAll()` for report data | ~100+ students or multi-semester reports |
| Auto-absent calls `GetGroupMembers` inside `lesson.closed` consumer for every event, including rapid bursts | gRPC call storm to Academic Service when many lessons close at same time (end of day cron cycle) | Cache group members with short TTL; debounce or batch process if multiple lessons close for same group simultaneously | 5+ groups closing at same time |
| Geo-checkin calls both `GetActiveLesson` AND `GetCampusGeofence` sequentially (two blocking gRPC calls per request) | Checkin latency = gRPC(Schedule) + gRPC(Academic) + MongoDB write; >200ms per checkin under load | Parallelize with `CompletableFuture` or cache geofence; geofence data changes < once per semester | 50+ concurrent student checkins at lesson start |
| MongoDB unique index on `{lesson_id, user_id}` not declared — falls back to application-level duplicate check | Inconsistent duplicate prevention under concurrent load; two inserts race between check and insert | Unique index at MongoDB level is the only reliable guard; application-level check is complementary, not sufficient | Any concurrent load (even 2 users) |
| `GetLessonsByGroup` returns entire semester worth of lessons for auto-absent check | Response size grows with semester length; at end of semester = 100+ lessons per group per gRPC call | Use `GetLessonById` for single-lesson auto-absent (pass `lesson_id` from `lesson.closed` event payload) | Second half of semester (50+ lessons) |

---

## Security Mistakes

Domain-specific security issues beyond general web security.

| Mistake | Risk | Prevention |
|---------|------|------------|
| Checkin accepts any `{lat, lng}` from request body without validating against known-good campus location | Student submits fake coordinates (0.0, 0.0 or any non-campus location); gets `present` status fraudulently | Validate distance using Haversine formula: `distance(studentLat, studentLng, campusLat, campusLng) <= campus.radius_m`; reject if outside geofence |
| Checkin does not verify student belongs to the group the active lesson is for | Student in group A checks in to lesson for group B (by guessing `lesson_id`) | `GetActiveLesson(group_id, timestamp)` where `group_id` comes from `X-Group-Id` JWT header (injected by Gateway), not from request body; student cannot supply a different group |
| Headman manual marking does not verify the student being marked is in the headman's group | Headman marks student from another group | Verify `student_group_id == headman_group_id` using `GetGroupMembers` gRPC before inserting record |
| Attendance record contains raw GPS coordinates stored in MongoDB | Privacy risk — exact device location persisted indefinitely | Store `distance_from_campus_m` (integer) instead of raw lat/lng; or store with explicit data retention policy. At minimum, round coordinates to 3 decimal places (~111m resolution) |
| RabbitMQ consumer processes `lesson.closed` without verifying the `lesson_id` belongs to a real lesson | Crafted AMQP message causes auto-absent for non-existent lesson_id | Verify via `GetLessonById` gRPC call before processing; discard if `NOT_FOUND` |

---

## UX Pitfalls

Common user experience mistakes in this domain.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Checkin API returns generic "error" when student is outside geofence | Student doesn't know if they're in the wrong location or the lesson hasn't started | Return specific error code with distance: `{"error": "outside_geofence", "distance_m": 450, "allowed_m": 200}` |
| Checkin API returns "no active lesson" when lesson is in 5-minute pre-start window | Student physically at campus 4 minutes early gets rejected; tries multiple times | Respect 5-minute pre-start window: `GetActiveLesson` should match lessons where `start_time - 5min <= now <= end_time + 5min` |
| Manual marking by headman returns 200 but doesn't confirm the new status | Headman clicks multiple times per student thinking click didn't register | Response must include the resulting attendance record with status and `marked_by` so the UI can update immediately |
| Auto-absent fires for ALL unmarked students including those on geo-blocked lessons | Students locked out of geo-marking by headman receive `absent` instead of `present` | Check `is_geo_blocked` flag from lesson; if geo-blocked, skip auto-absent for students who have NO record (manual marking is the only valid source) |

---

## "Looks Done But Isn't" Checklist

Things that appear complete but are missing critical pieces.

- [ ] **Geo-checkin:** Appears to work in dev (localhost gRPC stubs) — verify geofence comparison uses correct Earth radius (6371 km) and the right formula; test with coordinates exactly at radius boundary.
- [ ] **Auto-absent:** Consumer fires and inserts `absent` records — verify it SKIPS students who already have ANY attendance record (not just `present`); verify it skips cancelled lessons; verify it uses `$setOnInsert` not plain insert.
- [ ] **Domain isolation:** Package structure has `checkin/` and `report/` directories — verify ArchUnit test (`reportDoesNotAccessCheckinInternals`) is actually in the test classpath and fails when a direct import is added. The test must run, not just compile.
- [ ] **MongoDB indexes:** Collection exists and writes succeed — verify unique index on `{lesson_id, user_id}` exists by running `db.attendances.getIndexes()` in integration test or Mongo shell; a missing index won't cause failures until concurrent requests arrive.
- [ ] **RabbitMQ queue:** Consumer `@RabbitListener` receives events in unit test with mocked RabbitMQ — verify actual queue binding exists in Testcontainers integration test by publishing a real event and asserting the listener method was called.
- [ ] **Enum serialization:** Records save and load without error — verify the stored MongoDB document (raw BSON) contains lowercase strings (`"present"`, `"auto_scheduler"`), not uppercase enum names.
- [ ] **gRPC client wrappers:** Calls succeed in happy path — verify the client handles `NOT_FOUND` (no active lesson) and `UNAVAILABLE` (service down) without propagating `NullPointerException` or HTTP 500.
- [ ] **Cancelled lesson filtering:** GetLessonsByGroup consumer filters work — verify by inserting a cancelled lesson in test data and asserting no `absent` record is created for it.

---

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Missing unique index causes duplicate attendance records | MEDIUM | Run deduplication script: keep record with `present` status; delete duplicates with `absent`; re-create unique index with `db.attendances.createIndex({lesson_id:1, user_id:1}, {unique:true})` |
| Wrong enum case stored (UPPERCASE in MongoDB) | MEDIUM | Run migration script: `db.attendances.updateMany({status:{$in:["PRESENT","ABSENT","EXCUSED"]}}, [{$set:{status:{$toLower:"$status"}}}])` |
| Auto-absent created records for cancelled lessons | LOW | Delete records: `db.attendances.deleteMany({marked_by:"auto_scheduler", lesson_id:{$in:[<cancelled_lesson_ids>]}})` |
| lesson.closed events lost during startup (queue not bound) | HIGH | No recovery — events are gone. Trigger manual re-run of auto-absent for affected lessons via admin endpoint or DB script. Add startup-time queue existence check. |
| Race condition caused student to be marked absent despite geo-checkin | LOW | Manual correction via headman `PUT /attendance/{id}/status` endpoint (TEACHER_OVERRIDE source) |

---

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| MongoDB indexes not created | Phase 1: MongoDB infrastructure setup | Integration test asserts `{lesson_id,user_id}` unique index exists |
| RabbitMQ queue not bound | Phase 1: RabbitMQ consumer infrastructure | Testcontainers test: publish event, assert listener fires |
| Auto-absent race condition (late checkin) | Phase 2: Auto-absent consumer | Concurrent test: checkin + lesson.closed race, result must be `present` |
| Enum stored as UPPERCASE | Phase 1: MongoDB entity mapping | Mapping test: insert enum, assert raw document has lowercase string |
| Cancelled lessons included in auto-absent | Phase 2: Auto-absent consumer | Integration test with cancelled lesson fixture, assert no absent record |
| IllegalArgumentException → HTTP 500 from Schedule gRPC | Phase 2: gRPC client wrappers | Unit test with mocked UNKNOWN status response |
| Auto-index creation disabled | Phase 1: MongoDB setup | Verify with `mongoTemplate.indexOps().getIndexInfo()` in test |
| Domain isolation broken (report imports checkin) | All phases: ArchUnit test runs in CI | ArchUnit test must fail when direct import added |
| Geofence cache miss on every checkin | Phase 2: Geo-validation | Load test or simple timer assertion on geofence cache hit rate |
| Geo-blocked lesson triggers auto-absent | Phase 2: Auto-absent consumer | Integration test with `is_geo_blocked=true` lesson |

---

## Sources

- Spring Data MongoDB issue tracker: enum serialization — [DATAMONGO-891](https://github.com/spring-projects/spring-data-mongodb/issues/1817), [DATAMONGO-2635](https://github.com/spring-projects/spring-data-mongodb/issues/3462)
- Spring Boot auto-index-creation issue: [spring-projects/spring-boot#28478](https://github.com/spring-projects/spring-boot/issues/28478), [spring-projects/spring-data-mongodb#4548](https://github.com/spring-projects/spring-data-mongodb/issues/4548)
- Testcontainers MongoDB replica set for transactions: [dev.to/carc](https://dev.to/carc/testcontainers-mongodb-replicaset-4koa), [java.testcontainers.org](https://java.testcontainers.org/modules/databases/mongodb/)
- RabbitMQ DLQ and retry: [Spring AMQP resilience docs](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html), [dzone.com retry tutorial](https://dzone.com/articles/spring-boot-rabbitmq-tutorial-retry-and-error-hand)
- Haversine accuracy: [movable-type.co.uk](https://www.movable-type.co.uk/scripts/latlong.html), [Baeldung distance calculation](https://www.baeldung.com/java-find-distance-between-points)
- MongoDB findAndModify atomicity: [MongoDB community forums](https://www.mongodb.com/community/forums/t/race-condition-while-updating-single-document-multiple-times/134017), [kamranzafar.org atomic updates](https://kamranzafar.org/2016/10/25/atomic-updates-on-mongodb-with-spring-data/)
- gRPC retry fix (1.63): [LogNet/grpc-spring-boot-starter#396](https://github.com/LogNet/grpc-spring-boot-starter/issues/396)
- ArchUnit: [archunit.org user guide](https://www.archunit.org/userguide/html/000_Index.html)
- Known tech debt from codebase: `ScheduleGrpcServiceImpl.getLessonsByGroup()` passes all 4 statuses; `GrpcExceptionAdvice` does not map `IllegalArgumentException` to `INVALID_ARGUMENT`

---
*Pitfalls research for: Attendance Service MVP (MongoDB, RabbitMQ consumer, gRPC clients, geo-validation, auto-absent)*
*Researched: 2026-04-04*
